# Retire-configure RC-b — CMake computes the compiler flags + a derived per-TU flag gate — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-18
**Depends on:** RC-a (CMake computes + generates `machine.h`; the semantic `header_defines` dimension;
HEAD `ff6906a8232`). Strategy: `docs/design/build-cmake-maven-migration.md` §12 (the retirement endgame).

## 1. Goal

Replace the **static, hand-transcribed** compiler flags in `cmake/ScilabModule.cmake` with policy CMake
**computes itself**, and replace the flag gate's **hand-written expectations** with expectations
**derived from the autotools build and frozen into the parity baseline**. RC-b is the second sub-stage
of retire-configure; after it, the flags are a computed, gated fact rather than a transcribed, ungated
one. `./configure` still runs (RC-c/RC-d still need it); deleting it is RC-e.

## 2. The retire-configure decomposition (context)

**RC-a (done)** — `machine.h` + the semantic `header_defines` dimension · **RC-b (this)** — the flags ·
**RC-c** — the ~21 other generated files · **RC-d** — the macros build · **RC-e** — the cutover that
deletes `./configure`. **Binding invariant: coexistence is TEMPORARY — the destination is autotools
DELETED (§12).**

## 3. Background — what is actually true today (all verified, not assumed)

### 3.1 There are four `SCI_*FLAGS`, and CMake does not read them

`configure.ac:2384-2424` composes and `AC_SUBST`s exactly four. Ground truth from `config.status`:

```
SCI_CFLAGS   = -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector \
               -Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types
SCI_CXXFLAGS = -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector -Wall -Wpedantic
SCI_FFLAGS   = -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
SCI_LDFLAGS  = -mmacosx-version-min=11.0 -Wl,-rpath,/usr/lib -Wl,-rpath,/opt/homebrew/opt/gcc/lib/gcc/current
```

They reach the compiler via `Makefile.incl.am:25-34` (`AM_CFLAGS = $(SCI_CFLAGS)` etc.), included by all
78 module `Makefile.am`s — **not** via `CFLAGS`, which is a separate channel carrying only
`-I/opt/homebrew/opt/openssl/include` here.

**CMake does not read these from `config.status`.** `cmake/ScilabModule.cmake:146-160`
(`_scilab_module_flag_env()`) hardcodes them as literal CMake lists, and the file's own header says so:
*"every fact below … is transcribed from the CONFIGURED autotools build … not invented here"*. So RC-b
is **not** "remove a config.status read" — it is "replace an ungated transcription with a computed,
gated fact". The transcription cannot drift *detectably*: nothing compares it to autotools.

### 3.2 Three traps the flags carry

- **`-std=` is not in `SCI_*FLAGS`.** `CC = gcc -std=gnu23 -arch arm64` and
  `CXX = g++ -arch arm64 -std=c++17` — the language standard and `-arch` live in the compiler
  *variables*, not the flag variables. A port that reads only `SCI_CFLAGS`/`SCI_CXXFLAGS` silently
  drops the language standard.
- **`SCI_CPPFLAGS` is a phantom.** `Makefile.incl.am:25` and `Makefile.am:27` both do
  `AM_CPPFLAGS = $(SCI_CPPFLAGS)`, but nothing anywhere assigns or `AC_SUBST`s it — verified absent
  from `config.status`. It always expands empty. Do not implement it; document it.
- **Dead vs conditionally-dead ingredients.** Of the eight ingredient groups per language, five are
  assigned nowhere at all (`WARNING_FFLAGS`, `DEBUG_LDFLAGS`, `WARNING_LDFLAGS`, `SSE_LDFLAGS`,
  `BACKTRACE_LDFLAGS`), while **`COMPILER_FFLAGS` is live only on the Intel-compiler path**
  (`m4/intel_compiler.m4:28,30`), which this build does not take. "Dead everywhere" and "dead here"
  imply different CMake code; the distinction must be preserved.

### 3.3 The `_CFLAGS` footgun — and a live, unnoticed divergence

