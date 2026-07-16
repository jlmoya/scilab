# Stage 1e — Top-level CMake Driver (native drop-in) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build every native Scilab module dylib the parity harness tracks via one top-level CMake driver + one shared `scilab_module()` helper, dropped into `.libs/` byte-shape-identical to the autotools build.

**Architecture:** A `scilab/CMakeLists.txt` `add_subdirectory()`s each in-scope module; each module's `CMakeLists.txt` is a single `scilab_module(...)` data declaration; `scilab/cmake/ScilabModule.cmake` owns all policy; a `drop-in-all` target copies each built dylib into its `.libs/`. Autotools stays intact — it still links the executables, runs Ant, and builds help, consuming the CMake-built dylibs. The parity harness (`scilab/build-parity/`) is the gate for every module.

**Tech Stack:** CMake ≥ 3.20 (Makefile/Ninja generator), Apple clang 21 (`gcc`/`g++` aliases), gfortran (Homebrew gcc), the existing Python parity harness (`build-parity/`, pytest).

**Design doc:** `docs/design/build-cmake-maven-migration.md` + spec `docs/superpowers/specs/2026-07-16-stage1e-cmake-driver-design.md`.

## Global Constraints

- **The parity harness is the arbiter.** Every module's dropped-in dylib must make the whole-tree diff `PARITY OK` vs the committed `scilab/build-parity/baseline-autotools.json` (symbols, deps, install_name, SDK stamp). Scope = the **64 module dylibs** in that baseline (the 2 aggregates `libscilab`/`libscilab-cli` and the 2 third-party dylibs `libjavasci2`/`libxlnt` are OUT — autotools/prebuilt).
- **Hold C++ at `-std=c++17`** to match the baseline (the c++23 bump is a deferred separate axis — spec §12; do NOT raise it here).
- **Zero changes** to `configure.ac`, any `Makefile.am`, or generated code. Only add `CMakeLists.txt` / `cmake/*.cmake` / `build-parity/*` files. The autotools build must stay fully functional (rollback path).
- **Per-module dylib identity, exact:** filename `libsci<name>.2027.dylib` (+ `libsci<name>.dylib` symlink) via `OUTPUT_NAME "sci<name>.2027" + SUFFIX ".dylib"` (never CMake `VERSION`); install_name `/usr/local/lib/scilab/libsci<name>.2027.dylib` (`INSTALL_NAME_DIR` + `BUILD_WITH_INSTALL_NAME_DIR TRUE`); `DEFINE_SYMBOL ""`.
- **Link policy:** `-undefined dynamic_lookup -no_fixup_chains`; `LINKER_LANGUAGE CXX` if any C++/Fortran source present; the two `SCI_LDFLAGS` rpaths (`/usr/lib`, `/opt/homebrew/opt/gcc/lib/gcc/current`). No `-fvisibility=hidden`.
- **Convenience `-algo` lib → CMake OBJECT library** (never STATIC — drops unreferenced symbols).
- **System libs in `/usr/lib`** linked explicitly (`-lxml2 …`), NOT `find_package` (Homebrew-shadow trap). Homebrew-keg deps (OpenMP) via `find_package`. The recorded dep must match the baseline path exactly.
- **Linking class is per-module** — check `modules/Makefile.am` (`ENGINE_LIBS` vs `ENGINE_LIBS_DYNAMIC_LOAD`); it only selects the correct header comment. Never copy a linking story between modules.
- **NO AI-attribution trailers** in commits. Commit on `main`. Controller pushes (batched). Headless only; never `touch` non-existent paths; never screen-capture.
- **syspolicyd note:** under load, macOS may SIGKILL execs (rc 137) / deny ad-hoc dylib loads; `sudo pkill -9 syspolicyd` + a content-identical inode refresh clears it (touches no repo content).

## File Structure

- `scilab/cmake/ScilabModule.cmake` — **the helper.** Defines `scilab_module()`; owns every policy above. One responsibility: turn a data declaration into a parity-true dylib target + its drop-in.
- `scilab/cmake/ScilabToolchain.cmake` — shared discovery (source root, the default Scilab include base, the SDK/Homebrew paths, gfortran selection). Kept separate so the helper stays about *policy*, this about *environment*.
- `scilab/CMakeLists.txt` — **the driver.** `project()`, includes the two cmake modules, `add_subdirectory()` per in-scope module, defines `drop-in-all`. One responsibility: enumerate + aggregate.
- `scilab/modules/<m>/CMakeLists.txt` — per module: one (or two, for a variant) `scilab_module(...)` call(s). Nothing else.
- `scilab/build-parity/parity/flagfacts_check.py` + `tests/test_flagfacts_check.py` — the new per-module flag-fact check (§Task 3).
- `scilab/cmake/stage1e-manifest.md` — the enumerated scope: each of the 64 baseline dylibs → (module dir, main/variant, class, languages, external deps, module deps, symbol count). Produced in Task 4; consumed by Tasks 5–9.
- `docs/design/build-cmake-driver.md` — the `cmake --build … --target drop-in-all` usage + CI note (Task 10).

The 4 exemplars already have hand-written `scilab/modules/{sound,parallel,coverage,interpolation}/CMakeLists.txt` (commits `38e81564f3f`, `f3d3a58fade`, `6b43d012ae3`, `531436d485a`) — Tasks 1–2 replace them with `scilab_module()` calls.

---

### Task 1: `scilab_module()` helper + driver skeleton, proven on `sound`

