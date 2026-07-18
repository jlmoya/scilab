# Stage 1f-c — CMake generates version.h + the help post-step — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CMake generates `version.h` (byte-identical to configure's, consumed by the CMake native build) and runs the help build as a post-link `doc` target — the last two autotools code-generation bits inside CMake's ownership that this stage covers.

**Architecture:** `ScilabConfigure.cmake` `configure_file`s the existing `version.h.in` (`@ONLY`, values from `config.status`) into `build-cmake/generated-includes/version.h` — byte-identical because `version.h` is exactly `version.h.in` with three `@SCILAB_VERSION_*@` substitutions — and prepends that dir to the module include path so the CMake build consumes it. `ScilabHelp.cmake` adds a `BUILD_HELP`-gated `doc` target that runs the built `scilab-adv-cli` headless per locale (`xmltojar`). Coexistence is temporary: the autotools source-tree headers stay untouched and are deleted at retire-configure.

**Tech Stack:** CMake (`configure_file` + `config.status` parsing + custom target), the parity harness (unchanged — version.h stays byte-hashed), Scilab's headless `xmltojar` help build.

## Global Constraints

- **Coexistence is TEMPORARY (binding invariant).** The destination is autotools DELETED. Generated headers go to `build-cmake/generated-includes/`; the autotools source-tree `modules/core/includes/{version.h,machine.h}` are **untouched** (deleted at retire-configure, the sequenced next stage). A permanent dual generator is unacceptable.
- **version.h is byte-identical** — `configure_file(version.h.in … @ONLY)` reproduces configure's output exactly; the harness keeps byte-hashing it (NO harness change, NO semantic-header dimension in 1f-c).
- **machine.h is OUT of 1f-c** — it relocates to retire-configure (entangled with configure options + pkg-config substitutions).
- **Reproduce, don't improve; keep configure runnable.** NO edits to `configure.ac`, any `Makefile.am`, `machine.h.in`, or `version.h.in` (the CMake path *reads* `version.h.in` as a `configure_file` input — that is not an edit). `make` must still generate the headers + build help.
- **Help = the built app** — the `doc` target reproduces the top-level `Makefile`'s `doc:` recipe (headless env + per-locale `xmltojar`); it is opt-in (`BUILD_HELP`-gated), NOT on `drop-in-all`.
- **No AI-attribution in commit messages.**
- **Native side unchanged** — `-std=c++17` held; whole-tree parity must stay OK.

## File Structure

- `scilab/cmake/ScilabConfigure.cmake` — CREATE: generate `version.h` via `configure_file` + prepend the generated-includes dir.
- `scilab/cmake/ScilabHelp.cmake` — CREATE: the `BUILD_HELP`-gated `doc` target.
- `scilab/CMakeLists.txt` — MODIFY: `include()` both, after `project()` + before the module `foreach`; call `scilab_help_target()`.
- `docs/design/build-cmake-driver.md` — MODIFY: document version.h generation + the help post-step.
- `docs/design/build-cmake-maven-migration.md` — MODIFY: record the retirement endgame (spec §2).
- `.gitlab-ci.yml` — MODIFY: `sanity:cmake-driver` check that `ScilabConfigure.cmake` is wired.

---

### Task 1: CMake generates version.h + the build consumes it

**Files:**
- Create: `scilab/cmake/ScilabConfigure.cmake`
- Modify: `scilab/CMakeLists.txt` (include after `project()`, before the module `foreach`)

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR` (`ScilabToolchain.cmake`), `SCILAB_DEFAULT_INCLUDES` (`ScilabToolchain.cmake`), `CMAKE_BINARY_DIR` (post-`project()`).
- Produces: `build-cmake/generated-includes/version.h`; `SCILAB_DEFAULT_INCLUDES` gains `${CMAKE_BINARY_DIR}/generated-includes` as its FIRST entry.

- [ ] **Step 1: Create `scilab/cmake/ScilabConfigure.cmake`:**

```cmake
# scilab/cmake/ScilabConfigure.cmake — CMake generates version.h (Stage 1f-c).
#
# version.h is EXACTLY version.h.in with three @SCILAB_VERSION_*@ substitutions (the
# revision/timestamp are literals in the template, not substituted), so
# configure_file(@ONLY) with the config.status version values reproduces configure's
# version.h BYTE-FOR-BYTE — the harness keeps byte-hashing it, unchanged. Generated into
# ${CMAKE_BINARY_DIR}/generated-includes/, PREPENDED to the module include path so the
# CMake build consumes CMake's version.h; machine.h falls through to the source tree
# (configure's, untouched — coexistence, deleted at retire-configure). machine.h is NOT
# generated here (entangled with configure options/substitutions — retire-configure stage).
# Included AFTER project() (uses CMAKE_BINARY_DIR).

