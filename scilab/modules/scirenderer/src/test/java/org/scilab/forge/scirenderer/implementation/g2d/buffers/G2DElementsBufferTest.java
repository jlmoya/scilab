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

package org.scilab.forge.scirenderer.implementation.g2d.buffers;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic unit tests for {@link G2DElementsBuffer}, the pure-Java 4-component
 * vertex buffer used by the software (g2d) renderer. The package-private
 * constructor is reached directly since this test shares the class's package.
 *
 * The buffer always stores {@code ELEMENT_SIZE == 4} floats per vertex; when a
 * caller supplies fewer components each vertex is padded with the tail of the
 * default vertex {@code (0, 0, 0, 1)}.
 */
public class G2DElementsBufferTest {

    private static float[] readAll(FloatBuffer buffer) {
        FloatBuffer dup = buffer.duplicate();
        dup.rewind();
        float[] out = new float[dup.limit()];
        dup.get(out);
        return out;
    }

    @Test
    public void elementSizeIsFour() {
        assertEquals(4, G2DElementsBuffer.ELEMENT_SIZE);
        assertEquals(4, new G2DElementsBuffer().getElementsSize());
    }

    @Test
    public void freshBufferIsEmpty() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        assertEquals(0, buffer.getSize());
        assertNull(buffer.getData());
    }

    @Test
    public void setFullVerticesFromFloatArrayKeepsThemVerbatim() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4, 5, 6, 7, 8}, 4);
        assertEquals(2, buffer.getSize());
        assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6, 7, 8}, readAll(buffer.getData()), 0f);
    }

    @Test
    public void shortVerticesArePaddedWithDefaultVertexTail() {
        // elementSize 2 => each vertex (x, y) is completed to (x, y, 0, 1).
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4}, 2);
        assertEquals(2, buffer.getSize());
        assertArrayEquals(new float[] {1, 2, 0, 1, 3, 4, 0, 1}, readAll(buffer.getData()), 0f);
    }

    @Test
    public void setDataFromBoxedFloatArrayPadsToo() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(new Float[] {9f, 8f, 7f}, 3);
        assertEquals(1, buffer.getSize());
        // (9, 8, 7) completed with the 4th default component (1).
        assertArrayEquals(new float[] {9, 8, 7, 1}, readAll(buffer.getData()), 0f);
    }

    @Test
    public void setDataFromFullFloatBufferShortCircuits() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(FloatBuffer.wrap(new float[] {1, 2, 3, 4, 5, 6, 7, 8}), 4);
        assertEquals(2, buffer.getSize());
        assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6, 7, 8}, readAll(buffer.getData()), 0f);
    }

    @Test
    public void setDataFromShortFloatBufferPads() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(FloatBuffer.wrap(new float[] {1, 2, 3, 4}), 2);
        assertEquals(2, buffer.getSize());
        assertArrayEquals(new float[] {1, 2, 0, 1, 3, 4, 0, 1}, readAll(buffer.getData()), 0f);
    }

    @Test
    public void nullFullFloatBufferClearsTheData() {
        // The elementSize==4 fast-path accepts a null buffer and stores it as-is.
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4}, 4);
        assertEquals(1, buffer.getSize());
        buffer.setData((FloatBuffer) null, 4);
        assertEquals(0, buffer.getSize());
        assertNull(buffer.getData());
    }

    @Test
    public void elementSizeBelowOneIsRejected() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        assertThrows(RuntimeException.class, () -> buffer.setData(new float[] {1}, 0));
    }

    @Test
    public void elementSizeAboveFourIsRejected() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        assertThrows(RuntimeException.class, () -> buffer.setData(new float[] {1, 2, 3, 4, 5}, 5));
        assertThrows(RuntimeException.class, () -> buffer.setData(new Float[] {1f, 2f, 3f, 4f, 5f}, 5));
        assertThrows(RuntimeException.class, () -> buffer.setData(FloatBuffer.wrap(new float[] {1, 2, 3, 4, 5}), 5));
    }

    @Test
    public void clearReleasesTheData() {
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4}, 4);
        buffer.clear();
        assertEquals(0, buffer.getSize());
        assertNull(buffer.getData());
    }

    @Test
    public void clearOnEmptyBufferIsSafe() {
        // clear() guards against a null backing buffer, unlike G2DIndicesBuffer.
        G2DElementsBuffer buffer = new G2DElementsBuffer();
        buffer.clear();
        assertEquals(0, buffer.getSize());
    }
}
