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

package org.scilab.modules.graphic_objects.datatip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.datatip.Datatip.DatatipObjectProperty;
import org.scilab.modules.graphic_objects.datatip.Datatip.TipOrientation;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClipStateType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Datatip}. The data-bearing methods
 * (getTipData*, getPosition, updateText and the setters that call it) reach the
 * native PolylineData/interpreter runtime and are deliberately NOT exercised;
 * everything tested here is pure in-memory state.
 */
public class DatatipTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorDatatipDefaults() {
        Datatip d = new Datatip();
        assertEquals("xy", d.getDisplayComponents());
        assertTrue(d.isAutoOrientationEnabled());
        assertEquals(TipOrientation.TOP_RIGHT, d.getOrientationAsEnum());
        assertEquals(Integer.valueOf(1), d.getOrientation()); // TOP_RIGHT ordinal
        assertTrue(d.getTipBoxMode());
        assertTrue(d.getTipLabelMode());
        assertTrue(d.getInterpMode());
        assertEquals("", d.getDisplayFunction());
        assertFalse(d.getDetachedMode());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, d.getDetachedPosition());
    }

    @Test
    public void constructorConfiguresInheritedContourAndClipState() {
        Datatip d = new Datatip();
        assertTrue(d.getBox());
        assertTrue(d.getVisible());
        assertTrue(d.getFillMode());
        assertTrue(d.getMarkMode());
        assertEquals(Integer.valueOf(3), d.getLineStyle());
        assertEquals(Integer.valueOf(-2), d.getBackground());
        assertEquals(Integer.valueOf(8), d.getMarkSize());
        assertEquals(Integer.valueOf(11), d.getMarkStyle());
        assertEquals(Integer.valueOf(-1), d.getMarkBackground());
        assertEquals(Integer.valueOf(-1), d.getMarkForeground());
        assertEquals(ClipStateType.OFF, d.getClipStateAsEnum());
    }

    @Test
    public void typeIsDatatip() {
        assertEquals(GraphicObjectProperties.__GO_DATATIP__, new Datatip().getType());
    }

    @Test
    public void defaultIndexesAreMinValueAndZeroRatio() {
        Datatip d = new Datatip();
        Double[] idx = d.getIndexes();
        assertEquals(2, idx.length);
        assertEquals((double) Integer.MIN_VALUE, idx[0], EPS);
        assertEquals(0.0, idx[1], EPS);
    }

    @Test
    public void setOrientationMapsIntegerViaTipOrientation() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setOrientation(0));
        assertEquals(TipOrientation.TOP_LEFT, d.getOrientationAsEnum());
        assertEquals(Integer.valueOf(0), d.getOrientation());
        // Out-of-range folds to the TOP_RIGHT default.
        d.setOrientation(99);
        assertEquals(TipOrientation.TOP_RIGHT, d.getOrientationAsEnum());
    }

    @Test
    public void setOrientationAsEnumRoundTrips() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setOrientationAsEnum(TipOrientation.BOTTOM));
        assertEquals(TipOrientation.BOTTOM, d.getOrientationAsEnum());
        assertEquals(Integer.valueOf(7), d.getOrientation());
    }

    @Test
    public void tipOrientationIntToEnumCoversAllAndDefaults() {
        assertEquals(TipOrientation.TOP_LEFT, TipOrientation.intToEnum(0));
        assertEquals(TipOrientation.TOP_RIGHT, TipOrientation.intToEnum(1));
        assertEquals(TipOrientation.BOTTOM_LEFT, TipOrientation.intToEnum(2));
        assertEquals(TipOrientation.BOTTOM_RIGHT, TipOrientation.intToEnum(3));
        assertEquals(TipOrientation.LEFT, TipOrientation.intToEnum(4));
        assertEquals(TipOrientation.RIGHT, TipOrientation.intToEnum(5));
        assertEquals(TipOrientation.TOP, TipOrientation.intToEnum(6));
        assertEquals(TipOrientation.BOTTOM, TipOrientation.intToEnum(7));
        // Unknown -> TOP_RIGHT default.
        assertEquals(TipOrientation.TOP_RIGHT, TipOrientation.intToEnum(42));
    }

    @Test
    public void autoOrientationRoundTrips() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setAutoOrientation(false));
        assertFalse(d.isAutoOrientationEnabled());
    }

    @Test
    public void setTipBoxModeAlsoMirrorsIntoBox() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setTipBoxMode(false));
        assertFalse(d.getTipBoxMode());
        // setTipBoxMode also drives the inherited box flag.
        assertFalse(d.getBox());
    }

    @Test
    public void labelAndInterpModeRoundTrip() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setTipLabelMode(false));
        assertFalse(d.getTipLabelMode());
        assertEquals(UpdateStatus.Success, d.setInterpMode(false));
        assertFalse(d.getInterpMode());
    }

    @Test
    public void detachedModeAndPositionValidation() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success, d.setDetachedMode(true));
        assertTrue(d.getDetachedMode());

        assertEquals(UpdateStatus.Success, d.setDetachedPosition(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, d.getDetachedPosition());
        // Wrong length is rejected.
        assertEquals(UpdateStatus.Fail, d.setDetachedPosition(new Double[] {1.0, 2.0}));
    }

    @Test
    public void setDisplayComponentsRejectsInvalidStringsWithoutRendering() {
        // The Fail paths return before updateText(), so they are hermetic. Valid
        // strings would trigger the native text update and are not tested here.
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Fail, d.setDisplayComponents(null));
        assertEquals(UpdateStatus.Fail, d.setDisplayComponents(""));       // too short
        assertEquals(UpdateStatus.Fail, d.setDisplayComponents("xyzt"));   // too long
        assertEquals(UpdateStatus.Fail, d.setDisplayComponents("xx"));     // duplicate
        assertEquals(UpdateStatus.Fail, d.setDisplayComponents("w"));      // invalid char
        // The default is left intact after all the rejected sets.
        assertEquals("xy", d.getDisplayComponents());
    }

    @Test
    public void getPropertyDispatchesForPureProperties() {
        Datatip d = new Datatip();
        assertEquals(Integer.valueOf(1), d.getProperty(DatatipObjectProperty.TIP_ORIENTATION));
        assertEquals(Boolean.TRUE, d.getProperty(DatatipObjectProperty.TIP_BOX_MODE));
        assertEquals(Boolean.TRUE, d.getProperty(DatatipObjectProperty.TIP_LABEL_MODE));
        assertEquals(Boolean.TRUE, d.getProperty(DatatipObjectProperty.TIP_INTERP_MODE));
        assertEquals("xy", d.getProperty(DatatipObjectProperty.TIP_DISPLAY_COMPONENTS));
        assertEquals(Boolean.TRUE, d.getProperty(DatatipObjectProperty.TIP_AUTOORIENTATION));
        assertEquals(Boolean.FALSE, d.getProperty(DatatipObjectProperty.TIP_DETACHED_MODE));
    }

    @Test
    public void setPropertyDispatchesForPureProperties() {
        Datatip d = new Datatip();
        assertEquals(UpdateStatus.Success,
                     d.setProperty(DatatipObjectProperty.TIP_ORIENTATION, Integer.valueOf(2)));
        assertEquals(TipOrientation.BOTTOM_LEFT, d.getOrientationAsEnum());

        assertEquals(UpdateStatus.Success,
                     d.setProperty(DatatipObjectProperty.TIP_BOX_MODE, Boolean.FALSE));
        assertFalse(d.getTipBoxMode());

        assertEquals(UpdateStatus.Success,
                     d.setProperty(DatatipObjectProperty.TIP_DETACHED_MODE, Boolean.TRUE));
        assertTrue(d.getDetachedMode());
    }

    @Test
    public void getPropertyFromNameMapsDatatipKeys() {
        Datatip d = new Datatip();
        assertEquals(DatatipObjectProperty.TIP_ORIENTATION,
                     d.getPropertyFromName(GraphicObjectProperties.__GO_DATATIP_ORIENTATION__));
        assertEquals(DatatipObjectProperty.TIP_BOX_MODE,
                     d.getPropertyFromName(GraphicObjectProperties.__GO_DATATIP_BOX_MODE__));
    }
}
