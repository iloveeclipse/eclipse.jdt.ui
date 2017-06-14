/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.buildpath;


import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import org.eclipse.equinox.frameworkadmin.BundleInfo;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.osgi.service.resolver.VersionRange;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.JUnitPreferencesConstants;


public class BuildPathSupport {

	
	public static class JUnitPluginDescription {
		
		private final String bundleId;
		private final VersionRange versionRange;
		private final String bundleRoot;
		private final String binaryImportedRoot;
		private final String sourceBundleId;
		private final String repositorySource;
		private final String javadocPreferenceKey;
		
		private String resolvedVersion = null;

		public JUnitPluginDescription(String bundleId, VersionRange versionRange, String bundleRoot, String binaryImportedRoot, String sourceBundleId, String repositorySource, String javadocPreferenceKey) {
			this.bundleId= bundleId;
			this.versionRange= versionRange;
			this.bundleRoot= bundleRoot;
			this.binaryImportedRoot= binaryImportedRoot;
			this.sourceBundleId= sourceBundleId;
			this.repositorySource= repositorySource;
			this.javadocPreferenceKey= javadocPreferenceKey;
		}
		
		public IPath getBundleLocation() {
			return getBundleLocation(bundleId, versionRange);
		}
		
		public IPath getSourceBundleLocation() {
			return getSourceLocation(getBundleLocation());
		}

		private IPath getBundleLocation(String aBundleId, VersionRange aVersionRange) {
			return getBundleLocation(aBundleId, aVersionRange, false);
		}
		
		private IPath getBundleLocation(String aBundleId, VersionRange aVersionRange, boolean isSourceBundle) {
			BundleInfo bundleInfo = P2Utils.findBundle(aBundleId, aVersionRange, isSourceBundle);
			if (bundleInfo != null) {
				resolvedVersion = bundleInfo.getVersion();
				return P2Utils.getBundleLocationPath(bundleInfo);
			} else {
				// p2's simple configurator is not available. Let's try with installed bundles from the running platform.
				// Note: Source bundles are typically not available at run time!
				Bundle[] bundles= Platform.getBundles(aBundleId, aVersionRange.toString());
				Bundle bestMatch = null;
				if (bundles != null) {
					for (int i= 0; i < bundles.length; i++) {
						Bundle bundle= bundles[i];
						if (bestMatch == null || bundle.getState() > bestMatch.getState()) {
							bestMatch= bundle;
						}
					}
				}
				if (bestMatch != null) {
					try {
						resolvedVersion = bestMatch.getVersion().toString();
						URL rootUrl= bestMatch.getEntry("/"); //$NON-NLS-1$
						URL fileRootUrl= FileLocator.toFileURL(rootUrl);
						return new Path(fileRootUrl.getPath());
					} catch (IOException ex) {
						JUnitCorePlugin.log(ex);
					}
				}
			}
			return null;
		}
		
		private IPath getBundleFileLocation(String aBundleId, VersionRange aVersionRange, String filePath) {
			BundleInfo bundleInfo = P2Utils.findBundle(aBundleId, aVersionRange, false);

			if (bundleInfo != null) {
				resolvedVersion = bundleInfo.getVersion();
				IPath bundleLocation = P2Utils.getBundleLocationPath(bundleInfo);
				if(bundleLocation != null) {
					File bundleLoc = bundleLocation.toFile();
					if(bundleLoc.isDirectory() && new File(bundleLoc, filePath).exists()) {
						return bundleLocation.append(filePath);
					} else if (bundleLoc.isFile() && bundleLoc.getName().endsWith(".jar")) { //$NON-NLS-1$
						return extractArchiveEntry(bundleInfo, bundleLoc, filePath);
					}
				}
			} else {
				// p2's simple configurator is not available. Let's try with installed bundles from the running platform.
				// Note: Source bundles are typically not available at run time!
				Bundle[] bundles= Platform.getBundles(aBundleId, aVersionRange.toString());
				Bundle bestMatch= null;
				if (bundles != null) {
					for (int i= 0; i < bundles.length; i++) {
						Bundle bundle= bundles[i];
						if (bestMatch == null || bundle.getState() > bestMatch.getState()) {
							bestMatch= bundle;
						}
					}
				}
				if (bestMatch != null) {
					try {
						resolvedVersion= bestMatch.getVersion().toString();
						URL rootUrl= bestMatch.getEntry(filePath);
						if (rootUrl != null) {
							URL fileRootUrl= FileLocator.toFileURL(rootUrl);
							return new Path(fileRootUrl.getPath());
						}
					} catch (IOException ex) {
						JUnitCorePlugin.log(ex);
					}
				}
			}
			return null;
		}

