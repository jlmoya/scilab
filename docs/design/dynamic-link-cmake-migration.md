# Retiring the runtime autotools skeleton: `ilib_build` on CMake

**Status:** fully scoped, no open questions, not started.
**Decisions (2026-08-01):** emit CMake (option A) · reuse `scilab_module()`'s
policy via an installable package · require CMake, do not bundle · deprecate the
autotools path and **remove it in 2027.1**.

This is the last live autotools in the tree. Everything else — the project's own
build — moved to CMake + Maven; this skeleton survived the purge deliberately,
because it does not build Scilab. It builds *toolbox gateways on the end user's
machine*, which is a different contract with a different blast radius.

A plain generated Makefile (option B) was also on the table and was the cheaper
engineering answer — it removes autotools while adding no dependency at all. It
was **not** chosen. CMake was, and the reasons are worth recording, because they
are what justify paying the one real cost below:

- it collapses the **two** platform paths (autotools/Unix and nmake/MSVC) into a
  single generator, which B cannot do;
- it matches the direction the rest of the project already went, so there is one
  build language in the codebase rather than three;
- toolbox authors get an inspectable, debuggable, IDE-openable build file
  instead of a generated Makefile they are not meant to read.

---

## 1. What exists today (measured, 2026-08-01)

`modules/dynamic_link/src/scripts/` — **18 files, 1.8 MB**:

```
aclocal.m4  compile  compilerDetection.sh  config.guess  config.sub  configure
configure.ac  depcomp  install-sh  ltmain.sh  m4/  Makedll.incl  Makefile.am
Makefile.in  Makefile.incl.mak  missing  scicompile.sh  TEMPLATE_MAKEFILE.VC
```

### The Unix flow

`ilib_gen_Make_unix.sci` drives it:

1. copies the skeleton into the user's build dir (TMPDIR by default);
2. `compilerDetection.sh` runs `configure`, producing `Makefile.orig`;
3. `scicompile.sh` rewrites two template lines in `Makefile.orig` — the
   `_la_SOURCES` and `_la_OBJECTS` lists — and substitutes the real library name
   for the placeholder `libsciexternal`;
4. `host("touch Makefile")` — a deliberate hack. The freshly copied `configure`
   has mtime "now", `Makefile` depends on `configure`, so `make` would re-run
   configure and overwrite what `scicompile.sh` just produced. The touch defeats
   that rule (`ilib_gen_Make_unix.sci:180-188`);
5. `make`.

**Correction to an earlier claim of mine:** configure is *not* re-probed on every
toolbox build. `Makefile.orig` is cached and reused when the compilation flags
are empty or unchanged since the last call (`:167-177`). The per-build cost is
therefore lower than "a full configure each time" — the cost is the 1.8 MB of
vendored 1990s shell and the maintenance surface, not per-build latency.

### The Windows flow

Entirely separate: `Makefile.incl.mak` (nmake/MSVC variable block, with
arm64/x64/ix86 branches) plus `TEMPLATE_MAKEFILE.VC`. `ilib_gen_Make_unix`
returns immediately on Windows. Two implementations of one idea.

### The generator's input contract

Small, and this is what makes A tractable:

```scilab
ilib_gen_Make_unix(names, files, libs, libname, ldflags, cflags, fflags, cc, tables)
```

with flags assembled by `gencompilationflags_unix(ldflags, cflags, fflags, cc, flagsType)`,
which emits `CFLAGS`, `CXXFLAGS` (same as C), `FFLAGS`, and — per bug #4787 —
deliberately does **not** always set `LDFLAGS`.

---

## 2. How Scilab uses CMake today — and why the gateway path should reuse it

**Decision (2026-08-01): reuse the project's existing mechanism verbatim rather
than invent a second one.** That mechanism is:

- **A top-level driver.** `CMakeLists.txt`, `cmake_minimum_required(3.20)`,
  `project(scilab-native C CXX Fortran)`, including 14 helpers from `cmake/`.
- **One central abstraction**, `cmake/ScilabModule.cmake`: `scilab_module(NAME …)`
  for the 64 shared module dylibs and `scilab_object_module(NAME …)` for fold-in
  OBJECT libraries. They share `_scilab_module_flag_env()` (per-language flags
  and includes) and `_scilab_module_apply()` (defines, includes, options,
  target properties).
- **Declarative per-module files.** A module's `CMakeLists.txt` is a *call*, not
  a build script — `modules/fileio/CMakeLists.txt` is
  `scilab_object_module(fileio SOURCES …)` and nothing else. No module
  hand-rolls `add_library`.
