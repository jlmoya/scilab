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

package org.scilab.forge.scirenderer.implementation.bgfx;

/**
 * Column-major 4x4 matrix helpers for the bgfx backend.
 *
 * <p>scirenderer hands us the scene-to-clip matrix as a column-major {@code double[16]} (it is
 * loaded by the JOGL backend straight into {@code GL_MODELVIEW} with {@code GL_PROJECTION} = identity,
 * so it already maps to OpenGL clip space, z in [-1, 1]). bgfx's {@code u_modelViewProj} built-in is
 * {@code proj * view * model}; with view = proj = identity we feed this matrix as the per-draw model
 * transform. On Metal/D3D (non-homogeneous depth, z in [0, 1]) we must remap z first.
 */
final class BgfxMat {

    private BgfxMat() { }

    static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    /** Column-major double[16] -> float[16]. */
    static float[] toFloat(double[] m) {
        float[] r = new float[16];
        for (int i = 0; i < 16; i++) {
            r[i] = (float) m[i];
        }
        return r;
    }

    /** Column-major multiply: returns a * b. */
    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] =
                      a[0 * 4 + row] * b[c * 4 + 0]
                    + a[1 * 4 + row] * b[c * 4 + 1]
                    + a[2 * 4 + row] * b[c * 4 + 2]
                    + a[3 * 4 + row] * b[c * 4 + 3];
            }
        }
        return r;
    }

    /**
     * Remap a GL-clip-space matrix (z in [-1, 1]) to Metal/D3D clip space (z in [0, 1]) by
     * premultiplying with z' = (z + w) / 2. Returns the matrix unchanged when the renderer already
     * uses homogeneous (GL) depth.
     */
    static float[] toClip(double[] sceneToGlClip, boolean homogeneousDepth) {
        float[] m = toFloat(sceneToGlClip);
        if (homogeneousDepth) {
            return m;
        }
        // Column-major z-remap: rows unchanged except z' = 0.5 z + 0.5 w.
        final float[] zRemap = {
            1, 0, 0,    0,
            0, 1, 0,    0,
            0, 0, 0.5f, 0,
            0, 0, 0.5f, 1
        };
        return mul(zRemap, m);
    }
}
