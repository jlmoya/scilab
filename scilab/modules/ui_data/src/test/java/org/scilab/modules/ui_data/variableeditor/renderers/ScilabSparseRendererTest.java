/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabSparseRenderer#setValue(Object)}: for a real sparse matrix, a zero (or
 * null) is a structural zero rendered blank, and a non-zero Double is formatted via
 * {@code convertDouble}. Format pinned to SHORT for locale-independent integer formatting.
 */
public class ScilabSparseRendererTest {

    @BeforeEach
    public void resetFormat() {
        ScilabComplexRenderer.setFormat(ScilabComplexRenderer.SHORT);
    }

    private static String render(ScilabSparseRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void isAComplexSparseRendererSubclass() {
        assertTrue(new ScilabSparseRenderer() instanceof ScilabComplexSparseRenderer);
    }

    @Test
    public void zeroAndNullRenderBlank() {
        ScilabSparseRenderer renderer = new ScilabSparseRenderer();
        assertEquals("", render(renderer, null));
        assertEquals("", render(renderer, Double.valueOf(0.0)));
    }

    @Test
    public void nonZeroDoubleIsFormatted() {
        ScilabSparseRenderer renderer = new ScilabSparseRenderer();
        assertEquals("3", render(renderer, Double.valueOf(3.0)));
        assertEquals("-4", render(renderer, Double.valueOf(-4.0)));
    }

    @Test
    public void stringPassesThrough() {
        ScilabSparseRenderer renderer = new ScilabSparseRenderer();
        assertEquals("shown", render(renderer, "shown"));
    }
}
