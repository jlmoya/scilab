# Stage 1f-a — Native aggregate + executables under CMake — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-16
**Depends on:** Stage 1e (all 64 native module dylibs on the `scilab_module()` helper +
`scilab/CMakeLists.txt` driver, whole-tree PARITY OK; HEAD `8e6c7023ef4`). Strategy:
`docs/design/build-cmake-maven-migration.md`; Stage-1e usage: `docs/design/build-cmake-driver.md`;
scope manifest: `scilab/cmake/stage1e-manifest.md`.

## 1. Goal

Make `cmake --build … --target drop-in-all` produce the whole **native** Scilab app — the
`libscilab` / `libscilab-cli` aggregate libraries and the `scilab-bin` / `scilab-cli-bin`
executables — each proven byte-shape-identical to its autotools original by the parity harness
(now including `LC_RPATH`). Autotools stays fully functional; Java (Ant) and help remain
autotools-built (Stage 1f-b / 1f-c). This is the step where CMake becomes the master of the native
build end to end.

## 2. Scope

**In scope (1f-a):**
- The **23 fold-in modules** (below) compiled as CMake OBJECT libraries.
- The **aggregate** targets `libscilab` and `libscilab-cli` (fold the 23 OBJECT libs + link the 46
  Stage-1e standalone dylibs + jvm/GUI/NO_GUI/OTHER/EXTERNAL).
- The **executables** `scilab-bin` and `scilab-cli-bin`.
- **`LC_RPATH` in the parity fingerprint** + a re-baseline captured from a **pure-autotools rebuild**
  (the disciplined independent reference).
- The **`JAVA_HOME` hoist** into `ScilabToolchain.cmake` (`SCILAB_JAVA_HOME`).
- Extend the flag-fact check to the 23 fold-in modules.

**Out of scope (deferred):**
- The CMake→Ant bridge for the 24 Java modules (Stage 1f-b).
- Help generation (Stage 1f-c).
- Retiring `configure` / CMake taking over `machine.h`/`version.h` generation (spec §11; Stage 1f-c).
- The c++23 bump (spec §12).
- Any change to `configure.ac`, any `Makefile.am`, or generated code (except the harness + CMake files).

## 3. The 23 fold-in modules

`api_scilab, boolean, cacsd, core, data_structures, dynamic_link, elementary_functions, fftw,
fileio, integer, io, javasci, linear_algebra, mexlib, mpi, output_stream, parameters, polynomials,
sparse, string, threads, time, windows_tools`.

These declare `.la` libraries whose **objects fold into `libscilab`** (they are `LIBADD`ed as
convenience/static libs, or are `noinst`), so they build no standalone dylib and are absent from the
64-dylib Stage-1e baseline. In CMake each becomes an **OBJECT library** (`scilab_module()` already
supports this shape — the algo-lib fold), consumed by the aggregate via `$<TARGET_OBJECTS:…>`. Their
per-language flags, includes, and the linking rules are the Stage-1e helper's, unchanged. `windows_tools`
/ `mpi` may be macOS-inert (verify: build only what the autotools macOS build compiles).

## 4. Architecture

Three additions on top of the Stage-1e helper, all in `scilab/cmake/`:

- **Fold-in OBJECT libs** — reuse `scilab_module()` (or a thin `scilab_object_module()` wrapper) to
  compile each of the 23 modules into an OBJECT library, no dylib, no drop-in.
- **`scilab_aggregate(<name> …)`** — a new helper that builds `libscilab` / `libscilab-cli` as SHARED
  libraries: fold the relevant fold-in OBJECT libs (`$<TARGET_OBJECTS:…>`), link the 46 Stage-1e
  module dylib targets (by target name, so CMake records their install_names — matching autotools'
  shared `LIBADD`), plus jvm + the GUI/NO_GUI/OTHER/EXTERNAL system libs. `OUTPUT_NAME`/`SUFFIX`/
  install_name/rpaths exactly as autotools (the Stage-1e identity rules).
- **`scilab_executable(<name> …)`** — links `scilab-bin` / `scilab-cli-bin` from
  `modules/startup/src/cpp/scilab.cpp` (+ `initMPI.c` where MPI) against `libscilab(-cli)` + the exact
  `LDADD` (`COMMON_LIBS`, BLAS, …) and the platform `LDFLAGS` (`-Wl,-platform_version,macos,…`,
  the min-macos stamp, `-undefined` policy). Both executables carry `LC_BUILD_VERSION` (minos/sdk) +
  their 2 rpaths — the harness already fingerprints the executable's install_name + build_version;
  `LC_RPATH` (§6) closes the rest.

**Drop-in coexistence (unchanged from 1e):** CMake builds the aggregate + executables and drops them
into place (`modules/.libs/libscilab*.dylib`, `.libs/scilab-*-bin`) replacing the autotools ones; a
new `CMakeLists.txt` is invisible to automake; `make clean && make` recovers the autotools build.

## 5. The aggregate & executable link — parity targets

Both aggregates + both executables are ALREADY in the committed baseline (Stage 1e never rebuilt
them). So their symbol/dep/install_name fingerprints are the autotools reference; 1f-a must reproduce
them exactly, plus their `LC_RPATH`s (new, §6). The hard parts, each parity-arbitrated:
- `libscilab_la_LIBADD` mixes **linked** dylibs (the 46 pkglib `.la`s → recorded load commands) and
  **folded** objects (the 23 noinst `.la`s → no dep, symbols inlined). The `scilab_aggregate()` helper
  must get each class right — a folded module wrongly linked (or vice-versa) shows as a dep-set or
  symbol-count mismatch.
