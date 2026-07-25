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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.modules.graphic_objects.axes.Axes;

/**
 * Hermetic unit tests for {@link PointCComputer}, the third box-surface point
 * of the rubber-box interaction. It refines a previous {@link PointComputer}
 * (here a deterministic stub) using the position resolved by its
 * {@link CubeFacesPointComputer} base against a fresh {@link Axes}.
 *
 * <p>No live {@code DrawerVisitor} is registered for the axes' figure, so the
 * un-projection collapses (see {@link AbstractPointComputerTest}); the tests
 * therefore assert only the <em>structural</em> contract that holds regardless
 * of the projection basis:
 * <ul>
 *   <li>the first-axis index is propagated verbatim from the previous computer;</li>
 *   <li>the result is never 2D;</li>
 *   <li>{@code isValid()} is exactly {@code getSecondPosition() != null};</li>
 *   <li>the two positions are set together (both null or both non-null);</li>
 *   <li>when resolved, {@code setCoordinate} keeps the non-driving components of
 *       the previous positions and clamps everything into the corrected box.</li>
 * </ul>
 */
class PointCComputerTest {

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

    private static PointCComputer make(int axisIndex, Vector3d first, Vector3d second) {
        return new PointCComputer(new Axes(), new StubPointComputer(axisIndex, first, second), new Point(37, 21));
    }

    @Test
    void propagatesTheFirstAxisIndexFromThePreviousComputer() {
        for (int k = 0; k < 3; k++) {
            PointCComputer c = make(k, new Vector3d(0.2, 0.3, 0.4), new Vector3d(-0.5, -0.6, -0.7));
            assertEquals(k, c.getFirstAxisIndex(), "axis index must come from point B");
        }
    }

    @Test
    void aMinusOnePreviousIndexThrowsBecauseTheDrivingAxisIsNotGuarded() {
        // Defect characterization: when the base resolves a clicked position,
        // the constructor calls setCoordinate(..., firstAxisIndex) with the
        // previous computer's index verbatim. A -1 index (an "invalid" previous
        // computer) indexes data[-1] and throws - there is no guard.
        assertThrows(ArrayIndexOutOfBoundsException.class,
                     () -> make(-1, new Vector3d(0.0, 0.0, 0.0), new Vector3d(0.0, 0.0, 0.0)));
    }

    @Test
    void isNeverConsideredA2DZoom() {
        assertFalse(make(0, new Vector3d(0.2, 0.3, 0.4), new Vector3d(-0.5, -0.6, -0.7)).is2D());
    }

    @Test
    void validityMirrorsTheSecondPositionAndBothPositionsAreSetTogether() {
        PointCComputer c = make(1, new Vector3d(0.2, 0.3, 0.4), new Vector3d(-0.5, -0.6, -0.7));
        assertEquals(c.getSecondPosition() != null, c.isValid(),
                     "isValid() is defined as secondPosition != null");
        assertEquals(c.getFirstPosition() == null, c.getSecondPosition() == null,
                     "first and second positions are populated together");
    }

    @Test
    void resolvedPositionsPreserveTheNonDrivingComponentsOfThePreviousPositions() {
        // Drive axis 1. setCoordinate only overwrites component 1 (with the
        // resolved click position) and clamps; the non-driving components (0
        // and 2) of the previous positions - already inside the corrected unit
        // box - survive unchanged. The driving component itself is left to the
        // (here degenerate) projection and is deliberately not asserted.
        Vector3d a = new Vector3d(0.2, 0.3, 0.4);
        Vector3d b = new Vector3d(-0.5, -0.6, -0.7);
        PointCComputer c = make(1, a, b);

        if (c.getFirstPosition() != null) {
            double[] first = c.getFirstPosition().getData();
            double[] second = c.getSecondPosition().getData();
            assertEquals(0.2, first[0], EPS, "component 0 of point A is preserved");
            assertEquals(0.4, first[2], EPS, "component 2 of point A is preserved");
            assertEquals(-0.5, second[0], EPS, "component 0 of point B is preserved");
            assertEquals(-0.7, second[2], EPS, "component 2 of point B is preserved");
        }
    }

    @Test
    void freshAxesStillResolvesAClickedPosition() {
        // Documents the degenerate-projection outcome: even without a live
        // renderer, CubeFacesPointComputer resolves a (clamped) box position,
        // so point C is valid.
        PointCComputer c = make(0, new Vector3d(0.2, 0.3, 0.4), new Vector3d(-0.5, -0.6, -0.7));
        assertTrue(c.isValid(), "a fresh axes resolves a box-surface position");
    }
}
