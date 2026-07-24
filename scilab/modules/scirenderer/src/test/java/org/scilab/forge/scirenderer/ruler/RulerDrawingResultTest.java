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

package org.scilab.forge.scirenderer.ruler;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link RulerDrawingResult}, the immutable value object a
 * {@link RulerDrawer} hands back. The package-private constructor is reachable because this
 * test lives in the same package.
 */
public class RulerDrawingResultTest {

    private static RulerDrawingResult make(List<Double> ticks, List<Double> subTicks, int density,
                                           double maxDist, Vector3d dir) {
        return new RulerDrawingResult(new DecimalFormat("0.##"), ticks, subTicks, density, maxDist, dir);
    }

    @Test
    public void tickListIsCopiedIntoADoubleArray() {
        RulerDrawingResult r = make(Arrays.asList(1.0, 2.5, 4.0), Collections.<Double>emptyList(),
                                    2, 0.0, new Vector3d(1, 0, 0));
        assertArrayEquals(new double[] {1.0, 2.5, 4.0}, r.getTicksValues());
    }

    @Test
    public void subTickListIsCopiedIntoADoubleArray() {
        RulerDrawingResult r = make(Collections.<Double>emptyList(), Arrays.asList(0.5, 1.5),
                                    2, 0.0, new Vector3d(1, 0, 0));
        assertArrayEquals(new double[] {0.5, 1.5}, r.getSubTicksValues());
    }

    @Test
    public void tickAccessorsReturnDefensiveCopies() {
        RulerDrawingResult r = make(Arrays.asList(1.0, 2.0), Collections.<Double>emptyList(),
                                    1, 0.0, new Vector3d(1, 0, 0));
        double[] first = r.getTicksValues();
        double[] second = r.getTicksValues();
        assertNotSame(first, second, "each call must return a fresh array");
        first[0] = 999.0;
        assertArrayEquals(new double[] {1.0, 2.0}, r.getTicksValues(),
                          "mutating the returned array must not corrupt internal state");
    }

    @Test
    public void densityAndDistanceAreEchoedBack() {
        RulerDrawingResult r = make(Collections.<Double>emptyList(), Collections.<Double>emptyList(),
                                    7, 3.5, new Vector3d(0, 1, 0));
        assertEquals(7, r.getSubTicksDensity());
        assertEquals(3.5, r.getMaxDistToTicksDirNorm());
    }

    @Test
    public void formatIsTheSameInstancePassedIn() {
        DecimalFormat fmt = new DecimalFormat("00.0");
        RulerDrawingResult r = new RulerDrawingResult(fmt, Collections.<Double>emptyList(),
                Collections.<Double>emptyList(), 0, 0.0, new Vector3d(1, 0, 0));
        assertSame(fmt, r.getFormat());
    }

    @Test
    public void directionIsStoredAsAnIndependentCopy() {
        Vector3d dir = new Vector3d(0, 0, 1);
        RulerDrawingResult r = make(Collections.<Double>emptyList(), Collections.<Double>emptyList(),
                                    0, 0.0, dir);
        Vector3d stored = r.getNormalizedTicksDirection();
        assertTrue(dir.equals(stored), "value must match the constructor argument");
        assertNotSame(dir, stored, "the constructor copies the direction defensively");
        // The stored copy is stable across calls.
        assertSame(stored, r.getNormalizedTicksDirection());
    }

    @Test
    public void emptyTickListsYieldEmptyArrays() {
        RulerDrawingResult r = make(Collections.<Double>emptyList(), Collections.<Double>emptyList(),
                                    0, 0.0, new Vector3d(1, 0, 0));
        assertEquals(0, r.getTicksValues().length);
        assertEquals(0, r.getSubTicksValues().length);
    }
}
