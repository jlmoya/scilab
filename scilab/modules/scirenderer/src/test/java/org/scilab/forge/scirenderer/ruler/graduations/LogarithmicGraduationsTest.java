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

package org.scilab.forge.scirenderer.ruler.graduations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link LogarithmicGraduations}.
 */
public class LogarithmicGraduationsTest {

    @Test
    public void createNormalizesBoundOrder() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1000, 1);
        assertEquals(1.0, g.getLowerBound(), 0.0);
        assertEquals(1000.0, g.getUpperBound(), 0.0);
    }

    @Test
    public void allValuesArePowersOfTen() {
        List<Double> values = LogarithmicGraduations.create(1, 1000).getAllValues();
        assertEquals(List.of(1.0, 10.0, 100.0, 1000.0), values);
    }

    @Test
    public void newValuesEqualAllValues() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1, 1000);
        assertEquals(g.getAllValues(), g.getNewValues());
    }

    @Test
    public void containRespectsBounds() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1, 1000);
        assertTrue(g.contain(1));
        assertTrue(g.contain(500));
        assertTrue(g.contain(1000));
        assertFalse(g.contain(0.5));
        assertFalse(g.contain(2000));
    }

    @Test
    public void rootHasNoParent() {
        assertNull(LogarithmicGraduations.create(1, 1000).getParentGraduations());
    }

    @Test
    public void toStringExposesStepAndParent() {
        String s = LogarithmicGraduations.create(1, 1000).toString();
        assertTrue(s.startsWith("LogarithmicGraduations"), s);
        assertTrue(s.contains("stepExponent=1"), s);
        assertTrue(s.contains("parent=null"), s);
    }

    // ----- bound edge cases in getAllValues -----

    @Test
    public void allValuesOnlyKeepsPowersOfTenInsideNonPowerBounds() {
        // 2 and 500 are not powers of ten, so only 10 and 100 fall strictly inside.
        assertEquals(List.of(10.0, 100.0), LogarithmicGraduations.create(2, 500).getAllValues());
    }

    @Test
    public void excludedLowerPowerBoundIsSkipped() {
        // Lower bound 10 is excluded, so the first emitted tick jumps to 100.
        List<Double> values =
            LogarithmicGraduations.create(10, false, 1000, true).getAllValues();
        assertEquals(List.of(100.0, 1000.0), values);
    }

    @Test
    public void degeneratePointGraduationYieldsASingleValue() {
        LogarithmicGraduations g = LogarithmicGraduations.create(5, 5);
        assertEquals(List.of(5.0), g.getAllValues());
        assertTrue(g.toString().contains("stepExponent=0"), g.toString());
    }

    // ----- getSubGraduations(N): logarithmic interpolation between decade ticks -----

    @Test
    public void subGraduationsInterpolateGeometricallyBetweenDecades() {
        // Values 1, 10, 100, 1000 -> 3 gaps, each split once (N = 1) plus the last tick.
        List<Double> sub = LogarithmicGraduations.create(1, 1000).getSubGraduations(1);
        assertEquals(7, sub.size());
        assertEquals(1.0, sub.get(0), 1e-9);
        assertEquals(10.0, sub.get(2), 1e-9);
        assertEquals(1000.0, sub.get(6), 1e-9);
        // The interpolated point sits at 10^0.5, not the arithmetic midpoint 5.5.
        assertEquals(Math.sqrt(10.0), sub.get(1), 1e-9);
    }

    @Test
    public void zeroSubGraduationsGivesAnEmptyList() {
        assertTrue(LogarithmicGraduations.create(1, 1000).getSubGraduations(0).isEmpty());
    }

    // ----- getAlternative(): coarser logarithmic step -----

    @Test
    public void alternativeUsesAThousandFoldStep() {
        Graduations alternative = LogarithmicGraduations.create(1, 1000).getAlternative();
        assertNotNull(alternative);
        // step exponent 3 => factor 1000 => only the endpoints survive on [1, 1000].
        assertEquals(List.of(1.0, 1000.0), alternative.getAllValues());
        assertEquals(3, alternative.getSubDensity());
    }

    @Test
    public void alternativeIsCached() {
        LogarithmicGraduations g = LogarithmicGraduations.create(1, 1000);
        assertEquals(g.getAlternative(), g.getAlternative());
    }

    // ----- getMore()/getSubGraduations(): the LinLogGraduation inner class -----

    @Test
    public void moreGraduationKeepsTheDecadeTicks() {
        Graduations more = LogarithmicGraduations.create(1, 1000).getMore();
        assertNotNull(more);
        List<Double> values = more.getAllValues();
        // The LinLogGraduation carries every decade tick through from its logarithmic parent.
        assertTrue(values.containsAll(List.of(1.0, 10.0, 100.0, 1000.0)), values.toString());
        // A LinLogGraduation reports a zero sub-density.
        assertEquals(0, more.getSubDensity());
    }

    @Test
    public void moreGraduationExposesFinerLevelsButHasNoAlternative() {
        Graduations more = LogarithmicGraduations.create(1, 1000).getMore();
        assertNotNull(more.getSubGraduations());
        assertNotNull(more.getMore());
        // Every underlying linear graduation is mantissa-1, so there is no alternative.
        assertNull(more.getAlternative());
    }

    @Test
    public void rootSubDensityResolvesThroughTheLinLogChainToZero() {
        assertEquals(0, LogarithmicGraduations.create(1, 1000).getSubDensity());
        assertNotNull(LogarithmicGraduations.create(1, 1000).getSubGraduations());
    }
}
