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
import java.text.FieldPosition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link TinyIntervalFormat}, which renders values in a tiny
 * interval as "base +/- frac" (e.g. 3.0001 as "3+0.0001") so nearby ticks stay distinct.
 *
 * <p>The custom rendering lives in the overridden three-argument {@code format}; the
 * tests drive it directly (the convenience single-arg entry can take a DecimalFormat
 * fast path that bypasses the override).
 */
public class TinyIntervalFormatTest {

    private static final String PATTERN = "0.######";

    private static TinyIntervalFormat format() {
        return new TinyIntervalFormat(PATTERN, PATTERN);
    }

    private static String fmt(TinyIntervalFormat f, double value) {
        return f.format(value, new StringBuffer(), new FieldPosition(0)).toString();
    }

    @Test
    public void wholeNumbersUseThePlainBasePattern() {
        // No fractional part => delegates to the base DecimalFormat pattern.
        assertEquals(new DecimalFormat(PATTERN).format(3.0), fmt(format(), 3.0));
        assertEquals(new DecimalFormat(PATTERN).format(5.0), fmt(format(), 5.0));
    }

    @Test
    public void slightlyAboveAnIntegerGetsAPlusFraction() {
        String s = fmt(format(), 3.0001);
        assertTrue(s.startsWith("3"), s);
        assertTrue(s.contains("+"), s);
    }

    @Test
    public void slightlyBelowAnIntegerGetsAMinusFraction() {
        String s = fmt(format(), 2.9999);
        assertTrue(s.startsWith("3"), s);
        assertTrue(s.contains("-"), s);
    }

    @Test
    public void largeBaseStillSplitsIntoBasePlusFraction() {
        String s = fmt(format(), 12.0001);
        assertTrue(s.contains("12"), s);
        assertTrue(s.contains("+"), s);
    }
}