foreach(_v MAJOR MINOR MAINTENANCE)
  file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ver_line
       REGEX "^S\\[\"SCILAB_VERSION_${_v}\"\\]=")
  if(NOT _sci_ver_line)
    message(FATAL_ERROR "config.status has no S[\"SCILAB_VERSION_${_v}\"] — cannot generate version.h")
  endif()
  string(REGEX REPLACE "^S\\[\"SCILAB_VERSION_${_v}\"\\]=\"(.*)\"$" "\\1"
         SCILAB_VERSION_${_v} "${_sci_ver_line}")
endforeach()

set(SCILAB_GENERATED_INCLUDES ${CMAKE_BINARY_DIR}/generated-includes)
configure_file(${SCILAB_SOURCE_DIR}/modules/core/includes/version.h.in
               ${SCILAB_GENERATED_INCLUDES}/version.h @ONLY)

# Prepend so the CMake-generated version.h wins over the source-tree one; machine.h
# (absent here) resolves to modules/core/includes as before. Directory scope — consumed
# by the module add_subdirectory() calls that follow.
list(PREPEND SCILAB_DEFAULT_INCLUDES ${SCILAB_GENERATED_INCLUDES})
message(STATUS "CMake-generated version.h -> ${SCILAB_GENERATED_INCLUDES}/version.h "
               "(v${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}.${SCILAB_VERSION_MAINTENANCE})")
```

- [ ] **Step 2: Wire into `scilab/CMakeLists.txt`.** After `include(cmake/ScilabJava.cmake)` (and after `project()`), and **before** the first module `foreach(m …)`, add:

```cmake
# Stage 1f-c — CMake generates version.h into build-cmake/generated-includes/ and
# prepends it to SCILAB_DEFAULT_INCLUDES so the module builds below consume it.
include(cmake/ScilabConfigure.cmake)
```

- [ ] **Step 3: Configure and verify byte-identity + consumption (no full build needed).**

Run:
```bash
cd scilab && cmake -S . -B build-cmake >/tmp/cfg.log 2>&1; echo "configure rc=$?"
diff build-cmake/generated-includes/version.h modules/core/includes/version.h && echo "BYTE-IDENTICAL"
grep -c 'generated-includes' build-cmake/compile_commands.json | sed 's/^/generated-includes on compile lines: /'
```
Expected: configure rc=0; `BYTE-IDENTICAL` (the CMake-generated version.h equals configure's); `generated-includes` appears on the module compile lines (they consume it).

- [ ] **Step 4: Commit.**

```bash
git add scilab/cmake/ScilabConfigure.cmake scilab/CMakeLists.txt
git commit -m "cmake: generate version.h via configure_file (byte-identical), consumed by the build"
```

---

### Task 2: Help post-step — the `doc` target

**Files:**
- Create: `scilab/cmake/ScilabHelp.cmake`
- Modify: `scilab/CMakeLists.txt` (include + call `scilab_help_target()`, at the end)

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR`; `config.status` `BUILD_HELP_TRUE` + `ALL_LINGUAS_DOC`; the built `bin/scilab-adv-cli` (runtime).
- Produces: a top-level `doc` custom target (no-op stub when `BUILD_HELP` is off).

- [ ] **Step 1: Create `scilab/cmake/ScilabHelp.cmake`.** Transcribe the per-locale command + env from the **configured top-level `Makefile`'s `doc:` recipe** (which has `DOC_JAVA_XML_OPTS` expanded) — reproduce it exactly:

