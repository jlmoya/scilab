# The CMake native-build driver (Stage 1f-a — the whole native app)

**Status:** DONE — verified end-to-end 2026-07-17 (from-scratch build → whole-tree
**rpath-aware** PARITY OK → the real app runs on the CMake-built executable).
**What it is:** the top-level `scilab/CMakeLists.txt` + the helpers in `scilab/cmake/`
(`ScilabModule.cmake`, `ScilabAggregate.cmake`, `ScilabToolchain.cmake`) that build the
**entire native Scilab app** under CMake — the 64 baseline module dylibs, the 21 fold-in
core OBJECT libraries, the `libscilab`/`libscilab-cli` aggregate libraries, and the
`scilab-bin`/`scilab-cli-bin` executables — and drop each into the autotools `.libs/`
layout, matching the autotools build in exported symbols, link/dependency shape,
install_name, `LC_RPATH`, and compiler flag-facts (arbitrated by the parity harness).
This is *not* byte-for-byte identity — a fresh compile carries a distinct Mach-O UUID by
design; what the harness proves is behavioral/link-shape equivalence. Strategy context:
`docs/design/build-cmake-maven-migration.md`; design spec:
`docs/superpowers/specs/2026-07-16-stage1f-a-aggregate-executables-design.md`;
authoritative dylib list: `scilab/cmake/stage1e-manifest.md`.

CMake is now the master of the **native** build end to end. This remains **hybrid
coexistence**, not a full cutover: autotools still configures the tree, runs Ant for the
jars, and builds help. The CMakeLists files are invisible to automake, so the autotools
path is untouched and rollback is free (`make clean && make` recovers the entire native
build — dylibs, aggregates, and executables). The Java (Ant→Maven) bridge is Stage 1f-b;
help + retiring `configure` is Stage 1f-c.

## Usage

```bash
# 0. Prerequisite — an autotools-CONFIGURED (and, for the full parity gate, BUILT) tree:
#    config.status, modules/core/includes/machine.h + version.h must exist.
cd scilab && ./configure <usual flags> && make        # see docs/design/build-modernization.md

# 1. Configure the CMake build (Makefile/Ninja generators only).
#    Fortran must be Homebrew gfortran; if a stray flang wins, pass
#    -DCMAKE_Fortran_COMPILER=gfortran (the driver hard-fails otherwise).
cmake -S . -B build-cmake

# 2. Build the whole native app + drop each artifact into modules/.../.libs/
cmake --build build-cmake --target drop-in-all -j
#    Sub-targets: drop-in-<module> (one dylib), sci-foldin-all (the 21 OBJECT libs),
#    drop-in-libscilab / drop-in-libscilab-cli (aggregates), scilab-bin / scilab-cli-bin.

# 3. The gate — parity vs the committed autotools baseline + per-TU flag facts
cd build-parity
python3 -m parity.capture .. /tmp/cand.json cand
python3 -m parity.diff baseline-autotools.json /tmp/cand.json          # PARITY OK, rc=0
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json # rc=0
```

Measured on the dev machine (M-series, `-j`, ccache warm): configure ≈ 46 s; a
from-scratch `drop-in-all` of the whole native app (rm -rf build-cmake first; 3668
compile/link steps — the 64 dylibs + the 21 fold-in modules incl. elementary_functions'
269 TUs + the two aggregates + the two executables) ≈ 3 min; capture ≈ 1 min.

**Rollback / recovering the autotools output** — each drop-in is a plain file copy, so
the autotools artifact is always one rebuild away. Module dylibs recover per module dir
(`make -C modules/<m> clean && make -C modules/<m>` — restores all of that dir's dylibs;
a bare `make` may not relink when the `.la` is newer than its sources, so `clean` forces
it). The aggregates + executables recover with a top-level `make` (they are the final
link steps of the autotools native build).

## What is in scope (and proven)

- **The 64 baseline module dylibs** across 46 module dirs — the first `foreach` block of
  the driver, one `scilab_module()` call each. Per-dylib rows, external deps, and module
  edges: `scilab/cmake/stage1e-manifest.md`.
- **The 21 fold-in core OBJECT libraries** — the second `foreach` block, one
  `scilab_object_module()` call each. These dirs (`elementary_functions`, `string`, `io`,
  `core`, `linear_algebra`, …) build no standalone dylib on macOS (automake declares their
  `.la` `noinst`); their objects fold into the aggregates via `$<TARGET_OBJECTS:sci<m>-obj>`.
  `mpi` (macOS-inert) and `javasci` (its only native output, `libjavasci2`, is a separate
  pkglib that links *against* `libscilab`) are deliberately not fold-in targets.
- **The two aggregates** `libscilab` (GUI: ENGINE + jvm + GUI_LIBS; 59 deps) and
  `libscilab-cli` (ENGINE only; 39 deps), both exporting the same 3543 symbols (both fold
  the identical 21 modules) — `scilab_aggregate()` in `modules/CMakeLists.txt`. Each LIBADD
  member is classified FOLD (objects enter the aggregate, no dep) vs LINK (recorded as an
  `LC_LOAD_DYLIB` dep at its install_name); libtool's transitive `.la` records
  (`libscisundials`, `libsciconsole-minimal`, the system/keg libs) are reproduced
  explicitly because CMake target links do not propagate.
- **The two executables** `scilab-bin` and `scilab-cli-bin` — `scilab_executable()`, linked
  against the aggregate + the exact `LDADD`/platform `LDFLAGS` transcribed from the
  configured Makefile (byte-verified to reproduce the baseline's `LC_LOAD_DYLIB` order,
  `LC_BUILD_VERSION`, and `LC_RPATH`). Rpaths: `scilab-cli-bin` → `[/usr/lib, gcc]`;
  `scilab-bin` → `[/usr/lib, gcc, jdk-25/lib]`.
