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

package org.scilab.modules.renderer.JoGLView.interaction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.modules.graphic_objects.axes.Axes;

/**
 * Hermetic unit tests for the pure {@code protected} geometry helpers of
 * {@link AbstractPointComputer}: bounds testing, clamping, coordinate
 * substitution and the lambda/coordinate interpolation.
 *
 * <p>The constructor unprojects the clicked point through
 * {@code AxesDrawer.unProject}. With no live {@code DrawerVisitor} registered
 * for the axes' figure (the hermetic case) that call returns the origin for
 * both the near and far points, so the min/far interpolation basis collapses
 * to a point. Tests that depend on the basis ({@code computeLambda}) therefore
 * assert the documented <em>degenerate</em> (non-finite) outcome, while the
 * bounds/clamp helpers - which read only the axes' corrected bounds and the
 * argument vector - are asserted against their true behaviour. A fresh
 * {@link Axes} has zoom disabled, so its corrected bounds are the unit box
 * [-1, +1] on every axis.
 */
class AbstractPointComputerTest {

    private static final double EPS = 1e-12;

    /** Concrete probe exposing the abstract base's protected helpers. */
    private static final class Probe extends AbstractPointComputer {
        Probe(Axes axes, Point point) {
            super(axes, point);
        }

        double lambda(double value, int axisIndex) {
            return computeLambda(value, axisIndex);
        }
        Vector3d coordinate(double lambda, double value, int axisIndex) {
            return computeCoordinate(lambda, value, axisIndex);
        }
        boolean bounds(Vector3d v) {
            return inBounds(v);
        }
        boolean valid(Vector3d v) {
            return isValid(v);
        }
        Vector3d clampTo(Vector3d v) {
            return clamp(v);
        }
        Vector3d setCoord(Vector3d v1, Vector3d v2, int i) {
            return setCoordinate(v1, v2, i);
        }
        Axes axes() {
            return getAxes();
        }

        // PointComputer contract - unused by these tests.
        @Override public boolean isValid() {
            return true;
        }
        @Override public Vector3d getFirstPosition() {
            return null;
        }
        @Override public Vector3d getSecondPosition() {
            return null;
        }
        @Override public int getFirstAxisIndex() {
            return -1;
        }
        @Override public boolean is2D() {
            return false;
        }
    }

    private static Probe newProbe() {
        return new Probe(new Axes(), new Point(40, 30));
    }

    @Test
    void constructorRetainsTheSuppliedAxes() {
        Axes axes = new Axes();
        Probe probe = new Probe(axes, new Point(1, 2));
        assertSame(axes, probe.axes());
    }

    @Test
    void isValidAcceptsFiniteVectorsAndRejectsNaNOrInfinity() {
        Probe p = newProbe();
        assertTrue(p.valid(new Vector3d(1.0, 2.0, 3.0)));
        assertTrue(p.valid(new Vector3d(0.0, 0.0, 0.0)));
        assertFalse(p.valid(new Vector3d(Double.NaN, 0.0, 0.0)));
        assertFalse(p.valid(new Vector3d(0.0, Double.POSITIVE_INFINITY, 0.0)));
        assertFalse(p.valid(new Vector3d(0.0, 0.0, Double.NEGATIVE_INFINITY)));
    }

    @Test
    void inBoundsUsesTheUnitCorrectedBoxOfAFreshAxes() {
        Probe p = newProbe();
        assertTrue(p.bounds(new Vector3d(0.0, 0.0, 0.0)), "centre is inside");
        assertTrue(p.bounds(new Vector3d(1.0, 1.0, 1.0)), "the +1 corner is inclusive");
        assertTrue(p.bounds(new Vector3d(-1.0, -1.0, -1.0)), "the -1 corner is inclusive");
        assertFalse(p.bounds(new Vector3d(1.5, 0.0, 0.0)), "beyond +1 on x is outside");
        assertFalse(p.bounds(new Vector3d(0.0, -2.0, 0.0)), "below -1 on y is outside");
    }

    @Test
    void clampConstrainsEachComponentToTheUnitBox() {
        Probe p = newProbe();
        double[] clamped = p.clampTo(new Vector3d(2.0, -3.0, 0.5)).getData();
        assertEquals(1.0, clamped[0], EPS, "x clamped up to the upper bound");
        assertEquals(-1.0, clamped[1], EPS, "y clamped down to the lower bound");
        assertEquals(0.5, clamped[2], EPS, "z already inside is untouched");
    }

    @Test
    void clampLeavesAnInteriorPointUnchanged() {
        Probe p = newProbe();
        double[] d = p.clampTo(new Vector3d(0.25, -0.5, 0.75)).getData();
        assertEquals(0.25, d[0], EPS);
        assertEquals(-0.5, d[1], EPS);
        assertEquals(0.75, d[2], EPS);
    }

    @Test
    void setCoordinateReplacesOneComponentThenClamps() {
        Probe p = newProbe();
        // Overwrite y with an out-of-range value: it must be clamped to +1.
        double[] d = p.setCoord(new Vector3d(0.0, 0.0, 0.0), new Vector3d(5.0, 9.0, 9.0), 1).getData();
        assertEquals(0.0, d[0], EPS);
        assertEquals(1.0, d[1], EPS, "the copied component is clamped into the box");
        assertEquals(0.0, d[2], EPS);
    }

    @Test
    void computeCoordinateAlwaysForcesTheTargetAxisToTheGivenValue() {
        // The interpolation basis is degenerate here, but the documented
        // invariant "result[axisIndex] == value" must still hold, and the
        // other components stay at the collapsed-basis origin.
        Probe p = newProbe();
        double[] a = p.coordinate(0.5, 7.0, 1).getData();
        assertEquals(7.0, a[1], EPS);
        assertEquals(0.0, a[0], EPS);
        assertEquals(0.0, a[2], EPS);

        double[] b = p.coordinate(0.3, -4.0, 2).getData();
        assertEquals(-4.0, b[2], EPS);
    }

    @Test
    void computeLambdaIsNonFiniteWhenNoProjectionIsRegistered() {
        // min == max == origin, so (value - v2) / (v1 - v2) divides by zero:
        // a finite numerator gives +/-Infinity, a zero numerator gives NaN.
        Probe p = newProbe();
        assertTrue(Double.isInfinite(p.lambda(5.0, 0)), "positive value -> +Infinity");
        assertTrue(p.lambda(5.0, 0) > 0);
        assertTrue(Double.isInfinite(p.lambda(-5.0, 1)), "negative value -> -Infinity");
        assertTrue(p.lambda(-5.0, 1) < 0);
        assertTrue(Double.isNaN(p.lambda(0.0, 2)), "zero value -> 0/0 -> NaN");
    }
}
