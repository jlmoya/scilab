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

import java.nio.IntBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hermetic unit tests for {@link G2DIndicesBuffer}, the pure-Java index buffer of
 * the software (g2d) renderer. The package-private constructor is reached directly
 * since this test shares the class's package.
 */
public class G2DIndicesBufferTest {

    private static int[] readAll(IntBuffer buffer) {
        IntBuffer dup = buffer.duplicate();
        dup.rewind();
        int[] out = new int[dup.limit()];
        dup.get(out);
        return out;
    }

    @Test
    public void freshBufferHasZeroSize() {
        assertEquals(0, new G2DIndicesBuffer().getSize());
    }

    @Test
    public void setDataFromIntArrayStoresIndices() {
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        buffer.setData(new int[] {3, 1, 4, 1, 5});
        assertEquals(5, buffer.getSize());
        assertArrayEqualsInt(new int[] {3, 1, 4, 1, 5}, readAll(buffer.getData()));
    }

    @Test
    public void setDataFromCollectionStoresIndices() {
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        buffer.setData(List.of(7, 8, 9));
        assertEquals(3, buffer.getSize());
        assertArrayEqualsInt(new int[] {7, 8, 9}, readAll(buffer.getData()));
    }

    @Test
    public void setDataFromIntBufferCopiesContent() {
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        IntBuffer source = IntBuffer.wrap(new int[] {10, 20, 30, 40});
        buffer.setData(source);
        assertEquals(4, buffer.getSize());
        assertArrayEqualsInt(new int[] {10, 20, 30, 40}, readAll(buffer.getData()));
        // The source buffer is rewound (not consumed) by setData.
        assertEquals(0, source.position());
    }

    @Test
    public void getDataReturnsAReadOnlyView() {
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        buffer.setData(new int[] {1, 2});
        assertTrue(buffer.getData().isReadOnly());
    }

    @Test
    public void getDataOnFreshBufferThrows() {
        // Defect characterization: getData() dereferences the (null) backing
        // buffer before any setData call, so it throws instead of returning null.
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        assertThrows(NullPointerException.class, buffer::getData);
    }

    @Test
    public void clearOnFreshBufferThrows() {
        // Defect characterization: clear() also dereferences the null backing
        // buffer, unlike G2DElementsBuffer.clear() which guards against it.
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        assertThrows(NullPointerException.class, buffer::clear);
    }

    @Test
    public void clearAfterSetReleasesData() {
        G2DIndicesBuffer buffer = new G2DIndicesBuffer();
        buffer.setData(new int[] {1, 2, 3});
        buffer.clear();
        assertEquals(0, buffer.getSize());
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length, "length");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "index " + i);
        }
    }
}
