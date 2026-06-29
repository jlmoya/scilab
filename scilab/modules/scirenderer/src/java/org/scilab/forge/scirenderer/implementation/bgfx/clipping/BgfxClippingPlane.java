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

import org.scilab.forge.scirenderer.clipping.ClippingPlane;
import org.scilab.forge.scirenderer.tranformations.Transformation;
import org.scilab.forge.scirenderer.tranformations.Vector4d;

/**
 * bgfx clipping plane: stores plane state. Clipping is not yet applied in the first slice (most
 * plots don't clip); a future pass can feed these as clip-distance uniforms to the shader.
 */
public class BgfxClippingPlane implements ClippingPlane {

    private final int index;
    private boolean enable;
    private Vector4d equation;
    private Transformation transformation;

    public BgfxClippingPlane(int index) {
        this.index = index;
    }

    @Override
    public boolean isEnable() {
        return enable;
    }

    @Override
    public void setEnable(boolean isEnable) {
        this.enable = isEnable;
    }

    @Override
    public void setEquation(Vector4d v) {
        this.equation = v;
    }

    @Override
    public Vector4d getEquation() {
        return equation;
    }

    @Override
    public void setTransformation(Transformation transformation) {
        this.transformation = transformation;
    }

    @Override
    public Transformation getTransformation() {
        return transformation;
    }

    @Override
    public int getIndex() {
        return index;
    }
}
