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
import org.scilab.forge.scirenderer.tranformations.Vector4d;

/**
 * Clipping-plane registry (mirrors g2d): planes are lazily created data holders configured by the
 * DrawerVisitor around each object (enableClipping/disableClipping). The motor reads the currently
 * enabled planes at emit time ({@link #enabledEquations}) and bakes per-vertex clip distances into
 * the geometry — the clip test reduces to {@code dot(equation, scene-vertex) >= 0} because the plane
 * and the geometry share the same scene transformation (it cancels), matching the JOGL backend.
 */
public class VulkanClippingManager implements ClippingManager {

    /** Max clip planes the motor bakes per vertex (2 vec4 slots; JOGL uses 4 for 2D, 6 for the box). */
    public static final int MAX_PLANES = 6;

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

    /**
     * The equations {@code [a,b,c,d]} of the currently enabled clip planes (at most {@link #MAX_PLANES}),
     * for the motor to bake per-vertex clip distances. Returns {@code null} when none are enabled — the
     * common case (clip_state off), so the motor can skip the per-vertex work.
     */
    public double[][] enabledEquations() {
        double[][] out = null;
        int n = 0;
        for (ClippingPlane p : clippingPlanes) {
            if (p != null && p.isEnable() && n < MAX_PLANES) {
                if (out == null) {
                    out = new double[MAX_PLANES][];
                }
                Vector4d eq = p.getEquation();
                double[] d = eq.getData();
                out[n++] = new double[] {d[0], d[1], d[2], d[3]};
            }
        }
        if (out == null) {
            return null;
        }
        // trim to the actual count so callers can use out.length
        if (n < MAX_PLANES) {
            double[][] trimmed = new double[n][];
            System.arraycopy(out, 0, trimmed, 0, n);
            return trimmed;
        }
        return out;
    }
}
