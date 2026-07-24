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

package org.scilab.modules.helptools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.scilab.modules.helptools.Converter.Backend;

/**
 * Hermetic unit tests for the {@link Converter.Backend} enum — the closed set of
 * documentation-generation backends the help build can target.
 *
 * <p>{@code Converter} itself is an interface (only method contracts, nothing to
 * exercise directly); its one piece of concrete, testable behaviour is this
 * nested enum. The tests pin the exact constant set, their declaration order
 * (a few callers switch on {@code ordinal()}), and {@code valueOf} round-tripping.
 */
public class ConverterTest {

    @Test
    public void hasExactlyTheEightKnownBackends() {
        Backend[] all = Backend.values();
        assertEquals(8, all.length, "the backend set changed — update dependent switches");
    }

    @Test
    public void declarationOrderIsStable() {
        // Docbook converters first, then the container-only ones.
        assertEquals(0, Backend.JAVAHELP.ordinal());
        assertEquals(1, Backend.HTML.ordinal());
        assertEquals(2, Backend.WEB.ordinal());
        assertEquals(3, Backend.CHM.ordinal());
        assertEquals(4, Backend.FO.ordinal());
        assertEquals(5, Backend.JAR_ONLY.ordinal());
        assertEquals(6, Backend.PDF.ordinal());
        assertEquals(7, Backend.PS.ordinal());
    }

    @Test
    public void valueOfRoundTripsEveryConstant() {
        for (Backend b : Backend.values()) {
            assertSame(b, Backend.valueOf(b.name()));
        }
    }

    @Test
    public void valueOfRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> Backend.valueOf("XHTML"));
        // Enum constants are case sensitive.
        assertThrows(IllegalArgumentException.class, () -> Backend.valueOf("html"));
    }
}
