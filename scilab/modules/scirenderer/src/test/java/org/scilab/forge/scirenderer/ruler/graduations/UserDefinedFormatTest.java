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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Hermetic unit tests for {@link UserDefinedFormat}, a DecimalFormat that renders
 * ticks through a printf-style pattern with an affine (scale, translate) rescaling
 * and falls back to a delegate DecimalFormat.
 *
 * <p>The custom formatting lives in the overridden
 * {@code format(double, StringBuffer, FieldPosition)}; those tests drive it directly.
 */
public class UserDefinedFormatTest {

    private static DecimalFormat fallback() {
        return new DecimalFormat("0.00");
    }

    /** Invoke the overridden three-argument format, which carries the custom logic. */
    private static String fmt(UserDefinedFormat f, double value) {
        return f.format(value, new StringBuffer(), new FieldPosition(0)).toString();
    }

    @Test
    public void printfPatternIsApplied() {
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%.2f", 1.0, 0.0);
        assertEquals("3.14", fmt(f, 3.14159));
    }

    @Test
    public void getFormatReturnsThePattern() {
        assertEquals("%.2f", new UserDefinedFormat(fallback(), "%.2f", 1.0, 0.0).getFormat());
    }

    @Test
    public void scaleIsAppliedBeforeFormatting() {
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%.1f", 100.0, 0.0);
        assertEquals("50.0", fmt(f, 0.5));
    }

    @Test
    public void translationIsSubtractedBeforeScaling() {
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%.1f", 1.0, 10.0);
        // 1 * (15 - 10) = 5
        assertEquals("5.0", fmt(f, 15.0));
    }

    @Test
    public void integerConversionFallsBackToTruncatedLong() {
        // "%d" on a double throws IllegalFormatConversionException; the class retries with (long) d.
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%d", 1.0, 0.0);
        assertEquals("42", fmt(f, 42.7));
    }

    @Test
    public void nullPatternDelegatesToFallbackWithTheOriginalNumber() {
        UserDefinedFormat f = new UserDefinedFormat(fallback(), null, 5.0, 99.0);
        // Fallback ignores scale/translate and formats the original number.
        assertEquals(fallback().format(3.14159), fmt(f, 3.14159));
    }

    @Test
    public void emptyPatternDelegatesToFallback() {
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "", 1.0, 0.0);
        assertEquals(fallback().format(7.5), fmt(f, 7.5));
    }

    @Test
    public void unsupportedConversionDelegatesToFallback() {
        // "%q" is not a valid conversion; the generic catch routes to the fallback.
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%q", 1.0, 0.0);
        assertEquals(fallback().format(2.0), fmt(f, 2.0));
    }

    @Test
    public void singleArgumentFormatBypassesTheCustomPattern() {
        // Defect characterization: the convenience NumberFormat.format(double) entry point
        // does NOT route through the overridden three-arg method, so it silently ignores the
        // user pattern / scale / translate and emits the inherited default DecimalFormat
        // rendering. Only the three-arg format() honors the custom pattern.
        UserDefinedFormat f = new UserDefinedFormat(fallback(), "%.2f", 1.0, 0.0);
        assertEquals(new DecimalFormat().format(3.14159), f.format(3.14159));
        assertNotEquals("3.14", f.format(3.14159));
    }
}
