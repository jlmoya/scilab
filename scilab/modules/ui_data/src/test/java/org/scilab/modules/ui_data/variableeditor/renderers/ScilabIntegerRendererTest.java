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
 * Tests {@link ScilabIntegerRenderer#setValue(Object)}: null becomes ""; every other value
 * (String or number) is rendered via its {@code toString()}.
 */
public class ScilabIntegerRendererTest {

    private static String render(ScilabIntegerRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", render(new ScilabIntegerRenderer(), null));
    }

    @Test
    public void integerIsShown() {
        assertEquals("7", render(new ScilabIntegerRenderer(), Integer.valueOf(7)));
        assertEquals("-13", render(new ScilabIntegerRenderer(), Integer.valueOf(-13)));
    }

    @Test
    public void stringPassesThrough() {
        assertEquals("already", render(new ScilabIntegerRenderer(), "already"));
    }
}