**Files:**
- Create: `scilab/cmake/ScilabModule.cmake`, `scilab/cmake/ScilabToolchain.cmake`, `scilab/CMakeLists.txt`, `scilab/.gitignore` (add `/build-cmake/`)
- Modify (replace body with a `scilab_module()` call): `scilab/modules/sound/CMakeLists.txt`
- Test (the gate): `scilab/build-parity/` harness against `baseline-autotools.json`

**Interfaces:**
- Produces: `scilab_module(<name> [ALGO_SOURCES ...] GATEWAY_SOURCES ... [LANG C|CXX|Fortran ...] [SYSTEM_LIBS ...] [FIND_PACKAGES ...] [MODULE_DEPS ...] [EXTRA_INCLUDES ...] [CLASS ENGINE_LIBS|DYNAMIC_LOAD] [SYMBOLS <n>])`. Creates target `sci<name>` (+ `sci<name>-algo` OBJECT lib when `ALGO_SOURCES` given) and a `drop-in-<name>` custom target; registers `drop-in-<name>` as a dependency of the global `drop-in-all`. Consumed by every later task.
- Consumes: the default Scilab include base + source-root resolution from `ScilabToolchain.cmake`.

- [ ] **Step 1: Establish the gate command (the "failing test")** — with no CMake driver yet, capture the baseline delta by rebuilding `sound` the autotools way, then assert the harness is green, so any regression in later steps is attributable.

Run:
```bash
cd scilab && make -C modules/sound >/dev/null 2>&1
cd build-parity && python3 -m parity.capture .. /tmp/t1-pre.json pre && python3 -m parity.diff baseline-autotools.json /tmp/t1-pre.json
```
Expected: `PARITY OK` (autotools tree matches baseline — the starting point).

- [ ] **Step 2: Write `ScilabToolchain.cmake`** (environment discovery).

```cmake
# scilab/cmake/ScilabToolchain.cmake — shared environment discovery (NOT policy).
# SCILAB_SOURCE_DIR: the configured autotools source tree (has the generated
# machine.h/version.h). Default to this file's ../.. ; overridable via -D.
if(NOT DEFINED SCILAB_SOURCE_DIR)
  get_filename_component(SCILAB_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
endif()
if(NOT EXISTS "${SCILAB_SOURCE_DIR}/modules/core/includes/machine.h")
  message(FATAL_ERROR "machine.h not found under ${SCILAB_SOURCE_DIR}; run ./configure there first.")
endif()
set(CMAKE_OSX_ARCHITECTURES arm64)
set(CMAKE_OSX_DEPLOYMENT_TARGET 11.0)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
# gfortran (Homebrew gcc), not flang — enable_language(Fortran) is in the driver.
if(NOT DEFINED CMAKE_Fortran_COMPILER AND NOT DEFINED ENV{FC})
  set(CMAKE_Fortran_COMPILER gfortran)
endif()
# The default Scilab include base (the intersection of the 4 exemplars' include
# sets). Modules add extras via EXTRA_INCLUDES. Order preserved.
set(SCILAB_DEFAULT_INCLUDES
  ${SCILAB_SOURCE_DIR}/modules/core/includes
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/ast
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/exps
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/operations
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/parse
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/symbol
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/system_env
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/types
  ${SCILAB_SOURCE_DIR}/modules/api_scilab/includes
  ${SCILAB_SOURCE_DIR}/modules/localization/includes
  ${SCILAB_SOURCE_DIR}/modules/output_stream/includes
  ${SCILAB_SOURCE_DIR}/modules/dynamic_link/includes)
# Homebrew CPPFLAGS base (configure-detected on this machine; the future
# de-autotools driver derives these). Do NOT put libomp/libxml2 here — those come
# from find_package / SYSTEM_LIBS per module.
set(SCILAB_HOMEBREW_INCLUDES /opt/homebrew/include /opt/homebrew/opt/libarchive/include)
```

- [ ] **Step 3: Write `ScilabModule.cmake`** — the helper. It must reproduce, from a data declaration, exactly what the 4 hand-written exemplar CMakeLists produce. Use this structure (the implementer refines details until the Task-2 gate — all 4 exemplars PARITY OK — passes):

