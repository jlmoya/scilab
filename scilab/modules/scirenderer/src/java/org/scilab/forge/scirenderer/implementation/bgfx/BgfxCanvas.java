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

import org.scilab.forge.scirenderer.Canvas;
import org.scilab.forge.scirenderer.Drawer;
import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.implementation.bgfx.buffers.BgfxBuffersManager;
import org.scilab.forge.scirenderer.implementation.bgfx.renderer.BgfxRendererManager;
import org.scilab.forge.scirenderer.implementation.bgfx.texture.BgfxTextureManager;
import org.scilab.forge.scirenderer.picking.PickingManager;
import org.scilab.forge.scirenderer.picking.PickingTask;

import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;

import java.awt.Dimension;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * bgfx implementation of a scirenderer {@link Canvas}: it renders the figure's graphic_objects
 * (visited by the shared DrawerVisitor) into a bgfx/Metal surface.
 *
 * <p>bgfx is a single global context per process, so this canvas owns {@code bgfx_init} (on the
 * figure's native window handle) and the whole frame lifecycle. It is created up-front (managers +
 * drawing tools), then {@link #initBgfx(long)} runs once on the render thread, {@link #renderFrame()}
 * runs each frame, and {@link #shutdownBgfx()} tears it down — all on the same render thread (driven
 * by the gui-side SwingScilabBgfxCanvas).
 *
 * <p>Each draw bakes the scene-to-clip matrix into the model transform (view = proj = identity).
 * Two programs cover the scene: a vertex-color program for filled and line geometry, and a textured
 * program for colormap surfaces, screen-aligned text/mark sprites, and image plots (Matplot/Grayplot).
 */
public final class BgfxCanvas implements Canvas {

    static final short INVALID_HANDLE = (short) 0xffff;
    private static final int VIEW_ID = 0;
    private static final int DEFAULT_CLEAR_RGBA = 0x000000ff;

    // bgfx is a SINGLE global context per process (one bgfx_init / bgfx_frame / bgfx_shutdown, all on
    // one thread), so at most one figure may drive bgfx at a time. The gui factory acquires this slot
    // before constructing the bgfx canvas and falls back to JOGL when it is already taken; the owning
    // render thread releases it after shutdownBgfx(). Without this guard a second figure's bgfx_init
    // corrupts the first figure's live context. (Multiple concurrent bgfx figures would need a shared
    // context with per-window framebuffers driven by one render thread — a future enhancement.)
    private static final AtomicBoolean CONTEXT_IN_USE = new AtomicBoolean(false);

    /** Try to claim the process-wide bgfx context. @return {@code true} if acquired; caller must release. */
    public static boolean tryAcquireContext() {
        return CONTEXT_IN_USE.compareAndSet(false, true);
    }

    /** Release the process-wide bgfx context. Call only after the render thread's {@link #shutdownBgfx()}. */
    public static void releaseContext() {
        CONTEXT_IN_USE.set(false);
    }

    private final BgfxBuffersManager buffersManager;
    private final BgfxRendererManager rendererManager;
    private final BgfxTextureManager textureManager;
    private final BgfxDrawingTools drawingTools;
    private final Dimension dimension;

    private static final PickingManager PICKING_MANAGER = new PickingManager() {
        @Override
        public void addPickingTask(PickingTask pickingTask) { }
    };

    private Drawer mainDrawer;
    private int antiAliasingLevel = 0;

    // bgfx resources (valid between initBgfx and shutdownBgfx).
    private volatile boolean initialised = false;
    private boolean sizeDirty = false;
    private short program = INVALID_HANDLE;
    private short texProgram = INVALID_HANDLE;
    private short uColor = INVALID_HANDLE;
    private short uParams = INVALID_HANDLE;
    private short sTexColor = INVALID_HANDLE;
    private BGFXVertexLayout layout;
    private BGFXVertexLayout texLayout;
    private boolean homogeneousDepth;
    private FloatBuffer identityView;
    private FloatBuffer identityProj;
    private int clearRgba = DEFAULT_CLEAR_RGBA;

    // Optional non-intrusive QA capture: dumps the bgfx framebuffer (the figure only, never the
    // desktop) to -Dscilab.renderer.bgfx.shot=<path> a few seconds after init.
    private final String shotPath = System.getProperty("scilab.renderer.bgfx.shot");
    private long shotAtMs = 0L;
    private boolean shotRequested = false;
    private BgfxScreenShot screenShot;

    // Redraw coordination. The render thread blocks in awaitRedraw() until the shared DrawerVisitor
    // signals a model change via redraw()/redrawAndWait(); acquiring redrawLock there pairs with its
    // release here to publish the latest graphic_objects model to the render thread. Without this
    // happens-before a free-running read can keep seeing a stale child list and miss later mutations
    // (e.g. a surf surface added to the axes children after the first frames). A keep-alive timeout
    // still drives periodic frames for resize and the screenshot QA path.
    private final Object redrawLock = new Object();
    private boolean needsRedraw = true;

    BgfxCanvas(int width, int height) {
        this.dimension = new Dimension(Math.max(1, width), Math.max(1, height));
        this.buffersManager = new BgfxBuffersManager();
        this.rendererManager = new BgfxRendererManager();
        this.textureManager = new BgfxTextureManager();
        this.drawingTools = new BgfxDrawingTools(this);
    }

    // ---- bgfx lifecycle (render thread) -------------------------------------

    /**
     * Initialise bgfx on the given native window handle. Must run on the render thread, once.
     * @return {@code true} if bgfx initialised (the scene program may still be unavailable).
     */
    public boolean initBgfx(long nwh) {
        try (MemoryStack stack = stackPush()) {
            BGFXInit init = BGFXInit.malloc(stack);
            bgfx_init_ctor(init);
            init.type(BGFX_RENDERER_TYPE_COUNT);   // auto -> Metal on macOS
            init.resolution(res -> res.width(dimension.width).height(dimension.height).reset(BGFX_RESET_VSYNC));
            init.platformData(pd -> pd.nwh(nwh));
            // Always install the bgfx callback so driver-level fatal/trace errors are surfaced — not
            // only on the opt-in screenshot QA path; the screen-shot capture itself stays gated on shotPath.
            screenShot = new BgfxScreenShot();
            init.callback(screenShot.iface());
            if (!bgfx_init(init)) {
                System.err.println("[scirenderer.bgfx] bgfx_init failed (nwh=" + nwh + ")");
                screenShot.dispose();
                screenShot = null;
                return false;
            }
        }
        homogeneousDepth = bgfx_get_caps().homogeneousDepth();
        identityView = memAllocFloat(16).put(BgfxMat.identity()).flip();
        identityProj = memAllocFloat(16).put(BgfxMat.identity()).flip();

        // bgfx is up; mark initialised first so any failure below cleans up through shutdownBgfx()
        // (freeing the identity buffers, any partial program/uniforms, and the callback) — no leak.
        initialised = true;
        try {
            buildProgram();
        } catch (Throwable t) {
            System.err.println("[scirenderer.bgfx] scene program construction failed: " + t);
            t.printStackTrace();
            shutdownBgfx();
            return false;
        }

        if (shotPath != null) {
            shotAtMs = System.currentTimeMillis() + 4000L;
        }
        System.out.println("[scirenderer.bgfx] canvas ready: "
                           + bgfx_get_renderer_name(bgfx_get_renderer_type()) + "  "
                           + dimension.width + "x" + dimension.height
                           + (program == INVALID_HANDLE ? "  (scene program unavailable -> clear only; see errors above)" : ""));
        return true;
    }

    private void buildProgram() {
        short vsh = loadShader("vs_scene");
        short fsh = loadShader("fs_scene");
        if (vsh == INVALID_HANDLE || fsh == INVALID_HANDLE) {
            // Destroy whichever half loaded so a missing/failed pair never leaks a shader handle.
            destroyShaderIfValid(vsh);
            destroyShaderIfValid(fsh);
            System.err.println("[scirenderer.bgfx] scene shader pair unavailable; figure will clear only.");
            return;
        }
        program = bgfx_create_program(vsh, fsh, true);   // destroyShaders=true consumes both handles
        if (program == INVALID_HANDLE) {
            System.err.println("[scirenderer.bgfx] scene program link failed.");
        }

        layout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(layout, bgfx_get_renderer_type());
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(layout);

        uColor = bgfx_create_uniform("u_color", BGFX_UNIFORM_TYPE_VEC4, 1);
        uParams = bgfx_create_uniform("u_params", BGFX_UNIFORM_TYPE_VEC4, 1);

        // Textured program (colormap surfaces + text/mark sprites): POSITION + TEXCOORD0, sampled
        // and tinted by u_color (white for surfaces, the text/aux color for sprites).
        short vshTex = loadShader("vs_tex");
        short fshTex = loadShader("fs_tex");
        if (vshTex != INVALID_HANDLE && fshTex != INVALID_HANDLE) {
            texProgram = bgfx_create_program(vshTex, fshTex, true);
            if (texProgram == INVALID_HANDLE) {
                System.err.println("[scirenderer.bgfx] textured program link failed; "
                                   + "colormap surfaces, text and image plots will not draw.");
            }
            texLayout = BGFXVertexLayout.calloc();
            bgfx_vertex_layout_begin(texLayout, bgfx_get_renderer_type());
            bgfx_vertex_layout_add(texLayout, BGFX_ATTRIB_POSITION, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_add(texLayout, BGFX_ATTRIB_TEXCOORD0, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_end(texLayout);
            sTexColor = bgfx_create_uniform("s_texColor", BGFX_UNIFORM_TYPE_SAMPLER, 1);
        } else {
            destroyShaderIfValid(vshTex);
            destroyShaderIfValid(fshTex);
            System.err.println("[scirenderer.bgfx] textured shader pair unavailable; "
                               + "colormap surfaces, text and image plots will not draw.");
        }
    }

    private static void destroyShaderIfValid(short shader) {
        if (shader != INVALID_HANDLE) {
            bgfx_destroy_shader(shader);
        }
    }

    /**
     * Block the render thread until a redraw is requested (a DrawerVisitor model change) or the
     * keep-alive {@code timeoutMs} elapses, then clear the request. Acquiring {@code redrawLock}
     * here pairs with its release in {@link #redraw()} / {@link #redrawAndWait()} so the model
     * mutated on the calling thread is visible to this thread's subsequent {@link #renderFrame()}.
     */
    public void awaitRedraw(long timeoutMs) {
        synchronized (redrawLock) {
            // Bounded wait re-checked in a loop, so a spurious wakeup neither returns early nor drops a
            // pending redraw; the keep-alive timeout still bounds the idle wait.
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!needsRedraw) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    redrawLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            needsRedraw = false;
        }
    }

    /** Render one frame: clear, then let the shared DrawerVisitor submit the scene. */
    public void renderFrame() {
        if (!initialised) {
            return;
        }
        // Destroy GPU textures the model disposed since the last frame. bgfx is single-threaded, so the
        // destroy must run here on the render thread, not on the interpreter thread that disposed them.
        textureManager.drainDisposed();
        if (sizeDirty) {
            bgfx_reset(dimension.width, dimension.height, BGFX_RESET_VSYNC, BGFX_TEXTURE_FORMAT_COUNT);
            sizeDirty = false;
        }
        bgfx_set_view_rect(VIEW_ID, 0, 0, dimension.width, dimension.height);
        bgfx_set_view_clear(VIEW_ID, BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH, clearRgba, 1.0f, 0);
        bgfx_set_view_transform(VIEW_ID, identityView, identityProj);
        bgfx_touch(VIEW_ID);

        Drawer d = mainDrawer;
        if (d != null && program != INVALID_HANDLE) {
            try {
                d.draw(drawingTools);
            } catch (Throwable t) {
                reportDrawError(t);   // log once per distinct error; still present the (partial) frame
            }
        }
        if (shotPath != null && !shotRequested && System.currentTimeMillis() >= shotAtMs) {
            bgfx_request_screen_shot(INVALID_HANDLE, shotPath);
            shotRequested = true;
            System.out.println("[scirenderer.bgfx] screenshot -> " + shotPath);
        }
        bgfx_frame(false);
    }

    // Render-error throttle (render thread only). A persistent draw failure must not print a stack
    // trace every frame — that is 10+/s on the keep-alive tick and hundreds/s during an interactive
    // drag. Log each distinct error once in full, then just count the repeats.
    private String lastDrawErrorSig = null;
    private long suppressedDrawErrors = 0;

    private void reportDrawError(Throwable t) {
        String sig = t.getClass().getName() + ": " + t.getMessage();
        if (!sig.equals(lastDrawErrorSig)) {
            if (suppressedDrawErrors > 0) {
                System.err.println("[scirenderer.bgfx] (" + suppressedDrawErrors
                                   + " more frame(s) failed with the previous error)");
            }
            System.err.println("[scirenderer.bgfx] error drawing frame "
                               + "(identical errors will be suppressed until it changes):");
            t.printStackTrace();
            lastDrawErrorSig = sig;
            suppressedDrawErrors = 0;
        } else {
            suppressedDrawErrors++;
        }
    }

    public void shutdownBgfx() {
        if (!initialised) {
            return;
        }
        initialised = false;
        if (program != INVALID_HANDLE) {
            bgfx_destroy_program(program);
            program = INVALID_HANDLE;
        }
        if (texProgram != INVALID_HANDLE) {
            bgfx_destroy_program(texProgram);
            texProgram = INVALID_HANDLE;
        }
        if (sTexColor != INVALID_HANDLE) {
            bgfx_destroy_uniform(sTexColor);
            sTexColor = INVALID_HANDLE;
        }
        if (texLayout != null) {
            texLayout.free();
            texLayout = null;
        }
        if (uColor != INVALID_HANDLE) {
            bgfx_destroy_uniform(uColor);
            uColor = INVALID_HANDLE;
        }
        if (uParams != INVALID_HANDLE) {
            bgfx_destroy_uniform(uParams);
            uParams = INVALID_HANDLE;
        }
        if (layout != null) {
            layout.free();
            layout = null;
        }
        if (identityView != null) {
            memFree(identityView);
            identityView = null;
        }
        if (identityProj != null) {
            memFree(identityProj);
            identityProj = null;
        }
        bgfx_shutdown();
        if (screenShot != null) {
            screenShot.dispose();   // free the callback structs once bgfx no longer references them
            screenShot = null;
        }
    }

    public void setSize(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        // Written on the render thread; the lock publishes the new size to EDT readers (getWidth/Height,
        // used by HiDPI mouse->pixel scaling for picking/datatips) so they don't see a torn dimension.
        synchronized (dimension) {
            if (w != dimension.width || h != dimension.height) {
                dimension.width = w;
                dimension.height = h;
                sizeDirty = true;
            }
        }
    }

    public DrawingTools getDrawingTools() {
        return drawingTools;
    }

    // ---- package-private access for BgfxShapeDrawer -------------------------

    int viewId() {
        return VIEW_ID;
    }

    short program() {
        return program;
    }

    short uniformColor() {
        return uColor;
    }

    short uniformParams() {
        return uParams;
    }

    BGFXVertexLayout layout() {
        return layout;
    }

    short texProgram() {
        return texProgram;
    }

    BGFXVertexLayout texLayout() {
        return texLayout;
    }

    short uniformTexColor() {
        return sTexColor;
    }

    boolean homogeneousDepth() {
        return homogeneousDepth;
    }

    void setClearRgba(int rgba) {
        this.clearRgba = rgba;
    }

    // ---- shader loading -----------------------------------------------------

    private static short loadShader(String name) {
        String path = "/shaders/" + rendererDir() + "/" + name + ".bin";
        try (InputStream in = BgfxCanvas.class.getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[scirenderer.bgfx] shader resource not found on classpath: " + path);
                return INVALID_HANDLE;
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = memAlloc(bytes.length);
            try {
                buf.put(bytes).flip();
                return bgfx_create_shader(bgfx_copy(buf));
            } finally {
                memFree(buf);   // bgfx_copy took its own copy; free the staging buffer on every path
            }
        } catch (Exception e) {
            // A present-but-unloadable shader (corrupt/truncated .bin, I/O failure) is a real error,
            // distinct from the not-found case above; log it rather than silently degrade to clear-only.
            System.err.println("[scirenderer.bgfx] failed to load shader " + path + ": " + e);
            return INVALID_HANDLE;
        }
    }

    private static String rendererDir() {
        switch (bgfx_get_renderer_type()) {
            case BGFX_RENDERER_TYPE_METAL:      return "metal";
            case BGFX_RENDERER_TYPE_VULKAN:     return "spirv";
            case BGFX_RENDERER_TYPE_DIRECT3D11:
            case BGFX_RENDERER_TYPE_DIRECT3D12: return "dx11";
            default:                            return "glsl";
        }
    }

    // ---- Canvas interface ---------------------------------------------------

    @Override
    public void setMainDrawer(Drawer mainDrawer) {
        this.mainDrawer = mainDrawer;
    }

    @Override
    public Drawer getMainDrawer() {
        return mainDrawer;
    }

    @Override
    public BgfxRendererManager getRendererManager() {
        return rendererManager;
    }

    @Override
    public BgfxBuffersManager getBuffersManager() {
        return buffersManager;
    }

    @Override
    public PickingManager getPickingManager() {
        return PICKING_MANAGER;
    }

    @Override
    public BgfxTextureManager getTextureManager() {
        return textureManager;
    }

    @Override
    public int getWidth() {
        synchronized (dimension) {
            return dimension.width;
        }
    }

    @Override
    public int getHeight() {
        synchronized (dimension) {
            return dimension.height;
        }
    }

    @Override
    public Dimension getDimension() {
        // A snapshot, not the live mutable field, so a caller on another thread can't observe a torn
        // (width-new / height-old) size while the render thread is mid-resize.
        synchronized (dimension) {
            return new Dimension(dimension.width, dimension.height);
        }
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
        wakeRedraw();
    }

    @Override
    public void redrawAndWait() {
        // The model is mutated by the caller before this returns; signalling here publishes it to
        // the render thread (lock pairing with awaitRedraw). We deliberately do not block the
        // caller on frame completion — the model-construction thread issues many of these during
        // figure build, and blocking each one serializes construction against the render thread.
        wakeRedraw();
    }

    /** Request a redraw and unblock the render thread's {@link #awaitRedraw(long)} promptly. */
    public void wakeRedraw() {
        synchronized (redrawLock) {
            needsRedraw = true;
            redrawLock.notifyAll();
        }
    }

    @Override
    public void waitImage() {
    }

    @Override
    public void destroy() {
        // bgfx teardown must happen on the render thread; the driver calls shutdownBgfx().
    }
}
