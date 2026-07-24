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
import org.scilab.forge.scirenderer.tranformations.Vector3d;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Segment}. Only the non-rendering surface is exercised.
 */
public class SegmentTest {

    private static final double EPS = 1e-12;

    private static Segment segment(Vector3d a, Vector3d b) throws InvalidPolygonException {
        return new Segment(new Vector3d[] {a, b}, new Color[] {Color.RED, Color.RED});
    }

    @Test
    public void constructorRejectsWrongVertexCount() {
        assertThrows(InvalidPolygonException.class,
                     () -> new Segment(new Vector3d[] {new Vector3d(0, 0, 0)}, new Color[] {Color.RED}));
        assertThrows(InvalidPolygonException.class,
                     () -> new Segment(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(2, 0, 0)},
                                       new Color[] {Color.RED, Color.RED, Color.RED}));
    }

    @Test
    public void lengthIsTheEuclideanDistance() throws InvalidPolygonException {
        Segment s = segment(new Vector3d(0, 0, 0), new Vector3d(3, 4, 0));
        assertEquals(5.0, s.getLength(), EPS);
    }

    @Test
    public void staticLengthMatchesInstanceLength() {
        assertEquals(5.0, Segment.getLength(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(3, 4, 0)}), EPS);
    }

    @Test
    public void isIn2DWhenBothEndpointsHaveZeroZ() throws InvalidPolygonException {
        assertTrue(segment(new Vector3d(0, 0, 0), new Vector3d(3, 4, 0)).isIn2D());
        assertFalse(segment(new Vector3d(0, 0, 0), new Vector3d(3, 4, 1)).isIn2D());
    }

    @Test
    public void isInFrontWhenBothEndpointsSitAtMinusHalf() throws InvalidPolygonException {
        assertTrue(segment(new Vector3d(0, 0, -0.5), new Vector3d(1, 1, -0.5)).isInFront());
        assertFalse(segment(new Vector3d(0, 0, 0), new Vector3d(1, 1, 0)).isInFront());
    }

    @Test
    public void equalsIsOrientationIndependent() throws InvalidPolygonException {
        Segment forward = segment(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
        Segment reversed = segment(new Vector3d(1, 0, 0), new Vector3d(0, 0, 0));
        Segment same = segment(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
        assertEquals(forward, same);
        assertEquals(forward, reversed, "endpoints may be listed in either order");
        assertNotEquals(forward, segment(new Vector3d(0, 0, 0), new Vector3d(2, 0, 0)));
    }

    @Test
    public void toStringStartsWithSegmentLabel() throws InvalidPolygonException {
        String s = segment(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0)).toString();
        assertTrue(s.startsWith("Segment "), s);
    }

    @Test
    public void reversedEqualSegmentsHaveDifferentHashCodes() throws InvalidPolygonException {
        // Defect characterization: equals() ignores endpoint order but hashCode() is
        // computed from the ordered vertex array, so two segments that are equals() can
        // (and here do) hash differently - a violation of the equals/hashCode contract.
        Segment forward = segment(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
        Segment reversed = segment(new Vector3d(1, 0, 0), new Vector3d(0, 0, 0));
        assertEquals(forward, reversed);
        assertNotEquals(forward.hashCode(), reversed.hashCode());
    }
}
