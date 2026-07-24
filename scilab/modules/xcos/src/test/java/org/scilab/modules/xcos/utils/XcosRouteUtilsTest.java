/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab test coverage
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.scilab.modules.xcos.port.Orientation;

import com.mxgraph.model.mxGeometry;
import com.mxgraph.util.mxPoint;

/**
 * Hermetic unit tests for the geometry / routing helpers of
 * {@link XcosRouteUtils}.
 *
 * <p>This test lives in the same package as the class under test, so its
 * {@code protected static} helpers are invoked directly. Only the methods that
 * depend on primitives, jgraphx value objects ({@link mxPoint},
 * {@link mxGeometry}) and the {@link Orientation} enum are exercised — none of
 * these touch the Scilab native runtime (verified by running the compiled class
 * against these very inputs).</p>
 *
 * <p><b>Not covered here:</b> {@code getLinkPoints}, {@code checkObstacle} over
 * populated cells, {@code checkPointInBlocks}, {@code getPortOrientation} and
 * {@code getLinkPortPosition} require live {@code BasicBlock}/{@code BasicLink}/
 * {@code BasicPort}/{@code SplitBlock} model cells; the obstacle-free routing
 * paths are covered instead by passing an empty cell array.</p>
 */
public class XcosRouteUtilsTest {

    private static final double EPS = 0.0;

    private static void assertPoint(double expectedX, double expectedY, mxPoint p) {
        assertEquals(expectedX, p.getX(), EPS, "x");
        assertEquals(expectedY, p.getY(), EPS, "y");
    }

    /* ---- public constants ---- */

    @Test
    public void constantsHoldTheirDocumentedValues() {
        assertEquals(2.0, XcosRouteUtils.ALIGN_STRICT_ERROR, EPS);
        assertEquals(10.0, XcosRouteUtils.ALIGN_SPLITBLOCK_ERROR, EPS);
        assertEquals(40.0, XcosRouteUtils.BEAUTY_AWAY_DISTANCE, EPS);
        assertEquals(10.0, XcosRouteUtils.BEAUTY_AWAY_REVISION, EPS);
        assertEquals(15.0, XcosRouteUtils.SPLITBLOCK_AWAY_DISTANCE, EPS);
        assertEquals(40.0, XcosRouteUtils.NORMAL_BLOCK_SIZE, EPS);
        assertEquals(3, XcosRouteUtils.TRY_TIMES);
    }

    /* ---- isStrictlyAligned ---- */

    @Test
    public void strictlyAlignedWhenVerticallyClose() {
        // |x2 - x1| < 2
        assertTrue(XcosRouteUtils.isStrictlyAligned(0, 0, 1, 100));
        assertTrue(XcosRouteUtils.isStrictlyAligned(0, 0, 1.9, 100));
    }

    @Test
    public void strictlyAlignedWhenHorizontallyClose() {
        // |y2 - y1| < 2
        assertTrue(XcosRouteUtils.isStrictlyAligned(0, 0, 100, 1));
    }

    @Test
    public void notAlignedWhenBothDeltasReachTheError() {
        assertFalse(XcosRouteUtils.isStrictlyAligned(0, 0, 100, 100));
        // boundary: exactly 2 is NOT strictly less than the error of 2
        assertFalse(XcosRouteUtils.isStrictlyAligned(0, 0, 2, 100));
    }

    /* ---- pointInLineSegment ---- */

    @Test
    public void pointOnHorizontalSegmentIsInside() {
        assertTrue(XcosRouteUtils.pointInLineSegment(5, 0, 0, 0, 10, 0));
    }

    @Test
    public void segmentEndpointsCountAsInside() {
        assertTrue(XcosRouteUtils.pointInLineSegment(0, 0, 0, 0, 10, 0));
        assertTrue(XcosRouteUtils.pointInLineSegment(10, 0, 0, 0, 10, 0));
    }

    @Test
    public void pointOnDiagonalSegmentIsInside() {
        assertTrue(XcosRouteUtils.pointInLineSegment(5, 5, 0, 0, 10, 10));
    }

    @Test
    public void pointOffTheLineIsNotInside() {
        assertFalse(XcosRouteUtils.pointInLineSegment(5, 1, 0, 0, 10, 0));
    }

    @Test
    public void collinearButBeyondTheSegmentIsNotInside() {
        assertFalse(XcosRouteUtils.pointInLineSegment(15, 0, 0, 0, 10, 0));
    }

    /* ---- isPointCoincident ---- */

    @Test
    public void pointsWithinTheStrictErrorAreCoincident() {
        assertTrue(XcosRouteUtils.isPointCoincident(0, 0, 1, 1, false));
        assertTrue(XcosRouteUtils.isPointCoincident(0, 0, 2, 0, false));
        assertTrue(XcosRouteUtils.isPointCoincident(0, 0, 0, 0, false));
    }

