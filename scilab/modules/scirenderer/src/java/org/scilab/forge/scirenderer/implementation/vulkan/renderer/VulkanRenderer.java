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

package org.scilab.forge.scirenderer.implementation.vulkan.renderer;

import org.scilab.forge.scirenderer.Drawer;
import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.renderer.Renderer;

/**
 * Degenerate renderer (like g2d's): a re-drawable object is just its {@link Drawer}, replayed on
 * demand. No display-list / VBO caching yet — every frame re-streams geometry through the motor.
 */
public class VulkanRenderer implements Renderer {

    private Drawer drawer;

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

    /** Non-interface helper invoked by {@link VulkanRendererManager#draw}. */
    public void draw(DrawingTools drawingTools) {
        if (drawer != null) {
            drawer.draw(drawingTools);
        }
    }
}
