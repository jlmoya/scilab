# Retire-configure RC-a — CMake generates machine.h + the semantic-header parity dimension — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-18
**Depends on:** Stage 1f-c (CMake generates `version.h` into `build-cmake/generated-includes/` +
prepends it to the include path; the help `doc` target; HEAD `fc7a2be8efb`). Strategy:
`docs/design/build-cmake-maven-migration.md` §12 (the retirement endgame). `machine.h` was
explicitly deferred from 1f-c to here.

## 1. Goal

Make CMake **generate `modules/core/includes/machine.h` by computing every value itself** (not by
copying `config.status`), and prove it equivalent to configure's `machine.h` with a **new semantic
header-parity dimension** in the harness. This is the first sub-stage of retire-configure (RC-a); it
removes the last configure dependency for the *generated header* half of `machine.h` and builds the
semantic dimension that RC-b/RC-c reuse. `./configure` still runs after RC-a (for the `SCI_*FLAGS`,
`build.incl.xml`, the macros build, the option/decision inputs) — deleting `configure` is RC-e.

## 2. The retire-configure decomposition (context)

Retire-configure is decomposed into independently parity-provable sub-stages, each its own spec/plan:
**RC-a** (this: `machine.h` + the semantic-header dimension) · **RC-b** (`SCI_*FLAGS` computed in
CMake, flag-facts gated) · **RC-c** (the ~21 non-Makefile generated files — `build.incl.xml`, the
`scilab*.properties`, `etc/{classpath.xml,modules.xml,logging.properties,Info.plist}`, `scilab.pc`)
· **RC-d** (the macros build as a CMake post-step) · **RC-e** (the cutover — CMake reads nothing from
`config.status` → **delete `./configure` + `config.status`**). Retiring `make` (deleting the
`Makefile.am`/`Makefile.in`, which also freezes the parity baseline) is a separate later milestone.
RC-a–d are largely independent; RC-e depends on all. **Binding invariant: coexistence is TEMPORARY —
the destination is autotools DELETED (§12).**

## 3. Scope

**In scope (RC-a):**
- A **semantic header-parity dimension** in the harness: parse a header into its `{macro: value}`
  `#define` set and compare (not bytes). Applied to `machine.h`; re-baselined from configure's header.
- **`scilab/cmake/ScilabMachineHeader.cmake`** — compute all ~184 `machine.h` macros in CMake (the 5
  buckets, §5.2) + `configure_file` a CMake-authored `machine.h.cmake.in` template → `machine.h` in
  `build-cmake/generated-includes/`. The CMake build's include path already has that dir (1f-c).
- Docs + CI.

**Out of scope (later RC sub-stages / standing deferrals):**
- `SCI_*FLAGS` (RC-b), the other generated files (RC-c), the macros build (RC-d), deleting `configure`
  (RC-e), retiring `make`.
- Any change to `configure.ac`, any `Makefile.am`, or `machine.h.in`/`version.h.in` (the CMake path
  uses its OWN `machine.h.cmake.in` template; reading `machine.h.in` for the macro list is allowed).
- Making `machine.h`'s option macros real user-facing CMake options for *non-standard* configs — RC-a
  defaults them to THIS build's configuration; broad option support is a retirement-completion concern.

## 4. Background — `machine.h.in`'s 184 macros in 5 buckets (grounded)

- **~131 CMake-probeable:** 50 `HAVE_*_H` (`check_include_file`), ~71 `HAVE_<func>`
  (`check_symbol_exists`/`check_function_exists`), 2 `SIZEOF_*` (`check_type_size`), 8 misc
  (`STDC_HEADERS`/`WORDS_BIGENDIAN`/`const`/`inline`/`CLOSEDIR_VOID`/`RETSIGTYPE`/`_FILE_OFFSET_BITS`/…).
- **13 config-options:** `ENABLE_MPI`/`ENABLE_NLS`/`ENABLE_RELOCATABLE` + 10 `WITH_*` — configure's
  `--enable`/`--with` decisions.
- **12 pkg-config substitutions:** `CURL_CFLAGS`/`CURL_LIBS`/`CURL_VERSION`, `LIBARCHIVE_*`, `LIBXML*`,
  … — configure's `pkg-config` outputs.
- **6 Fortran mangling:** `C2F`, `F2C`, `CNAME`, `F77_DUMMY_MAIN`, `F77_NO_MINUS_C_MINUS_O`.
- **7 `PACKAGE_*`:** autoconf boilerplate (`PACKAGE_NAME`/`PACKAGE_VERSION`/…).

