# Retire-configure RC-a — CMake generates machine.h + semantic-header parity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CMake computes all 184 `machine.h` macros itself and generates the header, proven equivalent to configure's by a new **semantic** header-parity dimension in the harness (the `#define` set, not bytes).

**Architecture:** The harness gains `parse_defines()` + a `header_defines` fingerprint section comparing the CMake-generated `machine.h` against configure's macro set — built FIRST, because it is the gate the 184-macro port iterates against. `ScilabMachineHeader.cmake` then computes the five buckets (probes, pkg-config, Fortran mangling, options, PACKAGE) and `configure_file`s a CMake-authored template into `build-cmake/generated-includes/machine.h`. Additive: the source-tree header and its byte-hash are untouched.

**Tech Stack:** CMake (`CheckIncludeFile`/`CheckSymbolExists`/`CheckFunctionExists`/`CheckTypeSize`, `FindPkgConfig`, `FortranCInterface`, `configure_file`), Python 3 stdlib + pytest (the parity harness).

## Global Constraints

- **CMake COMPUTES the values itself — never copies `config.status`.** That is what makes the semantic dimension a real gate rather than a tautology.
- **Semantic, not byte.** A CMake-generated `machine.h` is not byte-identical to autoconf's; compare the `{macro: value}` `#define` set. `machine.h` has **no volatile fields**, so no normalization (unlike version.h's git fields / the jar MANIFEST).
- **ADDITIVE / coexistence-safe.** The source-tree `modules/core/includes/machine.h` and its existing byte-hash in the `generated` section are **untouched**; it still resolves first during coexistence (`ScilabModule.cmake`'s parity-critical `core/includes`-first order), and CMake's copy is *semantically* equal so codegen is identical. The generated copy activates at RC-e.
- **NO edits to `configure.ac`, any `Makefile.am`, `machine.h.in`, or `version.h.in`.** Reading `machine.h.in` to enumerate the macro list is allowed (read-only input).
- **Coexistence is TEMPORARY** — the destination is autotools DELETED (migration doc §12). RC-a is sub-stage 1 of RC-a…RC-e.
- **No AI-attribution in commit messages.** Commit directly on `main`.
- **Native side unchanged** — `-std=c++17` held; whole-tree parity must stay OK.

## File Structure

- `scilab/build-parity/parity/fingerprint.py` — MODIFY: add pure `parse_defines(header_text)`.
- `scilab/build-parity/parity/capture.py` — MODIFY: capture the CMake-generated `machine.h` into a `header_defines` section.
- `scilab/build-parity/parity/diff.py` — MODIFY: compare `header_defines` macro-by-macro (transition-gated).
- `scilab/build-parity/tests/test_header_defines.py` — CREATE: parse + diff + fault-injection tests.
- `scilab/cmake/ScilabMachineHeader.cmake` — CREATE: compute the 5 buckets + `configure_file`.
- `scilab/cmake/machine.h.cmake.in` — CREATE: the CMake-authored template (`#cmakedefine`).
- `scilab/CMakeLists.txt` — MODIFY: `include(cmake/ScilabMachineHeader.cmake)` beside ScilabConfigure.
- `scilab/build-parity/baseline-autotools.json` — MODIFY (Task 4): arm the reference macro set.
- `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml` — MODIFY (Task 4).

---

### Task 1: The semantic header-parity dimension (harness) — build FIRST, it gates the port

**Files:**
- Modify: `scilab/build-parity/parity/fingerprint.py` (add `parse_defines`)
- Modify: `scilab/build-parity/parity/capture.py` (capture `header_defines`)
- Modify: `scilab/build-parity/parity/diff.py` (compare `header_defines`)
- Test: `scilab/build-parity/tests/test_header_defines.py` (new)

**Interfaces:**
- Produces: `parse_defines(header_text: str) -> dict[str, str]` (fingerprint.py — key = macro identifier, value = the rest of the line, whitespace-normalized; a bare `#define X` → `""`); the fingerprint gains `"header_defines": {"machine.h": {macro: value}}`; `diff_fingerprints` compares it.
- Consumes: nothing from later tasks.

- [ ] **Step 1: Write the failing tests.** Create `scilab/build-parity/tests/test_header_defines.py`:

```python
"""Semantic header parity: a generated C header is compared by its {macro: value}
#define SET, never byte-for-byte — autoconf and CMake spell the same configuration
differently (comment style, `#define X 1` vs `/* #undef X */`, ordering), exactly
like they spell compiler flags differently. machine.h carries no volatile fields,
so no normalization is needed."""
from parity.fingerprint import parse_defines
from parity.diff import diff_fingerprints


def _fp(**over):
    base = {"build_id": "t", "executables": {}, "dylibs": {}, "generated": {},
            "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
            "jars": {}, "header_defines": {}}
    base.update(over)
    return base


def test_parse_defines_value_and_bare():
    h = "#define HAVE_ATEXIT 1\n#define STDC_HEADERS\n"
    assert parse_defines(h) == {"HAVE_ATEXIT": "1", "STDC_HEADERS": ""}


def test_parse_defines_ignores_undef_and_comments():
    h = "/* #undef HAVE_MPI */\n#undef HAVE_TK\n/* a comment */\n#define HAVE_DLFCN_H 1\n"
    assert parse_defines(h) == {"HAVE_DLFCN_H": "1"}


def test_parse_defines_function_like_macro_keeps_body():
    # C2F/F2C/CNAME are function-like; key is the bare identifier, value the rest.
    h = "#define C2F(name) name##_\n"
    assert parse_defines(h) == {"C2F": "(name) name##_"}


def test_parse_defines_tolerates_indentation_and_spacing():
    h = "  #  define  SIZEOF_INT   4  \n"
    assert parse_defines(h) == {"SIZEOF_INT": "4"}


def test_diff_detects_changed_macro():
    base = _fp(header_defines={"machine.h": {"HAVE_X": "1", "SIZEOF_INT": "4"}})
    cand = _fp(header_defines={"machine.h": {"HAVE_X": "0", "SIZEOF_INT": "4"}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("machine.h: macro changed: HAVE_X" in d for d in r["differences"])


def test_diff_detects_added_and_removed_macro():
    base = _fp(header_defines={"machine.h": {"HAVE_A": "1"}})
    cand = _fp(header_defines={"machine.h": {"HAVE_B": "1"}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("machine.h: macro removed: HAVE_A" in d for d in diffs)
    assert any("machine.h: macro added: HAVE_B" in d for d in diffs)


def test_diff_baseline_without_header_defines_skips():
    base = _fp()
    del base["header_defines"]                     # pre-RC-a baseline (transition)
    assert diff_fingerprints(base, _fp(header_defines={"machine.h": {"A": "1"}}))["ok"]


def test_diff_candidate_missing_header_defines_against_armed_baseline_fails():
    base = _fp(header_defines={"machine.h": {"A": "1"}})
    cand = _fp()
    del cand["header_defines"]
    assert not diff_fingerprints(base, cand)["ok"]


def test_diff_identical_header_defines_ok():
    h = {"machine.h": {"HAVE_X": "1", "STDC_HEADERS": ""}}
    assert diff_fingerprints(_fp(header_defines=h), _fp(header_defines=dict(h)))["ok"]
```

- [ ] **Step 2: Run to verify failure.**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_header_defines.py -q`
Expected: FAIL — `ImportError: cannot import name 'parse_defines'`.

- [ ] **Step 3: Implement `parse_defines` in `fingerprint.py`.** Append after `normalize_manifest`:

```python
# A generated C config header -> its {macro: value} #define SET. autoconf and CMake
# spell the SAME configuration differently (comment style, `#define X 1` vs
# `/* #undef X */`, ordering), exactly like they spell compiler flags differently —
# so machine.h is compared SEMANTICALLY by this set, never byte-for-byte. Key is the
# bare identifier (so a function-like C2F(name) keys as "C2F"); value is the rest of
# the line, whitespace-collapsed. #undef / commented-out macros are simply ABSENT
# from the map, which is exactly how "this feature is off" must compare.
_DEFINE_RE = re.compile(r"^\s*#\s*define\s+([A-Za-z_][A-Za-z_0-9]*)\s*(.*?)\s*$")


def parse_defines(header_text):
    """Generated C header text -> {macro: value}; bare `#define X` -> value ""."""
    out = {}
    for line in header_text.splitlines():
        m = _DEFINE_RE.match(line)
        if m:
            out[m.group(1)] = " ".join(m.group(2).split())
    return out
```

- [ ] **Step 4: Capture the section in `capture.py`.** Add `parse_defines` to the `from parity.fingerprint import (…)` list. In `fingerprint_build`, after the `generated` block and before the return, add:

```python
    # The CMake-GENERATED machine.h (RC-a), compared SEMANTICALLY against configure's
    # macro set (the baseline's reference, armed from the source-tree header). Absent
    # until RC-a's generator lands -> section simply empty (the diff's transition rule).
    header_defines = {}
    gen_machine = os.path.join(build_dir, "build-cmake", "generated-includes", "machine.h")
    if os.path.exists(gen_machine):
        with open(gen_machine, "r", errors="replace") as f:
            header_defines["machine.h"] = parse_defines(f.read())
```

Add `"header_defines": header_defines,` to the returned dict, and append
`f"{len(fp['header_defines'])} semantic headers, "` to the CLI summary `print(...)`.

- [ ] **Step 5: Compare the section in `diff.py`.** In `diff_fingerprints`, after the `jars` block and before the flags block, add:

```python
    # Semantic header parity (RC-a): machine.h compared by its #define SET, not bytes
    # (a CMake-generated header is never byte-identical to autoconf's). Transition rule
    # mirrors rpaths/jars: a baseline with no section predates RC-a -> skip; a candidate
    # that LOST the section against an armed baseline must FAIL.
    if "header_defines" in base:
        if "header_defines" not in cand:
            out.append("header_defines section missing in candidate")
        else:
            _diff_named("semantic header", base["header_defines"], cand["header_defines"], out)
            for name in sorted(set(base["header_defines"]) & set(cand["header_defines"])):
                b, c = base["header_defines"][name], cand["header_defines"][name]
                for m in sorted(set(b) - set(c)):
                    out.append(f"{name}: macro removed: {m}")
                for m in sorted(set(c) - set(b)):
                    out.append(f"{name}: macro added: {m}")
                for m in sorted(set(b) & set(c)):
                    if b[m] != c[m]:
                        out.append(f"{name}: macro changed: {m} ({b[m]!r} -> {c[m]!r})")
```

- [ ] **Step 6: Run the full suite.**

Run: `cd scilab/build-parity && python3 -m pytest tests/ -q`
Expected: PASS — the 9 new tests plus every pre-existing test (the section is additive; existing fingerprints without `header_defines` hit the transition skip).

- [ ] **Step 7: Commit.**

```bash
git add scilab/build-parity/parity/fingerprint.py scilab/build-parity/parity/capture.py \
        scilab/build-parity/parity/diff.py scilab/build-parity/tests/test_header_defines.py
git commit -m "build-parity: semantic header-parity dimension (machine.h #define set, not bytes)"
```

---

### Task 2: machine.h template + the ~131 CMake probes

**Files:**
- Create: `scilab/cmake/machine.h.cmake.in`, `scilab/cmake/ScilabMachineHeader.cmake`
- Modify: `scilab/CMakeLists.txt` (include it right after `include(cmake/ScilabConfigure.cmake)`)

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR`, `SCILAB_GENERATED_INCLUDES` (both set by `cmake/ScilabConfigure.cmake` from 1f-c); `parse_defines` (Task 1) for the convergence loop.
- Produces: `build-cmake/generated-includes/machine.h`.

- [ ] **Step 1: Enumerate the macro list mechanically (do NOT hand-type it).**

```bash
cd scilab
M=modules/core/includes/machine.h.in
grep -oE '#[[:space:]]*undef [A-Za-z_0-9]+' $M | awk '{print $NF}' | sort -u > /tmp/machine-macros.txt
wc -l /tmp/machine-macros.txt                      # 184
grep -cE '^HAVE_.*_H$' /tmp/machine-macros.txt      # 50  -> check_include_file
grep -E '^HAVE_' /tmp/machine-macros.txt | grep -vcE '_H$'   # ~71 -> check_symbol_exists/check_function_exists
```
This file is the contract: **every macro in it must appear in the template**, and the semantic diff (Task 4) proves each value.

- [ ] **Step 2: Create the template `scilab/cmake/machine.h.cmake.in`.** One `#cmakedefine` line per macro from `/tmp/machine-macros.txt`, `#cmakedefine01`-free (autoconf emits `#define X 1` or nothing, which `#cmakedefine X 1` reproduces). Header comment plus the three shapes:

```c
/* machine.h — GENERATED BY CMAKE (retire-configure RC-a).
   The CMake-computed counterpart of configure's modules/core/includes/machine.h.
   NOT byte-identical to autoconf's output by design; equivalence is proven
   SEMANTICALLY (the #define set) by the parity harness's header_defines dimension.
   One line per macro in modules/core/includes/machine.h.in. */
#ifndef SCI_MACHINE_H
#define SCI_MACHINE_H

/* boolean feature macros: defined-as-1 when the probe succeeded, absent otherwise */
#cmakedefine HAVE_DLFCN_H 1
#cmakedefine HAVE_ATEXIT 1
/* … one per HAVE_* macro … */

/* valued macros */
#cmakedefine SIZEOF_INT @SIZEOF_INT@
#cmakedefine CURL_LIBS "@CURL_LIBS@"

/* function-like macros (transcribe configure's exact text) */
#cmakedefine C2F(name) @C2F_BODY@

#endif
```

- [ ] **Step 3: Create `scilab/cmake/ScilabMachineHeader.cmake` with the probe bucket.** Compute, do not copy:

```cmake
# scilab/cmake/ScilabMachineHeader.cmake — CMake generates machine.h (retire-configure RC-a).
#
# CMake COMPUTES every macro itself (never copies config.status) — that is what makes the
# harness's semantic header_defines dimension a real gate rather than a tautology. Output is
# NOT byte-identical to autoconf's machine.h (comment/#define/ordering differ); equivalence is
# semantic. ADDITIVE: the source-tree machine.h is untouched and still resolves first during
# coexistence (ScilabModule.cmake keeps core/includes ahead); this copy activates at RC-e.
include(CheckIncludeFile)
include(CheckSymbolExists)
include(CheckFunctionExists)
include(CheckTypeSize)

# Pin the probe environment to the CONFIGURED build so a probe's answer matches configure's
# (different -isysroot/-I would flip HAVE_* silently). This is the single biggest fidelity lever.
set(CMAKE_REQUIRED_QUIET TRUE)
set(CMAKE_REQUIRED_FLAGS "-isysroot ${CMAKE_OSX_SYSROOT}")
set(CMAKE_REQUIRED_INCLUDES ${SCILAB_DEFAULT_INCLUDES} ${SCILAB_HOMEBREW_INCLUDES})

# --- bucket 1a: header probes (50) — one per HAVE_*_H in machine.h.in ---
foreach(_h dlfcn.h dirent.h curses.h archive.h curl/curl.h)   # … all 50 …
  string(TOUPPER "HAVE_${_h}" _v)
  string(REGEX REPLACE "[./]" "_" _v "${_v}")
  check_include_file(${_h} ${_v})
endforeach()

# --- bucket 1b: function/symbol probes (~71) ---
foreach(_f atexit bzero clock_gettime)                        # … all ~71 …
  string(TOUPPER "HAVE_${_f}" _v)
  check_function_exists(${_f} ${_v})
endforeach()

# --- bucket 1c: type sizes + misc ---
check_type_size("int"  SIZEOF_INT)
check_type_size("long" SIZEOF_LONG)
set(STDC_HEADERS 1)                 # C99+; autoconf defines it unconditionally here
```

- [ ] **Step 4: Wire it into the driver.** In `scilab/CMakeLists.txt`, immediately after `include(cmake/ScilabConfigure.cmake)` add:

```cmake
# RC-a — CMake computes + generates machine.h beside the generated version.h.
include(cmake/ScilabMachineHeader.cmake)
```
and end `ScilabMachineHeader.cmake` with:
```cmake
configure_file(${CMAKE_CURRENT_LIST_DIR}/machine.h.cmake.in
               ${SCILAB_GENERATED_INCLUDES}/machine.h)
```

- [ ] **Step 5: Converge the probe bucket against configure's header (this is the loop).**

```bash
cd scilab && cmake -S . -B build-cmake >/dev/null 2>&1
cd build-parity && python3 - <<'PY'
from parity.fingerprint import parse_defines
ref  = parse_defines(open("../modules/core/includes/machine.h").read())
cand = parse_defines(open("../build-cmake/generated-includes/machine.h").read())
probe = lambda d: {k: v for k, v in d.items()
                   if k.startswith(("HAVE_", "SIZEOF_")) or k in ("STDC_HEADERS", "CLOSEDIR_VOID")}
r, c = probe(ref), probe(cand)
print("missing:", sorted(set(r) - set(c)))
print("extra:  ", sorted(set(c) - set(r)))
print("changed:", sorted(k for k in set(r) & set(c) if r[k] != c[k]))
PY
```
Expected: all three lists **empty**. Iterate — the output names the exact divergent macro; fix its probe (usually `CMAKE_REQUIRED_*`, or `check_symbol_exists(<fn> <header>)` where a bare `check_function_exists` disagrees) and re-run. Do not proceed until empty.

- [ ] **Step 6: Commit.**

```bash
git add scilab/cmake/machine.h.cmake.in scilab/cmake/ScilabMachineHeader.cmake scilab/CMakeLists.txt
git commit -m "cmake: generate machine.h — the ~131 feature probes computed in CMake"
```

---

### Task 3: The remaining four buckets — pkg-config, Fortran mangling, options, PACKAGE

**Files:**
- Modify: `scilab/cmake/ScilabMachineHeader.cmake`, `scilab/cmake/machine.h.cmake.in`

**Interfaces:**
- Consumes: the template + probe bucket (Task 2); `parse_defines` (Task 1).
- Produces: a `machine.h` whose FULL 184-macro set matches configure's.

- [ ] **Step 1: Add the four buckets to `ScilabMachineHeader.cmake`:**

```cmake
# --- bucket 2: pkg-config values (12) — computed, in configure's spelling ---
find_package(PkgConfig REQUIRED)
pkg_check_modules(SCI_CURL       QUIET libcurl)
pkg_check_modules(SCI_LIBARCHIVE QUIET libarchive)
pkg_check_modules(SCI_LIBXML     QUIET libxml-2.0)
set(CURL_CFLAGS       "${SCI_CURL_CFLAGS}")
set(CURL_LIBS         "${SCI_CURL_LDFLAGS}")
set(CURL_VERSION      "${SCI_CURL_VERSION}")
set(LIBARCHIVE_CFLAGS "${SCI_LIBARCHIVE_CFLAGS}")
set(LIBARCHIVE_LIBS   "${SCI_LIBARCHIVE_LDFLAGS}")
set(LIBARCHIVE_VERSION "${SCI_LIBARCHIVE_VERSION}")
set(LIBXML_LIBS       "${SCI_LIBXML_LDFLAGS}")

# --- bucket 3: Fortran name mangling (6) ---
include(FortranCInterface)
FortranCInterface_HEADER(${CMAKE_BINARY_DIR}/FortranCInterface.h MACRO_NAMESPACE "FCI_")
# C2F/F2C/CNAME bodies transcribed from configure's machine.h (they are ABI contracts,
# not inventions); FortranCInterface confirms the underscore convention matches.
set(C2F_BODY "(name) name##_")
set(F2C_BODY "(name) name##_")

# --- bucket 4: configure OPTIONS (13) — CMake owns these decisions from here ---
option(ENABLE_NLS         "Native Language Support"        ON)
option(ENABLE_MPI         "MPI support"                    OFF)
option(ENABLE_RELOCATABLE "Relocatable install"            OFF)
option(WITH_GUI  "Build the GUI"  ON)
option(WITH_XCOS "Build Xcos"     ON)
# … WITH_EIGEN WITH_FFTW WITH_HDF5 WITH_KLU WITH_MATIO WITH_OCAML WITH_TK WITH_UMFPACK …

# --- bucket 5: PACKAGE_* (7) — autoconf boilerplate, from project() ---
set(PACKAGE_NAME    "scilab")
set(PACKAGE_TARNAME "scilab")
set(PACKAGE_VERSION "${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}.${SCILAB_VERSION_MAINTENANCE}")
```

Add the matching `#cmakedefine`/`@VAR@` lines to `machine.h.cmake.in` for every macro in these buckets.

- [ ] **Step 2: Converge the FULL macro set (no bucket filter this time).**

```bash
cd scilab && cmake -S . -B build-cmake >/dev/null 2>&1
cd build-parity && python3 - <<'PY'
from parity.fingerprint import parse_defines
ref  = parse_defines(open("../modules/core/includes/machine.h").read())
cand = parse_defines(open("../build-cmake/generated-includes/machine.h").read())
print("missing:", sorted(set(ref) - set(cand)))
print("extra:  ", sorted(set(cand) - set(ref)))
print("changed:", sorted(k for k in set(ref) & set(cand) if ref[k] != cand[k]))
print("counts:", len(ref), len(cand))
PY
```
Expected: all three lists **empty**, counts equal. Iterate on whatever is named — for a pkg-config value mismatch, reproduce configure's exact spelling (check `modules/core/includes/machine.h` for the literal it emitted).

- [ ] **Step 3: Commit.**

```bash
git add scilab/cmake/ScilabMachineHeader.cmake scilab/cmake/machine.h.cmake.in
git commit -m "cmake: machine.h — pkg-config, Fortran mangling, options + PACKAGE buckets"
```

---

### Task 4: Arm the baseline, gate it, docs + CI (CONTROLLER-executed — long build)

**Files:**
- Modify: `scilab/build-parity/baseline-autotools.json`, `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml`

**Interfaces:**
- Consumes: the dimension (Task 1) + the generated header (Tasks 2–3).
- Produces: none (finalization).

- [ ] **Step 1: Arm the baseline with configure's reference macro set.** The reference comes from the SOURCE-TREE (configure's) header — deliberately a different file from the one `capture` reads, because the gate is "CMake's generated header == configure's header":

```bash
cd scilab/build-parity && python3 - <<'PY'
import json
from parity.fingerprint import parse_defines
b = json.load(open("baseline-autotools.json"))
assert "header_defines" not in b, "baseline already armed"
b["header_defines"] = {"machine.h": parse_defines(open("../modules/core/includes/machine.h").read())}
json.dump(b, open("baseline-autotools.json", "w"), indent=2, sort_keys=True)
print("armed:", len(b["header_defines"]["machine.h"]), "macros")
PY
```
Expected: ~184 macros armed (the count configure's header actually defines).

- [ ] **Step 2: Fault-inject the ARMED gate (a guard must be seen to fail).**

```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/rc-a.json cand >/dev/null && \
python3 - <<'PY'
import json
c = json.load(open("/tmp/rc-a.json"))
k = sorted(c["header_defines"]["machine.h"])[0]
c["header_defines"]["machine.h"][k] = "BOGUS"
json.dump(c, open("/tmp/rc-a-mut.json", "w")); print("mutated", k)
PY
python3 -m parity.diff baseline-autotools.json /tmp/rc-a-mut.json; echo "rc=$? (expect 1)"
```
Expected: `PARITY FAILED` naming `machine.h: macro changed: <macro>`, rc=1.

- [ ] **Step 3: From-scratch whole-tree gate.**

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && cmake --build build-cmake --target drop-in-all -j
cd build-parity && python3 -m parity.capture .. /tmp/final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: `PARITY OK` (68 dylibs + 2 executables + 24 jars + the semantic `machine.h`) + flag-facts rc=0.

- [ ] **Step 4: Update `docs/design/build-cmake-driver.md`.** Extend the "Generated headers + help" section: CMake now generates `machine.h` too — all 184 macros computed in CMake (5 buckets), proven by the **semantic** header dimension (the `#define` set, since it is not byte-identical to autoconf's, unlike `version.h`); still additive/coexistence-safe; the source-tree header resolves first until RC-e. Note the RC-a…RC-e decomposition and that RC-b/RC-c reuse this dimension.

- [ ] **Step 5: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`'s `set -e` block, extend check G:

```bash
      grep -q 'include(cmake/ScilabMachineHeader.cmake)' CMakeLists.txt
```

- [ ] **Step 6: Commit.**

```bash
git add scilab/build-parity/baseline-autotools.json docs/design/build-cmake-driver.md .gitlab-ci.yml
git commit -m "build-parity: arm the semantic machine.h gate; RC-a complete (docs + CI)"
```

---

## Self-Review

**Spec coverage:** spec §5.1 (semantic dimension: parse, capture, diff, transition rule, fault-injection) → Task 1 + Task 4 Step 2; §5.2 (the 5 buckets + `CMAKE_REQUIRED_*` pinning + `configure_file`) → Tasks 2–3; §5.3 (consumption/coexistence — generated-includes, source-tree untouched) → Task 2 Step 4 + the constraint block; §6 gate (from-scratch parity + semantic dimension + flag-facts) → Task 4 Step 3; §7 order → Tasks 1→4; §8 testing → Task 1 tests + Task 4 fault-injection. No spec requirement without a task.

**Placeholder scan:** the macro *lists* are enumerated by a concrete runnable command (Task 2 Step 1) rather than 184 hand-typed lines — the extraction is the step, `/tmp/machine-macros.txt` is the contract, and the convergence loops (Task 2 Step 5, Task 3 Step 2) name every remaining divergence, so nothing is left vague. All code steps carry real code; all commands carry expected output.

**Type consistency:** `parse_defines(header_text) -> {macro: value}` is defined in Task 1 and consumed unchanged in Tasks 2–4; the fingerprint key is `header_defines["machine.h"]` everywhere (capture, diff, tests, baseline arm); `SCILAB_GENERATED_INCLUDES` is the 1f-c variable reused in Task 2; the CI check name matches Task 4 Step 5.
