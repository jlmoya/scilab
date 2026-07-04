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

/**
 * The seam between the backend-agnostic {@code VulkanMotor} (which turns graphic_objects geometry
 * into flat clip-space arenas) and the actual GPU renderer. Keeping it an interface lets the
 * scirenderer module compile and be exercised without the LWJGL/Vulkan surface library on the
 * classpath; the concrete implementation (backed by the standalone swing-gpu-surface renderer:
 * per-figure swapchain + arena upload + present) is injected by the GUI canvas once vendored.
 *
 * <p>Vertex layout for {@link #triangles}/{@link #lines} is 8 floats per vertex:
 * clip-space position (x, y, z, w — already multiplied by the scene→GL-clip matrix on the CPU) then
 * colour (r, g, b, a). The renderer applies only the fixed GL-clip→Vulkan z-remap and presents.
 */
public interface VulkanSceneRenderer {

    /** Notify the renderer of the current drawable size in physical pixels. */
    void resize(int width, int height);

    /** Begin a frame: clear to the given colour and start recording. */
    void beginFrame(float r, float g, float b, float a);

    /** Append filled triangles (3N vertices, {@code floatCount} = 8 * vertexCount). */
    void triangles(float[] clipPosColor, int floatCount);

    /** Append line segments (2N vertices, {@code floatCount} = 8 * vertexCount). */
    void lines(float[] clipPosColor, int floatCount);

    /**
     * Per-vertex user-clipping distances parallel to {@link #triangles}/{@link #lines} — 8 floats
     * (2 vec4, up to 6 clip planes; unused slots +LARGE) per geometry vertex, same order and count.
     * The fragment shader discards where any distance is negative. Supplied every frame alongside the
     * geometry so the scene pipeline's second vertex binding is always valid.
     */
    void triangleClips(float[] clipDistances, int floatCount);

    void lineClips(float[] clipDistances, int floatCount);

    /**
     * Depth epochs: the boundaries at each {@code clearDepthBuffer()} call, matching JOGL, which
     * draws the axes box then clears depth so data draws over it. {@code splits} holds 3 ints per
     * clear point — {triVertex, lineVertex, imageIndex} counts at that boundary — so the renderer
     * replays each epoch depth-tested and clears the depth buffer (preserving colour) between them.
     * Empty = one epoch (no clears). Must be set before {@link #endFrame}.
     */
    void depthEpochs(int[] splits);

    /**
     * Upload an RGBA8 image (glyph sprite, mark, colormap strip, image plot) and return a handle
     * (0 = failed). May be called outside beginFrame/endFrame, on the render thread.
     */
    long uploadTexture(int width, int height, java.nio.ByteBuffer rgba, boolean linearFilter);

    /** Destroy a texture previously returned by {@link #uploadTexture}. Render thread only. */
    void destroyTexture(long handle);

    /**
     * Append one screen-aligned textured sprite quad: 6 vertices x (x, y, u, v), positions in
     * GL-convention NDC (y-up). {@code tintAux8} = tint rgba + aux rgba, where aux.w selects the
     * colour mode: 0 = MODULATE texel*tint (glyphs/images; white tint = pass-through), 1 = the
     * fixed-function two-colour BLEND for marks (black template texels take the per-point tint,
     * white texels take aux, alpha keeps the shape). Sprites draw LAST, alpha-blended, no depth.
     */
    void sprite(long textureHandle, float[] posUv24, float[] tintAux8);

    /**
     * Append one textured SCENE quad (image plots): 6 vertices x (x, y, z, w, u, v), positions in
     * GL clip space (scene transform pre-applied on the CPU). Depth-tested like scene geometry.
     */
    void image(long textureHandle, float[] clipPosUv36);

    /** Finish the frame: submit + present (and read back for figure export). */
    void endFrame();

    /** Release GPU resources. */
    void dispose();

    /** No-op sink used until the GPU renderer is injected — lets the backend run headless. */
    VulkanSceneRenderer NOOP = new VulkanSceneRenderer() {
        @Override
        public void resize(int width, int height) {
        }

        @Override
        public void beginFrame(float r, float g, float b, float a) {
        }

        @Override
        public void triangles(float[] clipPosColor, int floatCount) {
        }

        @Override
        public void lines(float[] clipPosColor, int floatCount) {
        }

        @Override
        public void depthEpochs(int[] splits) {
        }

        @Override
        public void triangleClips(float[] clipDistances, int floatCount) {
        }

        @Override
        public void lineClips(float[] clipDistances, int floatCount) {
        }

        @Override
        public long uploadTexture(int width, int height, java.nio.ByteBuffer rgba, boolean linearFilter) {
            return 0;
        }

        @Override
        public void destroyTexture(long handle) {
        }

        @Override
        public void sprite(long textureHandle, float[] posUv24, float[] tintAux8) {
        }

        @Override
        public void image(long textureHandle, float[] clipPosUv36) {
        }

        @Override
        public void endFrame() {
        }

        @Override
        public void dispose() {
        }
    };
}
