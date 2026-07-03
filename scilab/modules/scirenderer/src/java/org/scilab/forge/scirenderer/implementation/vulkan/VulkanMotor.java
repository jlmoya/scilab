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

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;
import org.scilab.forge.scirenderer.texture.Texture;

/**
 * The Vulkan backend's rendering engine — the counterpart of g2d's {@code Motor3D}, but instead of
 * CPU-rasterising it packs the scene into two flat clip-space arenas (filled triangles, line
 * segments) that a {@link VulkanSceneRenderer} uploads and draws on the GPU with a real z-buffer.
 *
 * <p>Model: during {@code mainDrawer.draw(drawingTools)} the DrawerVisitor issues many
 * {@link #draw} calls; each reads the current scene→GL-clip matrix
 * ({@code TransformationManager.getTransformation().getMatrix()}) and multiplies every vertex by it
 * on the CPU, appending clip-space {@code (x,y,z,w)} + {@code (r,g,b,a)} to the arena. So the whole
 * frame shares one identity model transform on the GPU (only the fixed GL-clip→Vulkan z-remap is
 * applied there). {@link #flush} then streams both arenas to the renderer in one pass. Depth sorting
 * is unnecessary — the GPU z-buffer resolves occlusion.
 */
public class VulkanMotor {

    private final VulkanCanvas canvas;
    private VulkanSceneRenderer renderer = VulkanSceneRenderer.NOOP;

    private final FloatArena backdrop = new FloatArena();
    private final FloatArena triangles = new FloatArena();
    private final FloatArena lines = new FloatArena();

    private float clearR = 1f, clearG = 1f, clearB = 1f, clearA = 1f;

    private final double[] m = new double[16];
    private final float[] clip = new float[4];

    VulkanMotor(VulkanCanvas canvas) {
        this.canvas = canvas;
    }

    void setRenderer(VulkanSceneRenderer renderer) {
        this.renderer = (renderer == null) ? VulkanSceneRenderer.NOOP : renderer;
    }

    // ---- clear ----

    public void reset(Color color) {
        if (color != null) {
            clearR = color.getRedAsFloat();
            clearG = color.getGreenAsFloat();
            clearB = color.getBlueAsFloat();
            clearA = color.getAlphaAsFloat();
        }
        backdrop.reset();
        triangles.reset();
        lines.reset();
    }

    public void reset(java.awt.Color color) {
        if (color != null) {
            clearR = color.getRed() / 255f;
            clearG = color.getGreen() / 255f;
            clearB = color.getBlue() / 255f;
            clearA = color.getAlpha() / 255f;
        }
        backdrop.reset();
        triangles.reset();
        lines.reset();
    }

    public void clearDepth() {
        // Depth is cleared once per frame by the renderer; nothing to accumulate here.
    }

    // ---- geometry ----

    public void draw(DrawingTools drawingTools, Geometry geometry, Appearance appearance) {
        final ElementsBuffer vertices = geometry.getVertices();
        if (vertices == null || vertices.getData() == null) {
            return;
        }
        final FloatBuffer vb = vertices.getData();
        final int vStride = vertices.getElementsSize();
        final int vertexCount = vb.capacity() / vStride;

        final ElementsBuffer colorBuffer = geometry.getColors();
        final FloatBuffer cb = (colorBuffer != null) ? colorBuffer.getData() : null;
        final int cStride = (colorBuffer != null) ? colorBuffer.getElementsSize() : 0;

        System.arraycopy(drawingTools.getTransformationManager().getTransformation().getMatrix(), 0, m, 0, 16);

        // fills — classified into scene vs backdrop (the proven anti-occlusion split):
        //  * colormap-textured (surf/Fac3d): resolve per-vertex colours from the strip on the CPU
        //    (texcoord.x = colormap position) -> depth-tested scene triangles
        //  * per-vertex coloured -> depth-tested scene triangles
        //  * flat-coloured with neither (== the axes background cube, FaceCullingMode.BOTH):
        //    backdrop — no depth, drawn first — otherwise its near faces occlude the whole scene
        if (geometry.getFillDrawingMode() != Geometry.FillDrawingMode.NONE) {
            FloatBuffer colorSource = cb;
            int colorStride = cStride;
            FloatArena target;
            final float[] fill = rgba(appearance == null ? null : appearance.getFillColor());
            final FloatBuffer resolved = resolveColormapColors(geometry, appearance, vertexCount);
            if (resolved != null) {
                colorSource = resolved;
                colorStride = 4;
                target = triangles;
            } else if (cb != null) {
                target = triangles;
            } else {
                target = backdrop;
            }
            final IndicesBuffer indices = geometry.getIndices();
            if (indices != null && indices.getData() != null) {
                emitTriangles(target, indices.getData(), geometry.getFillDrawingMode(), vb, vStride, colorSource, colorStride, fill);
            } else {
                emitTrianglesSequential(target, vertexCount, geometry.getFillDrawingMode(), vb, vStride, colorSource, colorStride, fill);
            }
        }

        // lines: explicit wire edges, or the geometry itself is a polyline
        final float[] lineColor = rgba(appearance == null ? null : appearance.getLineColor());
        final IndicesBuffer wire = geometry.getWireIndices();
        if (wire != null && wire.getData() != null) {
            emitSegments(wire.getData(), vb, vStride, cb, cStride, lineColor);
        } else if (geometry.getLineDrawingMode() != Geometry.LineDrawingMode.NONE) {
            emitPolyline(vertexCount, geometry.getLineDrawingMode(), vb, vStride, cb, cStride, lineColor);
        }
    }

