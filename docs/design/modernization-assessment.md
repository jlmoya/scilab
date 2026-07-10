# Scilab modernization assessment (grounded, in progress)

Status: **discovery**, 2026-06-27; native track opened 2026-07-04. First-pass analysis of the actual
codebase for the [[scilab-modernization-vision]] north-star.

> **Scope = the ENTIRE application, not just Java** (user, 2026-06-27): C, C++, Fortran, Tcl/Tk,
> the `.sci` macro language, the build system, every dependency, CI/CD, and packaging. First pass
> covered **2 of N tracks** (dependencies + Java). The **native (C/C++/Fortran)** track is now
> **opened** with a critical, class-level finding (a `-O2` UB miscompilation — see below); **build +
> CI** are still to run. The native + build half is the larger half and matters just as much.
>
> **Operating principle** (user, 2026-07-04): there is no "pre-existing errors are not our
> responsibility" — on this fork we own the whole application; if something is wrong, we fix it,
> period. The goal is a bug-free application that performs with excellence.

This is a *map*, not a plan — we prioritize, then do focused per-track plans
(characterize → change one axis → prove parity).

## Scale (Java side, measured)

- **1,505 `.java` files, ~312,600 LOC** (incl. SWIG/JFlex generated). 8 modules ≈ 85% of it;
  `gui` (71.5k) + `xcos` (56k) alone ≈ 41%. Others: `scinotes` 35k, `graphic_objects` 32k,
  `scirenderer` 19k, `renderer` 18k, `ui_data` 18k, `helptools` 15k.
- Native (C/C++/Fortran) LOC: **not yet measured** (pending track).

## Dependency health — 23 abandoned/EOL libs (~27%)

**Critical-path abandonware** (highest risk):
- **JOGL / gluegen 2.5.0** — the graphics stack (→ being replaced by the bgfx work).
- **Swing frameworks**: flexdock 1.2.5 (dead ~2008), skinlf 1.2.3 (~2002), jgoodies-looks 2.7.0,
  jrosetta 1.0.4 (console, 2011) — **FlatLaf 3.4.1 is already bundled** as the modern replacement.
- **Docs/math rendering**: jhall 2.0 (JavaHelp, dead), jeuclid 3.1.14 (2013), jlatexmath 1.0.7
  (2014), freehep-* 2.4 (~2005) → MathJax/KaTeX + a browser/HTML help path.
- **xcos**: JGraphX 2.1.0.7 (EOL → maxGraph) across 112 files.
- **Test/build**: JUnit 4.10 (→ JUnit 5), cobertura 2.1.1 (dead → JaCoCo), asm 3.3.1 (→ 9.x),
  commons-logging 1.1.1 (SLF4J 2.0.9 already present).
- **Jakarta migration**: javax.activation 1.2.0 + jaxb 2.3.1 → jakarta.* (already partly present).

**Healthy** (keep/minor bumps): Saxon-HE 12.4, Lucene 9.10, Guava 33.2, ANTLR 4.13.1, gson,
jna 5.14, FlatLaf 3.4.1, commons-io/codec, httpclient5, jediterm 3.70. Native numerics are
current (OpenBLAS, FFTW3, HDF5, SuiteSparse, Eigen, PCRE2, Arrow).

## Java code — a JDK-25 *compiler target* on a Java-6/7 *source dialect*

The build compiles at `source/target=25` but the source uses almost no post-Java-8 features:
- **0** records, **0** sealed, **0** `var`, **0** switch-expressions, **0** text blocks (exact greps).
- Old idioms dominant: **483** `Vector`, **280** anonymous `ActionListener` + **118** `Runnable`
  (prime lambda targets), **50** deprecated boxing ctors (`new Integer(...)`), 39 `StringBuffer`.
- **12** files override `finalize()` (deprecated-for-removal; the xcos SWIG `VectorOfX` wrappers).
- **372** `printStackTrace()` + **158** empty `catch {}` — error-handling debt.
- **God-classes**: `gui/.../ScilabBridge.java` 3,060 LOC / **323 static** dispatch methods;
  `SwingView.java` 103 type-switch branches; `Axes` 2,939; `XcosDiagram` 2,671; `SciNotes` 2,592.
  23 files > 1,000 LOC.
