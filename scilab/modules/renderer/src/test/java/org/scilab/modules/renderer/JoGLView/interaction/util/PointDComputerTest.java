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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.modules.graphic_objects.axes.Axes;

/**
 * Hermetic unit tests for {@link PointDComputer}, the fourth and final
 * box-surface point of the rubber-box interaction. Unlike point C, point D
 * <em>passes the previous first position straight through</em> and only
 * refines the second position along the driving axis - the invariant asserted
 * below. The previous computer is a deterministic stub and the axes is a fresh
 * {@link Axes} (degenerate projection, see {@link AbstractPointComputerTest}).
 */
class PointDComputerTest {

    private static final double EPS = 1e-12;

    /** Deterministic previous-point computer. */
    private static final class StubPointComputer implements PointComputer {
        private final int axisIndex;
        private final Vector3d first;
        private final Vector3d second;
        StubPointComputer(int axisIndex, Vector3d first, Vector3d second) {
            this.axisIndex = axisIndex;
            this.first = first;
            this.second = second;
        }
        @Override public boolean isValid() {
            return axisIndex != -1;
        }
        @Override public Vector3d getFirstPosition() {
            return first;
        }
        @Override public Vector3d getSecondPosition() {
            return second;
        }
        @Override public int getFirstAxisIndex() {
            return axisIndex;
        }
        @Override public boolean is2D() {
            return false;
        }
    }

    private static PointDComputer make(int axisIndex, Vector3d first, Vector3d second) {
        return new PointDComputer(new Axes(), new StubPointComputer(axisIndex, first, second), new Point(12, 44));
    }

    @Test
    void propagatesTheFirstAxisIndexFromThePreviousComputer() {
        for (int k = 0; k < 3; k++) {
            PointDComputer d = make(k, new Vector3d(0.1, 0.2, 0.3), new Vector3d(-0.4, -0.5, -0.6));
            assertEquals(k, d.getFirstAxisIndex());
        }
    }

    @Test
    void isNeverConsideredA2DZoom() {
        assertFalse(make(2, new Vector3d(0.1, 0.2, 0.3), new Vector3d(-0.4, -0.5, -0.6)).is2D());
    }

    @Test
    void validityMirrorsTheSecondPositionAndBothPositionsAreSetTogether() {
        PointDComputer d = make(1, new Vector3d(0.1, 0.2, 0.3), new Vector3d(-0.4, -0.5, -0.6));
        assertEquals(d.getSecondPosition() != null, d.isValid());
        assertEquals(d.getFirstPosition() == null, d.getSecondPosition() == null);
    }

    @Test
    void firstPositionIsPassedThroughUntouchedWhileSecondIsRefined() {
        // Point D keeps the previous first position by reference and only
        // refines the second position; the second's non-driving components
        // (axes 0 and 2 when driving axis 1) are preserved. The driving
        // component is left to the (here degenerate) projection.
        Vector3d a = new Vector3d(0.1, 0.2, 0.3);
        Vector3d b = new Vector3d(-0.4, -0.5, -0.6);
        PointDComputer d = make(1, a, b);

        if (d.getFirstPosition() != null) {
            assertSame(a, d.getFirstPosition(), "point D reuses the previous first position");
            double[] second = d.getSecondPosition().getData();
            assertEquals(-0.4, second[0], EPS, "non-driving component 0 of the second point is preserved");
            assertEquals(-0.6, second[2], EPS, "non-driving component 2 of the second point is preserved");
        }
    }

    @Test
    void aMinusOnePreviousIndexThrowsBecauseTheDrivingAxisIsNotGuarded() {
        // Defect characterization mirroring PointCComputer: refining the second
        // position calls setCoordinate(..., firstAxisIndex) with the previous
        // index verbatim, so a -1 index indexes data[-1] and throws.
        assertThrows(ArrayIndexOutOfBoundsException.class,
                     () -> make(-1, new Vector3d(0.1, 0.2, 0.3), new Vector3d(-0.4, -0.5, -0.6)));
    }

    @Test
    void freshAxesStillResolvesAClickedPosition() {
        PointDComputer d = make(0, new Vector3d(0.1, 0.2, 0.3), new Vector3d(-0.4, -0.5, -0.6));
        assertTrue(d.isValid(), "a fresh axes resolves a box-surface position");
    }
}
