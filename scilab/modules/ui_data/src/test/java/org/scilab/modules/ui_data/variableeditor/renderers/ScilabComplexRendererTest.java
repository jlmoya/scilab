/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabComplexRenderer}: complex-number to display-string conversion, the
 * {@code convertDouble} helper, and the {@code isNull} predicate.
 *
 * The renderer formats via a {@code DecimalFormat}; to stay locale-independent every tested
 * value is integer-valued (no fractional digits are ever emitted). The number format is a
 * mutable static, so {@link #resetFormat()} pins it to SHORT before each test.
 */
public class ScilabComplexRendererTest {

    @BeforeEach
    public void resetFormat() {
        ScilabComplexRenderer.setFormat(ScilabComplexRenderer.SHORT);
    }

    private static Double[] complex(double re, double im) {
        return new Double[] {Double.valueOf(re), Double.valueOf(im)};
    }

    /** setValue() is protected; same-package access lets us drive it and read getText(). */
    private static String render(ScilabComplexRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void formatConstantsAreDistinct() {
        assertEquals(0, ScilabComplexRenderer.SHORT);
        assertEquals(1, ScilabComplexRenderer.SHORTE);
        assertEquals(2, ScilabComplexRenderer.LONG);
        assertEquals(3, ScilabComplexRenderer.LONGE);
    }

    @Test
    public void nullAndStringPassThroughSetValue() {
        ScilabComplexRenderer renderer = new ScilabComplexRenderer();
        assertEquals("", render(renderer, null));
        assertEquals("already-a-string", render(renderer, "already-a-string"));
    }

    @Test
    public void zeroComplexRendersAsZero() {
        ScilabComplexRenderer renderer = new ScilabComplexRenderer();
        assertEquals("0", render(renderer, complex(0, 0)));
    }

    @Test
    public void pureRealAndPureImaginary() {
        ScilabComplexRenderer renderer = new ScilabComplexRenderer();
        assertEquals("3", render(renderer, complex(3, 0)));
        assertEquals("i", render(renderer, complex(0, 1)));
        assertEquals("-i", render(renderer, complex(0, -1)));
        assertEquals("2i", render(renderer, complex(0, 2)));
        assertEquals("-2i", render(renderer, complex(0, -2)));
    }

    @Test
    public void fullComplexNumbers() {
        ScilabComplexRenderer renderer = new ScilabComplexRenderer();
        assertEquals("3+i", render(renderer, complex(3, 1)));
        assertEquals("3-i", render(renderer, complex(3, -1)));
        assertEquals("3+2i", render(renderer, complex(3, 2)));
        assertEquals("3-2i", render(renderer, complex(3, -2)));
    }

    @Test
    public void convertDoubleFormatsIntegerValues() {
        assertEquals("3", ScilabComplexRenderer.convertDouble(Double.valueOf(3.0)));
        assertEquals("-5", ScilabComplexRenderer.convertDouble(Double.valueOf(-5.0)));
        assertEquals("12", ScilabComplexRenderer.convertDouble(Double.valueOf(12.0)));
    }

    @Test
    public void isNullDetectsNullAndZeroComplex() {
        assertTrue(ScilabComplexRenderer.isNull(null));
        assertTrue(ScilabComplexRenderer.isNull(complex(0, 0)));
        assertFalse(ScilabComplexRenderer.isNull(complex(1, 0)));
        assertFalse(ScilabComplexRenderer.isNull(complex(0, 1)));
    }
}
