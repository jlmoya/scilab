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
import org.scilab.forge.scirenderer.texture.TextureDataProvider.ImageType;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link AbstractTextureDataProvider}, focused on its
 * concrete {@code getImage}/{@code getSubImage} RGBA-{@literal >}ARGB conversion.
 *
 * A concrete subclass supplies a fixed 2x2 RGBA byte buffer (row-major, four
 * bytes {@code R,G,B,A} per pixel) so the resulting {@link BufferedImage} pixels
 * can be asserted exactly.
 */
public class AbstractTextureDataProviderTest {

    private static final int W = 2;
    private static final int H = 2;

    /** 2x2 provider backed by a known buffer; {@code hasData=false} models an empty source. */
    private static final class FixedProvider extends AbstractTextureDataProvider {
        private final boolean hasData;

        FixedProvider(boolean hasData) {
            this.hasData = hasData;
            this.imageType = ImageType.RGBA_BYTE;
        }

        @Override
        public boolean isValid() {
            return hasData;
        }

        @Override
        public Dimension getTextureSize() {
            return new Dimension(W, H);
        }

        @Override
        public ByteBuffer getData() {
            if (!hasData) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.allocate(W * H * 4);
            putPixel(buffer, 255, 0, 0, 255);      // (0,0) opaque red
            putPixel(buffer, 0, 255, 0, 255);      // (1,0) opaque green
            putPixel(buffer, 0, 0, 255, 255);      // (0,1) opaque blue
            putPixel(buffer, 255, 255, 255, 128);  // (1,1) half-transparent white
            buffer.rewind();
            return buffer;
        }

        @Override
        public ByteBuffer getSubData(int x, int y, int width, int height) {
            return getData();
        }

        private static void putPixel(ByteBuffer b, int r, int g, int blue, int a) {
            b.put((byte) r);
            b.put((byte) g);
            b.put((byte) blue);
            b.put((byte) a);
        }
    }

    @Test
    public void reportsRowMajorOrderAndItsImageType() {
        FixedProvider provider = new FixedProvider(true);
        assertTrue(provider.isRowMajorOrder());
        assertEquals(ImageType.RGBA_BYTE, provider.getImageType());
    }

    @Test
    public void getImageConvertsRgbaBytesToArgbPixels() {
        BufferedImage image = new FixedProvider(true).getImage();
        assertEquals(BufferedImage.TYPE_INT_ARGB, image.getType());
        assertEquals(W, image.getWidth());
        assertEquals(H, image.getHeight());

        assertEquals(0xFFFF0000, image.getRGB(0, 0));  // red
        assertEquals(0xFF00FF00, image.getRGB(1, 0));  // green
        assertEquals(0xFF0000FF, image.getRGB(0, 1));  // blue
        assertEquals(0x80FFFFFF, image.getRGB(1, 1));  // 50% white
    }

    @Test
    public void getImageIsNullWhenThereIsNoData() {
        assertNull(new FixedProvider(false).getImage());
    }

    @Test
    public void getSubImageExtractsARegion() {
        BufferedImage sub = new FixedProvider(true).getSubImage(1, 0, 1, 1);
        assertEquals(1, sub.getWidth());
        assertEquals(1, sub.getHeight());
        assertEquals(0xFF00FF00, sub.getRGB(0, 0));  // the (1,0) green pixel
    }

    @Test
    public void getSubImageIsNullWhenThereIsNoData() {
        assertNull(new FixedProvider(false).getSubImage(0, 0, 1, 1));
    }
}
