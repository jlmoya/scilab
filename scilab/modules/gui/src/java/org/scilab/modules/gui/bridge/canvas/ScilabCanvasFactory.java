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

package org.scilab.modules.gui.bridge.canvas;

import org.scilab.modules.graphic_objects.axes.AxesContainer;

/**
 * Picks the rendering backend for a new figure canvas. The Vulkan canvas is opt-in via
 * {@code -Dscilab.renderer.vulkan=true}; any failure creating it (missing loader, no device, ...)
 * falls back to the JOGL canvas so a figure always renders.
 */
public final class ScilabCanvasFactory {

    private ScilabCanvasFactory() {
    }

    public static AbstractScilabCanvas createCanvas(AxesContainer figure) {
        if (Boolean.getBoolean("scilab.renderer.vulkan")) {
            try {
                return new SwingScilabVulkanCanvas(figure);
            } catch (Throwable t) {
                System.err.println("[scilab.vulkan] Vulkan canvas unavailable, falling back to JOGL: " + t);
                t.printStackTrace();
            }
        }
        return new SwingScilabCanvas(figure);
    }
}
