/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the ui_data module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.ui_data.variableeditor.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ScilabBooleanRenderer#setValue(Object)}: Boolean true/false display as
 * {@code T}/{@code F}; null becomes ""; a String passes through. No format dependency.
 */
public class ScilabBooleanRendererTest {

    private static String render(ScilabBooleanRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void trueAndFalseDisplayAsTandF() {
        ScilabBooleanRenderer renderer = new ScilabBooleanRenderer();
        assertEquals("T", render(renderer, Boolean.TRUE));
        assertEquals("F", render(renderer, Boolean.FALSE));
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", render(new ScilabBooleanRenderer(), null));
    }

    @Test
    public void stringPassesThrough() {
        assertEquals("T", render(new ScilabBooleanRenderer(), "T"));
        assertEquals("custom", render(new ScilabBooleanRenderer(), "custom"));
    }
}
