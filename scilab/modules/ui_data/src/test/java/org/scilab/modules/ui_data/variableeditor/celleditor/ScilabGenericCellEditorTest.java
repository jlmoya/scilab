/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.celleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JFormattedTextField;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabGenericCellEditor}, the base cell editor. Its
 * {@code getDataAsScilabString} is a neutral "" and is overridden by the typed subclasses.
 * Construction uses a headless-safe {@link JFormattedTextField}, no native code.
 */
public class ScilabGenericCellEditorTest {

    @Test
    public void baseEditorAlwaysReturnsEmptyScilabString() {
        ScilabGenericCellEditor editor = new ScilabGenericCellEditor();
        assertEquals("", editor.getDataAsScilabString("anything"));
        assertEquals("", editor.getDataAsScilabString(Integer.valueOf(42)));
        assertEquals("", editor.getDataAsScilabString(null));
    }

    @Test
    public void editorComponentIsAFormattedTextField() {
        ScilabGenericCellEditor editor = new ScilabGenericCellEditor();
        assertNotNull(editor.getComponent());
        assertTrue(editor.getComponent() instanceof JFormattedTextField);
    }
}
