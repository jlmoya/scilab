# Vulkan/MoltenVK renderer for Scilab — design

Status: **foundation de-risk complete** (2026-07-01). Direction: our own renderer on **raw Vulkan**,
running through **MoltenVK** on macOS. Raw Vulkan gives direct control over per-window native surfaces,
so multiple concurrent Scilab figures each get their own swapchain and present independently — the
multi-window requirement is met natively.

Branch: `feature/vulkan-renderer` (off `main`). The reusable Swing↔GPU surface (Layer-1) lives in the
standalone `swing-gpu-surface` repo; the renderer (Layer-2) and the scene translation (Layer-3) live here.

## Goal

Modern, fast, interactive 3-D for Scilab figures — real-time orbit/zoom, depth, eventually lights and
richer materials — driving the existing `graphic_objects` model through the **shared, backend-agnostic
`DrawerVisitor`** (the same visitor JOGL uses). **Multiple concurrent figures is a hard requirement.**
macOS/MoltenVK now; native Vulkan on Windows/Linux later behind the same seam.

## Non-goals (for now)

- Not replacing JOGL until parity — the two coexist behind the canvas factory.
- Not a general 3-D engine — built for Scilab; the only genuinely reusable piece is Layer-1.
- Not hardware ray tracing.

## What the foundation already proved (spikes in `swing-gpu-surface`)

On Apple M2 Max / MoltenVK / Vulkan SDK 1.4.350.1, LWJGL `lwjgl-vulkan` 3.3.4:

1. `VulkanProbe` — loader + MoltenVK enumerate the GPU.
2. `VulkanSurfaceProbe` — `CAMetalLayer → VkSurfaceKHR` (`VK_EXT_metal_surface`); queue family 0 does
   graphics+present; BGRA8 formats; FIFO present; 2–3 swapchain images.
3. `VulkanClearSpike` — device + swapchain + clear + **present** + GPU readback (single window, verified).
4. `VulkanMultiWindowSpike` — **one instance + one device, two surfaces/swapchains, two windows with
   distinct content**, each verified by readback. Native multi-window works.

So the whole present path (device → swapchain → command buffer → present → readback) is proven and
scales to N windows. The renderer is the layer above it.

## Architecture — three layers

| Layer | What | Where | Status |
|------|------|-------|--------|
| **1. Surface** | Swing component owning a `CAMetalLayer`, exposes a backend-agnostic `NativeSurface` | `swing-gpu-surface` | ✅ reused |
| **2. Renderer** | `VulkanContext` (instance/device/one render thread) + per-figure swapchain/pipelines | this repo | to build |
| **3. Scene** | `DrawerVisitor` → Vulkan draws (`VulkanCanvas`/`VulkanDrawingTools`/`VulkanShapeDrawer`) | this repo | to build |

### The multi-window model (the core decision)

**One `VkInstance` + one `VkDevice` + one render thread** — a process-wide `VulkanContext` singleton.
Each figure owns its **own `VkSurfaceKHR` + `VkSwapchainKHR` + depth image + framebuffers**, and
registers with the context. The single render thread loops over registered figures: for each, acquire
an image, record its scene, submit, present. This is exactly the shape spike 4 proved, generalized to
"figures come and go." Vulkan objects are externally synchronized, so a single render thread keeps
lifetime and synchronization simple, and matches the `DrawerVisitor` being driven per figure.

- Figures **register/unregister** on the EDT (figure open/close) via a thread-safe queue drained by the
  render thread; swapchain/pipeline creation and all `vk*` calls happen on the render thread.
- Context **inits lazily** on the first figure, **shuts down** when the last figure closes.
- **Production-hardening rigor from the start**: a `volatile stopRequested` shutdown gate, join the
  render thread before a surface is released, never present to a torn/disposed surface, log-once (not
  per-frame) render errors, and destroy GPU resources on the render thread — never the interpreter thread.

## Key design decisions

- **Shaders**: small GLSL, compiled to **SPIR-V** with the SDK's `glslc` (a build step). Two programs:
  **vertex-color** (filled + line geometry) and **textured** (colormap surfaces, text/mark sprites,
  image plots). Vulkan clip-space depth is **[0,1]** — the projection matrix targets [0,1] directly.

