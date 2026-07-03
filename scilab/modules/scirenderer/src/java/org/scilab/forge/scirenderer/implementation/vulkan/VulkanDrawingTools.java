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

import org.scilab.forge.scirenderer.Canvas;
import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.SciRendererException;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.clipping.ClippingManager;
import org.scilab.forge.scirenderer.implementation.jogl.drawer.JoGLShapeDrawer.AntiAliasing;
import org.scilab.forge.scirenderer.lightning.LightManager;
import org.scilab.forge.scirenderer.renderer.Renderer;
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;
import org.scilab.forge.scirenderer.texture.AnchorPosition;
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.tranformations.TransformationManager;
import org.scilab.forge.scirenderer.tranformations.TransformationManagerImpl;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

/**
 * Vulkan implementation of the DrawingTools — a thin delegation layer, exactly like g2d's. Geometry
 * and clears go to the {@link VulkanMotor}; the transformation manager is the reusable core
 * {@link TransformationManagerImpl}. Texture / sprite draws (labels, tick numbers, marks, colormap,
 * image plots) are the next slice and are currently no-ops, so the first slice renders filled
 * surfaces + wireframe + axis lines.
 */
public class VulkanDrawingTools implements DrawingTools {

    private final TransformationManager transformationManager;
    private final VulkanLightManager lightManager;
    private final VulkanClippingManager clippingManager;
    private final VulkanCanvas canvas;

    VulkanDrawingTools(VulkanCanvas canvas) {
        this.canvas = canvas;
        this.transformationManager = new TransformationManagerImpl(canvas);
        this.lightManager = new VulkanLightManager();
        this.clippingManager = new VulkanClippingManager();
    }

    @Override
    public Canvas getCanvas() {
        return canvas;
    }

    @Override
    public TransformationManager getTransformationManager() {
        return transformationManager;
    }

    @Override
    public LightManager getLightManager() {
        return lightManager;
    }

    @Override
    public ClippingManager getClippingManager() {
        return clippingManager;
    }

    @Override
    public void clear(Color color) {
        canvas.getMotor().reset(color);
    }

    @Override
    public void clear(java.awt.Color color) {
        canvas.getMotor().reset(color);
    }

    @Override
    public void clearDepthBuffer() {
        canvas.getMotor().clearDepth();
    }

    @Override
    public void draw(Renderer renderer) {
        canvas.getRendererManager().draw(this, renderer);
    }

    @Override
    public void draw(Geometry geometry) throws SciRendererException {
        canvas.getMotor().draw(this, geometry, Appearance.getDefault());
    }

    @Override
    public void draw(Geometry geometry, AntiAliasing eAntiAliasing) throws SciRendererException {
        canvas.getMotor().draw(this, geometry, Appearance.getDefault());
    }

    @Override
    public void draw(Geometry geometry, Appearance appearance) throws SciRendererException {
        canvas.getMotor().draw(this, geometry, appearance);
    }

    @Override
    public void draw(Geometry geometry, Appearance appearance, AntiAliasing eAntiAliasing) throws SciRendererException {
        canvas.getMotor().draw(this, geometry, appearance);
    }

    // ---- texture / sprite path: all position variants funnel to the motor's sprite emitter.
    //      (auxColor/colors modulation is not applied yet — glyph sprites carry their colour in
    //      the texture itself, which covers text/labels/ticks; per-mark colour is a later slice.)

    @Override
    public void draw(Texture texture) throws SciRendererException {
        // Image plots (Matplot): a textured quad in model space, depth-tested with the scene.
        canvas.getMotor().drawImage(this, texture);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, 0, 1, 0);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, double rotationAngle) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, 0, 1, rotationAngle);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, Color auxColor, ElementsBuffer colors) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, 0, 1, 0);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, double rotationAngle, Color auxColor, ElementsBuffer colors) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, 0, 1, rotationAngle);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, int offset, int stride, double rotationAngle) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, offset, stride, rotationAngle);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, int offset, int stride, double rotationAngle, Color auxColor, ElementsBuffer colors) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, positions, offset, stride, rotationAngle);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, Vector3d position) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, position, 0);
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, Vector3d position, double rotationAngle) throws SciRendererException {
        canvas.getMotor().drawSprite(this, texture, anchor, position, rotationAngle);
    }
}
