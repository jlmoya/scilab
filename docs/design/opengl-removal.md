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

**CORRECTION 2026-07-23 — there is no dead-dependency shortcut.** An earlier draft claimed
"3 of the 6 POM dependencies are already dead (completion, graphic_objects, graphic_export)."
That was a survey error: it came from `grep -li jogl modules/*/pom.xml` → 6, which matches
**comment prose**, not `<dependency>` elements. A real audit
(`grep -cE '<artifactId>(jogl-all|gluegen-rt)</artifactId>'`) shows JOGL is declared **only** in
the three real consumers — `gui`, `renderer`, `scirenderer` — each carrying jogl-all + gluegen-rt.
The three "dead" modules declare **zero** JOGL deps; their jogl mentions are accurate comments
(POM-ordering analogies; graphic_export's use of the Scilab `JoGLView`/`implementation.jogl.*`
classes via reactor siblings, which is a real Vulkan-porting dependency but not a `<dependency>`).

**scirenderer already carries both implementations side by side:**
`org/scilab/forge/scirenderer/implementation/jogl` (24 files) and `.../vulkan` (16 files).

**Shipped artifacts to remove at the end:** `thirdparty/{jogl-all-2.5.0.jar, jogl2.jar,
gluegen-rt-2.5.0.jar, gluegen2-rt.jar}`, their `etc/classpath.xml` entries, and the JOGL native
libraries.

## Proposed phases

Each phase ends with a working, testable tree — no phase leaves graphics broken.

**Phase 1 — REMOVED (was "drop 3 dead POM deps").** The mirage above. There is no cheap POM-only
start; the removal is entirely the porting work in the phases below. The renumbering keeps the
original Phase 2/3/4 names for continuity with earlier references.

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
