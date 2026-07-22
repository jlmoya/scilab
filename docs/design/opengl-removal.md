# Modernization TODO: remove OpenGL/JOGL entirely — Vulkan as the only graphics path

**Status:** NOT STARTED. Scoped 2026-07-21.
**Goal:** delete JOGL/GlueGen from Scilab. Vulkan (via our own renderer + MoltenVK on macOS)
becomes the single graphics implementation, not one of two.

## Why this exists

The Vulkan renderer (`docs/design/vulkan-renderer.md`, merged `d30f75059e5` 2026-07-10) was built
and readback-verified through M1–M8 including text/mark sprite clipping. That work added
`implementation.vulkan.*` to scirenderer **alongside** the existing `implementation.jogl.*`. It was
never followed by the removal step, so OpenGL is still the production path. This file records the
remaining work so that gap stops being invisible.

The intent was always *replacement*, not coexistence.

## Measured scope (2026-07-21)

Smaller than the 4 jars and 6 POM dependencies suggest.

**Only 3 modules actually import `com.jogamp` — 30 files total:**

| Module | Files importing `com.jogamp` |
|---|---|
| `scirenderer` | 20 |
| `gui` | 9 |
| `renderer` | 1 |

**3 of the 6 POM dependencies are already dead** — they declare JOGL and import it nowhere:
`completion`, `graphic_objects`, `graphic_export` (0 importing files each).

**scirenderer already carries both implementations side by side:**
`org/scilab/forge/scirenderer/implementation/jogl` (24 files) and `.../vulkan` (16 files).

**Shipped artifacts to remove at the end:** `thirdparty/{jogl-all-2.5.0.jar, jogl2.jar,
gluegen-rt-2.5.0.jar, gluegen2-rt.jar}`, their `etc/classpath.xml` entries, and the JOGL native
libraries.

## Proposed phases

Each phase ends with a working, testable tree — no phase leaves graphics broken.

**Phase 1 — drop the 3 dead POM dependencies.** `completion`, `graphic_objects`,
`graphic_export`. Zero importing files, so this is a POM-only change. Verify with a full
`mvn package` plus a GUI smoke. Cheapest possible start and it shrinks the dependency graph
before any real porting.

**Phase 2 — close the scirenderer Vulkan gap (24 jogl files vs 16 vulkan).** This is the core of
the work. Includes the two deferred renderer items that block parity: **#101** IMAGE-plot clipping
(needs the axes camera transform at `drawImage`; sprite clipping is already done) and **#102**
grayplot. Everything here is readback-verifiable headlessly, the same way M1–M8 were.

**Phase 3 — port the 9 `gui` files and 1 `renderer` file.** Canvas creation and surface
integration: the Swing↔GPU surface (`cc.sosonline.gpu`) already exists, so this is rewiring
consumers rather than new infrastructure.

**Phase 4 — delete.** Remove `implementation.jogl.*`, the 4 thirdparty jars, the classpath.xml
entries, the JOGL natives, and JOGL from `.gitlab-ci/prebuild.sh`. Parity harness re-baseline.

**Cross-cutting blocker: #103 Win/Linux surface.** The Swing↔GPU surface is macOS-only today. JOGL
cannot be deleted for *upstream* until Windows and Linux surfaces exist — though this fork is
macOS-targeted, so the fork could complete Phases 1–4 first and treat #103 as the upstreaming gate.

## Verification

Graphics correctness is provable headlessly here — the Vulkan work used readback comparison
throughout, and `xs2png` + image inspection covers plot output without a desktop session. Every
phase should carry that evidence. GUI surfaces remain user-verified, never screen-captured.

## Related

- `docs/design/vulkan-renderer.md` — the renderer design and the M1–M8 record
- `docs/design/deferred-fixes-register.md` §5d — why JOGL is still load-bearing today
- Deferred renderer items: #101 IMAGE-plot clip, #102 grayplot, #103 Win/Linux surface
