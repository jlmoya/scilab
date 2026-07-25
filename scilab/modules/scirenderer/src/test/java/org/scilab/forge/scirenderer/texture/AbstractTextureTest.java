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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link AbstractTexture}: its default filter/wrap
 * state, the setter round-trips, validity delegation, and the data-provider
 * (un)registration wiring.
 */
public class AbstractTextureTest {

    /** A data provider that records user (un)registration and exposes a settable validity. */
    private static final class RecordingProvider implements TextureDataProvider {
        boolean valid;
        int added;
        int removed;
        Texture lastAdded;
        Texture lastRemoved;

        @Override
        public void addDataUser(Texture t) {
            added++;
            lastAdded = t;
        }
        @Override
        public void removeDataUser(Texture t) {
            removed++;
            lastRemoved = t;
        }
        @Override
        public boolean isValid() {
            return valid;
        }
        @Override
        public boolean isRowMajorOrder() {
            return true;
        }
        @Override
        public ImageType getImageType() {
            return ImageType.RGBA_BYTE;
        }
        @Override
        public Dimension getTextureSize() {
            return new Dimension(1, 1);
        }
        @Override
        public ByteBuffer getData() {
            return null;
        }
        @Override
        public ByteBuffer getSubData(int x, int y, int width, int height) {
            return null;
        }
        @Override
        public BufferedImage getImage() {
            return null;
        }
        @Override
        public BufferedImage getSubImage(int x, int y, int width, int height) {
            return null;
        }
    }

    @Test
    public void defaultFiltersAndWrapModes() {
        AbstractTexture texture = new AbstractTexture();
        assertEquals(Texture.Filter.NEAREST, texture.getMagnificationFilter());
        assertEquals(Texture.Filter.NEAREST, texture.getMinifyingFilter());
        assertEquals(Texture.Wrap.CLAMP, texture.getSWrappingMode());
        assertEquals(Texture.Wrap.CLAMP, texture.getTWrappingMode());
    }

    @Test
    public void scaleFactorsAreUnit() {
        AbstractTexture texture = new AbstractTexture();
        assertEquals(1.0, texture.getSScaleFactor(), 0.0);
        assertEquals(1.0, texture.getTScaleFactor(), 0.0);
    }

    @Test
    public void aTextureWithoutAProviderIsInvalid() {
        AbstractTexture texture = new AbstractTexture();
        assertNull(texture.getDataProvider());
        assertFalse(texture.isValid());
    }

    @Test
    public void filterAndWrapSettersRoundTrip() {
        AbstractTexture texture = new AbstractTexture();
        texture.setMagnificationFilter(Texture.Filter.LINEAR);
        texture.setMinifyingFilter(Texture.Filter.LINEAR);
        texture.setSWrappingMode(Texture.Wrap.REPEAT);
        texture.setTWrappingMode(Texture.Wrap.REPEAT);
        assertEquals(Texture.Filter.LINEAR, texture.getMagnificationFilter());
        assertEquals(Texture.Filter.LINEAR, texture.getMinifyingFilter());
        assertEquals(Texture.Wrap.REPEAT, texture.getSWrappingMode());
        assertEquals(Texture.Wrap.REPEAT, texture.getTWrappingMode());
    }

    @Test
    public void setDataProviderRegistersTheTextureAsUser() {
        AbstractTexture texture = new AbstractTexture();
        RecordingProvider provider = new RecordingProvider();
        texture.setDataProvider(provider);

        assertSame(provider, texture.getDataProvider());
        assertEquals(1, provider.added);
        assertSame(texture, provider.lastAdded);
    }

    @Test
    public void validityIsDelegatedToTheProvider() {
        AbstractTexture texture = new AbstractTexture();
        RecordingProvider provider = new RecordingProvider();
        texture.setDataProvider(provider);

        assertFalse(texture.isValid());
        provider.valid = true;
        assertTrue(texture.isValid());
    }

    @Test
    public void replacingTheProviderUnregistersFromTheOldOne() {
        AbstractTexture texture = new AbstractTexture();
        RecordingProvider first = new RecordingProvider();
        RecordingProvider second = new RecordingProvider();

        texture.setDataProvider(first);
        texture.setDataProvider(second);

        assertEquals(1, first.removed);
        assertSame(texture, first.lastRemoved);
        assertEquals(1, second.added);
        assertSame(second, texture.getDataProvider());
    }

    @Test
    public void clearingTheProviderUnregistersAndInvalidates() {
        AbstractTexture texture = new AbstractTexture();
        RecordingProvider provider = new RecordingProvider();
        provider.valid = true;
        texture.setDataProvider(provider);

        texture.setDataProvider(null);
        assertEquals(1, provider.removed);
        assertNull(texture.getDataProvider());
        assertFalse(texture.isValid());
    }

    @Test
    public void setDrawerInstallsADrawnTextureDataProvider() {
        AbstractTexture texture = new AbstractTexture();
        texture.setDrawer(new NoOpTextureDrawer(new Dimension(2, 2)));

        assertInstanceOf(DrawnTextureDataProvider.class, texture.getDataProvider());
        // A non-null drawer makes the drawn provider valid.
        assertTrue(texture.isValid());
    }

    @Test
    public void dataUpdatedDoesNotThrow() {
        // No public read-back of the up-to-date flag; exercise the callback path.
        AbstractTexture texture = new AbstractTexture();
        texture.dataUpdated();
        assertFalse(texture.isValid());
    }
}