```cmake
# scilab/cmake/ScilabModule.cmake — ALL per-module policy, once.
include(CMakeParseArguments)
function(scilab_module NAME)
  cmake_parse_arguments(M "" "CLASS;SYMBOLS"
    "ALGO_SOURCES;GATEWAY_SOURCES;LANG;SYSTEM_LIBS;FIND_PACKAGES;MODULE_DEPS;EXTRA_INCLUDES" ${ARGN})
  set(_dir ${CMAKE_CURRENT_SOURCE_DIR})
  # --- flags, per language (transcribed SCI_*FLAGS; semantic parity facts) ---
  set(_cflags   -std=gnu23 -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
                -fno-stack-protector -Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types)
  set(_cxxflags -std=c++17 -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
                -fno-stack-protector -Wall -Wpedantic)
  set(_fflags   -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
                -I${SCILAB_SOURCE_DIR}/modules/core/includes)
  set(_incs ${_dir}/includes ${_dir}/src/c ${_dir}/src/cpp
            ${SCILAB_DEFAULT_INCLUDES} ${M_EXTRA_INCLUDES} ${SCILAB_HOMEBREW_INCLUDES})
  # --- find_package deps (e.g. OpenMP) ---
  set(_link_libs "")
  foreach(pkg IN LISTS M_FIND_PACKAGES)
    if(pkg STREQUAL "OpenMP")
      if(NOT DEFINED OpenMP_ROOT AND NOT DEFINED ENV{OpenMP_ROOT})
        set(OpenMP_ROOT /opt/homebrew/opt/libomp)
      endif()
      find_package(OpenMP REQUIRED COMPONENTS C CXX)
      list(APPEND _link_libs OpenMP::OpenMP_C)
    else()
      find_package(${pkg} REQUIRED)
    endif()
  endforeach()
  # helper to apply flags/includes/defs to a target
  function(_sci_apply tgt)
    target_compile_definitions(${tgt} PRIVATE HAVE_CONFIG_H)
    target_include_directories(${tgt} PRIVATE ${_incs})
    target_compile_options(${tgt} PRIVATE
      $<$<COMPILE_LANGUAGE:C>:${_cflags}> $<$<COMPILE_LANGUAGE:CXX>:${_cxxflags}>
      $<$<COMPILE_LANGUAGE:Fortran>:${_fflags}>)
    set_target_properties(${tgt} PROPERTIES DEFINE_SYMBOL "" POSITION_INDEPENDENT_CODE ON)
  endfunction()
  # --- OBJECT convenience lib (never STATIC) ---
  set(_algo_obj "")
  if(M_ALGO_SOURCES)
    add_library(sci${NAME}-algo OBJECT ${M_ALGO_SOURCES})
    _sci_apply(sci${NAME}-algo)
    set(_algo_obj $<TARGET_OBJECTS:sci${NAME}-algo>)
  endif()
  # --- the gateway SHARED lib ---
  add_library(sci${NAME} SHARED ${_algo_obj} ${M_GATEWAY_SOURCES})
  _sci_apply(sci${NAME})
  if("CXX" IN_LIST M_LANG OR "Fortran" IN_LIST M_LANG)
    set_target_properties(sci${NAME} PROPERTIES LINKER_LANGUAGE CXX)
  endif()
  target_link_libraries(sci${NAME} PRIVATE ${_link_libs} ${M_SYSTEM_LIBS} ${M_MODULE_DEPS})
  target_link_options(sci${NAME} PRIVATE
    "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains"
    "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")
  set_target_properties(sci${NAME} PROPERTIES
    OUTPUT_NAME "sci${NAME}.2027" PREFIX "lib" SUFFIX ".dylib"
    INSTALL_NAME_DIR "/usr/local/lib/scilab" BUILD_WITH_INSTALL_NAME_DIR TRUE)
  # --- drop-in: copy the real dylib into .libs/ + recreate the symlink ---
  add_custom_target(drop-in-${NAME}
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:sci${NAME}> ${_dir}/.libs/libsci${NAME}.2027.dylib
    COMMAND ${CMAKE_COMMAND} -E create_symlink libsci${NAME}.2027.dylib ${_dir}/.libs/libsci${NAME}.dylib
    DEPENDS sci${NAME} VERBATIM)
  if(TARGET drop-in-all)
    add_dependencies(drop-in-all drop-in-${NAME})
  endif()
endfunction()
```
NOTE for the implementer: `MODULE_DEPS` here are CMake target names (`sci<dep>`) so CMake orders the build and records the sibling install_name; for a module whose `-algo` sources use different flags than the gateway, they already share flags via `_sci_apply` (all 4 exemplars do). The `_link_libs`/`SYSTEM_LIBS` split preserves find_package vs explicit-`-l`.

- [ ] **Step 4: Write `scilab/CMakeLists.txt`** (driver skeleton — exemplars only for now).

```cmake
cmake_minimum_required(VERSION 3.20)
list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/cmake)
include(cmake/ScilabToolchain.cmake)
project(scilab-native C CXX Fortran)
include(cmake/ScilabModule.cmake)
add_custom_target(drop-in-all)          # modules register themselves onto this
foreach(m sound parallel coverage interpolation)   # grows as batches land
  add_subdirectory(modules/${m})
endforeach()
```

- [ ] **Step 5: Rewrite `scilab/modules/sound/CMakeLists.txt`** as a single call.

```cmake
scilab_module(sound
  GATEWAY_SOURCES sci_gateway/c/sci_beep.c sci_gateway/c/sci_PlaySound.c
  LANG C CLASS DYNAMIC_LOAD SYMBOLS 3)
```

- [ ] **Step 6: Build sound via CMake + drop in.**

Run:
```bash
cd scilab && cmake -S . -B build-cmake -DCMAKE_BUILD_TYPE= >/dev/null && \
  cmake --build build-cmake --target drop-in-sound
```
Expected: builds `libscisound.2027.dylib`, drops it into `modules/sound/.libs/`.

- [ ] **Step 7: Run the gate — sound PARITY OK via the helper.**

Run:
```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/t1.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/t1.json; echo "rc=$?"
```
Expected: `PARITY OK`, `rc=0`. The `libscisound.VER.dylib` entry matches (3 symbols, libSystem-only, install_name). If not, fix `ScilabModule.cmake` until green.

- [ ] **Step 8: Verify flag facts of the helper-built sound.**

`CMAKE_EXPORT_COMPILE_COMMANDS ON` (set in `ScilabToolchain.cmake`) writes `scilab/build-cmake/compile_commands.json`.
Run:
```bash
cd scilab/build-parity && python3 -c "import json;from parity.fingerprint import parse_flag_facts;cc=json.load(open('../build-cmake/compile_commands.json'));e=[x for x in cc if 'sci_beep.c' in x['file']][0];print(parse_flag_facts(e.get('command') or ' '.join(e['arguments'])))"
```
Expected: `{'opt': 'O2', 'wrapv': True, 'min_macos': '11.0', ...}`.