- **Native interop**: 29 `native` methods + 61 SWIG-generated JNI files → **strong fit for the JDK
  Foreign Function & Memory API (Panama, stable in 22+)**, starting with `javasci`/`call_scilab`
  and the `graphic_objects` data-loader (hot vertex buffers).

## Prioritized roadmap (Java track; sequence by risk/leverage)

| # | Work | Effort | Impact |
|---|------|--------|--------|
| 1 | Remove JDK-incompatible/dead deps (asm 3.3.1, cobertura, jgoodies/skinlf→FlatLaf; fix 50 boxing ctors) | S | High |
| 2 | Eliminate `finalize()` (12 files) → `Cleaner`/FFM `Arena` | S–M | High |
| 3 | Automated idiom pass (OpenRewrite): diamond, `var`, lambdas for 398 anon listeners, `Vector`→`ArrayList`, try-with-resources | M | High |
| 4 | Port `javasci`/`call_scilab` JNI → Panama FFM (incremental), then the graphic_objects data path | L | High |
| 5 | Sealed hierarchies + pattern-switch for `SwingView`/the widget taxonomy | M–L | Medium |
| 6 | Decompose god-classes; collapse the static `gui.bridge` layer | L–XL | Medium |
| 7 | Replace EOL UI frameworks (JGraphX→maxGraph, flexdock, JavaHelp) | XL | Medium |
| 8 | Error-handling hygiene (printStackTrace/empty-catch → logging) | M | Low–Med |

**Guardrails:** do **not** attempt a Swing→JavaFX rewrite (no runtime mandate, XL cost); the
leverage is dead-framework removal + the FFM native boundary. Gate the L/XL items on regression
tests (cobertura is dead — coverage is unknown).

## Native track — first finding: UB miscompiled at -O2 (CRITICAL, class-level)

The native C/C++/Fortran is decades old and was written for a non-optimizing / "signed overflow
wraps" compiler. Built with a **modern clang/gcc at `-O2`** (the shipped optimization level on this
macOS-2027 arm64 fork), **undefined behaviour that was benign for 20 years now gets miscompiled** —
silently, corrupting data, and invisible at `-O0`, so it passes casual testing. This is a *class* of
latent bug, not a one-off.

**Confirmed + fixed instance — `rand()` returned `Inf` for every element** (2026-07-04). Discovery
chain: `grayplot(1:20,1:20,rand(20,20))` rendered blank → its `z` was all `Inf` → **`rand` itself
returns `Inf`** (default "uniform"). Root cause in `modules/elementary_functions/src/c/basic_functions.c`
`durands()` (Malcolm-Moler uniform generator): the modulus-finding loop terminated by letting a signed
`int` overflow —

```c
m = 1; while (m > m2) { m2 = m; m = itwo * m2; }   /* itwo == 2 */
```

Signed overflow is UB. clang `-O2` leaves `m2 = 0` → `halfm = 0` → `s = 0.5/halfm = +Inf` → every value
`= (double)*_iVal * Inf = Inf`. At `-O0` it worked (`m2 = 2^30`), which is why it survived for years.
Proven with a standalone repro (clang `-O0` correct, `-O2` → `s = inf`). **Fix**: compute the same `m2`
without overflowing — `m2 = 1; while (m2 <= INT_MAX/itwo) { m2 = itwo*m2; }` (+ `<limits.h>`). Verified:
`rand(1e5)` uniform (mean 0.4997, std 0.2884), normal (mean ~0, std 1.000), complex, and seed-repro all
correct; grayplot renders. (The renderers — g2d/Vulkan — were innocent: they correctly cull all-`Inf`
facets.) `rand` is one of the most-used builtins, so this silently poisoned anything downstream of it.

**Systemic implication — the whole tree was exposed.** The base C build is `-DNDEBUG -g1 -O2` and
`-fno-strict-overflow` / `-fwrapv` was set nowhere (there is an opt-in `-fsanitize=address`, but no
UBSan). `rand` is very unlikely to be the only such bug. Two class-level actions:

