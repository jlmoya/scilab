/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.celleditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabDoubleCellEditor#getDataAsScilabString(Object)}: a single Double is
 * converted via the inherited {@code convertDouble}; a String passes through; anything else
 * (including a complex {@code Double[]}) becomes "".
 */
public class ScilabDoubleCellEditorTest {

    private final ScilabDoubleCellEditor editor = new ScilabDoubleCellEditor();

    @Test
    public void isAComplexEditorSubclass() {
        // The double editor reuses the complex editor's machinery.
        assertTrue(editor instanceof ScilabComplexCellEditor);
    }

    @Test
    public void finiteDoubleIsConverted() {
        assertEquals("3.0", editor.getDataAsScilabString(Double.valueOf(3.0)));
        assertEquals("-1.25", editor.getDataAsScilabString(Double.valueOf(-1.25)));
    }

    @Test
    public void specialDoublesUseScilabLiterals() {
        assertEquals("%nan", editor.getDataAsScilabString(Double.valueOf(Double.NaN)));
        assertEquals("%inf", editor.getDataAsScilabString(Double.valueOf(Double.POSITIVE_INFINITY)));
        assertEquals("-%inf", editor.getDataAsScilabString(Double.valueOf(Double.NEGATIVE_INFINITY)));
    }

    @Test
    public void stringPassesThroughAndOthersBecomeEmpty() {
        assertEquals("verbatim", editor.getDataAsScilabString("verbatim"));
        assertEquals("", editor.getDataAsScilabString(null));
        // A complex array is not a single Double, so the double editor returns "".
        assertEquals("", editor.getDataAsScilabString(new Double[] {1.0, 2.0}));
    }
}