    private void emitTriangles(FloatArena target, IntBuffer ib, Geometry.FillDrawingMode mode, FloatBuffer vb, int vs,
                               FloatBuffer cb, int cs, float[] fill) {
        final int n = ib.capacity();
        switch (mode) {
            case TRIANGLE_STRIP:
                for (int i = 2; i < n; i++) {
                    // keep winding consistent across the strip
                    if ((i & 1) == 0) {
                        tri(target, ib.get(i - 2), ib.get(i - 1), ib.get(i), vb, vs, cb, cs, fill);
                    } else {
                        tri(target, ib.get(i - 1), ib.get(i - 2), ib.get(i), vb, vs, cb, cs, fill);
                    }
                }
                break;
            case TRIANGLE_FAN:
                for (int i = 2; i < n; i++) {
                    tri(target, ib.get(0), ib.get(i - 1), ib.get(i), vb, vs, cb, cs, fill);
                }
                break;
            case TRIANGLES:
            default:
                for (int i = 0; i + 2 < n; i += 3) {
                    tri(target, ib.get(i), ib.get(i + 1), ib.get(i + 2), vb, vs, cb, cs, fill);
                }
                break;
        }
    }

    private void emitTrianglesSequential(FloatArena target, int count, Geometry.FillDrawingMode mode, FloatBuffer vb, int vs,
                                         FloatBuffer cb, int cs, float[] fill) {
        switch (mode) {
            case TRIANGLE_STRIP:
                for (int i = 2; i < count; i++) {
                    if ((i & 1) == 0) {
                        tri(target, i - 2, i - 1, i, vb, vs, cb, cs, fill);
                    } else {
                        tri(target, i - 1, i - 2, i, vb, vs, cb, cs, fill);
                    }
                }
                break;
            case TRIANGLE_FAN:
                for (int i = 2; i < count; i++) {
                    tri(target, 0, i - 1, i, vb, vs, cb, cs, fill);
                }
                break;
            case TRIANGLES:
            default:
                for (int i = 0; i + 2 < count; i += 3) {
                    tri(target, i, i + 1, i + 2, vb, vs, cb, cs, fill);
                }
                break;
        }
    }

