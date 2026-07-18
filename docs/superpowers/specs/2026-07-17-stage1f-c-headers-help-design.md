# Stage 1f-c — CMake generates machine.h/version.h + the help post-step — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-17

> **SCOPE REFINEMENT (2026-07-17, post-approval — governs the plan):** grounding the plan revealed
> two things that shrink 1f-c. (1) **`machine.h` moves to the retire-configure stage.** Only ~62 of
> its ~100 macros are pure CMake-probeable; the rest are configure options + pkg-config substitutions
> (`ENABLE_*`/`WITH_*`/`CURL_*`/Fortran-mangling) that retire-configure takes over anyway — generating
> it there avoids a circular `config.status` read-back (user decision). (2) **`version.h` is
> byte-identical**, so **no semantic-header dimension is needed in 1f-c**: `version.h` is exactly
> `version.h.in` with three `@SCILAB_VERSION_*@` substitutions, so `configure_file(version.h.in …
> @ONLY)` reproduces it byte-for-byte and the harness keeps byte-hashing it, unchanged. **Final 1f-c
> scope = CMake generates `version.h` (byte-identical) + the help post-step + docs/CI.** The
> **semantic-header dimension (§5.3) and `machine.h` generation (§5.1) relocate to retire-configure**;
> §§5.1/5.3/6.1's semantic-compare language applies THERE, not here. Everything else below stands.
**Depends on:** Stage 1f-b (CMake drives the whole native app + the 24 Java jars via `sci-java-all`;
the parity harness has native + rpath + jar dimensions; HEAD `689be760fa4`). Strategy:
`docs/design/build-cmake-maven-migration.md`; driver usage: `docs/design/build-cmake-driver.md`;
the header-generation idea originates as spec §11 of the 1f-a design.

## 1. Goal

