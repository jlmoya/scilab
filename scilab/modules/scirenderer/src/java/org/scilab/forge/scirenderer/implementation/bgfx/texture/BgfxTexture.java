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

/**
 * bgfx texture stub.
 *
 * <p>{@link AbstractTexture} already implements the whole {@link org.scilab.forge.scirenderer.texture.Texture}
 * contract (wrapping/filtering state + the data-provider plumbing), so DrawerVisitor can create and
 * configure textures (for text labels, marks, image plots) without error. The first slice does not
 * yet rasterize them to the GPU — {@code BgfxDrawingTools.draw(Texture, ...)} is a no-op — so text
 * and sprites simply don't appear yet. GPU texture upload is a later pass.
 */
public class BgfxTexture extends AbstractTexture {
}
