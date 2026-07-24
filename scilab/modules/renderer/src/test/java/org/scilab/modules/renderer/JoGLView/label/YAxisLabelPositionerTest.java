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

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Hermetic unit tests for {@link YAxisLabelPositioner}. This test sits in
 * the positioner package so it can call the {@code protected}
 * {@code getAutoRotationAngle} override, which is where the y-axis
 * specialisation lives.
 *
 * <p>With no parent axes attached the override takes its non-3D branch and
 * returns 270 degrees; the 3D branch (0 degrees) needs a live {@code Axes}
 * whose view mode is queried, so it is out of scope here. The inherited
 * {@link AxisLabelPositioner} accessors are pure and spot-checked to confirm
 * the subclass wiring.
 */
class YAxisLabelPositionerTest {

    @Test
    void autoRotationAngleIs270WhenNoParentAxesIsAttached() {
        // parentAxes is null on a freshly constructed positioner, so the
        // override falls through to its default (non-3D) rotation.
        assertEquals(270.0, new YAxisLabelPositioner().getAutoRotationAngle(), 0.0);
    }

    @Test
    void overridesTheBaseAxisRotationOfZero() {
        // The whole point of the subclass: the generic axis positioner keeps
        // labels upright (0 deg) while the y-axis one rotates them (270 deg).
        assertEquals(0.0, new AxisLabelPositioner().getAutoRotationAngle(), 0.0);
        assertEquals(270.0, new YAxisLabelPositioner().getAutoRotationAngle(), 0.0);
    }

    @Test
    void inheritsAxisLabelPositionerDefaults() {
        YAxisLabelPositioner p = new YAxisLabelPositioner();
        assertEquals(0.0, p.getDistanceRatio(), 0.0);
        Vector3d ticks = p.getTicksDirection();
        assertEquals(0.0, ticks.getX(), 0.0);
        assertEquals(0.0, ticks.getY(), 0.0);
        assertEquals(0.0, ticks.getZ(), 0.0);
    }

    @Test
    void distanceRatioRoundTripsThroughTheInheritedSetter() {
        YAxisLabelPositioner p = new YAxisLabelPositioner();
        p.setDistanceRatio(3.25);
        assertEquals(3.25, p.getDistanceRatio(), 0.0);
        // Rotation is independent of the distance ratio.
        assertEquals(270.0, p.getAutoRotationAngle(), 0.0);
    }
}
