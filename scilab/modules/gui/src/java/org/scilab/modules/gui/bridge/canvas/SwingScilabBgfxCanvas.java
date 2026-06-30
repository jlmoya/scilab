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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;

import com.jlmoya.gpu.GpuSurfaceComponent;
import com.jlmoya.gpu.NativeSurface;

import org.scilab.forge.scirenderer.implementation.bgfx.BgfxCanvas;
import org.scilab.forge.scirenderer.implementation.bgfx.BgfxCanvasFactory;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicView.GraphicView;
import org.scilab.modules.gui.canvas.AbstractScilabCanvas;
import org.scilab.modules.gui.utils.Position;
import org.scilab.modules.gui.utils.Size;
import org.scilab.modules.renderer.JoGLView.DrawerVisitor;

/**
 * Experimental bgfx/Metal figure canvas (real-time 3D renderer, Layer-2).
 *
 * <p>Selected when the bgfx backend is enabled — via the Preferences &gt; General &gt; Graphics checkbox
 * or {@code -Dscilab.renderer.bgfx=true} (see {@link ScilabCanvasFactory}); the default Scilab canvas
 * remains the JOGL {@link SwingScilabCanvas}. It embeds the reusable Layer-1 Swing&lt;-&gt;GPU surface
 * ({@link GpuSurfaceComponent}) and drives a {@link BgfxCanvas} (the scirenderer bgfx backend) on a
 * dedicated render thread, presenting directly to a {@code CAMetalLayer}.
 *
 * <p>The figure's {@code graphic_objects} are rendered through bgfx by the SHARED {@link DrawerVisitor}
 * — the very visitor the JOGL backend uses — so real plots (surf/plot3d, lines, text, marks, image
 * plots) draw on the GPU (Layer-3). macOS only for now.
 *
 * <p>Failure handling: if constructing this canvas fails, {@link ScilabCanvasFactory} falls back to
 * JOGL so a figure always appears. A failure later on the render thread (no surface acquired, or
 * {@code bgfx_init} fails) cannot retroactively choose JOGL — the figure is already committed — so it
 * shows an on-figure notice rather than a silent black window. bgfx is a single global context, so
 * only one figure renders through bgfx at a time; concurrent figures fall back to JOGL.
 *
 * @author Scilab macOS/2027 modernization
 */
public class SwingScilabBgfxCanvas extends AbstractScilabCanvas {

    private static final long serialVersionUID = 1L;

    /** Max time the render thread waits for the native surface (addNotify) before giving up. */
    private static final int SURFACE_WAIT_TRIES = 500;
    private static final int SURFACE_WAIT_STEP_MS = 10;

    /** Keep-alive cadence: redraw at least this often even without a model change (resize, screenshot). */
    private static final long RENDER_KEEPALIVE_MS = 100L;

    private final AxesContainer figure;
    private final GpuSurfaceComponent surfaceComponent;
    private final BgfxCanvas bgfxCanvas;
    private final DrawerVisitor drawerVisitor;
    private final RedrawNotifier redrawNotifier;
    // Shutdown signal. Only ever set true (by close()/removeNotify), so the render thread can never
    // re-arm itself after teardown was requested — the bug a plain running=true at loop start caused.
    private volatile boolean stopRequested = false;
    private volatile Thread renderThread;
    private boolean dumpWarned = false;

    // HiDPI: AWT delivers mouse coordinates in logical points, but the renderer's projection (used by
    // EntityPicker for datatip/object picking and by the editor) is computed at the bgfx framebuffer's
    // physical-pixel resolution. On a Retina display those differ by the backing-scale factor, so a
    // click never matches a projected data point. We scale the coordinates of the figure event-handler
    // listeners (picking/editing) to physical pixels; the rotate/zoom interaction attaches directly to
    // the surface and works on deltas, so it is intentionally left in logical space.
    private final Map<MouseListener, MouseListener> mouseWrappers = new HashMap<MouseListener, MouseListener>();
    private final Map<MouseMotionListener, MouseMotionListener> motionWrappers = new HashMap<MouseMotionListener, MouseMotionListener>();

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

        // Wake the render thread on every model change. The graphic_objects model is mutated on the
        // interpreter thread but read by our dedicated render thread with no shared lock; the
        // DrawerVisitor only signals a redraw for a subset of changes (it ignores __GO_CHILDREN__
        // updates on non-figure objects, so a surface added to an axes fires nothing). Registering
        // this notifier makes wakeRedraw() run after each mutation, on the mutating thread — its
        // redrawLock release then pairs with the render thread's awaitRedraw acquire to publish the
        // mutation. Without it the render thread can read a stale child list indefinitely.
        this.redrawNotifier = new RedrawNotifier(bgfxCanvas);
        GraphicController.getController().register(redrawNotifier);

