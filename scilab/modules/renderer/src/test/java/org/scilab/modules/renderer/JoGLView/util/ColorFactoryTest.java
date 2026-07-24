/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Jose Moya
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.renderer.JoGLView.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.modules.graphic_objects.figure.ColorMap;

/**
 * Hermetic unit tests for {@link ColorFactory}, which turns a Scilab
 * colour index into a scirenderer {@link Color} or an RGBA float array.
 * A real {@link ColorMap} is a plain model object (no controller/native
 * involvement), so these run without a display.
 *
 * Note on precision: {@code createColor} routes through java.awt.Color,
 * which quantises each channel to 8 bits, hence the 8-bit tolerance on
 * fractional channels. {@code createRGBAColor} copies the raw colormap
 * floats and is therefore exact.
 */
class ColorFactoryTest {

    /** Column-major colormap: reds, then greens, then blues. */
    private static ColorMap colorMapOf(Double[] data) {
        ColorMap cm = new ColorMap();
        cm.setData(data);
        return cm;
    }

    @Test
    void createColorWithNullColorMapIsOpaqueBlack() {
        Color c = ColorFactory.createColor(null, 12345);
        assertEquals(0.0f, c.getRedAsFloat(), 0.0f);
        assertEquals(0.0f, c.getGreenAsFloat(), 0.0f);
        assertEquals(0.0f, c.getBlueAsFloat(), 0.0f);
        assertEquals(1.0f, c.getAlphaAsFloat(), 0.0f);
    }

    @Test
    void createColorReadsTheRequestedScilabIndex() {
        // Two colours: index 1 = pure red, index 2 = pure green.
        ColorMap cm = colorMapOf(new Double[] {1.0, 0.0, /*R*/ 0.0, 1.0, /*G*/ 0.0, 0.0 /*B*/});

        Color first = ColorFactory.createColor(cm, 1);
        assertEquals(1.0f, first.getRedAsFloat(), 0.0f);
        assertEquals(0.0f, first.getGreenAsFloat(), 0.0f);
        assertEquals(0.0f, first.getBlueAsFloat(), 0.0f);

        Color second = ColorFactory.createColor(cm, 2);
        assertEquals(0.0f, second.getRedAsFloat(), 0.0f);
        assertEquals(1.0f, second.getGreenAsFloat(), 0.0f);
        assertEquals(0.0f, second.getBlueAsFloat(), 0.0f);
    }

    @Test
    void createColorPreservesFractionalChannelsWithin8BitTolerance() {
        ColorMap cm = colorMapOf(new Double[] {0.25, 0.5, 0.75});
        Color c = ColorFactory.createColor(cm, 1);
        assertEquals(0.25f, c.getRedAsFloat(), 1.0f / 255.0f);
        assertEquals(0.5f, c.getGreenAsFloat(), 1.0f / 255.0f);
        assertEquals(0.75f, c.getBlueAsFloat(), 1.0f / 255.0f);
        assertEquals(1.0f, c.getAlphaAsFloat(), 0.0f);
    }

    @Test
    void createRGBAColorAppendsOpaqueAlphaAndCopiesRawFloats() {
        ColorMap cm = colorMapOf(new Double[] {0.25, 0.5, 0.75});
        float[] rgba = ColorFactory.createRGBAColor(cm, 1);
        assertEquals(4, rgba.length);
        assertEquals(0.25f, rgba[0], 0.0f);
        assertEquals(0.5f, rgba[1], 0.0f);
        assertEquals(0.75f, rgba[2], 0.0f);
        assertEquals(1.0f, rgba[3], 0.0f);
    }

    @Test
    void createRGBAColorWithNullColorMapThrows() {
        // Unlike createColor, createRGBAColor dereferences the colormap
        // unconditionally; document that contract.
        assertThrows(NullPointerException.class, () -> ColorFactory.createRGBAColor(null, 1));
    }
}
