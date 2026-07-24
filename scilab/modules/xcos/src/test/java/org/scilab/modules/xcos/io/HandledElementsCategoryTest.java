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

package org.scilab.modules.xcos.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the {@link HandledElementsCategory} enum. This is a
 * plain Java enum with no dependencies on the Scilab native runtime, so every
 * assertion runs without loading any shared library.
 */
public class HandledElementsCategoryTest {

    /** The six categories, in their exact declaration order. */
    private static final HandledElementsCategory[] EXPECTED_IN_ORDER = {
        HandledElementsCategory.JGRAPHX,
        HandledElementsCategory.BLOCK,
        HandledElementsCategory.LINK,
        HandledElementsCategory.PORT,
        HandledElementsCategory.RAW_DATA,
        HandledElementsCategory.CUSTOM,
    };

    @Test
    public void hasExactlySixCategories() {
        assertEquals(6, HandledElementsCategory.values().length);
    }

    @Test
    public void valuesAreInDeclarationOrder() {
        // Ordinals are contractually the declaration order; this pins the wire/dispatch order.
        assertArrayEquals(EXPECTED_IN_ORDER, HandledElementsCategory.values());
    }

    @Test
    public void ordinalsAreZeroBasedAndContiguous() {
        HandledElementsCategory[] values = HandledElementsCategory.values();
        for (int i = 0; i < values.length; i++) {
            assertEquals(i, values[i].ordinal(),
                         "ordinal must equal the index for " + values[i]);
        }
    }

    @Test
    public void valueOfRoundTripsForEveryConstant() {
        for (HandledElementsCategory c : HandledElementsCategory.values()) {
            assertSame(c, HandledElementsCategory.valueOf(c.name()),
                       "valueOf(name()) must return the same singleton for " + c);
        }
    }

    @Test
    public void nameMatchesToStringForEveryConstant() {
        for (HandledElementsCategory c : HandledElementsCategory.values()) {
            assertEquals(c.name(), c.toString());
        }
    }

    @Test
    public void expectedNamesArePresentAndComplete() {
        EnumSet<HandledElementsCategory> all = EnumSet.allOf(HandledElementsCategory.class);
        assertEquals(6, all.size());
        assertNotNull(HandledElementsCategory.valueOf("JGRAPHX"));
        assertNotNull(HandledElementsCategory.valueOf("BLOCK"));
        assertNotNull(HandledElementsCategory.valueOf("LINK"));
        assertNotNull(HandledElementsCategory.valueOf("PORT"));
        assertNotNull(HandledElementsCategory.valueOf("RAW_DATA"));
        assertNotNull(HandledElementsCategory.valueOf("CUSTOM"));
    }

    @Test
    public void valueOfUnknownNameThrows() {
        assertThrows(IllegalArgumentException.class,
                     () -> HandledElementsCategory.valueOf("NOT_A_CATEGORY"));
    }

    @Test
    public void valueOfIsCaseSensitive() {
        // The lower-case spelling is not a constant name; it must not resolve.
        assertThrows(IllegalArgumentException.class,
                     () -> HandledElementsCategory.valueOf("block"));
    }

    @Test
    public void valueOfNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                     () -> HandledElementsCategory.valueOf(null));
    }

    @Test
    public void constantsAreDistinct() {
        // Sanity: no accidental duplication when collected through a set.
        assertEquals(HandledElementsCategory.values().length,
                     EnumSet.copyOf(Arrays.asList(HandledElementsCategory.values())).size());
    }
}
