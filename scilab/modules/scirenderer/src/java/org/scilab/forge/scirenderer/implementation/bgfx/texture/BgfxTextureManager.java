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

/**
 * bgfx texture manager stub: hands out {@link BgfxTexture}s so DrawerVisitor can configure text /
 * mark / image textures without error. They are not yet drawn (first slice). See {@link BgfxTexture}.
 */
public class BgfxTextureManager implements TextureManager {

    @Override
    public Texture createTexture() {
        return new BgfxTexture();
    }

    @Override
    public void dispose(Collection<Texture> textures) {
    }

    @Override
    public void dispose(Texture texture) {
    }
}
