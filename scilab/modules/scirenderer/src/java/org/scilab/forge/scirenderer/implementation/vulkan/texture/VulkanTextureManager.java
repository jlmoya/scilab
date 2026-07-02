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

import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.texture.TextureManager;

/**
 * Vulkan texture factory. Textures (colormap strips, glyph/mark sprites, image plots) are created
 * here and filled by the DrawerVisitor via their data provider; the motor uploads them to the GPU
 * on demand. GPU-side disposal is handled by the scene renderer, so dispose is a CPU-side no-op.
 */
public class VulkanTextureManager implements TextureManager {

    @Override
    public Texture createTexture() {
        return new VulkanTexture();
    }

    @Override
    public void dispose(Texture texture) {
    }

    @Override
    public void dispose(Collection<Texture> textures) {
    }
}