- The executables' heavy `LDFLAGS` are transcribed from the configured `Makefile` (as Stage-1e
  transcribed `SCI_*FLAGS`); macOS-only flags kept, Linux-only (`-static-libstdc++` etc.) excluded —
  the harness's dep-shape + build_version check is the arbiter.

## 6. LC_RPATH gate + re-baseline (decision: pure-autotools rebuild)

- **Harness change:** `parity/fingerprint.py` gains an `LC_RPATH` parser (`otool -l` → the ordered
  rpath list per dylib AND per executable); `capture.py` records it; `diff.py` compares it (order-
  significant). Fault-injected test: dropping/reordering an rpath must fail parity, naming the artifact.
  This closes the harness's rpath blind spot (surfaced by the jvm/JDK modules in Stage 1e).
- **Re-baseline (option a — the disciplined path):** restore the pure-autotools native tree
  (`make clean && make` — rebuilds the autotools module dylibs + aggregate + executables), then
  re-capture `baseline-autotools.json` WITH `LC_RPATH` from that independent autotools reference. This
  keeps the baseline a true autotools artifact (not CMake-derived). Then re-drop the CMake native build
  (`cmake --build … --target drop-in-all`) and confirm the whole tree is PARITY OK against the new
  baseline. The rebuild is ~30–60 min but is the honest reference; commit the new baseline.
- **Consequence:** every existing Stage-1e dylib is re-checked against the new rpath-aware baseline —
  a free regression sweep confirming their rpaths were right all along.

## 7. JAVA_HOME hoist

Move the `config.status`-sourced `JAVA_HOME` resolution (currently duplicated in `modules/jvm/
CMakeLists.txt` and the Batch-E JDK modules) into `ScilabToolchain.cmake` as `SCILAB_JAVA_HOME`
(config.status-first, `/usr/libexec/java_home` fallback with a warning that it may differ from the
configured JDK). jvm + external_objects_java + types + xcos consume the shared variable. Parity-neutral
(same resolved path); DRYs the duplication the Stage-8/9 reviews flagged.

## 8. The gate (the arbiter)

1. **Whole-tree harness (now rpath-aware) → PARITY OK** over all 68 dylibs + the 2 aggregates + the 2
   executables, incl. `LC_RPATH`, after `drop-in-all`.
2. **Flag-facts rc=0** — extended to the 23 fold-in modules' compile entries.
3. **End-to-end app run on the CMake-built executable:** the CMake `scilab-cli-bin` (dropped in) runs
   `1+1` → 2, a compute exercising a fold-in module (`disp(sum([1 2 3]))` → elementary_functions/core),
   and a gateway (`splin`/`covStart`) — clean exit. Proves CMake links a *working* executable, not just
   a shape-matching one. A UUID check confirms the running binary is the CMake build.
4. The autotools build still works (`make clean && make` recovers it — exercised during the re-baseline).

## 9. Migration mechanics & rollback

- **Order:** (1) add `LC_RPATH` to the harness + fault-injection test. (2) Autotools rebuild → re-capture
  the rpath-aware baseline → commit; confirm the current CMake tree (Stage-1e dylibs) is PARITY OK
  against it (proves no rpath regressions). (3) Migrate the 23 fold-in modules to OBJECT libs (batched,
  each folded module's objects verified). (4) `scilab_aggregate()` → build `libscilab`/`libscilab-cli`
  → drop in → PARITY OK. (5) `scilab_executable()` → link the 2 executables → drop in → PARITY OK +
  app-run. (6) JAVA_HOME hoist. (7) docs + CI update.
- **Rollback is free:** CMake is additive; `make clean && make` restores the entire autotools native
  build (module dylibs + aggregate + executables). No `configure.ac`/`Makefile.am` change.

## 10. Testing

- The **parity harness (rpath-aware)** is the primary test; its new `LC_RPATH` comparison is
  fault-injected (drop/reorder an rpath → fail), consistent with the harness's existing discipline.
- The **flag-fact check** extends to the fold-in modules (already fault-injected).
- The **app-run** (§8.3) is the behavioral acceptance for the executables.
- CI: the existing `.gitlab-ci.yml` native gate (`parity:cmake-drop-in`, runner-gated) covers the new
  aggregate/executables + rpath automatically once it runs; the always-on `sanity:cmake-driver` job's
  completeness check extends to the aggregate/executable targets.

## 11. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A fold-in module wrongly linked-as-dylib vs folded-as-objects | Dep-set + symbol-count parity catches it; the aggregate's dep list must match the baseline exactly. |
| The executables' platform `LDFLAGS` don't reproduce (build_version / rpath / undefined policy) | Transcribe from the configured Makefile; the harness gates install_name + build_version + (now) rpath; the app-run gate catches a mis-linked-but-shape-OK binary. |
| `LC_RPATH` order or a subtle rpath differs | The rpath-aware diff is order-significant; the autotools re-baseline is the exact reference. |
| The autotools rebuild for re-baseline is slow / flaky | One-time cost; run it once, commit the baseline; documented. |
| A fold-in module is macOS-inert (windows_tools/mpi) | Build only what the autotools macOS build compiles (check the configured Makefile's SUBDIRS/conditionals). |

## 12. Success criteria

- `cmake --build … --target drop-in-all` produces `libscilab`, `libscilab-cli`, `scilab-bin`,
  `scilab-cli-bin` (+ the 64 module dylibs), all dropped into place.
- Whole-tree **PARITY OK** (rpath-aware) over 68 dylibs + 2 executables; flag-facts rc=0.
- The CMake-built `scilab-cli-bin` runs the app end-to-end (compute + a fold-in module + a gateway).
- `JAVA_HOME` resolved once in the toolchain; the autotools build still recovers via `make`.
- The baseline is a rpath-aware pure-autotools reference; the harness now catches an rpath regression.
