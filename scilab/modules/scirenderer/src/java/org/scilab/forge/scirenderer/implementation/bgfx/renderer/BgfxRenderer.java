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

import org.scilab.forge.scirenderer.Drawer;
import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.renderer.Renderer;

/**
 * bgfx renderer: a thin cache of a {@link Drawer} that is replayed on demand (mirrors g2d).
 * No GPU-side caching yet; geometry is re-submitted each frame.
 */
public class BgfxRenderer implements Renderer {

    private Drawer drawer;

    /** Package-private: only {@link BgfxRendererManager} instantiates this. */
    BgfxRenderer() { }

    @Override
    public void setDrawer(Drawer drawer) {
        this.drawer = drawer;
    }

    @Override
    public Drawer getDrawer() {
        return drawer;
    }

    @Override
    public void reload() {
    }

    /** Replay the cached drawer onto the given drawing tools. */
    public void draw(DrawingTools drawingTools) {
        if (drawer != null) {
            drawer.draw(drawingTools);
        }
    }
}
