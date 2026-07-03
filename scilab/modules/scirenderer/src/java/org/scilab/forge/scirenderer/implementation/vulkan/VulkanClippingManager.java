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

import java.util.ArrayList;
import java.util.List;

import org.scilab.forge.scirenderer.clipping.ClippingManager;
import org.scilab.forge.scirenderer.clipping.ClippingPlane;
import org.scilab.forge.scirenderer.implementation.vulkan.clipping.VulkanClippingPlane;

/**
 * Clipping-plane registry (mirrors g2d): planes are lazily created data holders — the DrawerVisitor
 * configures them unconditionally while drawing axes, so real objects must exist. The Vulkan motor
 * does not apply user clipping yet (a later milestone: clip distances in the vertex shader).
 */
public class VulkanClippingManager implements ClippingManager {

    private final List<ClippingPlane> clippingPlanes = new ArrayList<ClippingPlane>(6);

    @Override
    public int getClippingPlaneNumber() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ClippingPlane getClippingPlane(int i) {
        if (i < 0) {
            return null;
        }
        while (clippingPlanes.size() <= i) {
            clippingPlanes.add(new VulkanClippingPlane(clippingPlanes.size()));
        }
        return clippingPlanes.get(i);
    }

    @Override
    public void disableClipping() {
        for (ClippingPlane clippingPlane : clippingPlanes) {
            if (clippingPlane != null) {
                clippingPlane.setEnable(false);
            }
        }
    }
}
