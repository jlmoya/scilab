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

package org.scilab.forge.scirenderer.implementation.vulkan.texture;

import java.util.Collection;

import org.scilab.forge.scirenderer.implementation.vulkan.VulkanCanvas;
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.texture.TextureManager;

/**
 * Vulkan texture factory. Textures (glyph/mark sprites, colormap strips, image plots) are created
 * here and filled by the DrawerVisitor via their data provider; the motor uploads them to the GPU
 * on demand. Disposal queues the GPU handle for destruction on the render thread (destroying it on
 * the caller's thread would race the in-flight frame).
 */
public class VulkanTextureManager implements TextureManager {

    private final VulkanCanvas canvas;

    public VulkanTextureManager(VulkanCanvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public Texture createTexture() {
        return new VulkanTexture();
    }

    @Override
    public void dispose(Texture texture) {
        if (texture instanceof VulkanTexture) {
            VulkanTexture vt = (VulkanTexture) texture;
            if (vt.getGpuHandle() != 0) {
                // Called on the INTERPRETER thread (DrawerVisitor.deleteObject). Only enqueue —
                // do NOT read past this or clear the GPU handle here; the render thread's
                // ensureUploaded owns the handle, and clearing it here would race that read.
                canvas.getMotor().queueTextureDispose(vt);
            }
        }
    }

    @Override
    public void dispose(Collection<Texture> textures) {
        for (Texture texture : textures) {
            dispose(texture);
        }
    }
}