- [ ] **Step 9: Commit.**

```bash
cd scilab && git add cmake/ScilabModule.cmake cmake/ScilabToolchain.cmake CMakeLists.txt .gitignore modules/sound/CMakeLists.txt
git commit -m "cmake: scilab_module() helper + top-level driver skeleton; sound migrated, parity-proven"
```

---

### Task 2: Migrate `parallel`, `coverage`, `interpolation` to `scilab_module()`

**Files:**
- Modify (replace body with a `scilab_module()` call): `scilab/modules/{parallel,coverage,interpolation}/CMakeLists.txt`
- Test: the harness (all 4 exemplars must be PARITY OK).

**Interfaces:**
- Consumes: `scilab_module()` from Task 1.
- Produces: proof the helper reproduces all four already-verified dylibs — the guard before the 60 remaining ride on it. If any exemplar regresses, the helper (not the module) is wrong; fix the helper.

- [ ] **Step 1: Rewrite the three CMakeLists as calls** (data extracted from their current hand-written files + their reports):

```cmake
# modules/parallel/CMakeLists.txt
scilab_module(parallel
  GATEWAY_SOURCES src/noparallel/noparallel.c
  LANG C FIND_PACKAGES OpenMP CLASS DYNAMIC_LOAD SYMBOLS 3)
```
```cmake
# modules/coverage/CMakeLists.txt   (extract the exact 8 algo + 8 gateway sources
# from modules/coverage/Makefile.am; EXTRA_INCLUDES: console, string, fileio/{includes,src/c},
# parameters, threads; the Xcode-SDK usr/include for libxml)
scilab_module(coverage
  ALGO_SOURCES    src/cpp/CoverModule.cpp src/cpp/InstrumentVisitor.cpp src/cpp/CoverResult.cpp
                  src/cpp/CodePrinterVisitor.cpp src/cpp/CovHTMLCodePrinter.cpp src/cpp/CoverMacroInfo.cpp
                  src/cpp/URLEncoder.cpp src/cpp/CoverModule_interface.cpp
  GATEWAY_SOURCES sci_gateway/cpp/sci_covStart.cpp sci_gateway/cpp/sci_covWrite.cpp
                  sci_gateway/cpp/sci_covStop.cpp sci_gateway/cpp/sci_covMerge.cpp
                  sci_gateway/cpp/sci_profileGetInfo.cpp sci_gateway/cpp/sci_profileEnable.cpp
                  sci_gateway/cpp/sci_profileDisable.cpp sci_gateway/cpp/coverage_gw.cpp
  LANG CXX SYSTEM_LIBS xml2 z icucore
  EXTRA_INCLUDES ${SCILAB_SOURCE_DIR}/modules/console/includes ${SCILAB_SOURCE_DIR}/modules/string/includes
                 ${SCILAB_SOURCE_DIR}/modules/fileio/includes ${SCILAB_SOURCE_DIR}/modules/fileio/src/c
                 ${SCILAB_SOURCE_DIR}/modules/threads/includes
                 /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS SYMBOLS 158)
```
```cmake
# modules/interpolation/CMakeLists.txt
scilab_module(interpolation
  ALGO_SOURCES    src/c/interpolation.c src/c/someinterp.c
                  src/fortran/somespline.f src/fortran/dspfit.f src/fortran/cshep2d.f src/fortran/mesh2b.f
  GATEWAY_SOURCES sci_gateway/cpp/sci_lsq_splin.cpp   # + the other 11 GATEWAY_CPP_SOURCES from Makefile.am
  LANG C Fortran CXX CLASS DYNAMIC_LOAD SYMBOLS 64)
```
(The Fortran runtime rides in via CMake's implicit Fortran link info when `LANG` includes Fortran; if an extra dep appears, add the explicit fallback to the helper — see the interpolation report.)

- [ ] **Step 2: Build all four + drop in.**

Run: `cd scilab && cmake --build build-cmake --target drop-in-all`
Expected: builds sci{sound,parallel,coverage,interpolation}, drops all four in.

- [ ] **Step 3: Run the gate — all four PARITY OK.**

Run: `cd scilab/build-parity && python3 -m parity.capture .. /tmp/t2.json cand && python3 -m parity.diff baseline-autotools.json /tmp/t2.json; echo "rc=$?"`
Expected: `PARITY OK`, `rc=0`. Each of libsci{sound,parallel,coverage,interpolation}.VER.dylib matches the baseline field-for-field (3 / 3 / 158 / 64 symbols; correct deps incl. libomp / xml2+z+icucore / libgfortran+libquadmath; no libc++ on parallel; correct install_names). If any fails, fix `ScilabModule.cmake` (the helper is wrong), not the module.

- [ ] **Step 4: Commit.**

```bash
cd scilab && git add modules/parallel/CMakeLists.txt modules/coverage/CMakeLists.txt modules/interpolation/CMakeLists.txt
git commit -m "cmake: migrate parallel/coverage/interpolation to scilab_module() — helper proven across all 4 dimensions"
```

---

### Task 3: Per-module flag-fact harness check

**Files:**
- Create: `scilab/build-parity/parity/flagfacts_check.py`, `scilab/build-parity/tests/test_flagfacts_check.py`
- Test: `pytest` in `build-parity/`.

**Interfaces:**
- Produces: `check_flag_facts(compile_commands_path, expected_by_file_suffix) -> list[str]` returning a list of mismatch strings (empty = pass), and a CLI `python3 -m parity.flagfacts_check <compile_commands.json>` that exits non-zero on any mismatch. Consumed by Task 10's gate + CI.
- Consumes: `parse_flag_facts` from `parity.fingerprint`.

- [ ] **Step 1: Write the failing test.**

```python
# scilab/build-parity/tests/test_flagfacts_check.py
import json, os, pytest
from parity.flagfacts_check import check_flag_facts

def _cc(tmp_path, entries):
    p = tmp_path / "compile_commands.json"; p.write_text(json.dumps(entries)); return str(p)

def test_pass_when_all_facts_match(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])
    assert check_flag_facts(cc, {".cpp": {"opt": "O2", "wrapv": True, "min_macos": "11.0"}}) == []

def test_fail_names_the_regressed_fact(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O0 -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])  # O0 + no fwrapv
    out = check_flag_facts(cc, {".cpp": {"opt": "O2", "wrapv": True, "min_macos": "11.0"}})
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out)
```

- [ ] **Step 2: Run it — fails (module missing).**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_flagfacts_check.py -q`
Expected: FAIL with `ModuleNotFoundError: parity.flagfacts_check`.

- [ ] **Step 3: Implement `flagfacts_check.py`.**

```python
# scilab/build-parity/parity/flagfacts_check.py
"""Assert the semantic compiler-flag facts of a CMake module's compile lines.
Closes the hybrid blind spot: the tree-wide flag manifest reads config.status
(autotools), so it cannot see a CMake module's own flags; this can."""
import json, sys
from parity.fingerprint import parse_flag_facts

def check_flag_facts(compile_commands_path, expected_by_suffix):
    with open(compile_commands_path) as f:
        entries = json.load(f)
    mismatches = []
    for e in entries:
        cmd = e.get("command") or " ".join(e.get("arguments", []))
        for suffix, expected in expected_by_suffix.items():
            if not e["file"].endswith(suffix):
                continue
            facts = parse_flag_facts(cmd)
            for k, want in expected.items():
                if facts.get(k) != want:
                    mismatches.append(f"{e['file']}: flag fact {k}={facts.get(k)!r} (want {want!r})")
    return mismatches

if __name__ == "__main__":
    # default expectation: every C/C++/Fortran TU is O2 + fwrapv + min_macos 11.0
    base = {"opt": "O2", "wrapv": True, "min_macos": "11.0"}
    out = check_flag_facts(sys.argv[1], {".c": base, ".cpp": base, ".f": base})
    for m in out:
        print(m)
    sys.exit(1 if out else 0)
```

- [ ] **Step 4: Run tests — pass.**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_flagfacts_check.py -q`
Expected: PASS (2 passed).

- [ ] **Step 5: Prove it on the real 4-exemplar build (the merged compile_commands).**

Run: `cd scilab/build-parity && python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"`
Expected: `rc=0` (no output). Mutating a flag in the build would print the offending file + fact.

- [ ] **Step 6: Commit.**

```bash
cd scilab && git add build-parity/parity/flagfacts_check.py build-parity/tests/test_flagfacts_check.py
git commit -m "build-parity: per-module CMake flag-fact check (closes the hybrid config.status blind spot)"
```

---

### Task 4: Enumerate the scope — the Stage-1e manifest

**Files:**
- Create: `scilab/cmake/stage1e-manifest.md`
- Test: the manifest accounts for all 64 baseline module dylibs; the 4 exemplars' rows match their known data.

**Interfaces:**
- Produces: `stage1e-manifest.md` — one row per baseline module dylib: `dylib key | module dir | main/variant | class (ENGINE_LIBS/DYNAMIC_LOAD) | languages | external deps | module deps | symbol count`. This is the authoritative work-list for Tasks 5–9 (the batches consume it).
- Consumes: `baseline-autotools.json` + each module's `Makefile.am` + `nm -gU` on each autotools dylib.

- [ ] **Step 1: Extract the exact scope from the baseline.**

Run:
```bash
cd scilab/build-parity && python3 -c "import json;b=json.load(open('baseline-autotools.json'));print('\n'.join(sorted(k for k in b['dylibs'] if k.startswith('libsci') and k not in ('libscilab.VER.dylib','libscilab-cli.VER.dylib'))))"
```
Expected: 64 dylib keys (incl. the ~18 `-disable`/`-cli`/`-minimal`/`-java` variants). This is the row set.

- [ ] **Step 2: For each dylib, classify.** For each key `libsci<X>.VER.dylib` (X may be `<module>` or `<module>-variant`): its module dir is the leading `<module>`; `main` vs `variant`; `class` from which list it is in `modules/Makefile.am` (`ENGINE_LIBS` vs `ENGINE_LIBS_DYNAMIC_LOAD`); `languages` from the source dirs; `external deps` + `module deps` from `otool -L` on `modules/<module>/.libs/libsci<X>.2027.dylib` (non-`libsci` externals; `libsci*` = module deps → `MODULE_DEPS`); `symbol count` from `nm -gU … | wc -l`. Record the special cases: `javasci` links the aggregate `libscilab` (not a module — resolve via `dynamic_lookup`); modules with no built dylib under their name (e.g. any that fold into the aggregate) are NOT in the baseline and are OUT of scope — confirm none of the 64 are such.

- [ ] **Step 3: Write `stage1e-manifest.md`** with a row per dylib (grouped into the batches of Tasks 5–9: simple leaves / variants / external-dep / JNI-Java-native / inter-module-edge). Mark the 4 exemplars as DONE.

- [ ] **Step 4: Verify the manifest is complete and correct.**

Run:
```bash
cd scilab && grep -c '| ' cmake/stage1e-manifest.md   # rows
```
Expected: 64 data rows. Cross-check the 4 exemplar rows against their known facts (sound C/3/DYNAMIC_LOAD; coverage CXX/158/ENGINE_LIBS/xml2+z+icucore; interpolation C+Fortran+CXX/64/DYNAMIC_LOAD/fortran-rt; parallel C/3/DYNAMIC_LOAD/OpenMP).

- [ ] **Step 5: Commit.**

```bash
cd scilab && git add cmake/stage1e-manifest.md
git commit -m "cmake: Stage-1e scope manifest — all 64 baseline module dylibs classified into batches"
```

---

### Task 5: Batch A — simple leaves (C/C++, no external dep, no edge, no variant)

**Files:** Create `scilab/modules/<m>/CMakeLists.txt` for each simple-leaf module in the manifest (e.g. `ast`, `call_scilab`, `commons`, `completion`, `external_objects`, `functions`, `functions_manager`, `graphic_export`, `graphic_objects`, `graphics`, `gui`, `history_browser`, `history_manager`, `scinotes`, `tclsci`, `ui_data`, `umfpack` — the exact set is the manifest's Batch-A rows, minus any that carry a variant → Batch B). Modify `scilab/CMakeLists.txt` (add these to the `foreach`).

**Interfaces:** Consumes `scilab_module()`. Each module is one call; no `SYSTEM_LIBS`/`FIND_PACKAGES`/`MODULE_DEPS`.

- [ ] **Step 1 (worked example — repeat the procedure per module): extract `ast`'s data.**

Run:
```bash
cd scilab && awk '/_la_SOURCES *=/,/[^\\]$/' modules/ast/Makefile.am | head -60   # sources (algo + gateway)
nm -gU modules/ast/.libs/libsciast.2027.dylib | wc -l                            # symbol count
grep -n 'libsciast' modules/Makefile.am                                          # class (ENGINE vs DYNLOAD)
```
Expected: the algo/gateway source lists, symbol count, and class for `ast`.

- [ ] **Step 2: Write `modules/ast/CMakeLists.txt`** (shape — fill from Step 1):

```cmake
scilab_module(ast
  ALGO_SOURCES    <the ast -algo sources from Makefile.am>
  GATEWAY_SOURCES <the ast gateway sources>
  LANG CXX C CLASS <ENGINE_LIBS|DYNAMIC_LOAD> SYMBOLS <n>)
```

- [ ] **Step 3: Repeat Steps 1–2 for every Batch-A module** (each is one `scilab_module()` call, data from its `Makefile.am`). Add all Batch-A module names to the `foreach` in `scilab/CMakeLists.txt`.

- [ ] **Step 4: Build the batch + drop in.**

Run: `cd scilab && cmake -S . -B build-cmake >/dev/null && cmake --build build-cmake --target drop-in-all`
Expected: all Batch-A dylibs build + drop in.

- [ ] **Step 5: Run the gate — whole-tree PARITY OK + flag facts.**

Run:
```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/t5.json cand && python3 -m parity.diff baseline-autotools.json /tmp/t5.json && \
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: `PARITY OK`, flag-facts `rc=0`. Every Batch-A dylib matches its baseline entry. Fix per-module CMakeLists (usually a missing include or a wrong source) until green.

- [ ] **Step 6: Commit.** `git add modules/<Batch-A>/CMakeLists.txt CMakeLists.txt && git commit -m "cmake: Batch A — simple leaf modules migrated, parity-proven"`

---

### Task 6: Batch B — modules with variant dylibs (`-disable` / `-cli` / `-minimal`)

**Files:** Create/extend `scilab/modules/<m>/CMakeLists.txt` for each module that emits a variant (from the manifest, e.g. `action_binding`+`-disable`, `console`+`-minimal`, `preferences`+`-cli`, `graphics`/`gui`/`graphic_objects`/`graphic_export`/`history_browser`/`scinotes`/`tclsci`/`ui_data`/`jvm`/`helptools`/`scicos_blocks`/`scicos`/`types`/`xcos` + their variants — exact set = manifest Batch-B rows). Modify `scilab/CMakeLists.txt`.

**Interfaces:** A variant is just a second `scilab_module()` call in the same module dir, with the variant name (hyphen allowed) and its own (stub / reduced) sources. The main module's `MODULE_DEPS` may include its variant (e.g. `console` links `console-minimal`).

- [ ] **Step 1 (worked example): extract `action_binding` + its `-disable` stub.**

Run:
```bash
cd scilab && grep -nE 'LTLIBRARIES|_la_SOURCES *=|_disable_la_SOURCES' modules/action_binding/Makefile.am
nm -gU modules/action_binding/.libs/libsciaction_binding-disable.2027.dylib | wc -l
```
Expected: main sources, the `-disable` stub source (`src/noaction_binding/noaction_binding.cpp`), and each's symbol count.

- [ ] **Step 2: Write `modules/action_binding/CMakeLists.txt`** (two calls):

```cmake
scilab_module(action_binding
  ALGO_SOURCES <...> GATEWAY_SOURCES <...> LANG CXX C CLASS DYNAMIC_LOAD SYMBOLS <n>)
scilab_module(action_binding-disable          # the stub variant
  GATEWAY_SOURCES src/noaction_binding/noaction_binding.cpp LANG CXX CLASS DYNAMIC_LOAD SYMBOLS <n>)
```

- [ ] **Step 3: Repeat for every Batch-B module** (main + variant calls; wire `MODULE_DEPS` where the main links the variant). Add names to the `foreach`.

- [ ] **Step 4: Build + drop in.** `cmake --build build-cmake --target drop-in-all`

- [ ] **Step 5: Gate — whole-tree PARITY OK + flag facts** (same commands as Task 5 Step 5). Each variant dylib must match its baseline entry (correct name `libsci<m>-<variant>.2027.dylib`, symbols, deps).

- [ ] **Step 6: Commit.** `git commit -m "cmake: Batch B — variant dylibs (disable/cli/minimal) migrated, parity-proven"`

---

### Task 7: Batch C — external-dependency modules

**Files:** Create `scilab/modules/<m>/CMakeLists.txt` for the external-dep modules (manifest Batch-C: `arnoldi` [arpack, openblas], `console` [ncurses], `hdf5`, `matio` [hdf5, matio], `optimization`/`randlib`/`scicos_blocks`/`signal_processing`/`special_functions`/`statistics` [fortran-rt], `slint`/`xml`/`preferences` [xml2, z, icucore], `spreadsheet` [arrow, parquet, xlnt], `webtools` [curl]). Modify `scilab/CMakeLists.txt`.

**Interfaces:** System libs (`/usr/lib`, `/usr/local/lib` Homebrew-abs-install-name) via `SYSTEM_LIBS`; the recorded dep must match the baseline path. Fortran modules include `Fortran` in `LANG` (runtime rides in). `find_package` only for what the exemplars proved (OpenMP); everything else explicit `SYSTEM_LIBS` to avoid the Homebrew-shadow trap.

- [ ] **Step 1 (worked example): `arnoldi`.**

Run:
```bash
cd scilab && otool -L modules/arnoldi/.libs/libsciarnoldi.2027.dylib   # exact dep names/paths
grep -nE '_LIBS|LIBADD' modules/arnoldi/Makefile | grep -iE 'arpack|blas|lapack' | head
```
Expected: the exact external dep set (e.g. `libarpack.2`, `libopenblas.0`) + how autotools linked them.

- [ ] **Step 2: Write `modules/arnoldi/CMakeLists.txt`:**

```cmake
scilab_module(arnoldi
  ALGO_SOURCES <...> GATEWAY_SOURCES <...> LANG C
  SYSTEM_LIBS arpack openblas   # verify the recorded dep path matches the baseline (abs install_name)
  CLASS <...> SYMBOLS <n>)
```
NOTE: if a dep records at an unexpected path (e.g. a Homebrew keg with a versioned Cellar install_name), pin it as the exemplars did for libomp (abs path) and re-check parity.

- [ ] **Step 3: Repeat for every Batch-C module.** Add to the `foreach`.

- [ ] **Step 4: Build + drop in.**
- [ ] **Step 5: Gate — whole-tree PARITY OK + flag facts.** Each module's external deps must match the baseline exactly (right lib, right path, no Homebrew-shadow).
- [ ] **Step 6: Commit.** `git commit -m "cmake: Batch C — external-dependency modules migrated, parity-proven"`

---

### Task 8: Batch D — JNI / Java-native modules (`jvm`, `external_objects_java`)

**Files:** Create `scilab/modules/{jvm,external_objects_java}/CMakeLists.txt`. Modify `scilab/CMakeLists.txt`.

**Interfaces:** These link the JDK (`libjli`/`libjvm`) — a dimension the 4 exemplars did not exercise. The native dylib links the JDK lib exactly as autotools did; the Java (Ant) side is untouched (out of scope). Add the JDK lib via `SYSTEM_LIBS` (explicit path) after confirming the baseline's recorded dep.

- [ ] **Step 1: Extract the JDK dep.**

Run:
```bash
cd scilab && otool -L modules/jvm/.libs/libscijvm.2027.dylib | grep -iE 'jli|jvm|jawt|jdk'
grep -nE 'JAVA_JNI|JNI_LIBS|jni' modules/jvm/Makefile | head
```
Expected: the exact JDK lib + recorded path (e.g. `.../libjli.dylib`), and how autotools found it.

- [ ] **Step 2: Write the two CMakeLists** (`jvm` first — `external_objects_java` `MODULE_DEPS jvm commons`). Add the JDK lib via `SYSTEM_LIBS` with the exact path so the recorded dep matches the baseline. If the JDK path is machine-specific, discover it (`/usr/libexec/java_home`) rather than hardcode, and note it as a driver-portability item.

- [ ] **Step 3: Build + drop in.**
- [ ] **Step 4: Gate — PARITY OK + flag facts** for `libscijvm` + `libsciexternal_objects_java` (+ their variants if any). The JDK dep must match the baseline.
- [ ] **Step 5: Commit.** `git commit -m "cmake: Batch D — JNI/Java-native modules (jvm, external_objects_java) migrated, parity-proven"`

---

### Task 9: Batch E — inter-module-edge modules + `javasci` special case

**Files:** Create `scilab/modules/<m>/CMakeLists.txt` for the 11 edge modules — dependency-ordered so each `MODULE_DEPS` target already exists: `differential_equations` (→`sundials`), `fileio` (→`console`), `helptools` (→`commons`), `integer` (→`polynomials`), `localization` (→`io`), `renderer` (→`jvm`), `scicos` (→`scicos_blocks`,`sundials`), `types` (→`ast`,`commons`,`jvm`), `xcos` (→`commons`,`jvm`,`scicos`), `external_objects_java` (done in Task 8), `javasci` (→`api_scilab`, + the aggregate `libscilab`). Modify `scilab/CMakeLists.txt`.

**Interfaces:** `MODULE_DEPS <sci-targets>` → CMake orders the build + records the sibling install_name. Any sibling that isn't itself in scope (only `javasci`→`libscilab` aggregate) is resolved via `dynamic_lookup` (the aggregate is autotools-built and present in `.libs/`; do NOT add it as a `MODULE_DEPS` CMake target).

- [ ] **Step 1 (worked example): `differential_equations` → `sundials`.**

Run:
```bash
cd scilab && otool -L modules/differential_equations/.libs/libscidifferential_equations.2027.dylib | grep -iE 'libscisundials|klu|umfpack|amd|omp'
```
Expected: the recorded `libscisundials.2027.dylib` dep (a module edge) + external deps (klu/umfpack/amd/omp).

- [ ] **Step 2: Write `modules/differential_equations/CMakeLists.txt`:**

```cmake
scilab_module(differential_equations
  ALGO_SOURCES <...> GATEWAY_SOURCES <...> LANG C Fortran CXX
  MODULE_DEPS scisundials SYSTEM_LIBS klu umfpack amd
  FIND_PACKAGES OpenMP CLASS ENGINE_LIBS SYMBOLS <n>)
```
(`sundials` itself must be migrated before this — it's a Batch-C/leaf module; ensure `add_subdirectory(modules/sundials)` precedes, or rely on CMake target-dep ordering.)

- [ ] **Step 3: Repeat for each edge module, dependency-ordered.** For `javasci`: `MODULE_DEPS sciapi_scilab`; the `libscilab` aggregate dep resolves via `dynamic_lookup` (verify the recorded dep matches the baseline — it should, since the aggregate's install_name is stable).

- [ ] **Step 4: Build + drop in.** CMake orders the 11 edges via target deps; no hand topo-sort.
- [ ] **Step 5: Gate — whole-tree PARITY OK + flag facts.** Each edge module records its sibling dep by the sibling's install_name, matching the baseline.
- [ ] **Step 6: Commit.** `git commit -m "cmake: Batch E — inter-module-edge modules + javasci special case, parity-proven"`

---

### Task 10: Finalize `drop-in-all` + documented flow + CI

**Files:** Modify `scilab/CMakeLists.txt` (ensure all in-scope modules are in the `foreach`); create `docs/design/build-cmake-driver.md`; modify `.gitlab-ci.yml` (add the parity + flag-fact gate).

**Interfaces:** Consumes everything. Produces the end-to-end `cmake --build … --target drop-in-all` → whole-tree PARITY OK flow + CI enforcement.

- [ ] **Step 1: Confirm the `foreach` covers every in-scope module** (its module count == the manifest's module count; the 64 dylibs are all produced).

Run:
```bash
cd scilab && grep -oE 'add_subdirectory\(modules/[a-z_0-9]+\)|foreach\(m .*\)' CMakeLists.txt
```
Expected: every manifest module present.

- [ ] **Step 2: Full clean build + drop-in-all + the final gate.**

Run:
```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && cmake --build build-cmake --target drop-in-all -j && \
cd build-parity && python3 -m parity.capture .. /tmp/final.json cand && python3 -m parity.diff baseline-autotools.json /tmp/final.json && \
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: `PARITY OK` over all 64 module dylibs, flag-facts `rc=0`.

- [ ] **Step 3: Prove the app still builds + runs via autotools on top of the CMake dylibs.**

Run:
```bash
cd scilab && make >/dev/null 2>&1 && ./bin/scilab-cli -nwni -e "disp(1+1); exit(0)"
```
Expected: `2.` — the executables (autotools-linked) load the CMake-built module dylibs and run clean.

- [ ] **Step 4: Write `docs/design/build-cmake-driver.md`** (the usage: configure prerequisite, `cmake --build`, `drop-in-all`, the parity gate; note c++17-held + the deferred c++23/header axes).

- [ ] **Step 5: Wire CI** — add a `.gitlab-ci.yml` job that (on the built tree) runs `cmake --build build-cmake --target drop-in-all` then the parity diff + flag-fact check, failing the pipeline on any regression.

- [ ] **Step 6: Commit.**

```bash
cd scilab && git add CMakeLists.txt ../docs/design/build-cmake-driver.md ../.gitlab-ci.yml
git commit -m "cmake: Stage-1e driver complete — drop-in-all builds all 64 module dylibs at parity + CI gate"
```

---

## Notes for the executor

- **The harness is the test.** Each task's "done" is `PARITY OK` (+ flag-facts) on the real tree. There are no unit tests to write except Task 3's flag-fact check — the parity harness, already mutation-proven, is the acceptance test for every module.
- **Batches are subagent-parallelizable** within a task: each module's data extraction + `scilab_module()` call is independent; only the whole-tree parity gate is shared. Dispatch per-module or per-sub-group, then run the batch gate once.
- **When a module needs a pattern the helper lacks** (a new external lib class, a JDK link, a generated-source module), extend `ScilabModule.cmake` and re-prove the 4 exemplars (Task 2's gate) before continuing — the helper change must not regress what's proven.
- **c++17 is held deliberately** (Global Constraints); the c++23 bump is spec §12, a separate later step (one line in `ScilabModule.cmake` after an autotools re-baseline).
