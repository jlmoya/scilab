# The CMake native-build driver (Stage 1f-c — native app + jars + generated version.h + help)

**Status:** DONE — verified end-to-end 2026-07-17 (from-scratch build → whole-tree
**rpath-aware** PARITY OK **incl. the 24 Java jars + the CMake-generated `version.h`** → the
real GUI runs on the CMake-built app; the `doc` target builds the help).
**What it is:** the top-level `scilab/CMakeLists.txt` + the helpers in `scilab/cmake/`
(`ScilabModule.cmake`, `ScilabAggregate.cmake`, `ScilabToolchain.cmake`) that build the
**entire native Scilab app** under CMake — the 64 baseline module dylibs, the 21 fold-in
core OBJECT libraries, the `libscilab`/`libscilab-cli` aggregate libraries, and the
`scilab-bin`/`scilab-cli-bin` executables — and drop each into the autotools `.libs/`
layout, matching the autotools build in exported symbols, link/dependency shape,
install_name, `LC_RPATH`, and compiler flag-facts (arbitrated by the parity harness).
This is *not* byte-for-byte identity — a fresh compile carries a distinct Mach-O UUID by
design; what the harness proves is behavioral/link-shape equivalence. Strategy context:
`docs/design/build-cmake-maven-migration.md`; design spec:
`docs/superpowers/specs/2026-07-16-stage1f-a-aggregate-executables-design.md`;
authoritative dylib list: `scilab/cmake/stage1e-manifest.md`.

