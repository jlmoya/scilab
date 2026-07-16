# The CMake native-build driver (Stage 1e — complete)

**Status:** DONE — verified end-to-end 2026-07-16 (clean build → whole-tree PARITY OK → real app
runs on the CMake dylibs).
**What it is:** the top-level `scilab/CMakeLists.txt` + `scilab/cmake/ScilabModule.cmake` that build
**all 64 baseline native module dylibs** (46 module directories) under CMake and drop them into the
autotools `.libs/` layout, matching the autotools build in exported symbols, link/dependency shape,
install_name, and compiler flag-facts (arbitrated by the parity harness). This is *not*
byte-for-byte identity — a fresh compile carries a distinct Mach-O UUID by design; what the harness
proves is behavioral/link-shape equivalence, not identical bytes. Strategy context:
`docs/design/build-cmake-maven-migration.md` (Stage 1e); design spec:
`docs/superpowers/specs/2026-07-16-stage1e-cmake-driver-design.md`; authoritative module list:
`scilab/cmake/stage1e-manifest.md`.

This is **hybrid coexistence**, not a cutover: autotools still configures the tree, links the
executables and aggregate libs, runs Ant for the jars, and builds help. CMake owns the native module
dylibs only; the CMakeLists files are invisible to automake, so the autotools path is untouched and
rollback is free.

## Usage

```bash
# 0. Prerequisite — an autotools-CONFIGURED (and, for the full parity gate, BUILT) tree:
#    config.status, modules/core/includes/machine.h + version.h must exist.
cd scilab && ./configure <usual flags> && make        # see docs/design/build-modernization.md

# 1. Configure the CMake build (Makefile/Ninja generators only).
#    Fortran must be Homebrew gfortran; if a stray flang wins, pass
#    -DCMAKE_Fortran_COMPILER=gfortran (the driver hard-fails otherwise).
cmake -S . -B build-cmake

# 2. Build all 64 module dylibs + drop each into its modules/<m>/.libs/
cmake --build build-cmake --target drop-in-all -j     # or drop-in-<name> per module

# 3. The gate — parity vs the committed autotools baseline + per-TU flag facts
cd build-parity
python3 -m parity.capture .. /tmp/cand.json cand
python3 -m parity.diff baseline-autotools.json /tmp/cand.json          # PARITY OK, rc=0
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json # rc=0
```

Measured on the dev machine (M-series, `-j`): configure ≈ 46 s, full clean build of all 64 dylibs
6 m 27 s, capture ≈ 1 min. `rm -rf build-cmake` first for a truly clean proof.

**Rollback / recovering the autotools dylib(s)** — each drop-in is a plain file copy, so the
autotools output is always one rebuild away, and recovery is **per module dir**. A module dir may
emit more than one dylib (the `-disable`/`-cli`/`-minimal` variant twins; `types` →
`libscitypes-java`; `differential_equations` → the main dylib **plus** the vendored
`libscisundials`), so recover by rebuilding the whole directory — it restores *all* of that dir's
dylibs at once: `make -C modules/<m> clean && make -C modules/<m>`. (A bare `make -C modules/<m>`
without `clean` may *not* relink when the `.la` targets are already newer than their sources — the
`clean` forces it. To relink just one specific dylib without a full clean, delete that exact
`modules/<m>/<its-libtool>.la` — e.g. `libscisundials.la` — and re-`make` the directory.)

## What is in scope (and proven)

- The 64 baseline module dylibs across 46 module dirs — the four exemplars plus batches A–E
  (simple leaves, variant pairs, external-dep modules, the jvm JNI module, and the
  inter-module-edge modules incl. the vendored `libscisundials`). Per-dylib rows, external deps,
  and module edges: `scilab/cmake/stage1e-manifest.md`.
- Every module is a one-call `scilab_module()` data declaration; **all** policy (flags, include
  order, `-std=c++17`, install_name, link classes, drop-in targets) lives in
  `scilab/cmake/ScilabModule.cmake`. A policy change touches one file.
- `drop-in-all` aggregates the per-module `drop-in-<name>` targets; each copies the dylib (+ its
  unversioned symlink) into `modules/<name>/.libs/`.

### End-to-end proof (2026-07-16)

1. Clean build: `rm -rf build-cmake` → configure + `drop-in-all` → all 64 dylibs built and
   dropped in, rc=0.
2. Whole-tree gate: `PARITY OK` rc=0 (68 dylibs, 2 executables, 4 generated files captured;
   the 64 module dylibs all match the autotools baseline) and `flagfacts_check` rc=0.
3. The real app on the CMake dylibs: `bin/scilab-cli` (autotools-linked, loads the module dylibs
   from `.libs/` by install_name) computed `1+1 → 2.`, ran `splin`/`interp` (dlopen-loaded
   interpolation module → 2.25 exact) and `covStart`/`covStop` (coverage module), all rc=0 —
   while all 64 `.libs/` dylibs matched the `build-cmake/` artifacts by Mach-O UUID (64/64,
   `dwarfdump --uuid`), and an autotools relink of the same module produced a *different* UUID.
   The app demonstrably ran on CMake-built code.

## CI

`.gitlab-ci.yml` (fork-native pipeline) carries two guards:

- **`sanity:cmake-driver`** (shared runner, every pipeline): the driver's `foreach` module list,
  the set of `modules/*/CMakeLists.txt`, and the manifest's module dirs must be identical, and the
  manifest must hold exactly 64 dylib rows; plus the parity-harness unit suite
  (`pytest build-parity/tests` — hermetic; the acceptance tests self-skip without a built tree).
- **`parity:cmake-drop-in`** (self-hosted macOS arm64 runner, rule-gated on
  `$SCILAB_NATIVE_RUNNER == "1"`): the real gate — `drop-in-all` + parity diff + flag facts on the
  built tree. Set the project variable only while such a runner is registered; without it the job
  is simply not created (shared runners can neither build nor fingerprint Mach-O).

## Deferred (deliberately out of Stage 1e)

- **Stage 1f — cutover surface:** linking `scilab`/`scilab-cli` and the aggregate
  `libscilab`/`libscilab-cli` under CMake; the CMake→Ant bridge (jars); help generation; retiring
  the autotools native path. Also carries the two recorded follow-ups: add **LC_RPATH** to the
  parity fingerprint + re-baseline (rpath is harness-blind today — a dropped/spurious rpath passes
  PARITY OK; jvm/JDK modules were hand-checked), and hoist the **JAVA_HOME** discovery from
  `modules/jvm/CMakeLists.txt` into the shared driver variable (`SCILAB_JAVA_HOME`).
- **Generated headers (spec §11):** `machine.h`/`version.h` stay `configure`-generated; CMake
  `-I`s at them. Porting configure's ~186 feature probes to CMake is its own stage, provable by
  the same harness (both headers are fingerprinted).
- **C++ standard bump (spec §12):** the tree is held at `-std=c++17` to match the baseline; the
  c++23 bump is a codegen axis — bump autotools first, re-baseline, then flip **one line** in
  `ScilabModule.cmake`.
- Jar parity semantics (Stage 2, Ant→Maven — jars are byte-identical in Stage 1 and deliberately
  not fingerprinted).
