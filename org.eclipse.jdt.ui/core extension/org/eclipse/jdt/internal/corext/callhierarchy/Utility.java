/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation 
 * 			(report 36180: Callers/Callees view)
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.callhierarchy;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.internal.ui.JavaPlugin;

class Utility {
    static void logDebug(String message) {
        if (JavaPlugin.getDefault().isDebugging()) {
            JavaPlugin.getDefault().getLog().log(new Status(IStatus.INFO,
                    JavaPlugin.getPluginId(), IStatus.INFO, message, null));
        }
    }

    static void logError(String message, Throwable t) {
        JavaPlugin.getDefault().getLog().log(new Status(IStatus.ERROR,
                JavaPlugin.getPluginId(), IStatus.ERROR, message, t));
    }
}
