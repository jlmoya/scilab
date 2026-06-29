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

package org.scilab.forge.scirenderer.implementation.bgfx.clipping;

import org.scilab.forge.scirenderer.clipping.ClippingManager;
import org.scilab.forge.scirenderer.clipping.ClippingPlane;

/**
 * bgfx clipping manager: a fixed bank of {@link BgfxClippingPlane}s. Clipping is recorded but not
 * yet enforced in the first slice.
 */
public class BgfxClippingManager implements ClippingManager {

    private static final int PLANE_NUMBER = 6;

    private final BgfxClippingPlane[] planes = new BgfxClippingPlane[PLANE_NUMBER];

    public BgfxClippingManager() {
        for (int i = 0; i < PLANE_NUMBER; i++) {
            planes[i] = new BgfxClippingPlane(i);
        }
    }

    @Override
    public int getClippingPlaneNumber() {
        return PLANE_NUMBER;
    }

    @Override
    public ClippingPlane getClippingPlane(int i) {
        if (i < 0 || i >= PLANE_NUMBER) {
            return null;
        }
        return planes[i];
    }

    @Override
    public void disableClipping() {
        for (BgfxClippingPlane plane : planes) {
            plane.setEnable(false);
        }
    }
}
