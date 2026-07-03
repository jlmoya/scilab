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

package org.scilab.forge.scirenderer.implementation.vulkan.clipping;

import org.scilab.forge.scirenderer.clipping.ClippingPlane;
import org.scilab.forge.scirenderer.tranformations.Transformation;
import org.scilab.forge.scirenderer.tranformations.TransformationFactory;
import org.scilab.forge.scirenderer.tranformations.Vector4d;

/**
 * Plain clipping-plane holder. The DrawerVisitor sets equation/transformation/enable state on these
 * unconditionally while drawing axes, so real objects must exist — but the Vulkan motor does not
 * apply user clipping yet (a later milestone: per-plane clip distances in the vertex shader), so
 * this only stores the state.
 */
public class VulkanClippingPlane implements ClippingPlane {

    private final int index;
    private boolean isEnable;
    private Vector4d equation = new Vector4d(0, 0, 0, 0);
    private Transformation transformation = TransformationFactory.getIdentity();

    public VulkanClippingPlane(int index) {
        this.index = index;
    }

    @Override
    public boolean isEnable() {
        return isEnable;
    }

    @Override
    public void setEnable(boolean isEnable) {
        this.isEnable = isEnable;
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