CMake now drives the whole build — the native app, the Java jars (`sci-java-all`, invoking
the unchanged Ant), the generated `version.h` (`ScilabConfigure.cmake`), and the help build
(the `doc` target). **Coexistence is TEMPORARY scaffolding, not the destination:** autotools
still *configures* the tree — the `SCI_*FLAGS` + `build.incl.xml` + `machine.h` that CMake
still reads/uses — and each remaining stage DELETES part of it (retire `configure`, retire
`make`, Ant→Maven) until autotools is gone (the migration doc's retirement endgame). The
CMakeLists files are invisible to automake, so the autotools path is untouched and rollback
is free (`make clean && make` recovers everything). Retire-`configure` is the next stage;
Ant→Maven is Stage 2.

## Usage

```bash
# 0. Prerequisite — an autotools-CONFIGURED (and, for the full parity gate, BUILT) tree:
#    config.status, modules/core/includes/machine.h + version.h must exist.
cd scilab && ./configure <usual flags> && make        # see docs/design/build-modernization.md

# 1. Configure the CMake build (Makefile/Ninja generators only).
#    Fortran must be Homebrew gfortran; if a stray flang wins, pass
#    -DCMAKE_Fortran_COMPILER=gfortran (the driver hard-fails otherwise).
cmake -S . -B build-cmake

# 2. Build the whole app (native + jars) + drop each artifact into place
cmake --build build-cmake --target drop-in-all -j
#    Sub-targets: drop-in-<module> (one dylib), sci-foldin-all (the 21 OBJECT libs),
#    drop-in-libscilab / drop-in-libscilab-cli (aggregates), scilab-bin / scilab-cli-bin,
#    sci-java-all / drop-in-jars (the 24 Java module jars, via Ant).

# 3. The gate — parity vs the committed autotools baseline + per-TU flag facts
cd build-parity
python3 -m parity.capture .. /tmp/cand.json cand
python3 -m parity.diff baseline-autotools.json /tmp/cand.json          # PARITY OK, rc=0
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json .. # rc=0
```

Measured on the dev machine (M-series, `-j`, ccache warm): configure ≈ 46 s; a
from-scratch `drop-in-all` of the whole native app (rm -rf build-cmake first; 3668
compile/link steps — the 64 dylibs + the 21 fold-in modules incl. elementary_functions'
269 TUs + the two aggregates + the two executables) ≈ 3 min; capture ≈ 1 min.

**Rollback / recovering the autotools output** — each drop-in is a plain file copy, so
the autotools artifact is always one rebuild away. Module dylibs recover per module dir
(`make -C modules/<m> clean && make -C modules/<m>` — restores all of that dir's dylibs;
a bare `make` may not relink when the `.la` is newer than its sources, so `clean` forces
it). The aggregates + executables recover with a top-level `make` (they are the final
link steps of the autotools native build).

## What is in scope (and proven)

- **The 64 baseline module dylibs** across 46 module dirs — the first `foreach` block of
  the driver, one `scilab_module()` call each. Per-dylib rows, external deps, and module
  edges: `scilab/cmake/stage1e-manifest.md`.
- **The 21 fold-in core OBJECT libraries** — the second `foreach` block, one
  `scilab_object_module()` call each. These dirs (`elementary_functions`, `string`, `io`,
  `core`, `linear_algebra`, …) build no standalone dylib on macOS (automake declares their
  `.la` `noinst`); their objects fold into the aggregates via `$<TARGET_OBJECTS:sci<m>-obj>`.
  `mpi` (macOS-inert) and `javasci` (its only native output, `libjavasci2`, is a separate
  pkglib that links *against* `libscilab`) are deliberately not fold-in targets.
- **The two aggregates** `libscilab` (GUI: ENGINE + jvm + GUI_LIBS; 59 deps) and
  `libscilab-cli` (ENGINE only; 39 deps), both exporting the same 3543 symbols (both fold
  the identical 21 modules) — `scilab_aggregate()` in `modules/CMakeLists.txt`. Each LIBADD
  member is classified FOLD (objects enter the aggregate, no dep) vs LINK (recorded as an
  `LC_LOAD_DYLIB` dep at its install_name); libtool's transitive `.la` records
  (`libscisundials`, `libsciconsole-minimal`, the system/keg libs) are reproduced
  explicitly because CMake target links do not propagate.
- **The two executables** `scilab-bin` and `scilab-cli-bin` — `scilab_executable()`, linked
  against the aggregate + the exact `LDADD`/platform `LDFLAGS` transcribed from the
  configured Makefile (byte-verified to reproduce the baseline's `LC_LOAD_DYLIB` order,
  `LC_BUILD_VERSION`, and `LC_RPATH`). Rpaths: `scilab-cli-bin` → `[/usr/lib, gcc]`;
  `scilab-bin` → `[/usr/lib, gcc, jdk-25/lib]`.
- All policy (flags, include order, `-std=c++17`, install_name, link classes, the fold vs
  link classification, drop-in targets, the JDK path) lives in `scilab/cmake/`; a policy
  change touches one file. `JAVA_HOME` is resolved once as `SCILAB_JAVA_HOME` in
  `ScilabToolchain.cmake` (config.status-first, `/usr/libexec/java_home` fallback with a
  warning), consumed by jvm + the JDK modules + the GUI aggregate's `-ljli`.

### End-to-end proof (2026-07-17)

1. **From-scratch build:** `rm -rf build-cmake` → configure + `drop-in-all` → all 64
   dylibs + 21 fold-in OBJECT libs + 2 aggregates + 2 executables built and dropped in,
   rc=0 (3668 steps).
2. **Whole-tree rpath-aware gate:** `PARITY OK` rc=0 (68 dylibs — the 64 module dylibs +
   both aggregates — and the 2 executables, all matching the autotools baseline incl.
   `LC_RPATH`) and `flagfacts_check` rc=0 (extended to the 21 fold-in modules).
3. **The real app on the CMake executable:** the CMake-built `scilab-cli-bin` (dropped
   into `.libs/`, UUID-matched to `build-cmake/scilab-cli-bin`) computed `1+1 → 2.`, a
   fold-in compute `sum([1 2 3 4 5]) → 15.` (elementary_functions/core), the interpolation
   gateway `splin` (dlopen-loaded), and resolved the `covStart` gateway — clean exit rc=0.
   CMake links a *working* app, not just a shape-matching one.
4. **The autotools build still works** — exercised during the pure-autotools re-baseline
   (`make clean && make` rebuilds the module dylibs + aggregates + executables; the
   rpath-aware baseline is captured from *that* independent autotools reference, not from
   the CMake tree).

## The parity harness is rpath-aware (Stage 1f-a)

`parity/fingerprint.py` parses `LC_RPATH` (ordered, from `otool -l`); `capture.py` records
it per dylib and per executable; `diff.py` compares it **order-significantly**. The
comparison is fault-injected in the unit suite (drop or reorder an rpath → parity fails,
naming the artifact). This closed the harness's rpath blind spot (a dropped or spurious
rpath previously passed `PARITY OK`; the jvm/JDK modules had been hand-checked). The
baseline (`baseline-autotools.json`) was re-captured rpath-aware from a pure-autotools
rebuild, so every Stage-1e dylib was re-checked against it — a free rpath regression sweep.

