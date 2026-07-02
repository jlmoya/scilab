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

import java.awt.Dimension;

import org.scilab.forge.scirenderer.Canvas;
import org.scilab.forge.scirenderer.Drawer;
import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.implementation.vulkan.buffers.VulkanBuffersManager;
import org.scilab.forge.scirenderer.implementation.vulkan.renderer.VulkanRendererManager;
import org.scilab.forge.scirenderer.implementation.vulkan.texture.VulkanTextureManager;
import org.scilab.forge.scirenderer.picking.PickingManager;
import org.scilab.forge.scirenderer.picking.PickingTask;

/**
 * Vulkan implementation of a scirenderer {@link Canvas}. Mirrors {@code G2DCanvas}: it owns the
 * managers + a {@link VulkanMotor}, and {@link #draw()} runs the backend-agnostic DrawerVisitor
 * (the main drawer) to accumulate the frame, then flushes the motor's arenas to the GPU.
 *
 * <p>The GPU is reached through a {@link VulkanSceneRenderer} injected by the GUI canvas via
 * {@link #setSceneRenderer} once the native surface + swapchain exist; until then the motor targets
 * {@link VulkanSceneRenderer#NOOP}, so the backend is fully exercisable headless.
 */
public final class VulkanCanvas implements Canvas {

    private final VulkanDrawingTools drawingTools;
    private final VulkanBuffersManager buffersManager;
    private final VulkanRendererManager rendererManager;
    private final VulkanTextureManager textureManager;
    private final VulkanMotor motor;
    private final Dimension dimension;

    private int antiAliasingLevel = 0;
    private boolean drawEnabled = true;
    private Drawer mainDrawer;

    private static final PickingManager PICKINGMANAGER = new PickingManager() {
        @Override
        public void addPickingTask(PickingTask pickingTask) {
        }
    };

    VulkanCanvas(int width, int height) {
        this.dimension = new Dimension(width, height);
        this.buffersManager = new VulkanBuffersManager();
        this.rendererManager = new VulkanRendererManager();
        this.textureManager = new VulkanTextureManager();
        this.motor = new VulkanMotor(this);
        this.drawingTools = new VulkanDrawingTools(this);
    }

    /** Inject the GPU renderer (backed by the swing-gpu-surface per-figure swapchain). */
    public void setSceneRenderer(VulkanSceneRenderer renderer) {
        motor.setRenderer(renderer);
    }

    VulkanMotor getMotor() {
        return motor;
    }

    public DrawingTools getDrawingTools() {
        return drawingTools;
    }

    public void setSize(int width, int height) {
        dimension.width = width;
        dimension.height = height;
    }

    public void draw() {
        if (drawEnabled && mainDrawer != null) {
            try {
                mainDrawer.draw(drawingTools);
                motor.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void disableDraw() {
        drawEnabled = false;
    }

    public void enableDraw() {
        drawEnabled = true;
    }

    // ---- Canvas interface ----

    @Override
    public void setMainDrawer(Drawer mainDrawer) {
        this.mainDrawer = mainDrawer;
    }

    @Override
    public Drawer getMainDrawer() {
        return mainDrawer;
    }

    @Override
    public VulkanRendererManager getRendererManager() {
        return rendererManager;
    }

    @Override
    public VulkanBuffersManager getBuffersManager() {
        return buffersManager;
    }

    @Override
    public PickingManager getPickingManager() {
        return PICKINGMANAGER;
    }

    @Override
    public VulkanTextureManager getTextureManager() {
        return textureManager;
    }

    @Override
    public int getWidth() {
        return dimension.width;
    }

    @Override
    public int getHeight() {
        return dimension.height;
    }

    @Override
    public Dimension getDimension() {
        return dimension;
    }

    @Override
    public int getAntiAliasingLevel() {
        return antiAliasingLevel;
    }

    @Override
    public void setAntiAliasingLevel(int antiAliasingLevel) {
        this.antiAliasingLevel = antiAliasingLevel;
    }

    @Override
    public void redraw() {
        draw();
    }

    @Override
    public void redrawAndWait() {
        draw();
    }

    @Override
    public void waitImage() {
    }

    @Override
    public void destroy() {
        motor.clean();
    }
}
