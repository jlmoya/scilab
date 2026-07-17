# Stage 1f-a — Native Aggregate + Executables under CMake — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `cmake --build … --target drop-in-all` produce the `libscilab`/`libscilab-cli` aggregate + the `scilab-bin`/`scilab-cli-bin` executables, each byte-shape-identical (incl. LC_RPATH) to autotools, so CMake is the master of the native build end to end.

**Architecture:** Extend the Stage-1e helper one level up: the 23 fold-in core modules become CMake OBJECT libraries; a new `scilab_aggregate()` folds them + links the 46 Stage-1e dylibs (+ jvm/GUI/system libs) into `libscilab`/`libscilab-cli`; a new `scilab_executable()` links the two executables. Same drop-in discipline; the parity harness — now rpath-aware — is the arbiter.

**Tech Stack:** CMake ≥3.20 (Makefile/Ninja), Apple clang 21 (`gcc`/`g++`), gfortran, the Python parity harness (`scilab/build-parity/`).

**Spec:** `docs/superpowers/specs/2026-07-16-stage1f-a-aggregate-executables-design.md`.

## Global Constraints

- **The rpath-aware parity harness is the arbiter.** Every dylib (68) + both executables must be PARITY OK vs the re-captured `baseline-autotools.json`, now including `LC_RPATH` (order-significant). Hold C++ `-std=c++17`, C `-std=gnu23`, Fortran per SCI_FFLAGS.
- **Drop-in coexistence:** CMake builds the aggregate/executables and drops them into `modules/.libs/libscilab*.dylib` + `.libs/scilab-*-bin`; the autotools build stays fully recoverable (`make clean && make`). NO changes to `configure.ac`, any `Makefile.am`, or generated code (only harness + CMake files).
- Per-artifact identity exact: aggregate filenames `libscilab.2027.dylib` / `libscilab-cli.2027.dylib` + symlinks, install_names `/usr/local/lib/scilab/…`; executables `scilab-bin` / `scilab-cli-bin` with their exact `LC_BUILD_VERSION` (minos/sdk) + rpaths.
- Reproduce the aggregate `LIBADD` classes exactly: the 46 pkglib module `.la`s are **linked** (recorded deps); the fold-in module objects are **folded** (no dep). `libscilab` = 60 deps, `libscilab-cli` = 40 deps.
- macOS reality: build only what the autotools macOS build compiles — `mpi` is inert (skip); `javasci` builds the third-party `libjavasci2` (special-case, already a separate baseline artifact); `windows_tools`/`mexlib`/`fftw` build small convenience libs.
- NO AI-attribution trailers. Commit on `main`. Controller pushes. Headless; no `touch` of missing paths; no screen-capture. syspolicyd note: under load `rc 137`/ad-hoc-load denials → `sudo pkill -9 syspolicyd` + inode refresh.

## File Structure

- `scilab/build-parity/parity/fingerprint.py` — add `parse_rpaths(otool_l_output) -> list[str]`.
- `scilab/build-parity/parity/capture.py` — record `rpaths` per dylib + per executable.
- `scilab/build-parity/parity/diff.py` — compare `rpaths` (order-significant); CLI exit on mismatch.
- `scilab/build-parity/tests/` — rpath parser + fault-injection tests.
- `scilab/build-parity/baseline-autotools.json` — re-captured, rpath-aware (Task 2).
- `scilab/cmake/ScilabAggregate.cmake` — `scilab_aggregate()` + `scilab_executable()` helpers.
- `scilab/cmake/ScilabToolchain.cmake` — add `SCILAB_JAVA_HOME` (Task 6).
- `scilab/modules/<foldin>/CMakeLists.txt` — 22 fold-in OBJECT-lib declarations (mpi skipped).
- `scilab/modules/CMakeLists.txt` (new) or `scilab/CMakeLists.txt` — the aggregate + executable targets + their drop-in.
- `docs/design/build-cmake-driver.md` + `.gitlab-ci.yml` — updated (Task 7).