```cmake
# scilab/cmake/ScilabHelp.cmake — the help build as a CMake post-step (Stage 1f-c).
#
# `make doc` runs the BUILT scilab-adv-cli HEADLESS per locale (xmltojar) — help needs the
# running app (the circular dep), so this is a post-link, opt-in (BUILD_HELP-gated) target,
# NOT on drop-in-all. Reproduces the top-level Makefile's `doc:` recipe env + command
# EXACTLY (transcribe DOC_JAVA_XML_OPTS from the configured Makefile).

# BUILD_HELP gate + the doc locales, from config.status (automake conditional: _TRUE="" = on).
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_bh REGEX "^S\\[\"BUILD_HELP_TRUE\"\\]=")
string(REGEX REPLACE "^S\\[\"BUILD_HELP_TRUE\"\\]=\"(.*)\"$" "\\1" SCILAB_BUILD_HELP "${_sci_bh}")
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ll REGEX "^S\\[\"ALL_LINGUAS_DOC\"\\]=")
string(REGEX REPLACE "^S\\[\"ALL_LINGUAS_DOC\"\\]=\"(.*)\"$" "\\1" _sci_doc_langs "${_sci_ll}")
separate_arguments(_sci_doc_langs)   # "en_US fr_FR …" -> a CMake list

function(scilab_help_target)
  if(NOT SCILAB_BUILD_HELP STREQUAL "")
    add_custom_target(doc COMMENT "Help disabled (BUILD_HELP off — ./configure --enable-build-help)")
    return()
  endif()
  set(_cmds "")
  foreach(l ${_sci_doc_langs})
    # NB: transcribe the exact _JAVA_OPTIONS incl. DOC_JAVA_XML_OPTS from the configured Makefile.
    list(APPEND _cmds COMMAND ${CMAKE_COMMAND} -E env
         LANG=${l}.UTF-8 LC_ALL=C.UTF-8 SCI_DISABLE_TK=1 SCI_JAVA_ENABLE_HEADLESS=1
         "_JAVA_OPTIONS=-Djava.awt.headless=true" HOME=/tmp
         ${SCILAB_SOURCE_DIR}/bin/scilab-adv-cli -noatomsautoload -nb -l ${l} -nouserstartup
         -e "try xmltojar([],[],'${l}');catch disp(lasterror());exit(-1);end;exit(0);")
  endforeach()
  add_custom_target(doc ${_cmds}
    WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}
    USES_TERMINAL
    COMMENT "Building Scilab help (xmltojar) per locale via the built scilab-adv-cli")
endfunction()
```

- [ ] **Step 2: Wire into `scilab/CMakeLists.txt`** (at the very end, after the executables):

```cmake
# Stage 1f-c — the help build as an opt-in CMake post-step (needs the built app).
include(cmake/ScilabHelp.cmake)
scilab_help_target()
```

- [ ] **Step 3: Configure + structurally verify the target (do NOT run the slow help build here — that is the finalize acceptance).**

Run:
```bash
cd scilab && cmake -S . -B build-cmake >/dev/null 2>&1
cmake --build build-cmake --target doc -- -n 2>/dev/null | grep -c 'xmltojar' | sed 's/^/xmltojar invocations in the doc rule: /'
cmake --build build-cmake --target doc -- -n 2>/dev/null | grep -oE "xmltojar\(\[\],\[\],'[a-z_A-Z]+'\)" | head
```
Expected: the dry-run (`-n`) shows one `xmltojar([],[],'<locale>')` per `ALL_LINGUAS_DOC` locale (5), each with the headless env — but does NOT execute them.

- [ ] **Step 4: Commit.**

```bash
git add scilab/cmake/ScilabHelp.cmake scilab/CMakeLists.txt
git commit -m "cmake: doc target — help build as an opt-in post-step (built scilab-adv-cli, headless xmltojar)"
```

---

### Task 3: Finalize — from-scratch parity + help acceptance + docs + CI

**Files:**
- Modify: `docs/design/build-cmake-driver.md`, `docs/design/build-cmake-maven-migration.md`, `.gitlab-ci.yml`

**Interfaces:**
- Consumes: `ScilabConfigure.cmake` (Task 1), the `doc` target (Task 2).
- Produces: none (finalization).