		/**
		 * Extract the a library from a bundle to a known location.
		 *
		 * @param bundleInfo bundle information, used to create a reproducible location to hold the
		 *            extracted library
		 * @param bundleLoc the bundle jar
		 * @param filePath the file path within the bundle of the library to be extracted
		 * @return the path to the extracted library
		 */
		private IPath extractArchiveEntry(BundleInfo bundleInfo, File bundleLoc, String filePath) {
			IPath container= JUnitCorePlugin.getDefault().getStateLocation().append(bundleInfo.getSymbolicName()).append(bundleInfo.getVersion());
			IPath extractedPath= container.append(filePath);
			if (extractedPath.toFile().exists()) {
				// previously extracted
				return extractedPath;
			}
			try (JarFile jar= new JarFile(bundleLoc)) {
				ZipEntry entry= jar.getEntry(filePath);
				if (entry != null) {
					if (!container.toFile().exists() && !container.toFile().mkdirs()) {
						JUnitCorePlugin.log(new Status(IStatus.ERROR, JUnitCorePlugin.CORE_PLUGIN_ID, "Unable to create directory to hold " + filePath)); //$NON-NLS-1$
						return null;
					}
					try (InputStream input= jar.getInputStream(entry);
							OutputStream output= Files.newOutputStream(extractedPath.toFile().toPath())) {
						int bytesRead;
						byte[] buffer= new byte[8192];
						while ((bytesRead= input.read(buffer)) > 0) {
							output.write(buffer, 0, bytesRead);
						}
					}
					return extractedPath;
				}
			} catch (IOException ex) {
				JUnitCorePlugin.log(ex);
			}
			return null;
		}

		public IClasspathEntry getLibraryEntry() {
			IPath bundleLocation = getBundleLocation(bundleId, versionRange);
			if (bundleLocation != null) {
				IPath bundleRootLocation= null;
				if (bundleRoot != null) {
					bundleRootLocation= getBundleFileLocation(bundleId, versionRange, bundleRoot);
					if(bundleRootLocation == null) {
						bundleRootLocation= getLocationIfExists(bundleLocation, bundleRoot);
					}
				}
				if (bundleRootLocation == null && binaryImportedRoot != null) {
					bundleRootLocation= getLocationIfExists(bundleLocation, binaryImportedRoot);
				}
				if (bundleRootLocation == null) {
					bundleRootLocation= getBundleLocation(bundleId, versionRange);
				}

				IPath srcLocation= getSourceLocation(bundleLocation);

				String javadocLocation= Platform.getPreferencesService().getString(JUnitCorePlugin.CORE_PLUGIN_ID, javadocPreferenceKey, "", null); //$NON-NLS-1$
				IClasspathAttribute[] attributes;
				if (javadocLocation.length() == 0) {
					attributes= new IClasspathAttribute[0];
				} else {
					attributes= new IClasspathAttribute[] { JavaCore.newClasspathAttribute(IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME, javadocLocation) };
				}
				
				return JavaCore.newLibraryEntry(bundleRootLocation, srcLocation, null, getAccessRules(), attributes, false);
			}
			return null;
		}

		public IAccessRule[] getAccessRules() {
			return new IAccessRule[0];
		}

		private IPath getSourceLocation(IPath bundleLocation) {
			IPath srcLocation= null;
			if (repositorySource != null) {
				// Try source in workspace (from repository)
				srcLocation= getLocationIfExists(bundleLocation, repositorySource);
			}
			
			if (srcLocation == null) {
				if (bundleLocation != null) {
					// Try exact version
					Version version= new Version(resolvedVersion);
					srcLocation= getBundleLocation(sourceBundleId, new VersionRange(version, true, version, true), true);
				}
				if (srcLocation == null) {
					// Try version range
					srcLocation= getBundleLocation(sourceBundleId, versionRange, true);
				}
			}

			return srcLocation;
		}

