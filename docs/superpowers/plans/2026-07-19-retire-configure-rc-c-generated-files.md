# Retire-configure RC-c — CMake generates the configure-substituted files — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CMake generates 9 of the files `config.status` substitutes, plus `Version.incl`, byte-identical to configure's copies — gated by extending the parity harness's byte-hash `generated` dimension from 3 entries to 13.

**Architecture:** Extend `GENERATED_FILES` and arm the baseline *first*, so the generation lands against a real gate. Then a new `cmake/ScilabGeneratedFiles.cmake` does one `configure_file(@ONLY)` per file plus `Version.incl`'s conditional write. Byte-identity is the target, following the `version.h` precedent.

**Tech Stack:** CMake `configure_file`, Python 3 + pytest (harness).

## Global Constraints

- **CMake COMPUTES its values — never reads them out of `config.status`.** The version triple comes from `ScilabConfigure.cmake` (which already computes it); boolean gates come from CMake-side policy. This is the constraint every RC stage has carried.
- **BYTE-IDENTICAL is the target, not semantic.** These are scalar-substitution templates — the `version.h` shape. A file that is *not* byte-identical is a **finding to investigate and report**, never grounds to relax the gate to semantic comparison. Do not add normalization to make a mismatch disappear.
- **ADDITIVE and rollback-free.** No edits to `configure.ac`, any `Makefile.am`/`Makefile.in`, or any `*.in` template. `make` must keep generating every file exactly as it does today.
- **REPRODUCE, don't improve** — with two documented exceptions, both stated in the spec: `etc/Info.plist` is reproduced faithfully despite being vestigial, and configure's wall-clock year-bump is deliberately **not** reproduced.
- **No AI-attribution trailers in any commit** — no `Co-Authored-By`, no "Generated with", no `Claude-Session`.
- **The gate must be seen to FAIL before it is trusted.**
- The full `build-parity` suite must stay green (160 passing at RC-c start, HEAD `adf2448d7b6`).

## File Structure

| File | Responsibility |
|---|---|
| `scilab/build-parity/parity/capture.py` | **modify** — `GENERATED_FILES` 3 → 13 |
| `scilab/build-parity/tests/test_capture.py` | **modify** — the list's composition is asserted there; update and strengthen |
| `scilab/build-parity/baseline-autotools.json` | **modify** — armed with the 10 new entries |
| `scilab/cmake/ScilabGeneratedFiles.cmake` | **new** — one `configure_file` per file + `Version.incl` |
| `scilab/CMakeLists.txt` | **modify** — include it |
| `docs/design/build-cmake-driver.md`, `docs/design/build-cmake-maven-migration.md`, `.gitlab-ci.yml` | **modify** — docs + CI + the Stage-2 dependency |

---

### Task 1: Extend the byte-hash gate and arm it

**Files:**
- Modify: `scilab/build-parity/parity/capture.py`, `scilab/build-parity/tests/test_capture.py`, `scilab/build-parity/baseline-autotools.json`

**Interfaces:**
- Consumes: the existing `normalize_path`/`roots` + sha256 mechanism, unchanged.
- Produces: a `generated` map with 13 entries + the macro-bin manifest key.

- [ ] **Step 1: Confirm the inventory against the live tree before touching anything.**

```bash
cd scilab && for f in build.incl.xml scilab.pc scilab.properties etc/logging.properties \
  etc/modules.xml etc/Info.plist modules/helptools/etc/SciDocConf.xml \
  modules/atoms/etc/repositories modules/atoms/tests/unit_tests/repositories.orig Version.incl; do
  printf '%-52s %s\n' "$f" "$([ -f "$f" ] && echo present || echo MISSING)"
done
```
Expected: all ten present. `Version.incl` is the one to watch — it is written by a conditional shell `echo` in `configure.ac:2965`, not by `AC_CONFIG_FILES`, so its absence would mean the guard at `configure.ac:2961` did not fire on this tree. If it is missing, **stop and report** rather than dropping it from the list.