## The Java jars (Stage 1f-b)

`sci-java-all` (in `scilab/cmake/ScilabJava.cmake`, one call to `scilab_java_bridge()`)
builds the **24 Java module jars** by invoking the **unchanged** Ant. Approach: ONE target
wrapping the existing `modules/prebuildjava` super-build (its `build.xml` `all` target
hand-topo-sorts 23 module jars + drives Ivy), plus a second bare-ant for `modules/terminal`
(the 24th jar — it is absent from `prebuildjava`'s list and autotools builds it via the
per-module recipe; **GUI-gated**, matching `modules/terminal/Makefile.am`'s `if GUI`). The
topo-sort and inter-module Java deps stay inside Ant — Stage 2's Maven reactor replaces them
wholesale (and must list `terminal` explicitly). `ANT`, `NEED_JAVA`, and `GUI` come from
`config.status`; `JAVA_HOME` from `SCILAB_JAVA_HOME` (1f-a). The Ant invocation is **bare**
(no `-D`): `target-jar` defaults to `jar` and the conditionals resolve from the
configure-substituted `build.incl.xml`. Jars land in `modules/<m>/jar/` (the same place Ant
always writes them — drop-in is automatic), and `sci-java-all` rides `drop-in-all`.

**Jar parity** is a new harness dimension (`jars` section): per jar, the sorted map of
`entry-name → sha256(content)`, with volatile `META-INF/MANIFEST.MF` lines normalized out
(`Ant-Version`, `Created-By`, …, and the `Implementation-Version: <DSTAMP> <TSTAMP>`
build-date stamp — form-anchored so a real semantic version survives). This is the honest
analog of native byte-shape parity — it changes only if the source, compile flags, JDK, or
module set changes — without chasing jar timestamp nondeterminism. The baseline's `jars`
section was captured from a pure-autotools jar rebuild; a two-build **cross-minute**
reproducibility probe confirmed the normalize-list complete, and a fault-injection (mutate a
jar entry) fails parity naming the jar + entry.

### Java end-to-end proof (2026-07-17)

From-scratch `drop-in-all` built the whole app + 24 jars; whole-tree **PARITY OK** (68 dylibs
+ 2 executables + 24 jars) + flag-facts rc=0. Headless `-nw` smoke (`bin/scilab -nw -nb -e
"disp(1+1); exit(0)"`) started the JVM + jars, rc=0, jar-error-free. The full **GUI**
(`bin/scilab`, `Scilab-2027.0.0` — UUID-matched to `build-cmake/scilab-bin`) launched on the
CMake-built jars with a jar-error-free startup log. The autotools `make` still builds the
jars via `prebuildjava` (coexistence).

## Generated headers + help (Stage 1f-c)

`scilab/cmake/ScilabConfigure.cmake` generates **`version.h`** by `configure_file`-ing the
existing `version.h.in` (`@ONLY`, version values from `config.status`) into
`build-cmake/generated-includes/` — **byte-identical** to configure's copy (`version.h` is
exactly the template with three `@SCILAB_VERSION_*@` substitutions), so the harness keeps
byte-hashing it, unchanged. The dir is on the module include path; during coexistence the
byte-identical source-tree copy still resolves first (`ScilabModule.cmake` keeps
`core/includes` ahead, reproducing automake's parity-critical `-I` order), and the generated
copy becomes the resolver when the source-tree header is deleted at retire-`configure`.
**`machine.h` is NOT generated here** — it is entangled with configure options + pkg-config
substitutions and moved to retire-`configure` **RC-a** (below), which also adds the
semantic-header parity dimension it needs; `version.h` is byte-identical so it needs no such
dimension.

## `machine.h` — computed in CMake (retire-configure RC-a)

`scilab/cmake/ScilabMachineHeader.cmake` **computes** all of `machine.h`'s macros itself and
`configure_file`s `cmake/machine.h.cmake.in` into `build-cmake/generated-includes/`, beside
`version.h`. It never reads a value out of `config.status` — that independence is the whole
point: it is what makes the parity check a real gate rather than a tautology. The five sources
are ~131 `check_include_file`/`check_symbol_exists`/`check_function_exists`/`check_type_size`
probes, pkg-config-family values (via `curl-config`/`xml2-config`, which is what the `m4/` macros
actually call — not `pkg-config`), Fortran mangling, the `--enable`/`--with` options, and the
`PACKAGE_*` boilerplate. A sixth source surfaced during the port and is worth naming for RC-b/RC-c:
**libtool** (`LT_OBJDIR`), which belongs to none of the five.

Unlike `version.h`, the output is **not** byte-identical to autoconf's (comment style, `#define`
vs `/* #undef */` formatting, and ordering all differ), so equivalence is proven **semantically**
by the harness's `header_defines` dimension: `parse_defines` reduces each header to its
`{macro: value}` `#define` set and the diff reports `macro added/removed/changed` by name. The
baseline is armed from **configure's source-tree header** while capture reads the
**CMake-generated** one — the two sides deliberately read different files, because the gate being
asserted is "CMake's header == configure's header". Coexistence is unchanged: the source-tree
header is untouched and still resolves first, and since the two are semantically equal the
compiled output is identical either way; the generated copy becomes the resolver at RC-e.

