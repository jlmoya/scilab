# Build parity harness (migration Stage 0)

Proves a CMake/Maven build is *behaviorally identical* to the autotools baseline — the safety net
for the make->CMake / Ant->Maven migration (`docs/design/build-cmake-maven-migration.md`).

## Usage
```bash
# Capture the current (candidate) build, then diff against the committed baseline:
./capture.sh .. /tmp/candidate.json candidate
./diff.sh baseline-autotools.json /tmp/candidate.json   # exit 0 = parity, 1 = regression
```

## What it compares
Per dylib: the exported symbol set (addresses stripped) and the link/dependency shape (deps compare
by path only — otool's `(compatibility version X, current version Y)` suffix is stripped, so a
routine `brew upgrade` bumping a system lib's `current version` doesn't flood every dependent dylib
with a false "link dependencies changed"). Per executable: the `LC_BUILD_VERSION` SDK stamp (must
stay `minos 11.0 / sdk 11.0` — the anti-SIGTRAP fix) and the link shape. Plus: any non-relocatable
`/tmp` path, the normalized content hash of `etc/classpath.xml`, `machine.h`, `version.h`, and a
manifest hash over every compiled macro `.bin` path (presence, not content — cheap, and enough to
catch a module's macros silently vanishing from a build).

Plus the **compiler-flag manifest**: the effective per-language (C / C++ / Fortran) codegen facts —
optimization level (last `-O<x>` wins), `-fwrapv`, `-mmacosx-version-min`, OpenMP, `-DNDEBUG`,
`-std=` — read from `config.status` (autotools) or `compile_commands.json` (CMake) and compared
*semantically*, so a dropped `-fwrapv` or an `-O2`→`-O0` slip fails parity even though it moves no
symbol, link edge, or SDK stamp (exactly the regression that once sat green for days; fixed in
`516c57573cc`). The `source` label itself is deliberately not compared — autotools→cmake is the
migration. **Known limitation (v1):** only the GLOBAL per-language flags are captured; per-TU
overrides (e.g. `differential_equations` forcing `colnew.f` to `-O0` on macOS) are invisible.

## Scope
Six dimensions are fingerprinted today:

| Dimension | Compares | Armed in |
|---|---|---|
| `dylibs` | nm symbols (address-stripped) + otool deps + `LC_BUILD_VERSION` + `LC_RPATH` | Stage 0 / 1f-a |
| `executables` | the same, for `scilab-bin` + `scilab-cli-bin` | Stage 1f-a |
| `generated` | byte hashes of generated files (incl. the source-tree `machine.h`/`version.h`) | Stage 0 |
| `flags` | semantic per-language flag facts | Stage 1 |
| `jars` | normalized jar content manifests (entry list + MANIFEST, volatile lines dropped) | Stage 1f-b |
| `header_defines` | a header's `{macro: value}` `#define` set — **semantic, not bytes** | RC-a |

`jars` was added in Stage 1f-b, once CMake began driving the Ant build and jar contents could
actually move. It covers the **24 module jars**; the doc build's output (`scilab_*_help.jar`,
`scilab_images.jar`) is excluded by filename pattern, since those are help artifacts rather than
module jars. Stage 2 (Ant -> Maven) is still what can change jar contents most, and the normalized
manifest is what makes a reactor build's timestamp churn survivable.

`header_defines` (RC-a) exists because CMake's generated `machine.h` is **not** byte-identical to
autoconf's — comment style, `#define` vs `/* #undef */` spelling and ordering all differ — so it is
compared semantically instead. Note what that means: it gates the **`#define` set**, not full
preprocessor equivalence. Directives that are not `#define` (a live vs commented-out `#undef`, an
`#ifdef` branch) are invisible to it by construction, as are duplicate definitions later in a file
(last wins) and internal whitespace inside a string value (collapsed before comparison). Those are
acceptable limits for the job it does — proving CMake computed the same macros configure did — but
they are limits, not oversights.

## What it does NOT automate (manual gates — run these too before declaring a module migrated)
1. **Behavior — the `.tst` suite.** There is no compiled test binary; run it inside the built
   interpreter:
   ```bash
   cd .. && LANG=C ./bin/scilab-cli -nb -e "exit(test_run([],[]))"   # or per-module: test_run('statistics')
   ```
   A migrated build must produce the same pass/fail set as autotools.
2. **The GUI-surface checklist** (needs a human at the screen): console, 2-D and 3-D plotting,
   the JavaFX file chooser, the embedded browser, and an xcos simulation run. These exercise the
   off-main-thread graphics path the SDK stamp protects — a green fingerprint does not prove them.

## Refreshing the baseline
Only when the autotools build itself legitimately changes:
`./capture.sh .. baseline-autotools.json autotools` and commit the new baseline in the same change.
A red `test_committed_baseline_matches_current_tree` right after a macOS/Homebrew update means
REFRESH the baseline (it's toolchain drift, e.g. a system lib's dep path moving) — it is not, by
itself, evidence of a Scilab regression.
