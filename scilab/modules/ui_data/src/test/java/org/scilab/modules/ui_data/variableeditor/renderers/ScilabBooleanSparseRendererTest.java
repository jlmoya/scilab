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

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabBooleanSparseRenderer}: for a boolean sparse matrix, {@code false} is a
 * structural zero (blank), {@code true} renders as {@code T}. Covers the {@code isNothing}
 * predicate and {@code setValue}.
 */
public class ScilabBooleanSparseRendererTest {

    private static String render(ScilabBooleanSparseRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void isNothingIsTrueForNullAndFalseOnly() {
        assertTrue(ScilabBooleanSparseRenderer.isNothing(null));
        assertTrue(ScilabBooleanSparseRenderer.isNothing(Boolean.FALSE));
        assertFalse(ScilabBooleanSparseRenderer.isNothing(Boolean.TRUE));
        assertFalse(ScilabBooleanSparseRenderer.isNothing("x"));
    }

    @Test
    public void falseAndNullRenderBlank() {
        ScilabBooleanSparseRenderer renderer = new ScilabBooleanSparseRenderer();
        assertEquals("", render(renderer, null));
        assertEquals("", render(renderer, Boolean.FALSE));
    }

    @Test
    public void trueRendersAsT() {
        ScilabBooleanSparseRenderer renderer = new ScilabBooleanSparseRenderer();
        assertEquals("T", render(renderer, Boolean.TRUE));
    }

    @Test
    public void stringPassesThrough() {
        ScilabBooleanSparseRenderer renderer = new ScilabBooleanSparseRenderer();
        assertEquals("kept", render(renderer, "kept"));
    }
}