Two caveats a future reader will need:

- **`LIBARCHIVE_CFLAGS`/`LIBARCHIVE_LIBS` are transcribed literals, not computed** — the single
  exception, and a deliberate one. `m4/libarchive.m4:61-62` overwrites the libarchive-specific
  variables with the *entire* global `$LIBS`/`$CFLAGS` accumulator, so configure's captured value
  is a snapshot of unrelated state rather than a fact about libarchive (hence an **openssl** `-I`
  path in `LIBARCHIVE_CFLAGS`, and a `-ldl` from an unscoped `AC_CHECK_LIB` 383 lines earlier).
  Computing the *correct* value would produce a different one and fail parity — "reproduce, don't
  improve" makes transcription the faithful choice here.
- **The reference itself is environment-contingent.** That openssl fragment comes from the
  developer's shell (`export CFLAGS=...` in `~/.bash_profile`), which autoconf inherited; nothing
  in the tree produces it. A fresh `./configure` from CI, from zsh, or from a login shell that
  does not source that profile can therefore shift `LIBARCHIVE_CFLAGS` and break this macro's
  parity **with no CMake-side change involved**. The committed baseline freezes the reference,
  which is the mitigation — but do not mistake such a shift for a CMake regression.

`scilab/cmake/ScilabHelp.cmake` adds the **`doc`** target (opt-in, `BUILD_HELP`-gated from
`config.status`, NOT on `drop-in-all`): it runs the built `scilab-adv-cli` headless per
`ALL_LINGUAS_DOC` locale (`xmltojar`), reproducing the top-level `Makefile`'s `doc:` recipe
env exactly (incl. the seven `DOC_JAVA_XML_OPTS` jdk.xml limits). `--disable-build-localization`
is handled (absent `ALL_LINGUAS_DOC` → a no-op `doc`, not a configure failure). **Build
`drop-in-all` first** — by its opt-in post-step design `doc` runs the app but does not build
its prerequisites, so `cmake --build … --target doc` on an unbuilt tree fails at
`scilab-adv-cli` rather than building it (unlike `make doc`, which depends on it).

### Headers + help end-to-end proof (2026-07-17)

