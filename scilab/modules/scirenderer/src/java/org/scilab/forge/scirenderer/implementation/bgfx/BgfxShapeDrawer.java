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

import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;
import org.scilab.forge.scirenderer.tranformations.TransformationManager;

import org.lwjgl.bgfx.BGFXTransientIndexBuffer;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Translates a scirenderer {@link Geometry} into bgfx draw submits (immediate mode).
 *
 * <p>One interleaved transient vertex buffer (pos {@code vec4} + color {@code vec4}) is filled per
 * geometry, then the fill (triangles) and the wire (lines) are submitted with the z-remapped
 * scene-to-clip matrix as the model transform. Per-vertex colors are used when present; otherwise a
 * flat {@code u_color} (fill or line color) is applied — selected by {@code u_params.x}.
 */
final class BgfxShapeDrawer {

    private static final long STATE_BASE =
          BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_WRITE_Z | BGFX_STATE_DEPTH_TEST_LESS
        | BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA);

    private BgfxShapeDrawer() { }

    static void draw(BgfxDrawingTools dt, Geometry geom, Appearance app) {
        final BgfxCanvas canvas = dt.getCanvas();
        if (canvas.program() == BgfxCanvas.INVALID_HANDLE) {
            return;
        }
        final ElementsBuffer vertsBuf = geom.getVertices();
        if (vertsBuf == null) {
            return;
        }
        final int count = vertsBuf.getSize();
        if (count == 0) {
            return;
        }
        final FloatBuffer verts = vertsBuf.getData();
        if (verts == null) {
            return;
        }
        final FloatBuffer colors = geom.getColors() != null ? geom.getColors().getData() : null;

        final TransformationManager tm = dt.getTransformationManager();
        final double[] sceneToClip = tm.isUsingSceneCoordinate()
                                     ? tm.getTransformation().getMatrix()
                                     : tm.getWindowTransformation().getMatrix();
        final float[] mvp = BgfxMat.toClip(sceneToClip, canvas.homogeneousDepth());

        try (MemoryStack stack = stackPush()) {
            final BGFXVertexLayout layout = canvas.layout();
            if (bgfx_get_avail_transient_vertex_buffer(count, layout) < count) {
                return;
            }
            final BGFXTransientVertexBuffer tvb = BGFXTransientVertexBuffer.malloc(stack);
            bgfx_alloc_transient_vertex_buffer(tvb, count, layout);
            final ByteBuffer vb = tvb.data();
            final int colorLimit = colors != null ? colors.limit() : 0;
            for (int i = 0; i < count; i++) {
                final int b = i * 4;
                vb.putFloat(verts.get(b)).putFloat(verts.get(b + 1)).putFloat(verts.get(b + 2)).putFloat(verts.get(b + 3));
                if (colors != null && (b + 3) < colorLimit) {
                    vb.putFloat(colors.get(b)).putFloat(colors.get(b + 1)).putFloat(colors.get(b + 2)).putFloat(colors.get(b + 3));
                } else {
                    vb.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(1f);
                }
            }

            // Fill (triangles).
            if (geom.getFillDrawingMode() != Geometry.FillDrawingMode.NONE) {
                final int[] idx = fillIndices(geom, count);
                final long pt = geom.getFillDrawingMode() == Geometry.FillDrawingMode.TRIANGLE_STRIP
                                ? BGFX_STATE_PT_TRISTRIP : 0L;
                submit(stack, canvas, tvb, count, idx, pt, colors != null, app.getFillColor(),
                       mvp, cullFlag(geom.getFaceCullingMode()));
            }

            // Wire (lines): line color overrides per-vertex; else per-vertex; else skip.
            if (geom.getLineDrawingMode() != Geometry.LineDrawingMode.NONE) {
                final Color lineColor = app.getLineColor();
                final boolean useVtx = lineColor == null && colors != null;
                if (lineColor != null || colors != null) {
                    final int[] idx = lineIndices(geom, count);
                    submit(stack, canvas, tvb, count, idx, linePt(geom.getLineDrawingMode()),
                           useVtx, lineColor, mvp, 0L);
                }
            }
        }
    }

    private static void submit(MemoryStack stack, BgfxCanvas canvas, BGFXTransientVertexBuffer tvb,
                               int vertexCount, int[] idx, long primType, boolean useVertexColor,
                               Color flatColor, float[] mvp, long cull) {
        final FloatBuffer mtx = stack.mallocFloat(16).put(mvp);
        mtx.flip();
        bgfx_set_transform(mtx);
        bgfx_set_transient_vertex_buffer(0, tvb, 0, vertexCount);

        if (idx != null) {
            if (bgfx_get_avail_transient_index_buffer(idx.length, true) < idx.length) {
                return;
            }
            final BGFXTransientIndexBuffer tib = BGFXTransientIndexBuffer.malloc(stack);
            bgfx_alloc_transient_index_buffer(tib, idx.length, true);
            final IntBuffer ib = tib.data().asIntBuffer();
            ib.put(idx);
            bgfx_set_transient_index_buffer(tib, 0, idx.length);
        }

        final FloatBuffer params = stack.mallocFloat(4)
                .put(useVertexColor ? 1f : 0f).put(0f).put(0f).put(0f);
        params.flip();
        bgfx_set_uniform(canvas.uniformParams(), params, 1);

        final FloatBuffer col = stack.mallocFloat(4);
        if (flatColor != null) {
            col.put(flatColor.getRedAsFloat()).put(flatColor.getGreenAsFloat())
               .put(flatColor.getBlueAsFloat()).put(flatColor.getAlphaAsFloat());
        } else {
            col.put(0.8f).put(0.8f).put(0.8f).put(1f);
        }
        col.flip();
        bgfx_set_uniform(canvas.uniformColor(), col, 1);

        bgfx_set_state(STATE_BASE | primType | cull, 0);
        bgfx_submit(canvas.viewId(), canvas.program(), 0, BGFX_DISCARD_ALL);
    }

    /** Triangle indices, or {@code null} for a sequential (non-indexed) draw. */
    private static int[] fillIndices(Geometry geom, int count) {
        final int[] base = toArray(geom.getIndices());
        if (geom.getFillDrawingMode() == Geometry.FillDrawingMode.TRIANGLE_FAN) {
            return fanToTriangles(base != null ? base : sequence(count));
        }
        return base; // TRIANGLES / TRIANGLE_STRIP: indices as-is, or null = sequential
    }

    /** Line indices, or {@code null} for a sequential draw. Closes SEGMENTS_LOOP. */
    private static int[] lineIndices(Geometry geom, int count) {
        final int[] base = toArray(geom.getWireIndices());
        if (geom.getLineDrawingMode() == Geometry.LineDrawingMode.SEGMENTS_LOOP) {
            final int[] src = base != null ? base : sequence(count);
            if (src.length == 0) {
                return src;
            }
            final int[] closed = Arrays.copyOf(src, src.length + 1);
            closed[src.length] = src[0];
            return closed;
        }
        return base;
    }

    private static long linePt(Geometry.LineDrawingMode mode) {
        switch (mode) {
            case SEGMENTS_STRIP:
            case SEGMENTS_LOOP:
                return BGFX_STATE_PT_LINESTRIP;
            case SEGMENTS:
            default:
                return BGFX_STATE_PT_LINES;
        }
    }

    private static long cullFlag(Geometry.FaceCullingMode mode) {
        switch (mode) {
            case CW:
                return BGFX_STATE_CULL_CW;
            case CCW:
                return BGFX_STATE_CULL_CCW;
            case BOTH:
            default:
                return 0L;
        }
    }

    private static int[] fanToTriangles(int[] fan) {
        if (fan.length < 3) {
            return new int[0];
        }
        final int triangles = fan.length - 2;
        final int[] out = new int[triangles * 3];
        int o = 0;
        for (int i = 1; i <= triangles; i++) {
            out[o++] = fan[0];
            out[o++] = fan[i];
            out[o++] = fan[i + 1];
        }
        return out;
    }

    private static int[] toArray(IndicesBuffer ib) {
        if (ib == null) {
            return null;
        }
        final IntBuffer data = ib.getData();
        if (data == null) {
            return null;
        }
        final int n = data.limit();
        final int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = data.get(i);
        }
        return a;
    }

    private static int[] sequence(int n) {
        final int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        return a;
    }
}
