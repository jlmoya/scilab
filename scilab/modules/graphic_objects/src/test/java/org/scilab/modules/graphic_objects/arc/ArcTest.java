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

package org.scilab.modules.graphic_objects.arc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.arc.Arc.ArcDrawingMethod;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.*;

/**
 * Hermetic unit tests for the {@link Arc} graphic object: bounding-box geometry,
 * start/end angles, and the drawing-method converter.
 */
public class ArcTest {

    @Test
    public void arcDrawingMethodIntToEnum() {
        assertEquals(ArcDrawingMethod.NURBS, ArcDrawingMethod.intToEnum(0));
        assertEquals(ArcDrawingMethod.LINES, ArcDrawingMethod.intToEnum(1));
        assertNull(ArcDrawingMethod.intToEnum(2));
        assertNull(ArcDrawingMethod.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        Arc a = new Arc();
        assertEquals(Double.valueOf(0.0), a.getWidth());
        assertEquals(Double.valueOf(0.0), a.getHeight());
        assertEquals(Double.valueOf(0.0), a.getStartAngle());
        assertEquals(Double.valueOf(0.0), a.getEndAngle());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, a.getUpperLeftPoint());
        // Default drawing method is LINES (ordinal 1).
        assertEquals(ArcDrawingMethod.LINES, a.getArcDrawingMethodAsEnum());
        assertEquals(Integer.valueOf(1), a.getArcDrawingMethod());
    }

    @Test
    public void typeIsArc() {
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_ARC__), new Arc().getType());
    }

    @Test
    public void geometrySettersRoundTrip() {
        Arc a = new Arc();
        assertEquals(UpdateStatus.Success, a.setWidth(3.5));
        assertEquals(Double.valueOf(3.5), a.getWidth());
        assertEquals(UpdateStatus.Success, a.setHeight(4.5));
        assertEquals(Double.valueOf(4.5), a.getHeight());
        assertEquals(UpdateStatus.Success, a.setStartAngle(1.0));
        assertEquals(Double.valueOf(1.0), a.getStartAngle());
        assertEquals(UpdateStatus.Success, a.setEndAngle(2.0));
        assertEquals(Double.valueOf(2.0), a.getEndAngle());
    }

    @Test
    public void upperLeftPointRoundTripsAndReturnsCopy() {
        Arc a = new Arc();
        assertEquals(UpdateStatus.Success, a.setUpperLeftPoint(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, a.getUpperLeftPoint());

        Double[] fetched = a.getUpperLeftPoint();
        fetched[0] = 99.0;
        // The getter returns a fresh array; mutating it must not corrupt state.
        assertEquals(Double.valueOf(1.0), a.getUpperLeftPoint()[0]);
    }

    @Test
    public void drawingMethodSetterRoundTrips() {
        Arc a = new Arc();
        assertEquals(UpdateStatus.Success, a.setArcDrawingMethod(0));
        assertEquals(ArcDrawingMethod.NURBS, a.getArcDrawingMethodAsEnum());
        assertEquals(Integer.valueOf(0), a.getArcDrawingMethod());

        assertEquals(UpdateStatus.Success, a.setArcDrawingMethodAsEnum(ArcDrawingMethod.LINES));
        assertEquals(Integer.valueOf(1), a.getArcDrawingMethod());
    }

    @Test
    public void cloneResetsUpperLeftPointToOriginButKeepsScalars() {
        Arc a = new Arc();
        a.setUpperLeftPoint(new Double[] {1.0, 2.0, 3.0});
        a.setWidth(7.0);

        Arc copy = a.clone();
        // Documented behaviour: clone zeroes the upper-left point...
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, copy.getUpperLeftPoint());
        // ...while scalar geometry survives the shallow copy.
        assertEquals(Double.valueOf(7.0), copy.getWidth());
        assertEquals(Integer.valueOf(GraphicObjectProperties.__GO_ARC__), copy.getType());

        // The original is untouched.
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, a.getUpperLeftPoint());
    }

    /* ---- generic getProperty/setProperty dispatch coverage ---- */

    private void assertScalarRoundTrip(int propertyId, Object value) {
        Arc a = new Arc();
        Object prop = a.getPropertyFromName(propertyId);
        a.setProperty(prop, value);
        assertEquals(value, a.getProperty(prop), "round-trip mismatch for id " + propertyId);
    }

    @Test
    public void scalarArcPropertiesRoundTripThroughGenericDispatch() {
        assertScalarRoundTrip(__GO_WIDTH__, Double.valueOf(3.5));
        assertScalarRoundTrip(__GO_HEIGHT__, Double.valueOf(4.5));
        assertScalarRoundTrip(__GO_START_ANGLE__, Double.valueOf(1.0));
        assertScalarRoundTrip(__GO_END_ANGLE__, Double.valueOf(2.0));
        assertScalarRoundTrip(__GO_ARC_DRAWING_METHOD__, Integer.valueOf(0)); // NURBS
    }

    @Test
    public void upperLeftPointRoundTripsThroughGenericDispatch() {
        Arc a = new Arc();
        Object prop = a.getPropertyFromName(__GO_UPPER_LEFT_POINT__);
        a.setProperty(prop, new Double[] {1.0, 2.0, 3.0});
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, (Double[]) a.getProperty(prop));
    }
}