- **Policy lives in exactly one place** — and it is already the policy a toolbox
  gateway needs:
  - `-undefined dynamic_lookup` + `-no_fixup_chains`, because gateways call
    Scilab API symbols (`Scierror`, `types::Function`, …) that resolve at
    dlopen time, never at static link time. This is *precisely* the toolbox
    gateway situation, and precisely what libtool does for them today;
  - the two `LC_RPATH`s, `INSTALL_NAME_DIR`, `BUILD_WITH_INSTALL_NAME_DIR`,
    MACHO compat/current versions, `OUTPUT_NAME`/`PREFIX`/`SUFFIX`;
  - Fortran deliberately gets a *different* include and flag set from C/C++,
    mirroring automake's F77 rule;
  - a POST_BUILD unversioned symlink, then the drop-in copy.

So the target is **not** a bespoke generator. `ilib_gen_Make_unix` should emit a
declarative `CMakeLists.txt` of the same shape a Scilab module has:

```cmake
cmake_minimum_required(VERSION 3.20)
project(mytoolbox C CXX Fortran)
find_package(Scilab REQUIRED)          # <-- does not exist yet; see below
scilab_gateway(mylib
  SOURCES  foo.c bar.cpp baz.f
  LIBS     …
)
```

`scilab_gateway()` is a thin, runtime-facing sibling of `scilab_module()`,
sharing the same flag/rpath/install_name policy.

### The gap this exposes (and it is the real work)

**Scilab exports no CMake package today.** `cmake/ScilabInstall.cmake` has
`install()` rules but no `install(EXPORT …)` and no generated `ScilabConfig.cmake`,
so there is nothing a third party could `find_package(Scilab)` against. And
`_scilab_module_apply()` resolves includes against `${SCILAB_SOURCE_DIR}` —
**build-tree paths**, which do not exist on a user's machine.

Reusing the exact mechanism therefore means promoting it from build-time-private
to installable:

1. add a `ScilabConfig.cmake` + version file, installed with the app;
2. factor the flag/rpath/install_name policy so it can resolve against either
   `SCILAB_SOURCE_DIR` (in-tree build) or the installed prefix (user machine),
   from **one** source — not a copy that drifts;
3. expose `scilab_gateway()` from that package.

This is more work than a standalone generator would have been, and it is the
right kind of work: it means a gateway built on a user's machine is compiled and
linked by the same rules that build Scilab's own dylibs, instead of a parallel
set that silently diverges.

**One caveat found while surveying:** there is **no `codesign` anywhere in
`cmake/`**. The macOS re-signing that gateways need (§6) is handled per-module
by custom commands (xcos does it for scicos/scicos_blocks), not centrally. If
`scilab_gateway()` is to be safe by default, that step has to become part of the
shared policy rather than something each caller remembers.

### Mapping the existing generator inputs

The mapping is nearly mechanical:

| today | CMake |
|---|---|
| `libname` | `add_library(<libname> SHARED …)` + `set_target_properties(PREFIX "")` |
| `files` | sources; extension drives `enable_language(C/CXX/Fortran)` |
| `libs` | `target_link_libraries` |
| `cflags` / `fflags` | `target_compile_options` per language |
| `ldflags` | `target_link_options` (preserving the #4787 exception) |
| `cc` | `CMAKE_C_COMPILER` / `CMAKE_CXX_COMPILER` override |
| `scicompile.sh` rewriting SOURCES/OBJECTS | nothing — the list is generated, not patched |
| `touch Makefile` hack | nothing — no configure/Makefile timestamp rule exists |

Two whole moving parts (`scicompile.sh`, the timestamp hack) disappear rather
than being ported. That is the strongest argument that this is the right shape.

---

## 3. CMake availability — SETTLED 2026-08-01: require it, do not bundle

**Decision: CMake is a documented prerequisite, like the compiler.** Scilab
bundles no build tool today (verified: 0 `cmake`, `make`, `clang` or `gfortran`
executables anywhere in the shipped app — only the autotools *scripts*), and
this migration does not change that.

### Who is actually affected

Measured, because the answer decides the question:

- `tbxInstall` git-clones **source** and builds when the clone ships no
  committed `loader.sce`. That is **50 of 54** installed toolboxes — so building
  on the user's machine is the normal path, not an edge case. (`helptbx` proves
  it: its `loader.sce` exists but is untracked — it was built at install time.)
- **But almost none of that touches a compiler.** For a pure-macro toolbox
  `builder.sce` is just `genlib`. Only toolboxes with native gateways reach
  `ilib_build` and this skeleton: **3** — nan, scicv, scimax.

So the exposure is: an end user — not a developer — installing one of ~3 native
toolboxes. Real, but narrow.

### Why require rather than bundle

Bundling was considered and rejected. It buys determinism (one CMake version for
everyone) and removes a setup step, at a measured **13 MB binary + 23 MB share
data ≈ 36 MB**, i.e. a **net +34 MB** app to delete 1.8 MB of autotools. Against
that:

