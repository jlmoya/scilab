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

import org.scilab.forge.scirenderer.buffers.BuffersManager;
import org.scilab.forge.scirenderer.buffers.DataBuffer;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * bgfx buffers manager: hands out client-side element/index buffers (mirrors the g2d backend).
 */
public final class BgfxBuffersManager implements BuffersManager {

    private final Set<DataBuffer> buffers = new HashSet<DataBuffer>();

    public BgfxBuffersManager() {
    }

    @Override
    public ElementsBuffer createElementsBuffer() {
        BgfxElementsBuffer newBuffer = new BgfxElementsBuffer();
        buffers.add(newBuffer);
        return newBuffer;
    }

    @Override
    public IndicesBuffer createIndicesBuffer() {
        BgfxIndicesBuffer newBuffer = new BgfxIndicesBuffer();
        buffers.add(newBuffer);
        return newBuffer;
    }

    @Override
    public void dispose(DataBuffer buffer) {
        if (buffer != null && buffers.contains(buffer)) {
            buffer.clear();
            buffers.remove(buffer);
        }
    }

    @Override
    public void dispose(Collection <? extends DataBuffer > toDispose) {
        for (DataBuffer buffer : toDispose) {
            dispose(buffer);
        }
    }
}
