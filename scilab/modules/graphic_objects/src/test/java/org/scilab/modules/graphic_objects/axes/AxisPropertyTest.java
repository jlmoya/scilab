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

import org.scilab.modules.graphic_objects.axes.AxisProperty.AxisLocation;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link AxisProperty}: a plain data holder for one
 * axis of an Axes object, plus its {@link AxisLocation} int/enum converter.
 */
public class AxisPropertyTest {

    @Test
    public void axisLocationIntToEnumCoversEveryOrdinal() {
        assertEquals(AxisLocation.BOTTOM, AxisLocation.intToEnum(0));
        assertEquals(AxisLocation.TOP, AxisLocation.intToEnum(1));
        assertEquals(AxisLocation.MIDDLE, AxisLocation.intToEnum(2));
        assertEquals(AxisLocation.ORIGIN, AxisLocation.intToEnum(3));
        assertEquals(AxisLocation.LEFT, AxisLocation.intToEnum(4));
        assertEquals(AxisLocation.RIGHT, AxisLocation.intToEnum(5));
    }

    @Test
    public void axisLocationIntToEnumReturnsNullOutOfRange() {
        assertNull(AxisLocation.intToEnum(6));
        assertNull(AxisLocation.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        AxisProperty ap = new AxisProperty();
        assertFalse(ap.getVisible());
        assertFalse(ap.getReverse());
        assertEquals(Integer.valueOf(0), ap.getGridColor());
        assertEquals(Double.valueOf(-1.0), ap.getGridThickness());
        assertEquals(AxisLocation.ORIGIN, ap.getAxisLocationAsEnum());
        assertFalse(ap.getLogFlag());
        assertEquals(Integer.valueOf(0), ap.getLabel());
        // Default grid style is DOT, whose Scilab index is 7 (ordinal 6 + 1).
        assertEquals(Integer.valueOf(7), ap.getGridStyle());
        assertNotNull(ap.getTicks());
        // Ticks default to non-automatic, therefore zero user ticks.
        assertEquals(Integer.valueOf(0), ap.getNumberOfTicks());
    }

    @Test
    public void scalarSettersReportSuccessThenNoChange() {
        AxisProperty ap = new AxisProperty();

        assertEquals(UpdateStatus.Success, ap.setVisible(true));
        assertTrue(ap.getVisible());
        assertEquals(UpdateStatus.NoChange, ap.setVisible(true));

        assertEquals(UpdateStatus.Success, ap.setReverse(true));
        assertEquals(UpdateStatus.NoChange, ap.setReverse(true));

        assertEquals(UpdateStatus.Success, ap.setGridColor(42));
        assertEquals(Integer.valueOf(42), ap.getGridColor());
        assertEquals(UpdateStatus.NoChange, ap.setGridColor(42));

        assertEquals(UpdateStatus.Success, ap.setGridThickness(2.5));
        assertEquals(Double.valueOf(2.5), ap.getGridThickness());
        assertEquals(UpdateStatus.NoChange, ap.setGridThickness(2.5));

        assertEquals(UpdateStatus.Success, ap.setLogFlag(true));
        assertEquals(UpdateStatus.NoChange, ap.setLogFlag(true));
    }

    @Test
    public void axisLocationSetterRoundTrips() {
        AxisProperty ap = new AxisProperty();
        assertEquals(UpdateStatus.Success, ap.setAxisLocation(AxisLocation.TOP));
        assertEquals(AxisLocation.TOP, ap.getAxisLocation());
        assertEquals(UpdateStatus.NoChange, ap.setAxisLocation(AxisLocation.TOP));
    }

    @Test
    public void gridStyleConvertsThroughScilabIndex() {
        AxisProperty ap = new AxisProperty();
        // Scilab index 1 == SOLID.
        assertEquals(UpdateStatus.Success, ap.setGridStyle(1));
        assertEquals(Integer.valueOf(1), ap.getGridStyle());
        assertEquals(UpdateStatus.NoChange, ap.setGridStyle(1));
        // Out-of-range index falls back to SOLID (index 1), so no change here.
        assertEquals(UpdateStatus.NoChange, ap.setGridStyle(999));
        assertEquals(Integer.valueOf(1), ap.getGridStyle());
    }

    @Test
    public void nullLabelIsNormalisedToZero() {
        AxisProperty ap = new AxisProperty();
        ap.setLabel(11);
        assertEquals(Integer.valueOf(11), ap.getLabel());
        // Setting a null label must not NPE and must normalise to 0.
        assertEquals(UpdateStatus.Success, ap.setLabel(null));
        assertEquals(Integer.valueOf(0), ap.getLabel());
    }

    @Test
    public void autoTicksAndSubticksDelegateToTicks() {
        AxisProperty ap = new AxisProperty();
        assertFalse(ap.getAutoTicks());
        assertEquals(UpdateStatus.Success, ap.setAutoTicks(true));
        assertTrue(ap.getAutoTicks());

        assertEquals(UpdateStatus.Success, ap.setSubticks(4));
        assertEquals(Integer.valueOf(4), ap.getSubticks());
        assertEquals(UpdateStatus.NoChange, ap.setSubticks(4));
    }

    @Test
    public void copyConstructorCopiesFieldsResetsLabelAndIsIndependent() {
        AxisProperty src = new AxisProperty();
        src.setVisible(true);
        src.setReverse(true);
        src.setGridColor(9);
        src.setGridThickness(3.0);
        src.setAxisLocation(AxisLocation.LEFT);
        src.setLogFlag(true);
        src.setLabel(77);

        AxisProperty copy = new AxisProperty(src);
        assertTrue(copy.getVisible());
        assertTrue(copy.getReverse());
        assertEquals(Integer.valueOf(9), copy.getGridColor());
        assertEquals(Double.valueOf(3.0), copy.getGridThickness());
        assertEquals(AxisLocation.LEFT, copy.getAxisLocationAsEnum());
        assertTrue(copy.getLogFlag());
        // The label UID is deliberately NOT propagated by the copy constructor.
        assertEquals(Integer.valueOf(0), copy.getLabel());

        // Independence: mutating the copy leaves the source untouched.
        copy.setGridColor(1000);
        assertEquals(Integer.valueOf(9), src.getGridColor());
    }

    @Test
    public void getPropertyFromNameMapsKnownNamesAndUnknown() {
        AxisProperty ap = new AxisProperty();
        assertEquals(AxisProperty.AxisPropertyProperty.VISIBLE, ap.getPropertyFromName("Visible"));
        assertEquals(AxisProperty.AxisPropertyProperty.GRIDCOLOR, ap.getPropertyFromName("GridColor"));
        assertEquals(AxisProperty.AxisPropertyProperty.UNKNOWNPROPERTY, ap.getPropertyFromName("Nonexistent"));
    }
}
