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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link Triangle}. Only the geometry/predicate surface that
 * does not touch Graphics2D is exercised.
 */
public class TriangleTest {

    private static final Color[] MONO = {Color.RED, Color.RED, Color.RED};

    private static Triangle unitTriangle() throws InvalidPolygonException {
        return new Triangle(
                   new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
                   MONO);
    }

    @Test
    public void constructorRejectsWrongVertexCount() {
        assertThrows(InvalidPolygonException.class,
                     () -> new Triangle(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0)},
                                        new Color[] {Color.RED, Color.RED}));
        assertThrows(InvalidPolygonException.class,
                     () -> new Triangle(new Vector3d[] {
            new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(1, 1, 0)
        },
        new Color[] {Color.RED, Color.RED, Color.RED, Color.RED}));
    }

    @Test
    public void constructorRejectsDegenerateTriangle() {
        assertThrows(InvalidPolygonException.class,
                     () -> new Triangle(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), new Vector3d(0, 1, 0)},
                                        MONO));
    }

    @Test
    public void isIn2DDetectsAZeroZTriangle() throws InvalidPolygonException {
        assertTrue(unitTriangle().isIn2D());
        Triangle spatial = new Triangle(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 1)},
            MONO);
        assertFalse(spatial.isIn2D());
    }

    @Test
    public void pointOnVertices() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        assertTrue(t.pointOnVertices(new Vector3d(0, 0, 0)));
        assertTrue(t.pointOnVertices(new Vector3d(1, 0, 0)));
        assertTrue(t.pointOnVertices(new Vector3d(0, 1, 0)));
        assertFalse(t.pointOnVertices(new Vector3d(0.5, 0.5, 0)));
    }

    @Test
    public void normalOfTheZPlaneTriangleIsUnitZ() throws InvalidPolygonException {
        assertTrue(new Vector3d(0, 0, 1).equals(unitTriangle().getNormal()));
    }

    @Test
    public void toStringStartsWithTriangleLabel() throws InvalidPolygonException {
        String s = unitTriangle().toString();
        assertTrue(s.startsWith("Triangle:"), s);
        assertTrue(s.contains("[0.0, 0.0, 0.0]"), s);
    }

    @Test
    public void boundingBoxSpansTheTriangle() throws InvalidPolygonException {
        Triangle t = new Triangle(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(4, 0, 0), new Vector3d(0, 2, 0)},
            MONO);
        assertTrue(t.getBBox().isIntersecting(new BoundingBox(0, 4, 0, 2, 0, 0)));
    }

    @Test
    public void pointOffThePlaneIsNotInside() throws InvalidPolygonException {
        // A point with non-zero Z is not coplanar => reported outside.
        assertFalse(unitTriangle().isPointInside(new Vector3d(0.25, 0.25, 5)));
    }

    @Test
    public void aVertexIsNotStrictlyInside() throws InvalidPolygonException {
        // Boundary points are excluded by the strict inequalities.
        assertFalse(unitTriangle().isPointInside(new Vector3d(0, 0, 0)));
    }

    @Test
    public void anExteriorCoplanarPointIsNotInside() throws InvalidPolygonException {
        assertFalse(unitTriangle().isPointInside(new Vector3d(5, 5, 0)));
    }

    @Test
    public void centroidIsReportedOutsideForACounterClockwiseTriangle() throws InvalidPolygonException {
        // Defect characterization: isPointInside() uses a sign convention that only
        // accepts one winding order. For this counter-clockwise triangle the centroid
        // - which is geometrically inside - is reported as outside. This test pins the
        // current behavior rather than the geometric expectation.
        assertFalse(unitTriangle().isPointInside(new Vector3d(1.0 / 3.0, 1.0 / 3.0, 0)));
    }
}
