/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the gui module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Color;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link SwingScilabListItem}, an immutable holder of the
 * (text, icon, background, foreground) tuple rendered in Scilab uicontrol lists.
 * Construction and the accessors are headless-safe: {@link Color} and the no-arg
 * {@link ImageIcon} need no display, and the class touches no native code.
 */
public class SwingScilabListItemTest {

    @Test
    public void gettersReturnTheSameReferencesPassedToConstructor() {
        Icon icon = new ImageIcon();
        Color background = Color.RED;
        Color foreground = Color.BLUE;
        SwingScilabListItem item = new SwingScilabListItem("hello", icon, background, foreground);

        assertSame(icon, item.getIcon());
        assertSame(background, item.getBackground());
        assertSame(foreground, item.getForeground());
    }

    @Test
    public void backgroundAndForegroundAreNotSwapped() {
        // Distinct colors guard against a background/foreground mix-up in the constructor.
        SwingScilabListItem item = new SwingScilabListItem("x", null, Color.RED, Color.BLUE);
        assertEquals(Color.RED, item.getBackground());
        assertEquals(Color.BLUE, item.getForeground());
    }

    @Test
    public void toStringReturnsTheText() {
        SwingScilabListItem item = new SwingScilabListItem("the label", null, null, null);
        assertEquals("the label", item.toString());
    }

    @Test
    public void nullIconAndColorsAreStoredAsIs() {
        SwingScilabListItem item = new SwingScilabListItem("t", null, null, null);
        assertNull(item.getIcon());
        assertNull(item.getBackground());
        assertNull(item.getForeground());
    }

    /**
     * Defect-characterization: {@code toString()} returns the raw text field verbatim, so a
     * null text yields a null {@code toString()} - a violation of the usual
     * {@code Object.toString()} non-null contract. This documents current behavior.
     */
    @Test
    public void toStringReturnsNullWhenTextIsNull() {
        SwingScilabListItem item = new SwingScilabListItem(null, null, null, null);
        assertNull(item.toString());
    }

    @Test
    public void emptyTextIsPreserved() {
        SwingScilabListItem item = new SwingScilabListItem("", null, null, null);
        assertEquals("", item.toString());
    }
}
