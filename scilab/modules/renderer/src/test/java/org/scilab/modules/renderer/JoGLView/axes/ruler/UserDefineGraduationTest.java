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

package org.scilab.modules.renderer.JoGLView.axes.ruler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.scilab.modules.graphic_objects.axes.AxisProperty;

/**
 * Hermetic unit tests for the package-private {@code UserDefineGraduation}
 * (hence this test lives in the same package). It is a pure implementation
 * of the {@code Graduations} interface driven by a plain model
 * {@link AxisProperty}; no ruler drawing or GL context is involved.
 */
class UserDefineGraduationTest {

    private static AxisProperty linearAxis(Double[] ticks) {
        AxisProperty ax = new AxisProperty();
        ax.setLogFlag(Boolean.FALSE);
        ax.setTicksLocations(ticks);
        return ax;
    }

    @Test
    void boundsAccessorsReturnConstructorArguments() {
        UserDefineGraduation g = new UserDefineGraduation(new AxisProperty(), -3.0, 7.5);
        assertEquals(-3.0, g.getLowerBound(), 0.0);
        assertEquals(7.5, g.getUpperBound(), 0.0);
    }

    @Test
    void bothBoundsAreAlwaysReportedAsIncluded() {
        UserDefineGraduation g = new UserDefineGraduation(new AxisProperty(), 0.0, 1.0);
        assertTrue(g.isLowerBoundIncluded());
        assertTrue(g.isUpperBoundIncluded());
    }

    @Test
    void containIsInclusiveOnBothEnds() {
        UserDefineGraduation g = new UserDefineGraduation(new AxisProperty(), 2.0, 4.0);
        assertTrue(g.contain(2.0), "lower bound is inside");
        assertTrue(g.contain(4.0), "upper bound is inside");
        assertTrue(g.contain(3.0), "interior point is inside");
        assertFalse(g.contain(1.999), "just below lower is outside");
        assertFalse(g.contain(4.001), "just above upper is outside");
    }

    @Test
    void getFormatReturnsAFreshDecimalFormatEachCall() {
        UserDefineGraduation g = new UserDefineGraduation(new AxisProperty(), 0.0, 1.0);
        DecimalFormat a = g.getFormat();
        DecimalFormat b = g.getFormat();
        assertNotNull(a);
        assertNotSame(a, b, "each call must hand back a new formatter");
    }

    @Test
    void unusedGraduationHooksAreNull() {
        UserDefineGraduation g = new UserDefineGraduation(new AxisProperty(), 0.0, 1.0);
        assertNull(g.getParentGraduations());
        assertNull(g.getMore());
        assertNull(g.getAlternative());
        assertNull(g.getSubGraduations());
    }

    @Test
    void constructingWithNullAxisPropertyStillAllowsBoundOnlyQueries() {
        // The bound/contain/format/hook methods never dereference the
        // AxisProperty, so a null model is tolerated for those paths.
        UserDefineGraduation g = new UserDefineGraduation(null, 1.0, 2.0);
        assertEquals(1.0, g.getLowerBound(), 0.0);
        assertEquals(2.0, g.getUpperBound(), 0.0);
        assertTrue(g.contain(1.5));
        assertNotNull(g.getFormat());
        assertNull(g.getMore());
    }

    @Test
    void getAllValuesKeepsOnlyTicksInsideTheBounds() {
        AxisProperty axis = linearAxis(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 2.0, 4.0);
        List<Double> values = g.getAllValues();
        assertEquals(Arrays.asList(2.0, 3.0, 4.0), values);
    }

    @Test
    void getAllValuesIsCachedAndSharedWithGetNewValues() {
        AxisProperty axis = linearAxis(new Double[] {0.0, 1.0, 2.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 10.0);
        List<Double> first = g.getAllValues();
        assertSame(first, g.getAllValues(), "values are computed once and cached");
        assertSame(first, g.getNewValues(), "getNewValues delegates to the cached values");
    }

    @Test
    void getSubGraduationsWithZeroDivisionsIsEmpty() {
        AxisProperty axis = linearAxis(new Double[] {1.0, 2.0, 3.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 10.0);
        assertTrue(g.getSubGraduations(0).isEmpty());
    }

    @Test
    void subDensityIsSubticksPlusOne() {
        AxisProperty axis = new AxisProperty();
        axis.setSubticks(0);
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 1.0);
        assertEquals(1, g.getSubDensity());

        axis.setSubticks(3);
        assertEquals(4, g.getSubDensity());
    }

    private static AxisProperty logAxis(Double[] ticks) {
        AxisProperty ax = new AxisProperty();
        ax.setLogFlag(Boolean.TRUE);
        ax.setTicksLocations(ticks);
        return ax;
    }

    @Test
    void getAllValuesFiltersOnTheLog10OfTicksWhenLogFlagIsSet() {
        // Bounds are expressed in log space: keep a tick d when log10(d) is
        // in [0, 2], i.e. d in [1, 100]; the stored value is the raw tick.
        AxisProperty axis = logAxis(new Double[] {1.0, 10.0, 100.0, 1000.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 2.0);
        assertEquals(Arrays.asList(1.0, 10.0, 100.0), g.getAllValues());
    }

    @Test
    void getSubGraduationsInterpolatesLinearlyBetweenTicks() {
        // One division between each pair of consecutive ticks: the midpoint.
        AxisProperty axis = linearAxis(new Double[] {0.0, 10.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 10.0);
        assertEquals(Arrays.asList(0.0, 5.0, 10.0), g.getSubGraduations(1));
    }

    @Test
    void getSubGraduationsIsComputedOnceAndCached() {
        AxisProperty axis = linearAxis(new Double[] {0.0, 10.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 10.0);
        List<Double> first = g.getSubGraduations(1);
        assertSame(first, g.getSubGraduations(1), "sub-values are memoised");
        // Even a different division count returns the cached list (documents
        // that the first N wins).
        assertSame(first, g.getSubGraduations(4));
    }

    @Test
    void getSubGraduationsFallsBackToRawTicksWhenNoTickIsInBounds() {
        // No tick's own value lies in [8, 12], so getAllValues() is empty and
        // the raw locations drive the interpolation; only the interpolated
        // midpoint 10 falls inside the bounds and survives.
        AxisProperty axis = linearAxis(new Double[] {5.0, 15.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 8.0, 12.0);
        assertEquals(Arrays.asList(10.0), g.getSubGraduations(1));
    }

    @Test
    void getSubGraduationsAppliesTheLogFlagToTheContainmentTest() {
        // Log axis: ticks 1 and 100 map to log-values 0 and 2, both inside
        // [0, 2]; the raw-space midpoint 50.5 is kept because log10(50.5) is
        // inside the bounds too.
        AxisProperty axis = logAxis(new Double[] {1.0, 100.0});
        UserDefineGraduation g = new UserDefineGraduation(axis, 0.0, 2.0);
        assertEquals(Arrays.asList(1.0, 50.5, 100.0), g.getSubGraduations(1));
    }
}