Automake's per-target `foo_la_CFLAGS` **replaces** `AM_CFLAGS` rather than appending, so a module
setting it to a bare `-I` list loses all of `SCI_CFLAGS`. Verified in the generated recipe
(`modules/history_browser/Makefile:986`) — no `$(AM_CFLAGS)`, and `libscihistory_browser_la_CFLAGS=`
is empty (`Makefile.am:50`).

**CMake currently diverges on 4 TUs across 3 modules**, verified on both sides (autotools recipe vs
`build-cmake/compile_commands.json`):

| Module | TUs | autotools | CMake |
|---|---|---|---|
| `history_browser` | `sci_browsehistory.c`, `CommandHistory_Wrap_Fake.c` | bare (no `-O2`, no `-fwrapv`) | `-O2 -fwrapv` |
| `preferences` | `getScilabPreference.c` | bare | `-O2 -fwrapv` |
| `types` | `getScilabVariable_wrap.c` | bare | `-O2 -fwrapv` |

`parameters`, `windows_tools`, and `string/src/c` are reproduced correctly — they are the three in
`DIR_EXPECTED_OVERRIDES`. **Nothing caught the other three**, for the structural reason in §3.4.

### 3.4 What the existing gates can and cannot catch (fault-injected, measured)

Two mechanisms exist, with opposite failure modes:

| Experiment | Result |
|---|---|
| `flagfacts_check` on today's tree | **rc=0 — passes**, despite the 4 divergent TUs of §3.3 |
| Global `flags` dimension vs a 3570-TU regression (only the 3 language representatives spared) | **misses it entirely** — captured facts identical to the clean tree |
| `flagfacts_check` vs that same regression | **catches it** — 7138 mismatches |
| `flagfacts_check` vs the inverse (CMake stops reproducing the footgun on `parameters.c`) | **catches it**, naming `opt` and `wrapv` |

- **The global `flags` row is weak by construction.** `capture.py:96-97` takes the **first
  `compile_commands.json` entry per language** as "the" global fact. Today's representatives
  (`scilab.cpp`, `sci_beep.c`, `somespline.f`) are ordinary TUs, so it is *accidentally* honest — but
  that is ordering luck, and its own docstring warns an overridden TU landing first would be mistaken
  for the global fact. It is a coarse cross-build record, not a gate.
- **`flagfacts_check` is strong where it was assumed weak.** It walks all 3600 TUs and merges
  per-file/per-directory overrides correctly, in both directions. Its **only** defect is that the
  expectation is hand-written: `_BASE` plus two hand-maintained tables. It faithfully enforces what
  someone remembered to record and silently blesses what they did not — which is exactly how the §3.3
  divergence survived.

**Conclusion that shapes the architecture:** the per-TU walk is proven and should be *kept*. What must
change is **where its expectation comes from**.

## 4. Scope

**In scope:**
- Derive per-TU expected flag facts from the **autotools** side and freeze them in
  `build-parity/baseline-autotools.json`; re-point `flagfacts_check` at them.
- Close the 4-TU divergence (§3.3) by reproducing autotools faithfully.
- `scilab/cmake/ScilabFlags.cmake` — compute the four `SCI_*FLAGS` equivalents as CMake policy,
  replacing the hardcoded lists.
- Docs + CI.

**Out of scope (recorded, not done):**
- **Fixing the footgun** (restoring `-O2 -fwrapv` for the affected TUs in both build systems +
  re-baselining) — a deliberate product change, its own stage, so the migration gate and the product
  change do not entangle. Those TUs are currently missing the `-fwrapv` UB hardening added after the
  `rand()` Inf miscompile, so the follow-up matters and must not be lost.
- Deleting the global `flags` dimension. It is the only cross-build flag record in the baseline; RC-b
  documents it as a coarse record rather than a gate.
- `SCI_CPPFLAGS` (phantom) and the five dead ingredient groups — documented, not implemented.
- RC-c/RC-d/RC-e surfaces.

## 5. Architecture

### 5.1 The derived per-TU expectation (build FIRST — it gates §5.3)

