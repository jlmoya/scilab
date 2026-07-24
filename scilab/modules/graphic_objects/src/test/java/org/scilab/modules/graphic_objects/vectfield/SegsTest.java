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
 * Hermetic unit tests for {@link Segs}: the colour-per-segment array, the
 * colour-reset behaviour on arrow-count changes, and the aggregate mark
 * accessors delegated to the underlying arrows.
 */
public class SegsTest {

    @Test
    public void typeIsSegs() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_SEGS__), new Segs().getType());
    }

    @Test
    public void settingArrowCountResetsColoursToZero() {
        // Arrow default line colour is -1, but Segs forces 0 whenever the count changes.
        Segs s = new Segs();
        assertEquals(UpdateStatus.Success, s.setNumberArrows(3));
        assertArrayEquals(new Integer[] {0, 0, 0}, s.getColors());
    }

    @Test
    public void singleColourIsBroadcastToEverySegment() {
        Segs s = new Segs();
        s.setNumberArrows(3);
        assertEquals(UpdateStatus.Success, s.setColors(new Integer[] {5}));
        assertArrayEquals(new Integer[] {5, 5, 5}, s.getColors());
    }

    @Test
    public void perSegmentColoursAreAppliedInOrder() {
        Segs s = new Segs();
        s.setNumberArrows(3);
        assertEquals(UpdateStatus.Success, s.setColors(new Integer[] {1, 2, 3}));
        assertArrayEquals(new Integer[] {1, 2, 3}, s.getColors());
    }

    @Test
    public void coloursAreEmptyWhenThereAreNoSegments() {
        assertEquals(0, new Segs().getColors().length);
    }

    @Test
    public void resizingToTheSameCountPreservesColours() {
        Segs s = new Segs();
        s.setNumberArrows(2);
        s.setColors(new Integer[] {7, 8});
        // Same count -> Segs must not clobber the existing colours.
        assertEquals(UpdateStatus.Success, s.setNumberArrows(2));
        assertArrayEquals(new Integer[] {7, 8}, s.getColors());
    }

    @Test
    public void changingCountClobbersColoursBackToZero() {
        Segs s = new Segs();
        s.setNumberArrows(2);
        s.setColors(new Integer[] {7, 8});
        s.setNumberArrows(3);
        assertArrayEquals(new Integer[] {0, 0, 0}, s.getColors());
    }

    @Test
    public void markAccessorDefaults() {
        Segs s = new Segs();
        s.setNumberArrows(1);
        assertFalse(s.getMarkMode());
        assertEquals(Integer.valueOf(0), s.getMarkStyle());
        assertEquals(Integer.valueOf(0), s.getMarkSize());
        assertEquals(Integer.valueOf(0), s.getMarkForeground());
        assertEquals(Integer.valueOf(0), s.getMarkBackground());
        assertEquals(Integer.valueOf(0), s.getMarkSizeUnit()); // POINT ordinal
    }

    @Test
    public void aggregateMarkSettersApplyToEverySegment() {
        Segs s = new Segs();
        s.setNumberArrows(3);
        assertEquals(UpdateStatus.Success, s.setMarkMode(true));
        assertTrue(s.getMarkMode());
        assertEquals(UpdateStatus.Success, s.setMarkStyle(4));
        assertEquals(Integer.valueOf(4), s.getMarkStyle());
        assertEquals(UpdateStatus.Success, s.setMarkSize(10));
        assertEquals(Integer.valueOf(10), s.getMarkSize());
        assertEquals(UpdateStatus.Success, s.setMarkForeground(2));
        assertEquals(Integer.valueOf(2), s.getMarkForeground());
        assertEquals(UpdateStatus.Success, s.setMarkBackground(6));
        assertEquals(Integer.valueOf(6), s.getMarkBackground());
        assertEquals(UpdateStatus.Success, s.setMarkSizeUnit(1));
        assertEquals(Integer.valueOf(1), s.getMarkSizeUnit()); // TABULATED

        // Confirm it landed on every underlying arrow, not just the first.
        for (Arrow arrow : s.getArrows()) {
            assertTrue(arrow.getMarkMode());
            assertEquals(Integer.valueOf(4), arrow.getMarkStyle());
        }
    }

    @Test
    public void markAccessorsThrowWhenThereAreNoSegments() {
        // Defect characterization: the mark accessors read arrows.get(0) directly.
        Segs s = new Segs();
        assertThrows(IndexOutOfBoundsException.class, s::getMarkMode);
        assertThrows(IndexOutOfBoundsException.class, s::getMarkStyle);
        assertThrows(IndexOutOfBoundsException.class, s::getMarkSize);
    }

    @Test
    public void propertyNameLookupRoundTrips() {
        Segs s = new Segs();
        s.setNumberArrows(3);

        Object colors = s.getPropertyFromName(__GO_SEGS_COLORS__);
        assertEquals(UpdateStatus.Success, s.setProperty(colors, new Integer[] {3, 6, 9}));
        assertArrayEquals(new Integer[] {3, 6, 9}, (Integer[]) s.getProperty(colors));

        Object markMode = s.getPropertyFromName(__GO_MARK_MODE__);
        assertEquals(UpdateStatus.Success, s.setProperty(markMode, Boolean.TRUE));
        assertEquals(Boolean.TRUE, s.getProperty(markMode));

        Object markStyle = s.getPropertyFromName(__GO_MARK_STYLE__);
        assertEquals(UpdateStatus.Success, s.setProperty(markStyle, Integer.valueOf(5)));
        assertEquals(Integer.valueOf(5), s.getProperty(markStyle));
    }
}
