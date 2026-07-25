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

package org.scilab.modules.graphic_objects.vectfield;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Hermetic unit tests for the controller-independent surface of
 * {@link VectFieldDecomposer}. The buffer-filling methods that read a graphic
 * object's properties go through GraphicController and are out of scope here;
 * what is exercised instead is the pure vertex-colour writing helpers
 * ({@code writeSegmentColors} / {@code writeArrowColors}, which duplicate an
 * RGB(+alpha) colour across the vertices of a segment / arrow into a
 * {@link FloatBuffer}), the constant {@code DEFAULT_LOG_COORD_Z}, and the two
 * String-keyed overloads that are hard-coded no-ops.
 *
 * The test lives in the same package as the class under test so that its
 * {@code protected static} helpers and constant are reachable.
 */
public class VectFieldDecomposerTest {

    private static final float EPS = 0.0f;

    @Test
    public void defaultLogCoordZIsOne() {
        assertEquals(1.0, VectFieldDecomposer.DEFAULT_LOG_COORD_Z, 1e-12);
    }

    @Test
    public void writeSegmentColorsRgbDuplicatesColourAcrossTwoVertices() {
        FloatBuffer buf = FloatBuffer.allocate(6);
        float[] color = {0.25f, 0.5f, 0.75f};
        VectFieldDecomposer.writeSegmentColors(buf, 3, 0, color);

        // Two vertices, each carrying the same r,g,b.
        assertArrayEquals(new float[] {0.25f, 0.5f, 0.75f, 0.25f, 0.5f, 0.75f},
                          bufferToArray(buf, 6), EPS);
    }

    @Test
    public void writeSegmentColorsRgbaAppendsOpaqueAlpha() {
        FloatBuffer buf = FloatBuffer.allocate(8);
        float[] color = {0.1f, 0.2f, 0.3f};
        VectFieldDecomposer.writeSegmentColors(buf, 4, 0, color);

        // elementsSize == 4 => an alpha of 1.0 is written after each rgb triple.
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f, 1.0f, 0.1f, 0.2f, 0.3f, 1.0f},
                          bufferToArray(buf, 8), EPS);
    }

    @Test
    public void writeSegmentColorsHonoursBufferOffset() {
        FloatBuffer buf = FloatBuffer.allocate(9);
        float[] color = {1.0f, 2.0f, 3.0f};
        VectFieldDecomposer.writeSegmentColors(buf, 3, 3, color);

        // The first three slots are left untouched (default 0.0f).
        assertArrayEquals(new float[] {0f, 0f, 0f, 1f, 2f, 3f, 1f, 2f, 3f},
                          bufferToArray(buf, 9), EPS);
    }

    @Test
    public void writeArrowColorsRgbDuplicatesColourAcrossThreeVertices() {
        FloatBuffer buf = FloatBuffer.allocate(9);
        float[] color = {0.2f, 0.4f, 0.6f};
        VectFieldDecomposer.writeArrowColors(buf, 3, 0, color);

        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f, 0.2f, 0.4f, 0.6f, 0.2f, 0.4f, 0.6f},
                          bufferToArray(buf, 9), EPS);
    }

    @Test
    public void writeArrowColorsRgbaAppendsOpaqueAlphaOnEachVertex() {
        FloatBuffer buf = FloatBuffer.allocate(12);
        float[] color = {0.5f, 0.5f, 0.5f};
        VectFieldDecomposer.writeArrowColors(buf, 4, 0, color);

        assertArrayEquals(new float[] {0.5f, 0.5f, 0.5f, 1.0f,
                                       0.5f, 0.5f, 0.5f, 1.0f,
                                       0.5f, 0.5f, 0.5f, 1.0f
                                      },
                          bufferToArray(buf, 12), EPS);
    }

    @Test
    public void fillColorsStringOverloadIsANoOp() {
        FloatBuffer buf = FloatBuffer.allocate(4);
        VectFieldDecomposer.fillColors(buf, "ignored", 4);
        // Nothing is written; every slot keeps its default.
        assertArrayEquals(new float[] {0f, 0f, 0f, 0f}, bufferToArray(buf, 4), EPS);
    }

    @Test
    public void getIndicesSizeIsZero() {
        assertEquals(0, VectFieldDecomposer.getIndicesSize());
    }

    @Test
    public void fillIndicesStringOverloadWritesNothingAndReturnsZero() {
        IntBuffer buf = IntBuffer.allocate(3);
        assertEquals(0, VectFieldDecomposer.fillIndices(buf, "ignored", 0));
        assertEquals(0, buf.get(0));
        assertEquals(0, buf.get(1));
        assertEquals(0, buf.get(2));
    }

    /** Reads the first {@code n} absolute slots of a FloatBuffer into an array. */
    private static float[] bufferToArray(FloatBuffer buf, int n) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.get(i);
        }
        return out;
    }
}
