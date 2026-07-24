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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabComplexSparseRenderer}: the {@code isNothing} predicate (a "structural
 * zero" of a complex sparse matrix renders as blank) and {@code setValue}. Format pinned to
 * SHORT for locale-independent integer formatting.
 */
public class ScilabComplexSparseRendererTest {

    @BeforeEach
    public void resetFormat() {
        ScilabComplexRenderer.setFormat(ScilabComplexRenderer.SHORT);
    }

    private static Double[] complex(double re, double im) {
        return new Double[] {Double.valueOf(re), Double.valueOf(im)};
    }

    private static String render(ScilabComplexSparseRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void isNothingTreatsNullZeroDoubleAndZeroComplexAsBlank() {
        assertTrue(ScilabComplexSparseRenderer.isNothing(null));
        assertTrue(ScilabComplexSparseRenderer.isNothing(Double.valueOf(0.0)));
        assertTrue(ScilabComplexSparseRenderer.isNothing(complex(0, 0)));
    }

    @Test
    public void isNothingRejectsNonZeroValues() {
        assertFalse(ScilabComplexSparseRenderer.isNothing(Double.valueOf(5.0)));
        assertFalse(ScilabComplexSparseRenderer.isNothing(complex(1, 0)));
        assertFalse(ScilabComplexSparseRenderer.isNothing("x"));
    }

    @Test
    public void structuralZerosRenderBlank() {
        ScilabComplexSparseRenderer renderer = new ScilabComplexSparseRenderer();
        assertEquals("", render(renderer, null));
        assertEquals("", render(renderer, complex(0, 0)));
    }

    @Test
    public void nonZeroComplexRendersLikeTheDenseComplexRenderer() {
        ScilabComplexSparseRenderer renderer = new ScilabComplexSparseRenderer();
        assertEquals("3+2i", render(renderer, complex(3, 2)));
    }

    @Test
    public void stringPassesThrough() {
        ScilabComplexSparseRenderer renderer = new ScilabComplexSparseRenderer();
        assertEquals("literal", render(renderer, "literal"));
    }

    @Test
    public void nonZeroSingleDoubleHitsTheComplexArrayCastAndThrows() {
        // Documented behavior: a non-"nothing" value that is not a String is passed to the
        // dense complex renderer, which casts it to Double[]. A bare Double therefore fails
        // the cast. This renderer is only ever fed complex (Double[]) cells in practice.
        ScilabComplexSparseRenderer renderer = new ScilabComplexSparseRenderer();
        assertThrows(ClassCastException.class, () -> renderer.setValue(Double.valueOf(5.0)));
    }
}
