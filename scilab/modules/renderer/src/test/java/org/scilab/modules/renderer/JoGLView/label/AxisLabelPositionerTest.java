/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.JoGLView.label;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.texture.AnchorPosition;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Hermetic unit tests for {@link AxisLabelPositioner}. This test sits in
 * the same package so it can exercise the {@code protected} auto-placement
 * algorithms ({@code computeDisplacedPosition}, {@code getAutoRotationAngle},
 * {@code getAutoAnchorPosition}) directly. They are pure vector arithmetic
 * over {@link Vector3d} and need no drawing tools.
 */
class AxisLabelPositionerTest {

    private static void assertVec(Vector3d v, double x, double y, double z) {
        assertEquals(x, v.getX(), 1e-9);
        assertEquals(y, v.getY(), 1e-9);
        assertEquals(z, v.getZ(), 1e-9);
    }

    @Test
    void constructorDefaultsAreZeroVectorsAndZeroRatio() {
        AxisLabelPositioner p = new AxisLabelPositioner();
        assertVec(p.getTicksDirection(), 0.0, 0.0, 0.0);
        assertVec(p.getProjectedTicksDirection(), 0.0, 0.0, 0.0);
        assertEquals(0.0, p.getDistanceRatio(), 0.0);
    }

    @Test
    void ticksDirectionSetterStoresADefensiveCopy() {
        AxisLabelPositioner p = new AxisLabelPositioner();
        Vector3d in = new Vector3d(1.0, 0.0, 0.0);
        p.setTicksDirection(in);
        assertNotSame(in, p.getTicksDirection());
        assertVec(p.getTicksDirection(), 1.0, 0.0, 0.0);
    }

    @Test
    void projectedTicksDirectionSetterStoresADefensiveCopy() {
        AxisLabelPositioner p = new AxisLabelPositioner();
        Vector3d in = new Vector3d(0.0, 1.0, 0.0);
        p.setProjectedTicksDirection(in);
        assertNotSame(in, p.getProjectedTicksDirection());
        assertVec(p.getProjectedTicksDirection(), 0.0, 1.0, 0.0);
    }

    @Test
    void distanceRatioRoundTrips() {
        AxisLabelPositioner p = new AxisLabelPositioner();
        p.setDistanceRatio(2.5);
        assertEquals(2.5, p.getDistanceRatio(), 0.0);
    }

    @Test
    void computeDisplacedPositionOffsetsLabelAlongTicksDirection() {
        AxisLabelPositioner p = new AxisLabelPositioner();
        p.setLabelPosition(new Vector3d(1.0, 1.0, 1.0));
        p.setTicksDirection(new Vector3d(2.0, 0.0, 0.0));
        p.setDistanceRatio(3.0);

        Vector3d displaced = p.computeDisplacedPosition();

        // displacement = ticksDirection * distRatio = (6,0,0)
        assertVec(p.getLabelDisplacement(), 6.0, 0.0, 0.0);
        // anchor position = labelPosition + displacement = (7,1,1)
        assertVec(displaced, 7.0, 1.0, 1.0);
    }

    @Test
    void autoRotationAngleIsZero() {
        assertEquals(0.0, new AxisLabelPositioner().getAutoRotationAngle(), 0.0);
    }

    /** Helper: place a positioner with a projected direction and rotation. */
    private static AnchorPosition autoAnchor(double px, double py, double rotationDeg) {
        AxisLabelPositioner p = new AxisLabelPositioner();
        p.setProjectedTicksDirection(new Vector3d(px, py, 0.0));
        p.setRotationAngle(rotationDeg);
        return p.getAutoAnchorPosition();
    }

    @Test
    void autoAnchorPositionForAxisAlignedDirectionsAtZeroRotation() {
        // With no rotation the corrected anchor equals the uncorrected one:
        // +X -> LEFT, +Y -> DOWN, -Y -> UP, -X -> RIGHT.
        assertEquals(AnchorPosition.LEFT, autoAnchor(1.0, 0.0, 0.0));
        assertEquals(AnchorPosition.DOWN, autoAnchor(0.0, 1.0, 0.0));
        assertEquals(AnchorPosition.UP, autoAnchor(0.0, -1.0, 0.0));
        assertEquals(AnchorPosition.RIGHT, autoAnchor(-1.0, 0.0, 0.0));
    }

    @Test
    void autoAnchorPositionRotatesThroughTheAllowedQuadrantsClockwise() {
        // Starting from +X (LEFT), +90 deg advances one quadrant to DOWN,
        // and -90 deg steps back one quadrant to UP. This exercises the
        // signed-offset / modulo-4 cycling logic.
        assertEquals(AnchorPosition.DOWN, autoAnchor(1.0, 0.0, 90.0));
        assertEquals(AnchorPosition.UP, autoAnchor(1.0, 0.0, -90.0));
    }
}
