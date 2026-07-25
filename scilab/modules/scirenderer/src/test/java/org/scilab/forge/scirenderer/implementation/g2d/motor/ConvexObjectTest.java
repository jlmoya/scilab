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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the {@link ConvexObject} geometry surface (coplanarity,
 * depth ordering and 2D separating-axis intersection). {@code ConvexObject} is
 * abstract, so its behavior is exercised through the concrete {@link Triangle}
 * subclass. Nothing here touches Graphics2D.
 */
public class ConvexObjectTest {

    private static final Color[] MONO = {Color.RED, Color.RED, Color.RED};

    /** A unit right-triangle {(0,0,z), (1,0,z), (0,1,z)} living in the plane Z = z. */
    private static Triangle triangleAtZ(double z) throws InvalidPolygonException {
        return new Triangle(
                   new Vector3d[] {new Vector3d(0, 0, z), new Vector3d(1, 0, z), new Vector3d(0, 1, z)},
                   MONO);
    }

    /** A unit right-triangle translated by (dx, dy) inside the plane Z = 0. */
    private static Triangle triangleShifted(double dx, double dy) throws InvalidPolygonException {
        return new Triangle(
                   new Vector3d[] {new Vector3d(dx, dy, 0), new Vector3d(dx + 1, dy, 0), new Vector3d(dx, dy + 1, 0)},
                   MONO);
    }

    // ----- areCoplanar -----

    @Test
    public void trianglesInTheSamePlaneAreCoplanar() throws InvalidPolygonException {
        assertTrue(triangleAtZ(0).areCoplanar(triangleShifted(5, 5)));
    }

    @Test
    public void trianglesInParallelPlanesAreNotCoplanar() throws InvalidPolygonException {
        assertFalse(triangleAtZ(0).areCoplanar(triangleAtZ(1)));
    }

    // ----- isBehind: depth ordering of two overlapping parallel triangles -----

    @Test
    public void higherZTriangleIsReportedBehind() throws InvalidPolygonException {
        Triangle front = triangleAtZ(0);
        Triangle back = triangleAtZ(1);
        // The two triangles share the same XY projection (full overlap) but sit in
        // different Z-planes, so the depth comparison resolves via the bounding box.
        assertEquals(1, front.isBehind(back));
        assertEquals(-1, back.isBehind(front));
    }

    // ----- 2D separating-axis intersection -----

    @Test
    public void fullyOverlappingProjectionsIntersectIn2D() throws InvalidPolygonException {
        Triangle a = triangleAtZ(0);
        Triangle b = triangleAtZ(3); // same XY footprint, different depth
        assertTrue(a.check2DIntersection(b));
        assertTrue(a.check2DTrueIntersection(b));
    }

    @Test
    public void disjointProjectionsDoNotIntersectIn2D() throws InvalidPolygonException {
        Triangle a = triangleAtZ(0);
        Triangle b = triangleShifted(10, 10); // pushed far away in the plane
        assertFalse(a.check2DIntersection(b));
        assertFalse(a.check2DTrueIntersection(b));
    }

    // ----- check(): projection onto a candidate separating axis -----

    @Test
    public void checkReturnsDepthSignForAVerticalAxis() throws InvalidPolygonException {
        Triangle front = triangleAtZ(0);
        Triangle back = triangleAtZ(1);
        // Projected on +Z the two triangles are cleanly separated, so check() yields
        // the sign of the axis' Z component.
        assertEquals(1, front.check(back, new Vector3d(0, 0, 1)));
    }

    @Test
    public void checkReturnsZeroForAHorizontalAxis() throws InvalidPolygonException {
        Triangle front = triangleAtZ(0);
        Triangle back = triangleAtZ(1);
        // A separating axis with a zero Z component cannot order objects in depth.
        assertEquals(0, front.check(back, new Vector3d(1, 0, 0)));
    }

    @Test
    public void checkReturnsZeroForANearZeroAxis() throws InvalidPolygonException {
        Triangle front = triangleAtZ(0);
        Triangle back = triangleAtZ(1);
        assertEquals(0, front.check(back, new Vector3d(0, 0, 0)));
    }

    // ----- addArea -----

    @Test
    public void addAreaAcceptsMultipleObjects() throws InvalidPolygonException {
        Triangle host = triangleAtZ(0);
        // First call lazily allocates the list; the second exercises the append path.
        assertDoesNotThrow(() -> {
            host.addArea(triangleAtZ(1));
            host.addArea(triangleAtZ(2));
        });
    }
}
