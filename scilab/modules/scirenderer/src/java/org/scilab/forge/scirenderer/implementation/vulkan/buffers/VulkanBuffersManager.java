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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.scilab.forge.scirenderer.buffers.BuffersManager;
import org.scilab.forge.scirenderer.buffers.DataBuffer;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

/**
 * Vulkan backend buffer factory + registry. Mirrors the g2d manager: it hands out CPU-side
 * element / index buffers and tracks them for disposal. GPU vertex/index buffers are not allocated
 * here — the motor streams these CPU buffers into a per-frame arena each redraw.
 */
public class VulkanBuffersManager implements BuffersManager {

    private final Set<DataBuffer> buffers = new HashSet<DataBuffer>();

    @Override
    public ElementsBuffer createElementsBuffer() {
        VulkanElementsBuffer buffer = new VulkanElementsBuffer();
        buffers.add(buffer);
        return buffer;
    }

    @Override
    public IndicesBuffer createIndicesBuffer() {
        VulkanIndicesBuffer buffer = new VulkanIndicesBuffer();
        buffers.add(buffer);
        return buffer;
    }

    @Override
    public void dispose(DataBuffer buffer) {
        if (buffer != null) {
            buffer.clear();
            buffers.remove(buffer);
        }
    }

    @Override
    public void dispose(Collection<? extends DataBuffer> toDispose) {
        for (DataBuffer buffer : toDispose) {
            dispose(buffer);
        }
    }
}
