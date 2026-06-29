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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

import com.jogamp.opengl.GL;

import com.jlmoya.gpu.GpuSurfaceComponent;
import com.jlmoya.gpu.NativeSurface;

import org.scilab.forge.scirenderer.implementation.bgfx.BgfxCanvas;
import org.scilab.forge.scirenderer.implementation.bgfx.BgfxCanvasFactory;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.gui.canvas.AbstractScilabCanvas;
import org.scilab.modules.gui.utils.Position;
import org.scilab.modules.gui.utils.Size;
import org.scilab.modules.renderer.JoGLView.DrawerVisitor;

/**
 * Experimental bgfx/Metal figure canvas (real-time 3D renderer, Layer-2).
 *
 * <p>Selected only when {@code -Dscilab.renderer.bgfx=true} (see {@link ScilabCanvasFactory}); the
 * default Scilab canvas remains the JOGL {@link SwingScilabCanvas}. It embeds the reusable Layer-1
 * Swing&lt;-&gt;GPU surface ({@link GpuSurfaceComponent}) and drives a {@link BgfxCanvas} (the
 * scirenderer bgfx backend) on a dedicated render thread, presenting directly to a {@code CAMetalLayer}.
 *
 * <p>The figure's {@code graphic_objects} are rendered through bgfx by the SHARED
 * {@link DrawerVisitor} — the very visitor the JOGL backend uses — so real plots (surf/plot3d) draw
 * on the GPU (Layer-3). Text/sprites are not yet rasterized. macOS only for now; any
 * construction/runtime failure falls back to JOGL upstream.
 *
 * @author Scilab macOS/2027 modernization
 */
public class SwingScilabBgfxCanvas extends AbstractScilabCanvas {

    private static final long serialVersionUID = 1L;

    /** Max time the render thread waits for the native surface (addNotify) before giving up. */
    private static final int SURFACE_WAIT_TRIES = 500;
    private static final int SURFACE_WAIT_STEP_MS = 10;

    private final AxesContainer figure;
    private final GpuSurfaceComponent surfaceComponent;
    private final BgfxCanvas bgfxCanvas;
    private final DrawerVisitor drawerVisitor;
    private volatile boolean running;
    private volatile Thread renderThread;

    public SwingScilabBgfxCanvas(final AxesContainer figure) {
        super(new BorderLayout());
        this.figure = figure;
        this.surfaceComponent = new GpuSurfaceComponent();
        add(surfaceComponent, BorderLayout.CENTER);
        setBackground(Color.black);
        setFocusable(true);
        setEnabled(true);

        // The scirenderer bgfx backend + the shared DrawerVisitor: the figure's graphic_objects
        // render through bgfx exactly as they do through JOGL, just via a different Canvas.
        this.bgfxCanvas = BgfxCanvasFactory.createCanvas(Math.max(1, getWidth()), Math.max(1, getHeight()));
        this.drawerVisitor = new DrawerVisitor(surfaceComponent, bgfxCanvas, figure);
        bgfxCanvas.setMainDrawer(drawerVisitor);

        startRenderThread();
    }

    private void startRenderThread() {
        Thread t = new Thread(() -> {
            NativeSurface s = waitForSurface();
            if (s == null || s.handle() == 0L) {
                System.err.println("[scilab.renderer.bgfx] no native surface acquired; "
                                   + "render thread aborting.");
                return;
            }
            bgfxCanvas.setSize(s.width(), s.height());
            if (!bgfxCanvas.initBgfx(s.handle())) {
                return;
            }
            running = true;
            try {
                while (running) {
                    NativeSurface cur = surfaceComponent.surface();
                    if (cur != null && cur.handle() != 0L) {
                        bgfxCanvas.setSize(cur.width(), cur.height());
                    }
                    bgfxCanvas.renderFrame();
                }
            } catch (Throwable err) {
                err.printStackTrace();
            } finally {
                bgfxCanvas.shutdownBgfx();
            }
        }, "scilab-bgfx-render-" + System.identityHashCode(this));
        t.setDaemon(true);
        renderThread = t;
        t.start();
    }

    /** Poll until the heavyweight peer is realized (addNotify -> CAMetalLayer) and sized. */
    private NativeSurface waitForSurface() {
        for (int i = 0; i < SURFACE_WAIT_TRIES; i++) {
            NativeSurface s = surfaceComponent.surface();
            if (s != null && s.handle() != 0L && surfaceComponent.getWidth() > 0) {
                return s;
            }
            try {
                Thread.sleep(SURFACE_WAIT_STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return surfaceComponent.surface();
    }

    // ---- AbstractScilabCanvas ----------------------------------------------

    @Override
    public AxesContainer getFigure() {
        return figure;
    }

    @Override
    public void addEventHandlerKeyListener(KeyListener listener) {
        addKeyListener(listener);
    }

    @Override
    public void removeEventHandlerKeyListener(KeyListener listener) {
        removeKeyListener(listener);
    }

    @Override
    public void addEventHandlerMouseListener(MouseListener listener) {
        surfaceComponent.addMouseListener(listener);
    }

    @Override
    public void removeEventHandlerMouseListener(MouseListener listener) {
        surfaceComponent.removeMouseListener(listener);
    }

    @Override
    public void addEventHandlerMouseMotionListener(MouseMotionListener listener) {
        surfaceComponent.addMouseMotionListener(listener);
    }

    @Override
    public void removeEventHandlerMouseMotionListener(MouseMotionListener listener) {
        surfaceComponent.removeMouseMotionListener(listener);
    }

    // ---- SimpleCanvas -------------------------------------------------------

    @Override
    public Size getDims() {
        return new Size(getWidth(), getHeight());
    }

    @Override
    public void setDims(Size newSize) {
        setSize(new Dimension(newSize.getWidth(), newSize.getHeight()));
    }

    @Override
    public Position getPosition() {
        return new Position(getX(), getY());
    }

    @Override
    public void setPosition(Position newPosition) {
        setLocation(newPosition.getX(), newPosition.getY());
    }

    @Override
    public void draw() {
        setVisible(true);
        doLayout();
    }

    @Override
    public void display() {
        repaint();
    }

    @Override
    public GL getGL() {
        return null;   // the bgfx canvas exposes no JOGL pipeline
    }

    @Override
    public void setAutoSwapBufferMode(boolean onOrOff) {
        // bgfx manages its own swap (vsync via reset flags)
    }

    @Override
    public boolean getAutoSwapBufferMode() {
        return false;
    }

    @Override
    public void setBackgroundColor(double red, double green, double blue) {
        setBackground(new Color((float) red, (float) green, (float) blue));
    }

    @Override
    public void close() {
        running = false;
        Thread t = renderThread;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public BufferedImage dumpAsBufferedImage() {
        return null;   // GPU readback from the Metal surface is future work
    }

    @Override
    public void setSingleBuffered(boolean useSingleBuffer) {
        // not applicable to the bgfx backend
    }

    @Override
    public void removeNotify() {
        close();                 // stop bgfx before the peer (and its CAMetalLayer) is torn down
        super.removeNotify();
    }
}