- All policy (flags, include order, `-std=c++17`, install_name, link classes, the fold vs
  link classification, drop-in targets, the JDK path) lives in `scilab/cmake/`; a policy
  change touches one file. `JAVA_HOME` is resolved once as `SCILAB_JAVA_HOME` in
  `ScilabToolchain.cmake` (config.status-first, `/usr/libexec/java_home` fallback with a
  warning), consumed by jvm + the JDK modules + the GUI aggregate's `-ljli`.

### End-to-end proof (2026-07-17)

1. **From-scratch build:** `rm -rf build-cmake` → configure + `drop-in-all` → all 64
   dylibs + 21 fold-in OBJECT libs + 2 aggregates + 2 executables built and dropped in,
   rc=0 (3668 steps).
2. **Whole-tree rpath-aware gate:** `PARITY OK` rc=0 (68 dylibs — the 64 module dylibs +
   both aggregates — and the 2 executables, all matching the autotools baseline incl.
   `LC_RPATH`) and `flagfacts_check` rc=0 (extended to the 21 fold-in modules).
3. **The real app on the CMake executable:** the CMake-built `scilab-cli-bin` (dropped
   into `.libs/`, UUID-matched to `build-cmake/scilab-cli-bin`) computed `1+1 → 2.`, a
   fold-in compute `sum([1 2 3 4 5]) → 15.` (elementary_functions/core), the interpolation
   gateway `splin` (dlopen-loaded), and resolved the `covStart` gateway — clean exit rc=0.
   CMake links a *working* app, not just a shape-matching one.
4. **The autotools build still works** — exercised during the pure-autotools re-baseline
   (`make clean && make` rebuilds the module dylibs + aggregates + executables; the
   rpath-aware baseline is captured from *that* independent autotools reference, not from
   the CMake tree).

## The parity harness is rpath-aware (Stage 1f-a)

`parity/fingerprint.py` parses `LC_RPATH` (ordered, from `otool -l`); `capture.py` records
it per dylib and per executable; `diff.py` compares it **order-significantly**. The
comparison is fault-injected in the unit suite (drop or reorder an rpath → parity fails,
naming the artifact). This closed the harness's rpath blind spot (a dropped or spurious
rpath previously passed `PARITY OK`; the jvm/JDK modules had been hand-checked). The
baseline (`baseline-autotools.json`) was re-captured rpath-aware from a pure-autotools
rebuild, so every Stage-1e dylib was re-checked against it — a free rpath regression sweep.

## CI

`.gitlab-ci.yml` (fork-native pipeline) carries two guards:

- **`sanity:cmake-driver`** (shared runner, every pipeline): a cheap wiring/manifest
  consistency check (no Mach-O needed) that mirrors the driver's two-tier structure —
  (A) every `add_subdirectory(modules/X)` across both `foreach` blocks has a real
  `modules/X/CMakeLists.txt`; (B) the dylib block equals the manifest's 46 dylib dirs;
  (C) the fold-in block equals the aggregate's `_scilab_fold_objects` set; (D) the
  manifest still holds exactly 64 dylib rows; (E) both aggregate + both executable calls
  are still declared — plus the parity-harness unit suite (`pytest build-parity/tests`,
  hermetic; the acceptance tests self-skip without a built tree).
- **`parity:cmake-drop-in`** (self-hosted macOS arm64 runner, rule-gated on
  `$SCILAB_NATIVE_RUNNER == "1"`): the real gate — `drop-in-all` + rpath-aware parity diff
  + flag facts on the built tree. Because the aggregates + executables ride `drop-in-all`
  and the capture fingerprints every `.libs/` artifact (incl. `LC_RPATH`), this job now
  gates the whole native app automatically. Set the project variable only while such a
  runner is registered; without it the job is not created (shared runners can neither build
  nor fingerprint Mach-O).

## Deferred (deliberately out of Stage 1f-a)

- **Stage 1f-b — the CMake→Ant bridge:** CMake invokes Ant for the 24 Java modules' jars
  (Stage 1 keeps Ant; the jars are byte-identical and deliberately not fingerprinted until
  Stage 2's Ant→Maven).
- **Stage 1f-c — help + retiring `configure`:** help generation stays a post-build step
  (it needs the running app); porting `configure`'s ~186 feature probes so CMake generates
  `machine.h`/`version.h` (spec §11) is its own stage, provable by the same harness (both
  headers are fingerprinted).
- **Machine-specific absolute paths:** the calls transcribe `config.status`-faithful
  absolute paths (the jdk-25 lib dir, the Xcode SDK, Homebrew Cellar dirs, the miniconda
  FLIBS lib dir, the from-source `xlnt-prefix`). These are parity-neutral but pin the CMake
  build + native CI gate to this machine's layout; the de-autotools driver should derive
  them from the active toolchain (`xcrun --show-sdk-path`, `brew --prefix`, the configured
  JDK).
- **C++ standard bump (spec §12):** the tree is held at `-std=c++17` to match the baseline;
  the c++23 bump is a codegen axis — bump autotools first, re-baseline, then flip **one
  line** in `ScilabModule.cmake`.
- **The `_CFLAGS`-replaces-`AM_CFLAGS` footgun:** a handful of dirs (`parameters`,
  `windows_tools`, `string/src/c`) and 6 Fortran files compile at `-O0` in the baseline
  because a per-target `_CFLAGS` silently drops the tree's `-O2 -fwrapv`. CMake reproduces
  this faithfully (the flag-fact check's `FILE_`/`DIR_EXPECTED_OVERRIDES`); the actual fix
  (restore the optimization, re-baseline) is a deliberate later improvement, not a silent
  1f-a change.
