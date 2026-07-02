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

import org.scilab.forge.scirenderer.clipping.ClippingManager;
import org.scilab.forge.scirenderer.clipping.ClippingPlane;

/**
 * Clipping is not yet wired in the Vulkan backend: it reports zero active planes, so the
 * DrawerVisitor never requests one. User clipping planes are a later milestone (they map to
 * {@code gl_ClipDistance}-style shader clipping).
 */
public class VulkanClippingManager implements ClippingManager {

    @Override
    public int getClippingPlaneNumber() {
        return 0;
    }

    @Override
    public ClippingPlane getClippingPlane(int i) {
        return null;
    }

    @Override
    public void disableClipping() {
    }
}
