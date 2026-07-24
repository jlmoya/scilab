/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graph.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;

import org.junit.jupiter.api.Test;

import com.mxgraph.util.mxConstants;

/**
 * Hermetic unit tests for the {@link Font} enum (Scilab xlfont mapping).
 * java.awt.Color is headless-safe and mxConstants only exposes int constants.
 */
public class FontTest {

    @Test
    public void enumHasTenXlfontEntries() {
        assertEquals(10, Font.values().length);
    }

    @Test
    public void namesAndModifiersMatchXlfontTable() {
        assertEquals(java.awt.Font.MONOSPACED, Font.COURIER.getName());
        assertEquals(0, Font.COURIER.getModifiers());

        assertEquals(java.awt.Font.DIALOG, Font.SYMBOL.getName());
        assertEquals(0, Font.SYMBOL.getModifiers());

        assertEquals(java.awt.Font.SERIF, Font.SERIF.getName());
        assertEquals(0, Font.SERIF.getModifiers());

        assertEquals(java.awt.Font.SERIF, Font.SERIF_ITALIC.getName());
        assertEquals(mxConstants.FONT_ITALIC, Font.SERIF_ITALIC.getModifiers());

        assertEquals(mxConstants.FONT_BOLD, Font.SERIF_BOLD.getModifiers());
        assertEquals(mxConstants.FONT_BOLD + mxConstants.FONT_ITALIC,
                     Font.SERIF_BOLD_ITALIC.getModifiers());

        assertEquals(java.awt.Font.SANS_SERIF, Font.SANS_SERIF.getName());
        assertEquals(0, Font.SANS_SERIF.getModifiers());
        assertEquals(mxConstants.FONT_ITALIC, Font.SANS_SERIF_ITALIC.getModifiers());
        assertEquals(mxConstants.FONT_BOLD, Font.SANS_SERIF_BOLD.getModifiers());
        assertEquals(mxConstants.FONT_BOLD + mxConstants.FONT_ITALIC,
                     Font.SANS_SERIF_BOLD_ITALIC.getModifiers());
    }

    @Test
    public void getFontMapsIndexToOrdinal() {
        assertSame(Font.COURIER, Font.getFont(0));
        assertSame(Font.SYMBOL, Font.getFont(1));
        assertSame(Font.SANS_SERIF_BOLD_ITALIC, Font.getFont(9));
    }

    @Test
    public void getFontWrapsModuloTheNumberOfValues() {
        int n = Font.values().length;
        assertSame(Font.getFont(0), Font.getFont(n));
        assertSame(Font.getFont(1), Font.getFont(n + 1));
    }

    @Test
    public void getFontWithNegativeIndexThrows_defectCharacterization() {
        // Java's % keeps the sign, so a negative index produces a negative
        // array subscript rather than wrapping.
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Font.getFont(-1));
    }

    @Test
    public void getSizeNonPositiveIsZero() {
        assertEquals(0, Font.getSize(0));
        assertEquals(0, Font.getSize(-3));
    }

    @Test
    public void getSizeSmallSizesUseLinearInterpolation() {
        // (int)(13 + 1.2*(size-1)) for 1..4
        assertEquals(13, Font.getSize(1));
        assertEquals(14, Font.getSize(2));
        assertEquals(15, Font.getSize(3));
        assertEquals(16, Font.getSize(4));
    }

    @Test
    public void getSizeLargeSizesUseSteeperSlope() {
        // 7*(size-4) + 17 for size > 4
        assertEquals(24, Font.getSize(5));
        assertEquals(31, Font.getSize(6));
        assertEquals(59, Font.getSize(10));
    }

    @Test
    public void getSizeFromStringParsesThenMaps() {
        assertEquals(24, Font.getSize("5"));
        assertEquals(0, Font.getSize("0"));
    }

    @Test
    public void getSizeFromNonNumericStringThrows() {
        assertThrows(NumberFormatException.class, () -> Font.getSize("not-a-number"));
    }

    @Test
    public void getColorMapsScilabIndexToJavaColor() {
        assertEquals(Color.BLACK, Font.getColor(0));
        assertEquals(Color.BLUE, Font.getColor(1));
        assertEquals(Color.GREEN, Font.getColor(2));
        assertEquals(Color.CYAN, Font.getColor(3));
        assertEquals(Color.RED, Font.getColor(4));
        assertEquals(Color.MAGENTA, Font.getColor(5));
        assertEquals(Color.YELLOW, Font.getColor(6));
        assertEquals(Color.WHITE, Font.getColor(7));
    }

    @Test
    public void getColorWrapsModuloTheColormapLength() {
        // The colormap holds 33 entries; index 33 wraps back to entry 0.
        assertEquals(Font.getColor(0), Font.getColor(33));
    }

    @Test
    public void getColorFromStringParsesThenMaps() {
        assertEquals(Color.RED, Font.getColor("4"));
    }

    @Test
    public void getColorWithNegativeIndexThrows_defectCharacterization() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> Font.getColor(-1));
    }
}
