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
 * Factory for the bgfx {@link BgfxCanvas}. The caller (the gui-side SwingScilabBgfxCanvas) then runs
 * {@link BgfxCanvas#initBgfx(long)} / {@link BgfxCanvas#renderFrame()} / {@link BgfxCanvas#shutdownBgfx()}
 * on its render thread, mirroring how SwingScilabCanvas drives a JoGLCanvas.
 */
public final class BgfxCanvasFactory {

    private BgfxCanvasFactory() {
    }

    public static BgfxCanvas createCanvas(int width, int height) {
        return new BgfxCanvas(width, height);
    }
}