- **Pipelines**: pipeline state (topology, depth-test, cull, blend) is baked into `VkPipeline` objects.
  A **small fixed set** — depth-tested triangles, depth-tested lines, sprite (no depth), image (depth) —
  created once on the shared context. Viewport/scissor stay dynamic. (If MoltenVK exposes
  `VK_EXT_extended_dynamic_state` we can collapse some; not assumed.)

- **Depth epochs (honoring `clearDepthBuffer`)**: JOGL draws the axes box, then clears the depth buffer
  so data draws over it. The renderer mirrors this exactly: every `clearDepthBuffer()` records an epoch
  boundary (tri-vertex / line-vertex / image counts), and one render pass replays the frame epoch by
  epoch — each depth-tested — clearing only the depth aspect (colour preserved) between epochs with
  `vkCmdClearAttachments`. So **all fills are depth-tested triangles** (an earlier no-depth "backdrop"
  pass was dropped): the axes box is an ordinary flat fill, and real flat polygons — bars, histograms,
  `xfpoly`, flat 3D facets — now depth-test and self-occlude correctly. Handles subplots (each axes'
  box → clear → data) in the single pass.

- **Geometry (immediate mode)**: the `DrawerVisitor` re-submits geometry per frame. Use a **per-frame
  arena** — a large host-visible (or staged device-local) vertex/index buffer written each frame,
  sub-allocated per draw. Uniforms (MVP, flat color, params) via **push constants** where they fit, else
  a small per-frame UBO + descriptor set. Textures (colormap/text/mark/image) via combined image
  samplers; upload staged; destroyed on the render thread.

- **Render pass**: classic `VkRenderPass` with **color + depth** attachments per swapchain (Vulkan 1.1
  safe; dynamic rendering not assumed on MoltenVK 1.1). One framebuffer per swapchain image; a depth
  image per figure. Real depth test with correct face-winding.

- **HiDPI + resize**: the surface's `currentExtent` is **logical**; drive the swapchain at **physical**
  pixels (set the `CAMetalLayer` `drawableSize`). On resize / DPI change, **recreate the swapchain**
  (and depth/framebuffers) — the standard `VK_ERROR_OUT_OF_DATE_KHR` / size-changed path, on the render
  thread.

- **Export/readback**: GPU readback is proven, so `dumpAsBufferedImage` is **properly supported** — copy
  the presented image to a host-visible buffer and hand back a `BufferedImage`.

- **MoltenVK provisioning** (implemented): the packaged app ships `libMoltenVK.dylib` in `thirdparty/`
  (the prebuild copies it from `$VULKAN_SDK`); the canvas factory resolves it **SCI-relative** and points
  LWJGL at it via `-Dvk.loader` → `Configuration.VULKAN_LIBRARY_NAME`, so no SDK/env is needed at runtime.
  Loader priority: `-Dvk.loader` → `$VULKAN_SDK` (dev) → the system default (fails cleanly to JOGL). We
  drive MoltenVK **directly** (no loader), so `VK_KHR_portability_enumeration` is a loader-only feature
  and is requested **only if the instance advertises it** (queried, conditional); device needs
  `VK_KHR_swapchain` + `VK_KHR_portability_subset`. Verified end-to-end with no SDK env: LWJGL loads the
  bundled dylib and a `surf` renders.

- **Scilab integration**: a `VulkanCanvas extends AbstractScilabCanvas` selected by a canvas factory
  (JOGL default; Vulkan when enabled), plus LWJGL `lwjgl-vulkan` vendoring + `classpath.xml` wiring.

## Build plan — incremental, de-risk each milestone

Each milestone is verified headlessly (readback → PNG) where possible; the window is eyeballed only
when needed.

- **M1 — pipeline**: GLSL→SPIR-V + a `VkPipeline` + draw a **triangle** (proves shaders/pipeline/draw).
- **M2 — geometry**: per-frame vertex/index arena + MVP push-constant → a **rotating cube / a surf mesh**.
- **M3 — context + lifecycle**: extract `VulkanContext` (shared instance/device/thread) + per-figure
  swapchain + register/unregister; render **two figures** with independent scenes.
- **M4 — scene translation**: `VulkanShapeDrawer`/`VulkanDrawingTools`/`VulkanCanvas` driven by the
  shared `DrawerVisitor` → a real **`surf` through Vulkan** (fills + lines + depth).
