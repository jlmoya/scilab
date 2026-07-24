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
 * Tests {@link ScilabBooleanCellEditor#getDataAsScilabString(Object)}:
 * Boolean true/false map to the Scilab literals {@code %t}/{@code %f};
 * a String passes through; anything else (including null) becomes "".
 */
public class ScilabBooleanCellEditorTest {

    private final ScilabBooleanCellEditor editor = new ScilabBooleanCellEditor();

    @Test
    public void trueMapsToPercentT() {
        assertEquals("%t", editor.getDataAsScilabString(Boolean.TRUE));
    }

    @Test
    public void falseMapsToPercentF() {
        assertEquals("%f", editor.getDataAsScilabString(Boolean.FALSE));
    }

    @Test
    public void stringPassesThrough() {
        assertEquals("already", editor.getDataAsScilabString("already"));
    }

    @Test
    public void nullAndOtherTypesBecomeEmptyString() {
        assertEquals("", editor.getDataAsScilabString(null));
        assertEquals("", editor.getDataAsScilabString(Integer.valueOf(1)));
    }
}
