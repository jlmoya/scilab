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
 * Tests {@link ScilabDoubleRenderer#setValue(Object)}: null becomes "", a String passes
 * through, a Double is formatted via the inherited {@code convertDouble}. Values are
 * integer-valued to keep the {@code DecimalFormat} output locale-independent.
 */
public class ScilabDoubleRendererTest {

    @BeforeEach
    public void resetFormat() {
        ScilabComplexRenderer.setFormat(ScilabComplexRenderer.SHORT);
    }

    private static String render(ScilabDoubleRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void isAComplexRendererSubclass() {
        assertTrue(new ScilabDoubleRenderer() instanceof ScilabComplexRenderer);
    }

    @Test
    public void doubleValuesAreFormatted() {
        ScilabDoubleRenderer renderer = new ScilabDoubleRenderer();
        assertEquals("3", render(renderer, Double.valueOf(3.0)));
        assertEquals("-2", render(renderer, Double.valueOf(-2.0)));
    }

    @Test
    public void nullBecomesEmptyAndStringPassesThrough() {
        ScilabDoubleRenderer renderer = new ScilabDoubleRenderer();
        assertEquals("", render(renderer, null));
        assertEquals("preformatted", render(renderer, "preformatted"));
    }
}
