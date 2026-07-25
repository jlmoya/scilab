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
import org.scilab.forge.scirenderer.tranformations.Vector4d;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link PolyLine}. The static {@code getPolyLines} splitter
 * and the clipping-plane {@code breakObject} are exercised; the {@code draw} path is
 * out of scope. The test shares the class' package, so the inherited protected
 * {@code vertices} array is reachable for structural assertions.
 */
public class PolyLineTest {

    private static Vector3d v(double x, double y, double z) {
        return new Vector3d(x, y, z);
    }

    private static Color[] reds(int n) {
        Color[] c = new Color[n];
        for (int i = 0; i < n; i++) {
            c[i] = Color.RED;
        }
        return c;
    }

    private static PolyLine polyline(Vector3d... vs) throws InvalidPolygonException {
        return new PolyLine(vs, reds(vs.length), null);
    }

    // ----- construction -----

    @Test
    public void constructorRejectsSinglePoint() {
        assertThrows(InvalidPolygonException.class, () -> new PolyLine(new Vector3d[] {v(0, 0, 0)}, reds(1), null));
    }

    @Test
    public void polyLineSkipsDegeneracyAndFinitenessChecks() throws InvalidPolygonException {
        // PolyLine overrides isDegenerate()/isNanOrInf() to false, so - unlike other
        // drawables - it tolerates duplicate (and even non-finite) vertices.
        PolyLine duplicate = polyline(v(0, 0, 0), v(0, 0, 0));
        assertEquals(2, duplicate.vertices.length);
        // The overridden normal is always null and the object is always declared planar.
        assertNull(duplicate.getNormal());
        assertTrue(duplicate.isPlanar());
    }

    // ----- getPolyLines: splitting on NaN / Inf -----

    @Test
    public void getPolyLinesReturnsOneRunWhenClean() {
        List<PolyLine> list = PolyLine.getPolyLines(
                                  new Vector3d[] {v(0, 0, 0), v(1, 0, 0), v(2, 0, 0)}, reds(3), null, false);
        assertEquals(1, list.size());
        assertEquals(3, list.get(0).vertices.length);
    }

    @Test
    public void getPolyLinesSplitsOnAnInteriorNaN() {
        List<PolyLine> list = PolyLine.getPolyLines(
                                  new Vector3d[] {v(0, 0, 0), v(1, 0, 0), v(Double.NaN, 0, 0), v(3, 0, 0), v(4, 0, 0)},
                                  reds(5), null, false);
        assertEquals(2, list.size());
        assertEquals(2, list.get(0).vertices.length);
        assertEquals(2, list.get(1).vertices.length);
    }

    @Test
    public void getPolyLinesDropsRunsTooShortToBeAPolyLine() {
        // The lone leading point (before the NaN) cannot form a poly-line and is dropped.
        List<PolyLine> list = PolyLine.getPolyLines(
                                  new Vector3d[] {v(0, 0, 0), v(Double.POSITIVE_INFINITY, 0, 0), v(2, 0, 0), v(3, 0, 0)},
                                  reds(4), null, false);
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).vertices.length);
    }

    @Test
    public void getPolyLinesLoopAppendsTheFirstVertex() {
        List<PolyLine> list = PolyLine.getPolyLines(
                                  new Vector3d[] {v(0, 0, 0), v(1, 0, 0), v(0, 1, 0)}, reds(3), null, true);
        assertEquals(1, list.size());
        PolyLine loop = list.get(0);
        assertEquals(4, loop.vertices.length, "a looping poly-line repeats the first vertex");
        assertTrue(loop.vertices[0].equals(loop.vertices[3]));
    }

    // ----- breakObject(Vector4d): clipping against a plane -----

    @Test
    public void breakObjectKeepsWholePolyLineWhenFullyInside() throws InvalidPolygonException {
        PolyLine pl = polyline(v(0, 0, 0), v(1, 0, 0), v(2, 0, 0));
        // Plane x + 10 >= 0 keeps every vertex, so the original object is returned as-is.
        List<ConvexObject> result = pl.breakObject(new Vector4d(1, 0, 0, 10));
        assertEquals(1, result.size());
        assertSame(pl, result.get(0));
    }

    @Test
    public void breakObjectDropsEverythingWhenFullyOutside() throws InvalidPolygonException {
        PolyLine pl = polyline(v(0, 0, 0), v(1, 0, 0), v(2, 0, 0));
        // Plane x - 10 >= 0 excludes every vertex.
        List<ConvexObject> result = pl.breakObject(new Vector4d(1, 0, 0, -10));
        assertTrue(result.isEmpty());
    }

    @Test
    public void breakObjectClipsAtThePlaneCrossing() throws InvalidPolygonException {
        PolyLine pl = polyline(v(0, 0, 0), v(1, 0, 0), v(2, 0, 0));
        // Plane x - 0.5 >= 0 keeps the sub-path with x >= 0.5; the first edge is cut at x = 0.5.
        List<ConvexObject> result = pl.breakObject(new Vector4d(1, 0, 0, -0.5));
        assertEquals(1, result.size());
        PolyLine clipped = (PolyLine) result.get(0);
        assertEquals(3, clipped.vertices.length);
        assertEquals(0.5, clipped.vertices[0].getX(), 1e-9);
        assertEquals(2.0, clipped.vertices[2].getX(), 1e-9);
    }

    @Test
    public void breakObjectIsIdentityForANonPlanarClip() throws InvalidPolygonException {
        PolyLine pl = polyline(v(0, 0, 0), v(1, 0, 0), v(2, 0, 0));
        // A clip whose normal has a non-zero Z component is ignored for 2D poly-lines.
        List<ConvexObject> result = pl.breakObject(new Vector4d(0, 0, 1, -5));
        assertEquals(1, result.size());
        assertSame(pl, result.get(0));
    }

    @Test
    public void breakObjectAgainstAConvexObjectIsUnsupported() throws InvalidPolygonException {
        PolyLine pl = polyline(v(0, 0, 0), v(1, 0, 0));
        assertNull(pl.breakObject(pl));
    }
}