---

### Task 1: LC_RPATH in the parity fingerprint

**Files:** Modify `scilab/build-parity/parity/fingerprint.py`, `capture.py`, `diff.py`; Test `scilab/build-parity/tests/test_rpath.py`.

**Interfaces:**
- Produces: `parse_rpaths(text: str) -> list[str]` (ordered LC_RPATH paths from `otool -l` output); each dylib + executable fingerprint gains an `"rpaths": [...]` key; `diff_fingerprints` reports an rpath mismatch as a difference. Consumed by Tasks 2/4/5 + the whole-tree gate.

- [ ] **Step 1: Write the failing test.**

```python
# scilab/build-parity/tests/test_rpath.py
from parity.fingerprint import parse_rpaths

OTOOL_L = """\
Load command 12
      cmd LC_RPATH
  cmdsize 32
     path /usr/lib (offset 12)
Load command 13
      cmd LC_RPATH
  cmdsize 56
     path /opt/homebrew/opt/gcc/lib/gcc/current (offset 12)
"""

def test_parse_rpaths_ordered():
    assert parse_rpaths(OTOOL_L) == ["/usr/lib", "/opt/homebrew/opt/gcc/lib/gcc/current"]

def test_parse_rpaths_empty():
    assert parse_rpaths("Load command 0\n cmd LC_SEGMENT_64\n") == []
```

- [ ] **Step 2: Run it — fails** (`parse_rpaths` undefined). `cd scilab/build-parity && python3 -m pytest tests/test_rpath.py -q` → FAIL.

- [ ] **Step 3: Implement `parse_rpaths`** in `fingerprint.py`:

```python
def parse_rpaths(otool_l_output):
    """Ordered LC_RPATH paths from `otool -l <dylib>`. Order is significant (dyld
    searches rpaths in order). Strips the trailing '(offset N)' otool annotation."""
    import re
    rpaths, in_rpath = [], False
    for line in otool_l_output.splitlines():
        s = line.strip()
        if s.startswith("cmd LC_RPATH"):
            in_rpath = True
        elif in_rpath and s.startswith("path "):
            rpaths.append(re.sub(r"\s*\(offset \d+\)\s*$", "", s[len("path "):]).strip())
            in_rpath = False
    return rpaths
```

- [ ] **Step 4: Wire it into capture + diff.** In `capture.py`, when fingerprinting each dylib AND each executable, run `otool -l <path>` and store `"rpaths": parse_rpaths(out)`. In `diff.py`, in the per-dylib and per-executable comparison, add `rpaths` to the compared fields (report `f"{name}: rpaths {base} != {cand}"` on mismatch). Add a fault-injection test mirroring the existing `test_sensitivity_*`: mutate a captured fingerprint's `rpaths` (drop one) → `diff_fingerprints(...)["ok"] is False`, naming rpaths.

- [ ] **Step 5: Run the suite.** `cd scilab/build-parity && python3 -m pytest -q` → all pass (incl. the new rpath tests). Expected: the acceptance tests still green (the current tree recapture is self-consistent).

- [ ] **Step 6: Commit.** `git add scilab/build-parity/parity/*.py scilab/build-parity/tests/test_rpath.py && git commit -m "build-parity: fingerprint LC_RPATH (order-significant) for dylibs + executables"`

---

### Task 2: Re-baseline from a pure-autotools rebuild (rpath-aware)

**Files:** Modify `scilab/build-parity/baseline-autotools.json` (re-captured). No code.

**Interfaces:** Consumes Task 1's rpath-aware capture. Produces the new independent baseline that Tasks 4/5 gate against.

- [ ] **Step 1: Restore the pure-autotools native tree.** `cd scilab && make clean && make` (rebuilds the autotools module dylibs + aggregate + executables; ~30–60 min). Confirm rc=0 and `./bin/scilab-cli -nwni -e "disp(1+1);exit(0)"` → `2.` (autotools app works).

