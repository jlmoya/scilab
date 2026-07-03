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
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.scilab.forge.scirenderer.DrawingTools;
import org.scilab.forge.scirenderer.buffers.ElementsBuffer;
import org.scilab.forge.scirenderer.buffers.IndicesBuffer;
import org.scilab.forge.scirenderer.implementation.vulkan.texture.VulkanTexture;
import org.scilab.forge.scirenderer.shapes.appearance.Appearance;
import org.scilab.forge.scirenderer.shapes.appearance.Color;
import org.scilab.forge.scirenderer.shapes.geometry.Geometry;
import org.scilab.forge.scirenderer.texture.AnchorPosition;
import org.scilab.forge.scirenderer.texture.Texture;
import org.scilab.forge.scirenderer.tranformations.Transformation;
import org.scilab.forge.scirenderer.tranformations.TransformationManager;
import org.scilab.forge.scirenderer.tranformations.Vector3d;

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

    // sprites staged during the traversal, forwarded inside beginFrame/endFrame at flush
    private final List<long[]> spriteRefs = new ArrayList<long[]>();      // {textureHandle}
    private final List<float[]> spriteQuads = new ArrayList<float[]>();   // 6 verts x (x,y,u,v)

    // textured scene quads (image plots), staged like sprites but depth-tested
    private final List<long[]> imageRefs = new ArrayList<long[]>();       // {textureHandle}
    private final List<float[]> imageQuads = new ArrayList<float[]>();    // 6 verts x (clip xyzw, u, v)

    // texture handles queued for GPU disposal (any thread) — drained on the render thread at flush
    private final ConcurrentLinkedQueue<Long> disposedTextures = new ConcurrentLinkedQueue<Long>();

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
        spriteRefs.clear();
        spriteQuads.clear();
        imageRefs.clear();
        imageQuads.clear();
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
        spriteRefs.clear();
        spriteQuads.clear();
        imageRefs.clear();
        imageQuads.clear();
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

    // ---- image plots (single-arg draw(Texture): Matplot) ----

    /**
     * Draw a texture on the XY plane in MODEL coordinates — the rectangle
     * {@code (0,0)-(width,height)}, positioned in data space by the Matplot scale/translate
     * already on the modelView stack. The corners go through the CURRENT scene->clip matrix
     * (like geometry, unlike screen-space sprites) and the quad is depth-tested.
     */
    public void drawImage(DrawingTools drawingTools, Texture texture) {
        final long handle = ensureUploaded(texture);
        if (handle == 0) {
            return;
        }
        final Dimension texSize = texture.getDataProvider().getTextureSize();
        if (texSize == null) {
            return;
        }
        final float tw = (float) texSize.getWidth();
        final float th = (float) texSize.getHeight();
        System.arraycopy(drawingTools.getTransformationManager().getTransformation().getMatrix(), 0, m, 0, 16);

        // model-space corners; v=0 at the TOP edge (y=th) — image row 0 is the matrix's first row
        final float[][] corners = {
            {0, 0}, {tw, 0}, {tw, th},
            {0, 0}, {tw, th}, {0, th},
        };
        final float[][] uv = {
            {0, 1}, {1, 1}, {1, 0},
            {0, 1}, {1, 0}, {0, 0},
        };
        final float[] quad = new float[36];
        for (int v = 0; v < 6; v++) {
            final float x = corners[v][0];
            final float y = corners[v][1];
            for (int r = 0; r < 4; r++) {
                quad[v * 6 + r] = (float) (m[r] * x + m[4 + r] * y + m[12 + r]);
            }
            quad[v * 6 + 4] = uv[v][0];
            quad[v * 6 + 5] = uv[v][1];
        }
        imageRefs.add(new long[] {handle});
        imageQuads.add(quad);
    }

    // ---- sprites (text labels, tick numbers, marks) ----

    /**
     * Draw a texture as screen-aligned sprites at each position (mirrors the JOGL texture
     * manager): project the anchor to WINDOW PIXELS via {@code canvasProjection} (positions are
     * already window coords when not in scene-coordinate mode), offset the quad's lower-left
     * corner from the anchor per {@link AnchorPosition}, rotate about the anchor (degrees, CCW —
     * the rotation applies to the anchor offset too), then convert pixels to GL-convention NDC.
     */
    public void drawSprite(DrawingTools drawingTools, Texture texture, AnchorPosition anchor,
                           ElementsBuffer positions, int offset, int stride, double rotationAngle) {
        if (positions == null || positions.getData() == null) {
            return;
        }
        final long handle = ensureUploaded(texture);
        if (handle == 0) {
            return;
        }
        final FloatBuffer pb = positions.getData();
        final int ps = positions.getElementsSize();
        final int count = pb.capacity() / ps;
        final int step = Math.max(1, stride);
        for (int i = Math.max(0, offset); i < count; i += step) {
            emitSprite(drawingTools, texture, handle, anchor,
                       new Vector3d(pb.get(i * ps), pb.get(i * ps + 1), ps > 2 ? pb.get(i * ps + 2) : 0),
                       rotationAngle);
        }
    }

    /** Single-position variant. */
    public void drawSprite(DrawingTools drawingTools, Texture texture, AnchorPosition anchor,
                           Vector3d position, double rotationAngle) {
        final long handle = ensureUploaded(texture);
        if (handle != 0) {
            emitSprite(drawingTools, texture, handle, anchor, position, rotationAngle);
        }
    }

    private void emitSprite(DrawingTools drawingTools, Texture texture, long handle,
                            AnchorPosition anchor, Vector3d position, double rotationAngle) {
        final TransformationManager tm = drawingTools.getTransformationManager();
        final Vector3d projected;
        if (tm.isUsingSceneCoordinate()) {
            Transformation canvasProjection = tm.getCanvasProjection();
            projected = canvasProjection.project(position);
        } else {
            projected = position;
        }
        final Dimension texSize = texture.getDataProvider().getTextureSize();
        if (texSize == null) {
            return;
        }
        final double tw = texSize.getWidth();
        final double th = texSize.getHeight();
        final double dx = anchorDeltaX(anchor, tw);
        final double dy = anchorDeltaY(anchor, th);
        final double px = Math.round(projected.getX());
        final double py = Math.round(projected.getY());

        // quad corners relative to the anchor (pixels, y-up), rotation applied about the anchor
        final double[][] local = {
            {dx, dy}, {dx + tw, dy}, {dx + tw, dy + th},
            {dx, dy}, {dx + tw, dy + th}, {dx, dy + th},
        };
        // texture v: image row 0 = glyph top -> v=0 at the quad's TOP edge (y-up: top = dy + th)
        final float[][] uv = {
            {0, 1}, {1, 1}, {1, 0},
            {0, 1}, {1, 0}, {0, 0},
        };
        final double rad = Math.toRadians(rotationAngle);
        final double cos = Math.cos(rad);
        final double sin = Math.sin(rad);
        final int w = canvas.getWidth();
        final int h = canvas.getHeight();
        final float[] quad = new float[24];
        for (int v = 0; v < 6; v++) {
            final double lx = local[v][0];
            final double ly = local[v][1];
            final double rx = px + lx * cos - ly * sin;
            final double ry = py + lx * sin + ly * cos;
            quad[v * 4] = (float) (rx / w * 2.0 - 1.0);
            quad[v * 4 + 1] = (float) (ry / h * 2.0 - 1.0);
            quad[v * 4 + 2] = uv[v][0];
            quad[v * 4 + 3] = uv[v][1];
        }
        if (DEBUG && spriteQuads.size() < 3) {
            System.out.println("[vulkan.motor] sprite tex=" + handle + " size=" + tw + "x" + th
                               + " anchor=" + anchor + " projected=(" + px + "," + py + ")"
                               + " ndc0=(" + quad[0] + "," + quad[1] + ")");
        }
        spriteRefs.add(new long[] {handle});
        spriteQuads.add(quad);
    }

    /** X offset of the quad's lower-left corner from the anchor point (JOGL semantics). */
    private static double anchorDeltaX(AnchorPosition anchor, double w) {
        switch (anchor) {
            case LEFT:
            case LOWER_LEFT:
            case UPPER_LEFT:
                return 0;
            case UP:
            case CENTER:
            case DOWN:
                return -w / 2.0;
            case RIGHT:
            case LOWER_RIGHT:
            case UPPER_RIGHT:
                return -w;
            default:
                return 0;
        }
    }

    /** Y offset of the quad's lower-left corner from the anchor point (JOGL semantics, y-up). */
    private static double anchorDeltaY(AnchorPosition anchor, double h) {
        switch (anchor) {
            case UPPER_LEFT:
            case UP:
            case UPPER_RIGHT:
                return -h;
            case LEFT:
            case CENTER:
            case RIGHT:
                return -h / 2.0;
            case LOWER_LEFT:
            case DOWN:
            case LOWER_RIGHT:
                return 0;
            default:
                return 0;
        }
    }

    /**
     * Lazily upload a texture's current data to the GPU (RGBA8, via the provider's format-proof
     * {@code getImage()}). Re-uploads when the provider reports new data ({@code dataUpdated}
     * flips {@code upToDate}); the stale handle is destroyed first.
     */
    private long ensureUploaded(Texture texture) {
        if (!(texture instanceof VulkanTexture) || !texture.isValid()) {
            return 0;
        }
        final VulkanTexture vt = (VulkanTexture) texture;
        if (vt.isUpToDate() && vt.getGpuHandle() != 0) {
            return vt.getGpuHandle();
        }
        try {
            final ByteBuffer rgba = textureToRgba(vt);
            if (rgba == null) {
                return 0;
            }
            if (vt.getGpuHandle() != 0) {
                renderer.destroyTexture(vt.getGpuHandle());
            }
            final Dimension size = vt.getDataProvider().getTextureSize();
            final int w = size.width;
            final int h = size.height;
            if (DEBUG) {
                int opaque = 0;
                for (int i = 3; i < rgba.limit(); i += 4) {
                    if (rgba.get(i) != 0) {
                        opaque++;
                    }
                }
                System.out.println("[vulkan.motor] upload tex " + w + "x" + h + " opaquePx=" + opaque + "/" + (w * h));
            }
            final boolean linear = vt.getMagnificationFilter() == Texture.Filter.LINEAR;
            final long handle = renderer.uploadTexture(w, h, rgba, linear);
            if (handle != 0) {
                vt.setGpuHandle(handle);
            }
            return handle;
        } catch (Throwable t) {
            logOnce(t);
            return 0;
        }
    }

    /** Queue a GPU texture for disposal (any thread); drained on the render thread at flush. */
    public void queueTextureDispose(long handle) {
        if (handle != 0) {
            disposedTextures.add(handle);
        }
    }

    /**
     * Convert a texture's current data to a row-major RGBA byte buffer. Row-major sources (glyph
     * sprites, colormap strips) go through the provider's format-proof {@code getImage()};
     * COLUMN-major sources (Matplot image data, {@code isRowMajorOrder()==false}) are transposed
     * once here — the provider's linear {@code getImage()} would shear them — so the draw side
     * always uses a straight texcoord map.
     */
    private ByteBuffer textureToRgba(VulkanTexture vt) {
        final org.scilab.forge.scirenderer.texture.TextureDataProvider provider = vt.getDataProvider();
        final Dimension size = provider.getTextureSize();
        if (size == null || size.width <= 0 || size.height <= 0) {
            return null;
        }
        final int w = size.width;
        final int h = size.height;
        if (!provider.isRowMajorOrder()) {
            final ByteBuffer src = provider.getData();
            if (src != null && src.capacity() >= w * h * 4) {
                // column-major RGBA: element (row r, col c) at src index (c*h + r)
                final ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
                for (int r = 0; r < h; r++) {
                    for (int c = 0; c < w; c++) {
                        final int s = (c * h + r) * 4;
                        rgba.put(src.get(s));
                        rgba.put(src.get(s + 1));
                        rgba.put(src.get(s + 2));
                        rgba.put(src.get(s + 3));
                    }
                }
                rgba.flip();
                return rgba;
            }
            // fall through: unexpected layout — getImage() below at least renders something
        }
        final BufferedImage img = provider.getImage();
        if (img == null) {
            return null;
        }
        final int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        final ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
        for (int p : argb) {
            rgba.put((byte) ((p >> 16) & 0xff));
            rgba.put((byte) ((p >> 8) & 0xff));
            rgba.put((byte) (p & 0xff));
            rgba.put((byte) ((p >>> 24) & 0xff));
        }
        rgba.flip();
        return rgba;
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
                               + (lines.count / 8) + " line verts, "
                               + spriteQuads.size() + " sprites, canvas " + canvas.getWidth() + "x" + canvas.getHeight());
        }
        // frames are synchronous, so nothing is in flight here — safe point for GPU disposal
        Long dead;
        while ((dead = disposedTextures.poll()) != null) {
            renderer.destroyTexture(dead);
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
        for (int i = 0; i < imageQuads.size(); i++) {
            renderer.image(imageRefs.get(i)[0], imageQuads.get(i));
        }
        for (int i = 0; i < spriteQuads.size(); i++) {
            renderer.sprite(spriteRefs.get(i)[0], spriteQuads.get(i));
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
