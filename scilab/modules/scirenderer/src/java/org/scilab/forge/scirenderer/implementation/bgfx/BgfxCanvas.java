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
 * <p>Each draw bakes the scene-to-clip matrix into the model transform (view = proj = identity), so
 * one generic vertex-color shader covers every geometry. Text/sprites/textures are not yet drawn.
 */
public final class BgfxCanvas implements Canvas {

    static final short INVALID_HANDLE = (short) 0xffff;
    private static final int VIEW_ID = 0;
    private static final int DEFAULT_CLEAR_RGBA = 0x000000ff;

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
    private short uColor = INVALID_HANDLE;
    private short uParams = INVALID_HANDLE;
    private BGFXVertexLayout layout;
    private boolean homogeneousDepth;
    private FloatBuffer identityView;
    private FloatBuffer identityProj;
    private int clearRgba = DEFAULT_CLEAR_RGBA;

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
            if (!bgfx_init(init)) {
                System.err.println("[scirenderer.bgfx] bgfx_init failed (nwh=" + nwh + ")");
                return false;
            }
        }
        homogeneousDepth = bgfx_get_caps().homogeneousDepth();
        identityView = memAllocFloat(16).put(BgfxMat.identity()).flip();
        identityProj = memAllocFloat(16).put(BgfxMat.identity()).flip();

        buildProgram();

        initialised = true;
        System.out.println("[scirenderer.bgfx] canvas ready: "
                           + bgfx_get_renderer_name(bgfx_get_renderer_type()) + "  "
                           + dimension.width + "x" + dimension.height
                           + (program == INVALID_HANDLE ? "  (scene shaders missing -> clear only)" : ""));
        return true;
    }

    private void buildProgram() {
        short vsh = loadShader("vs_scene");
        short fsh = loadShader("fs_scene");
        if (vsh == INVALID_HANDLE || fsh == INVALID_HANDLE) {
            return;
        }
        program = bgfx_create_program(vsh, fsh, true);

        layout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(layout, bgfx_get_renderer_type());
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(layout);

        uColor = bgfx_create_uniform("u_color", BGFX_UNIFORM_TYPE_VEC4, 1);
        uParams = bgfx_create_uniform("u_params", BGFX_UNIFORM_TYPE_VEC4, 1);
    }

    /** Render one frame: clear, then let the shared DrawerVisitor submit the scene. */
    public void renderFrame() {
        if (!initialised) {
            return;
        }
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
                t.printStackTrace();
            }
        }
        bgfx_frame(false);
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
    }

    public void setSize(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (w != dimension.width || h != dimension.height) {
            dimension.width = w;
            dimension.height = h;
            sizeDirty = true;
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
                return INVALID_HANDLE;
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = memAlloc(bytes.length).put(bytes);
            buf.flip();
            short h = bgfx_create_shader(bgfx_copy(buf));
            memFree(buf);
            return h;
        } catch (Exception e) {
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
        // The gui-side render thread redraws continuously; nothing to schedule here.
    }

    @Override
    public void redrawAndWait() {
    }

    @Override
    public void waitImage() {
    }

    @Override
    public void destroy() {
        // bgfx teardown must happen on the render thread; the driver calls shutdownBgfx().
    }
}
