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
`/tmp` path; the normalized content hash of the 13 files this harness gates from `config.status`'s
substitution set — `etc/classpath.xml`, the source-tree `machine.h`/`version.h`, and the ten RC-c
files (`scilab.pc`, `etc/Info.plist`, `etc/modules.xml`, `Version.incl`, and so on — the exact list
is `parity/capture.py`'s `GENERATED_FILES`) — **always hashed from the SOURCE TREE** (configure's
own copies), on *both* sides of every comparison, never from anything CMake wrote; a manifest hash
over every compiled macro `.bin`'s **and each module's `lib`'s path AND content** (RC-d strengthened
this from path-only: presence alone would miss a `.bin` sitting at the right path with wrong bytes,
which is exactly what migrating the macro compiler's driver risks; a final review folded `lib` — the
XML index Scilab actually loads to resolve a macro name to its `.bin` and md5, not a byproduct — into
the SAME manifest entry, since every `.bin` byte could match while a corrupted `lib` left macros
unresolvable at runtime. Content hashing is strict rather than flaky because `.bin`/`lib` output is
*reproducible for a full build from a purged tree* — 0 of 3,516 `.bin` files, and separately all 81
`lib` files, differing across two independent full rebuilds — but that is **not** the same as
"deterministic" outright, an earlier over-claim a later final review corrected: the `.bin` format
embeds AST node numbers from a never-reset process-wide counter, and genlib's incremental skip
advances that counter only for the files it actually reparses, so an *incremental* rebuild (unlike the
from-purged *full* rebuild the 0-differing measurement used) can legitimately assign different node
numbers to whichever files it does recompile — same sources, different bytes. A manifest mismatch is
only trustworthy when both sides were captured from a full, purged rebuild; `docs/design/
build-cmake-driver.md`'s "Macros" section has the measured example and what to do before trusting a
mismatch. One hash covers the whole set, so a failure reports that *something* moved without naming
which file); and, separately, CMake's *own* copies of **eleven
files across two directories** — the ten RC-c files (`build-cmake/generated/`) plus `version.h`
(`build-cmake/generated-includes/`) — checked against those same source-tree hashes — the only
comparison here that actually looks at what CMake wrote, rather than re-hashing configure's copy a
second time (see `generated_cmake` in the table below; this is the fix for a real gap a final-review
caught — corrupting `build-cmake/generated/` or `build-cmake/generated-includes/version.h` used to
still report `PARITY OK`). `machine.h`, which lives in that same `generated-includes/` directory, is
deliberately NOT part of `generated_cmake` — it is covered separately by the semantic
`header_defines` dimension, because CMake's `machine.h` is not byte-identical to configure's.

Plus the **compiler-flag manifest**: the effective per-language (C / C++ / Fortran) codegen facts —
optimization level (last `-O<x>` wins), `-fwrapv`, `-mmacosx-version-min`, OpenMP, `-DNDEBUG`,
`-std=` — read from `config.status` (autotools) or `compile_commands.json` (CMake) and compared
*semantically*, so a dropped `-fwrapv` or an `-O2`→`-O0` slip fails parity even though it moves no
symbol, link edge, or SDK stamp (exactly the regression that once sat green for days; fixed in
`516c57573cc`). The `source` label itself is deliberately not compared — autotools→cmake is the
migration. **Known limitation (v1):** only the GLOBAL per-language flags are captured; per-TU
overrides (e.g. `differential_equations` forcing `colnew.f` to `-O0` on macOS) are invisible.

## Scope
Nine dimensions are fingerprinted today:

