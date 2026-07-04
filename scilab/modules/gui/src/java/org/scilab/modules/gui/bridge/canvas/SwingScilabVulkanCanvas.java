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

import java.awt.Color;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

import com.jogamp.opengl.GL;

import cc.sosonline.gpu.GpuSurfaceComponent;
import cc.sosonline.gpu.NativeSurface;
import cc.sosonline.gpu.VulkanScene;

import org.scilab.forge.scirenderer.Canvas;
import org.scilab.forge.scirenderer.implementation.vulkan.VulkanCanvas;
import org.scilab.forge.scirenderer.implementation.vulkan.VulkanCanvasFactory;
import org.scilab.forge.scirenderer.implementation.vulkan.VulkanSceneRenderer;
import org.scilab.modules.graphic_objects.axes.AxesContainer;
import org.scilab.modules.graphic_objects.figure.Figure;
import org.scilab.modules.graphic_objects.graphicController.GraphicController;
import org.scilab.modules.graphic_objects.graphicObject.GraphicObjectProperties;
import org.scilab.modules.graphic_objects.graphicView.GraphicView;
import org.scilab.modules.gui.graphicWindow.PanelLayout;
import org.scilab.modules.renderer.JoGLView.DrawerVisitor;

/**
 * The Vulkan-backed figure canvas: embeds the Layer-1 {@link GpuSurfaceComponent} (an AWT component
 * exposing a native CAMetalLayer) and drives the scirenderer {@link VulkanCanvas} + shared
 * {@link DrawerVisitor} from a dedicated render thread.
 *
 * <p>The render thread is signal-driven, not free-running: the graphic_objects model is mutated on
 * the interpreter thread under per-object monitors but read here without locks, so a free-running
 * traversal can latch a stale child list (a surface added to an axes never becomes visible — the
 * DrawerVisitor also ignores {@code __GO_CHILDREN__} updates unless the id is the figure's). The
 * {@link RedrawNotifier} therefore wakes this thread on EVERY model mutation, which both provides
 * the happens-before edge and covers the ignored axes-children case; the loop then blocks again
 * with a keep-alive timeout.
 *
 * <p>Set {@code -Dscilab.renderer.vulkan.shot=<path>} to write each presented frame to a PNG — the
 * non-intrusive verification/export path (the figure only, never the desktop).
 */
public class SwingScilabVulkanCanvas extends AbstractScilabCanvas {

    private static final long serialVersionUID = 1L;
    private static final int KEEP_ALIVE_MS = 100;
    private static final int MAX_CONSECUTIVE_FAILURES = 30;

    private final AxesContainer figure;
    private final GpuSurfaceComponent surfaceComponent;
    private final VulkanCanvas rendererCanvas;
    private final DrawerVisitor drawerVisitor;
    private final RedrawNotifier notifier;

    private final Object redrawLock = new Object();
    private boolean needsRedraw = true;
    private volatile boolean stopRequested;
    private final Thread renderThread;
    private volatile VulkanScene scene;

    private Integer id;

