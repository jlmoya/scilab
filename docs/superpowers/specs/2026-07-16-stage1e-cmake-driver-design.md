# Stage 1e — Top-level CMake driver (native drop-in) — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-16
**Depends on:** the Stage-0 parity harness (`scilab/build-parity/`), the flag manifest, and the four
proven per-module exemplars — `sound` (`38e81564f3f`), `parallel` (`f3d3a58fade`), `coverage`
(`6b43d012ae3`), `interpolation` (`531436d485a`). Strategy context:
`docs/design/build-cmake-maven-migration.md`.

## 1. Goal

Scale the four proven per-module CMake exemplars to **all 69 native (`.la`-producing) Scilab modules**
via a single top-level CMake driver and one shared `scilab_module()` helper, so that `cmake --build`
produces every native module dylib and drops it into the autotools `.libs/` layout **byte-shape-identical
to the autotools build** (arbitrated by the parity harness). The autotools build stays fully intact and
continues to link the executables, run Ant for the Java side, and build help — it simply consumes the
CMake-built dylibs. This is the incremental, provable step that makes CMake the master of the *native*
build without a big-bang cutover.

## 2. Scope

**In scope (Stage 1e):**
- A top-level `scilab/CMakeLists.txt` that builds the 69 native modules.
- One `scilab/cmake/ScilabModule.cmake` helper (`scilab_module()`) that owns all per-module policy.
- A thin `CMakeLists.txt` per native module — a single `scilab_module(...)` data declaration.
- A `drop-in-all` target that places every built dylib (+ its `.dylib` symlink) into its module's
  `.libs/`.
- The inter-module native dependency edges (11 modules; see §5).
- A new harness check: per-module CMake flag-facts over the merged `compile_commands.json`.
- Migrating the four existing exemplars from their hand-written `CMakeLists.txt` to `scilab_module()`
  calls (proving the helper reproduces their already-parity-proven output).

**Out of scope (deferred to Stage 1f and beyond):**
- Linking the `scilab-cli` / `scilab` executables under CMake (autotools still links them).
- CMake invoking Ant (the Ant bridge) — autotools still runs Ant per module for the Java side.
- Building help under CMake — it stays an autotools post-step on the running app.
- The `std=c++17` baseline re-capture / flag-source switch (needed only when CMake becomes the
  tree-wide flag source; in Stage 1e the whole-tree flag manifest still reads `config.status`).
- **Raising the C++ standard** (c++17 → c++23, eventually c++26 once ratified) — a separate axis that
  changes codegen and would break parity against the c++17 baseline. Deferred to its own step (§12);
  Stage 1e stays at c++17 to match the current baseline.
- **Config-header generation** (`machine.h`/`version.h`) — Stage 1e consumes the `configure`-generated
  headers as-is; replacing `configure`'s feature detection with CMake is a distinct later stage (§11).
- Java-only modules (3) and macro/doc-only modules — they stay under autotools untouched.
- Any change to `configure.ac`, any `Makefile.am`, or generated code.

## 3. Architecture

```
scilab/
  CMakeLists.txt              # top-level driver: project(), add_subdirectory() × 69, drop-in-all
  cmake/
    ScilabModule.cmake        # scilab_module() — ALL policy, once
    ScilabToolchain.cmake     # (optional) shared discovery: SDK path, Homebrew gcc, flags
  modules/<m>/CMakeLists.txt  # thin: one scilab_module(<m> ...) call
  build-cmake/                # scratch build dir (gitignored); merged compile_commands.json
```

- `cmake -S scilab -B scilab/build-cmake` configures; `cmake --build scilab/build-cmake` builds all 69
  (in parallel, honoring the §5 edges); `cmake --build … --target drop-in-all` copies each dylib into
  its `.libs/`.
- The autotools tree must already be configured (so `configure`-generated headers — `machine.h`
  (the autoconf `config.h`: ~186 `HAVE_*`/`SIZEOF_*` feature macros) and `version.h`, both in
  `modules/core/includes/` — exist for the CMake compiles to `-I` at). CMake does **not** regenerate
  them in Stage 1e; the config-detection axis is a separate, later stage (§11).
- `cmake --build` builds the **native** tree; `make` still produces the final app. The two coexist;
  the CMakeLists files are invisible to automake.

