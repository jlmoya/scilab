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
 *
 */

package org.scilab.modules.gui.bridge.canvas;

import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.gui.canvas.AbstractScilabCanvas;

/**
 * Chooses the rendering backend for a Scilab figure canvas.
 *
 * <p>The default is the JOGL {@link SwingScilabCanvas}. When the system property
 * {@code scilab.renderer.bgfx} is {@code true}, the experimental bgfx/Metal canvas
 * ({@link SwingScilabBgfxCanvas}) is used instead — the integration milestone that proves a modern
 * real-time 3D (bgfx) surface inside a real Scilab figure. The bgfx path is opt-in and macOS-only
 * for now; on ANY error constructing it we fall back to JOGL, so a figure always renders.
 *
 * <p>Enable with, e.g.: {@code scilab -J-Dscilab.renderer.bgfx=true}.
 *
 * @author Scilab macOS/2027 modernization
 */
public final class ScilabCanvasFactory {

    /** System property to opt into the bgfx/Metal canvas (off by default). */
    public static final String BGFX_PROPERTY = "scilab.renderer.bgfx";

    private ScilabCanvasFactory() {
    }

    /**
     * @return {@code true} if the bgfx/Metal canvas is requested via the system property.
     */
    public static boolean isBgfxRequested() {
        return Boolean.getBoolean(BGFX_PROPERTY);
    }

    /**
     * Creates the figure canvas for the given figure, honoring the renderer flag.
     *
     * @param figure the MVC figure (axes container)
     * @return a bgfx canvas when requested and available, otherwise the default JOGL canvas
     */
    public static AbstractScilabCanvas createCanvas(final AxesContainer figure) {
        if (isBgfxRequested()) {
            try {
                return new SwingScilabBgfxCanvas(figure);
            } catch (Throwable t) {
                // An experimental backend must never break figure creation.
                System.err.println("[" + BGFX_PROPERTY + "] bgfx canvas unavailable, "
                                   + "falling back to JOGL: " + t);
            }
        }
        return new SwingScilabCanvas(figure);
    }
}
