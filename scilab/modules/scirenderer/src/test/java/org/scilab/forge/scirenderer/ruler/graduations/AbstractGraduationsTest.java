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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for the interval / formatting / sub-graduation logic implemented in
 * {@link AbstractGraduations}. Exercised through a minimal in-test concrete subclass so the
 * base-class behavior is pinned directly, independent of the Linear/Logarithmic subclasses.
 */
public class AbstractGraduationsTest {

    /** Minimal concrete graduation: only {@code getAllValues()} carries data. */
    private static final class TestGraduations extends AbstractGraduations {
        private final List<Double> all;

        TestGraduations(double lo, double hi, List<Double> all) {
            super(lo, hi);
            this.all = new ArrayList<>(all);
        }

        TestGraduations(double lo, boolean loIncluded, double hi, boolean hiIncluded) {
            super(lo, loIncluded, hi, hiIncluded);
            this.all = new ArrayList<>();
        }

        TestGraduations(Graduations parent) {
            super(parent);
            this.all = new ArrayList<>();
        }

        @Override
        public List<Double> getAllValues() {
            // Fresh copy each call: AbstractGraduations sorts this list in place.
            return new ArrayList<>(all);
        }

        @Override
        public List<Double> getNewValues() {
            return getAllValues();
        }

        @Override
        public Graduations getMore() {
            return null;
        }

        @Override
        public Graduations getAlternative() {
            return null;
        }

        @Override
        public Graduations getSubGraduations() {
            return null;
        }

        @Override
        public int getSubDensity() {
            return 0;
        }
    }

    private static TestGraduations closed(double lo, double hi) {
        return new TestGraduations(lo, hi, new ArrayList<>());
    }

    @Test
    public void boundGettersEchoTheConstructor() {
        TestGraduations g = new TestGraduations(-2.0, false, 7.0, true);
        assertEquals(-2.0, g.getLowerBound());
        assertEquals(7.0, g.getUpperBound());
        assertFalse(g.isLowerBoundIncluded());
        assertTrue(g.isUpperBoundIncluded());
    }

    @Test
    public void twoArgConstructorIncludesBothBounds() {
        TestGraduations g = closed(0.0, 10.0);
        assertTrue(g.isLowerBoundIncluded());
        assertTrue(g.isUpperBoundIncluded());
    }

    @Test
    public void containHonorsBoundInclusion() {
        TestGraduations closed = closed(0.0, 10.0);
        assertTrue(closed.contain(0.0));
        assertTrue(closed.contain(10.0));
        assertTrue(closed.contain(5.0));
        assertFalse(closed.contain(-0.001));
        assertFalse(closed.contain(10.001));

        TestGraduations open = new TestGraduations(0.0, false, 10.0, false);
        assertFalse(open.contain(0.0));
        assertFalse(open.contain(10.0));
        assertTrue(open.contain(5.0));
    }

    @Test
    public void containRelativeMirrorsContainOnTheShiftedInterval() {
        TestGraduations closed = closed(0.0, 10.0);
        assertTrue(closed.contain(0.0));
        assertTrue(closed.containRelative(0.0));
        assertTrue(closed.containRelative(10.0));
        assertTrue(closed.containRelative(5.0));
        assertFalse(closed.containRelative(-1.0));
        assertFalse(closed.containRelative(11.0));

        TestGraduations open = new TestGraduations(0.0, false, 10.0, false);
        assertFalse(open.containRelative(0.0));
        assertFalse(open.containRelative(10.0));
        assertTrue(open.containRelative(5.0));
    }

    @Test
    public void formatSelectsScientificPatternForVerySmallMagnitudes() {
        DecimalFormat expected = new DecimalFormat("0.##########E00");
        assertEquals(expected.toPattern(), closed(0.0, 1e-4).getFormat().toPattern());
    }

    @Test
    public void formatSelectsScientificPatternForVeryLargeMagnitudes() {
        DecimalFormat expected = new DecimalFormat("0.##########E00");
        assertEquals(expected.toPattern(), closed(0.0, 2e6).getFormat().toPattern());
    }

    @Test
    public void formatSelectsSixDecimalPatternForSubUnitMagnitudes() {
        DecimalFormat expected = new DecimalFormat("0.######");
        assertEquals(expected.toPattern(), closed(0.0, 0.5).getFormat().toPattern());
    }

    @Test
    public void formatSelectsGroupedPatternForMidMagnitudes() {
        DecimalFormat expected = new DecimalFormat("#,##0.####");
        assertEquals(expected.toPattern(), closed(0.0, 100.0).getFormat().toPattern());
    }

    @Test
    public void formatUsesALowercaseExponentSeparator() {
        String s = closed(0.0, 2e6).getFormat().format(2000000.0);
        assertTrue(s.contains("e"), "exponent separator overridden to lowercase 'e', got " + s);
        assertFalse(s.contains("E"), "the default uppercase 'E' must be gone, got " + s);
    }

    @Test
    public void formatIsMemoized() {
        TestGraduations g = closed(0.0, 100.0);
        DecimalFormat first = g.getFormat();
        assertSame(first, g.getFormat());
    }

    @Test
    public void subGraduationsInterpolateBetweenTicks() {
        TestGraduations g = new TestGraduations(0.0, 10.0, Arrays.asList(0.0, 10.0));
        List<Double> sub = g.getSubGraduations(1);
        assertEquals(Arrays.asList(0.0, 5.0, 10.0), sub);
    }

    @Test
    public void subGraduationsWithZeroDensityIsEmpty() {
        TestGraduations g = new TestGraduations(0.0, 10.0, Arrays.asList(0.0, 10.0));
        assertTrue(g.getSubGraduations(0).isEmpty());
    }

    @Test
    public void subGraduationsAreMemoizedIgnoringLaterDensityArguments() {
        // Defect characterization: the result is cached on first call and the cached list is
        // returned for every later call, even when a different density N is requested.
        TestGraduations g = new TestGraduations(0.0, 10.0, Arrays.asList(0.0, 10.0));
        List<Double> firstN = g.getSubGraduations(1);
        List<Double> secondN = g.getSubGraduations(5);
        assertSame(firstN, secondN);
        assertEquals(Arrays.asList(0.0, 5.0, 10.0), secondN);
    }

    @Test
    public void toStringUsesSquareBracketsForIncludedBounds() {
        String s = closed(0.0, 10.0).toString();
        assertTrue(s.startsWith("TestGraduations["), s);
        assertTrue(s.endsWith("]"), s);
        assertTrue(s.contains(", "), s);
    }

    @Test
    public void toStringReversesBracketsForOpenBounds() {
        // An open interval prints reversed brackets: ]lo, hi[
        String s = new TestGraduations(0.0, false, 10.0, false).toString();
        assertTrue(s.startsWith("TestGraduations]"), s);
        assertTrue(s.endsWith("["), s);
    }

    @Test
    public void parentCopyingConstructorInheritsBoundsAndLinksParent() {
        TestGraduations parent = new TestGraduations(-5.0, false, 5.0, true);
        TestGraduations child = new TestGraduations(parent);

        assertEquals(-5.0, child.getLowerBound());
        assertEquals(5.0, child.getUpperBound());
        assertFalse(child.isLowerBoundIncluded());
        assertTrue(child.isUpperBoundIncluded());
        assertSame(parent, child.getParentGraduations());
    }

    @Test
    public void rootGraduationHasNoParent() {
        assertSame(null, closed(0.0, 1.0).getParentGraduations());
    }
}
