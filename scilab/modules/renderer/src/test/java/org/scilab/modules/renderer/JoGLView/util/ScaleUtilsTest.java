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

package org.scilab.modules.renderer.JoGLView.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Hermetic unit tests for {@link ScaleUtils}, the pure logarithmic
 * scaling / unscaling helpers used by the JoGL view. All methods are
 * {@code static} and operate on plain arrays or on the immutable-value
 * {@link Vector3d}; none touch the GL pipeline, so they run without a
 * display.
 */
class ScaleUtilsTest {

    private static final double EPS = 1e-9;
    private static final boolean[] ALL = {true, true, true};
    private static final boolean[] NONE = {false, false, false};

    @Test
    void applyLogScaleArrayAllAxes() {
        double[] coords = {10.0, 100.0, 1000.0};
        ScaleUtils.applyLogScale(coords, ALL);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, coords, EPS);
    }

    @Test
    void applyLogScaleArrayHonorsPerAxisFlags() {
        double[] coords = {10.0, 5.0, 1000.0};
        ScaleUtils.applyLogScale(coords, new boolean[] {true, false, true});
        // Only x and z are log-scaled; y is left untouched.
        assertArrayEquals(new double[] {1.0, 5.0, 3.0}, coords, EPS);
    }

    @Test
    void applyLogScaleArrayNoFlagsLeavesDataUntouched() {
        double[] coords = {42.0, -7.0, 3.14};
        ScaleUtils.applyLogScale(coords, NONE);
        assertArrayEquals(new double[] {42.0, -7.0, 3.14}, coords, EPS);
    }

    @Test
    void applyInverseLogScaleArrayAllAxes() {
        double[] coords = {1.0, 2.0, 3.0};
        ScaleUtils.applyInverseLogScale(coords, ALL);
        assertArrayEquals(new double[] {10.0, 100.0, 1000.0}, coords, 1e-6);
    }

    @Test
    void inverseUndoesForwardScaling() {
        double[] coords = {12.5, 0.4, 987.0};
        ScaleUtils.applyLogScale(coords, ALL);
        ScaleUtils.applyInverseLogScale(coords, ALL);
        assertArrayEquals(new double[] {12.5, 0.4, 987.0}, coords, 1e-6);
    }

    @Test
    void applyLogScaleVectorReturnsScaledCopyLeavingInputUntouched() {
        Vector3d input = new Vector3d(10.0, 100.0, 1000.0);
        Vector3d result = ScaleUtils.applyLogScale(input, ALL);

        assertEquals(1.0, result.getX(), EPS);
        assertEquals(2.0, result.getY(), EPS);
        assertEquals(3.0, result.getZ(), EPS);

        // getData() copies, so the source vector must be unchanged.
        assertEquals(10.0, input.getX(), EPS);
        assertEquals(100.0, input.getY(), EPS);
        assertEquals(1000.0, input.getZ(), EPS);
    }

    @Test
    void applyInverseLogScaleVectorReturnsUnscaledCopy() {
        Vector3d input = new Vector3d(1.0, 2.0, 3.0);
        Vector3d result = ScaleUtils.applyInverseLogScale(input, new boolean[] {true, false, true});

        assertEquals(10.0, result.getX(), 1e-6);
        assertEquals(2.0, result.getY(), EPS);   // y flag false -> passthrough
        assertEquals(1000.0, result.getZ(), 1e-6);
    }

    @Test
    void applyInverseLogScaleToBoundsScalesEachAxisPair() {
        Double[] bounds = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        ScaleUtils.applyInverseLogScaleToBounds(bounds, new boolean[] {true, false, true});

        // x pair (indices 0,1) and z pair (indices 4,5) are 10^v; y pair kept.
        assertEquals(10.0, bounds[0], 1e-6);
        assertEquals(100.0, bounds[1], 1e-6);
        assertEquals(3.0, bounds[2], EPS);
        assertEquals(4.0, bounds[3], EPS);
        assertEquals(100000.0, bounds[4], 1e-3);
        assertEquals(1000000.0, bounds[5], 1e-2);
    }

    @Test
    void applyInverseLogScaleToBoundsNoFlagsIsIdentity() {
        Double[] bounds = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        ScaleUtils.applyInverseLogScaleToBounds(bounds, NONE);
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, bounds);
    }
}
