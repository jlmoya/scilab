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

import java.text.DecimalFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link LinearGraduations} (and, through it, the shared
 * behavior of {@link AbstractGraduations}).
 */
public class LinearGraduationsTest {

    @Test
    public void createNormalizesBoundOrder() {
        LinearGraduations g = LinearGraduations.create(10, 0);
        assertEquals(0.0, g.getLowerBound(), 0.0);
        assertEquals(10.0, g.getUpperBound(), 0.0);
    }

    @Test
    public void boundsAreIncludedByDefault() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        assertTrue(g.isLowerBoundIncluded());
        assertTrue(g.isUpperBoundIncluded());
    }

    @Test
    public void allValuesForZeroToTenAreTheEndpoints() {
        List<Double> values = LinearGraduations.create(0, 10).getAllValues();
        assertEquals(List.of(0.0, 10.0), values);
    }

    @Test
    public void rootNewValuesEqualAllValues() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        assertEquals(g.getAllValues(), g.getNewValues());
    }

    @Test
    public void subGraduationsInterpolateEvenly() {
        // One sub-tick between each pair splits [0,10] at its midpoint.
        List<Double> sub = LinearGraduations.create(0, 10).getSubGraduations(1);
        assertEquals(List.of(0.0, 5.0, 10.0), sub);
    }

    @Test
    public void containRespectsBounds() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        assertTrue(g.contain(0));
        assertTrue(g.contain(5));
        assertTrue(g.contain(10));
        assertFalse(g.contain(-0.1));
        assertFalse(g.contain(10.1));
    }

    @Test
    public void containRelativeIsShiftedToZeroBased() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        assertTrue(g.containRelative(0));
        assertTrue(g.containRelative(5));
        assertTrue(g.containRelative(10));
        assertFalse(g.containRelative(-1));
        assertFalse(g.containRelative(11));
    }

    @Test
    public void subDensityIsTwoForMantissaOne() {
        assertEquals(2, LinearGraduations.create(0, 10).getSubDensity());
    }

    @Test
    public void getMoreCarriesTheParentAndGetAlternativeIsNull() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        LinearGraduations more = g.getMore();
        assertNotNull(more);
        assertSame(g, more.getParentGraduations());
        assertNotNull(g.getSubGraduations());
        // A mantissa-1 graduation has no alternative.
        assertNull(g.getAlternative());
        assertNull(g.getParentGraduations(), "a root graduation has no parent");
    }

    @Test
    public void getFormatIsCachedAndIsADecimalFormat() {
        LinearGraduations g = LinearGraduations.create(0, 10);
        DecimalFormat f = g.getFormat();
        assertNotNull(f);
        assertSame(f, g.getFormat());
    }

    @Test
    public void toStringExposesStepAndParent() {
        String s = LinearGraduations.create(0, 10).toString();
        assertTrue(s.startsWith("LinearGraduations"), s);
        assertTrue(s.contains("stepMantissa=1"), s);
        assertTrue(s.contains("stepExponent=1"), s);
        assertTrue(s.contains("parent=null"), s);
    }
}