- it would be the **first executable build tool Scilab ever ships**, breaking the
  pattern that every other build dependency (compiler, make, Fortran runtime) is
  system-provided;
- it becomes an ongoing obligation — security updates, signing and notarising a
  third-party binary inside the bundle, architecture coverage;
- a pinned CMake eventually becomes the *old* one a toolbox author cannot use;
- and it solves a problem only for users who **already** need a full
  C/C++/Fortran toolchain. Someone without Command Line Tools cannot build scicv
  today either. One more named prerequisite is marginal.

### The gap it leaves, and the fix

The one genuine loss is ergonomic: macOS auto-prompts to install Command Line
Tools the first time a compiler is invoked; nothing prompts for CMake. That
deserves **a precise diagnostic, not 36 MB**. `ilib_build` must detect a missing
or too-old CMake and say so plainly —

    CMake not found. Scilab needs it to build toolbox gateways.
    Install it with:  brew install cmake     (requires >= 3.20)

— rather than surfacing a CMake stack trace or a bare non-zero exit. Same shape
as the preflight in `build-macos.sh`. This is a required deliverable of step 4,
not a nicety.

---

## 4. Compatibility contract — non-negotiable

`ilib_build`, `ilib_gen_gateway`, `ilib_compile`, `ilib_for_link`,
`ilib_mex_build`, `ilib_gen_Make` are **documented public API** used by
third-party ATOMS toolboxes we do not control. Signatures, argument semantics,
return values, and the on-disk names of produced libraries must not change.
Only the mechanism behind them changes.

Corollary: the generated `CMakeLists.txt` is an implementation detail, but the
*outputs* (`lib<name>.so`/`.dylib` in the expected directory, loader/cleaner
scripts) are contract.

---

## 5. Affected set and acceptance gates (measured)

Toolboxes in `SciLabProjects` that build native code:

| toolbox | path |
|---|---|
| **nan, scicv, scimax** | call `ilib_build`/`ilib_gen_gateway` — **directly affected** |
| FOSSEE-Optimization-toolbox, sci-ipopt, sciTorch, xlsx | own shell scripts, bypass `ilib_build` — **unaffected; good controls** |

Narrower than feared: most of our ported natives already bypass this path. The
real consumer is the third-party ATOMS author, which is precisely why §4 matters
more than the size of our own set.

**Gates:**

1. A purpose-built minimal gateway matrix — C, C++, Fortran, and mixed — built
   from clean. Fortran is the one most likely to break (see §6).
2. Clean rebuild of **nan, scicv, scimax** from source, then load and run their
   existing smokes.
3. The full **54-toolbox verification sweep** (`tbx-verify-all.sh`), which builds
   any toolbox lacking a `loader.sce` and then exercises a functional smoke — a
   real acceptance test, not a smoke test, for this change.
4. `PARITY OK`, plus the payload delta recorded in `build-size-baseline.md`
   (−1.8 MB skeleton, +36 MB CMake if bundled — a net *increase*, which must be
   stated plainly rather than buried).

---

## 6. Risks and known traps

- **Fortran.** `configure` detects `gfortran` and its runtime; CMake must find
  the same one, and on this machine gfortran comes from Homebrew GCC with the
  `emutls_w` linkage quirk already documented for the FOSSEE port. Highest-risk
  language of the three.
- **macOS code signing.** A rebuilt gateway's ad-hoc linker signature can pass
  `codesign --verify` yet be AMFI-killed at load (SIGKILL, empty stderr). The
  existing rule — re-sign with `codesign --force --sign -` in the builder — must
  survive the migration, or every freshly built gateway dies on load with no
  diagnostic.
- **`install_name` / rpath.** libtool currently handles this. CMake's
  `BUILD_RPATH`/`INSTALL_NAME_DIR` behaviour differs and must be set explicitly,
  or gateways will not resolve `libscilab` at load.
- **The flags pipeline.** `gencompilationflags_unix` has accumulated real
  history, including the #4787 `LDFLAGS` exception. Port it deliberately, with
  the bug reference preserved, rather than "cleaning it up".
- **Caching semantics.** The `Makefile.orig` reuse (§1) is an observable
  behaviour — repeated `ilib_build` calls are fast. CMake's own build-dir
  caching should preserve that, but it must be measured, not assumed.
- **`ilib_verbose()`** output is what toolbox authors debug with; the new path
  should produce comparable diagnostics.

---

## 7. Work breakdown

1. **Characterise** — capture the exact `make` command lines the current path
   produces for the C/C++/Fortran/mixed matrix. This is the oracle everything
   else is compared against.
