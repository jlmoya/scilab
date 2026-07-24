/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.celleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabStringCellEditor#getDataAsScilabString(Object)}:
 * a String passes through unchanged; anything else (including null) becomes "".
 */
public class ScilabStringCellEditorTest {

    private final ScilabStringCellEditor editor = new ScilabStringCellEditor();

    @Test
    public void stringPassesThrough() {
        assertEquals("hello", editor.getDataAsScilabString("hello"));
        assertEquals("", editor.getDataAsScilabString(""));
    }

    @Test
    public void nullBecomesEmptyString() {
        assertEquals("", editor.getDataAsScilabString(null));
    }

    @Test
    public void nonStringBecomesEmptyString() {
        assertEquals("", editor.getDataAsScilabString(Integer.valueOf(5)));
        assertEquals("", editor.getDataAsScilabString(Double.valueOf(1.5)));
    }
}