Move the last two pieces of autotools **code generation** into CMake's ownership: CMake generates
`modules/core/includes/machine.h` + `version.h` (porting `configure`'s feature probes), and CMake
runs the **help** build as a post-link step (it needs the built app). Both are proven by the parity
harness. `./configure` still runs after 1f-c (for the `SCI_*FLAGS` + `build.incl.xml` it computes) —
**fully retiring `configure` is the immediately-following stage** (§2), not this one.

## 2. Coexistence is temporary — the retirement endgame (binding invariant)

**Coexistence is the migration scaffold, NOT the destination.** Every stage so far is additive and
rollback-free so each step is parity-proven and reversible — but the destination is unambiguous:
**`./configure && make` and Ant are DELETED; `cmake` + Maven are the only build.** A permanent
dual-build is explicitly unacceptable. Coexistence shrinks to zero across these committed stages,
each of which *deletes* part of autotools:

1. **1f-c (this stage):** CMake owns `machine.h`/`version.h` generation + the help build; `configure`'s
   header-generation becomes **dead code to be deleted at step 2** (not a kept parallel generator).
2. **Retire `configure`:** CMake computes the `SCI_*FLAGS` and generates `build.incl.xml` →
   `./configure` + `config.status` **deleted**.
3. **Retire `make`:** the `Makefile.am`/`Makefile.in` **deleted** → the native side is CMake-only.
4. **Stage 2 (Ant→Maven):** Ant + `build.incl.xml` gone → Maven is the sole Java build.
5. **End state:** autotools + Ant fully removed; **zero coexistence.**

1f-c is a down payment on step 2 — CMake is *proven* to generate the correct headers, so step 2 only
has to remove `configure`, not also invent header generation. The migration doc
(`build-cmake-maven-migration.md`) gains this sequence explicitly.

## 3. Scope

**In scope (1f-c):**
- **`ScilabConfigure.cmake`** — port `configure`'s ~150 `machine.h` probes to CMake `check_*` +
  `configure_file` a CMake-authored template → `machine.h`; compute + generate `version.h` (version
  macros + git revision + commit timestamp). The **CMake module builds consume these** (the CMake
  native build no longer needs `configure`'s headers).
- **A semantic-header parity dimension** in the harness: compare the `#define` SET of the two headers
  (not the bytes), with `version.h`'s volatile git-revision/timestamp normalized. Re-baseline.
- **The help post-step** — a CMake `doc` target (`BUILD_HELP`-gated) running the built
  `scilab-adv-cli` headless per locale (`xmltojar`) to build the help jars.
- Docs (`build-cmake-driver.md` + the migration doc's endgame) + CI.

**Out of scope (the next stage — "retire configure"):**
- CMake computing the `SCI_*FLAGS`, generating `build.incl.xml`, owning the macros build, and
  **deleting** `./configure`/`config.status`. (After 1f-c, CMake still reads `config.status` for the
  flags + the 4 tool/gate keys.)
- Retiring `make`; Ant→Maven (Stage 2).
- Any change to `configure.ac`, `Makefile.am`, `machine.h.in`/`version.h.in` (the CMake path uses its
  OWN template; the autotools templates stay for `configure` until step 2 deletes them).

## 4. Background (grounded)

- **`machine.h`** (587 lines) is `AC_CONFIG_HEADERS`-generated from `machine.h.in`: ~150 `#define`s —
  **102 `HAVE_*`** (headers/functions/features) + a few `SIZEOF_*`/`PACKAGE_*`/`STDC`, plus `#undef`
  lines for absent features. A textbook autoconf config header — each probe maps to a CMake
  `check_include_file` / `check_symbol_exists` / `check_function_exists` / `check_type_size`.
- **`version.h`** (35 lines) = version macros (`@SCILAB_VERSION_MAJOR@` … from `configure.ac`) +
  `SCI_VERSION_REVISION` (git hash) + `SCI_VERSION_TIMESTAMP` (commit epoch), the latter two baked by
  `configure` from `git`. Both are per-commit **volatile**.
- **Help** = `make doc` (`BUILD_HELP`-gated): for each locale in `ALL_LINGUAS_DOC`, run the built
  `scilab-adv-cli` **headless** (`SCI_JAVA_ENABLE_HEADLESS=1`, `_JAVA_OPTIONS=-Djava.awt.headless=true`,
  `SCI_DISABLE_TK=1`, `HOME=/tmp`, `-noatomsautoload -nb -l <locale> -nouserstartup`) with
  `xmltojar([],[],'<locale>')`. Produces per-locale help jars. No display needed. The circular dep
  (help needs the running app) is why it is a post-link step forever.

## 5. Architecture

### 5.1 `machine.h` generation (`scilab/cmake/ScilabConfigure.cmake`)
Run the ~150 probes with CMake's `Check*` modules, keyed to the exact `machine.h.in` macro names
(`HAVE_<HEADER>_H`, `HAVE_<FUNC>`, `SIZEOF_<TYPE>`, `STDC_HEADERS`, `PACKAGE_*`, …). Set a CMake
variable per macro, then `configure_file(machine.h.cmake.in machine.h)` (a CMake-authored template
using `#cmakedefine`) into `${CMAKE_BINARY_DIR}/generated-includes/`. Prepend that dir to the module
include path so **CMake compiles consume the CMake-generated header** (the autotools source-tree
header is untouched — `make` still uses `configure`'s). The macro NAMES + VALUES are the contract;
the harness (§5.3) proves the set matches.

### 5.2 `version.h` generation
Read the version from `configure.ac` (`SCILAB_VERSION_{MAJOR,MINOR,MAINTENANCE}`), the revision from
`git rev-parse HEAD`, the timestamp from the commit epoch; `configure_file` a CMake template →
`version.h` in the same generated-includes dir. The revision + timestamp are volatile (they differ
from the baseline whenever HEAD moves) — handled by §5.3's normalization.

### 5.3 Semantic-header parity dimension (harness)
`machine.h`/`version.h` are currently byte-hashed in the fingerprint's `generated` section. A
CMake-generated header is never byte-identical to autoconf's (comment style, `#define` vs
`/* #undef */`, ordering) — exactly the autotools-vs-CMake spelling gap the flag-facts check already
handles for compiler flags. So for these two files the harness switches to a **semantic compare**:
parse each into `{macro: value}` (a bare `#define X` → value `""`; an absent/`#undef` macro →
absent), diff the maps. For `version.h`, normalize the volatile `SCI_VERSION_REVISION` +
`SCI_VERSION_TIMESTAMP` (+ the `_STRING`/`_WIDE_STRING` that embed the version) to placeholders before
compare — the STABLE macros (`MAJOR`/`MINOR`/`MAINTENANCE`) are gated, the git fields are not. The
baseline stores the parsed `#define` set (from the pure-autotools headers). Fault-injected: flip a
`HAVE_X`, drop a macro → parity fails naming the macro.

### 5.4 Help post-step (`scilab/cmake/ScilabHelp.cmake` or in the driver)
A CMake `doc` custom target, created only when `BUILD_HELP` is on (from `config.status`, like the
Java gates), that depends on the built `scilab-adv-cli` and runs it once per `ALL_LINGUAS_DOC` locale
with the exact Makefile env + `xmltojar([],[],'<locale>')`. Not wired onto `drop-in-all` by default
(help is slow + opt-in); a top-level `doc` target. Output jars land where `make doc` puts them.

## 6. The gate & acceptance

1. **Whole-tree parity OK** (native + rpath + jars unchanged) **+ the semantic-header dimension**:
   the CMake-generated `machine.h`/`version.h` `#define` sets match the autotools baseline (version.h
   git-fields normalized). The harness names any divergent macro.
2. **The CMake native build consumes the generated headers** — a from-scratch `drop-in-all` with the
   generated-includes dir on the include path builds + stays at full parity (proving the generated
   headers are not just shape-equal but *compile the same app*).
3. **Help:** the `doc` target produces the per-locale help jars (at least `en_US`); a behavioral check
   that the help browser opens a known entry on the built app.
4. **Coexistence still recovers:** `make` still generates `configure`'s headers + builds help
   (exercised during the re-baseline).

## 7. Migration mechanics & rollback

- **Order:** (1) semantic-header harness dimension + fault-injection + re-baseline (from the
  autotools headers). (2) `version.h` generation + parity (git-field normalization proven across a
  commit). (3) `machine.h` probe port + generation + parity (the big mechanical piece; the semantic
  diff names any mis-ported probe). (4) CMake build consumes the generated-includes; from-scratch
  parity. (5) help `doc` target + acceptance. (6) docs (incl. the migration-doc endgame §2) + CI.
- **Rollback is free:** the generated headers go to `build-cmake/generated-includes/` (the source-tree
  autotools headers are untouched); the CMake files are additive; `make` recovers everything. No
  `configure.ac`/`Makefile.am`/`*.in` change.

## 8. Testing

- The **semantic-header harness dimension** is the primary test (fault-injected: flip/drop a macro →
  fail); the `version.h` normalization is seen to work (regenerate at a different commit → stable
  macros still parity-OK, git fields ignored).
- The **from-scratch consume** build (§6.2) proves the generated headers compile the same app.
- The **help acceptance** (§6.3) is behavioral.
- CI: `sanity:cmake-driver` gains a check that `ScilabConfigure.cmake` is wired + the generated-includes
  dir is on the include path; `parity:cmake-drop-in` covers the semantic-header dimension automatically.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A mis-ported probe (`HAVE_X` CMake computes wrong) | The semantic-header diff names the exact divergent macro; the from-scratch consume build fails if a wrong macro changes codegen. |
| `version.h` git-field volatility flaps the gate | Normalize `REVISION`/`TIMESTAMP`/version-strings before compare; proven by a cross-commit regenerate (the MANIFEST-DSTAMP precedent). |
| CMake `check_*` semantics differ subtly from autoconf (e.g. cross-compile, cache) | This is a native host build (not cross); pin `CMAKE_REQUIRED_*` to the configured flags; the harness is the arbiter — a difference shows as a macro diff, not a silent pass. |
| The generated-includes dir shadows a real header wrongly | Prepend ONLY the two generated headers' dir; scope tightly; the from-scratch parity build catches a wrong shadow. |
| Help build is slow / locale-dependent | `doc` is opt-in (not on `drop-in-all`); gate the acceptance on `en_US`; the env matches the Makefile exactly (headless, HOME=/tmp). |

## 10. Success criteria

- CMake generates `machine.h` + `version.h` (into `build-cmake/generated-includes/`); the CMake native
  build consumes them and stays **whole-tree PARITY OK** (semantic-header dimension included, version.h
  git-fields normalized); the harness catches a flipped/dropped macro.
- The `doc` target builds the per-locale help jars on the CMake-built app; the help browser opens.
- `make` still generates `configure`'s headers + builds help (coexistence recovers).
- The migration doc records the retirement endgame (§2); "retire configure" is the sequenced next stage.