From-scratch `drop-in-all` generated `version.h` (byte-identical to configure's) and the CMake
build consumed the generated-includes path; whole-tree **PARITY OK** (68 dylibs + 2 executables
+ 24 jars) + flag-facts rc=0. The `doc` target built the `en_US` help jar
(`scilab_en_US_help.jar`) on the CMake-built `scilab-adv-cli`. `make` still generates the
headers + builds help (coexistence).

## Compiler flags — computed in CMake (retire-configure RC-b)

`scilab/cmake/ScilabFlags.cmake` **computes** the compiler-flag policy and exports
`SCILAB_C_FLAGS` / `SCILAB_CXX_FLAGS` / `SCILAB_Fortran_FLAGS`, which
`ScilabModule.cmake`'s `_scilab_module_flag_env()` consumes. It replaces three hardcoded
literal lists whose own header admitted they were "transcribed from the CONFIGURED
autotools build … not invented here". It reads nothing from `config.status`: the
release/debug branch is a CMake `option()` whose default is declared here, and
`-mmacosx-version-min` derives from `CMAKE_OSX_DEPLOYMENT_TARGET`. Equivalence was proven
at full scale — **3600/3600 compile lines byte-identical** across two fresh worktrees.

**The trap, recorded because a naive port falls straight into it:** `-std=gnu23` and
`-std=c++17` are **not** in `SCI_CFLAGS`/`SCI_CXXFLAGS`. Autotools carries them in the
*compiler* variables (`CC = gcc -std=gnu23 -arch arm64`, `CXX = g++ -arch arm64
-std=c++17`), so mirroring only `SCI_*FLAGS` silently drops the language standard.
Also documented in the file: `SCI_CPPFLAGS` is a phantom (referenced by three
`Makefile.am`s, assigned nowhere, absent from `config.status`); five ingredient groups are
dead everywhere; and `COMPILER_FFLAGS` is dead *here* but live on the Intel-compiler path —
a distinction worth preserving, since "dead everywhere" and "dead in this configuration"
imply different code.

### The flag gate now derives its expectations

`parity/flagfacts_check.py` used to assert **hand-written** expectations: a hardcoded
default plus two manually maintained override tables. It therefore enforced what someone
had remembered to record and silently blessed what they hadn't — it returned rc=0 while
real divergences existed. It now takes its expectations from `tu_flag_facts` in
`baseline-autotools.json`, **derived** from the autotools generated Makefiles by
`parity/makeflags.py` (whole-recipe variable expansion; a rule counts as live only if the
build actually requests its object, which excludes config.status-disabled and stale
hand-written rules). Frozen into the baseline deliberately: retire-`configure`'s later
sub-stages delete the generated Makefiles, so the committed baseline is what lets the
autotools-derived truth outlive autotools.

Invocation gained two arguments — `python3 -m parity.flagfacts_check <compile_commands.json>
<baseline.json> <source_root>`.

`min_macos` is deliberately **not** derived: a footgunned TU's recipe drops
`-mmacosx-version-min` entirely, while `CMAKE_OSX_DEPLOYMENT_TARGET` stamps 11.0 on every
CMake TU. That difference was reviewed and accepted, so `min_macos` is asserted as a
CMake-side invariant while `opt`/`wrapv`/`ndebug`/`std`/`openmp` come from the derived facts.

**What switching to derived expectations immediately found — and why it matters.** The gate
went from rc=0 to **50 divergent files**: the 3 unreproduced footgun modules above, plus
**47 mismatching on `openmp`**, which the old gate could never have seen because it never
asserted `openmp` at all. CMake requested OpenMP for four modules where autotools compiles
with it in two, so it both missed the flag and added it spuriously. That was **not**
cosmetic: `-fopenmp` defines the `_OPENMP` macro, and three `differential_equations` files
carry live `#ifdef _OPENMP` branches selecting serial-vs-parallel solver construction and
thread-count parsing (12 files tree-wide guard on it). CMake was compiling the serial paths.

Nothing had caught it because `nm` lists symbol *names*: two `#ifdef` branches defining the
same functions with different bodies produce an identical symbol set, so the dylib and
executable fingerprints are structurally blind to it. All 50 are closed; the gate is rc=0.

## CI

`.gitlab-ci.yml` (fork-native pipeline) carries two guards:

- **`sanity:cmake-driver`** (shared runner, every pipeline): a cheap wiring/manifest
  consistency check (no Mach-O needed) that mirrors the driver's two-tier structure —
  (A) every `add_subdirectory(modules/X)` across both `foreach` blocks has a real
  `modules/X/CMakeLists.txt`; (B) the dylib block equals the manifest's 46 dylib dirs;
  (C) the fold-in block equals the aggregate's `_scilab_fold_objects` set; (D) the
  manifest still holds exactly 64 dylib rows; (E) both aggregate + both executable calls
  are still declared; (F) the Java bridge (`scilab_java_bridge()` + `add_dependencies(
  drop-in-all sci-java-all)`) is still wired; (G) the 1f-c codegen (the
  `ScilabConfigure.cmake` include + `scilab_help_target()`) is wired — plus the
  parity-harness unit suite (`pytest build-parity/tests`, hermetic; the acceptance tests
  self-skip without a built tree).
- **`parity:cmake-drop-in`** (self-hosted macOS arm64 runner, rule-gated on
  `$SCILAB_NATIVE_RUNNER == "1"`): the real gate — `drop-in-all` + rpath-aware parity diff
  + flag facts on the built tree. Because the aggregates + executables + the 24 jars ride
  `drop-in-all` and the capture fingerprints every `.libs/` artifact (incl. `LC_RPATH`) and
  every `modules/*/jar/*.jar`, this job now gates the whole app — native + jars —
  automatically (the runner has Ant + the JDK). Set the project variable only while such a
  runner is registered; without it the job is not created (shared runners can neither build
  nor fingerprint Mach-O).

## Deferred (deliberately out of Stage 1f-c)

- **Retire `configure` (the next stage):** CMake computes the `SCI_*FLAGS`, generates
  `build.incl.xml`, generates `machine.h` (porting configure's ~150 probes + its
  option/substitution macros — with a NEW *semantic* header parity dimension, since a
  CMake-generated `machine.h` is not byte-identical to autoconf's, unlike `version.h`), and
  owns the macros build → `./configure`/`config.status` **deleted**. Then retire `make`
  (delete the `Makefile.am`s). Coexistence is temporary — see the migration doc's endgame.
- **Stage 2 — Ant → Maven:** the `prebuildjava` topo-sort + Ivy → one Maven reactor (which
  must list `modules/terminal` explicitly); jar byte-reproducibility (`SOURCE_DATE_EPOCH`);
  the ~23 dead jars drop out. Stage 1f-b keeps Ant unchanged and fingerprints jar *content*
  (not bytes).
- **Machine-specific absolute paths:** the calls transcribe `config.status`-faithful
  absolute paths (the jdk-25 lib dir, the Xcode SDK, Homebrew Cellar dirs, the miniconda
  FLIBS lib dir, the from-source `xlnt-prefix`). These are parity-neutral but pin the CMake
  build + native CI gate to this machine's layout; the de-autotools driver should derive
  them from the active toolchain (`xcrun --show-sdk-path`, `brew --prefix`, the configured
  JDK).
- **C++ standard bump (spec §12):** the tree is held at `-std=c++17` to match the baseline;
  the c++23 bump is a codegen axis — bump autotools first, re-baseline, then flip **one
  line** in `ScilabModule.cmake`.
- **The `_CFLAGS`-replaces-`AM_CFLAGS` footgun:** **6 modules / 33 C translation units**
  (`parameters`, `windows_tools`, `string/src/c`, `history_browser`, `types`, `preferences`)
  compile without any of `SCI_CFLAGS` — no `-O2`, no `-fwrapv` — because a per-target
  `_CFLAGS` replaces `$(AM_CFLAGS)` wholesale rather than appending. Six Fortran files
  separately compile at `-O0` via a deliberate `if IS_MACOSX` gfortran workaround. CMake
  reproduces all of it faithfully. The measured figure replaces an earlier "a handful of
  dirs" estimate: RC-b's derived gate found that **3 of those modules were silently NOT
  reproduced** and CMake was compiling them at full flags. The actual fix (restore the
  optimization and the `-fwrapv` hardening, then re-baseline) remains a deliberate later
  stage — those TUs currently lack the UB hardening applied tree-wide everywhere else.
