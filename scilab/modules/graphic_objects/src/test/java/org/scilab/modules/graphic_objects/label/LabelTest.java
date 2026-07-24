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

package org.scilab.modules.graphic_objects.label;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.scilab.modules.graphic_objects.label.Label.LabelProperty;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Label}: a text object carrying an angle,
 * position and four corners, with auto-position / auto-rotation flags that are
 * cleared when their value is set explicitly.
 */
public class LabelTest {

    private static final double EPS = 1e-12;

    @Test
    public void constructorDefaults() {
        Label l = new Label();
        assertEquals(0.0, l.getFontAngle(), EPS);
        assertFalse(l.getAutoPosition());
        assertFalse(l.getAutoRotation());
        assertArrayEquals(new Double[] {0.0, 0.0, 0.0}, l.getPosition());
        // Constructor seeds a 1x1 array with a single empty string.
        assertArrayEquals(new Integer[] {1, 1}, l.getTextArrayDimensions());
        assertArrayEquals(new String[] {""}, l.getTextStrings());
    }

    @Test
    public void typeIsLabel() {
        assertEquals(GraphicObjectProperties.__GO_LABEL__, new Label().getType());
    }

    @Test
    public void settingFontAngleClearsAutoRotation() {
        Label l = new Label();
        l.setAutoRotation(true);
        assertTrue(l.getAutoRotation());
        assertEquals(UpdateStatus.Success, l.setFontAngle(0.75));
        assertEquals(0.75, l.getFontAngle(), EPS);
        // Explicitly setting the angle disables automatic rotation.
        assertFalse(l.getAutoRotation());
    }

    @Test
    public void settingPositionClearsAutoPosition() {
        Label l = new Label();
        l.setAutoPosition(true);
        assertTrue(l.getAutoPosition());
        assertEquals(UpdateStatus.Success, l.setPosition(new Double[] {1.0, 2.0, 3.0}));
        assertArrayEquals(new Double[] {1.0, 2.0, 3.0}, l.getPosition());
        assertFalse(l.getAutoPosition());
    }

    @Test
    public void autoFlagsRoundTrip() {
        Label l = new Label();
        assertEquals(UpdateStatus.Success, l.setAutoPosition(true));
        assertTrue(l.getAutoPosition());
        assertEquals(UpdateStatus.Success, l.setAutoRotation(true));
        assertTrue(l.getAutoRotation());
    }

    @Test
    public void cornersRequireExactlyTwelveValues() {
        Label l = new Label();
        assertEquals(UpdateStatus.NoChange, l.setCorners(new Double[] {1.0, 2.0}));
        Double[] twelve = new Double[12];
        for (int i = 0; i < 12; i++) {
            twelve[i] = (double) (i + 1);
        }
        assertEquals(UpdateStatus.Success, l.setCorners(twelve));
        assertArrayEquals(twelve, l.getCorners());
    }

    @Test
    public void propertyDispatchRoundTripsForAutoRotation() {
        Label l = new Label();
        assertEquals(LabelProperty.AUTOROTATION,
                     l.getPropertyFromName(GraphicObjectProperties.__GO_AUTO_ROTATION__));
        assertEquals(UpdateStatus.Success, l.setProperty(LabelProperty.AUTOROTATION, Boolean.TRUE));
        assertEquals(Boolean.TRUE, l.getProperty(LabelProperty.AUTOROTATION));
    }

    @Test
    public void propertyDispatchRoundTripsForFontAngle() {
        Label l = new Label();
        assertEquals(LabelProperty.FONTANGLE,
                     l.getPropertyFromName(GraphicObjectProperties.__GO_FONT_ANGLE__));
        l.setProperty(LabelProperty.FONTANGLE, Double.valueOf(2.0));
        assertEquals(2.0, (Double) l.getProperty(LabelProperty.FONTANGLE), EPS);
    }

    @Test
    public void cloneCopiesPositionIndependently() {
        Label l = new Label();
        l.setPosition(new Double[] {5.0, 6.0, 7.0});
        l.setFontStyle(9);

        Label copy = l.clone();
        assertNotSame(l, copy);
        assertArrayEquals(new Double[] {5.0, 6.0, 7.0}, copy.getPosition());
        assertEquals(Integer.valueOf(9), copy.getFontStyle());

        // Mutating the clone's position must not affect the original.
        copy.setPosition(new Double[] {0.0, 0.0, 0.0});
        assertArrayEquals(new Double[] {5.0, 6.0, 7.0}, l.getPosition());
    }
}
