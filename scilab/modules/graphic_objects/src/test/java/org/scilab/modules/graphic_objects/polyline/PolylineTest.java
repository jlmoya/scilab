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

package org.scilab.modules.graphic_objects.polyline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.polyline.Polyline.DatatipDisplayMode;
import org.scilab.modules.graphic_objects.graphicObject.ClippableProperty.ClipStateType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Polyline}. The datatip-refreshing methods
 * (updateDatatips, setDisplayFunction) reach the native GraphicController and
 * are NOT exercised; everything tested here is pure in-memory state.
 */
public class PolylineTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorDefaults() {
        Polyline p = new Polyline();
        assertFalse(p.getClosed());
        assertEquals(1.0, p.getArrowSizeFactor(), EPS);
        assertEquals(Integer.valueOf(1), p.getPolylineStyle());
        assertArrayEquals(new Integer[] {0, 0, 0, 0}, p.getInterpColorVector());
        assertFalse(p.getInterpColorVectorSet());
        assertFalse(p.getInterpColorMode());
        assertNull(p.getXShift());
        assertNull(p.getYShift());
        assertNull(p.getZShift());
        assertEquals(0.0, p.getBarWidth(), EPS);
        assertEquals(0, p.getDatatips().length);
        assertEquals("", p.getDisplayFunction());
        assertEquals(Integer.valueOf(11), p.getTipMark());
        assertFalse(p.getColorSet());
        assertEquals(DatatipDisplayMode.ALWAYS, p.getDatatipDisplayModeAsEnum());
    }

    @Test
    public void typeIsPolyline() {
        assertEquals(GraphicObjectProperties.__GO_POLYLINE__, new Polyline().getType());
    }

    @Test
    public void closedRoundTrips() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setClosed(true));
        assertTrue(p.getClosed());
    }

    @Test
    public void arrowSizeFactorTracksChange() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.NoChange, p.setArrowSizeFactor(1.0));
        assertEquals(UpdateStatus.Success, p.setArrowSizeFactor(2.5));
        assertEquals(2.5, p.getArrowSizeFactor(), EPS);
    }

    @Test
    public void polylineStyleTracksChange() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.NoChange, p.setPolylineStyle(1));
        assertEquals(UpdateStatus.Success, p.setPolylineStyle(5));
        assertEquals(Integer.valueOf(5), p.getPolylineStyle());
    }

    @Test
    public void interpColorModeTracksChange() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.NoChange, p.setInterpColorMode(false));
        assertEquals(UpdateStatus.Success, p.setInterpColorMode(true));
        assertTrue(p.getInterpColorMode());
    }

    @Test
    public void interpColorVectorStoresAndFlipsSetFlag() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setInterpColorVector(new Integer[] {1, 2, 3, 4}));
        assertArrayEquals(new Integer[] {1, 2, 3, 4}, p.getInterpColorVector());
        assertTrue(p.getInterpColorVectorSet());
    }

    @Test
    public void interpColorVectorSetRoundTrips() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setInterpColorVectorSet(true));
        assertTrue(p.getInterpColorVectorSet());
    }

    @Test
    public void shiftsRoundTrip() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setXShift(new double[] {1.0, 2.0}));
        assertArrayEquals(new double[] {1.0, 2.0}, p.getXShift(), EPS);
        p.setYShift(new double[] {3.0});
        assertArrayEquals(new double[] {3.0}, p.getYShift(), EPS);
        p.setZShift(new double[] {4.0, 5.0, 6.0});
        assertArrayEquals(new double[] {4.0, 5.0, 6.0}, p.getZShift(), EPS);
    }

    @Test
    public void barWidthRoundTrips() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setBarWidth(0.8));
        assertEquals(0.8, p.getBarWidth(), EPS);
    }

    @Test
    public void datatipsListRoundTrips() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setDatatips(new Integer[] {7, 8, 9}));
        assertArrayEquals(new Integer[] {7, 8, 9}, p.getDatatips());
    }

    @Test
    public void tipMarkTracksChange() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.NoChange, p.setTipMark(11));
        assertEquals(UpdateStatus.Success, p.setTipMark(3));
        assertEquals(Integer.valueOf(3), p.getTipMark());
    }

    @Test
    public void colorSetRoundTrips() {
        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setColorSet(true));
        assertTrue(p.getColorSet());
    }

    @Test
    public void datatipDisplayModeConvertersAndRoundTrip() {
        assertEquals(DatatipDisplayMode.ALWAYS, DatatipDisplayMode.intToEnum(0));
        assertEquals(DatatipDisplayMode.MOUSECLICK, DatatipDisplayMode.intToEnum(1));
        assertEquals(DatatipDisplayMode.MOUSEOVER, DatatipDisplayMode.intToEnum(2));
        // Unknown values fall back to ALWAYS.
        assertEquals(DatatipDisplayMode.ALWAYS, DatatipDisplayMode.intToEnum(7));

        Polyline p = new Polyline();
        assertEquals(UpdateStatus.Success, p.setDatatipDisplayMode(1));
        assertEquals(Integer.valueOf(1), p.getDatatipDisplayMode());
        assertEquals(DatatipDisplayMode.MOUSECLICK, p.getDatatipDisplayModeAsEnum());
        assertEquals(UpdateStatus.Success, p.setDatatipDisplayModeAsEnum(DatatipDisplayMode.MOUSEOVER));
        assertEquals(DatatipDisplayMode.MOUSEOVER, p.getDatatipDisplayModeAsEnum());
    }

    @Test
    public void inheritedClipStateIsHermetic() {
        Polyline p = new Polyline();
        assertEquals(ClipStateType.OFF, p.getClipStateAsEnum());
        assertFalse(p.getClipBoxSet());
        assertEquals(UpdateStatus.Success, p.setClipBox(new Double[] {0.0, 0.0, 1.0, 1.0}));
        assertTrue(p.getClipBoxSet());
    }

    @Test
    public void getPropertyDispatchesForPureProperties() {
        Polyline p = new Polyline();
        Object closedKey = p.getPropertyFromName(GraphicObjectProperties.__GO_CLOSED__);
        p.setProperty(closedKey, Boolean.TRUE);
        assertEquals(Boolean.TRUE, p.getProperty(closedKey));

        Object styleKey = p.getPropertyFromName(GraphicObjectProperties.__GO_POLYLINE_STYLE__);
        assertEquals(UpdateStatus.Success, p.setProperty(styleKey, Integer.valueOf(4)));
        assertEquals(Integer.valueOf(4), p.getProperty(styleKey));

        Object arrowKey = p.getPropertyFromName(GraphicObjectProperties.__GO_ARROW_SIZE_FACTOR__);
        assertEquals(UpdateStatus.Success, p.setProperty(arrowKey, Double.valueOf(3.0)));
        assertEquals(3.0, (Double) p.getProperty(arrowKey), EPS);
    }
}
