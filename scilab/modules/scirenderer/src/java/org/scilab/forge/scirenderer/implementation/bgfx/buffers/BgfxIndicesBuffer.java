/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab macOS/2027 modernization
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.bgfx.buffers;

import org.scilab.forge.scirenderer.buffers.DataBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

import java.nio.IntBuffer;
import java.util.Collection;

/**
 * bgfx indices buffer: a client-side {@link IntBuffer} (uploaded to a transient 32-bit index
 * buffer at draw time). Mirrors the g2d backend's storage.
 */
public class BgfxIndicesBuffer implements IndicesBuffer, DataBuffer {

    private IntBuffer data;

    /** Package-private: only {@link BgfxBuffersManager} instantiates this. */
    BgfxIndicesBuffer() {
        data = null;
    }

    @Override
    public void setData(int[] indices) {
        IntBuffer buffer = IntBuffer.allocate(indices.length);
        buffer.rewind();
        buffer.put(indices);
        buffer.rewind();
        this.data = buffer;
    }

    @Override
    public void setData(Collection<Integer> indices) {
        IntBuffer buffer = IntBuffer.allocate(indices.size());
        buffer.rewind();
        for (int index : indices) {
            buffer.put(index);
        }
        buffer.rewind();
        this.data = buffer;
    }

    @Override
    public void setData(IntBuffer indexBuffer) {
        IntBuffer buffer = IntBuffer.allocate(indexBuffer.limit());
        buffer.rewind();
        indexBuffer.rewind();
        buffer.put(indexBuffer);
        buffer.rewind();
        indexBuffer.rewind();
        this.data = buffer;
    }

    @Override
    public int getSize() {
        return data == null ? 0 : data.limit();
    }

    @Override
    public IntBuffer getData() {
        return data == null ? null : data.asReadOnlyBuffer();
    }

    @Override
    public void clear() {
        if (data != null) {
            data.clear();
        }
        data = null;
    }
}