- [ ] **Step 2: Write the failing test.**

```python
# in scilab/build-parity/tests/test_capture.py -- replace the existing
# assertion that pins GENERATED_FILES' composition
def test_generated_files_covers_the_rc_c_inventory():
    """The 9 configure-substituted files RC-c generates, plus Version.incl, plus the
    3 that predate it. Pinned by exact set: this list IS the gate's coverage, and a
    silent shrink is exactly the failure mode the campaign keeps rediscovering.

    NOT here on purpose -- etc/classpath.xml is, but scilab-lib.properties and
    scilab-lib-doc.properties are deferred to Stage 2 (Ant->Maven) along with the jar
    -path search that feeds them; see the RC-c design doc S4.
    """
    from parity.capture import GENERATED_FILES
    assert set(GENERATED_FILES) == {
        "etc/classpath.xml",
        "modules/core/includes/machine.h",
        "modules/core/includes/version.h",
        "build.incl.xml",
        "scilab.pc",
        "scilab.properties",
        "etc/logging.properties",
        "etc/modules.xml",
        "etc/Info.plist",
        "modules/helptools/etc/SciDocConf.xml",
        "modules/atoms/etc/repositories",
        "modules/atoms/tests/unit_tests/repositories.orig",
        "Version.incl",
    }
```

- [ ] **Step 3: Run it and watch it fail.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_capture.py -q
```
Expected: FAIL, the assertion showing the 10 missing entries.

- [ ] **Step 4: Extend `GENERATED_FILES`.** In `parity/capture.py`, replace the 3-entry list with the 13, and extend its comment to record why these and not the other two:

```python
# Files config.status substitutes, byte-hashed after root normalization. The three
# original entries plus RC-c's ten. Byte hash (not semantic) is right here: these are
# scalar-substitution templates -- configure_file(@ONLY) reproduces autoconf's @VAR@
# expansion exactly when the values match, which version.h proved. machine.h is the
# exception that needed a semantic dimension, for a reason none of these share.
#
# Version.incl is NOT an AC_CONFIG_FILES entry -- it is written by a conditional shell
# echo at configure.ac:2965 -- so an inventory built from config.status misses it
# entirely, while build.incl.xml:154 stamps every jar's Specification-Version from it.
#
# scilab-lib.properties and scilab-lib-doc.properties are deliberately ABSENT: they and
# etc/classpath.xml carry 115 of the inventory's 142 substitutions, all jar paths from
# AC_JAVA_CHECK_JAR's filesystem search, and are consumed only by the Ant build that
# Stage 2 replaces. (etc/classpath.xml predates RC-c and stays.) RC-c design doc S4.
GENERATED_FILES = [
    "etc/classpath.xml",
    "modules/core/includes/machine.h",
    "modules/core/includes/version.h",
    "build.incl.xml",
    "scilab.pc",
    "scilab.properties",
    "etc/logging.properties",
    "etc/modules.xml",
    "etc/Info.plist",
    "modules/helptools/etc/SciDocConf.xml",
    "modules/atoms/etc/repositories",
    "modules/atoms/tests/unit_tests/repositories.orig",
    "Version.incl",
]
```

- [ ] **Step 5: Run the test to green, then the full suite.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_capture.py -q && python3 -m pytest -q | tail -1
```
Expected: the new test passes. Other tests may need updating if they assert on the `generated` map's exact keys — update them to match; do **not** weaken what they assert.

- [ ] **Step 6: Arm the baseline.**

```bash
cd scilab/build-parity && python3 - <<'PY'
import json
from parity.capture import fingerprint_build, _default_roots
fresh = fingerprint_build("..", _default_roots(".."), build_id="arm")
b = json.load(open("baseline-autotools.json"))
before = set(b["generated"])
b["generated"].update(fresh["generated"])
json.dump(b, open("baseline-autotools.json", "w"), indent=2, sort_keys=True)
print("armed:", sorted(set(b["generated"]) - before))
print("total generated entries:", len(b["generated"]))
PY
```
Expected: the 10 new entries listed; 14 total (13 files + the macro-bin manifest key).