        // Repaint promptly on resize. The render thread polls the surface size every frame, but only
        // wakes on a model change or the keep-alive tick; a window drag-resize that doesn't touch the
        // model would otherwise lag up to RENDER_KEEPALIVE_MS. componentResized fires after the AWT
        // layout, so by the time we wake the surface already reports the new size.
        surfaceComponent.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                bgfxCanvas.wakeRedraw();
            }
        });

        startRenderThread();
    }

    /**
     * Minimal {@link GraphicView} that forwards every model change to {@link BgfxCanvas#wakeRedraw()}.
     * It carries no state and does no drawing — it exists solely to give the render thread a
     * happens-before edge to model mutations (see the constructor for why this is necessary).
     */
    private static final class RedrawNotifier implements GraphicView {
        private final BgfxCanvas canvas;

        RedrawNotifier(BgfxCanvas canvas) {
            this.canvas = canvas;
        }

        @Override
        public void updateObject(Integer id, int property) {
            canvas.wakeRedraw();
        }

        @Override
        public void createObject(Integer id) {
            canvas.wakeRedraw();
        }

        @Override
        public void deleteObject(Integer id) {
            canvas.wakeRedraw();
        }
    }

    private void startRenderThread() {
        Thread t = new Thread(() -> {
            try {
                NativeSurface s = waitForSurface();
                if (s == null || s.handle() == 0L) {
                    reportInitFailure("no native surface was acquired");
                    return;
                }
                bgfxCanvas.setSize(s.width(), s.height());
                if (!bgfxCanvas.initBgfx(s.handle())) {
                    reportInitFailure("bgfx could not initialise on this display");
                    return;
                }
                renderLoop();
            } catch (Throwable err) {
                reportInitFailure("the render thread stopped on an unexpected error");
                err.printStackTrace();
            } finally {
                // Always run, on every exit path (init failure included), so the process-wide bgfx
                // context slot is freed for the next figure even when this one never rendered a frame.
                bgfxCanvas.shutdownBgfx();      // no-op if bgfx never initialised
                BgfxCanvas.releaseContext();
            }
        }, "scilab-bgfx-render-" + System.identityHashCode(this));
        t.setDaemon(true);
        renderThread = t;
        t.start();
    }

    /** The frame loop, on the render thread, once bgfx is initialised. */
    private void renderLoop() {
        if (stopRequested) {
            return;   // close() was requested during init — do not start presenting
        }
        // Per-frame error throttle: a transient bgfx_reset/bgfx_frame failure must neither kill this
        // thread (which would freeze the figure) nor print a stack trace every frame.
        String lastErrSig = null;
        long suppressed = 0;
        while (!stopRequested) {
            // Block until the shared DrawerVisitor signals a model change (redraw), or a keep-alive
            // tick. This is the happens-before that lets the traversal below see the latest
            // graphic_objects model rather than a free-running, possibly stale read.
            bgfxCanvas.awaitRedraw(RENDER_KEEPALIVE_MS);
            if (stopRequested) {
                break;
            }
            NativeSurface cur = surfaceComponent.surface();
            if (cur == null || cur.handle() == 0L) {
                continue;   // peer/CAMetalLayer gone (teardown in flight) — never present to a dead surface
            }
            bgfxCanvas.setSize(cur.width(), cur.height());
            try {
                bgfxCanvas.renderFrame();
            } catch (Throwable err) {
                String sig = err.getClass().getName() + ": " + err.getMessage();
                if (!sig.equals(lastErrSig)) {
                    if (suppressed > 0) {
                        System.err.println("[scilab.renderer.bgfx] (" + suppressed
                                           + " more frame(s) failed with the previous error)");
                    }
                    System.err.println("[scilab.renderer.bgfx] render error "
                                       + "(identical errors suppressed until it changes):");
                    err.printStackTrace();
                    lastErrSig = sig;
                    suppressed = 0;
                } else {
                    suppressed++;
                }
            }
        }
    }

    /**
     * Report an async render-thread init failure. The figure is already committed to bgfx (the JOGL
     * fallback in {@link ScilabCanvasFactory} only guards construction), so instead of leaving a silent
     * black window we log a clear, actionable message and replace the dead GPU surface with an on-figure
     * notice. Best-effort — the log line is the reliable signal.
     */
    private void reportInitFailure(String reason) {
        System.err.println("[scilab.renderer.bgfx] this figure's GPU renderer failed to start ("
                           + reason + "). Disable the experimental backend in "
                           + "Preferences > General > Graphics (or -Dscilab.renderer.bgfx=false) "
                           + "to use the default JOGL renderer.");
        SwingUtilities.invokeLater(() -> {
            try {
                remove(surfaceComponent);
                JLabel notice = new JLabel(
                    "<html><div style='text-align:center'>The experimental bgfx/Metal renderer "
                    + "could not start for this figure.<br>Disable it in "
                    + "Preferences &gt; General &gt; Graphics.</div></html>",
                    SwingConstants.CENTER);
                notice.setForeground(Color.WHITE);
                add(notice, BorderLayout.CENTER);
                revalidate();
                repaint();
            } catch (Throwable ignored) {
                // best-effort; the logged message above is the reliable signal
            }
        });
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
    public void addEventHandlerMouseListener(final MouseListener listener) {
        MouseListener wrapper = new MouseListener() {
            public void mouseClicked(MouseEvent e)  {
                listener.mouseClicked(scaled(e));
            }
            public void mousePressed(MouseEvent e)  {
                listener.mousePressed(scaled(e));
            }
            public void mouseReleased(MouseEvent e) {
                listener.mouseReleased(scaled(e));
            }
            public void mouseEntered(MouseEvent e)  {
                listener.mouseEntered(scaled(e));
            }
            public void mouseExited(MouseEvent e)   {
                listener.mouseExited(scaled(e));
            }
        };
        mouseWrappers.put(listener, wrapper);
        surfaceComponent.addMouseListener(wrapper);
    }

    @Override
    public void removeEventHandlerMouseListener(MouseListener listener) {
        MouseListener wrapper = mouseWrappers.remove(listener);
        surfaceComponent.removeMouseListener(wrapper != null ? wrapper : listener);
    }

    @Override
    public void addEventHandlerMouseMotionListener(final MouseMotionListener listener) {
        MouseMotionListener wrapper = new MouseMotionListener() {
            public void mouseDragged(MouseEvent e) {
                listener.mouseDragged(scaled(e));
            }
            public void mouseMoved(MouseEvent e) {
                listener.mouseMoved(scaled(e));
            }
        };
        motionWrappers.put(listener, wrapper);
        surfaceComponent.addMouseMotionListener(wrapper);
    }

    @Override
    public void removeEventHandlerMouseMotionListener(MouseMotionListener listener) {
        MouseMotionListener wrapper = motionWrappers.remove(listener);
        surfaceComponent.removeMouseMotionListener(wrapper != null ? wrapper : listener);
    }

    /**
     * Rescale a mouse event from logical points to the bgfx framebuffer's physical pixels (the space
     * the renderer projection works in). Returns the original event when there is no HiDPI scaling.
     */
    private MouseEvent scaled(MouseEvent e) {
        final int cw = surfaceComponent.getWidth();
        final int ch = surfaceComponent.getHeight();
        if (cw <= 0 || ch <= 0) {
            return e;
        }
        final double sx = (double) bgfxCanvas.getWidth() / cw;
        final double sy = (double) bgfxCanvas.getHeight() / ch;
        if (sx == 1.0 && sy == 1.0) {
            return e;
        }
        return new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiersEx(),
                (int) Math.round(e.getX() * sx), (int) Math.round(e.getY() * sy),
                e.getXOnScreen(), e.getYOnScreen(),
                e.getClickCount(), e.isPopupTrigger(), e.getButton());
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
        stopRequested = true;
        GraphicController.getController().unregister(redrawNotifier);
        bgfxCanvas.wakeRedraw();   // unblock the render thread if it is waiting on a redraw
        Thread t = renderThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                // The render thread must be dead before removeNotify() lets the peer release the
                // CAMetalLayer it presents to, otherwise use-after-free. The loop re-checks
                // stopRequested after each <=100ms keep-alive, so this returns promptly; the generous
                // bound only guards a wedged GPU driver (warn, then proceed — never hang the EDT forever).
                t.join(5000);
                if (t.isAlive()) {
                    System.err.println("[scilab.renderer.bgfx] render thread did not stop within 5s; "
                                       + "proceeding with teardown.");
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public BufferedImage dumpAsBufferedImage() {
        // The bgfx backend presents to a CAMetalLayer, not an offscreen buffer the AWT export/print
        // path can read; GPU readback into a BufferedImage is not wired here yet. Returning null (which
        // callers treat as "no image") is correct, but say so once rather than failing silently.
        if (!dumpWarned) {
            dumpWarned = true;
            System.err.println("[scilab.renderer.bgfx] figure export/print is not supported by the "
                               + "experimental bgfx backend yet; use a JOGL figure to export, or "
                               + "-Dscilab.renderer.bgfx.shot=<path> to capture this figure to a PNG.");
        }
        return null;
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