		private IPath getLocationIfExists(IPath bundleLocationPath, final String entryInBundle) {
			IPath srcLocation= null;
			if (bundleLocationPath != null) {
				File bundleFile= bundleLocationPath.toFile();
				if (bundleFile.isDirectory()) {
					File srcFile= null;
					final int starIdx= entryInBundle.indexOf('*');
					if (starIdx != -1) {
						File[] files= bundleFile.listFiles(new FilenameFilter() {
							private String pre= entryInBundle.substring(0, starIdx);
							private String post= entryInBundle.substring(starIdx + 1);
							@Override
							public boolean accept(File dir, String name) {
								return name.startsWith(pre) && name.endsWith(post);
							}
						});
						if (files.length > 0) {
							srcFile= files[0];
						}
					}
					if (srcFile == null)
						srcFile= new File(bundleFile, entryInBundle);
					if (srcFile.exists()) {
						srcLocation= new Path(srcFile.getPath());
						if (srcFile.isDirectory()) {
							srcLocation= srcLocation.addTrailingSeparator();
						}
					}
				}
			}
			return srcLocation;
		}
	}

	
	public static final JUnitPluginDescription JUNIT3_PLUGIN= new JUnitPluginDescription(
			"org.junit", new VersionRange("[3.8.2,3.9)"), "junit.jar", "junit.jar", "org.junit.source", "source-bundle/", JUnitPreferencesConstants.JUNIT3_JAVADOC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	
	public static final JUnitPluginDescription JUNIT4_PLUGIN= new JUnitPluginDescription(
			"org.junit", new VersionRange("[4.7.0,5.0.0)"), "junit.jar", "junit.jar", "org.junit.source", "source-bundle/", JUnitPreferencesConstants.JUNIT4_JAVADOC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	
	private static final JUnitPluginDescription HAMCREST_CORE_PLUGIN= new JUnitPluginDescription(
			"org.hamcrest.core", new VersionRange("[1.1.0,2.0.0)"), null, "org.hamcrest.core_1.*.jar", "org.hamcrest.core.source", "source-bundle/", JUnitPreferencesConstants.HAMCREST_CORE_JAVADOC); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	public static final JUnitPluginDescription JUNIT_JUPITER_API_PLUGIN= new JUnitPluginDescription(
			"org.junit.jupiter.api", new VersionRange("[5.0.0,6.0.0)"), "org.junit.jupiter.api.jar", "", "org.junit.jupiter.api.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_JUPITER_API_JAVADOC);

	public static final JUnitPluginDescription JUNIT_JUPITER_ENGINE_PLUGIN= new JUnitPluginDescription(
			"org.junit.jupiter.engine", new VersionRange("[5.0.0,6.0.0)"), "org.junit.jupiter.engine.jar", "", "org.junit.jupiter.engine.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_JUPITER_ENGINE_JAVADOC);

	public static final JUnitPluginDescription JUNIT_JUPITER_MIGRATIONSUPPORT_PLUGIN= new JUnitPluginDescription(
			"org.junit.jupiter.migrationsupport", new VersionRange("[5.0.0,6.0.0)"), "org.junit.jupiter.migrationsupport.jar", "", "org.junit.jupiter.migrationsupport.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_JUPITER_MIGRATIONSUPPORT_JAVADOC);

	public static final JUnitPluginDescription JUNIT_JUPITER_PARAMS_PLUGIN= new JUnitPluginDescription(
			"org.junit.jupiter.params", new VersionRange("[5.0.0,6.0.0)"), "org.junit.jupiter.params.jar", "", "org.junit.jupiter.params.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_JUPITER_PARAMS_JAVADOC);

	public static final JUnitPluginDescription JUNIT_PLATFORM_COMMONS_PLUGIN= new JUnitPluginDescription(
			"org.junit.platform.commons", new VersionRange("[1.0.0,2.0.0)"), "org.junit.platform.commons.jar", "", "org.junit.platform.commons.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_PLATFORM_COMMONS_JAVADOC);

	public static final JUnitPluginDescription JUNIT_PLATFORM_ENGINE_PLUGIN= new JUnitPluginDescription(
			"org.junit.platform.engine", new VersionRange("[1.0.0,2.0.0)"), "org.junit.platform.engine.jar", "", "org.junit.platform.engine.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_PLATFORM_ENGINE_JAVADOC);

	public static final JUnitPluginDescription JUNIT_PLATFORM_LAUNCHER_PLUGIN= new JUnitPluginDescription(
			"org.junit.platform.launcher", new VersionRange("[1.0.0,2.0.0)"), "org.junit.platform.launcher.jar", "", "org.junit.platform.launcher.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_PLATFORM_LAUNCHER_JAVADOC);

	public static final JUnitPluginDescription JUNIT_PLATFORM_RUNNER_PLUGIN= new JUnitPluginDescription(
			"org.junit.platform.runner", new VersionRange("[1.0.0,2.0.0)"), "org.junit.platform.runner.jar", "", "org.junit.platform.runner.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_PLATFORM_RUNNER_JAVADOC);

	public static final JUnitPluginDescription JUNIT_PLATFORM_SUITE_API_PLUGIN= new JUnitPluginDescription(
			"org.junit.platform.suite.api", new VersionRange("[1.0.0,2.0.0)"), "org.junit.platform.suite.api.jar", "", "org.junit.platform.suite.api.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_PLATFORM_SUITE_API_JAVADOC);

	public static final JUnitPluginDescription JUNIT_VINTAGE_ENGINE_PLUGIN= new JUnitPluginDescription(
			"org.junit.vintage.engine", new VersionRange("[4.12.0,5.0.0)"), "org.junit.vintage.engine.jar", "", "org.junit.vintage.engine.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_VINTAGE_ENGINE_JAVADOC);

	public static final JUnitPluginDescription JUNIT_OPENTEST4J_PLUGIN= new JUnitPluginDescription(
			"org.opentest4j", new VersionRange("[1.0.0,2.0.0)"), "org.opentest4j.jar", "", "org.opentest4j.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_OPENTEST4J_JAVADOC);

	public static final JUnitPluginDescription JUNIT_APIGUARDIAN_PLUGIN= new JUnitPluginDescription(
			"org.apiguardian", new VersionRange("[1.0.0,2.0.0)"), "org.apiguardian.jar", "", "org.apiguardian.source", "", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
			JUnitPreferencesConstants.JUNIT_APIGUARDIAN_JAVADOC);

	public static final JUnitPluginDescription JUNIT4_AS_3_PLUGIN= new JUnitPluginDescription(
			JUNIT4_PLUGIN.bundleId, JUNIT4_PLUGIN.versionRange, JUNIT4_PLUGIN.bundleRoot, JUNIT4_PLUGIN.binaryImportedRoot,
			JUNIT4_PLUGIN.sourceBundleId, JUNIT4_PLUGIN.repositorySource, JUNIT3_PLUGIN.javadocPreferenceKey) {
		@Override
		public IAccessRule[] getAccessRules() {
			return new IAccessRule[] {
					JavaCore.newAccessRule(new Path("junit/"), IAccessRule.K_ACCESSIBLE), //$NON-NLS-1$
					JavaCore.newAccessRule(new Path("**/*"), IAccessRule.K_NON_ACCESSIBLE) //$NON-NLS-1$
			};
		}
	};
	
	/**
	 * @return the JUnit3 classpath container
	 */
	public static IClasspathEntry getJUnit3ClasspathEntry() {
		return JavaCore.newContainerEntry(JUnitCore.JUNIT3_CONTAINER_PATH);
	}

	/**
	 * @return the JUnit4 classpath container
	 */
	public static IClasspathEntry getJUnit4ClasspathEntry() {
		return JavaCore.newContainerEntry(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	/**
	 * @return the JUnit5 classpath container
	 */
	public static IClasspathEntry getJUnit5ClasspathEntry() {
		return JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	/**
	 * @return the org.junit version 3 library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnit3LibraryEntry() {
		return JUNIT3_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit version 4 library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnit4LibraryEntry() {
		return JUNIT4_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit version 4 library with access rules for JUnit 3, or <code>null</code>
	 *         if not available
	 */
	public static IClasspathEntry getJUnit4as3LibraryEntry() {
		return JUNIT4_AS_3_PLUGIN.getLibraryEntry();
	}
	
	/**
	 * @return the org.hamcrest.core library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getHamcrestCoreLibraryEntry() {
		return HAMCREST_CORE_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.jupiter.api library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitJupiterApiLibraryEntry() {
		return JUNIT_JUPITER_API_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.jupiter.engine library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitJupiterEngineLibraryEntry() {
		return JUNIT_JUPITER_ENGINE_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.jupiter.migrationsupport library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitJupiterMigrationSupportLibraryEntry() {
		return JUNIT_JUPITER_MIGRATIONSUPPORT_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.jupiter.params library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitJupiterParamsLibraryEntry() {
		return JUNIT_JUPITER_PARAMS_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.platform.commons library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitPlatformCommonsLibraryEntry() {
		return JUNIT_PLATFORM_COMMONS_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.platform.engine library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitPlatformEngineLibraryEntry() {
		return JUNIT_PLATFORM_ENGINE_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.platform.launcher library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitPlatformLauncherLibraryEntry() {
		return JUNIT_PLATFORM_LAUNCHER_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.platform.runner library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitPlatformRunnerLibraryEntry() {
		return JUNIT_PLATFORM_RUNNER_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.platform.suite.api library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitPlatformSuiteApiLibraryEntry() {
		return JUNIT_PLATFORM_SUITE_API_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.junit.vintage.engine library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitVintageEngineLibraryEntry() {
		return JUNIT_VINTAGE_ENGINE_PLUGIN.getLibraryEntry();
	}
	
	/**
	 * @return the org.opentest4j library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitOpentest4jLibraryEntry() {
		return JUNIT_OPENTEST4J_PLUGIN.getLibraryEntry();
	}

	/**
	 * @return the org.apiguardian library, or <code>null</code> if not available
	 */
	public static IClasspathEntry getJUnitApiGuardianLibraryEntry() {
		return JUNIT_APIGUARDIAN_PLUGIN.getLibraryEntry();
	}
}
