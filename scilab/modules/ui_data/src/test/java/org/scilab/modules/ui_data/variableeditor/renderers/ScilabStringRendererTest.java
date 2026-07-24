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
 * Tests {@link ScilabStringRenderer#setValue(Object)}: null becomes ""; every other value
 * is rendered via its {@code toString()} (the {@code DefaultTableCellRenderer} contract).
 */
public class ScilabStringRendererTest {

    private static String render(ScilabStringRenderer renderer, Object value) {
        renderer.setValue(value);
        return renderer.getText();
    }

    @Test
    public void nullBecomesEmpty() {
        assertEquals("", render(new ScilabStringRenderer(), null));
    }

    @Test
    public void stringIsShownVerbatim() {
        assertEquals("hello", render(new ScilabStringRenderer(), "hello"));
        assertEquals("", render(new ScilabStringRenderer(), ""));
    }

    @Test
    public void nonStringUsesToString() {
        assertEquals("42", render(new ScilabStringRenderer(), Integer.valueOf(42)));
    }
}
