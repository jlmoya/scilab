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

package org.scilab.forge.scirenderer.implementation.bgfx.texture;

import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.texture.TextureManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * bgfx texture manager: hands out {@link BgfxTexture}s for the shared DrawerVisitor's text, mark,
 * colormap and image textures, and reclaims their GPU handles when the model disposes them.
 *
 * <p>Threading: {@code dispose(...)} is called on the interpreter/EDT thread (e.g. on a colormap
 * change, a label edit, or a tick relabel during zoom/pan), but bgfx is single-threaded — a
 * {@code bgfx_destroy_texture} may run only on the render thread. So dispose() merely enqueues the
 * texture, and the render thread calls {@link #drainDisposed()} once per frame to do the actual GPU
 * destroy. Without this, every disposed texture leaked its GPU handle until the figure (and the whole
 * bgfx context) was torn down.
 */
public class BgfxTextureManager implements TextureManager {

    /** Textures the model has disposed, awaiting GPU destroy on the render thread. */
    private final ConcurrentLinkedQueue<BgfxTexture> pendingDisposal = new ConcurrentLinkedQueue<BgfxTexture>();

    @Override
    public Texture createTexture() {
        return new BgfxTexture();
    }

    @Override
    public void dispose(Collection<Texture> textures) {
        if (textures != null) {
            for (Texture texture : textures) {
                dispose(texture);
            }
        }
    }

    @Override
    public void dispose(Texture texture) {
        if (texture instanceof BgfxTexture) {
            pendingDisposal.add((BgfxTexture) texture);
        }
    }

    /**
     * Destroy the GPU handles of textures disposed since the last frame. Must run on the render thread
     * (bgfx is single-threaded); called from {@code BgfxCanvas.renderFrame()}.
     */
    public void drainDisposed() {
        BgfxTexture texture;
        while ((texture = pendingDisposal.poll()) != null) {
            texture.disposeGpu();
        }
    }
}
