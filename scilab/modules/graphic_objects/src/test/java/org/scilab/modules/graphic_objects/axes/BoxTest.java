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

import org.scilab.modules.graphic_objects.axes.Box.BoxType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Box}: the data-bounds/zoom/box-type holder of
 * an Axes object, plus its {@link BoxType} int/enum converter.
 */
public class BoxTest {

    @Test
    public void boxTypeIntToEnumCoversEveryOrdinal() {
        assertEquals(BoxType.OFF, BoxType.intToEnum(0));
        assertEquals(BoxType.ON, BoxType.intToEnum(1));
        assertEquals(BoxType.HIDDEN_AXES, BoxType.intToEnum(2));
        assertEquals(BoxType.BACK_HALF, BoxType.intToEnum(3));
    }

    @Test
    public void boxTypeIntToEnumReturnsNullOutOfRange() {
        assertNull(BoxType.intToEnum(4));
        assertNull(BoxType.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        Box b = new Box();
        assertEquals(BoxType.OFF, b.getBox());
        assertEquals(Integer.valueOf(0), b.getHiddenAxisColor());
        assertFalse(b.getXTightLimits());
        assertFalse(b.getYTightLimits());
        assertFalse(b.getZTightLimits());
        assertFalse(b.getZoomEnabled());
        assertFalse(b.getAutoScale());
        assertTrue(b.getAutoStretch());
        assertTrue(b.getFirstPlot());
        assertArrayEquals(new Double[] {0.0, 1.0, 0.0, 1.0, -1.0, 1.0}, b.getDataBounds());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, b.getRealDataBounds());
    }

    @Test
    public void boxTypeSetterRoundTrips() {
        Box b = new Box();
        assertEquals(UpdateStatus.Success, b.setBox(BoxType.ON));
        assertEquals(BoxType.ON, b.getBox());
        assertEquals(UpdateStatus.NoChange, b.setBox(BoxType.ON));
    }

    @Test
    public void tightLimitsAreIndependentPerAxis() {
        Box b = new Box();
        assertEquals(UpdateStatus.Success, b.setXTightLimits(true));
        assertTrue(b.getXTightLimits());
        assertFalse(b.getYTightLimits());
        assertFalse(b.getZTightLimits());
        assertEquals(UpdateStatus.NoChange, b.setXTightLimits(true));

        assertEquals(UpdateStatus.Success, b.setZTightLimits(true));
        assertTrue(b.getZTightLimits());
        assertFalse(b.getYTightLimits());
    }

    @Test
    public void scalarSettersReportSuccessThenNoChange() {
        Box b = new Box();
        assertEquals(UpdateStatus.Success, b.setHiddenAxisColor(5));
        assertEquals(Integer.valueOf(5), b.getHiddenAxisColor());
        assertEquals(UpdateStatus.NoChange, b.setHiddenAxisColor(5));

        assertEquals(UpdateStatus.Success, b.setZoomEnabled(true));
        assertEquals(UpdateStatus.NoChange, b.setZoomEnabled(true));

        assertEquals(UpdateStatus.Success, b.setAutoScale(true));
        assertEquals(UpdateStatus.NoChange, b.setAutoScale(true));

        // firstPlot defaults to true, so setting false is a real change.
        assertEquals(UpdateStatus.Success, b.setFirstPlot(false));
        assertFalse(b.getFirstPlot());
        assertEquals(UpdateStatus.NoChange, b.setFirstPlot(false));

        // autoStretch defaults to true.
        assertEquals(UpdateStatus.Success, b.setAutoStretch(false));
        assertEquals(UpdateStatus.NoChange, b.setAutoStretch(false));
    }

    @Test
    public void dataBoundsRoundTripAndNoChangeOnEqual() {
        Box b = new Box();
        Double[] bounds = new Double[] {5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
        assertEquals(UpdateStatus.Success, b.setDataBounds(bounds));
        assertArrayEquals(bounds, b.getDataBounds());
        assertEquals(UpdateStatus.NoChange, b.setDataBounds(bounds));
    }

    @Test
    public void zoomBoxAndRealDataBoundsRoundTrip() {
        Box b = new Box();
        Double[] zoom = new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        assertEquals(UpdateStatus.Success, b.setZoomBox(zoom));
        assertArrayEquals(zoom, b.getZoomBox());

        Double[] real = new Double[] {-1.0, -2.0, -3.0, -4.0, -5.0, -6.0};
        assertEquals(UpdateStatus.Success, b.setRealDataBounds(real));
        assertArrayEquals(real, b.getRealDataBounds());
    }

    @Test
    public void gettersReturnDefensiveCopies() {
        Box b = new Box();
        Double[] first = b.getDataBounds();
        first[0] = 999.0;
        // Mutating the returned array must not corrupt the internal state.
        assertEquals(Double.valueOf(0.0), b.getDataBounds()[0]);
    }

    @Test
    public void copyConstructorCopiesStateAndIsIndependent() {
        Box src = new Box();
        src.setBox(BoxType.HIDDEN_AXES);
        src.setHiddenAxisColor(3);
        src.setXTightLimits(true);
        src.setZoomEnabled(true);
        src.setAutoScale(true);
        src.setFirstPlot(false);
        src.setDataBounds(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0});

        Box copy = new Box(src);
        assertEquals(BoxType.HIDDEN_AXES, copy.getBox());
        assertEquals(Integer.valueOf(3), copy.getHiddenAxisColor());
        assertTrue(copy.getXTightLimits());
        assertTrue(copy.getZoomEnabled());
        assertTrue(copy.getAutoScale());
        assertFalse(copy.getFirstPlot());
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, copy.getDataBounds());

        // Independence: changing the copy's data bounds must not touch the source.
        copy.setDataBounds(new Double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0}, src.getDataBounds());
    }
}