Unlike `version.h` (byte-identical via `configure_file`), a CMake-generated `machine.h` is **not**
byte-identical to autoconf's (different comment/`#define`/`#undef` formatting, ordering) — hence the
semantic dimension. `machine.h` has **no volatile fields** (no git revision/timestamp), so the
semantic compare needs no normalization (unlike version.h's git fields / the jar MANIFEST).

## 5. Architecture

### 5.1 The semantic header-parity dimension (harness — build FIRST, it gates §5.2)
`parity/fingerprint.py` gains `parse_defines(header_text) -> {macro: value}`: each `#define KEY VALUE`
→ `{KEY: VALUE}` (a bare `#define KEY` → value `""`); `#undef`/absent macros are simply absent from
the map. `capture.py` captures the **CMake-generated** `machine.h` (from
`build-cmake/generated-includes/machine.h` when present) as a `header_defines["machine.h"]` map;
`diff.py` compares it to the baseline map — reporting `macro added/removed/changed`. The **existing
byte-hash of the SOURCE-TREE `machine.h`** (configure's, in `generated`) stays untouched during
coexistence — the semantic dimension is ADDITIVE, gating the CMake-generated copy. Re-baseline: parse
configure's source-tree `machine.h` into the reference `#define` set (cheap — no rebuild). Fault-
injected: flip a `HAVE_X` value / drop a macro / add a macro in the candidate → parity fails naming it.

### 5.2 `machine.h` generation (`scilab/cmake/ScilabMachineHeader.cmake`)
Compute the 5 buckets IN CMake, then `configure_file(machine.h.cmake.in machine.h)` into
`build-cmake/generated-includes/`:
- **Probes (131):** `check_include_file`/`check_symbol_exists`/`check_function_exists`/`check_type_size`,
  keyed to the exact `machine.h.in` macro names. **`CMAKE_REQUIRED_*` pinned** to the configured
  compiler flags + `-isysroot` + include dirs so a probe's answer matches configure's (the biggest
  fidelity lever).
- **pkg-config (12):** `find_package(PkgConfig)` + `pkg_check_modules` for curl/libarchive/libxml/… →
  the `_CFLAGS`/`_LIBS`/`_VERSION` values, in configure's spelling (the semantic diff is the arbiter).
- **Fortran mangling (6):** CMake's `FortranCInterface` module → `C2F`/`F2C`/`CNAME`; `F77_*` set to the
  configured values.
- **Options (13):** CMake cache options DEFAULTED to this build's configuration (e.g. `ENABLE_NLS` on,
  `ENABLE_MPI` off) — CMake owns these decisions from here.
- **`PACKAGE_*` (7):** from `project()` / fixed strings, matching configure's values.

The macro **NAMES** (from `machine.h.in`) are the contract; the semantic dimension proves the **VALUES**
match configure — so a mis-probed macro is named, never silent.

### 5.3 Consumption & coexistence
`build-cmake/generated-includes/` is already on the module include path (1f-c). `machine.h` joins
`version.h` there. During coexistence the source-tree `machine.h` (configure's) resolves first
(`ScilabModule.cmake`'s `core/includes`-first parity order) — and because CMake's `machine.h` is
*semantically* (not byte-) equal, the compiled output is identical either way (comments/formatting are
stripped; the `#define` set drives codegen). The generated copy becomes the resolver at RC-e when the
source-tree `machine.h` is deleted. `make` still generates the source-tree header (rollback free).

## 6. The gate & acceptance

1. **From-scratch build → whole-tree PARITY OK** (68 dylibs + 2 executables + 24 jars unchanged) +
   **the semantic-header dimension**: CMake's `machine.h` `#define` set == configure's, macro-by-macro
   + flag-facts rc=0. The harness names any divergent macro.
2. **The CMake build still compiles + links + runs** on the (byte-different, semantically-equal)
   header set — the from-scratch parity build is the proof (a wrong `#define` that changed codegen
   would move a symbol/flag and fail parity).
3. **Coexistence:** `make` still generates configure's `machine.h`; the source-tree header + its
   byte-hash are unchanged.

## 7. Migration mechanics & rollback

- **Order:** (1) the semantic-header dimension + fault-injection + re-baseline (parse configure's
  `machine.h`). (2) `ScilabMachineHeader.cmake` — the 131 probes (the bulk; the semantic diff names any
  wrong one, so iterate against it). (3) the pkg-config + Fortran + options + PACKAGE buckets. (4) wire
  into the driver + `configure_file` → generated-includes; from-scratch parity + semantic gate. (5)
  docs + CI.
- **Rollback is free:** the generated header goes to `build-cmake/generated-includes/` (the source-tree
  `machine.h` is untouched); the CMake files are additive; `make` recovers everything. No
  `configure.ac`/`Makefile.am`/`*.in` change.

## 8. Testing

- The **semantic-header dimension** is the primary test (fault-injected: flip/drop/add a macro → fail),
  built + proven before the probe port so the port iterates against a real gate.
- The **from-scratch parity build** proves the semantically-equal header compiles the same app.
- CI: `sanity:cmake-driver` gains a check that `ScilabMachineHeader.cmake` is wired; the native
  `parity:cmake-drop-in` gate covers the semantic-header dimension automatically.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A `check_*` probe disagrees with autoconf (header found via a different `-I`; a link-vs-compile function check) | Pin `CMAKE_REQUIRED_FLAGS`/`_INCLUDES`/`-isysroot` to the configured environment; the semantic diff names the exact divergent macro — iterate against it, not blind. |
| pkg-config value FORMAT differs (CMake `pkg_check_modules` vs configure's `pkg-config` — `-L` order, list vs string) | The semantic diff compares the values; reproduce configure's spelling exactly for the 12 macros; a mismatch is named, not silent. |
| `FortranCInterface` yields a different mangling token than configure | The semantic diff catches it; fall back to the configured values if CMake's detection differs (documented, like other transcribed facts). |
| The 131-probe port is large + error-prone | It is mechanical AND fully gated — every macro is checked against configure's value by name; no macro passes unverified. |
| An option macro's default is wrong for a NON-standard config | RC-a targets THIS build's config (the harness's charter); broad option support is deferred; a wrong default shows immediately in the semantic diff for this config. |

## 10. Success criteria

- CMake generates `machine.h` (into `build-cmake/generated-includes/`) by computing all 184 macros
  itself; the **semantic-header dimension** proves its `#define` set == configure's, and the harness
  catches a flipped/dropped/added macro.
- From-scratch whole-tree **PARITY OK** + flag-facts rc=0 (the semantically-equal header compiles the
  same app).
- `make` still generates configure's `machine.h` (coexistence recovers).
- The semantic-header dimension is in place for RC-b/RC-c to reuse; `machine.h`'s generation no longer
  needs `config.status` (RC-a's de-configure increment).