- [ ] **Step 1: Full clean build (proves the modules consume CMake's version.h) + whole-tree gate.**

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && \
  cmake --build build-cmake --target drop-in-all -j
diff build-cmake/generated-includes/version.h modules/core/includes/version.h && echo "version.h BYTE-IDENTICAL"
cd build-parity && python3 -m parity.capture .. /tmp/final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: `version.h BYTE-IDENTICAL`; whole-tree **PARITY OK** (68 dylibs + 2 executables + 24 jars) + flag-facts rc=0 — the CMake build consumed CMake's version.h and stayed at parity.

- [ ] **Step 2: Help acceptance (en_US) — the real doc build + the browser.** Build the app first (Step 1 did), then run help for one locale + confirm the jar + open the browser:

```bash
cd scilab
LANG=en_US.UTF-8 LC_ALL=C.UTF-8 SCI_DISABLE_TK=1 SCI_JAVA_ENABLE_HEADLESS=1 \
  _JAVA_OPTIONS=-Djava.awt.headless=true HOME=/tmp timeout 900 \
  ./bin/scilab-adv-cli -noatomsautoload -nb -l en_US -nouserstartup \
  -e "try xmltojar([],[],'en_US');catch disp(lasterror());exit(-1);end;exit(0);" >/tmp/doc.log 2>&1
echo "doc rc=$?"; ls -la modules/helptools/jar/scilab_en_US_help.jar 2>/dev/null || \
  find modules -name 'scilab_en_US_help.jar' 2>/dev/null | head
```
Expected: rc=0; a `scilab_en_US_help.jar` produced. (Then a GUI `help('plot')` check that the browser opens is the behavioral confirmation — controller-run, left open per the one-app-instance rule.)

- [ ] **Step 3: Update `docs/design/build-cmake-driver.md`.** New subsection "Generated headers + help (Stage 1f-c)": CMake generates `version.h` via `configure_file(version.h.in @ONLY)` into `build-cmake/generated-includes/` (byte-identical; prepended to the include path so the build consumes it); `machine.h` is deferred to retire-configure; the `doc` target runs the built app headless per locale. Update the title/status to Stage 1f-c and the deferred list (retire-configure now leads, and owns machine.h + the semantic-header dimension).

- [ ] **Step 4: Record the retirement endgame in `docs/design/build-cmake-maven-migration.md`** (spec §2): coexistence is temporary; the sequence 1f-c → retire configure → retire make → Ant→Maven → zero coexistence, each deleting part of autotools. Make it an explicit section so the destination (autotools deleted) is on record.

- [ ] **Step 5: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`'s `set -e` block, add:

```bash
      # G. version.h generation is wired (ScilabConfigure included + generated-includes on the path)
      grep -q 'include(cmake/ScilabConfigure.cmake)' CMakeLists.txt
      grep -q 'scilab_help_target()' CMakeLists.txt
```

- [ ] **Step 6: Commit.**

```bash
git add docs/design/build-cmake-driver.md docs/design/build-cmake-maven-migration.md .gitlab-ci.yml
git commit -m "cmake: Stage 1f-c complete — CMake generates version.h + the help post-step (docs + CI)"
```

---

## Self-Review

**Spec coverage:** spec §5.2 (version.h via configure_file, byte-identical, consumed) → Task 1; §5.4 (help doc target, BUILD_HELP-gated, headless per-locale xmltojar) → Task 2; §6.2 (from-scratch consume parity) + §6.3 (help jar + browser) → Task 3; §2 (retirement endgame in the migration doc) → Task 3 Step 4; §7 order → Tasks 1→3. The spec's §5.1 (machine.h) + §5.3 (semantic-header dimension) are explicitly relocated to retire-configure by the scope-refinement note — correctly ABSENT here.

**Placeholder scan:** every code step is complete; the one transcription instruction (Task 2 Step 1: copy `DOC_JAVA_XML_OPTS` from the configured Makefile) is a concrete lookup, not a placeholder — the surrounding command is fully specified.

**Type consistency:** `SCILAB_GENERATED_INCLUDES` / `generated-includes` path, `SCILAB_DEFAULT_INCLUDES` prepend, `scilab_help_target()`, and the `doc` target name are consistent across Tasks 1–3 and the CI check. `SCILAB_VERSION_{MAJOR,MINOR,MAINTENANCE}` match the `version.h.in` `@VARS@` and the `config.status` keys.