    @Test
    public void diagonalErrorSumBoundaryIsNotCoincident() {
        // |dy| <= 2 and |dx| <= 2 hold, but the extra (|dy| + |dx|) < 4 guard fails.
        assertFalse(XcosRouteUtils.isPointCoincident(0, 0, 2, 2, false));
    }

    @Test
    public void axisDistanceBeyondErrorIsNotCoincident() {
        assertFalse(XcosRouteUtils.isPointCoincident(0, 0, 3, 0, false));
    }

    @Test
    public void splitBlockUsesTheLargerError() {
        // (5,5) is not coincident under the strict error of 2 ...
        assertFalse(XcosRouteUtils.isPointCoincident(0, 0, 5, 5, false));
        // ... but is under the split-block error of 10.
        assertTrue(XcosRouteUtils.isPointCoincident(0, 0, 5, 5, true));
    }

    /* ---- isOrientationParallel ---- */

    @Test
    public void identicalOrientationsAreAlwaysParallel() {
        assertTrue(XcosRouteUtils.isOrientationParallel(0, 0, 0, 100, Orientation.EAST, Orientation.EAST));
    }

    @Test
    public void opposingHorizontalPortsAreParallelWhenVerticallySeparated() {
        assertTrue(XcosRouteUtils.isOrientationParallel(0, 0, 0, 100, Orientation.EAST, Orientation.WEST));
    }

    @Test
    public void opposingVerticalPortsAreParallelWhenHorizontallySeparated() {
        assertTrue(XcosRouteUtils.isOrientationParallel(0, 0, 100, 0, Orientation.SOUTH, Orientation.NORTH));
    }

    @Test
    public void opposingHorizontalPortsWithinErrorAreNotParallel() {
        // vertical separation of 1 is inside the strict error, so the EAST/WEST
        // parallel rule does not fire.
        assertFalse(XcosRouteUtils.isOrientationParallel(0, 0, 0, 1, Orientation.EAST, Orientation.WEST));
    }

    @Test
    public void opposingVerticalPortsSharingAnXAreNotParallel_defectCharacterization() {
        // NORTH/SOUTH ports that are vertically apart but share an x-coordinate:
        // the SOUTH/NORTH rule requires |x1 - x2| > error, so this returns false.
        assertFalse(XcosRouteUtils.isOrientationParallel(0, 0, 0, 100, Orientation.NORTH, Orientation.SOUTH));
    }

    /* ---- isLineParallel ---- */

    @Test
    public void distinctParallelLinesReportParallel() {
        assertTrue(XcosRouteUtils.isLineParallel(0, 0, 10, 0, 0, 5, 10, 5, false));
    }

    @Test
    public void intersectingLinesAreNotParallel() {
        assertFalse(XcosRouteUtils.isLineParallel(0, 0, 10, 0, 5, -5, 5, 5, false));
    }

    @Test
    public void coincidentLinesAreExcludedUnlessCoincidenceIsIncluded() {
        // Overlapping collinear segments: excluded when includeCoincide == false ...
        assertFalse(XcosRouteUtils.isLineParallel(0, 0, 10, 0, 5, 0, 15, 0, false));
        // ... and reported parallel when includeCoincide == true.
        assertTrue(XcosRouteUtils.isLineParallel(0, 0, 10, 0, 5, 0, 15, 0, true));
    }

    /* ---- checkPointInGeometry ---- */

    @Test
    public void pointInsideGeometryIsDetected() {
        mxGeometry geo = new mxGeometry(10, 20, 30, 40); // spans x[10,40] y[20,60]
        assertTrue(XcosRouteUtils.checkPointInGeometry(25, 40, geo));
    }

    @Test
    public void geometryBoundaryCornersAreInclusive() {
        mxGeometry geo = new mxGeometry(10, 20, 30, 40);
        assertTrue(XcosRouteUtils.checkPointInGeometry(10, 20, geo));
        assertTrue(XcosRouteUtils.checkPointInGeometry(40, 60, geo));
    }

    @Test
    public void pointOutsideGeometryIsRejected() {
        mxGeometry geo = new mxGeometry(10, 20, 30, 40);
        assertFalse(XcosRouteUtils.checkPointInGeometry(9, 20, geo));   // left of x range
        assertFalse(XcosRouteUtils.checkPointInGeometry(41, 60, geo));  // right of x range
    }

    /* ---- pointInLink (list overload) ---- */

    @Test
    public void pointOnAHorizontalLinkSegmentIsInside() {
        List<mxPoint> link = Arrays.asList(new mxPoint(0, 0), new mxPoint(10, 0));
        assertTrue(XcosRouteUtils.pointInLink(5, 0, link));
    }

    @Test
    public void pointOffAHorizontalLinkIsOutside() {
        List<mxPoint> link = Arrays.asList(new mxPoint(0, 0), new mxPoint(10, 0));
        assertFalse(XcosRouteUtils.pointInLink(5, 5, link));
    }