A new autotools-side extractor resolves, for each compiled TU, the flags its **generated recipe**
actually uses, and reduces them to the fact set `parse_flag_facts` already produces (`opt`, `wrapv`,
`min_macos`, `ndebug`, `std`, `openmp`).

The mechanic that matters: for each per-object compile rule, determine which flag variable the recipe
references, then **resolve that variable's definition** — `$(AM_CFLAGS)` means inherit `SCI_CFLAGS`; a
per-target `$(foo_la_CFLAGS)` means *that variable's expansion*, which may be empty (footgun) or may
itself re-include `$(AM_CFLAGS)` (not a footgun).

> **Resolution is mandatory, not an optimization.** A throwaway census written for this design
> pattern-matched recipe text for the literal `$(AM_CFLAGS)` and immediately produced a false positive
> on `localization`, whose `libscilocalization_la_CFLAGS = $(AM_CFLAGS) $(am__append_5)`
> (`modules/localization/Makefile:748`) extends rather than replaces. A parser that does not follow
> variable definitions will mis-classify modules in both directions.

**Frozen, not live.** The derived facts are captured into `baseline-autotools.json` once and compared
against thereafter — the same shape as RC-a's `header_defines`. This is required, not stylistic: a
live parser needs generated Makefiles present, and **RC-e deletes them**. The frozen baseline is what
lets the autotools-derived truth outlive autotools.

`flagfacts_check` keeps its per-TU walk and its merge semantics; only its source of expectation
changes. `DEFAULT_EXPECTED_BY_SUFFIX`, `FILE_EXPECTED_OVERRIDES`, and `DIR_EXPECTED_OVERRIDES` are
retired in favour of the baseline — with their documentation preserved as commentary on *why* the
recorded facts look the way they do.

### 5.2 Closing the divergence

CMake gains per-target flag overrides for `history_browser`, `preferences`, and `types`, matching the
mechanism already used for `parameters`/`windows_tools`/`string`
(`scilab_object_module(... C_FLAGS_OVERRIDE …)`). Faithful reproduction, per §4.

### 5.3 `scilab/cmake/ScilabFlags.cmake` — computed policy

The hardcoded lists in `_scilab_module_flag_env()` are replaced by computed values:

