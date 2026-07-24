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
 * Tests {@link ScilabIntegerCellEditor#getDataAsScilabString(Object)}:
 * every non-null value is rendered via {@code toString()}; null becomes "".
 */
public class ScilabIntegerCellEditorTest {

    private final ScilabIntegerCellEditor editor = new ScilabIntegerCellEditor();

    @Test
    public void integerRenderedViaToString() {
        assertEquals("42", editor.getDataAsScilabString(Integer.valueOf(42)));
        assertEquals("-7", editor.getDataAsScilabString(Integer.valueOf(-7)));
    }

    @Test
    public void otherNonNullTypesAlsoUseToString() {
        // The `if (value != null)` branch catches every non-null value; the subsequent
        // `else if (value instanceof String)` is therefore dead code. A String still
        // round-trips through toString() to itself.
        assertEquals("text", editor.getDataAsScilabString("text"));
        assertEquals("123", editor.getDataAsScilabString(Long.valueOf(123L)));
    }

    @Test
    public void nullBecomesEmptyString() {
        assertEquals("", editor.getDataAsScilabString(null));
    }
}