2. ~~Decide the CMake-availability policy~~ **SETTLED (§3): require it, do not
   bundle.** Nothing to wire into `package-macos.sh`; the size baseline is
   unaffected. What this step now owns is the *diagnostic* — see step 4.
2b. **Export a Scilab CMake package** (§2) — `ScilabConfig.cmake` + version file,
   installed with the app; refactor `_scilab_module_flag_env` /
   `_scilab_module_apply` so the flag, include, rpath and install_name policy
   resolves against either the source tree or the installed prefix from ONE
   definition; expose `scilab_gateway()`. Fold the macOS re-sign into that
   shared policy. **This is the bulk of the work and the prerequisite for
   step 3** — it is what "use the exact mechanism" costs, and it is worth it.
3. **Generator** — emit a declarative `CMakeLists.txt` calling
   `scilab_gateway()`; keep the skeleton in place and switch behind an env flag
   so both paths can be diffed.
4. **Driver** — `ilib_compile` runs cmake; preserve `ilib_verbose` diagnostics,
   re-signing, and rpath/install_name. **Plus the missing-CMake diagnostic (§3)**:
   detect absent/too-old CMake and print the install line, rather than leaking a
   CMake error to someone who just wanted `tbxInstall("scicv")` to work.
5. **Gate** — run §5 in full, both paths, and compare artifacts.
6. **Cut over and deprecate** — CMake becomes the default and does the work.
   The skeleton STAYS, reachable only by explicit opt-out, and every use of it
   prints the deprecation warning naming its removal release (§10). The
   development-time A/B flag from step 3 is deleted here; the opt-out is a
   different, deliberately user-facing thing.
7. **Delete, in 2027.1** — remove the 18-file skeleton, the opt-out,
   `scicompile.sh`, `compilerDetection.sh`, and the timestamp hack. This is a
   scheduled task, not an aspiration (§10).
8. **Windows (phase 2)** — fold `Makefile.incl.mak` + `TEMPLATE_MAKEFILE.VC`
   into the same generator. This is A's payoff and is deliberately *not*
   attempted in pass 1; it cannot be validated on this machine.

---

## 8. Out of scope

- The Windows nmake path in pass 1 (phase 2 above).
- Changing any public `ilib_*` signature (§4).
- `ilib_build_jar` — Java, unrelated to this skeleton.

## 9. Open questions

1. ~~Bundle CMake, or require it?~~ **Answered 2026-08-01: require it** (§3).
2. ~~Is a net +34 MB app acceptable?~~ **Moot** — nothing is bundled.
3. ~~Cut clean, or keep a fallback?~~ **Answered 2026-08-01: DEPRECATE, remove in
   2027.1** — see §10. No open questions remain.


---

## 10. Deprecation policy for the autotools path (decided 2026-08-01)

The skeleton is **kept, not cut**, when CMake lands — but only as a compatibility
path, and on a clock.

**What ships:**

- **CMake is the default and does the work.** `ilib_build` and friends emit
  CMake. Every toolbox goes through it unless someone explicitly opts out.
- The autotools skeleton remains reachable **only by deliberate opt-out** (an
  env var or an explicit argument — not a fallback that engages silently on
  error, which would hide CMake bugs behind a path nobody is testing).
- **Every use of the opt-out prints a deprecation warning naming the release it
  is removed in.** Not "deprecated" — a release. That release is **2027.1**
  (confirmed 2026-08-01), and the warning text says so literally:

      WARNING: the autotools build path for toolbox gateways is DEPRECATED and
      will be REMOVED in Scilab 2027.1. Your gateway built, but rebuild it with
      the default (CMake) path before upgrading.

**Why not cut clean.** `ilib_build` is documented public API with third-party
ATOMS consumers we do not control. Ours are safe (nan, scicv and scimax all use
the public API), but a third-party toolbox that patched `Makefile.orig`, called
`scicompile.sh` directly or parsed the generated Makefile would have had no
recourse in that release. One release of overlap costs little and removes that
cliff.

**Why the date is not optional.** A deprecation without a named removal release
is how a "temporary" path survives for a decade — which is *precisely* this
skeleton's own history. It outlived autotools everywhere else in the project
because nobody ever set a date for it. Setting one is what distinguishes this
from repeating that.

Accordingly the removal is step 7 in the work breakdown, targeted at **2027.1** —
a scheduled task with the release named in the warning text and in this
document, not an aspiration recorded in a comment. The current version is
2027.0.0, so the deprecated path lives for exactly one minor release.

**Cost of keeping it for one release, stated plainly:** the 1.8 MB stays, both
code paths must keep working and being tested, and the "delete the last
autotools" win is deferred rather than banked. That is the price of not
stranding a third-party author, and it is worth paying once — not twice.
