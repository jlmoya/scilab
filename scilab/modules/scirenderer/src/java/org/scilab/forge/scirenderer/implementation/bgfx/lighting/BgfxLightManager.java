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

package org.scilab.forge.scirenderer.implementation.bgfx.lighting;

import org.scilab.forge.scirenderer.lightning.Light;
import org.scilab.forge.scirenderer.lightning.LightManager;
import org.scilab.forge.scirenderer.shapes.appearance.Material;

/**
 * bgfx light manager: records lighting state for a fixed bank of lights. The first slice renders
 * flat / vertex-colored (no lighting shader yet), so state is stored but not consumed.
 */
public class BgfxLightManager implements LightManager {

    private static final int LIGHT_NUMBER = 8;

    private final BgfxLight[] lights = new BgfxLight[LIGHT_NUMBER];
    private boolean lightningEnable = LightManager.DEFAULT_LIGHTNING_STATUS;
    private Material material;

    public BgfxLightManager() {
        for (int i = 0; i < LIGHT_NUMBER; i++) {
            lights[i] = new BgfxLight(i);
        }
    }

    @Override
    public int getLightNumber() {
        return LIGHT_NUMBER;
    }

    @Override
    public Light getLight(int i) {
        if (i < 0 || i >= LIGHT_NUMBER) {
            return null;
        }
        return lights[i];
    }

    @Override
    public void setLightningEnable(boolean isLightningEnable) {
        this.lightningEnable = isLightningEnable;
    }

    @Override
    public boolean isLightningEnable() {
        return lightningEnable;
    }

    @Override
    public void setMaterial(Material material) {
        this.material = material;
    }
}
