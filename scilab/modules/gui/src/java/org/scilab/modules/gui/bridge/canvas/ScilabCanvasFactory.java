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

import org.scilab.forge.scirenderer.implementation.bgfx.BgfxCanvas;
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

    /** Guards the "could not read the preference" warning so it is logged at most once per process. */
    private static boolean bgfxPrefWarned = false;

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
            // Preferences may legitimately be unavailable (e.g. -nogui / very early startup) -> default
            // to JOGL. But don't silently swallow an unexpected config error that would suppress the
            // user's explicit choice: log it once so a genuine XConfiguration problem stays visible.
            if (!bgfxPrefWarned) {
                bgfxPrefWarned = true;
                System.err.println("[" + BGFX_PROPERTY + "] could not read the bgfx renderer preference"
                                   + " (defaulting to JOGL): " + t);
            }
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
            // bgfx is a single global context per process: only one figure can drive it at a time.
            // Claim the slot here; a concurrent bgfx figure falls back to JOGL rather than corrupting
            // the live context. The bgfx canvas's render thread releases the slot when it shuts down.
            if (BgfxCanvas.tryAcquireContext()) {
                try {
                    return new SwingScilabBgfxCanvas(figure);
                } catch (Throwable t) {
                    // An experimental backend must never break figure creation. The constructor starts
                    // its render thread last, so a throw here means the thread is not running to release
                    // the slot — release it before falling back to JOGL.
                    BgfxCanvas.releaseContext();
                    System.err.println("[" + BGFX_PROPERTY + "] bgfx canvas unavailable, "
                                       + "falling back to JOGL: " + t);
                }
            } else {
                System.err.println("[" + BGFX_PROPERTY + "] bgfx is already rendering another figure;"
                                   + " this figure uses the JOGL renderer.");
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
