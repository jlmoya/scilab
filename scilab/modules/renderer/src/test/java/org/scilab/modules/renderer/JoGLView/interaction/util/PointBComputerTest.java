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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.tranformations.Vector3d;
import org.scilab.modules.graphic_objects.axes.Axes;

/**
 * Hermetic unit tests for {@link PointBComputer}. It refines a first
 * {@link PointComputer} into a second box-surface point. The first computer
 * is supplied here as a small deterministic stub, and the axes is a fresh
 * {@link Axes} (unit corrected bounds, zoom disabled), so no live projection
 * is involved.
 *
 * <p>Two structurally different constructor paths are covered:
 * <ul>
 *   <li>an <em>invalid</em> first computer (axis index -1) short-circuits to a
 *       null/invalid result;</li>
 *   <li>a <em>valid</em> first computer drives the interpolation path. Without
 *       a live projection the interpolation basis is degenerate, which the
 *       {@code check2D} test resolves to a 2D zoom whose endpoints clamp to the
 *       box edges - the documented behaviour asserted below.</li>
 * </ul>
 */
class PointBComputerTest {

    private static final double EPS = 1e-12;

    /** Deterministic first-point computer. */
    private static final class StubPointComputer implements PointComputer {
        private final int axisIndex;
        StubPointComputer(int axisIndex) {
            this.axisIndex = axisIndex;
        }
        @Override public boolean isValid() {
            return axisIndex != -1;
        }
        @Override public Vector3d getFirstPosition() {
            return new Vector3d(0.5, 0.0, 0.0);
        }
        @Override public Vector3d getSecondPosition() {
            return new Vector3d(0.5, 0.0, 0.0);
        }
        @Override public int getFirstAxisIndex() {
            return axisIndex;
        }
        @Override public boolean is2D() {
            return false;
        }
    }

    @Test
    void anInvalidFirstComputerProducesAnInvalidResult() {
        PointBComputer b = new PointBComputer(new Axes(), new StubPointComputer(-1), new Point(10, 20));
        assertFalse(b.isValid(), "a -1 first-axis index means no point B");
        assertFalse(b.is2D());
        assertEquals(-1, b.getFirstAxisIndex());
        assertNull(b.getFirstPosition());
        assertNull(b.getSecondPosition());
    }

    @Test
    void aValidFirstComputerPropagatesTheFirstAxisIndex() {
        PointBComputer b = new PointBComputer(new Axes(), new StubPointComputer(0), new Point(10, 20));
        assertEquals(0, b.getFirstAxisIndex(), "the first axis index comes from point A");
    }

    @Test
    void aValidFirstComputerYieldsBoxEdgeEndpointsInTheDegenerate2DCase() {
        PointBComputer b = new PointBComputer(new Axes(), new StubPointComputer(0), new Point(10, 20));

        assertTrue(b.isValid(), "a valid first computer yields a non-null second position");
        assertTrue(b.is2D(), "the degenerate projection resolves to a 2D zoom");

        // In the 2D branch the first/second positions are pushed to the
        // opposite edges of the box along the driving axis and clamped.
        assertEquals(-1.0, b.getFirstPosition().getData()[0], EPS);
        assertEquals(1.0, b.getSecondPosition().getData()[0], EPS);
    }
}
