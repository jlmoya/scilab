/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * Hermetic JUnit 6 unit tests for the gui module.
 * This file is hereby licensed under the terms of the GNU GPL v2.0.
 */

package org.scilab.modules.gui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link Size}, a mutable (width, height) integer holder used across the Scilab
 * GUIs. Pure data class; no native code is involved.
 */
public class SizeTest {

    @Test
    public void constructorMapsWidthThenHeight() {
        // The constructor signature is Size(width, height); guard against a field swap.
        Size s = new Size(4, 9);
        assertEquals(4, s.getWidth());
        assertEquals(9, s.getHeight());
    }

    @Test
    public void setWidthChangesOnlyWidth() {
        Size s = new Size(4, 9);
        s.setWidth(100);
        assertEquals(100, s.getWidth());
        assertEquals(9, s.getHeight());
    }

    @Test
    public void setHeightChangesOnlyHeight() {
        Size s = new Size(4, 9);
        s.setHeight(100);
        assertEquals(4, s.getWidth());
        assertEquals(100, s.getHeight());
    }

    @Test
    public void toStringIsWidthThenHeightInBrackets() {
        // toString renders width first, then height: "[width, height]".
        assertEquals("[4, 9]", new Size(4, 9).toString());
    }

    @Test
    public void toStringHandlesZeroAndNegativeValues() {
        assertEquals("[0, 0]", new Size(0, 0).toString());
        assertEquals("[-1, -2]", new Size(-1, -2).toString());
    }

    @Test
    public void toStringReflectsMutation() {
        Size s = new Size(4, 9);
        s.setWidth(7);
        s.setHeight(8);
        assertEquals("[7, 8]", s.toString());
    }

    @Test
    public void integerBoundaryValuesArePreserved() {
        Size s = new Size(Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(Integer.MAX_VALUE, s.getWidth());
        assertEquals(Integer.MIN_VALUE, s.getHeight());
    }

    /**
     * Defect-characterization: {@link Size} overrides {@code toString} but NOT
     * {@code equals}/{@code hashCode}, so two equal-valued sizes are not value-equal;
     * only reference identity holds. This documents current behavior.
     */
    @Test
    public void equalsIsReferenceIdentityOnly() {
        Size a = new Size(4, 9);
        Size b = new Size(4, 9);
        assertNotSame(a, b);
        assertNotEquals(a, b);
        assertEquals(a, a);
    }
}
