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

import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.SciRendererException;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.clipping.ClippingManager;
import org.scilab.forge.scirenderer.implementation.bgfx.clipping.BgfxClippingManager;
import org.scilab.forge.scirenderer.implementation.bgfx.lighting.BgfxLightManager;
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
 * bgfx implementation of {@link DrawingTools}.
 *
 * <p>Geometry draws are forwarded to {@link BgfxShapeDrawer} (immediate-mode bgfx submits). Texture
 * draws (text, marks, image plots) are no-ops in the first slice — they don't yet rasterize to the
 * GPU. The transformation is read per-draw by the shape drawer (no GL-style matrix listener needed).
 */
public class BgfxDrawingTools implements DrawingTools {

    private final TransformationManager transformationManager;
    private final BgfxLightManager lightManager;
    private final BgfxClippingManager clippingManager;
    private final BgfxCanvas canvas;

    BgfxDrawingTools(BgfxCanvas canvas) {
        this.canvas = canvas;
        this.transformationManager = new TransformationManagerImpl(canvas);
        this.lightManager = new BgfxLightManager();
        this.clippingManager = new BgfxClippingManager();
    }

    @Override
    public BgfxCanvas getCanvas() {
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
        canvas.setClearRgba(rgba(color));
    }

    @Override
    public void clear(java.awt.Color color) {
        canvas.setClearRgba(rgba(color));
    }

    private static int rgba(java.awt.Color c) {
        return (c.getRed() << 24) | (c.getGreen() << 16) | (c.getBlue() << 8) | c.getAlpha();
    }

    @Override
    public void clearDepthBuffer() {
        // Depth is cleared with the view each frame.
    }

    @Override
    public void draw(Renderer renderer) {
        canvas.getRendererManager().draw(this, renderer);
    }

    @Override
    public void draw(Geometry geometry) throws SciRendererException {
        BgfxShapeDrawer.draw(this, geometry, Appearance.getDefault());
    }

    @Override
    public void draw(Geometry geometry, AntiAliasing eAntiAliasing) throws SciRendererException {
        BgfxShapeDrawer.draw(this, geometry, Appearance.getDefault());
    }

    @Override
    public void draw(Geometry geometry, Appearance appearance) throws SciRendererException {
        BgfxShapeDrawer.draw(this, geometry, appearance);
    }

    @Override
    public void draw(Geometry geometry, Appearance appearance, AntiAliasing eAntiAliasing) throws SciRendererException {
        BgfxShapeDrawer.draw(this, geometry, appearance);
    }

    // ---- Texture draws: not yet rasterized (text/marks/images) --------------

    @Override
    public void draw(Texture texture) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, double rotationAngle) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, Color auxColor, ElementsBuffer colors) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, double rotationAngle, Color auxColor, ElementsBuffer colors) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, int offset, int stride, double rotationAngle) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, ElementsBuffer positions, int offset, int stride, double rotationAngle, Color auxColor, ElementsBuffer colors) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, Vector3d position) throws SciRendererException {
    }

    @Override
    public void draw(Texture texture, AnchorPosition anchor, Vector3d position, double rotationAngle) throws SciRendererException {
    }
}
