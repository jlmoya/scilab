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

package org.scilab.modules.renderer.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for the pure log-scaling helpers of
 * {@link CommonHandler}. The class also carries many editor helpers that
 * go through the graphic controller; those are out of scope here. The
 * scalar and array log/inverse-log converters below are plain arithmetic
 * and run without any Scilab engine.
 */
class CommonHandlerTest {

    private static final double EPS = 1e-9;

    @Test
    void scalarLogScaleOnlyAppliesWhenFlagged() {
        assertEquals(2.0, CommonHandler.logScale(100.0, true), EPS);
        assertEquals(100.0, CommonHandler.logScale(100.0, false), EPS);
    }

    @Test
    void scalarInverseLogScaleOnlyAppliesWhenFlagged() {
        assertEquals(100.0, CommonHandler.InverseLogScale(2.0, true), 1e-6);
        assertEquals(2.0, CommonHandler.InverseLogScale(2.0, false), EPS);
    }

    @Test
    void scalarLogAndInverseAreMutualInverses() {
        double v = 37.5;
        assertEquals(v, CommonHandler.InverseLogScale(CommonHandler.logScale(v, true), true), 1e-6);
    }

    @Test
    void arrayToLogScaleReturnsANewArrayWhenScalingAndLeavesInputUntouched() {
        double[] input = {10.0, 100.0, 1000.0};
        double[] out = CommonHandler.toLogScale(input, true);
        assertNotSame(input, out, "scaling must not mutate the caller's array");
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, out, EPS);
        assertArrayEquals(new double[] {10.0, 100.0, 1000.0}, input, EPS);
    }

    @Test
    void arrayToLogScaleReturnsSameArrayWhenNotScaling() {
        double[] input = {10.0, 100.0, 1000.0};
        double[] out = CommonHandler.toLogScale(input, false);
        assertSame(input, out, "no-op scaling should pass the array straight through");
    }

    @Test
    void arrayToInverseLogScaleRoundTripsWithToLogScale() {
        double[] input = {5.0, 50.0, 500.0};
        double[] logged = CommonHandler.toLogScale(input, true);
        double[] back = CommonHandler.toInverseLogScale(logged, true);
        assertArrayEquals(input, back, 1e-6);
    }

    @Test
    void arrayToInverseLogScaleReturnsSameArrayWhenNotScaling() {
        double[] input = {1.0, 2.0, 3.0};
        assertSame(input, CommonHandler.toInverseLogScale(input, false));
    }

    @Test
    void tripletToLogScaleAppliesPerAxisFlagsInPlace() {
        // Two xyz points; log x and z, keep y.
        double[] data = {10.0, 100.0, 1000.0, 10.0, 100.0, 1000.0};
        CommonHandler.toLogScale(data, new boolean[] {true, false, true});
        assertArrayEquals(new double[] {1.0, 100.0, 3.0, 1.0, 100.0, 3.0}, data, EPS);
    }

    @Test
    void tripletToLogScaleWithNoFlagsIsIdentity() {
        double[] data = {10.0, 100.0, 1000.0};
        CommonHandler.toLogScale(data, new boolean[] {false, false, false});
        assertArrayEquals(new double[] {10.0, 100.0, 1000.0}, data, EPS);
    }

    @Test
    void tripletInverseUndoesTripletLog() {
        double[] data = {10.0, 7.0, 1000.0};
        boolean[] flags = {true, false, true};
        CommonHandler.toLogScale(data, flags);
        CommonHandler.toInverseLogScale(data, flags);
        assertArrayEquals(new double[] {10.0, 7.0, 1000.0}, data, 1e-6);
    }
}
