/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.modules.commons.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/**
 * The dangerous half of this class is {@link ThemedIcons#isMonochrome}: if it ever
 * says yes to a colour icon, that icon is silently flattened to a single colour and
 * the artwork is destroyed with no error anywhere. These tests pin that boundary.
 */
public class ThemedIconsTest {

    private static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    public void blackOnAlphaArtworkIsMonochrome() {
        BufferedImage img = image(4, 4);
        img.setRGB(1, 1, 0xFF000000);   // opaque black, as the real toolbar icons are
        img.setRGB(2, 2, 0x80000000);   // half-transparent black (anti-aliased edge)
        assertTrue(ThemedIcons.isMonochrome(img));
    }

    @Test
    public void greyArtworkIsStillMonochrome() {
        BufferedImage img = image(2, 2);
        img.setRGB(0, 0, 0xFF404040);
        img.setRGB(1, 1, 0xFFC0C0C0);
        assertTrue(ThemedIcons.isMonochrome(img));
    }

    @Test
    public void aColouredIconIsNotMonochromeAndMustNotBeFlattened() {
        BufferedImage img = image(2, 2);
        img.setRGB(0, 0, 0xFF000000);
        img.setRGB(1, 1, 0xFFCC2200);   // a strongly chromatic pixel is enough
        assertFalse(ThemedIcons.isMonochrome(img));
    }

    @Test
    public void aFullyTransparentImageHasNothingToRecolour() {
        assertFalse(ThemedIcons.isMonochrome(image(3, 3)));
    }

    @Test
    public void tintReplacesColourButPreservesEveryAlphaValue() {
        BufferedImage img = image(3, 1);
        img.setRGB(0, 0, 0x00000000);   // transparent
        img.setRGB(1, 0, 0x80000000);   // half
        img.setRGB(2, 0, 0xFF000000);   // opaque

        BufferedImage out = ThemedIcons.tint(img, new Color(0xDD, 0xDD, 0xDD));

        // alpha survives: that is what keeps the glyph shape and its soft edges
        assertEquals(0x00, (out.getRGB(0, 0) >>> 24) & 0xFF);
        assertEquals(0x80, (out.getRGB(1, 0) >>> 24) & 0xFF);
        assertEquals(0xFF, (out.getRGB(2, 0) >>> 24) & 0xFF);

        // and every pixel now carries the requested colour
        for (int x = 0; x < 3; x++) {
            assertEquals(0xDDDDDD, out.getRGB(x, 0) & 0x00FFFFFF, "pixel " + x);
        }
    }

    @Test
    public void tintOnALightThemeColourKeepsTheIconDark() {
        BufferedImage img = image(1, 1);
        img.setRGB(0, 0, 0xFF000000);
        BufferedImage out = ThemedIcons.tint(img, new Color(0x26, 0x26, 0x26));
        assertEquals(0x262626, out.getRGB(0, 0) & 0x00FFFFFF);
    }
}
