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

package org.scilab.forge.scirenderer.implementation.g2d.motor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link BoundingBox}, the axis-aligned box used for
 * broad-phase intersection/ordering in the Graphics2D motor.
 */
public class BoundingBoxTest {

    /** A unit-ish box spanning [0,10] on every axis. */
    private static BoundingBox base() {
        return new BoundingBox(0, 10, 0, 10, 0, 10);
    }

    @Test
    public void toStringFormat() {
        assertEquals("[0.0;10.0]x[0.0;10.0]x[0.0;10.0]", base().toString());
    }

    @Test
    public void xCompareDetectsLeftRightSeparation() {
        BoundingBox a = base();
        BoundingBox right = new BoundingBox(20, 30, 0, 10, 0, 10);
        assertEquals(-1, a.xCompare(right));
        assertEquals(1, right.xCompare(a));
    }

    @Test
    public void xCompareReturnsZeroWhenOverlapping() {
        BoundingBox a = base();
        BoundingBox overlapping = new BoundingBox(5, 15, 0, 10, 0, 10);
        assertEquals(0, a.xCompare(overlapping));
    }

    @Test
    public void yCompareDetectsVerticalSeparation() {
        BoundingBox a = base();
        BoundingBox above = new BoundingBox(0, 10, 20, 30, 0, 10);
        assertEquals(-1, a.yCompare(above));
        assertEquals(1, above.yCompare(a));
    }

    @Test
    public void zCompareDetectsDepthOrdering() {
        BoundingBox a = base();
        BoundingBox behind = new BoundingBox(0, 10, 0, 10, 20, 30);
        assertEquals(1, a.zCompare(behind));
        assertEquals(-1, behind.zCompare(a));
    }

    @Test
    public void isIntersecting() {
        BoundingBox a = base();
        assertTrue(a.isIntersecting(a));
        assertTrue(a.isIntersecting(new BoundingBox(5, 15, 5, 15, 5, 15)));
        assertFalse(a.isIntersecting(new BoundingBox(20, 30, 0, 10, 0, 10)));
    }

    @Test
    public void touchingBoxesIntersectButNotStrictly() {
        BoundingBox a = base();
        BoundingBox touching = new BoundingBox(10, 20, 0, 10, 0, 10);
        assertTrue(a.isIntersecting(touching));
        assertFalse(a.isStrictlyIntersecting(touching));
    }

    @Test
    public void strictlyIntersectingForRealOverlap() {
        BoundingBox a = base();
        assertTrue(a.isStrictlyIntersecting(new BoundingBox(5, 15, 5, 15, 5, 15)));
    }

    @Test
    public void isNonZOverlapping() {
        BoundingBox a = base();
        // Fully separated on X => non-overlapping.
        assertTrue(a.isNonZOverlapping(new BoundingBox(20, 30, 0, 10, 0, 10)));
        // Fully coincident => overlapping, so NOT "non-overlapping".
        assertFalse(a.isNonZOverlapping(a));
    }
}
