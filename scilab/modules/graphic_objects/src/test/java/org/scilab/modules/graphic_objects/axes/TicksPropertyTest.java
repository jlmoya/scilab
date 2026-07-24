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

package org.scilab.modules.graphic_objects.axes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link TicksProperty}: the ticks holder for one axis,
 * which switches between an automatic and a user ticks set based on its "auto"
 * flag.
 */
public class TicksPropertyTest {

    @Test
    public void constructorDefaults() {
        TicksProperty tp = new TicksProperty();
        assertFalse(tp.getAuto());
        assertEquals(Integer.valueOf(0), tp.getSubticks());
        assertEquals("", tp.getFormat());
        assertArrayEquals(new Double[] {1.0, 0.0}, tp.getSTFactors());
        assertNotNull(tp.getDefaultFont());
        // Not automatic => the user ticks set (empty) is active.
        assertEquals(Integer.valueOf(0), tp.getNumber());
        assertEquals(0, tp.getLocations().length);
    }

    @Test
    public void autoFlagSelectsTheAutomaticTicksSet() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setAuto(true));
        assertTrue(tp.getAuto());
        // The automatic set is seeded with 11 ticks.
        assertEquals(Integer.valueOf(11), tp.getNumber());
        assertEquals(UpdateStatus.NoChange, tp.setAuto(true));
        assertEquals(UpdateStatus.Success, tp.setAuto(false));
    }

    @Test
    public void subticksRoundTrips() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setSubticks(3));
        assertEquals(Integer.valueOf(3), tp.getSubticks());
        assertEquals(UpdateStatus.NoChange, tp.setSubticks(3));
    }

    @Test
    public void formatRoundTrips() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setFormat("%.2f"));
        assertEquals("%.2f", tp.getFormat());
        assertEquals(UpdateStatus.NoChange, tp.setFormat("%.2f"));
    }

    @Test
    public void scaleTranslateFactorsRoundTrip() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setSTFactors(new Double[] {2.0, 3.0}));
        assertArrayEquals(new Double[] {2.0, 3.0}, tp.getSTFactors());
        // Equal factors report no change.
        assertEquals(UpdateStatus.NoChange, tp.setSTFactors(new Double[] {2.0, 3.0}));
    }

    @Test
    public void settingLocationsResizesTheActiveTicksSet() {
        TicksProperty tp = new TicksProperty();
        assertEquals(UpdateStatus.Success, tp.setLocations(new Double[] {0.0, 0.5, 1.0}));
        assertEquals(Integer.valueOf(3), tp.getNumber());
        assertArrayEquals(new Double[] {0.0, 0.5, 1.0}, tp.getLocations());
    }

    @Test
    public void equalsIsReflexive() {
        TicksProperty tp = new TicksProperty();
        assertEquals(tp, tp);
    }

    @Test
    public void equalsRejectsNullOtherTypeAndDifferingAuto() {
        TicksProperty tp = new TicksProperty();
        assertNotEquals(tp, null);
        assertNotEquals(tp, "ticks");

        TicksProperty other = new TicksProperty();
        other.setAuto(true);
        assertNotEquals(tp, other);
    }
}