- [ ] **Step 2: Capture the rpath-aware baseline from the autotools tree.**
```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/base-rpath.json baseline
```
Expected: 68 dylibs, 2 executables, each now with an `rpaths` key.

- [ ] **Step 3: Sanity-check the new baseline has rpaths.**
```bash
python3 -c "import json;b=json.load(open('/tmp/base-rpath.json'));import sys;assert all('rpaths' in d for d in b['dylibs'].values());assert all('rpaths' in e for e in b['executables'].values());print('rpaths present on all', len(b['dylibs']),'dylibs +',len(b['executables']),'exes')"
cp /tmp/base-rpath.json baseline-autotools.json
```

- [ ] **Step 4: Rebuild the CMake native tree + confirm PARITY OK against the new baseline** (proves the Stage-1e dylibs' rpaths were right all along — a free regression sweep).
```bash
cd .. && cmake --build build-cmake --target drop-in-all -j >/dev/null
cd build-parity && python3 -m parity.capture .. /tmp/t2.json cand && python3 -m parity.diff baseline-autotools.json /tmp/t2.json; echo "rc=$?"
```
Expected: `PARITY OK` rc=0 (the 64 CMake module dylibs match, incl. rpaths; the aggregate + executables are still autotools here and match themselves).

- [ ] **Step 5: Commit the new baseline.** `git add scilab/build-parity/baseline-autotools.json && git commit -m "build-parity: re-baseline with LC_RPATH from a pure-autotools rebuild (Stage-1f-a reference)"`

---

### Task 3: The 23 fold-in core modules → CMake OBJECT libraries

**Files:** Create `scilab/modules/<m>/CMakeLists.txt` for the 22 non-inert fold-in modules; modify `scilab/CMakeLists.txt` (register them). Test: the flag-fact check + compile.

**Interfaces:** Produces one OBJECT-lib target `sci<m>-obj` per fold-in module (no dylib, no drop-in), consumed by Task 4's aggregate via `$<TARGET_OBJECTS:sci<m>-obj>`.

- [ ] **Step 1: Enumerate the exact fold-in set + each module's objects.** For each of `api_scilab boolean cacsd core data_structures dynamic_link elementary_functions fftw fileio integer io linear_algebra mexlib output_stream parameters polynomials sparse string threads time windows_tools` (mpi SKIPPED — macOS-inert; javasci handled in Step 4): read `modules/<m>/Makefile.am` for its `.la` source lists (there may be several convenience `.la`s per module — e.g. `libsci<m>-algo.la` + a gateway `.la`; all their objects fold in). Extract sources by language + the `libsci<m>_la_CPPFLAGS` include set (the Stage-1e procedure).

- [ ] **Step 2 (worked example — repeat per module): `elementary_functions`.**
```bash
cd scilab && grep -E '_la_SOURCES *=|_la_CPPFLAGS' modules/elementary_functions/Makefile.am | head
```
Write `modules/elementary_functions/CMakeLists.txt` using a new thin wrapper `scilab_object_module(<name> ...)` (added to `ScilabModule.cmake` in this task — it reuses the per-language flag/include machinery but emits ONLY an OBJECT library `sci<name>-obj`, no SHARED lib, no drop-in):
```cmake
scilab_object_module(elementary_functions
  SOURCES <all the module's .la sources, by language, from Makefile.am>
  LANG C Fortran CXX
  EXTRA_INCLUDES <the module's non-default -I set>)
```

- [ ] **Step 3: Repeat for every fold-in module.** `javasci` special case: its native output is `libjavasci2` (a distinct baseline third-party artifact, currently prebuilt/OUT of the 64) — build only its fold-in objects that land in `libscilab` (check `otool -L modules/.libs/libscilab.2027.dylib | grep -c javasci` and the `ENGINE_LIBS`/`OTHER_LIBS` membership; if javasci contributes only via `libjavasci2` which is a separate dep, it needs no fold-in OBJECT lib — document the finding). Register all fold-in modules in the driver.

- [ ] **Step 4: Build the OBJECT libs + verify flag-facts.**
```bash
cd scilab && cmake -S . -B build-cmake >/dev/null && cmake --build build-cmake --target sci-foldin-all -j 2>&1 | tail -3
cd build-parity && python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: all fold-in objects compile; flag-facts rc=0 (the fold-in TUs now appear + pass, incl. any colnew.f-class per-file exception the harness already knows). (`sci-foldin-all` is an aggregating custom target of the OBJECT libs, added in this task for the build check.)

- [ ] **Step 5: Commit.** `git add scilab/modules/<foldin>/CMakeLists.txt scilab/cmake/ScilabModule.cmake scilab/CMakeLists.txt && git commit -m "cmake: 22 fold-in core modules as OBJECT libraries (mpi inert; javasci documented)"`

---

### Task 4: `scilab_aggregate()` → `libscilab` + `libscilab-cli`

**Files:** Create `scilab/cmake/ScilabAggregate.cmake`; create/modify the aggregate CMakeLists (`scilab/modules/CMakeLists.txt` or a block in `scilab/CMakeLists.txt`); modify `scilab/CMakeLists.txt` (include the helper + register the drop-in). Test: harness.

**Interfaces:** Produces `scilab_aggregate(NAME <libscilab|libscilab-cli> FOLD_OBJECTS <sci*-obj…> LINK_MODULES <sci*…> SYSTEM_LIBS … FRAMEWORKS … CLASS aggregate)` → a SHARED target with the exact filename/install_name/rpaths + a `drop-in-<name>` target (into `modules/.libs/`). Consumed by Task 5 (executables link it).

- [ ] **Step 1: Extract each aggregate's exact LIBADD breakdown.** From `modules/Makefile.am`: `libscilab_la_LIBADD = $(ENGINE_LIBS) + jvm + $(GUI_LIBS) + $(NO_GUI_LIBS) + $(OTHER_LIBS) + $(EXTERNAL_LIBS)`; `libscilab_cli_la_LIBADD = $(ENGINE_LIBS) + $(NO_GUI_LIBS) + $(OTHER_LIBS) + $(EXTERNAL_LIBS) + $(FLIBS)`. Expand each `*_LIBS` var (resolve which module `.la`s are pkglib=linked vs noinst=folded) + the system libs. Ground truth for the RESULT: `otool -L modules/.libs/libscilab.2027.dylib` (60 deps) and `libscilab-cli.2027.dylib` (40 deps) — the CMake aggregate must record exactly these.

- [ ] **Step 2: Write `scilab_aggregate()`** in `ScilabAggregate.cmake` — a SHARED target that: adds `$<TARGET_OBJECTS:…>` for each fold-in OBJECT lib in `FOLD_OBJECTS`; `target_link_libraries` the `LINK_MODULES` (the pkglib module targets — CMake records their install_names) + `SYSTEM_LIBS`/`FRAMEWORKS`; forces `OUTPUT_NAME`/`SUFFIX`/`INSTALL_NAME_DIR`/rpaths (reuse the Stage-1e identity rules); `LINKER_LANGUAGE CXX`; drop-in into `modules/.libs/`.

- [ ] **Step 3: Declare the two aggregates** (GUI folds+links its set incl. jvm+GUI_LIBS; CLI its set incl. NO_GUI_LIBS+FLIBS, no GUI). Build + drop in: `cmake --build build-cmake --target drop-in-libscilab drop-in-libscilab-cli`.

- [ ] **Step 4: Gate — PARITY OK for both aggregates.**
```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/t4.json cand && python3 -m parity.diff baseline-autotools.json /tmp/t4.json; echo "rc=$?"
```
Expected: `PARITY OK` rc=0. `libscilab.VER.dylib` (60 deps) + `libscilab-cli.VER.dylib` (40 deps) match the baseline field-for-field — exact dep set (linked modules recorded, folded modules absent), symbols, install_name, rpaths. If a fold-in module shows as a dep (wrongly linked) or a linked module is missing, fix the FOLD/LINK split.

- [ ] **Step 5: Commit.** `git add scilab/cmake/ScilabAggregate.cmake scilab/modules/CMakeLists.txt scilab/CMakeLists.txt && git commit -m "cmake: scilab_aggregate() — libscilab + libscilab-cli, parity-proven (rpath-aware)"`

---

### Task 5: `scilab_executable()` → `scilab-bin` + `scilab-cli-bin`

**Files:** Add `scilab_executable()` to `ScilabAggregate.cmake`; modify the top-level CMakeLists (declare the two executables + their drop-in). Test: harness + app-run.

**Interfaces:** Produces `scilab_executable(NAME <scilab-bin|scilab-cli-bin> SOURCES … LINK <scilab_aggregate target> LDADD … LDFLAGS …)` → an executable with the exact `LC_BUILD_VERSION` + rpaths + a `drop-in-<name>` (into `.libs/`).

- [ ] **Step 1: Extract the executable link.** `scilab_cli_bin_SOURCES = modules/startup/src/cpp/scilab.cpp` (+ `initMPI.c` only if MPI — inert on macOS, so likely just scilab.cpp); `scilab_cli_bin_LDADD = $(COMMON_LIBS)[=libintl] $(top_builddir)/modules/libscilab-cli.la $(BLAS_LIBS) …`; the macOS `LDFLAGS` (`-Wl,-platform_version,macos,<min>,<sdk>`, the undefined policy, min-macos). `scilab-bin` (GUI) is the same source linking `libscilab` + the GUI LDADD. Ground truth: `otool -L .libs/scilab-cli-bin` + `otool -l .libs/scilab-cli-bin` (deps, LC_BUILD_VERSION, rpaths).

- [ ] **Step 2: Write `scilab_executable()`** + declare both. The executable's install_name field (executables have no `LC_ID_DYLIB`) — the harness compares the first recorded dep as the "install_name" slot; match the autotools link order. Force `LC_BUILD_VERSION` via `CMAKE_OSX_DEPLOYMENT_TARGET`/`-Wl,-platform_version`. Build + drop in.

- [ ] **Step 3: Gate — PARITY OK for both executables (incl. rpaths + build_version).**
```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/t5.json cand && python3 -m parity.diff baseline-autotools.json /tmp/t5.json; echo "rc=$?"
```
Expected: `PARITY OK` rc=0 — `scilab-bin` + `scilab-cli-bin` match: deps, `build_version` (minos/sdk), rpaths.

- [ ] **Step 4: End-to-end app run on the CMake-built `scilab-cli-bin`** (the behavioral acceptance):
```bash
cd scilab && ./bin/scilab-cli -nwni -e "disp(1+1); disp(sum([1 2 3])); x=[0 1 2 3];y=[0 1 4 9];disp(interp(1.5,x,y,splin(x,y))); exit(0)"
# UUID of the dropped-in exe must equal the build-cmake artifact (locate it: find build-cmake -name scilab-cli-bin):
dwarfdump --uuid .libs/scilab-cli-bin | awk '{print $2}'; dwarfdump --uuid "$(find build-cmake -name scilab-cli-bin -type f | head -1)" | awk '{print $2}'
```
Expected: `2.`, `6.` (sum → elementary_functions/core, a fold-in module), `2.25` (interp → interpolation gateway); UUID confirms the running binary is the CMake build.

- [ ] **Step 5: Commit.** `git add scilab/cmake/ScilabAggregate.cmake scilab/CMakeLists.txt && git commit -m "cmake: scilab_executable() — scilab-bin + scilab-cli-bin, parity-proven + app runs on the CMake executable"`

---

### Task 6: Hoist JAVA_HOME to the toolchain

**Files:** Modify `scilab/cmake/ScilabToolchain.cmake` (add `SCILAB_JAVA_HOME`); `scilab/modules/{jvm,external_objects_java,types,xcos}/CMakeLists.txt` (consume it).

**Interfaces:** Produces `SCILAB_JAVA_HOME` (config.status-first, `/usr/libexec/java_home` fallback with a warning). Consumed by the 4 JDK modules.

- [ ] **Step 1: Add `SCILAB_JAVA_HOME`** to `ScilabToolchain.cmake`: parse `S["JAVA_HOME"]` from `${SCILAB_SOURCE_DIR}/config.status`; fallback `execute_process(/usr/libexec/java_home)` with `message(WARNING …may differ from the configured JDK…)`.
- [ ] **Step 2: Replace the duplicated JAVA_HOME resolution** in the 4 JDK module CMakeLists with `${SCILAB_JAVA_HOME}`.
- [ ] **Step 3: Rebuild + gate.** `cmake --build build-cmake --target drop-in-all` then the whole-tree harness → **PARITY OK** rc=0 (the 4 JDK modules' `@rpath/libjli` deps unchanged; parity-neutral refactor). Manually confirm the JDK modules' rpaths unchanged (`otool -l`).
- [ ] **Step 4: Commit.** `git add scilab/cmake/ScilabToolchain.cmake scilab/modules/{jvm,external_objects_java,types,xcos}/CMakeLists.txt && git commit -m "cmake: hoist JAVA_HOME into ScilabToolchain (SCILAB_JAVA_HOME) — DRY the 4 JDK modules"`

---

### Task 7: Finalize — full clean build, docs, CI

**Files:** Modify `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml`.

- [ ] **Step 1: Full clean build of the whole native app + the final gate.**
```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && cmake --build build-cmake --target drop-in-all -j
cd build-parity && python3 -m parity.capture .. /tmp/final.json cand && python3 -m parity.diff baseline-autotools.json /tmp/final.json && python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: **PARITY OK** (68 dylibs + 2 aggregates already among them + 2 executables, rpath-aware) + flag-facts rc=0.

- [ ] **Step 2: App-run acceptance** (the CMake `scilab-cli-bin`): `./bin/scilab-cli -nwni -e "disp(1+1); exit(0)"` → `2.`.
- [ ] **Step 3: Update `docs/design/build-cmake-driver.md`:** Stage 1f-a done — the aggregate + executables build under CMake; the harness is rpath-aware; JAVA_HOME hoisted; still-deferred = Ant bridge (1f-b), help + retire-configure (1f-c), c++23 (§12).
- [ ] **Step 4: Update `.gitlab-ci.yml`:** the native gate now also covers the aggregate + executables + rpath (automatic once the drop-in-all target includes them); the `sanity:cmake-driver` completeness check extends to the aggregate/executable targets.
- [ ] **Step 5: Commit.** `git add docs/design/build-cmake-driver.md .gitlab-ci.yml && git commit -m "cmake: Stage-1f-a complete — CMake builds the whole native app (aggregate + executables) at rpath-aware parity"`

---

## Notes for the executor

- **The harness is the test.** Each task's done = PARITY OK (rpath-aware) + flag-facts. Only Task 1 writes unit tests (the rpath parser + fault-injection).
- **Task 2 is a one-time slow step** (the autotools rebuild). Run it once, commit the baseline; everything after gates against it.
- **The fold-in/aggregate/executable data is extracted per-artifact from the configured Makefiles** (the Stage-1e procedure) — the plan gives the helper interfaces, the LIBADD/LDADD structure, the exact dep counts (60/40) as the parity target, and the extraction commands; the per-module source/flag lists live in the Makefiles.
- **Reproduce, don't improve.** Keep the executables' platform LDFLAGS byte-shape-faithful; the point of 1f-a is proving CMake makes the SAME app. Improvements (deriving machine paths, c++23) are later stages.
