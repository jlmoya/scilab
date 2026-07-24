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

import org.scilab.modules.graphic_objects.axes.Axes.GridPosition;
import org.scilab.modules.graphic_objects.axes.AxisProperty.AxisLocation;
import org.scilab.modules.graphic_objects.axes.Camera.ViewType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for the {@link Axes} graphic object. These drive the
 * plain Java model directly (no GraphicController), covering construction
 * defaults, the sub-object (axis/box/camera) delegation surface, and clone
 * independence. The controller-dependent path (setRotationAngles) is
 * deliberately avoided.
 */
public class AxesTest {

    @Test
    public void gridPositionIntToEnum() {
        assertEquals(GridPosition.BACKGROUND, GridPosition.intToEnum(0));
        assertEquals(GridPosition.FOREGROUND, GridPosition.intToEnum(1));
        assertNull(GridPosition.intToEnum(2));
        assertNull(GridPosition.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        Axes a = new Axes();
        assertEquals(GridPosition.FOREGROUND, a.getGridPositionAsEnum());
        assertEquals(Integer.valueOf(1), a.getGridPosition());
        assertFalse(a.getAutoClear());
        assertFalse(a.getFilled());
        assertEquals(Integer.valueOf(0), a.getBackground());
        assertTrue(a.getAutoMargins());
        assertEquals(Integer.valueOf(0), a.getTitle());
        assertEquals(Integer.valueOf(0), a.getView()); // VIEW_2D
        assertEquals(Integer.valueOf(0), a.getBoxType()); // OFF
        assertEquals(Integer.valueOf(1), a.getArcDrawingMethod()); // LINES
        assertNotNull(a.getColorMap());
    }

    @Test
    public void typeIsAxes() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_AXES__), new Axes().getType());
    }

    @Test
    public void holdsThreeDistinctAxisProperties() {
        Axes a = new Axes();
        assertEquals(3, a.getAxes().length);
        assertNotNull(a.getXAxis());
        assertNotNull(a.getYAxis());
        assertNotNull(a.getZAxis());
        assertSame(a.getXAxis(), a.getAxes()[0]);
        assertSame(a.getYAxis(), a.getAxes()[1]);
        assertSame(a.getZAxis(), a.getAxes()[2]);
        assertNotSame(a.getXAxis(), a.getYAxis());
    }

    @Test
    public void xAxisVisibilityDelegatesToFirstAxis() {
        Axes a = new Axes();
        assertFalse(a.getXAxisVisible());
        assertEquals(UpdateStatus.Success, a.setXAxisVisible(true));
        assertTrue(a.getXAxisVisible());
        // The delegate carries state: the X axis object now reads visible too.
        assertTrue(a.getXAxis().getVisible());
    }

    @Test
    public void xAxisLocationConvertsThroughEnum() {
        Axes a = new Axes();
        // Default axis location is ORIGIN (ordinal 3).
        assertEquals(Integer.valueOf(3), a.getXAxisLocation());
        assertEquals(UpdateStatus.Success, a.setXAxisLocation(1)); // TOP
        assertEquals(AxisLocation.TOP, a.getXAxisLocationAsEnum());
        assertEquals(Integer.valueOf(1), a.getXAxisLocation());
    }

    @Test
    public void gridPositionSetterRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setGridPosition(0)); // BACKGROUND
        assertEquals(GridPosition.BACKGROUND, a.getGridPositionAsEnum());
        assertEquals(Integer.valueOf(0), a.getGridPosition());
        assertEquals(UpdateStatus.NoChange, a.setGridPosition(0));
    }

    @Test
    public void boxTypeAndViewDelegate() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setBoxType(1)); // ON
        assertEquals(Integer.valueOf(1), a.getBoxType());

        assertEquals(UpdateStatus.Success, a.setView(1)); // VIEW_3D
        assertEquals(ViewType.VIEW_3D, a.getViewAsEnum());
        assertEquals(Integer.valueOf(1), a.getView());
    }

    @Test
    public void dataBoundsDelegateToBox() {
        Axes a = new Axes();
        assertArrayEquals(new Double[] {0.0, 1.0, 0.0, 1.0, -1.0, 1.0}, a.getDataBounds());
        Double[] bounds = new Double[] {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        assertEquals(UpdateStatus.Success, a.setDataBounds(bounds));
        assertArrayEquals(bounds, a.getDataBounds());
    }

    @Test
    public void marginsRoundTripAndReportNoChange() {
        Axes a = new Axes();
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0, 0.0}, a.getMargins());
        Double[] margins = new Double[] {0.1, 0.2, 0.3, 0.4};
        assertEquals(UpdateStatus.Success, a.setMargins(margins));
        assertArrayEquals(margins, a.getMargins());
        assertEquals(UpdateStatus.NoChange, a.setMargins(margins));
    }

    @Test
    public void axesBoundsRoundTripAndReportNoChange() {
        Axes a = new Axes();
        Double[] bounds = new Double[] {0.0, 0.0, 1.0, 1.0};
        assertEquals(UpdateStatus.Success, a.setAxesBounds(bounds));
        assertArrayEquals(bounds, a.getAxesBounds());
        assertEquals(UpdateStatus.NoChange, a.setAxesBounds(bounds));
    }

    @Test
    public void scalarFlagsRoundTrip() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setAutoClear(true));
        assertEquals(UpdateStatus.NoChange, a.setAutoClear(true));
        assertEquals(UpdateStatus.Success, a.setFilled(true));
        assertEquals(UpdateStatus.NoChange, a.setFilled(true));
        assertEquals(UpdateStatus.Success, a.setBackground(9));
        assertEquals(Integer.valueOf(9), a.getBackground());
        assertEquals(UpdateStatus.NoChange, a.setBackground(9));
        assertEquals(UpdateStatus.Success, a.setAutoMargins(false));
        assertFalse(a.getAutoMargins());
        assertEquals(UpdateStatus.NoChange, a.setAutoMargins(false));
    }

    @Test
    public void arcDrawingMethodRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setArcDrawingMethod(0)); // NURBS
        assertEquals(Integer.valueOf(0), a.getArcDrawingMethod());
        assertEquals(UpdateStatus.NoChange, a.setArcDrawingMethod(0));
    }

    @Test
    public void scaleRoundTrips() {
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setScale(1.0, 2.0, 3.0));
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, a.getScale());
    }

    @Test
    public void titleReferenceEqualityIsMaskedForCachedIntegers() {
        // Small Integer values are cached, so the reference "!=" check behaves
        // like value equality here: the second identical set is a NoChange.
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setTitle(50));
        assertEquals(Integer.valueOf(50), a.getTitle());
        assertEquals(UpdateStatus.NoChange, a.setTitle(50));
    }

    @Test
    public void titleReferenceEqualityBugIsVisibleForLargeIntegers() {
        // Values outside the Integer cache box to distinct objects, exposing
        // setTitle's reference ("!=") comparison: an identical re-set is wrongly
        // reported as a change. This documents current behaviour.
        Axes a = new Axes();
        assertEquals(UpdateStatus.Success, a.setTitle(100000));
        assertEquals(UpdateStatus.Success, a.setTitle(100000));
    }

    @Test
    public void cloneCopiesStateResetsTitleAndKeepsAxesIndependent() {
        Axes a = new Axes();
        a.setTitle(5);
        a.setFilled(true);
        a.setBackground(9);
        a.setGridPositionAsEnum(GridPosition.BACKGROUND);
        a.setXAxisVisible(true);

        Axes copy = a.clone();
        // Clone resets the title UID and marks itself valid.
        assertEquals(Integer.valueOf(0), copy.getTitle());
        assertTrue(copy.isValid());
        // Scalar/enum state is carried over.
        assertTrue(copy.getFilled());
        assertEquals(Integer.valueOf(9), copy.getBackground());
        assertEquals(GridPosition.BACKGROUND, copy.getGridPositionAsEnum());
        assertTrue(copy.getXAxisVisible());

        // The axes array is deep-copied: mutating the clone leaves the source.
        copy.setXAxisVisible(false);
        assertTrue(a.getXAxisVisible());
    }
}
