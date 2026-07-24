/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link LayoutType}: a pure enum with three
 * conversion helpers (intToEnum, stringToEnum, enumToString).
 */
public class LayoutTypeTest {

    @Test
    public void intToEnumMapsKnownValues() {
        assertEquals(LayoutType.NONE, LayoutType.intToEnum(0));
        assertEquals(LayoutType.GRIDBAG, LayoutType.intToEnum(1));
        assertEquals(LayoutType.GRID, LayoutType.intToEnum(2));
        assertEquals(LayoutType.BORDER, LayoutType.intToEnum(3));
    }

    @Test
    public void intToEnumUnknownValuesFallBackToNone() {
        assertEquals(LayoutType.NONE, LayoutType.intToEnum(4));
        assertEquals(LayoutType.NONE, LayoutType.intToEnum(99));
        assertEquals(LayoutType.NONE, LayoutType.intToEnum(-1));
    }

    @Test
    public void intToEnumNullThrows() {
        // switch on a null Integer unboxes to int -> NullPointerException.
        assertThrows(NullPointerException.class, () -> LayoutType.intToEnum(null));
    }

    @Test
    public void stringToEnumNullOrEmptyIsNone() {
        assertEquals(LayoutType.NONE, LayoutType.stringToEnum(null));
        assertEquals(LayoutType.NONE, LayoutType.stringToEnum(""));
    }

    @Test
    public void stringToEnumRecognisesGridVariantsCaseInsensitively() {
        assertEquals(LayoutType.GRID, LayoutType.stringToEnum("grid"));
        assertEquals(LayoutType.GRID, LayoutType.stringToEnum("GRID"));
        assertEquals(LayoutType.GRID, LayoutType.stringToEnum("Grid"));
        assertEquals(LayoutType.GRIDBAG, LayoutType.stringToEnum("gridbag"));
        assertEquals(LayoutType.GRIDBAG, LayoutType.stringToEnum("GridBag"));
    }

    @Test
    public void stringToEnumRecognisesBorderByFirstLetter() {
        assertEquals(LayoutType.BORDER, LayoutType.stringToEnum("border"));
        assertEquals(LayoutType.BORDER, LayoutType.stringToEnum("BORDER"));
        // Any word starting with b/B is treated as BORDER.
        assertEquals(LayoutType.BORDER, LayoutType.stringToEnum("banana"));
    }

    @Test
    public void stringToEnumUnknownIsNone() {
        assertEquals(LayoutType.NONE, LayoutType.stringToEnum("xyz"));
        // Starts with 'g' but is neither grid nor gridbag -> falls through to NONE.
        assertEquals(LayoutType.NONE, LayoutType.stringToEnum("g"));
        assertEquals(LayoutType.NONE, LayoutType.stringToEnum("grimace"));
    }

    @Test
    public void enumToStringMapsEveryConstant() {
        assertEquals("none", LayoutType.enumToString(LayoutType.NONE));
        assertEquals("gridbag", LayoutType.enumToString(LayoutType.GRIDBAG));
        assertEquals("grid", LayoutType.enumToString(LayoutType.GRID));
        assertEquals("border", LayoutType.enumToString(LayoutType.BORDER));
    }

    @Test
    public void stringRoundTripIsStable() {
        for (LayoutType t : LayoutType.values()) {
            String s = LayoutType.enumToString(t);
            assertEquals(t, LayoutType.stringToEnum(s),
                         "round trip broken for " + t);
        }
    }

    @Test
    public void enumDeclaresExactlyFourConstants() {
        assertEquals(4, LayoutType.values().length);
        assertEquals(LayoutType.GRID, LayoutType.valueOf("GRID"));
    }
}
