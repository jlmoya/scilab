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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

public class ContrastColorsTest {

    private static final Color DARK_BG = new Color(0x28, 0x28, 0x28);
    private static final Color LIGHT_BG = Color.WHITE;

    @Test
    public void contrastIsSymmetricAndSpansTheKnownRange() {
        assertEquals(21.0, ContrastColors.contrast(Color.BLACK, Color.WHITE), 0.05);
        assertEquals(ContrastColors.contrast(Color.BLACK, Color.WHITE),
                     ContrastColors.contrast(Color.WHITE, Color.BLACK), 1e-9);
        assertEquals(1.0, ContrastColors.contrast(Color.RED, Color.RED), 1e-9);
    }

    /**
     * The whole point: a light theme must not change. Every SciNotes default already
     * passes on white, so readable() has to hand each one back untouched.
     */
    @Test
    public void colorsThatAlreadyPassAreReturnedIdentically() {
        for (Color c : new Color[] {Color.BLACK, new Color(0x5C, 0x5C, 0x5C),
                                    new Color(0x00, 0x00, 0xFF), new Color(0x8B, 0x22, 0x52)}) {
            assertSame(c, ContrastColors.readable(c, LIGHT_BG),
                       "already readable on white, must not be altered: " + c);
        }
    }

    @Test
    public void blackBecomesReadableOnADarkBackground() {
        Color out = ContrastColors.readable(Color.BLACK, DARK_BG);
        assertTrue(ContrastColors.contrast(out, DARK_BG) >= ContrastColors.MIN_CONTRAST,
                   "still unreadable: " + out);
        assertTrue(ContrastColors.luminance(out) > ContrastColors.luminance(Color.BLACK),
                   "should have been lightened");
    }

    /**
     * A red keyword must still look red after being made readable.
     *
     * HUE is the invariant, not saturation: reaching the target sometimes requires
     * washing a colour towards white (#B01813 is such a case -- raising brightness to
     * the maximum still leaves it below 4.5:1 on the dark editor). Saturation may
     * therefore fall, but it must never RISE, because intensifying a colour the user
     * chose would be inventing a different one.
     */
    @Test
    public void hueIsPreservedAndSaturationNeverIncreases() {
        for (int rgb : new int[] {0xB01813, 0xFF0000, 0x0000FF, 0x8B2252, 0x834310}) {
            Color in = new Color(rgb);
            Color out = ContrastColors.readable(in, DARK_BG);
            float[] before = Color.RGBtoHSB(in.getRed(), in.getGreen(), in.getBlue(), null);
            float[] after = Color.RGBtoHSB(out.getRed(), out.getGreen(), out.getBlue(), null);
            String tag = String.format("#%06X", rgb);
            assertEquals(before[0], after[0], 0.02f, "hue drifted for " + tag);
            assertTrue(after[1] <= before[1] + 0.02f, "saturation increased for " + tag);
            assertTrue(ContrastColors.contrast(out, DARK_BG) >= ContrastColors.MIN_CONTRAST,
                       "not readable: " + tag);
        }
    }

    @Test
    public void aLightColorIsDarkenedOnALightBackground() {
        Color pale = new Color(0xF5, 0xF5, 0xF5);
        Color out = ContrastColors.readable(pale, LIGHT_BG);
        assertTrue(ContrastColors.contrast(out, LIGHT_BG) >= ContrastColors.MIN_CONTRAST,
                   "still unreadable: " + out);
        assertTrue(ContrastColors.luminance(out) < ContrastColors.luminance(pale),
                   "should have been darkened");
    }

    @Test
    public void everyScinotesDefaultBecomesReadableOnTheDarkEditor() {
        // the real shipped palette, the reason this class exists
        int[] palette = {0x000000, 0x5C5C5C, 0x32B9B9, 0xAE5CB0, 0xBC8F8F, 0xFF0000,
                         0x834310, 0x64AE64, 0xAAAAAA, 0xB01813, 0x0000FF, 0x5F9EA0,
                         0xA020F0, 0xDA70D6, 0xDCDCDC, 0x8B2252};
        for (int rgb : palette) {
            Color out = ContrastColors.readable(new Color(rgb), DARK_BG);
            assertTrue(ContrastColors.contrast(out, DARK_BG) >= ContrastColors.MIN_CONTRAST,
                       String.format("#%06X -> #%06X still fails on the dark editor", rgb, out.getRGB() & 0xFFFFFF));
        }
    }

    @Test
    public void nullsAreToleratedRatherThanThrowing() {
        assertSame(null, ContrastColors.readable(null, DARK_BG));
        assertSame(Color.RED, ContrastColors.readable(Color.RED, null));
    }
}