- [ ] **Step 7: Fault-inject the armed gate — it must be seen to fail.**

```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/rcc.json cand >/dev/null && python3 - <<'PY'
import json
c = json.load(open("/tmp/rcc.json"))
c["generated"]["scilab.pc"] = "0" * 64
json.dump(c, open("/tmp/rcc-mut.json", "w")); print("mutated scilab.pc's hash")
PY
python3 -m parity.diff baseline-autotools.json /tmp/rcc-mut.json; echo "rc=$? (expect 1)"
echo "--- and the clean capture ---"
python3 -m parity.diff baseline-autotools.json /tmp/rcc.json; echo "rc=$? (expect 0)"
```
Expected: the mutated run prints `PARITY FAILED` naming `scilab.pc` and returns rc=1; the clean run prints `PARITY OK`, rc=0.

- [ ] **Step 8: Commit.**

```bash
git add scilab/build-parity/parity/capture.py scilab/build-parity/tests/test_capture.py \
        scilab/build-parity/baseline-autotools.json
git commit -m "build-parity: extend the generated-file gate to RC-c's inventory"
```

---

### Task 2: Generate the files in CMake

**Files:**
- Create: `scilab/cmake/ScilabGeneratedFiles.cmake`
- Modify: `scilab/CMakeLists.txt`

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR`, `SCILAB_VERSION_MAJOR`/`MINOR`/`MAINTENANCE` (all from `cmake/ScilabConfigure.cmake`).
- Produces: the 9 files + `Version.incl` under `build-cmake/generated/`.

- [ ] **Step 1: Inventory each template's substitution variables before writing anything.**

```bash
cd scilab && for t in build.incl.xml scilab.pc scilab.properties etc/logging.properties \
  etc/modules.xml etc/Info.plist modules/helptools/etc/SciDocConf.xml \
  modules/atoms/etc/repositories modules/atoms/tests/unit_tests/repositories.orig; do
  printf '=== %s ===\n' "$t"
  grep -oE '@[A-Za-z_][A-Za-z0-9_]*@' "$t.in" | sort -u | tr '\n' ' '; echo
done
```
This is the contract: every variable printed must be set before its `configure_file`, or it substitutes empty and the byte-comparison fails naming the file. Record the full list in your report — it is the checklist for Step 2.

- [ ] **Step 2: Create `scilab/cmake/ScilabGeneratedFiles.cmake`.**

```cmake
# scilab/cmake/ScilabGeneratedFiles.cmake -- the configure-substituted files (RC-c).
#
# One configure_file(@ONLY) per file. BYTE-IDENTICAL to configure's copies is the
# target, not semantic equivalence: these are scalar-substitution templates, the same
# shape as version.h, which reproduces byte-for-byte. (machine.h needed a semantic
# dimension because autoconf renders un-defined macros as `/* #undef X */`; nothing
# here has that property.) The parity harness's `generated` dimension byte-compares
# all of them, so a wrong or unset variable is named, never silent.
#
# Values are COMPUTED here or come from ScilabConfigure.cmake -- never read out of
# config.status, which is the dependency the retire-configure stages exist to remove.
#
# NOT REPRODUCED, deliberately: configure.ac:2930-2937 compares `date +%Y` against a
# year hardcoded in banner.cpp and, on mismatch, runs `sed -i` over banner.cpp AND
# etc/Info.plist.in -- i.e. the build system rewrites its own tracked sources on a
# wall-clock trigger. That is a wart worth dropping rather than carrying forward. If
# the year bump is wanted, it belongs in a release script, not in configure.

