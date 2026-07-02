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

package org.scilab.forge.scirenderer.implementation.vulkan;

/**
 * Factory for the Vulkan canvas (mirrors {@code G2DCanvasFactory}). The GPU renderer is attached
 * afterwards via {@link VulkanCanvas#setSceneRenderer} once the GUI has a native surface.
 */
public final class VulkanCanvasFactory {

    private VulkanCanvasFactory() {
    }

    public static VulkanCanvas createCanvas(int width, int height) {
        return new VulkanCanvas(width, height);
    }
}
