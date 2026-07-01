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
  Keep a **small pipeline cache keyed by (program, topology, depth/blend/cull state)**, created on
  demand — a handful of combinations (scene-fill, backdrop, lines, sprite, image). Viewport/scissor
  stay dynamic. (If MoltenVK exposes `VK_EXT_extended_dynamic_state` we can collapse some; not assumed.)

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

- **MoltenVK provisioning**: dev uses the LunarG SDK loader + `MoltenVK_icd.json`. The **packaged app
  must bundle MoltenVK** (`libMoltenVK.dylib` as the ICD, or the loader + ICD) and point LWJGL at it
  (`Configuration.VULKAN_LIBRARY_NAME`) — a shipping task, not a dev blocker. Instance needs
  `VK_KHR_portability_enumeration` (+ the enumerate-portability flag); device needs `VK_KHR_swapchain`
  + `VK_KHR_portability_subset`.

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
- **M7 — production-hardening pass**: full audit (leaks, thread-safety, silent failures, docs) + MoltenVK
  bundling for the packaged app.

## Risks

1. **MoltenVK feature subset** (Vulkan 1.1 + `portability_subset` limits) — validate features as used;
   avoid 1.2/1.3-only paths.
2. **Immediate-mode geometry churn** — size/manage the per-frame arena to avoid stalls; consider caching
   static geometry later.
3. **Pipeline explosion** — keep the state matrix small; cache aggressively.
4. **Shipping MoltenVK** in the relocatable macOS app (dylib bundling + loader path).
5. **Visual verification in the sandbox** — readback covers most; the window is eyeballed at UI-facing
   milestones.
