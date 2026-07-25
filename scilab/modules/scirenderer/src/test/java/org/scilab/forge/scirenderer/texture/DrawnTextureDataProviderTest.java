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

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link DrawnTextureDataProvider}. Both the
 * no-drawer ("invalid") state and the valid state (backed by a headless
 * {@link NoOpTextureDrawer} rendered into a {@link TextureBufferedImage}) are
 * exercised. No display or GPU resources are touched.
 */
public class DrawnTextureDataProviderTest {

    @Test
    public void metadataIsConstant() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(null);
        assertEquals(TextureDataProvider.ImageType.RGBA_BYTE, provider.getImageType());
        assertTrue(provider.isRowMajorOrder());
    }

    @Test
    public void aNullDrawerIsInvalid() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(null);
        assertFalse(provider.isValid());
    }

    @Test
    public void invalidProviderReportsSentinelSizeAndNoData() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(null);
        assertEquals(new Dimension(-1, -1), provider.getTextureSize());
        assertNull(provider.getData());
        assertNull(provider.getImage());
        assertNull(provider.getSubData(0, 0, 1, 1));
        assertNull(provider.getSubImage(0, 0, 1, 1));
    }

    @Test
    public void aNonNullDrawerIsValidAndReportsItsSize() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(new NoOpTextureDrawer(new Dimension(4, 4)));
        assertTrue(provider.isValid());
        assertEquals(new Dimension(4, 4), provider.getTextureSize());
    }

    @Test
    public void validProviderRendersAnImageOfTheRequestedSize() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(new NoOpTextureDrawer(new Dimension(4, 4)));
        BufferedImage image = provider.getImage();
        assertNotNull(image);
        assertEquals(4, image.getWidth());
        assertEquals(4, image.getHeight());
    }

    @Test
    public void validProviderProducesAFourBytePerPixelBuffer() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(new NoOpTextureDrawer(new Dimension(4, 4)));
        ByteBuffer data = provider.getData();
        assertNotNull(data);
        // 4 x 4 pixels x 4 bytes (RGBA).
        assertEquals(4 * 4 * 4, data.capacity());
    }

    @Test
    public void getSubImageExtractsARegionFromTheRenderedImage() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(new NoOpTextureDrawer(new Dimension(4, 4)));
        BufferedImage sub = provider.getSubImage(0, 0, 2, 2);
        assertEquals(2, sub.getWidth());
        assertEquals(2, sub.getHeight());
    }

    @Test
    public void settingANewDrawerReDrawsAtTheNewSize() {
        DrawnTextureDataProvider provider = new DrawnTextureDataProvider(new NoOpTextureDrawer(new Dimension(4, 4)));
        provider.setTextureDrawingTools(new NoOpTextureDrawer(new Dimension(2, 8)));
        assertEquals(new Dimension(2, 8), provider.getTextureSize());
        assertEquals(2, provider.getImage().getWidth());
        assertEquals(8, provider.getImage().getHeight());
    }
}
