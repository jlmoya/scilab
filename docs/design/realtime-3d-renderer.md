# Real-time 3-D renderer for Scilab (bgfx) — design

Status: **Stage-2 integration PROVEN**, 2026-06-28 (design approved 2026-06-27). Direction: a reusable Swing↔GPU surface layer +
a **bgfx** renderer, with raw Vulkan kept as a future backend.

## Goal

Give Scilab modern, fast, **interactive** 3-D: real-time orbit / fly-through, multiple light
sources, shadows, and PBR-ish materials — so 3-D plots look great and read clearly — and keep
it **portable** (macOS now, Windows/Linux later) without locking to Metal.

## Non-goals (for now)

- Not replacing the existing JOGL/OpenGL renderer until parity — the two **coexist behind a flag**.
- Not hardware ray tracing (raster-cinematic first; HW RT is a Metal-only future, out of scope).
- Not JavaFX — Swing/AWT only (JavaFX has no clean native-handle path; deferred deliberately).
- Not a general-purpose 3-D engine — build for Scilab, **extract** the one genuinely reusable
  piece (Layer-1), don't speculatively generalize the rest (YAGNI).

## Architecture — three layers, and what is reusable

| Layer | What | Reusable? | Where it lives |
|------|------|-----------|----------------|
| **1. Surface** | a Swing component that owns a native GPU surface and exposes a backend-agnostic handle | ✅ **very** (any Java GUI app) | **standalone module** |
| **2. Renderer** | the drawing engine, on **bgfx** (→ Metal/Vulkan/D3D/GL) | ⚠️ Scilab-shaped but bgfx-portable | scilab fork |
| **3. Scene** | translate Scilab's `graphic_objects` model → bgfx meshes/materials/lights | ❌ Scilab-only | scilab fork |

**Why bgfx for Layer-2:** cinematic-capable raster, **native Metal on Mac**, **official LWJGL
bindings** (zero binding work), broadest portability, and *far* less plumbing than raw Vulkan.
Raw **Vulkan** (LWJGL) stays a future Layer-2 backend if we ever need single-API control or a
feature bgfx doesn't expose — and because **Layer-1 is backend-agnostic**, that swap is cheap.

## Layer-1 — `swing-gpu-surface` (the reusable module)

A heavyweight AWT `Canvas` that owns a native surface and hands it to a GPU backend:

- **macOS:** via JAWT (`org.lwjgl.system.jawt`), obtain the component's `CALayer` interface
  (`JAWT_SurfaceLayers`), attach a **`CAMetalLayer`**, and pass it to bgfx as
  `PlatformData.nwh`. This is the same JAWT/`CAMetalLayer` pattern already proven by
  **lwjgl3-awt's `AWTVulkanCanvas`** — we generalize it and keep a clean backend seam.
- **Lifecycle:** `addNotify` → acquire surface; resize → update drawable size + `bgfx_reset`;
  HiDPI → `contentsScale` from the component's `GraphicsConfiguration`; `removeNotify` → release.