    @Test
    public void pointOnAVerticalLinkSegmentIsInside() {
        List<mxPoint> link = Arrays.asList(new mxPoint(0, 0), new mxPoint(0, 10));
        assertTrue(XcosRouteUtils.pointInLink(0, 5, link));
        assertFalse(XcosRouteUtils.pointInLink(5, 0, link));
    }

    @Test
    public void degenerateLinksNeverContainAPoint() {
        assertFalse(XcosRouteUtils.pointInLink(0, 0, new ArrayList<mxPoint>()));
        assertFalse(XcosRouteUtils.pointInLink(0, 0, Arrays.asList(new mxPoint(0, 0))));
    }

    /* ---- checkObstacle over an empty world ---- */

    @Test
    public void noCellsMeansNoObstacle() {
        Object[] empty = new Object[0];
        assertFalse(XcosRouteUtils.checkObstacle(0, 0, 10, 10, empty));
        assertFalse(XcosRouteUtils.checkObstacle(0, 0, 10, 10, empty, true));
    }

    /* ---- getIntersection ---- */

    @Test
    public void crossingSegmentsIntersectAtTheCrossPoint() {
        assertPoint(5, 0, XcosRouteUtils.getIntersection(0, 0, 10, 0, 5, -5, 5, 5));
    }

    @Test
    public void diagonalSegmentsIntersectAtTheMidpoint() {
        assertPoint(5, 5, XcosRouteUtils.getIntersection(0, 0, 10, 10, 0, 10, 10, 0));
    }

    @Test
    public void parallelSegmentsDoNotIntersect() {
        assertNull(XcosRouteUtils.getIntersection(0, 0, 10, 0, 0, 5, 10, 5));
    }

    @Test
    public void nonOverlappingCollinearSpansDoNotIntersect() {
        assertNull(XcosRouteUtils.getIntersection(0, 0, 10, 0, 20, -5, 20, 5));
    }

    @Test
    public void overlappingCollinearSegmentsReturnTheOverlapEndpoint() {
        // Coincident horizontal segments [0,10] and [5,15]: the method returns
        // the shared endpoint (x2,y2) of the first line.
        assertPoint(10, 0, XcosRouteUtils.getIntersection(0, 0, 10, 0, 5, 0, 15, 0));
    }

    /* ---- getSimpleRoute ---- */

    @Test
    public void simpleRouteWithoutOrientationYieldsNoRoute_defectCharacterization() {
        // The 2-argument overload forwards null orientations; with no orientation
        // none of the routing branches run, so the route is empty.
        List<mxPoint> route = XcosRouteUtils.getSimpleRoute(new mxPoint(0, 0), new mxPoint(100, 50), new Object[0]);
        assertTrue(route.isEmpty());
    }

    @Test
    public void simpleRouteFromEastPortRoutesHorizontallyFirst() {
        List<mxPoint> route = XcosRouteUtils.getSimpleRoute(
                                  new mxPoint(0, 0), Orientation.EAST,
                                  new mxPoint(100, 50), null, new Object[0]);
        assertEquals(2, route.size());
        assertPoint(100, 0, route.get(0)); // travel along y1 to the target x
        assertPoint(100, 50, route.get(1)); // then drop to the target
    }

    @Test
    public void simpleRouteFromNorthPortRoutesVerticallyFirst() {
        List<mxPoint> route = XcosRouteUtils.getSimpleRoute(
                                  new mxPoint(0, 0), Orientation.NORTH,
                                  new mxPoint(100, 50), null, new Object[0]);
        assertEquals(2, route.size());
        assertPoint(0, 50, route.get(0)); // travel along x1 to the target y
        assertPoint(100, 50, route.get(1));
    }

    @Test
    public void simpleRouteOmitsTargetPointWhenTargetPortIsPerpendicular() {
        // With o2 == NORTH the horizontal-first branch skips appending p2,
        // leaving a single elbow point.
        List<mxPoint> route = XcosRouteUtils.getSimpleRoute(
                                  new mxPoint(0, 0), Orientation.EAST,
                                  new mxPoint(100, 50), Orientation.NORTH, new Object[0]);
        assertEquals(1, route.size());
        assertPoint(100, 0, route.get(0));
    }

    /* ---- getComplexRoute ---- */

    @Test
    public void complexRouteWithNoRemainingTriesIsNull() {
        assertNull(XcosRouteUtils.getComplexRoute(
                       new mxPoint(0, 0), Orientation.EAST,
                       new mxPoint(100, 50), null, new Object[0], 0));
    }

    @Test
    public void complexRouteFromEastPortPrependsTheStartAndDelegates() {
        // With an obstacle-free world it steps NORMAL_BLOCK_SIZE east, then hands
        // off to a simple route, prepending the original start point.
        List<mxPoint> route = XcosRouteUtils.getComplexRoute(
                                  new mxPoint(0, 0), Orientation.EAST,
                                  new mxPoint(100, 50), null, new Object[0], 3);
        assertEquals(3, route.size());
        assertPoint(0, 0, route.get(0));
        assertPoint(100, 0, route.get(1));
        assertPoint(100, 50, route.get(2));
    }
}