- **M5 — textured + features**: colormap textures, text/mark sprites, image plots; rotate/zoom;
  picking/datatips; resize.
- **M6 — Scilab integration**: `VulkanCanvas` + factory + vendoring on `feature/vulkan-renderer`;
  a real Scilab figure renders through Vulkan.
- **M7 — production-hardening pass** *(done)*: four audits (leaks, thread-safety, silent failures,
  correctness) + fixes; the C1 depth-epoch redesign above; MoltenVK bundling for the packaged app;
  docs. All verified by GPU readback.
- **M8 — user clipping + lighting** *(done)*:
  - **Clipping** (`clip_state`/`clip_box`/`clipgrf`): the motor bakes up to 6 per-vertex clip distances
    (`dot(plane, scene-vertex)`; the plane's transform cancels the geometry's) into a parallel vertex
    buffer (binding 1); the fragment shader discards where any is negative. Handles 2D (4-plane) and the
    3D box (6-plane). Verified: a curve clips to an axes box tighter than its range.
  - **Lighting** (`light()` + material): CPU per-vertex ambient + diffuse (Gouraud) baked into the vertex
    colour, in scene space with the raw vertex + normal, colorMaterial-aware — mirroring JOGL/g2d.
    Specular is skipped (needs the eye in scene space). Verified against the g2d software renderer: the
    shading gradient matches.

## Status (M1–M8 complete)

Everything is done and verified via readback: `surf`, `plot2d`, `param3d`, scatter/marks, text/labels,
`Matplot` image plots (single + multi-figure), bars/histograms/flat-3D (depth epochs), figure export,
picking, preferences, the bundled-MoltenVK packaged-app path, user clipping, and lit surfaces. Every
commit is authored plainly with no AI-attribution trailers.

**Known limitations / follow-ups:**
- **Text/mark sprites are clipped** (M8c): the sprite pass carries the active clip planes' baked distances
  in its per-draw **push constant** (`tex.frag` discards where any <0). The motor bakes `dot(plane, anchor)`
  once per glyph — the anchor is in scale-translated data space (`canvasProjection`'s input, same frame as
  the planes), so a mark or label is kept/dropped as its anchor enters/leaves the clip region; window-space
  chrome (tick labels, titles) is never clipped. Push-constant (per-draw), not a vertex attribute — an
  earlier per-vertex-attribute attempt regressed all sprites on MoltenVK and was reverted. Verified by
  readback (marks + line clip to a box tighter than the data; ticks stay).
- **Image plots (`Matplot`) are NOT clipped yet**: the image pass shares `tex.frag` and pushes all-inside
  clip (never discards). Correct clipping needs the corner in scale-translated data space, but the corners
  are texture-pixel space and the axes camera transform to convert is not isolated at `drawImage`
  (`getTransformation()` composes the Matplot's local pixel→data push). Rarely visible (images usually fit).
- **Lighting of images/sprites is intentionally absent** — unlit texture overlays in Scilab (raw `Matplot`
  pixels, fixed-colour text/marks), matching JOGL. Only geometry is lit.
- **Specular highlights**: skipped — the clip-space CPU transform doesn't carry the eye position in scene
  space. Diffuse + ambient cover the dominant visual effect.
- **`grayplot`**: renders blank — its mesh geometry (`dataManager.getVertexBuffer`) arrives empty, so
  nothing reaches the motor. Reproduces in the headless PNG export too, i.e. **upstream of the renderer**
  and not Vulkan-specific; tracked separately.
- **Win/Linux**: the renderer is Vulkan (portable); only the Layer-1 Swing↔GPU surface (macOS
  `CAMetalLayer`) and the MoltenVK bundling are macOS-specific. A Win/Linux surface + native Vulkan
  loader are the remaining portability work.

## Risks

1. **MoltenVK feature subset** (Vulkan 1.1 + `portability_subset` limits) — validate features as used;
   avoid 1.2/1.3-only paths.
2. **Immediate-mode geometry churn** — size/manage the per-frame arena to avoid stalls; consider caching
   static geometry later.
3. **Pipeline explosion** — keep the state matrix small; cache aggressively.
4. **Shipping MoltenVK** in the relocatable macOS app (dylib bundling + loader path).
5. **Visual verification in the sandbox** — readback covers most; the window is eyeballed at UI-facing
   milestones.