    public SwingScilabVulkanCanvas(final AxesContainer figure) {
        super(new PanelLayout());
        this.figure = figure;

        surfaceComponent = new GpuSurfaceComponent();
        surfaceComponent.setEnabled(true);
        surfaceComponent.setVisible(true);
        add(surfaceComponent, PanelLayout.GL_CANVAS);

        rendererCanvas = VulkanCanvasFactory.createCanvas(1, 1);
        rendererCanvas.setRedrawRequestListener(this::wakeRedraw);
        drawerVisitor = new DrawerVisitor(surfaceComponent, rendererCanvas, figure);
        rendererCanvas.setMainDrawer(drawerVisitor);

        notifier = new RedrawNotifier();
        GraphicController.getController().register(notifier);

        surfaceComponent.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                wakeRedraw();
            }
        });

        renderThread = new Thread(this::renderLoop, "scilab-vulkan-render");
        renderThread.setDaemon(true);
        renderThread.start();

        setBackground(Color.white);
        setFocusable(true);
        setEnabled(true);
    }

    // ---- render thread ----

    private void renderLoop() {
        try {
            NativeSurface surface = waitForSurface();
            if (surface == null) {
                if (!stopRequested) {
                    System.err.println("[scilab.vulkan] native surface unavailable; this figure will not render");
                }
                return;
            }
            scene = new VulkanScene(surface.handle(), surface.width(), surface.height());
            final VulkanScene s = scene;
            rendererCanvas.setSceneRenderer(new VulkanSceneRenderer() {
                @Override
                public void resize(int width, int height) {
                    s.resize(width, height);
                }

                @Override
                public void beginFrame(float r, float g, float b, float a) {
                    s.beginFrame(r, g, b, a);
                }

                @Override
                public void triangles(float[] clipPosColor, int floatCount) {
                    s.triangles(clipPosColor, floatCount);
                }

                @Override
                public void lines(float[] clipPosColor, int floatCount) {
                    s.lines(clipPosColor, floatCount);
                }

                @Override
                public void depthEpochs(int[] splits) {
                    s.depthEpochs(splits);
                }

                @Override
                public void triangleClips(float[] clipDistances, int floatCount) {
                    s.triangleClips(clipDistances, floatCount);
                }

                @Override
                public void lineClips(float[] clipDistances, int floatCount) {
                    s.lineClips(clipDistances, floatCount);
                }

                @Override
                public long uploadTexture(int width, int height, java.nio.ByteBuffer rgba, boolean linearFilter) {
                    return s.uploadTexture(width, height, rgba, linearFilter);
                }

                @Override
                public void destroyTexture(long handle) {
                    s.destroyTexture(handle);
                }

                @Override
                public void sprite(long textureHandle, float[] posUv24, float[] tintAux8) {
                    s.sprite(textureHandle, posUv24, tintAux8);
                }

                @Override
                public void image(long textureHandle, float[] clipPosUv36) {
                    s.image(textureHandle, clipPosUv36);
                }

                @Override
                public void endFrame() {
                    s.endFrame();
                }

                @Override
                public void dispose() {
                    // scene lifetime is owned by the render loop's finally block
                }
            });
            System.out.println("[scilab.vulkan] canvas ready: " + surface.width() + "x" + surface.height());

            final String shot = perFigurePath(System.getProperty("scilab.renderer.vulkan.shot"));
            int consecutiveFailures = 0;
            while (!stopRequested) {
                awaitRedraw(KEEP_ALIVE_MS);
                if (stopRequested) {
                    break;
                }
                // stop presenting once the component is torn down (window closing / app exit):
                // the CAMetalLayer's backing scale collapses during disposal and keep-alive frames
                // would render (and capture) degenerate logical-size frames
                if (!surfaceComponent.isShowing()) {
                    continue;
                }
                int pw = surface.width();
                int ph = surface.height();
                if (pw <= 0 || ph <= 0) {
                    continue;
                }
                // scirenderer canvas dimension = PHYSICAL pixels (matches the swapchain); the
                // projection is resolution-independent, only the aspect matters.
                rendererCanvas.setSize(pw, ph);
                if (shot != null) {
                    scene.captureNext(shot);
                }
                try {
                    rendererCanvas.draw();
                    consecutiveFailures = 0;
                } catch (Throwable t) {
                    logOnce(t);
                    // a persistent fatal error (device lost, surface lost) would otherwise be
                    // retried every keep-alive tick forever — stop the loop after a run of them
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        System.err.println("[scilab.vulkan] render thread stopping after "
                            + consecutiveFailures + " consecutive failures; last: " + t);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[scilab.vulkan] render thread failed: " + t);
            t.printStackTrace();
        } finally {
            VulkanScene sc = scene;
            scene = null;
            if (sc != null) {
                sc.dispose();
            }
        }
    }

    /**
     * Make the capture path unique per figure ("/tmp/x.png" -> "/tmp/x-0.png") so multi-figure
     * runs don't overwrite each other's shots.
     */
    private String perFigurePath(String path) {
        if (path == null) {
            return null;
        }
        int fid = (figure instanceof Figure) ? ((Figure) figure).getId() : figure.getIdentifier();
        int dot = path.lastIndexOf('.');
        return (dot > 0) ? path.substring(0, dot) + "-" + fid + path.substring(dot) : path + "-" + fid;
    }

    private NativeSurface waitForSurface() throws InterruptedException {
        for (int i = 0; i < 3000 && !stopRequested; i++) {
            NativeSurface s = surfaceComponent.surface();
            if (s != null && s.handle() != 0L && surfaceComponent.getWidth() > 0) {
                return s;
            }
            Thread.sleep(10);
        }
        return null;
    }

    private String lastError;

    private void logOnce(Throwable t) {
        String key = String.valueOf(t);
        if (!key.equals(lastError)) {
            lastError = key;
            System.err.println("[scilab.vulkan] draw failed (logged once): " + t);
            t.printStackTrace();
        }
    }

    // ---- redraw signalling ----

    private void wakeRedraw() {
        synchronized (redrawLock) {
            needsRedraw = true;
            redrawLock.notifyAll();
        }
    }

    private void awaitRedraw(long timeoutMs) {
        synchronized (redrawLock) {
            if (!needsRedraw && !stopRequested) {
                try {
                    redrawLock.wait(timeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            needsRedraw = false;
        }
    }

    /** Wakes the render thread on every model mutation (see class javadoc). */
    private class RedrawNotifier implements GraphicView {
        @Override
        public void updateObject(Integer uid, int property) {
            wakeRedraw();
        }

        @Override
        public void createObject(Integer uid) {
            wakeRedraw();
        }

        @Override
        public void deleteObject(Integer uid) {
            wakeRedraw();
        }
    }

    // ---- AbstractScilabCanvas / SimpleCanvas ----

    @Override
    public Canvas getRendererCanvas() {
        return rendererCanvas;
    }

    @Override
    public AxesContainer getFigure() {
        return figure;
    }

    @Override
    public int getFigureIndex() {
        return figure.getIdentifier();
    }

    @Override
    public boolean isAutoResize() {
        Boolean b = (Boolean) GraphicController.getController().getProperty(figure.getIdentifier(),
                    GraphicObjectProperties.__GO_AUTORESIZE__);
        return b == null ? false : b;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void draw() {
        setVisible(true);
        doLayout();
        wakeRedraw();
    }

    @Override
    public org.scilab.modules.gui.utils.Size getDims() {
        return new org.scilab.modules.gui.utils.Size(getWidth(), getHeight());
    }

    @Override
    public void setDims(org.scilab.modules.gui.utils.Size newSize) {
        setSize(new java.awt.Dimension(newSize.getWidth(), newSize.getHeight()));
    }

    @Override
    public org.scilab.modules.gui.utils.Position getPosition() {
        return new org.scilab.modules.gui.utils.Position(getX(), getY());
    }

    @Override
    public void setPosition(org.scilab.modules.gui.utils.Position newPosition) {
        setLocation(newPosition.getX(), newPosition.getY());
    }

    @Override
    public void setBackgroundColor(double red, double green, double blue) {
        setBackground(new Color((float) red, (float) green, (float) blue));
    }

    @Override
    public void close() {
        stopRequested = true;
        wakeRedraw();
        try {
            renderThread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        GraphicController.getController().unregister(notifier);
        rendererCanvas.destroy();
    }

    @Override
    public BufferedImage dumpAsBufferedImage() {
        // The scene's readback buffer holds the last presented frame; snapshot() is GPU-locked,
        // so this is safe from the EDT / interpreter thread. Briefly wait out the async bring-up
        // window (export called right after plot) so we return the frame rather than null.
        VulkanScene sc = scene;
        for (int i = 0; sc == null && i < 100 && !stopRequested; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            sc = scene;
        }
        return (sc != null) ? sc.snapshot() : null;
    }

    @Override
    public void setSingleBuffered(boolean useSingleBuffer) {
    }

    @Override
    public void display() {
    }

    @Override
    public GL getGL() {
        return null;
    }

    @Override
    public void setAutoSwapBufferMode(boolean onOrOff) {
    }

    @Override
    public boolean getAutoSwapBufferMode() {
        return false;
    }

    // ---- event-handler plumbing (mirrors the JOGL canvas) ----
    //
    // Event-handler listeners (picking, figure editor, datatip create) consume ABSOLUTE
    // coordinates that are compared against the renderer's projection — which runs at the
    // PHYSICAL framebuffer size (2x on Retina), while AWT events are LOGICAL points. Wrap these
    // listeners to scale coordinates by the canvas/component ratio (1.0 = pass-through).
    // Rotate/zoom listeners attach directly to the component and use deltas, so they stay in
    // logical space (a uniform scale would make rotation twitchy on Retina).

    private final java.util.Map<MouseListener, MouseListener> wrappedMouse =
        new java.util.HashMap<MouseListener, MouseListener>();
    private final java.util.Map<MouseMotionListener, MouseMotionListener> wrappedMotion =
        new java.util.HashMap<MouseMotionListener, MouseMotionListener>();

    private java.awt.event.MouseEvent scaled(java.awt.event.MouseEvent e) {
        int cw = rendererCanvas.getWidth();
        int ch = rendererCanvas.getHeight();
        int lw = surfaceComponent.getWidth();
        int lh = surfaceComponent.getHeight();
        if (lw <= 0 || lh <= 0 || (cw == lw && ch == lh)) {
            return e;
        }
        int sx = (int) Math.round(e.getX() * (cw / (double) lw));
        int sy = (int) Math.round(e.getY() * (ch / (double) lh));
        return new java.awt.event.MouseEvent((java.awt.Component) e.getSource(), e.getID(), e.getWhen(),
                e.getModifiersEx(), sx, sy, e.getClickCount(), e.isPopupTrigger(), e.getButton());
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
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                listener.mouseClicked(scaled(e));
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                listener.mousePressed(scaled(e));
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                listener.mouseReleased(scaled(e));
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                listener.mouseEntered(scaled(e));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                listener.mouseExited(scaled(e));
            }
        };
        wrappedMouse.put(listener, wrapper);
        surfaceComponent.addMouseListener(wrapper);
    }

    @Override
    public void removeEventHandlerMouseListener(MouseListener listener) {
        MouseListener wrapper = wrappedMouse.remove(listener);
        surfaceComponent.removeMouseListener(wrapper != null ? wrapper : listener);
    }

    @Override
    public void addEventHandlerMouseMotionListener(final MouseMotionListener listener) {
        MouseMotionListener wrapper = new MouseMotionListener() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                listener.mouseDragged(scaled(e));
            }

            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                listener.mouseMoved(scaled(e));
            }
        };
        wrappedMotion.put(listener, wrapper);
        surfaceComponent.addMouseMotionListener(wrapper);
    }

    @Override
    public void removeEventHandlerMouseMotionListener(MouseMotionListener listener) {
        MouseMotionListener wrapper = wrappedMotion.remove(listener);
        surfaceComponent.removeMouseMotionListener(wrapper != null ? wrapper : listener);
    }

    @Override
    public void addNotify() {
        surfaceComponent.setVisible(true);
        surfaceComponent.setEnabled(true);
        super.addNotify();
    }
}
