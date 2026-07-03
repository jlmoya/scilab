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

package org.scilab.modules.gui.bridge.canvas;

import java.io.File;

import javax.xml.xpath.XPathFactory;

import cc.sosonline.gpu.VulkanScene;

import org.scilab.modules.commons.ScilabConstants;
import org.scilab.modules.commons.xml.XConfiguration;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.w3c.dom.Document;

/**
 * Picks the rendering backend for a new figure canvas. The Vulkan canvas is opt-in via
 * Preferences &gt; General &gt; Graphics, or the {@code -Dscilab.renderer.vulkan} property (which,
 * when set, wins in both directions — launch override + tests). Read per figure creation, so
 * toggling the preference applies to new figures without a restart. Any failure creating the
 * Vulkan canvas (missing loader, no device, ...) falls back to the JOGL canvas so a figure
 * always renders.
 */
public final class ScilabCanvasFactory {

    private ScilabCanvasFactory() {
    }

    private static boolean isVulkanRequested() {
        String prop = System.getProperty("scilab.renderer.vulkan");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        try {
            Document doc = XConfiguration.getXConfigurationDocument();
            if (doc != null) {
                String state = XPathFactory.newInstance().newXPath()
                               .evaluate("string(//general/graphics/body/rendering/@renderer-vulkan)", doc);
                return "checked".equals(state);
            }
        } catch (Exception e) {
            // unreadable configuration: stay on the default renderer
        }
        return false;
    }

    public static AbstractScilabCanvas createCanvas(AxesContainer figure) {
        if (isVulkanRequested()) {
            try {
                // Bring up the shared Vulkan context HERE (synchronously) so a missing loader /
                // device / shader throws now and falls through to JOGL. The Vulkan canvas otherwise
                // does all its bring-up later on its own render thread, where a failure would leave
                // the figure permanently blank instead of falling back.
                resolveBundledLoader();
                VulkanScene.ensureContext();
                return new SwingScilabVulkanCanvas(figure);
            } catch (Throwable t) {
                System.err.println("[scilab.vulkan] Vulkan unavailable, using JOGL: " + t);
            }
        }
        return new SwingScilabCanvas(figure);
    }

    private static boolean loaderResolved;

    /**
     * If {@code -Dvk.loader} isn't set, point it at the MoltenVK dylib bundled next to the Scilab
     * install (SCI-relative, covering dev + packaged layouts). If none is found, VulkanScene falls
     * back to {@code $VULKAN_SDK} / its dev path.
     */
    private static synchronized void resolveBundledLoader() {
        if (loaderResolved || System.getProperty("vk.loader") != null) {
            loaderResolved = true;
            return;
        }
        String sci = ScilabConstants.SCI.getPath();
        String[] candidates = {
            sci + "/thirdparty/libMoltenVK.dylib",
            sci + "/../thirdparty/libMoltenVK.dylib",
            sci + "/../../thirdparty/libMoltenVK.dylib",
        };
        for (String c : candidates) {
            if (new File(c).exists()) {
                System.setProperty("vk.loader", c);
                break;
            }
        }
        loaderResolved = true;
    }
}