- **Threading:** a **dedicated render thread** runs the bgfx frame loop; the EDT only manages the
  component. (macOS main-thread/AppKit interplay is **Risk #1** — see below.)
- **API:** `NativeSurface` (handle, width, height, scale) + `GpuSurfaceComponent` (Swing) +
  a `BgfxBackend` adapter. A `VulkanBackend` adapter slots in later.

## Layer-2 — bgfx renderer (Stage-0 = minimal)

LWJGL `org.lwjgl.bgfx`. Init with the Metal renderer (auto on Mac) + the Layer-1 surface handle;
frame loop = set view rect → clear → submit geometry → `bgfx_frame`. Shaders compile via bgfx's
`shaderc` (a build step); Stage-0 uses one trivial vertex+fragment shader.

## Stage-0 deliverable (the bounded first milestone)

A **standalone** app — built with **Maven** (the POC's own build; LWJGL bgfx + JAWT pulled from
Maven Central with the macOS-arm64 native classifiers) — that opens a Swing window with a
bgfx-rendered **spinning lit cube** on macOS Metal: resizable, HiDPI-correct, clean shutdown. It
proves Layer-1 + bgfx + the macOS threading model **end-to-end, independent of Scilab** — i.e. the
riskiest 20% first. No Scilab code is touched in Stage-0.

> **Build & dependencies — note the split.** The **standalone Stage-0 POC uses Maven** (user's
> choice, and aligned with the modernization north-star of moving Scilab off Ant). **Scilab proper**
> today builds with Autotools (`make`) + **Ant**, with Java deps **vendored as jars** in
> `thirdparty/` + `etc/classpath.xml` — so when Layer-1/2 integrate into Scilab, they follow that
> (vendor the LWJGL jars), *until* the project-wide Ant→Maven migration lands. See
> `[[scilab-modernization-vision]]`.

## Roadmap after Stage-0

**Progress (2026-06-28).** Stage-0 ✅ PROVEN (`[bgfx] initialised: Metal`, no crash — Risk #1
retired). Stage-1 ✅ the **spinning cube** is wired (Metal shaders compiled via bgfx `shaderc`;
`[bgfx] cube ready`), committed in `swing-gpu-surface`. Stage-2 ✅ **Scilab integration PROVEN
end-to-end**: a flag-gated `SwingScilabBgfxCanvas` (`-Dscilab.renderer.bgfx=true`, **OFF by
default**) renders the cube inside a real Scilab figure (`[bgfx] initialised: Metal 1220x920` = the
figure's canvas), with LWJGL natives loading correctly under Scilab's `ScilabClassLoader`. The seam:
a new `AbstractScilabCanvas` base + a `ScilabCanvasFactory` that falls back to JOGL on any error;
LWJGL + lwjgl-bgfx + lwjgl3-awt + the Layer-1 jar vendored into `thirdparty/` and wired through
`classpath.xml`(.in) + `build.incl.xml`. What remains below is the rendering **content** (Layer-3)
and visual features.

1. Harden Layer-1 into the reusable library (clean API + tests) — the reuse payoff.
2. ✅ **DONE** — Scilab integration behind a flag: a bgfx canvas alongside `SwingScilabCanvas`
   (proof cube in a real figure). **Next:** translate `graphic_objects` → a bgfx scene so a real
   `surf`/`plot3d` renders through bgfx (Layer-3 — a `DrawerVisitor` equivalent; the bigger half).
3. Rendering features: orbit/trackball camera → multiple lights → depth → **shadow maps** →
   PBR-ish materials (the "amazing" look).
4. Plot-type coverage: `surf`, `mesh`, `plot3d`, scatter, lines, text/axes (translate the
   `graphic_objects` model / `scirenderer` draw calls to bgfx).
5. Make it default; validate portability (Linux→Vulkan, Windows→D3D, all via bgfx).

## Risks (front-loaded)

1. **macOS AWT main-thread ↔ Metal-layer threading.** Scilab already hit main-thread graphics
   crashes (build doc Appendix A). bgfx uses a dedicated render thread; **the Stage-0 POC's #1
   job is to prove this is stable on macOS 26 + JDK 25**. Highest risk → surfaced first.
2. **JAWT / `CALayer` interop across JDK versions.** Mitigation: LWJGL JAWT + the
   lwjgl3-awt-proven pattern.
3. **Coexisting with `scirenderer`** (deeply wired into `graphic_objects`). Mitigation: flag-gated
   coexistence; reach parity before retiring JOGL.
4. **bgfx shader build pipeline** (`shaderc`). A build-time tool dependency; vendor it.
5. **Visual verification in the dev sandbox.** I can write/compile/run headless checks, but the
   rendered window must be eyeballed on your display — so you confirm visuals at each stage.

## Reuse plan

Layer-1 ships as a **standalone library** (own repo, e.g. `swing-gpu-surface`): backend-agnostic
`NativeSurface` + a bgfx adapter now, a Vulkan adapter later — reusable in any Swing app. Layers
2-3 are built for Scilab, factored cleanly, with reusable bits extracted only when a second
consumer actually appears.

## Open decisions

- **Layer-1 home:** a standalone repo (recommended for reuse) — start it as its own project under
  `SciLabProjects/swing-gpu-surface`, or in the scilab tree then extract.
- **Verification cadence:** I provide a `mvn` run command + headless checks; you eyeball the
  window at each stage.
- **Stage-0 build tool:** ✅ **Maven** (decided).
