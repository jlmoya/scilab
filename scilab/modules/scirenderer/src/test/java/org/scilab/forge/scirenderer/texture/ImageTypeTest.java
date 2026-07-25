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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hermetic unit tests for {@link TextureDataProvider.ImageType} and its
 * {@code fromInt(int)} integer-code mapping.
 */
public class ImageTypeTest {

    @Test
    public void twentyFiveImageTypesAreDeclared() {
        assertEquals(25, ImageType.values().length);
    }

    @Test
    public void firstAndLastConstantsAnchorTheDeclarationOrder() {
        assertEquals(0, ImageType.RGB.ordinal());
        assertEquals(24, ImageType.RGBA_BYTE.ordinal());
    }

    /**
     * {@code fromInt} is defined so that each valid code returns the constant at
     * that ordinal, i.e. it mirrors the declaration order 0..24.
     */
    @Test
    public void fromIntMapsEachCodeToTheConstantAtThatOrdinal() {
        ImageType[] values = ImageType.values();
        for (int i = 0; i < values.length; i++) {
            assertSame(values[i], ImageType.fromInt(i), "code " + i);
            assertEquals(i, ImageType.fromInt(i).ordinal(), "ordinal " + i);
        }
    }

    @Test
    public void fromIntSpotChecksSelectedCodes() {
        assertSame(ImageType.RGB, ImageType.fromInt(0));
        assertSame(ImageType.GRAY, ImageType.fromInt(3));
        assertSame(ImageType.RGBA, ImageType.fromInt(5));
        assertSame(ImageType.INTENSITY, ImageType.fromInt(12));
        assertSame(ImageType.RGB_FLOAT, ImageType.fromInt(15));
        assertSame(ImageType.RGBA_BYTE, ImageType.fromInt(24));
    }

    @Test
    public void fromIntDefaultsToGrayForOutOfRangeCodes() {
        assertSame(ImageType.GRAY, ImageType.fromInt(-1));
        assertSame(ImageType.GRAY, ImageType.fromInt(25));
        assertSame(ImageType.GRAY, ImageType.fromInt(Integer.MAX_VALUE));
        assertSame(ImageType.GRAY, ImageType.fromInt(Integer.MIN_VALUE));
    }

    @Test
    public void valueOfRoundTripsConstantNames() {
        assertSame(ImageType.ABGR, ImageType.valueOf("ABGR"));
        assertSame(ImageType.RGBA_5551, ImageType.valueOf("RGBA_5551"));
        assertEquals("BLUE_FLOAT", ImageType.BLUE_FLOAT.name());
    }
}
