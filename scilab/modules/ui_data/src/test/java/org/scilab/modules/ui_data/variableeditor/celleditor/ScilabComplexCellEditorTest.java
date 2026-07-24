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
 * Tests {@link ScilabComplexCellEditor}: conversion of a complex value ({@code Double[]} of
 * {re, im}) into a Scilab expression, plus the shared {@code convertDouble} helper. All
 * formatting here is {@code Double.toString}-based, so results are locale-independent.
 */
public class ScilabComplexCellEditorTest {

    private final ScilabComplexCellEditor editor = new ScilabComplexCellEditor();

    private static Double[] complex(double re, double im) {
        return new Double[] {Double.valueOf(re), Double.valueOf(im)};
    }

    // ---- convertDouble (protected static, same-package access) ----

    @Test
    public void convertDoubleHandlesFiniteNanAndInfinities() {
        assertEquals("3.0", ScilabComplexCellEditor.convertDouble(Double.valueOf(3.0)));
        assertEquals("1.5", ScilabComplexCellEditor.convertDouble(Double.valueOf(1.5)));
        assertEquals("%nan", ScilabComplexCellEditor.convertDouble(Double.valueOf(Double.NaN)));
        assertEquals("%inf", ScilabComplexCellEditor.convertDouble(Double.valueOf(Double.POSITIVE_INFINITY)));
        assertEquals("-%inf", ScilabComplexCellEditor.convertDouble(Double.valueOf(Double.NEGATIVE_INFINITY)));
    }

    // ---- getDataAsScilabString on complex arrays ----

    @Test
    public void pureZeroComplexIsZero() {
        assertEquals("0", editor.getDataAsScilabString(complex(0, 0)));
    }

    @Test
    public void pureRealComplex() {
        assertEquals("3.0", editor.getDataAsScilabString(complex(3, 0)));
    }

    @Test
    public void pureImaginaryUnit() {
        assertEquals("%i", editor.getDataAsScilabString(complex(0, 1)));
        assertEquals("-%i", editor.getDataAsScilabString(complex(0, -1)));
    }

    @Test
    public void pureImaginaryScaled() {
        assertEquals("2.0*%i", editor.getDataAsScilabString(complex(0, 2)));
        assertEquals("-2.0*%i", editor.getDataAsScilabString(complex(0, -2)));
    }

    @Test
    public void fullComplexWithUnitImaginary() {
        assertEquals("3.0+%i", editor.getDataAsScilabString(complex(3, 1)));
        assertEquals("3.0-%i", editor.getDataAsScilabString(complex(3, -1)));
    }

    @Test
    public void fullComplexWithScaledImaginary() {
        assertEquals("3.0+2.0*%i", editor.getDataAsScilabString(complex(3, 2)));
        assertEquals("3.0-2.0*%i", editor.getDataAsScilabString(complex(3, -2)));
    }

    // ---- getDataAsScilabString on other inputs ----

    @Test
    public void stringPassesThroughAndOthersBecomeEmpty() {
        assertEquals("keepme", editor.getDataAsScilabString("keepme"));
        // A single Double is NOT a Double[]; the complex editor does not handle it.
        assertEquals("", editor.getDataAsScilabString(Double.valueOf(3.0)));
        assertEquals("", editor.getDataAsScilabString(null));
    }
}
