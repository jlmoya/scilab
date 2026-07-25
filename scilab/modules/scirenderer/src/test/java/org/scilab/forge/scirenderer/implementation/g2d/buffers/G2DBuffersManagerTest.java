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
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Hermetic unit tests for {@link G2DBuffersManager}, the factory/owner of the
 * software renderer's element and index buffers.
 */
public class G2DBuffersManagerTest {

    @Test
    public void createsG2DElementsBuffers() {
        G2DBuffersManager manager = new G2DBuffersManager();
        ElementsBuffer buffer = manager.createElementsBuffer();
        assertNotNull(buffer);
        assertInstanceOf(G2DElementsBuffer.class, buffer);
    }

    @Test
    public void createsG2DIndicesBuffers() {
        G2DBuffersManager manager = new G2DBuffersManager();
        IndicesBuffer buffer = manager.createIndicesBuffer();
        assertNotNull(buffer);
        assertInstanceOf(G2DIndicesBuffer.class, buffer);
    }

    @Test
    public void eachCreateReturnsADistinctInstance() {
        G2DBuffersManager manager = new G2DBuffersManager();
        assertNotSame(manager.createElementsBuffer(), manager.createElementsBuffer());
        assertNotSame(manager.createIndicesBuffer(), manager.createIndicesBuffer());
    }

    @Test
    public void disposeClearsAnOwnedElementsBuffer() {
        G2DBuffersManager manager = new G2DBuffersManager();
        ElementsBuffer buffer = manager.createElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4}, 4);
        assertEquals(1, buffer.getSize());

        manager.dispose(buffer);
        assertEquals(0, buffer.getSize());
        assertNull(buffer.getData());
    }

    @Test
    public void disposeClearsAnOwnedIndicesBuffer() {
        G2DBuffersManager manager = new G2DBuffersManager();
        IndicesBuffer buffer = manager.createIndicesBuffer();
        buffer.setData(new int[] {1, 2, 3});
        assertEquals(3, buffer.getSize());

        manager.dispose(buffer);
        assertEquals(0, buffer.getSize());
    }

    @Test
    public void disposeOfAForeignBufferIsANoOp() {
        // A buffer owned by another manager is not tracked here, so dispose must
        // leave it untouched (getLocalBuffer returns null => nothing cleared).
        G2DBuffersManager owner = new G2DBuffersManager();
        G2DBuffersManager other = new G2DBuffersManager();
        ElementsBuffer foreign = owner.createElementsBuffer();
        foreign.setData(new float[] {5, 6, 7, 8}, 4);

        other.dispose(foreign);
        assertEquals(1, foreign.getSize(), "foreign buffer must keep its data");
    }

    @Test
    public void disposeOfAlreadyDisposedBufferIsANoOp() {
        G2DBuffersManager manager = new G2DBuffersManager();
        ElementsBuffer buffer = manager.createElementsBuffer();
        buffer.setData(new float[] {1, 2, 3, 4}, 4);
        manager.dispose(buffer);
        // Second dispose: the buffer is no longer tracked, so this is a no-op
        // (and must not re-run clear()).
        manager.dispose(buffer);
        assertEquals(0, buffer.getSize());
    }

    @Test
    public void disposeOfACollectionClearsEachOwnedBuffer() {
        G2DBuffersManager manager = new G2DBuffersManager();
        ElementsBuffer a = manager.createElementsBuffer();
        ElementsBuffer b = manager.createElementsBuffer();
        a.setData(new float[] {1, 2, 3, 4}, 4);
        b.setData(new float[] {5, 6, 7, 8}, 4);

        manager.dispose(List.of(a, b));
        assertEquals(0, a.getSize());
        assertEquals(0, b.getSize());
    }
}
