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
package org.scilab.modules.xcos.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hermetic unit tests for {@link BlockPositioning}.
 *
 * <p><b>Scope &amp; native boundary.</b> Most of {@code BlockPositioning}'s API
 * ({@code update*PortsPosition}, {@code rotateAllPorts}, {@code toggleFlip},
 * {@code toggleMirror}, {@code toggleAntiClockwiseRotation}, ...) drives a live
 * {@code XcosDiagram}/{@code BasicBlock} model and constructs a
 * {@code JavaController}, all of which require the Scilab native runtime. Those
 * methods are out of scope here.</p>
 *
 * <p>What is covered is the pure, side-effect-free surface: the public rotation
 * constants and {@link BlockPositioning#roundAngle(int)}, which is plain integer
 * arithmetic. Merely calling {@code roundAngle} triggers class linking but not
 * the initialization of any native-backed collaborator, so the test stays
 * hermetic.</p>
 *
 * <p>{@code roundAngle} snaps an angle to the nearest lower/equal grid step using
 * midpoints 45/135/225/315. Several assertions below are <em>characterization</em>
 * tests: they document two current quirks -- (1) values in {@code [315,360]} are
 * left untouched (no step is &ge; them below 360), and (2) the normalization
 * {@code (angle + 360) % 360} only adds a single turn, so inputs below
 * {@code -360} are under-normalized and can collapse to 0.</p>
 */
public class BlockPositioningTest {

    private static final double EPS = 0.0;

    // ---------------------------------------------------------------------
    // public constants
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("ROTATION_STEP is a quarter turn and MAX_ROTATION a full turn")
    public void rotationConstants() {
        assertEquals(90, BlockPositioning.ROTATION_STEP);
        assertEquals(360, BlockPositioning.MAX_ROTATION);
        // The algorithm assumes exactly four grid steps in a full turn.
        assertEquals(4, BlockPositioning.MAX_ROTATION / BlockPositioning.ROTATION_STEP);
    }

    @Test
    @DisplayName("DEFAULT_GRIDSIZE is the positive Double.MIN_NORMAL sentinel")
    public void defaultGridSizeSentinel() {
        assertEquals(Double.MIN_NORMAL, BlockPositioning.DEFAULT_GRIDSIZE, EPS);
        assertTrue(BlockPositioning.DEFAULT_GRIDSIZE > 0.0, "grid size sentinel must be strictly positive");
        assertTrue(Double.isFinite(BlockPositioning.DEFAULT_GRIDSIZE));
    }

    // ---------------------------------------------------------------------
    // roundAngle() -- in-range rounding
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("exact grid multiples in [0,270] are returned unchanged")
    public void roundExactMultiples() {
        assertEquals(0, BlockPositioning.roundAngle(0));
        assertEquals(90, BlockPositioning.roundAngle(90));
        assertEquals(180, BlockPositioning.roundAngle(180));
        assertEquals(270, BlockPositioning.roundAngle(270));
    }

    @Test
    @DisplayName("values just below a midpoint round down to the lower step")
    public void roundBelowMidpointRoundsDown() {
        assertEquals(0, BlockPositioning.roundAngle(44));   // < 45
        assertEquals(90, BlockPositioning.roundAngle(134));  // < 135
        assertEquals(180, BlockPositioning.roundAngle(224)); // < 225
        assertEquals(270, BlockPositioning.roundAngle(314)); // < 315
    }

    @Test
    @DisplayName("values at or above a midpoint round up to the next step")
    public void roundAtOrAboveMidpointRoundsUp() {
        assertEquals(90, BlockPositioning.roundAngle(45));   // == midpoint 45
        assertEquals(90, BlockPositioning.roundAngle(89));
        assertEquals(180, BlockPositioning.roundAngle(135)); // == midpoint 135
        assertEquals(180, BlockPositioning.roundAngle(179));
        assertEquals(270, BlockPositioning.roundAngle(225)); // == midpoint 225
        assertEquals(270, BlockPositioning.roundAngle(269));
    }

    // ---------------------------------------------------------------------
    // roundAngle() -- normalization of out-of-range inputs
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("negatives in [-360,-1] are normalized by one turn, then rounded")
    public void roundNegativeNormalizedIntoRange() {
        assertEquals(0, BlockPositioning.roundAngle(-360));   // -> 0
        assertEquals(90, BlockPositioning.roundAngle(-270));  // -> 90
        assertEquals(180, BlockPositioning.roundAngle(-180)); // -> 180
        assertEquals(270, BlockPositioning.roundAngle(-90));  // -> 270
        assertEquals(270, BlockPositioning.roundAngle(-46));  // -> 314 -> 270
    }

    @Test
    @DisplayName("values above a full turn are reduced modulo 360, then rounded")
    public void roundOverflowNormalizedModulo() {
        assertEquals(0, BlockPositioning.roundAngle(361));   // -> 1 -> 0
        assertEquals(90, BlockPositioning.roundAngle(405));  // -> 45 -> 90
        assertEquals(90, BlockPositioning.roundAngle(450));  // -> 90
        assertEquals(180, BlockPositioning.roundAngle(540)); // -> 180
        assertEquals(0, BlockPositioning.roundAngle(720));   // -> 0
    }

    // ---------------------------------------------------------------------
    // roundAngle() -- characterization of current quirks
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("CHARACTERIZATION: angles in [315,360] are NOT snapped (no step >= them below 360)")
    public void roundUpperGapIsUntouched() {
        // The loop only ever assigns the *lower* step (min); there is no step at
        // 360, so once ret >= 315 no branch fires and the input is returned as-is.
        assertEquals(315, BlockPositioning.roundAngle(315));
        assertEquals(340, BlockPositioning.roundAngle(340));
        assertEquals(359, BlockPositioning.roundAngle(359));
        assertEquals(360, BlockPositioning.roundAngle(360));
        // -1 normalizes to 359, which likewise falls in the untouched gap.
        assertEquals(359, BlockPositioning.roundAngle(-1));
        assertEquals(315, BlockPositioning.roundAngle(-45)); // -> 315 (gap)
    }

    @Test
    @DisplayName("CHARACTERIZATION: inputs below -360 are under-normalized and collapse to 0")
    public void roundDeepNegativeUnderNormalized() {
        // (angle + 360) % 360 adds only a single turn, so e.g. -450 -> -90 and
        // -900 -> -180; both are < 45 and therefore snap to 0 rather than to
        // their true equivalents (270 and 180).
        assertEquals(0, BlockPositioning.roundAngle(-450));
        assertEquals(0, BlockPositioning.roundAngle(-900));
    }

    // ---------------------------------------------------------------------
    // roundAngle() -- derived properties
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("for inputs in [0,314] the result is always a grid multiple in {0,90,180,270}")
    public void roundInRangeAlwaysGridMultiple() {
        for (int a = 0; a <= 314; a++) {
            int r = BlockPositioning.roundAngle(a);
            assertEquals(0, r % 90, "result must be a multiple of 90 for input " + a);
            assertTrue(r == 0 || r == 90 || r == 180 || r == 270,
                       "result " + r + " out of grid set for input " + a);
            // Snapping never moves an in-range value by half a step or more.
            assertTrue(Math.abs(a - r) < BlockPositioning.ROTATION_STEP,
                       "input " + a + " snapped too far to " + r);
        }
    }

    @Test
    @DisplayName("roundAngle is idempotent on its own canonical outputs")
    public void roundIsIdempotentOnCanonicalOutputs() {
        for (int step : new int[] { 0, 90, 180, 270 }) {
            assertEquals(step, BlockPositioning.roundAngle(BlockPositioning.roundAngle(step)));
        }
    }
}