- **Release/debug** — a CMake option reproducing configure's `if enable_debug then "-O0 -g3" else
  "-DNDEBUG -g1 -O2 -fwrapv"` branch, so a debug configuration is *expressible* rather than a second
  hardcoded string. The option's default is declared **in CMake** (release, matching this build); it
  is **not** read from `config.status` — reading configure's evaluation of the policy is the very
  dependency RC-b removes.
- **`-mmacosx-version-min`** — from `CMAKE_OSX_DEPLOYMENT_TARGET` (already set in
  `ScilabToolchain.cmake`), not a baked `11.0`. Note Fortran gets no CMake-native equivalent and needs
  the flag spelled explicitly, as it does today.
- **`-fno-stack-protector`** — vendor-conditional policy, carrying the bug-3131 rationale.
- **Warning sets** — per-vendor `-Wall -Wpedantic`, preserving the **C-only** `-Werror=implicit
  -Werror=incompatible-pointer-types` asymmetry (`configure.ac:2358-2360`) and Fortran getting no
  warning flags at all.
- **`-std=gnu23` / `-std=c++17`** — set explicitly, with a prominent comment recording that these come
  from `$CC`/`$CXX` and **not** from `SCI_*FLAGS` (§3.2).
- **`SCI_LDFLAGS`** — computed; its gate remains the existing `LC_RPATH`/dylib dimension, which
  already covers the two rpath entries.

Where configure *probes* (e.g. `m4/backtrace.m4`'s `-rdynamic` link test), CMake probes; where the
result is inert on this platform, that is documented rather than silently omitted.

## 6. The gate & acceptance

1. **The derived gate must catch the known bug.** Run against today's tree it must **FAIL**, naming
   exactly the 4 TUs of §3.3 — the proof that it sees what the hand-written gate could not. A gate
   that cannot find a bug we already hold in hand is not a gate.
2. **Parser validation against known answers**, before it is trusted. The known-answer set is:
   - the **6 footgun modules** (`parameters`, `windows_tools`, `string/src/c`, `history_browser`,
     `types`, `preferences` — 33 C TUs total) must all classify as *not inheriting* `AM_CFLAGS`;
   - the **6 Fortran `-O0` files** (`colnew.f`, `sszer.f`, `dtensbs.f`, `blkfct.f`, `symfct.f`,
     `ordmmd.f`) must classify as `opt=O0` with `wrapv` still true;
   - **negative controls** — `localization` (`_CFLAGS = $(AM_CFLAGS) $(am__append_5)`) and
     `spreadsheet` (`_CXXFLAGS = $(AM_CXXFLAGS) -std=c++20`) must **not** be flagged; both extend
     rather than replace, and both are exactly what the §5.1 failure mode gets wrong.

   All three groups must pass before the derived facts are used to gate anything.
3. **After §5.2**, the derived gate passes on the whole tree.
4. **After §5.3**, it still passes, plus a from-scratch whole-tree **PARITY OK** (68 dylibs + 2
   executables + 24 jars + the semantic header) and the `build-parity` suite green.
5. **Fault injection on the armed gate:** flip one TU's `opt` in the candidate → fail naming it.

## 7. Migration mechanics & rollback

- **Order:** (1) the extractor + baseline capture + validation, gated by §6.1–6.2. (2) close the
  divergence. (3) `ScilabFlags.cmake`. (4) from-scratch gate + docs + CI.
- **Rollback is free:** the harness changes are additive; `ScilabFlags.cmake` replaces a function body
  whose previous contents are one `git revert` away; no `configure.ac`/`Makefile.am`/`*.in` edits.
- **Blast radius is total but observed:** all 68 migrated modules / 166 target calls funnel through
  `_scilab_module_flag_env()`, so §5.3 changes every TU at once — which is precisely why §5.1 lands
  first and covers every TU.

## 8. Testing

- The derived gate is the primary test; §6.1 (must fail on the known bug) and §6.2 (negative controls)
  are its acceptance, ahead of any flag computation.
- Unit tests for the recipe parser covering: `$(AM_CFLAGS)` inheritance, an empty per-target override,
  a per-target override that re-includes `$(AM_CFLAGS)`, the Fortran path, and subdir-object paths.
- From-scratch whole-tree parity proves the computed flags build the same app.
- CI: `sanity:cmake-driver` gains a check that `ScilabFlags.cmake` is wired.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| The recipe parser mis-classifies a module (demonstrated: `localization` false positive) | Resolve variable definitions, never pattern-match recipe text; 16 known-answer cases + 2 negative controls must pass before the gate is trusted (§6.2). |
| A computed flag differs subtly from the transcribed one across 3600 TUs | The derived per-TU gate covers every TU; a divergence is named with its file and fact. |
| `-std=` dropped because it is not in `SCI_*FLAGS` | Called out in §3.2, handled explicitly in §5.3, and `std` is one of the captured facts, so the gate catches it. |
| Freezing the baseline hides later autotools drift | Same tradeoff RC-a accepted; the baseline is the deliberate frozen record, and re-arming is a conscious act. Noted alongside RC-a's environment-contingency caveat. |
| The retired override tables lose their documented rationale | Their comments are preserved as commentary on the recorded facts, not deleted with the code. |

## 10. Success criteria

- `flagfacts_check` derives its expectations from autotools-derived facts frozen in the baseline; the
  hand-maintained tables are gone.
- The gate demonstrably catches the 4-TU divergence (§6.1) and clears the negative controls (§6.2).
- The divergence is closed; CMake reproduces autotools for all six footgun modules.
- CMake computes the four `SCI_*FLAGS` equivalents itself; from-scratch whole-tree **PARITY OK** +
  the suite green.
- The footgun fix is recorded as an owned follow-up, with its `-fwrapv` hardening rationale intact.
