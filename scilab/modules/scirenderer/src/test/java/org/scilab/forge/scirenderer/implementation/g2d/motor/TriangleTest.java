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
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.forge.scirenderer.tranformations.Vector4d;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    public void isPointInsideAcceptsTheReflectedRegion() throws InvalidPolygonException {
        // Companion to the test above: because of the winding convention, the region
        // isPointInside() actually accepts is the triangle reflected through the origin
        // {x<0, y<0, -x-y<1}. This pins that (buggy) behavior explicitly.
        Triangle t = unitTriangle();
        assertTrue(t.isPointInside(new Vector3d(-0.25, -0.25, 0), false));
        assertFalse(t.isPointInside(new Vector3d(0.25, 0.25, 0), false));
    }

    // ----- alternate constructors -----

    @Test
    public void explicitNormalArgumentIsIgnoredAndComputedFromVertices() throws InvalidPolygonException {
        // The 3-argument constructor accepts a normal but never stores it; getNormal()
        // still derives the geometric normal from the vertices.
        Triangle t = new Triangle(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
            MONO, new Vector3d(9, 9, 9));
        assertTrue(new Vector3d(0, 0, 1).equals(t.getNormal()));
    }

    @Test
    public void textureConstructorUsesTheBlackPlaceholderColors() throws InvalidPolygonException {
        Triangle t = new Triangle(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), new Vector3d(0, 1, 0)},
            null, Texture.Filter.LINEAR);
        assertTrue(t.isMonochromatic());
        assertEquals(Color.BLACK, t.getColor(0));
    }

    // ----- isCoplanar -----

    @Test
    public void coplanarityWithSegmentsAndTriangles() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment inPlane = new Segment(new Vector3d[] {new Vector3d(2, 0, 0), new Vector3d(3, 0, 0)},
                                      new Color[] {Color.RED, Color.RED});
        Segment above = new Segment(new Vector3d[] {new Vector3d(2, 0, 1), new Vector3d(3, 0, 1)},
                                    new Color[] {Color.RED, Color.RED});
        assertTrue(t.isCoplanar(inPlane));
        assertFalse(t.isCoplanar(above));

        Triangle coplanarTri = new Triangle(
            new Vector3d[] {new Vector3d(2, 0, 0), new Vector3d(3, 0, 0), new Vector3d(2, 1, 0)}, MONO);
        Triangle raisedTri = new Triangle(
            new Vector3d[] {new Vector3d(2, 0, 1), new Vector3d(3, 0, 1), new Vector3d(2, 1, 1)}, MONO);
        assertTrue(t.isCoplanar(coplanarTri));
        assertFalse(t.isCoplanar(raisedTri));
    }

    // ----- segment membership -----

    @Test
    public void addAndRemoveASegmentThatIsATriangleEdge() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment edge = new Segment(new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0)},
                                   new Color[] {Color.RED, Color.RED});
        // The edge P0-P1 is inside the triangle (border shortcut), so it is accepted.
        assertTrue(t.addSegment(edge));
        assertTrue(t.segments.contains(edge));
        t.removeSegment(edge);
        assertFalse(t.segments.contains(edge));
    }

    @Test
    public void aFarAwayCoplanarSegmentIsNotInside() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment outside = new Segment(new Vector3d[] {new Vector3d(5, 5, 0), new Vector3d(6, 6, 0)},
                                      new Color[] {Color.RED, Color.RED});
        assertFalse(t.addSegment(outside), "a segment outside the triangle must be rejected");
    }

    // ----- getSegmentIntersection -----

    /**
     * Builds a 2-point segment with its normal (the inherited {@code v0} edge vector)
     * pre-initialized. The triangle/segment intersection APIs read {@code segment.v0}
     * directly and assume it was computed - as it is during real scene assembly - so
     * hermetic tests must initialize it explicitly.
     */
    private static Segment initedSegment(Vector3d a, Vector3d b) throws InvalidPolygonException {
        Segment s = new Segment(new Vector3d[] {a, b}, new Color[] {Color.RED, Color.RED});
        s.getNormal();
        return s;
    }

    @Test
    public void segmentIntersectionReturnsTheCrossingRatioWhenItLandsInside() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        // Crosses Z=0 at (-0.25,-0.25) which is inside per the code's (reflected) convention.
        Segment crossing = initedSegment(new Vector3d(-0.25, -0.25, -1), new Vector3d(-0.25, -0.25, 1));
        assertEquals(0.5, t.getSegmentIntersection(crossing), 1e-9);
    }

    @Test
    public void segmentIntersectionIsNaNWhenTheCrossingIsOutside() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment crossingOutside = initedSegment(new Vector3d(0.25, 0.25, -1), new Vector3d(0.25, 0.25, 1));
        assertTrue(Double.isNaN(t.getSegmentIntersection(crossingOutside)));
    }

    @Test
    public void segmentIntersectionIsNaNWhenTheSegmentDoesNotCrossThePlane() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment sameSide = initedSegment(new Vector3d(-0.25, -0.25, 1), new Vector3d(-0.25, -0.25, 2));
        assertTrue(Double.isNaN(t.getSegmentIntersection(sameSide)));
    }

    // ----- breakObject(Vector4d): clip against a plane -----

    @Test
    public void clipKeepsAFullyInsideTriangle() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        List<ConvexObject> result = t.breakObject(new Vector4d(0, 0, 0, 10));
        assertEquals(1, result.size());
        assertSame(t, result.get(0));
    }

    @Test
    public void clipDropsAFullyOutsideTriangle() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        assertNull(t.breakObject(new Vector4d(1, 0, 0, -10)));
    }

    @Test
    public void clipSplitsAStraddlingTriangle() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        // Keep x >= 0.5: the plane cuts the triangle, so a non-empty set of pieces
        // (all on the kept side) is produced.
        List<ConvexObject> result = t.breakObject(new Vector4d(1, 0, 0, -0.5));
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ----- breakObject dispatch -----

    @Test
    public void breakObjectAgainstAnUnsupportedConvexObjectIsNull() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        PolyLine pl = new PolyLine(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(1, 0, 0)},
            new Color[] {Color.RED, Color.RED}, null);
        assertNull(t.breakObject((ConvexObject) pl));
    }

    @Test
    public void breakObjectAgainstANonCrossingSegmentIsNull() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        // The crossing point is outside the (reflected) inside region => no break.
        Segment outside = initedSegment(new Vector3d(0.25, 0.25, -1), new Vector3d(0.25, 0.25, 1));
        assertNull(t.breakObject(outside));
    }

    @Test
    public void breakObjectAgainstACrossingSegmentProducesPieces() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment crossing = initedSegment(new Vector3d(-0.25, -0.25, -1), new Vector3d(-0.25, -0.25, 1));
        List<ConvexObject> pieces = t.breakObject(crossing);
        assertNotNull(pieces);
        assertFalse(pieces.isEmpty());
    }

    @Test
    public void breakIntersectingTrianglesInPerpendicularPlanes() throws InvalidPolygonException {
        Triangle inZ = unitTriangle();
        Triangle inX = new Triangle(
            new Vector3d[] {new Vector3d(0, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)}, MONO);
        List<ConvexObject> pieces = Triangle.breakIntersectingTriangles(inZ, inX);
        assertNotNull(pieces);
        assertFalse(pieces.isEmpty());
    }

    // ----- breakSegmentOnTriangle (static) -----

    @Test
    public void breakSegmentOnTriangleSplitsACrossingSegment() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment crossing = initedSegment(new Vector3d(-0.25, -0.25, -1), new Vector3d(-0.25, -0.25, 1));
        List<ConvexObject> parts = Triangle.breakSegmentOnTriangle(t, crossing);
        assertNotNull(parts);
        assertEquals(2, parts.size());
    }

    @Test
    public void breakSegmentOnTriangleReturnsNullWhenTheSegmentMisses() throws InvalidPolygonException {
        Triangle t = unitTriangle();
        Segment sameSide = initedSegment(new Vector3d(-0.25, -0.25, 1), new Vector3d(-0.25, -0.25, 2));
        assertNull(Triangle.breakSegmentOnTriangle(t, sameSide));
    }
}
