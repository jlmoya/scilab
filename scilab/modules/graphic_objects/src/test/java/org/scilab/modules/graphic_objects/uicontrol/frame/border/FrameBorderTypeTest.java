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

package org.scilab.modules.graphic_objects.uicontrol.frame.border;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link FrameBorderType}: a pure enum with
 * intToEnum and (first-letter driven) stringToEnum converters.
 */
public class FrameBorderTypeTest {

    @Test
    public void intToEnumMapsEveryKnownValue() {
        assertEquals(FrameBorderType.NONE, FrameBorderType.intToEnum(0));
        assertEquals(FrameBorderType.LINE, FrameBorderType.intToEnum(1));
        assertEquals(FrameBorderType.BEVEL, FrameBorderType.intToEnum(2));
        assertEquals(FrameBorderType.SOFTBEVEL, FrameBorderType.intToEnum(3));
        assertEquals(FrameBorderType.ETCHED, FrameBorderType.intToEnum(4));
        assertEquals(FrameBorderType.TITLED, FrameBorderType.intToEnum(5));
        assertEquals(FrameBorderType.EMPTY, FrameBorderType.intToEnum(6));
        assertEquals(FrameBorderType.COMPOUND, FrameBorderType.intToEnum(7));
        assertEquals(FrameBorderType.MATTE, FrameBorderType.intToEnum(8));
    }

    @Test
    public void intToEnumNullIsNull() {
        assertNull(FrameBorderType.intToEnum(null));
    }

    @Test
    public void intToEnumOutOfRangeFallsBackToNone() {
        assertEquals(FrameBorderType.NONE, FrameBorderType.intToEnum(9));
        assertEquals(FrameBorderType.NONE, FrameBorderType.intToEnum(-3));
    }

    @Test
    public void stringToEnumNullOrEmptyIsNull() {
        assertNull(FrameBorderType.stringToEnum(null));
        assertNull(FrameBorderType.stringToEnum(""));
    }

    @Test
    public void stringToEnumMapsByFirstLetter() {
        assertEquals(FrameBorderType.LINE, FrameBorderType.stringToEnum("line"));
        assertEquals(FrameBorderType.BEVEL, FrameBorderType.stringToEnum("bevel"));
        assertEquals(FrameBorderType.SOFTBEVEL, FrameBorderType.stringToEnum("softbevel"));
        assertEquals(FrameBorderType.TITLED, FrameBorderType.stringToEnum("titled"));
        assertEquals(FrameBorderType.COMPOUND, FrameBorderType.stringToEnum("compound"));
        assertEquals(FrameBorderType.MATTE, FrameBorderType.stringToEnum("matte"));
    }

    @Test
    public void stringToEnumIsCaseInsensitiveOnFirstLetter() {
        assertEquals(FrameBorderType.LINE, FrameBorderType.stringToEnum("Line"));
        assertEquals(FrameBorderType.MATTE, FrameBorderType.stringToEnum("MATTE"));
    }

    @Test
    public void stringToEnumDisambiguatesEmptyVersusEtchedBySecondLetter() {
        // Both start with 'e'; the second letter 'm' selects EMPTY, else ETCHED.
        assertEquals(FrameBorderType.EMPTY, FrameBorderType.stringToEnum("empty"));
        assertEquals(FrameBorderType.ETCHED, FrameBorderType.stringToEnum("etched"));
        assertEquals(FrameBorderType.EMPTY, FrameBorderType.stringToEnum("Empty"));
    }

    @Test
    public void stringToEnumUnrecognisedFirstLetterIsNone() {
        assertEquals(FrameBorderType.NONE, FrameBorderType.stringToEnum("xyz"));
        assertEquals(FrameBorderType.NONE, FrameBorderType.stringToEnum("9"));
    }

    @Test
    public void stringToEnumSingleLetterEThrowsBecauseSecondCharIsInspected() {
        // "e" enters the etched/empty branch, then reads chars[1] on a
        // length-1 char array -> IndexOutOfBoundsException.
        assertThrows(IndexOutOfBoundsException.class,
                     () -> FrameBorderType.stringToEnum("e"));
    }

    @Test
    public void enumDeclaresNineConstants() {
        assertEquals(9, FrameBorderType.values().length);
        assertEquals(FrameBorderType.SOFTBEVEL, FrameBorderType.valueOf("SOFTBEVEL"));
    }
}