    /**
     * If this geometry is colormap-textured (surf/Fac3d: texture coordinates whose x is the
     * position in an (N+2)x1 colormap strip), resolve each vertex's colour from the strip on the
     * CPU and return them (4 floats/vertex); otherwise null. Real GPU texturing (needed for image
     * plots and sprites) is a later slice — for a 1-D colormap, per-vertex resolution + Gouraud
     * interpolation is visually equivalent.
     */
    private FloatBuffer resolveColormapColors(Geometry geometry, Appearance appearance, int vertexCount) {
        final Texture texture = (appearance == null) ? null : appearance.getTexture();
        final ElementsBuffer texCoords = geometry.getTextureCoordinates();
        if (texture == null || texCoords == null || texCoords.getData() == null || !texture.isValid()) {
            return null;
        }
        try {
            final BufferedImage strip = texture.getDataProvider().getImage();
            if (strip == null) {
                return null;
            }
            final int w = strip.getWidth();
            final FloatBuffer tc = texCoords.getData();
            final int ts = texCoords.getElementsSize();
            final float[] out = new float[vertexCount * 4];
            for (int i = 0; i < vertexCount; i++) {
                float u = tc.get(i * ts);
                int px = (int) (u * w);
                if (px < 0) {
                    px = 0;
                } else if (px >= w) {
                    px = w - 1;
                }
                final int argb = strip.getRGB(px, 0);
                out[i * 4] = ((argb >> 16) & 0xff) / 255f;
                out[i * 4 + 1] = ((argb >> 8) & 0xff) / 255f;
                out[i * 4 + 2] = (argb & 0xff) / 255f;
                out[i * 4 + 3] = ((argb >>> 24) & 0xff) / 255f;
            }
            return FloatBuffer.wrap(out);
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    private String lastError;

    private void logOnce(Throwable t) {
        String key = String.valueOf(t);
        if (!key.equals(lastError)) {
            lastError = key;
            System.err.println("[vulkan.motor] colormap resolve failed (logged once): " + t);
        }
    }

    private void emitSegments(IntBuffer ib, FloatBuffer vb, int vs, FloatBuffer cb, int cs, float[] lineColor) {
        final int n = ib.capacity();
        for (int i = 0; i + 1 < n; i += 2) {
            seg(ib.get(i), ib.get(i + 1), vb, vs, cb, cs, lineColor);
        }
    }

    private void emitPolyline(int count, Geometry.LineDrawingMode mode, FloatBuffer vb, int vs,
                              FloatBuffer cb, int cs, float[] lineColor) {
        switch (mode) {
            case SEGMENTS:
                for (int i = 0; i + 1 < count; i += 2) {
                    seg(i, i + 1, vb, vs, cb, cs, lineColor);
                }
                break;
            case SEGMENTS_LOOP:
                for (int i = 0; i < count; i++) {
                    seg(i, (i + 1) % count, vb, vs, cb, cs, lineColor);
                }
                break;
            case SEGMENTS_STRIP:
            default:
                for (int i = 0; i + 1 < count; i++) {
                    seg(i, i + 1, vb, vs, cb, cs, lineColor);
                }
                break;
        }
    }

    private void tri(FloatArena target, int a, int b, int c, FloatBuffer vb, int vs, FloatBuffer cb, int cs, float[] fill) {
        vertex(target, a, vb, vs, cb, cs, fill);
        vertex(target, b, vb, vs, cb, cs, fill);
        vertex(target, c, vb, vs, cb, cs, fill);
    }

    private void seg(int a, int b, FloatBuffer vb, int vs, FloatBuffer cb, int cs, float[] lineColor) {
        vertex(lines, a, vb, vs, cb, cs, lineColor);
        vertex(lines, b, vb, vs, cb, cs, lineColor);
    }

    /** Transform vertex {@code i} to clip space and append it (clip.xyzw + rgba) to {@code arena}. */
    private void vertex(FloatArena arena, int i, FloatBuffer vb, int vs, FloatBuffer cb, int cs, float[] flat) {
        final float x = vb.get(i * vs);
        final float y = vb.get(i * vs + 1);
        final float z = vs > 2 ? vb.get(i * vs + 2) : 0f;
        final float w = vs > 3 ? vb.get(i * vs + 3) : 1f;
        for (int r = 0; r < 4; r++) {
            clip[r] = (float) (m[r] * x + m[4 + r] * y + m[8 + r] * z + m[12 + r] * w);
        }
        float cr = flat[0], cg = flat[1], cbb = flat[2], ca = flat[3];
        if (cb != null && (i + 1) * cs <= cb.capacity()) {
            cr = cb.get(i * cs);
            cg = cs > 1 ? cb.get(i * cs + 1) : cr;
            cbb = cs > 2 ? cb.get(i * cs + 2) : cr;
            ca = cs > 3 ? cb.get(i * cs + 3) : 1f;
        }
        arena.vertex(clip[0], clip[1], clip[2], clip[3], cr, cg, cbb, ca);
    }

    private static float[] rgba(Color color) {
        if (color == null) {
            return new float[] {0.8f, 0.8f, 0.8f, 1f};
        }
        return new float[] {color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), color.getAlphaAsFloat()};
    }

    // ---- present ----

    private static final boolean DEBUG = Boolean.getBoolean("scilab.renderer.vulkan.debug");

    public void flush() {
        if (DEBUG) {
            System.out.println("[vulkan.motor] flush: " + (backdrop.count / 8) + " backdrop verts, "
                               + (triangles.count / 8) + " tri verts, "
                               + (lines.count / 8) + " line verts, canvas " + canvas.getWidth() + "x" + canvas.getHeight());
        }
        renderer.resize(canvas.getWidth(), canvas.getHeight());
        renderer.beginFrame(clearR, clearG, clearB, clearA);
        if (backdrop.count > 0) {
            renderer.backdrop(backdrop.data, backdrop.count);
        }
        if (triangles.count > 0) {
            renderer.triangles(triangles.data, triangles.count);
        }
        if (lines.count > 0) {
            renderer.lines(lines.data, lines.count);
        }
        renderer.endFrame();
    }

    public void clean() {
        renderer.dispose();
    }

    /** Growable float array for one arena (8 floats per vertex). */
    private static final class FloatArena {
        float[] data = new float[8192];
        int count;

        void reset() {
            count = 0;
        }

        void vertex(float x, float y, float z, float w, float r, float g, float b, float a) {
            if (count + 8 > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, count + 8));
            }
            data[count] = x;
            data[count + 1] = y;
            data[count + 2] = z;
            data[count + 3] = w;
            data[count + 4] = r;
            data[count + 5] = g;
            data[count + 6] = b;
            data[count + 7] = a;
            count += 8;
        }
    }
}
