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

import org.scilab.forge.scirenderer.texture.AbstractTexture;
import org.scilab.forge.scirenderer.texture.TextureDataProvider;

import org.lwjgl.system.MemoryUtil;

import java.awt.Dimension;
import java.nio.ByteBuffer;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * A bgfx texture. {@link AbstractTexture} provides the wrapping/filter state and the
 * {@link TextureDataProvider} plumbing (with the {@code upToDate} dirty flag); this adds the GPU
 * upload, mirroring the JOGL backend's {@code JoGLTexture.checkData}.
 *
 * <p>The data provider yields RGBA8 pixels ({@code getData()}) and a size ({@code getTextureSize()})
 * — used identically for colormap surfaces, text labels, and marks. Upload happens lazily on the
 * render thread the first time the texture is bound, and again whenever the provider invalidates it
 * ({@code dataUpdated()} clears {@code upToDate}).
 */
public class BgfxTexture extends AbstractTexture {

    public static final short INVALID = (short) 0xffff;

    private short handle = INVALID;
    private int texWidth;
    private int texHeight;

    /**
     * Ensure the current pixel data is on the GPU; returns the bgfx texture handle (or
     * {@link #INVALID} if there is no drawable data yet). Must run on the render thread.
     */
    public short ensureUploaded() {
        if (handle != INVALID && upToDate) {
            return handle;
        }
        final TextureDataProvider provider = getDataProvider();
        if (provider == null) {
            return handle;
        }
        final Dimension size = provider.getTextureSize();
        final ByteBuffer data = provider.getData();   // heap RGBA8
        if (size == null || data == null || size.width <= 0 || size.height <= 0) {
            return handle;
        }
        final int w = size.width;
        final int h = size.height;

        data.rewind();
        final ByteBuffer buf = MemoryUtil.memAlloc(data.remaining());
        buf.put(data);
        buf.flip();

        if (handle != INVALID) {
            bgfx_destroy_texture(handle);
        }
        handle = bgfx_create_texture_2d((short) w, (short) h, false, 1,
                                        BGFX_TEXTURE_FORMAT_RGBA8, samplerFlags(), bgfx_copy(buf));
        MemoryUtil.memFree(buf);
        texWidth = w;
        texHeight = h;
        upToDate = true;
        return handle;
    }

    private long samplerFlags() {
        long flags = 0L;
        if (getSWrappingMode() == Wrap.CLAMP) {
            flags |= BGFX_SAMPLER_U_CLAMP;
        }
        if (getTWrappingMode() == Wrap.CLAMP) {
            flags |= BGFX_SAMPLER_V_CLAMP;
        }
        if (getMagnificationFilter() == Filter.NEAREST) {
            flags |= BGFX_SAMPLER_MAG_POINT;
        }
        if (getMinifyingFilter() == Filter.NEAREST) {
            flags |= BGFX_SAMPLER_MIN_POINT;
        }
        return flags;
    }

    public short handle() {
        return handle;
    }

    public int textureWidth() {
        return texWidth;
    }

    public int textureHeight() {
        return texHeight;
    }

    /** Release the GPU texture (render thread). */
    public void disposeGpu() {
        if (handle != INVALID) {
            bgfx_destroy_texture(handle);
            handle = INVALID;
        }
    }
}
