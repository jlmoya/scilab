/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 *
 */

package org.scilab.forge.scirenderer.texture;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link TextureBufferedImage}. A {@link BufferedImage} of type
 * {@code TYPE_INT_ARGB} can be created and inspected with no display, so the RGBA
 * repacking helpers can be pinned exactly. The unused private {@code updateFrame} Swing
 * path is never touched.
 */
public class TextureBufferedImageTest {

    // A pixel with four distinct component bytes so any mis-ordering is visible.
    // Stored as ARGB: A=0x11, R=0x22, G=0x33, B=0x44.
    private static final int ARGB = 0x11223344;
    // The helpers keep A and G in place and swap the R and B bytes.
    private static final int PACKED = 0x11443322;

    @Test
    public void constructorBuildsAnArgbImageOfTheRequestedSize() {
        TextureBufferedImage img = new TextureBufferedImage(3, 2);
        assertEquals(3, img.getWidth());
        assertEquals(2, img.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, img.getType());
    }

    @Test
    public void getRGBADataSwapsRedAndBlueKeepingAlphaAndGreen() {
        TextureBufferedImage img = new TextureBufferedImage(2, 2);
        img.setRGB(0, 0, ARGB);
        int[] data = img.getRGBAData();
        assertEquals(PACKED, data[0]);
    }

    @Test
    public void getRGBADataReturnsTheLiveBackingArrayAndMutatesInPlace() {
        // Defect characterization: getRGBAData() rewrites the image's own raster and hands
        // back the live int[] backing store, so calling it is a destructive operation.
        TextureBufferedImage img = new TextureBufferedImage(2, 2);
        img.setRGB(0, 0, ARGB);

        int[] first = img.getRGBAData();
        assertEquals(PACKED, first[0]);
        // The pixel actually stored in the image has changed.
        assertEquals(PACKED, img.getRGB(0, 0));

        int[] second = img.getRGBAData();
        assertSame(first, second, "the same backing array is returned each time");
        // The swap is its own inverse, so a second call restores the original ARGB value.
        assertEquals(ARGB, second[0]);
        assertEquals(ARGB, img.getRGB(0, 0));
    }

    @Test
    public void getRGBABufferProducesTheSamePackingWithoutMutatingTheImage() {
        TextureBufferedImage img = new TextureBufferedImage(2, 2);
        img.setRGB(0, 0, ARGB);

        ByteBuffer buffer = img.getRGBABuffer();
        // 2x2 pixels, 4 bytes each.
        assertEquals(16, buffer.capacity());
        assertEquals(PACKED, buffer.getInt(0));

        // Unlike getRGBAData(), this path leaves the source image untouched.
        assertEquals(ARGB, img.getRGB(0, 0));
    }

    @Test
    public void getRGBABufferIsPositionedAtTheStart() {
        TextureBufferedImage img = new TextureBufferedImage(2, 2);
        ByteBuffer buffer = img.getRGBABuffer();
        assertEquals(0, buffer.position(), "buffer is rewound before being returned");
    }
}
