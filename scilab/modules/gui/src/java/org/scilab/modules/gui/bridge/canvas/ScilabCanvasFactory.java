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

import org.w3c.dom.Document;

import org.scilab.modules.commons.xml.XConfiguration;
import static org.scilab.modules.commons.xml.XConfiguration.XConfAttribute;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.gui.canvas.AbstractScilabCanvas;

/**
 * Chooses the rendering backend for a Scilab figure canvas.
 *
 * <p>The default is the JOGL {@link SwingScilabCanvas}. The experimental bgfx/Metal canvas
 * ({@link SwingScilabBgfxCanvas}) is used instead when enabled — a modern real-time 3D (bgfx) surface
 * inside a real Scilab figure. It is opt-in and macOS-only for now; on ANY error constructing it we
 * fall back to JOGL, so a figure always renders.
 *
 * <p>Two ways to enable it, checked in this order:
 * <ol>
 *   <li>the system property {@code -Dscilab.renderer.bgfx=true} (a launch-time override, also used by
 *       tests). Setting it to {@code false} force-disables, ignoring the preference.</li>
 *   <li>the <b>Preferences &gt; General &gt; Graphics</b> checkbox (path {@code //general/graphics/body/rendering},
 *       attribute {@code renderer-bgfx}). Applies to newly-created figures.</li>
 * </ol>
 *
 * @author Scilab macOS/2027 modernization
 */
public final class ScilabCanvasFactory {

    /** System property to opt into the bgfx/Metal canvas (overrides the preference when present). */
    public static final String BGFX_PROPERTY = "scilab.renderer.bgfx";

    /** XConfiguration path of the Preferences checkbox backing the bgfx toggle. */
    private static final String BGFX_PREF_PATH = "//general/graphics/body/rendering";

    private ScilabCanvasFactory() {
    }

    /**
     * @return {@code true} if the bgfx/Metal canvas should be used. The {@code scilab.renderer.bgfx}
     *         system property wins when set (true/false); otherwise the Preferences checkbox decides.
     */
    public static boolean isBgfxRequested() {
        if (System.getProperty(BGFX_PROPERTY) != null) {
            return Boolean.getBoolean(BGFX_PROPERTY);
        }
        return isBgfxPreferenceEnabled();
    }

    /** Read the Preferences checkbox; defaults to {@code false} when preferences are unavailable. */
    private static boolean isBgfxPreferenceEnabled() {
        try {
            Document doc = XConfiguration.getXConfigurationDocument();
            BgfxRendererPref[] prefs = XConfiguration.get(BgfxRendererPref.class, doc, BGFX_PREF_PATH);
            return prefs.length > 0 && prefs[0].enabled;
        } catch (Throwable t) {
            // No preferences (e.g. -nogui / very early startup) -> default JOGL.
            return false;
        }
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

    /** Maps the {@code renderer-bgfx} checkbox attribute ("checked"/"unchecked") to a boolean. */
    @XConfAttribute
    private static class BgfxRendererPref {

        public boolean enabled;

        private BgfxRendererPref() { }

        @XConfAttribute(attributes = {"renderer-bgfx"})
        private void set(String rendererBgfx) {
            this.enabled = "checked".equals(rendererBgfx) || "true".equals(rendererBgfx);
        }
    }
}
