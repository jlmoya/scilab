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

package org.scilab.modules.graphic_objects.axis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.axis.Axis.TicksDirection;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for the {@link Axis} graphic object: a standalone ruler
 * with ticks coordinates, labels/interpreters, and a delegated font.
 */
public class AxisTest {

    @Test
    public void constructorDefaults() {
        Axis a = new Axis();
        assertEquals(Integer.valueOf(0), a.getTicksDirection()); // TOP
        assertEquals(TicksDirection.TOP, a.getTicksDirectionAsEnum());
        assertEquals(Integer.valueOf(0), a.getTicksColor());
        assertFalse(a.getTicksSegment());
        assertEquals(Integer.valueOf(0), a.getTicksStyle());
        assertEquals(Integer.valueOf(0), a.getSubticks());
        assertEquals("", a.getFormatn());
        assertEquals(Integer.valueOf(0), a.getNumberTicksLabels());
        // Defaults: 10 x-ticks, 1 y-tick.
        assertEquals(Integer.valueOf(10), a.getXNumberTicks());
        assertEquals(Integer.valueOf(1), a.getYNumberTicks());
        assertNotNull(a.getFont());
    }

    @Test
    public void typeIsAxis() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_AXIS__), new Axis().getType());
    }

    @Test
    public void ticksDirectionIntMapsThroughEnumValuesOrder() {
        Axis a = new Axis();
        // The int setter indexes values(): TOP, BOTTOM, LEFT, RIGHT.
        assertEquals(UpdateStatus.Success, a.setTicksDirection(1));
        assertEquals(TicksDirection.BOTTOM, a.getTicksDirectionAsEnum());
        assertEquals(Integer.valueOf(1), a.getTicksDirection());

        a.setTicksDirection(2);
        assertEquals(TicksDirection.LEFT, a.getTicksDirectionAsEnum());
        a.setTicksDirection(3);
        assertEquals(TicksDirection.RIGHT, a.getTicksDirectionAsEnum());
    }

    @Test
    public void ticksDirectionOutOfRangeThrows() {
        Axis a = new Axis();
        // The int setter blindly indexes values(), so out-of-range values throw.
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> a.setTicksDirection(4));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> a.setTicksDirection(-1));
    }

    @Test
    public void ticksDirectionEnumSetterRoundTrips() {
        Axis a = new Axis();
        assertEquals(UpdateStatus.Success, a.setTicksDirectionAsEnum(TicksDirection.RIGHT));
        assertEquals(TicksDirection.RIGHT, a.getTicksDirectionAsEnum());
    }

    @Test
    public void scalarSettersRoundTrip() {
        Axis a = new Axis();
        assertEquals(UpdateStatus.Success, a.setTicksColor(12));
        assertEquals(Integer.valueOf(12), a.getTicksColor());

        assertEquals(UpdateStatus.Success, a.setTicksSegment(true));
        assertTrue(a.getTicksSegment());

        assertEquals(UpdateStatus.Success, a.setTicksStyle(2));
        assertEquals(Integer.valueOf(2), a.getTicksStyle());

        assertEquals(UpdateStatus.Success, a.setSubticks(5));
        assertEquals(Integer.valueOf(5), a.getSubticks());

        assertEquals(UpdateStatus.Success, a.setFormatn("%d"));
        assertEquals("%d", a.getFormatn());
    }

    @Test
    public void settingLabelsSeedsAutoInterpreters() {
        Axis a = new Axis();
        assertEquals(UpdateStatus.Success, a.setTicksLabels(new String[] {"a", "b", "c"}));
        assertArrayEquals(new String[] {"a", "b", "c"}, a.getTicksLabels());
        assertEquals(Integer.valueOf(3), a.getNumberTicksLabels());
        // Each freshly set label is paired with the "auto" interpreter.
        assertArrayEquals(new String[] {"auto", "auto", "auto"}, a.getTicksInterpreters());
    }

    @Test
    public void interpretersCanBeOverriddenAndBroadcastSingleValue() {
        Axis a = new Axis();
        a.setTicksLabels(new String[] {"a", "b"});
        // A single interpreter is broadcast across all labels.
        assertEquals(UpdateStatus.Success, a.setTicksInterpreters(new String[] {"latex"}));
        assertArrayEquals(new String[] {"latex", "latex"}, a.getTicksInterpreters());
    }

    @Test
    public void xTicksCoordsResizeAndRoundTrip() {
        Axis a = new Axis();
        assertEquals(UpdateStatus.Success, a.setXTicksCoords(new Double[] {0.0, 0.25, 0.5, 1.0}));
        assertEquals(Integer.valueOf(4), a.getXNumberTicks());
        assertArrayEquals(new Double[] {0.0, 0.25, 0.5, 1.0}, a.getXTicksCoords());

        assertEquals(UpdateStatus.Success, a.setYTicksCoords(new Double[] {2.0, 3.0}));
        assertEquals(Integer.valueOf(2), a.getYNumberTicks());
        assertArrayEquals(new Double[] {2.0, 3.0}, a.getYTicksCoords());
    }

    @Test
    public void fontPropertiesDelegateToTheFont() {
        Axis a = new Axis();
        a.setStyle(2);
        a.setSize(14.0);
        a.setColor(7);
        a.setFractional(true);
        assertEquals(Integer.valueOf(2), a.getStyle());
        assertEquals(Double.valueOf(14.0), a.getSize());
        assertEquals(Integer.valueOf(7), a.getColor());
        assertTrue(a.getFractional());
    }
}
