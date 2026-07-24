/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab Enterprises
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.graphic_objects.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic unit tests for {@link Utils}: pure static validity predicates
 * on scalar values and points, including logarithmic-scale checks.
 */
public class UtilsTest {

    @Test
    public void scalarIsValidForFiniteNumbers() {
        assertTrue(Utils.isValid(0.0));
        assertTrue(Utils.isValid(-42.5));
        assertTrue(Utils.isValid(Double.MAX_VALUE));
        assertTrue(Utils.isValid(Double.MIN_VALUE));
    }

    @Test
    public void scalarIsInvalidForNaNAndInfinities() {
        assertFalse(Utils.isValid(Double.NaN));
        assertFalse(Utils.isValid(Double.POSITIVE_INFINITY));
        assertFalse(Utils.isValid(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void pointIsValidWhenAllCoordinatesFinite() {
        assertTrue(Utils.isValid(1.0, 2.0, 3.0));
        assertTrue(Utils.isValid(0.0, 0.0, 0.0));
    }

    @Test
    public void pointIsInvalidWhenAnyCoordinateIsNotFinite() {
        assertFalse(Utils.isValid(Double.NaN, 2.0, 3.0));
        assertFalse(Utils.isValid(1.0, Double.NaN, 3.0));
        assertFalse(Utils.isValid(1.0, 2.0, Double.NaN));
        assertFalse(Utils.isValid(Double.POSITIVE_INFINITY, 2.0, 3.0));
        assertFalse(Utils.isValid(1.0, Double.NEGATIVE_INFINITY, 3.0));
        assertFalse(Utils.isValid(1.0, 2.0, Double.POSITIVE_INFINITY));
    }

    @Test
    public void logValidSingleRequiresStrictlyPositive() {
        assertTrue(Utils.isLogValid(0.0001));
        assertTrue(Utils.isLogValid(1000.0));
        assertFalse(Utils.isLogValid(0.0));
        assertFalse(Utils.isLogValid(-1.0));
    }

    @Test
    public void logValidWithZeroMaskAlwaysTrue() {
        // No axis flagged as logarithmic -> nothing is checked.
        assertTrue(Utils.isLogValid(-1.0, -2.0, -3.0, 0x0));
    }

    @Test
    public void logValidChecksOnlyMaskedCoordinates() {
        // Mask 0x1 -> only x must be > 0.
        assertTrue(Utils.isLogValid(1.0, -5.0, -5.0, 0x1));
        assertFalse(Utils.isLogValid(-1.0, 5.0, 5.0, 0x1));

        // Mask 0x2 -> only y must be > 0.
        assertTrue(Utils.isLogValid(-5.0, 1.0, -5.0, 0x2));
        assertFalse(Utils.isLogValid(5.0, -1.0, 5.0, 0x2));

        // Mask 0x4 -> only z must be > 0.
        assertTrue(Utils.isLogValid(-5.0, -5.0, 1.0, 0x4));
        assertFalse(Utils.isLogValid(5.0, 5.0, -1.0, 0x4));
    }

    @Test
    public void logValidWithFullMaskRequiresAllPositive() {
        assertTrue(Utils.isLogValid(1.0, 2.0, 3.0, 0x7));
        assertFalse(Utils.isLogValid(1.0, 2.0, 0.0, 0x7));
        assertFalse(Utils.isLogValid(0.0, 2.0, 3.0, 0x7));
    }

    @Test
    public void logValidCombinedMaskIgnoresUnflaggedNegative() {
        // x and z flagged (0x5); y is not checked even though negative.
        assertTrue(Utils.isLogValid(1.0, -100.0, 2.0, 0x5));
        assertFalse(Utils.isLogValid(1.0, -100.0, -2.0, 0x5));
    }
}
