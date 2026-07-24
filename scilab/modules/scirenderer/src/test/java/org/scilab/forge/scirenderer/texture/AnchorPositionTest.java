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

package org.scilab.forge.scirenderer.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic unit tests for the {@link AnchorPosition} enumeration.
 */
public class AnchorPositionTest {

    @Test
    public void hasExactlyNineAnchors() {
        assertEquals(9, AnchorPosition.values().length);
    }

    @Test
    public void valueOfRoundTripsForEveryConstant() {
        for (AnchorPosition p : AnchorPosition.values()) {
            assertSame(p, AnchorPosition.valueOf(p.name()));
        }
    }

    @Test
    public void declarationOrderIsStable() {
        // The ordinal contract is relied upon elsewhere; pin the first and last.
        assertEquals(0, AnchorPosition.UPPER_LEFT.ordinal());
        assertEquals(4, AnchorPosition.CENTER.ordinal());
        assertEquals(8, AnchorPosition.UP.ordinal());
    }

    @Test
    public void valueOfUnknownNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> AnchorPosition.valueOf("MIDDLE"));
    }
}
