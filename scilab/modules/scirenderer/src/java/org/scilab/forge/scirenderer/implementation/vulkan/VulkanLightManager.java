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

import org.scilab.forge.scirenderer.implementation.vulkan.lighting.VulkanLight;
import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.lightning.LightManager;
import org.scilab.forge.scirenderer.shapes.appearance.Material;

/**
 * Light registry (mirrors g2d): a fixed pool of light holders — the DrawerVisitor configures them
 * unconditionally, so real objects must exist. The Vulkan motor does not evaluate lighting yet
 * (a later milestone: Phong in the fragment shader; geometry already carries normals), so surfaces
 * render with their per-vertex / colormap colours.
 */
public class VulkanLightManager implements LightManager {

    private static final int LIGHT_COUNT = 8;

    private final VulkanLight[] lights = new VulkanLight[LIGHT_COUNT];
    private boolean isLightningEnable = DEFAULT_LIGHTNING_STATUS;
    private Material material;

    @Override
    public int getLightNumber() {
        return LIGHT_COUNT;
    }

    @Override
    public Light getLight(int i) {
        if (i < 0 || i >= LIGHT_COUNT) {
            return null;
        }
        if (lights[i] == null) {
            lights[i] = new VulkanLight(i);
        }
        return lights[i];
    }

    @Override
    public void setLightningEnable(boolean isLightningEnable) {
        this.isLightningEnable = isLightningEnable;
    }

    @Override
    public boolean isLightningEnable() {
        return isLightningEnable;
    }

    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }
}
