/*
 * Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
 * Copyright (C) 2026 - Scilab / macOS 2027 fork
 *
 * This file is hereby licensed under the terms of the GNU GPL v2.0,
 * pursuant to article 5.3.4 of the CeCILL v.2.1.
 * This file was originally licensed under the terms of the CeCILL v2.1,
 * and continues to be available under such terms.
 * For more information, see the COPYING file which you should have received
 * along with this program.
 */

package org.scilab.forge.scirenderer.implementation.vulkan.buffers;

import java.nio.IntBuffer;
import java.util.Collection;

import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

/**
 * Vulkan backend index buffer: a plain CPU {@link IntBuffer} holder. Fill indices come from
 * {@code Geometry.getIndices()} and edge indices from {@code getWireIndices()}; the motor reads them
 * to expand triangles / line segments into the per-frame arena.
 */
public class VulkanIndicesBuffer implements IndicesBuffer {

    private IntBuffer data;

    @Override
    public void setData(int[] indices) {
        this.data = IntBuffer.wrap(indices);
    }

    @Override
    public void setData(Collection<Integer> indices) {
        IntBuffer buffer = IntBuffer.allocate(indices.size());
        for (Integer i : indices) {
            buffer.put(i);
        }
        buffer.rewind();
        this.data = buffer;
    }

    @Override
    public void setData(IntBuffer indexBuffer) {
        this.data = indexBuffer;
    }

    @Override
    public IntBuffer getData() {
        return data;
    }

    @Override
    public int getSize() {
        return data == null ? 0 : data.capacity();
    }

    @Override
    public void clear() {
        data = null;
    }
}