| Dimension | Compares | Armed in |
|---|---|---|
| `dylibs` | nm symbols (address-stripped) + otool deps + `LC_BUILD_VERSION` + `LC_RPATH` | Stage 0 / 1f-a |
| `executables` | the same, for `scilab-bin` + `scilab-cli-bin` | Stage 1f-a |
| `generated` | byte hashes of configure's OWN copies of all 13 generated files — always the SOURCE TREE, on both sides | Stage 0, grown 3→13 at RC-c |
| `generated_cmake` | CMake's OWN copies of eleven files across two directories — the ten RC-c files (`build-cmake/generated/`) plus `version.h` (`build-cmake/generated-includes/`) — byte-checked against `generated`'s baseline hashes — the actual CMake-vs-configure comparison. `machine.h`, in that same second directory, is covered separately by `header_defines`, not here | RC-c final-review fix, extended |
| `flags` | semantic per-language flag facts | Stage 1 |
| `jars` | normalized jar content manifests (entry list + MANIFEST, volatile lines dropped) | Stage 1f-b |
| `maven_jars` | normalized jar content manifests for Maven's `modules/*/target/*.jar` output, keyed at Ant's `modules/<m>/jar/<basename>` path so it is directly comparable to `jars` | Stage 2-c Task 1 (captured; dormant until Task 2 arms the baseline) |
| `header_defines` | a header's `{macro: value}` `#define` set — **semantic, not bytes** | RC-a |
| `tu_flag_facts` | per-TU flag facts **derived from the autotools generated Makefiles** | RC-b |

`generated_cmake` exists because `generated` alone cannot: resolving `GENERATED_FILES` against the
source tree, as `generated` does, reads configure's own output regardless of which build produced the
fingerprint, so a corrupted or stale `build-cmake/generated/` file was invisible to parity before this
— proven end-to-end (corrupting three of the ten RC-c files there still reported `PARITY OK`). No
baseline arming was needed: `generated_cmake` is checked against `generated`'s hashes, which were
already armed.

A later final review found `version.h` shared the identical gap: it is CMake-generated too, and
byte-identical to configure's copy (same as the ten RC-c files), but its CMake output lands in
`build-cmake/generated-includes/`, not `build-cmake/generated/` — so it was silently skipped by the
same directory-prefix loop, and the same corrupt-and-recapture proof applied (`PARITY OK` even with a
mangled `SCI_VERSION_MAJOR`). `parity/capture.py` closes it with a small explicit path mapping
(`_GENERATED_CMAKE_PATH_OVERRIDES`) rather than a second directory prefix, since `version.h` is the
only entry that needs one. `machine.h`, in that same `generated-includes/` directory, deliberately
stays out of that mapping — it is not byte-identical across generators, so `header_defines` remains
its only comparison. This is the **third** time this exact class of gap has been found (`machine.h`
before `header_defines` existed; the ten RC-c files before `generated_cmake` existed; now
`version.h`) — the underlying reason is structural, not a one-off: `generated` hashes configure's own
copy on *both* sides of every comparison, so any new CMake-generated artifact is unguarded until it
gets an explicit entry (here, or its own semantic dimension).

`jars` was added in Stage 1f-b, once CMake began driving the Ant build and jar contents could
actually move. It covers the **24 module jars**; the doc build's output (`scilab_*_help.jar`,
`scilab_images.jar`) is excluded by filename pattern, since those are help artifacts rather than
module jars. Stage 2 (Ant -> Maven) is still what can change jar contents most, and the normalized
manifest is what makes a reactor build's timestamp churn survivable.

`tu_flag_facts` (RC-b) is what `parity/flagfacts_check.py` now checks against, per translation
unit. It replaced **hand-written** expectations — a hardcoded default plus two manually maintained
override tables — which had a structural flaw worth stating plainly: such a gate enforces only what
someone remembered to record, and silently blesses everything else. It was returning rc=0 while real
divergences existed. `parity/makeflags.py` derives the facts instead, by expanding each generated
`Makefile`'s own compile recipes (whole-recipe expansion, because `-std=` arrives via `$(CC)` rather
than `$(SCI_CFLAGS)`; and a rule counts as live only if the build actually requests its object,
which excludes config.status-disabled and stale hand-written rules). `parity/capture.py` stores a
tree-wide default per language plus only the ~211 TUs that deviate. It is **frozen** into the
baseline on purpose: retire-`configure`'s later sub-stages delete the generated Makefiles this is
derived from, so the committed baseline is what lets autotools-derived truth outlive autotools.

Switching to derived expectations immediately surfaced 50 divergent files, 47 of which mismatched on
`openmp` — invisible before because the old tables never asserted `openmp` at all. Three of those
files carry live `#ifdef _OPENMP` branches, so CMake had been compiling serial code paths where
autotools compiled parallel ones. The `dylibs` dimension cannot see that class: `nm` lists symbol
*names*, and two `#ifdef` branches defining the same functions with different bodies yield an
identical symbol set. That is the concrete argument for keeping a semantic flag dimension at all.

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
