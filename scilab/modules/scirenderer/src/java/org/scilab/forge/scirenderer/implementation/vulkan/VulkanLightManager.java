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

import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.lightning.LightManager;
import org.scilab.forge.scirenderer.shapes.appearance.Material;

/**
 * Lighting is disabled in the Vulkan backend for now: zero lights, so surfaces render with their
 * per-vertex / colormap colours (matching Scilab's default flat look). Real Phong lighting is a
 * later milestone (uniforms + normals are already carried by the geometry).
 */
public class VulkanLightManager implements LightManager {

    @Override
    public int getLightNumber() {
        return 0;
    }

    @Override
    public Light getLight(int i) {
        return null;
    }

    @Override
    public void setLightningEnable(boolean isLightningEnable) {
    }

    @Override
    public boolean isLightningEnable() {
        return false;
    }

    @Override
    public void setMaterial(Material material) {
    }
}
