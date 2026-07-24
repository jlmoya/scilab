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

package org.scilab.modules.graphic_objects.uicontrol.uiimage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UICONTROL__;
import static org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties.__GO_UI_IMAGE__;

import org.scilab.modules.graphic_objects.uicontrol.Uicontrol.UicontrolStyle;

/**
 * Hermetic unit tests for {@link UiImage}, a Uicontrol whose constructor seeds
 * the "value" vector with the affine image transform
 * {XScale, YScale, XShear, YShear, RotationAngle}.
 */
public class UiImageTest {

    @Test
    public void styleIsImage() {
        UiImage img = new UiImage();
        assertEquals(Integer.valueOf(__GO_UI_IMAGE__), img.getStyle());
        assertEquals(UicontrolStyle.IMAGE, img.getStyleAsEnum());
    }

    @Test
    public void typeInheritsGenericUicontrol() {
        assertEquals(Integer.valueOf(__GO_UICONTROL__), new UiImage().getType());
    }

    @Test
    public void constructorSeedsIdentityImageTransform() {
        UiImage img = new UiImage();
        // {XScale=1, YScale=1, XShear=0, YShear=0, RotationAngle=0}
        assertArrayEquals(new Double[] {1.0, 1.0, 0.0, 0.0, 0.0}, img.getUiValue());
        assertEquals(Integer.valueOf(5), img.getUiValueSize());
    }

    @Test
    public void constructorSetsAlignment() {
        UiImage img = new UiImage();
        assertEquals("left", img.getHorizontalAlignment());
        assertEquals("middle", img.getVerticalAlignment());
    }

    @Test
    public void valueIsSettable() {
        UiImage img = new UiImage();
        Double[] transform = {2.0, 3.0, 0.5, 0.25, 90.0};
        img.setUiValue(transform);
        assertArrayEquals(transform, img.getUiValue());
    }
}