1. **Add `-fwrapv` globally** — **DONE (2026-07-09)**: the non-debug `DEBUG_CFLAGS` /
   `DEBUG_CXXFLAGS` / `DEBUG_FFLAGS` in `configure.ac` (and the tracked generated `configure`, so no
   autoreconf churn) now carry the flag for the gcc *and* clang branches; the live dev tree's 83
   generated Makefiles were patched in place (avoiding a reconfigure that would clobber the macOS
   OpenMP Makefile fixes) and all 3,600 native objects rebuilt with it. Signed overflow can no longer
   be exploited by the optimizer anywhere in the tree — this would have prevented `durands` and
   prevents any sibling. Zero risk to correct code (it defines wrapping semantics; standard for
   legacy-numerics codebases, e.g. the Linux kernel) and zero measurable compile cost (worst TU:
   3.74 s → 3.77 s). **Engineering note:** the first attempt used `-fno-strict-overflow`, which clang
   expands to `-fwrapv -fwrapv-pointer`; the pointer-wrap variant sent an optimizer pass
   quasi-exponential on template-heavy TUs (`ast/types/arguments.cpp`: 3.7 s → 60+ min), so the
   policy is deliberately the integer-only `-fwrapv`. Pointer-overflow UB remains a *discovery*
   item for the UBSan pass below. CI (`guard:ub-miscompile`) greps the policy into place and diffs a
   `durands` O0/O2 run so the class can't silently return.
2. **Run a UBSan pass** (`-fsanitize=undefined`) over the test suite to **enumerate** the remaining UB
   (overflow, OOB, bad shifts, misalignment) and fix each — turning "unknown unknowns" into a work
   list. *(Open — next.)*

This is the "no pre-existing-error-is-not-mine" principle in practice: on this fork everything is ours
to fix. It also strengthens the case for the FFM/native-boundary work (roadmap #4) — the more of this
code we can characterize and re-express safely, the fewer of these traps remain.

## Native track — Apple-Silicon: 100% native, no Rosetta (2026-07-09)

**Trigger:** the packaged `/Applications/Scilab-2027.0.0.app` exposed an "Open using Rosetta" toggle.
Rosetta 2 is on Apple's deprecation path, so the goal is a guaranteed-native arm64 app.

**Finding — nothing actually requires Rosetta; it was a packaging-metadata gap.** Every layer of the
runtime is already arm64 (audited with `lipo -archs` / `file`):

| Layer | Arch |
|-------|------|
| `scilab-bin` + all 160 module dylibs | arm64 |
| JDK 25 JVM (`java`/`libjli`/`libjvm`) | arm64 |
| Thirdparty (JOGL, GlueGen, MoltenVK) | universal (arm64 slice present) |
| Every Homebrew/system dep in the link closure | arm64 slice present |
| Installed toolboxes (`~/.Scilab`, 21 native libs) | arm64 |
| Launcher script | no forced `arch -x86_64` |

The only gap: our app's `Info.plist` lacked the two keys the **official** arm64 build
(`scilab-2026.1.0.app`) carries — `LSRequiresNativeExecution=true` and `LSArchitecturePriority=[arm64]`.
Without them LaunchServices *permits* the Rosetta toggle; because a process is single-arch, one flip
drags the **entire** JVM (and every arm64 JNI lib it loads) through x86_64 translation.

**Fix applied (2026-07-09):** added both keys to `package-macos.sh`'s Info.plist heredoc, and patched
the installed app in place (`plutil -insert` → ad-hoc `codesign --force` → `lsregister -f`). No code
changes, no library upgrades — the arm64 build/JDK/thirdparty work was already done.

**The one ongoing risk is toolboxes:** an x86_64-only toolbox `.dylib`, once loaded, either fails
(an arm64 process can't `dlopen` x86_64) or is the reason a user re-enables Rosetta. The installed set
is clean; the porting campaign (finance/ATOMS ports) must keep enforcing arm64. Recommended: a
`tbxInstall`/`package-macos.sh` arch gate that rejects/reports any non-arm64 native lib. (The stray
`krisp`/`sci_gsl` `.so` in the dev tree are Linux ELF — never loaded by macOS.) If a JRE is ever
bundled into the `.app` instead of using the system JDK, it must be arm64 too.

## Still to analyze (next discovery pass)

- **Native track** (STARTED — see the finding above): C/C++/Fortran LOC + standards used; the deprecated
  stack API (`__USE_DEPRECATED_STACK_FUNCTIONS__`); f2c Fortran; C++ modernization (C++20/23, RAII).
  **Highest-priority native item: the `-fno-strict-overflow` + UBSan class-elimination above.**
- **Build/CI track**: the Autotools/Ant → Maven/CMake path; the band-aid elimination
  ([[scilab-modernization-vision]] root-cause notes); CI/CD + reproducibility across environments.