**Isolation / boundaries.** Each unit has one job: the top-level driver only enumerates modules and
wires the drop-in target; `ScilabModule.cmake` only encapsulates policy; each module `CMakeLists.txt`
only declares data. A policy change touches one file; a module change touches one short declaration.

## 4. The `scilab_module()` helper

`ScilabModule.cmake` defines `scilab_module(<name> <options>)`, encapsulating every pattern the four
exemplars proved:

- **Targets:** an `OBJECT` library `<name>-algo` for `ALGO_SOURCES` (the automake `noinst` convenience
  lib — never `STATIC`, which would drop unreferenced algo symbols), folded into a `SHARED`
  `<name>` target via `$<TARGET_OBJECTS:<name>-algo>` together with `GATEWAY_SOURCES`. If a module has
  no algo lib, the OBJECT target is omitted.
- **Per-language flags** via `$<COMPILE_LANGUAGE:C|CXX|Fortran>` generator expressions, transcribed
  semantic facts (`-O2 -fwrapv -mmacosx-version-min=11.0 -DNDEBUG` + the C-only `-Werror`s for C,
  `-std=c++17` for C++, `SCI_FFLAGS` + `-I core/includes` for Fortran). Requires the Makefile/Ninja
  generator (per-language includes/defines don't express under Xcode/VS).
- **Link policy:** `-undefined dynamic_lookup -no_fixup_chains`; `LINKER_LANGUAGE CXX` when any C++ or
  Fortran source is present (pulls in `libc++`; keeps the Fortran runtime via implicit link info);
  the two `SCI_LDFLAGS` rpaths.
- **Identity:** `OUTPUT_NAME "sci<name>.2027"` + `SUFFIX ".dylib"` (never CMake `VERSION`, which
  mis-keys the harness), `INSTALL_NAME_DIR /usr/local/lib/scilab` + `BUILD_WITH_INSTALL_NAME_DIR TRUE`,
  `DEFINE_SYMBOL ""`, `POSITION_INDEPENDENT_CODE ON`.
- **Includes:** a **default Scilab include base** (the common `ast/{ast,exps,operations,parse,symbol,
  system_env,types}`, `core`, `api_scilab`, `localization`, `output_stream`, `dynamic_link`, plus the
  module's own `includes/` + `src/*/` and `modules/core/includes` for the generated headers) supplied
  by the helper; modules add extras via `EXTRA_INCLUDES`.
- **Drop-in:** a per-module `drop-in` custom target (copy the real dylib into `.libs/`, recreate the
  `.dylib` symlink), aggregated by the top-level `drop-in-all`.

**Parameter contract (one-line each):**

| Param | Meaning |
|---|---|
| `ALGO_SOURCES` | convenience-lib sources (→ OBJECT lib); optional |
| `GATEWAY_SOURCES` | gateway sources (→ SHARED lib) |
| `LANG` | `C` / `CXX` / `Fortran` / any mix (drives per-language flags + linker language) |
| `SYSTEM_LIBS` | `/usr/lib` system libs linked explicitly (`xml2 z icucore …`) — never `find_package` |
| `FIND_PACKAGES` | Homebrew-keg deps via `find_package` (e.g. `OpenMP`) |
| `MODULE_DEPS` | sibling native modules this one links (§5) |
| `CLASS` | `ENGINE_LIBS` or `DYNAMIC_LOAD` — selects the correct header note only |
| `SYMBOLS` | expected exported-symbol count (documents the parity contract) |
| `EXTRA_INCLUDES` | include dirs beyond the default base |

**The four exemplars migrate to `scilab_module()` first** (Task 1 of the plan): each hand-written
`CMakeLists.txt` becomes a `scilab_module(...)` call and must still produce a byte-shape-identical
dylib (harness PARITY OK). This proves the helper against four already-verified references before the
other 65 depend on it.

## 5. Inter-module dependency graph

58 of 69 modules are independent (they use `dynamic_lookup`; no sibling dylib is linked — verified on
all four exemplars). **11 modules link sibling module dylibs at build time** and declare `MODULE_DEPS`:

| Module | Sibling deps |
|---|---|
| `differential_equations` | `sundials` |
| `external_objects_java` | `commons`, `jvm` |
| `fileio` | `console` |
| `helptools` | `commons` |
| `integer` | `polynomials` |
| `javasci` | `api_scilab`, `scilab` (aggregate — special case) |
| `localization` | `io` |
| `renderer` | `jvm` |
| `scicos` | `scicos_blocks`, `sundials` |
| `types` | `ast`, `commons`, `jvm` |
| `xcos` | `commons`, `jvm`, `scicos` |

CMake's target-dependency resolution orders these builds and records the sibling dep by the sibling's
install_name (`/usr/local/lib/scilab/libsci<dep>.2027.dylib`), matching autotools. **Special case:**
`javasci` links `libscilab` (the aggregate engine lib produced by `modules/Makefile.am`, *not* a module
subdir); in the drop-in model that aggregate is autotools-built, so `javasci`'s CMake build resolves it
via `dynamic_lookup` (or links the existing `.libs/` aggregate). Flag `javasci` as a per-module special
case; the harness verifies its recorded deps either way.

## 6. The parity gate (the arbiter, unchanged)

Every module is proven the same way the four exemplars were:

1. **Whole-tree harness capture vs the committed baseline → PARITY OK for all 69 dylibs** (symbols,
   deps, install_name, SDK stamp). Run after `drop-in-all`.
2. **Per-module flag-facts** (NEW): a harness check parses each module's compile entries in the merged
   `scilab/build-cmake/compile_commands.json` and asserts the semantic facts (`opt=O2, wrapv=True,
   min_macos=11.0, ndebug=True`, `std` per language, `openmp` where expected). This closes the hybrid
   blind spot — the tree-wide flag manifest reads `config.status` (autotools) on both sides, so it
   cannot see a CMake module's actual flags; this check can. It becomes part of the harness test suite.
3. **App still builds + runs via autotools:** after drop-in, an autotools `make` links clean and a
   headless smoke (`bin/scilab-cli -nwni` exercising a representative builtin per batch) runs green.

Modules are brought under the helper in **dependency-respecting batches** (leaves first, then
dependents), each batch parity-gated, so any regression is localized to the module that caused it.

## 7. Migration mechanics & rollback

- **Order:** (1) write `ScilabModule.cmake` + the top-level driver skeleton driving only the four
  exemplars; migrate the four exemplars to `scilab_module()`; prove PARITY OK. (2) Roll the remaining
  65 modules in batches (leaves → core-dependents), extracting each module's data declaration (sources
  by language, includes, deps, class, symbol count) from its `Makefile.am`/`Makefile`, each parity-
  gated. (3) Add the per-module flag-fact harness check. (4) Wire `drop-in-all` + a documented
  `cmake --build && --target drop-in-all` flow.
- **Data extraction** per module mirrors the exemplar briefs (sources, include set, external/sibling
  deps, class via `modules/Makefile.am`, symbol count via `nm -gU` on the autotools dylib). This is the
  bulk of the work and is naturally subagent-parallelizable per batch.
- **Rollback is free:** CMake is purely additive; the autotools build remains the source of truth and
  can rebuild any module (`make -C modules/<m> clean && make`). No `configure.ac`/`Makefile.am` change
  means `git` cannot regress the autotools path.

## 8. Testing strategy

- The **parity harness is the primary test** (§6.1) — it already exists and is mutation-proven.
- The **per-module flag-fact check** (§6.2) is new test code in `build-parity/`, itself fault-injected
  (mutating a module's `-O2`→`-O0` or dropping `-fwrapv` must fail it), consistent with the harness's
  existing discipline.
- **Batch smoke** (§6.3): a representative builtin per batch, headless, `exit(0)`.
- CI: the parity gate + flag-fact check should run in `.gitlab-ci.yml` once the driver builds the full
  set, so native-build drift is caught automatically (fork-native pipeline).

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A module needs a pattern the four exemplars didn't exercise (e.g. GIWS-generated sources compiled, a JNI/`libjvm` link, a `-l` system lib not yet seen) | The helper's parameters are open (`SYSTEM_LIBS`/`FIND_PACKAGES`/`EXTRA_INCLUDES`); the harness catches any dep/symbol mismatch; genuinely new patterns get a helper extension proven against that module, then reused. |
| `javasci` / aggregate-lib edge | Handled as a documented special case (§5); harness verifies. |
| Helper over-generalization hides a per-module quirk | The four-exemplar migration (Task 1) is the guard: the helper must reproduce four independently-verified dylibs before anything else rides on it. |
| Flag-manifest hybrid blind spot | The new per-module flag-fact check (§6.2) closes it. |
| Build order for the 11 edges | CMake target dependencies express it declaratively; no hand topo-sort. |

## 10. Success criteria

- `cmake --build scilab/build-cmake --target drop-in-all` produces all 69 native module dylibs,
  dropped into `.libs/`.
- Whole-tree harness capture vs the committed baseline = **PARITY OK** (all 69 dylibs match).
- Per-module flag-facts pass for all 69 modules.
- An autotools `make` on top of the dropped-in dylibs links the executables and the app runs headless
  clean.
- Each module's `CMakeLists.txt` is a single `scilab_module()` declaration; all policy lives in
  `ScilabModule.cmake`. A policy fix touches one file.
- The autotools build remains fully functional (rollback path intact).

## 11. The config-detection axis — generated headers (deferred, not ignored)

`configure` generates two headers the compiles depend on, from `.in` templates:
`modules/core/includes/machine.h` (the autoconf `config.h` — ~186 `HAVE_*` / `SIZEOF_*` /
`PACKAGE` macros from `AC_CONFIG_HEADERS` + the feature probes in `configure.ac`) and `version.h`
(the Scilab version, from `version.h.in`). **Stage 1e consumes them as-is** and `-I`s at them; it does
not regenerate them, because config-detection is a *different axis* from compile/link and Stage 1e
proves only the latter.

**When `configure` itself is removed** (a distinct later stage, part of shedding autotools — it is NOT
Stage 1e and NOT a footnote to it), CMake takes over generation the standard way:

- `machine.h` → `configure_file(machine.h.in machine.h)` driven by CMake's platform-probe modules —
  `CheckIncludeFile`, `CheckSymbolExists`, `CheckFunctionExists`, `CheckTypeSize`, `TestBigEndian` —
  the direct analogs of autoconf's `AC_CHECK_HEADER` / `AC_CHECK_FUNC` / `AC_CHECK_SIZEOF`. Reproducing
  the ~186 macros is "port `configure.ac`'s feature detection to CMake," a real body of work meriting
  its own stage/plan.
- `version.h` → `configure_file(version.h.in version.h)` — trivial variable substitution.

**This is provable by the same harness.** The parity harness already fingerprints both generated
headers (`generated` keys include `modules/core/includes/machine.h` and `version.h`), so a CMake-
generated header is diffed against the autotools one; and even a cosmetic textual difference is caught
semantically because a changed `HAVE_*` macro would alter compiled code → different module symbols/
behavior, which the per-module dylib parity check would flag. So the takeover, when it comes, is as
provable as every step before it.

## 12. The C++ standard axis — raise c++17 → c++23 (deferred, its own step)

The target C++ standard is **c++23** (Apple clang 21 accepts through c++26, and a real Scilab TU
compiles clean at c++23; c++26 is still a draft with incomplete library support, so c++23 is the
finalized latest — revisit c++26 once ratified). But the standard is a **codegen axis, not a
build-system axis**: bumping it changes compiled output, so a CMake build at c++23 against a c++17
autotools baseline fails parity two ways — the flag-fact `std` mismatches, and the codegen (hence
symbols/behavior) can diverge. **Stage 1e therefore stays at c++17** and the standard bump is deferred
to its own step.

Clean sequence when it runs (change one axis at a time):
1. Bump `AX_CXX_COMPILE_STDCXX(17,…)` → `(23,…)` in `configure.ac` (+ regenerate/patch the tracked
   `configure`), a global change to the autotools build.
2. Full `make` rebuild of all ~1,428 C++ TUs → fix whatever c++23 surfaces (removed/deprecated features)
   → run the `.tst` suite. (One TU compiling at c++23 ≠ the whole tree; the blast radius is real work.)
3. Re-capture the parity baseline at c++23.
4. Flip the migration's C++ standard to match.

**Payoff of the shared-helper decision (§4):** once the driver is in place, step 4 is a **one-line
change** in `cmake/ScilabModule.cmake` (`-std=c++17` → `-std=c++23`), not an edit across 69 module
files — the maintainability win the helper was chosen for, made concrete.
