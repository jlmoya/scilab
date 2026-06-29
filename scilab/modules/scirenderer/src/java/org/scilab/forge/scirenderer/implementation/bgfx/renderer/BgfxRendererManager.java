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

package org.scilab.forge.scirenderer.implementation.bgfx.renderer;

import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.renderer.Renderer;
import org.scilab.forge.scirenderer.renderer.RendererManager;

/**
 * bgfx renderer manager (mirrors g2d): creates {@link BgfxRenderer}s and replays them.
 */
public class BgfxRendererManager implements RendererManager {

    public BgfxRendererManager() {
    }

    @Override
    public Renderer createRenderer() {
        return new BgfxRenderer();
    }

    @Override
    public void dispose(Renderer renderer) {
    }

    /** Perform a draw with the given renderer onto the given drawing tools. */
    public void draw(DrawingTools drawingTools, Renderer renderer) {
        if (renderer instanceof BgfxRenderer) {
            ((BgfxRenderer) renderer).draw(drawingTools);
        }
    }
}
