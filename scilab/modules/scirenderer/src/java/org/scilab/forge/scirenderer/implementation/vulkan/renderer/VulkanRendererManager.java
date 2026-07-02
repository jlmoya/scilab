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

import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.renderer.Renderer;
import org.scilab.forge.scirenderer.renderer.RendererManager;

/**
 * Vulkan backend renderer factory. Mirrors g2d: it also carries the non-interface
 * {@link #draw(DrawingTools, Renderer)} helper the drawing tools invoke for {@code draw(Renderer)}.
 */
public class VulkanRendererManager implements RendererManager {

    @Override
    public Renderer createRenderer() {
        return new VulkanRenderer();
    }

    @Override
    public void dispose(Renderer renderer) {
    }

    public void draw(DrawingTools drawingTools, Renderer renderer) {
        if (renderer instanceof VulkanRenderer) {
            ((VulkanRenderer) renderer).draw(drawingTools);
        }
    }
}
