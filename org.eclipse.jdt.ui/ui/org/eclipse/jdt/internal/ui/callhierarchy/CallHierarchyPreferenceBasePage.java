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
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * @see PreferencePage
 */
public class CallHierarchyPreferenceBasePage extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {
    public CallHierarchyPreferenceBasePage() {
        super(FieldEditorPreferencePage.GRID);

        // Set the preference store for the preference page.
        IPreferenceStore store = JavaPlugin.getDefault().getPreferenceStore();
        setPreferenceStore(store);
    }

    /**
     * @see PreferencePage#init
     */
    public void init(IWorkbench workbench) {}

    /**
     * Set the default preferences for this page.
     */
    public static void initDefaults(IPreferenceStore store) {
        CallHierarchy.getDefault().initializeDefaultBasePreferences(store);
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.preference.FieldEditorPreferencePage#createFieldEditors()
     */
    protected void createFieldEditors() {
        IntegerFieldEditor maxCallDepth = new IntegerFieldEditor(ICallHierarchyPreferencesConstants.PREF_MAX_CALL_DEPTH,
                "&Max call depth", getFieldEditorParent());
        maxCallDepth.setValidRange(1, 99);
        addField(maxCallDepth);

        BooleanFieldEditor useImplementorsForCallerSearch = new BooleanFieldEditor(ICallHierarchyPreferencesConstants.PREF_USE_IMPLEMENTORS_FOR_CALLER_SEARCH,
                "Search for &callers using the Implementors plugin",
                getFieldEditorParent());
        addField(useImplementorsForCallerSearch);

        BooleanFieldEditor useImplementorsForCalleeSearch = new BooleanFieldEditor(ICallHierarchyPreferencesConstants.PREF_USE_IMPLEMENTORS_FOR_CALLEE_SEARCH,
                "Sea&rch for callees using the Implementors plugin",
                getFieldEditorParent());
        addField(useImplementorsForCalleeSearch);

        // This should be reenabled when the openInEditor(Object, boolean) method is made API.
        //        BooleanFieldEditor activateEditorOnSelect = new BooleanFieldEditor(ICallersConstants.PREF_ACTIVATE_EDITOR_ON_SELECT,
        //                "&Activate editor on select", getFieldEditorParent());
        //        addField(activateEditorOnSelect);
    }
}