set(SCILAB_GENERATED_DIR ${CMAKE_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${SCILAB_GENERATED_DIR})

# --- values ---------------------------------------------------------------
# The version triple is already computed by ScilabConfigure.cmake (RC-a).
# Add the remaining scalars here, each traced to its configure.ac origin.
# (Fill from Step 1's inventory -- every @VAR@ in every template below.)

# --- the files ------------------------------------------------------------
# build.incl.xml has ZERO substitutions -- output is byte-identical to its template
# (verified: `grep -c '@' build.incl.xml.in` is 0). configure_file still handles it
# correctly; it is listed for completeness of the migration, not because it needs work.
foreach(_f build.incl.xml scilab.pc scilab.properties etc/logging.properties
           etc/modules.xml etc/Info.plist
           modules/helptools/etc/SciDocConf.xml
           modules/atoms/etc/repositories
           modules/atoms/tests/unit_tests/repositories.orig)
  get_filename_component(_d ${SCILAB_GENERATED_DIR}/${_f} DIRECTORY)
  file(MAKE_DIRECTORY ${_d})
  configure_file(${SCILAB_SOURCE_DIR}/${_f}.in ${SCILAB_GENERATED_DIR}/${_f} @ONLY)
endforeach()

# Version.incl -- NOT an AC_CONFIG_FILES entry. configure.ac:2965 writes it with a raw
# shell echo, guarded (configure.ac:2961) by a comparison against a version string
# scraped out of modules/gui/images/icons/aboutscilab.svg. build.incl.xml:154 stamps
# every jar's Specification-Version from it, so it matters despite being invisible to
# any inventory built from config.status.
file(WRITE ${SCILAB_GENERATED_DIR}/Version.incl
     "SCIVERSION=scilab-branch-${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}\n")
```

**Two things to get right, both of which a mechanical implementation gets wrong:**

- **`etc/modules.xml`'s `helptools` entry is hardcoded** `activate="yes"`, *not* `@HELP_ENABLE@` — see the template's own comment at `etc/modules.xml.in:79-82`. Do not "fix" it into a flag mapping. Since you are `configure_file`-ing the template rather than authoring the XML, this is preserved for free — but verify it in the output.
- **`Version.incl`'s exact bytes** — reproduce configure's line including its trailing newline. The byte-comparison will name it if not.

- [ ] **Step 3: Include it from the driver.** In `scilab/CMakeLists.txt`, immediately after `include(cmake/ScilabFlags.cmake)`:

```cmake
# RC-c -- the configure-substituted files, generated by CMake.
include(cmake/ScilabGeneratedFiles.cmake)
```

- [ ] **Step 4: Prove byte-identity, file by file.**

```bash
cd scilab && rm -rf /tmp/rcc-verify && cmake -S . -B /tmp/rcc-verify >/dev/null 2>&1
for f in build.incl.xml scilab.pc scilab.properties etc/logging.properties \
  etc/modules.xml etc/Info.plist modules/helptools/etc/SciDocConf.xml \
  modules/atoms/etc/repositories modules/atoms/tests/unit_tests/repositories.orig Version.incl; do
  if diff -q "$f" "/tmp/rcc-verify/generated/$f" >/dev/null 2>&1; then
    printf '  %-52s IDENTICAL\n' "$f"
  else
    printf '  %-52s DIFFERS\n' "$f"; diff "$f" "/tmp/rcc-verify/generated/$f" | head -5
  fi
done
```
Expected: all ten `IDENTICAL`. **A `DIFFERS` is a finding to investigate and report — not something to normalize away.** Report the diff verbatim if any file differs; the likely causes are an unset variable, a trailing-newline mismatch, or an autoconf quoting behavior worth understanding rather than papering over.

- [ ] **Step 5: Confirm coexistence is intact and run the suite.**

```bash
cd scilab && git status --short -- '*.in' configure.ac '*/Makefile.am' | head
cd build-parity && python3 -m pytest -q | tail -1
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
echo "flag gate rc=$?"
```
Expected: no template/`configure.ac`/`Makefile.am` modifications; suite green; flag gate rc=0.

- [ ] **Step 6: Commit.**

```bash
git add scilab/cmake/ScilabGeneratedFiles.cmake scilab/CMakeLists.txt
git commit -m "cmake: generate the configure-substituted files (RC-c)"
```

---

### Task 3: From-scratch gate, docs + CI (CONTROLLER-executed — long build)

**Files:**
- Modify: `docs/design/build-cmake-driver.md`, `docs/design/build-cmake-maven-migration.md`, `.gitlab-ci.yml`

- [ ] **Step 1: From-scratch whole-tree gate.**

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && cmake --build build-cmake --target drop-in-all -j
cd build-parity && python3 -m parity.capture .. /tmp/rcc-final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/rcc-final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
echo "rc=$?"
```
Expected: `PARITY OK` (68 dylibs + 2 executables + 24 jars + 13 generated files + the semantic header + the derived flag facts) and the flag gate rc=0.

- [ ] **Step 2: Update `docs/design/build-cmake-driver.md`.** Add a "Generated files — produced by CMake (RC-c)" section: the 9 + `Version.incl`, byte-identical via `configure_file(@ONLY)`; `Version.incl`'s invisibility to `config.status`-derived inventories and why it matters (jar `Specification-Version`); `etc/Info.plist` recorded as **vestigial** with the `package-macos.sh:106-133` evidence, reproduced faithfully but flagged for a cleanup pass; the deliberately-unreproduced year-bump; and the corrected inventory figure (**12**, not "~21").

- [ ] **Step 3: Record the Stage-2 dependency in `docs/design/build-cmake-maven-migration.md` §12.** The three jar-path files (`etc/classpath.xml`, `scilab-lib.properties`, `scilab-lib-doc.properties`) are deferred to Stage 2 because their 115 substitutions are jar paths from `AC_JAVA_CHECK_JAR`'s filesystem search, consumed only by the Ant build Maven replaces. State the consequence plainly: **RC-e cannot delete `./configure` until Stage 2 lands.** That is a hard ordering constraint, not a preference.

- [ ] **Step 4: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`, extend the wiring checks:

```bash
      # J. Retire-configure RC-c is wired: CMake generates the configure-substituted
      #    files. A deleted include would leave them to configure alone -- which still
      #    works during coexistence, so nothing else would notice until RC-e.
      grep -q 'include(cmake/ScilabGeneratedFiles.cmake)' CMakeLists.txt
```

- [ ] **Step 5: Commit.**

```bash
git add docs/design/build-cmake-driver.md docs/design/build-cmake-maven-migration.md .gitlab-ci.yml
git commit -m "build-parity: RC-c complete — generated files (docs + CI)"
```

---

## Self-Review

**Spec coverage:** §5.1 (extend the gate, arm, fault-inject) → Task 1; §5.2 (`ScilabGeneratedFiles.cmake`, byte-identity, the two traps) → Task 2; §5.3 (coexistence untouched) → Task 2 Step 5; §6.1–6.3 (gate + byte-identity + Info.plist recorded not deleted) → Task 1 Step 7, Task 2 Step 4, Task 3 Step 2; §6.4–6.5 (year-bump divergence documented) → Task 2 Step 2's comment + Task 3 Step 2; §4's Stage-2 dependency → Task 3 Step 3; §8 (testing) → Task 1 Steps 2/7, Task 2 Step 4, Task 3 Step 1. No spec requirement lacks a task.

**Placeholder scan:** Task 2 Step 2's `--- values ---` block is filled from Step 1's mechanical inventory rather than hand-listed here, because the variable set is a property of the templates that must be read from them, not transcribed into a plan that could drift from them. Every other step carries runnable code or a concrete command with expected output. The one figure the plan cannot pre-compute — which templates need which variables — is what Step 1 produces and Step 4 verifies.

**Type consistency:** `GENERATED_FILES` is a list of repo-relative path strings in Task 1 and consumed as such by the existing capture loop; `SCILAB_GENERATED_DIR` is defined in Task 2 Step 2 and used in the same file; `SCILAB_VERSION_MAJOR`/`MINOR` are the names `ScilabConfigure.cmake` already sets (RC-a), consumed unchanged in `Version.incl`'s write.
