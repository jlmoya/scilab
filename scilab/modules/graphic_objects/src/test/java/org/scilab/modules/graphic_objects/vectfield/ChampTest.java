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

package org.scilab.modules.graphic_objects.vectfield;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for {@link Champ}: the row-major grid layout, the
 * per-column / per-row base accessors, and the geometric helpers
 * (max length, max usable length, bounding box) computed against a fixed grid.
 */
public class ChampTest {

    /**
     * Builds a 2x2 champ on the unit-ish grid:
     *   column x-coordinates {0, 2}, row y-coordinates {0, 3},
     *   every arrow direction (3, 4, 0) whose norm is exactly 5.
     */
    private static Champ grid2x2() {
        Champ c = new Champ();
        c.setDimensions(new Integer[] {2, 2});
        c.setNumberArrows(4);
        c.setBaseX(new Double[] {0.0, 2.0});
        c.setBaseY(new Double[] {0.0, 3.0});
        c.setDirection(new Double[] {3.0, 4.0, 0.0, 3.0, 4.0, 0.0, 3.0, 4.0, 0.0, 3.0, 4.0, 0.0});
        return c;
    }

    @Test
    public void typeIsChamp() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_CHAMP__), new Champ().getType());
    }

    @Test
    public void constructorDefaults() {
        Champ c = new Champ();
        assertFalse(c.getColored());
        assertArrayEquals(new Integer[] {0, 0}, c.getDimensions());
    }

    @Test
    public void dimensionsRoundTripAndReturnDefensiveCopy() {
        Champ c = new Champ();
        assertEquals(UpdateStatus.Success, c.setDimensions(new Integer[] {4, 7}));
        assertArrayEquals(new Integer[] {4, 7}, c.getDimensions());
        Integer[] snapshot = c.getDimensions();
        snapshot[0] = -1;
        assertArrayEquals(new Integer[] {4, 7}, c.getDimensions());
    }

    @Test
    public void coloredRoundTrips() {
        Champ c = new Champ();
        assertEquals(UpdateStatus.Success, c.setColored(true));
        assertTrue(c.getColored());
        assertEquals(UpdateStatus.Success, c.setColored(false));
        assertFalse(c.getColored());
    }

    @Test
    public void perColumnAndPerRowBasesReflectTheGrid() {
        Champ c = grid2x2();
        assertArrayEquals(new Double[] {0.0, 2.0}, c.getBaseX());
        assertArrayEquals(new Double[] {0.0, 3.0}, c.getBaseY());
    }

    @Test
    public void baseXBroadcastsAcrossEveryRow() {
        Champ c = grid2x2();
        c.setBaseX(new Double[] {5.0, 9.0});
        // Every arrow in column 0 has x=5, in column 1 has x=9 (indices are row-major: 2*j+i).
        assertEquals(5.0, c.getArrows().get(0).getBase()[0], 0.0);
        assertEquals(9.0, c.getArrows().get(1).getBase()[0], 0.0);
        assertEquals(5.0, c.getArrows().get(2).getBase()[0], 0.0);
        assertEquals(9.0, c.getArrows().get(3).getBase()[0], 0.0);
    }

    @Test
    public void maxLengthIsLargestVectorNorm() {
        Champ c = grid2x2();
        // All directions have norm sqrt(9+16)=5.
        assertEquals(5.0, c.getMaxLength(), 1e-12);
    }

    @Test
    public void maxUsableLengthIsSmallestGridSpacing() {
        Champ c = grid2x2();
        // Column spacing is 2, row spacing is 3, so the usable length is 2.
        assertEquals(2.0, c.getMaxUsableLength(), 1e-12);
    }

    @Test
    public void maxUsableLengthDefaultsToOneWhenGridTooSmall() {
        // With fewer than two columns/rows the spacing degenerates to 1.0, and no
        // arrow is dereferenced, so a fresh champ is safe to query.
        assertEquals(1.0, new Champ().getMaxUsableLength(), 0.0);
    }

    @Test
    public void boundingBoxSpansBasesAndScaledDirections() {
        Champ c = grid2x2();
        // scaled tip = base + dir * (maxUsable / maxLength) = base + (3,4,0)*2/5 = base + (1.2,1.6,0)
        // extremes: x in [0, 3.2], y in [0, 4.6], z in [0, 0].
        Double[] bb = c.getBoundingBox();
        assertEquals(6, bb.length);
        assertEquals(0.0, bb[0], 1e-9);
        assertEquals(3.2, bb[1], 1e-9);
        assertEquals(0.0, bb[2], 1e-9);
        assertEquals(4.6, bb[3], 1e-9);
        assertEquals(0.0, bb[4], 1e-9);
        assertEquals(0.0, bb[5], 1e-9);
    }

    @Test
    public void baseAccessorsAreEmptyOnAFreshChamp() {
        Champ c = new Champ();
        assertEquals(0, c.getBaseX().length);
        assertEquals(0, c.getBaseY().length);
    }

    @Test
    public void lengthHelpersThrowOnAFreshChampWithoutArrows() {
        // Defect characterization: computeMaxLength / computeBoundingBox read
        // arrows.get(0) with no guard, so they throw on an unpopulated champ.
        Champ c = new Champ();
        assertThrows(IndexOutOfBoundsException.class, c::getMaxLength);
        assertThrows(IndexOutOfBoundsException.class, c::getBoundingBox);
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        Champ c = grid2x2();

        Object dims = c.getPropertyFromName(__GO_CHAMP_DIMENSIONS__);
        assertEquals(UpdateStatus.Success, c.setProperty(dims, new Integer[] {2, 2}));
        assertArrayEquals(new Integer[] {2, 2}, (Integer[]) c.getProperty(dims));

        Object colored = c.getPropertyFromName(__GO_COLORED__);
        assertEquals(UpdateStatus.Success, c.setProperty(colored, Boolean.TRUE));
        assertEquals(Boolean.TRUE, c.getProperty(colored));

        Object baseX = c.getPropertyFromName(__GO_BASE_X__);
        assertEquals(UpdateStatus.Success, c.setProperty(baseX, new Double[] {1.0, 2.0}));
        assertArrayEquals(new Double[] {1.0, 2.0}, (Double[]) c.getProperty(baseX));

        // Read-only geometric helper, reachable through the property machinery.
        Object usable = c.getPropertyFromName(__GO_MAX_USABLE_LENGTH__);
        assertEquals(c.getMaxUsableLength(), (Double) c.getProperty(usable), 1e-12);
    }
}
