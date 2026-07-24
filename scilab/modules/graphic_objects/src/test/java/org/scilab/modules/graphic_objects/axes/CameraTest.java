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

import org.scilab.modules.graphic_objects.axes.Camera.ViewType;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObject.UpdateStatus;

/**
 * Hermetic unit tests for {@link Camera}: the projection/rotation holder of an
 * Axes object, including the 2D/3D view state machine and its {@link ViewType}
 * converter.
 */
public class CameraTest {

    @Test
    public void viewTypeIntToEnum() {
        assertEquals(ViewType.VIEW_2D, ViewType.intToEnum(0));
        assertEquals(ViewType.VIEW_3D, ViewType.intToEnum(1));
        assertNull(ViewType.intToEnum(2));
        assertNull(ViewType.intToEnum(-1));
    }

    @Test
    public void constructorDefaults() {
        Camera c = new Camera();
        assertEquals(ViewType.VIEW_2D, c.getView());
        assertFalse(c.getIsoview());
        assertFalse(c.getCubeScaling());
        assertArrayEquals(new Double[] {0.0, 0.0}, c.getRotationAngles());
        assertArrayEquals(new Double[] {0.0, 0.0}, c.getRotationAngles3d());
    }

    @Test
    public void defaultRotationAnglesConstant() {
        assertArrayEquals(new double[] {0.0, 270.0}, Camera.DEFAULT_ROTATION_ANGLES);
    }

    @Test
    public void freshCamerasAreEqualAndCopyEqualsOriginal() {
        Camera a = new Camera();
        Camera b = new Camera();
        assertEquals(a, b);

        a.setCubeScaling(true);
        a.setIsoview(true);
        Camera copy = new Camera(a);
        assertEquals(a, copy);
    }

    @Test
    public void equalsRejectsNullOtherTypeAndDifferingField() {
        Camera a = new Camera();
        assertNotEquals(a, null);
        assertNotEquals(a, "camera");

        Camera b = new Camera();
        b.setIsoview(true);
        assertNotEquals(a, b);
    }

    @Test
    public void isoviewAndCubeScalingRoundTrip() {
        Camera c = new Camera();
        assertEquals(UpdateStatus.Success, c.setIsoview(true));
        assertTrue(c.getIsoview());
        assertEquals(UpdateStatus.NoChange, c.setIsoview(true));

        assertEquals(UpdateStatus.Success, c.setCubeScaling(true));
        assertTrue(c.getCubeScaling());
        assertEquals(UpdateStatus.NoChange, c.setCubeScaling(true));
    }

    @Test
    public void settingSameViewIsNoChange() {
        Camera c = new Camera();
        assertEquals(UpdateStatus.NoChange, c.setView(ViewType.VIEW_2D));
    }

    @Test
    public void switchingToThreeDThenBackResetsAnglesToDefault() {
        Camera c = new Camera();
        assertEquals(UpdateStatus.Success, c.setView(ViewType.VIEW_3D));
        assertEquals(ViewType.VIEW_3D, c.getView());

        // Returning to 2D parks the current angles as the saved 3D angles and
        // restores the default (0, 270) rotation.
        assertEquals(UpdateStatus.Success, c.setView(ViewType.VIEW_2D));
        assertEquals(ViewType.VIEW_2D, c.getView());
        assertArrayEquals(new Double[] {0.0, 270.0}, c.getRotationAngles());
    }

    @Test
    public void nonDefaultRotationFromTwoDPromotesToThreeD() {
        Camera c = new Camera();
        assertTrue(c.setRotationAngles(new Double[] {45.0, 60.0}));
        assertEquals(ViewType.VIEW_3D, c.getView());
        assertArrayEquals(new Double[] {45.0, 60.0}, c.getRotationAngles());

        // Setting the same angles again reports "no change" (returns false).
        assertFalse(c.setRotationAngles(new Double[] {45.0, 60.0}));
    }

    @Test
    public void settingDefaultRotationKeepsTwoDView() {
        Camera c = new Camera();
        // (0, 270) is the default view orientation, so the view stays 2D.
        assertTrue(c.setRotationAngles(new Double[] {0.0, 270.0}));
        assertEquals(ViewType.VIEW_2D, c.getView());
    }

    @Test
    public void rotationAngles3dRoundTrips() {
        Camera c = new Camera();
        assertEquals(UpdateStatus.Success, c.setRotationAngles3d(new Double[] {10.0, 20.0}));
        assertArrayEquals(new Double[] {10.0, 20.0}, c.getRotationAngles3d());
        assertEquals(UpdateStatus.NoChange, c.setRotationAngles3d(new Double[] {10.0, 20.0}));
    }
}
