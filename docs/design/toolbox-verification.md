# Toolbox Verification Baseline (2027.0.0-macos-dev.1)

This document captures the toolbox-manager verification baseline for macOS arm64 Scilab 2027. **Final state: 2026-07-12, harness v1.1 -- 50/50 cataloged toolboxes verified.** The last holdout, `scimax`, was resolved the same day the wave-2 sweep ran: the user chose to fix its Maxima IPC handshake rather than delist it, and the repaired toolbox now passes the same smoke that had gated it (see its per-toolbox note). The harness establishes that a toolbox is ready for adoption when:

1. **Build succeeds** (if the toolbox has native gates; many are macro-only)
2. **arm64 arch gate passes** (no stale non-arm64 `.so` / `.dylib` artifacts)
3. **Loader executes cleanly** (`loader.sce` runs without error)
4. **Registers ≥1 macro library** OR **smoke evidence proves functionality** — gateway-only toolboxes (those that register functions via `addinter` and produce zero macro-library delta) verify **exclusively via a smoke file** (`scilab/tbx-smoke/<name>.sce`) that must run clean and set `smoke_ok = %t`

Smoke-author guidance, for the next toolbox added to the catalog:
- Prefer calling native gateway entry points directly over wrapper macros that may silently fall back on native failure (see the `nan` and `sciTorch` notes below -- both cost a wave-2 rewrite for exactly this reason).
- The `.sce` parser hard-errors on `'`-delimited strings containing a literal `"` ("Heterogeneous string detected") -- use `""`-doubling inside `"`-delimited strings instead.
- Clean up any temp files the smoke creates; `unwind_protect` is nice-to-have, success-path cleanup is the current precedent.

**Wave 2 closed the evidence gap:** every one of the 50 catalog entries now carries a smoke fixture in `scilab/tbx-smoke/`, including the 17 that previously passed on `delta>=1` alone. `(verified)` -- the tag `tbxManager()`'s GUI shows per row, driven by `cfg.verified` -- uniformly means a representative call actually executed on this build: for native/gateway-only toolboxes, specifically a call that crosses into the native gateway, not just a clean macro-library load.

**Known limitation (historical):** `delta>=1` alone proves a loader **registered** macro libraries, not that the toolbox **works** at runtime -- see `scimax` below, where (until its 2026-07-12 fix) a clean `delta=2` load coexisted with a `maxinit()` handshake that never returned. This was the whole-catalog gap wave 2 closed: every toolbox now carries a smoke, so `delta>=1` alone is no longer load-bearing evidence anywhere in the matrix. A subtler trap survives smoke coverage itself, and wave 2 hit it twice: a smoke built on a *wrapper macro* rather than a native entry point can still pass while the native path is dead, because the wrapper's own `try`/`catch` silently falls back to a pure-Scilab implementation on failure -- `nan`'s `sumskipnan.sci` falls back to a macro sum when its native `sumskipnan_mex` fails; `sciTorch`'s `etc/sciTorch.start` swallowed a failing gateway `link()` and reported a clean load with zero native functions actually registered. Both smokes were rewritten to call (or otherwise verify) the native path directly -- see their per-toolbox notes below. The rule going forward: a smoke for a native/gateway-backed toolbox must call the gateway entry point directly, or otherwise positively verify the native path is live -- not just observe that some top-level macro returned without error.

To re-run the full sweep (50 toolboxes, budget ~1 hour):

```bash
cd scilab && ./tbx-verify-all.sh
```

Environment: `JAVA_HOME` is auto-resolved by the script; per-toolbox timeout is `TBX_TIMEOUT` (default 300 seconds).

## Matrix

| Toolbox | Status | Detail |
|---------|--------|--------|
| scimax | PASS | delta=2; smoke=OK -- see note (Maxima IPC handshake fixed 2026-07-12, toolbox commit 32d984290e5; row from the post-fix harness run, reproduced by fixer twice + reviewer once) |
| accsum | PASS | delta=1; smoke=OK |
| anova | PASS | delta=1; smoke=OK |
| apifun | PASS | delta=1; smoke=OK |
| arfit | PASS | delta=1; smoke=OK |
| casci | PASS | delta=1; smoke=OK |
| cgal | PASS | delta=1; smoke=OK |
| cma-es | PASS | delta=1; smoke=OK |
| condnb | PASS | delta=1; smoke=OK |
| conint | PASS | delta=1; smoke=OK |
| csv-readwrite | PASS | delta=1; smoke=OK |
| dataint | PASS | delta=1; smoke=OK |
| dbldbl | PASS | delta=1; smoke=OK |
| distfun | PASS | delta=2; smoke=OK |
| financial | PASS | delta=1; smoke=OK |
| fmincont | PASS | delta=3; smoke=OK |
| FOSSEE-Optimization-toolbox | PASS | delta=1; smoke=OK |
| grocer | PASS | delta=36; smoke=OK |
| guibuilder | PASS | delta=1; smoke=OK |
| hypt | PASS | delta=1; smoke=OK |
| intprbs | PASS | delta=1; smoke=OK |
| json | PASS | delta=1; smoke=OK |
| krisp | PASS | delta=3; smoke=OK |
| libsvm | PASS | delta=1; smoke=OK |
| lowdisc | PASS | delta=1; smoke=OK |
| lsf_toolbox | PASS | delta=1; smoke=OK |
| makematrix | PASS | delta=1; smoke=OK |
| montesci | PASS | delta=1; smoke=OK |
| nan | PASS | delta=1; smoke=OK |
| neuralnetwork | PASS | delta=5; smoke=OK |
| nisp | PASS | delta=3; smoke=OK |
| number | PASS | delta=1; smoke=OK |
| ortpol | PASS | delta=5; smoke=OK |
| parquet | PASS | delta=1; smoke=OK |
| PIMS | PASS | delta=0; smoke=OK |
| pso-toolbox | PASS | delta=1; smoke=OK |
| quapro | PASS | delta=1; smoke=OK |
| regtools | PASS | delta=1; smoke=OK |
| scicv | PASS | delta=1; smoke=OK |
| sciDatabase | PASS | delta=1; smoke=OK |
| scidoe | PASS | delta=1; smoke=OK |
| sci_gsl | PASS | delta=1; smoke=OK |
| sci-ipopt | PASS | delta=0; smoke=OK |
| sciQuantLib | PASS | delta=0; smoke=OK |
| sciSymPy | PASS | delta=1; smoke=OK |
| sciTorch | PASS | delta=1; smoke=OK |
| sndfile-toolbox | PASS | delta=1; smoke=OK |
| specfun | PASS | delta=1; smoke=OK |
| stixbox | PASS | delta=1; smoke=OK |
| xlsx | PASS | delta=1; smoke=OK |

**Summary:** 50 PASS / 0 FAIL / 0 TIMEOUT / 0 CRASH of 50 total

## Per-toolbox notes

### accsum

**Error:** build failed

**Analysis & fix lane:** Native C gateway was never ported to macOS arm64. The build step fails early, before loader execution. **Planned:** playbook port of the gateway module to arm64.

**Resolved (Task 10):** two build-time defects, both class-consistent with prior ports. (1) `src/c/builder_c.sce` had no Darwin branch: its Unix/else branch hardcodes `-mfpmath=sse -msse2` to force strict IEEE double semantics instead of x87's 80-bit extended-precision registers -- an x86-only concern that clang on Apple Silicon rejects outright (`unsupported option '-msse2' for target 'arm64-apple-darwin25.5.0'`); arm64's FPU has no equivalent extended-precision register class, so the flags simply don't apply and the new Darwin branch omits them. (2) all three gateway files (`sci_gateway/c/sci_accsum_f{dcs,scs,compsum}.c`) declared their entry point behind `#if SCI_VERSION_MAJOR < 6` / `#else`, but none of them include `core/includes/version.h` (only `machine.h`/`Scierror.h`/`api_scilab.h`/`localization.h`/`accsum.h`), so `SCI_VERSION_MAJOR` is invisible to the preprocessor there and the undefined macro evaluates to 0 in the `#if`, always selecting the legacy 1-arg signature (`char *fname`, no `pvApiCtx`); `api_stack_common.h`'s `Rhs`/`Lhs`/`LhsVar`/`CheckRhs`/`CheckLhs` macros unconditionally expand to reference a function-scope `pvApiCtx` identifier regardless of that gate, so the legacy signature failed with `use of undeclared identifier 'pvApiCtx'` (13 occurrences per file). Dropped the version conditional in all three files and kept only the modern `(char *fname, void *pvApiCtx)` signature -- this build always targets Scilab 6+/2027. Both produced dylibs are arm64 Mach-O (`file` confirmed) and link only against system libs plus real, resolvable Homebrew paths (`libgfortran.5.dylib`, `libquadmath.0.dylib` under `/opt/homebrew/opt/gcc/lib/gcc/current/`, matching `cfg.libpath`) -- no `@loader_path` rewriting needed; both are ad hoc-signed by the linker automatically (`codesign -f -s -` re-applied anyway per the playbook, no-op). Gateway functions are `accsum_fdcs`/`accsum_fscs`/`accsum_fcompsum` (confirmed from `sci_gateway/c/builder_gateway_c.sce`'s own `namelist` table), registered via `addinter` and callable immediately after `loader.sce` (`exists()` on all three returns 1). Verified with `tbx-smoke/accsum.sce`: exact small-vector cases lifted from the toolbox's own `tests/unit_tests/{fdcs,fscs,fcompsum}.tst`, plus its `accsum_wilkinson(10)` ill-conditioned-series discriminator (Higham SNAA Exercise 4.2, reproduced inline from `macros/accsum_wilkinson.sci`'s documented formula since the macro layer isn't reliably reachable through this harness's nested-exec shape -- `etc/accsum.start`'s `loadaccsumlib()` sometimes fails to propagate `lib()`'s registration to `librarieslist()` depending on exec-nesting depth in ad hoc manual probing, though the official harness run below shows `delta=1`, i.e. the macro library did register that run; the smoke avoids the dependency either way since gateway registration is what this task actually ports and is unaffected by it). The reference value confirms real compensated-vs-naive discrimination: `accsum_fdcs`/`fscs`/`fcompsum` all reproduce the toolbox's documented `s=1023.9999999999612, e=-3.7858605139717838e-14` (matching `assert_checkalmostequal(s,1024,1.e-12)` / `assert_checkalmostequal(e,-3.786e-14,[],1.e-10)`), while a plain `sum(x)` on the identical vector rounds to exactly `1024` -- silently losing the ~3.9e-11 correction that the compensated algorithms track explicitly.

### arfit

**Error:** none (passes the generic load bar cleanly: `delta=1; smoke=none` at baseline).

**Analysis & fix lane:** arfit loads cleanly and its top-level `arfit()` fitting function never hangs on its own — but the toolbox's other core functions (`arsim()`, the AR-process simulator; `arres()`, the residual/model-adequacy check), both part of the documented fit-and-diagnose workflow (`demos/ardem.sce`), call an undefined MATLAB-compatibility shim `mtlb_repmat()` (5 call sites: `macros/arsim.sci:82,104,134`, `macros/arres.sci:81,91`). Scilab's own m2sci converter (`modules/m2sci/macros/sci_files/sci_repmat.sci`) documents `mtlb_repmat()` as its runtime-emulation fallback for `repmat()` calls it can't statically type at MATLAB→Scilab conversion time, but core never shipped that runtime macro (unlike its `mtlb_sum`/`mtlb_triu`/`mtlb_mean` siblings in `modules/m2sci/macros/compat_functions/`). The resulting undefined-variable error is uncaught by the toolbox; raised from a bare, non-harness invocation (an interactive session, or any script without its own try/catch) it leaves the Scilab process blocked reading from console instead of exiting — externally observed as a hang, matching the port-time note ("core fns HANG under 2027, mtlb_-heavy"). **Planned:** add a local `mtlb_repmat` compat macro to the toolbox, forwarding to Scilab's native `repmat()` (argument-compatible at every call site).

**Resolved (Task 8):** Added `macros/mtlb_repmat.sci` — a one-function shim (`B = mtlb_repmat(A, varargin); B = repmat(A, varargin(:));`) — and rebuilt the toolbox's macro library (`tbx_builder_macros`) so `mtlb_repmat.bin` is registered in `macros/lib` alongside the toolbox's other macros. Verified with a full-chain probe (`arsim` → `arfit` → `arres` → `arconf` → `armode`, exercising all 5 `mtlb_repmat` call sites) that now runs clean end to end and recovers known VAR(1) coefficients within tolerance. Note on the harness's own catch behavior: `tbxVerify()`'s smoke step wraps execution in `execstr(..., "errcatch")` (`tbxVerify.sci:40`), so replaying the pre-fix defect *through the official harness* actually produces a fast, clean `FAIL: smoke error: Undefined variable: mtlb_repmat` rather than a literal `TIMEOUT` — confirmed by a negative-control re-run with the fix backed out. The hang itself was independently reproduced and confirmed outside the errcatch-protected harness path (a bare, uncaught top-level script call into `arsim()`, run directly under `scilab-adv-cli -f`), which is the invocation shape the port-time note's "hang" almost certainly describes; both the harness's catchable FAIL and the raw hang share the identical root cause. Added `tbx-smoke/arfit.sce`: a known-coefficient VAR(1) simulate→fit→residual-check round trip (`arsim`+`arfit`+`arres`) that exercises every `mtlb_repmat` call site — the brief's originally-proposed bare `[w,A,C]=arfit(v,1,2)` smoke was confirmed to PASS even on the broken toolbox (`arfit()` itself never calls `mtlb_repmat`), so it would not have caught this defect.

### csv-readwrite

**Error:** loader error 10000: startModule: error on line #23: "exec: Cannot open file /Users/josemoya/Projects/SciLabProjects/csv-readwrite/sci_gateway/loader_gateway.sce."

**Analysis & fix lane:** The gateway port was left ~80% complete. The loader references `loader_gateway.sce` which is missing or incomplete. **Planned:** finish the gateway port, or delist csv-readwrite as core-redundant (core Scilab ships `csvRead` / `csvWrite` natively).

**Resolved (Task 7):** the real blocker wasn't the previously-recorded file-I/O API churn alone but a chain of five further issues never reached before: a mismatched-quote parse bug in `builder.sce` (line 37) that failed before any builder macro ran, an obsolete `getversion()`-based version gate that misfires under the year-based 2027 numbering, missing standard-library includes (`stddef.h`/`stdio.h`/`ctype.h`/`string.h`) that are hard errors under modern clang, a wholly-fictional `IsValidUTF8()` call in `latintoutf.c` (never a real API, replaced with a small local implementation), and two gateway files (`sci_csv_default.c`'s RHS-count helpers, `gw_csv_helpers.c`) whose bodies referenced a bare `pvApiCtx` that was never in scope; the mopen/mgetl/mclose narrow-to-wide rewrite in `csv_read.c` followed core's own modern `csvRead.c` as a template, and `sci_csv_write.c`'s `CheckLhs(1,1)` was loosened to `CheckLhs(0,1)` to match its void-return success path and the toolbox's own shipped test convention (bare, uncaptured `csv_write(x,filename)` call). Function names are `csv_read`/`csv_write`/`csv_textscan`/`csv_stringtodouble`/`csv_default`/`csv_isnum` (confirmed from `sci_gateway/c/builder_gateway_c.sce`'s own table) -- no collision with core's `csvRead`/`csvWrite`/`csvTextScan` (different case/underscore convention). Verified with a real round-trip smoke (`tbx-smoke/csv-readwrite.sce`): a 2x3 matrix written and read back via the toolbox's own `csv_write`/`csv_read` compares exactly equal.

### distfun

Smoke design note: `delta=2` is two separate `lib()` calls in `etc/distfun.start` (one for `macros/`, one for `macros/internals/`) -- both register the same toolbox, so the doubled delta is benign, not evidence of a second undiscovered library.

### guibuilder

guibuilder ships 0 tests and 0 demos and is inherently a GUI-construction toolbox; its smoke uses `guicheckprops` -- the one macro among its 50 that runs GUI-free (a property-value parser/sanitizer) -- exercised via a color/position shape check. Disclosed pre-existing toolbox bug found while building the smoke: `guicheckprops`'s color-range validation (`val_set<=1 | val_set>=0`) is a tautology (true for every real value), so only the smoke's shape check actually discriminates a broken call from a working one.

### json

`JSONWrite` is an empty stub in this toolbox -- it has no body -- so json is a JSON *parser* only here; the smoke exercises `JSONParse` (round-tripping a string+number+vector struct) and makes no `JSONWrite`/round-trip claim.

### krisp

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libkrisp_c.so, /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libskeleton_c.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/c/`. **Planned:** remove stale artifacts, rebuild for arm64, and fix `corr_*` registration.

**Resolved (Task 6):** Removed the two tracked stale non-arm64 build artifacts (`libkrisp_c.so`, a 32-bit x86 ELF; `libskeleton_c.so`, a dead unused legacy gateway-registration template never referenced by the actual builder) from `sci_gateway/c/`, clearing the arch gate. Rebuilt the `corr_*` native gateway from source in a clean room (`sci_gateway/c/builder_gateway_c.sce` with the standard `CPATH`/`LIBRARY_PATH` env recipe) and confirmed `c_corr_D`/`c_corr_X`/`c_corr_vector` register and compute correct values (`corr(0)=1`, closed-form gaussian kernel match) — the "natives build but don't register" issue logged in FINANCE-TOOLBOX-PORTING.md at port time no longer reproduces; the real blocker was purely the arch-gate artifacts. Added `tbx-smoke/krisp.sce` (RLHS bounds/shape check + a real `c_corr_D` call verified against the gateway's own closed-form kernel).

### lowdisc

The smoke loads lowdisc's `apifun` and `number` sibling toolboxes first, per lowdisc's own README-documented dependency, before exercising lowdisc itself; that loading happens before lowdisc's own `delta` is measured, so it doesn't inflate lowdisc's pass criterion.

### nan

`nan_mean()`'s own call chain reaches `sumskipnan_mex` through `macros/sumskipnan.sci`, which wraps the native call in a `try`/`catch` that silently falls back to a pure-macro sum on any native failure -- so a smoke built on `nan_mean` alone would still pass with a broken or missing gateway. The smoke instead calls `sumskipnan_mex` directly, bypassing that fallback, so a broken gateway now produces an honest smoke FAIL instead of a silently-degraded PASS.

### parquet

**Error:** loader error 10000: exec: error on line #13: "link: The shared archive was not loaded: dlopen(/Users/josemoya/Projects/SciLabProjects/parquet/sci_gateway/cpp//../../src/cpp/libarrow.dylib, 0x000A): Library not loaded: /opt/homebrew/opt/apache-arrow/lib/libarrow.2400.dylib"

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. Homebrew's apache-arrow library has been bumped past the `libarrow.2400.dylib` ABI that the cached gateway was built against. **Planned:** rebuild the gateway against the current apache-arrow version.

**Resolved (Task 4b):** re-ran `build_macos.sce` against the now-current Homebrew apache-arrow (v25.0.0, `libarrow.2500.dylib`); the Darwin builder branch links generically (`-larrow -lparquet` against whatever `/opt/homebrew/opt/apache-arrow` resolves to), so no source change was needed, only a rebuild of the gitignored native artifacts. Verified with a real round-trip smoke (`tbx-smoke/parquet.sce`): a mixed-type table (double/int32/string/bool) written to and read back from a `.parquet` file compares equal. Also surfaced (out of scope for this task, flagged for awareness): Scilab core's own `modules/spreadsheet` links the same old `libparquet.2400.dylib` and logs a non-fatal dlopen error at shutdown on every session — pre-existing, unrelated to the toolbox fix, not remediated here. (Since resolved -- see Follow-ups item 3.)

### PIMS

**Error:** loader registered no new library

**Analysis & fix lane:** Gateway-only toolbox (addinter natives, zero macro-library delta). Under harness v1.1, gateway-only toolboxes must provide a smoke file to verify; without one, the delta==0 result is correctly reported as FAIL. **Planned:** author a smoke file at `scilab/tbx-smoke/PIMS.sce`.

**Resolved (Task 4b):** loader already worked cleanly (no toolbox source changes needed). Authored `tbx-smoke/PIMS.sce`, which evaluates a trivial Python expression through the bridge (`pyEvalStr("print(1+1)", %t)`, matching the toolbox's own `tests/unit_tests/pyEvalStr.tst` pattern) and checks the returned string. Passes cleanly through the real harness (`gtimeout`-wrapped `-f` script). Note: an ad hoc manual probe using `scilab-adv-cli -e '...'` with inherited stdin appeared to hang indefinitely at high CPU; a thread sample showed Scilab's console-reader thread spinning in its interactive-prompt read loop (`scilabReadAndStore`/`getKey`), not inside Python -- an artifact of that invocation style (same class of issue as the pre-existing `scimax` TIMEOUT), not a PIMS or Python-env defect. The actual verification harness is unaffected since every run is `gtimeout`-bounded.

### pso-toolbox

**Error:** loader error 10000: add_help_chapter: error on line #71: "add_help_chapter: Wrong value for input argument #2: An existing directory expected."

**Analysis & fix lane:** The loader calls `add_help_chapter()` but the help directory is missing in this build. The help registration is unconditional and aborts when the directory doesn't exist. **Planned:** guard the help registration call in the loader to skip gracefully if the directory is absent.

**Resolved (Task 4b):** `etc/PSO.start` used the ATOMS-era idiom `if ( isdir(path) <> [] )`, which is always `%t` under Scilab 6 (`x <> []` is defined as `%t`), so `add_help_chapter` ran even though `jar/` doesn't exist in this tree. Changed to `if isdir(path_addchapter) then` (same fix already applied to sci_gsl's `etc/sci_gsl.start`). Added `tbx-smoke/pso-toolbox.sce`: loads the `apifun` dependency, then runs `PSO_inertial` on a 2D sphere function (the toolbox's own `tests/unit_tests/PSO_inertial.tst` case) and checks the optimum lands within bounds and near the known global minimum.

### regtools

**Error:** loader error 10000: exec: error on line #29: "Failed to install guimaker from atoms."

**Analysis & fix lane:** The loader auto-installs the `guimaker` toolbox from ATOMS as a dependency, but `guimaker` fails to build on 2027/arm64. This blocks regtools even though the batch functions don't inherently require guimaker. **Planned:** decouple guimaker; ensure batch functions work standalone.

**Resolved (Task 5):** `etc/regtools.start` no longer auto-installs guimaker from ATOMS at load time (it just checks `isdef("guimaker")` and warns), and `linregr`/`nlinregr` now guard their own interactive-GUI entry points to raise a clear error at call time instead of a load-time failure, leaving `ff2n`/`fullfact` and command-line-mode `linregr`/`nlinregr` unaffected.

### scidoe

The smoke calls `scidoe_pdist` -- the toolbox's only native entry point (63 of its macros are pure Scilab; this is the one that crosses into C) -- making it the discriminating call for this toolbox's native gateway rather than an arbitrary pick.

### sci_gsl

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libMC_toolbox.so, /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libsci_gsl.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/cpp/`. **Planned:** clean stale artifacts and rebuild for arm64.

**Resolved (Task 4b):** `libMC_toolbox.so` and `libsci_gsl.so` were leftover Linux ELF binaries (`file` confirms `ELF 64-bit LSB shared object, x86-64` -- not even Mach-O) tracked in git since the initial port commit, evidently bundled in the original off-forge source tarball and never touched by the Darwin build branch, which only ever produces `libsci_gsl.dylib` (the correct arm64 Mach-O, already present and gitignored). `git rm`'d both stale files; no rebuild needed. The existing smoke (`tbx-smoke/sci_gsl.sce`, `phyconst(1)==299792458`) now passes.

### sci-ipopt

**Error:** loader registered no new library

**Analysis & fix lane:** Gateway-only toolbox (addinter natives, zero macro-library delta), same as PIMS. Under harness v1.1, requires a smoke file. **Planned:** author a smoke file at `scilab/tbx-smoke/sci-ipopt.sce` that actually **solves** (historical failure mode was at solve time, not load).

**Resolved (Task 4b):** the historical solve-time MPI abort fix (`macos-fix-arpack-mpi.sh`, redirecting arpack's OpenMPI deps to IPOPT's sequential `libmpiseq` stub) is still intact on this machine -- no rebuild needed. Authored `tbx-smoke/sci-ipopt.sce`: lifts the toolbox's own constrained-Rosenbrock regression test (`tests/unit_tests/ipopt_rosenbrock.tst`, exact Hessian, nonlinear inequality constraint) and asserts the solution against the test's own documented reference (`[0.90723379674169202, 0.82275515858492032]`). Solves cleanly; the MPI blocker did not resurface.

### scimax

**RESOLVED (2026-07-12, user decision: fix over delist):** `scimax PASS delta=2; smoke=OK` — toolbox commit `32d984290e5` on the jlmoya mirrors, reproduced twice consecutively by the fixer and once independently by its reviewer, with a console-integrity check each time (the prior failure mode — see below). The real root cause sat one level deeper than the buffering story the diagnosis below tells: the macOS port had dropped Maxima's `-p <lispfile>` mechanism, which is what installs the `macsyma-top-level`/prompt redefinitions — so the `<EO>` terminator the C reader waits for was **never emitted at all**, and folding the lisp load into the framed init command couldn't work either (`$_` isn't `defmspec`'d until the file loads). The fix is pipe-only, no pty anywhere (the openpty regression class is structurally unreachable): `src/lisp/loader.lisp` now redefines a flushing `main-prompt` (`format` + `finish-output`) at load time, and `src/c/maxinit.c` restores a two-step handshake (send the `load(...)$`, drain until `<EO>`, then send the framed init). Residuals, disclosed and accepted: a present-but-internally-broken `loader.lisp` would still hang step 1 until the harness `gtimeout` (the pre-flight gate covers only the missing-file case); each `maxinit`/`maxkill` cycle leaves one zombie child (no `waitpid` anywhere — pre-existing); matrix/list/set results raise a clean documented error (pre-existing). The diagnostic history below is kept as written — it is why the smoke exists and how the defect was cornered.

**Historical — Error at wave-2 close:** TIMEOUT (300s); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-scimax-D3uSg9

**Analysis & fix lane:** The builder errors into an interactive REPL prompt loop, which the harness cannot exit; the 300-second timeout is exceeded. **Planned:** run the builder with `mode(3)` to locate the failing line; also requires Homebrew maxima at runtime.

**Status after Task 12 (partial -- build+load ported, runtime blocked, DONE_WITH_CONCERNS):** The builder/loader issues above are fully fixed. This toolbox's `src/c` was written against the pre-2011 raw flat-stack Scilab API (`stack-c.h`/`GetRhsVar`/`CreateVar`), fully removed from core in 2015 with no compat shim; the marshaling layer was rewritten from scratch against modern `api_scilab` position-based accessors. The native gateway now builds, links, and loads cleanly via `addinter()`, and the `newfun()`-registered operator overloads (`x^2` etc.) resolve correctly. WIP commit `04977b5` (`macOS/2027: partial port (WIP) — build+load fixed, runtime blocked on subprocess fork`) is pushed to both jlmoya mirrors (GitLab + GitHub); no scilab-repo changes were committed for this work, per the task's time-box protocol.

**Where the TIMEOUT actually fires, precisely:** build and load are both clean -- `loader.sce` registers 2 macro libraries (`delta=2`), which alone satisfies the harness's `delta>=1 OR smoke OK` pass criterion. Read naively (as the whole-branch review did), that reads as "the toolbox times out" at some unspecified point. It doesn't: the TIMEOUT fires **inside `tbx-smoke/scimax.sce`**, at that smoke's `maxinit()` call (its first executable line), never during the build or the loader. Maxima 5.49 (SBCL-hosted, via Homebrew) fully buffers its own stdout once it isn't attached to a tty, so `maxinit()`'s `<BO>E...` handshake response sits unread in Maxima's own process memory and the harness's bounded wait never returns -- root-caused with a standalone `fork()`+`pipe()` harness outside Scilab entirely (one isolated trial round-tripped after ~45s once some unrelated internal event, e.g. GC, forced a flush; another identical-looking trial never resolved inside 280s). Not a deadlock, but neither a bounded delay. The textbook fix (`openpty()` instead of `pipe()`, keeping the child's `isatty()` true) was tried and reverted: it reliably broke the *parent* Scilab session's own console instead of fixing the child's buffering. Estimated 2-4h of further fork/IPC iteration to close (a `select()`/`poll()`-based non-blocking read loop, or an SBCL/Lisp flag forcing unbuffered output, are the next things to try).

**The smoke is committed to this repo deliberately, precisely because of the previous paragraph:** without it, `delta=2` alone satisfies v1.1's `delta>=1 OR smoke OK` pass criterion and scimax would have false-PASSed on every clean checkout throughout the months the CAS handshake was broken. While the defect was live this was confirmed both ways: with the smoke present, `TBX_TIMEOUT=90 ./tbx-verify-all.sh scimax` returned `TIMEOUT` (reproduced at least three times); with the smoke temporarily moved aside -- exactly the state of a fresh clone before the smoke landed -- the identical checkout returned `PASS` instead. Since the 2026-07-12 IPC fix the same smoke is what proves the repair: it passes, and it remains the tripwire that will catch any future Maxima/SBCL regression. Do not delete it, and never weaken its check to keep it green.

**The decision landed (2026-07-12): fix.** The supersession argument recorded here for the record — scimax is the only toolbox in the catalog that forks a live external CAS subprocess at runtime (an ongoing maintenance burden tied to whatever Maxima/SBCL ships next), while **sciSymPy** covers symbolic-CAS use cases through PIMS' in-process embedded interpreter with genuine smoke evidence (`diff(x**2, x)` → `2*x`; see `### sciSymPy`). The user weighed that and chose to fix scimax anyway; both CAS routes are now verified side by side. The maintenance-burden asymmetry stands as written and is worth remembering at the next Maxima/SBCL brew bump — the smoke will say immediately whether the handshake survived it.

### sciQuantLib

**Error:** CRASH (rc=133 = SIGTRAP); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-sciQuantLib-wV7nAM

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. The process dies with a trap signal during load, producing zero console output. This is a dylib-level issue. **Planned:** retrieve the crash report from `~/Library/Logs/DiagnosticMessages/` and run `otool -L` on the gateway dylibs to diagnose link failures or symbol mismatches.

**Resolved (Task 4b):** root cause was NOT a dylib/codesign/deployment-target issue (the `otool -l` LC_BUILD_VERSION precedent didn't apply here). The crash report's faulting thread showed `sciprint`->`scivprint`->`__vsprintf_chk`->`__chk_fail_overflow` ("detected buffer overflow") called from Scilab core's `AddInterfaceToScilab`/`scilabLink` (`modules/dynamic_link/src/cpp/dynamic_link.cpp`) during `addinter()`. Root cause traced to `SciLabProjects/sciQuantLib/loader.sce` (the toolbox-root entry point the harness execs) being a **stale, untracked leftover** from an early SWIG "baseline" (Adder/twice) proof-of-concept spike: it referenced a `libbaseline.dylib` that was never built or committed anywhere in the tree. Its `dlopen()` failure produced a dyld "tried: ..." error message (lists every fallback search path -- confirmed independently to run into multiple KB via the analogous `libparquet.2400.dylib` message logged by `modules/spreadsheet/etc/spreadsheet.quit` on every session) that overflowed `scivprint`'s fixed 4096-byte static buffer (`modules/output_stream/src/c/sciprint.c` uses unbounded `vsprintf` on POSIX, unlike the bounded `vsnprintf` on the Windows branch) -- SIGTRAP, zero output, because the crash happened while *forming* the first diagnostic message. The real, working toolbox has lived all along in the `quantlib-swig` git submodule (`quantlib-swig/Scilab/toolbox/`), confirmed to load cleanly (61 registrations) when exec'd directly. Fixed by replacing the stale root `loader.sce` with a small delegator to the submodule's real loader (force-added past `.gitignore`'s generic `loader.sce` rule, since this one is deliberate plumbing, not generated build output). Smoke (`tbx-smoke/sciQuantLib.sce`) prices a canonical European call under Black-Scholes (lifted from `quantlib-swig/Scilab/test/t_european.sce`) and checks NPV/delta/gamma/vega/theta/rho against that test's documented golden values -- reproduced exactly (npv=10.450584 etc.).

### sciSymPy

**Error:** none (passed the generic load bar cleanly at baseline: `delta=1; smoke=none` -- the loader registered a macro library, but nothing exercised Python or SymPy).

**Analysis & fix lane (this pass, C2):** sciSymPy is a thin macro-only pass-through over PIMS' Python bridge (no native gateway of its own), so its baseline `delta=1` PASS never actually called into Python. The whole-branch review's argument for superseding `scimax` with sciSymPy (see `### scimax` above) cited this toolbox as already `smoke=OK` -- false at the time: no smoke file existed, so that claim rested on the same delta-only evidence `scimax` proves is not sufficient. The fix is to collect the missing evidence, not to delete the claim.

**Resolved:** authored `tbx-smoke/sciSymPy.sce`, modeled directly on the toolbox's own regression test (`tests/unit_tests/sympy.tst`). sciSymPy's `loader.sce` does not load PIMS itself, so the smoke loads it first (same sibling-toolbox pattern already used by `fmincont` -> `sci-ipopt` and `pso-toolbox` -> `apifun`), then: `sp = sympy(); x = symbol("x"); d = sp.diff(sp.sympify("x**2"), x)`, and checks `pystr(d)` contains `2*x`. Runs clean through the real harness (`smoke=OK`) and was cross-checked manually outside the harness (`RESULT=[2*x]`). First run through a fresh, isolated `-scihome` (matching the harness's own per-toolbox isolation) completed in ~7s total, including two lazy `uv sync` provisions triggered along the way (PIMS' shared `_base` env, and sciSymPy's own `sympy`+`mpmath` env) -- both resolved from a warm local `uv` cache with no network fetch, so the smoke stays fast under the harness's default `TBX_TIMEOUT`.

### sciTorch

**Repaired** (toolbox commit `b8d63183d27` on the jlmoya mirrors -- the fix lives in the toolbox repo, not this one). Root cause was a stray hardcoded `link()` call in `sci_gateway/cpp/loader.sce` pointing at a *different* Scilab app bundle's IPCV library (version-pinned, absolute path); it failed, and `etc/sciTorch.start`'s `try`/`catch` swallowed the failure and reported a clean load with zero native functions actually registered -- `delta=1` (the macro library) hid a fully-dead gateway, the same wrapper-swallows-failure class as `nan` above, one level up at whole-toolbox startup. Fixed: the stray IPCV `link()` removed; the toolbox's OpenCV closure vendored self-contained; the swallowed-error `catch` now warns instead of silently returning. Fixing the link also exposed that the load chain had been masking unimplemented gateway helpers (`GetDouble`/`GetImage`/`GetString` -- now implemented) and that the bundled model was a 2019 TorchScript export a modern libtorch refuses to load (regenerated -- honestly untrained, exercising the load/list/props/unload surface only, no accuracy claim). Known gap: the untracked ~51MB vendored opencv+ffmpeg closure has no fetch script yet (extends the pre-existing libtorch fetch gap) -- fresh clones need manual staging. Reviewer follow-up worth recording for the toolbox repo (not a harness matter): the pre-existing gateway callers (`sci_int_torch_*.cpp`) dereference `GetDouble`/`GetString` outputs without checking the return code -- newly reachable now that the gateway actually registers, so a wrong-typed argument to a `torch_*` call can NULL-deref instead of raising a clean `Scierror`.

## Delist ledger

- **scidb** — deleted 2026-07-11 by user decision. Legacy Qt4-based database toolbox, superseded by sciDatabase (Qt5/6-compatible). Unbuildable on modern macOS. Its final local commit (75f5bc6) was not on the mirrors at deletion time; it was pushed to the jlmoya GitLab and GitHub mirrors first, so the source survives.

## Closure

**arfit**'s runtime hang is resolved (Task 8) — see the `### arfit` note above. Root cause was an undefined `mtlb_repmat()` MATLAB-compatibility shim called by `arsim()`/`arres()` (not by `arfit()` itself, which is why the generic load bar and a naive smoke both missed it); fixed with a small local compat macro forwarding to Scilab's native `repmat()`. `cfg.verified` includes `arfit` as of this update.

The **10 macro-only unknowns** (anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol) are smoke-tested as of Task 9; all 10 pass with `smoke=OK` and are now in `cfg.verified`. One of the ten, **casci**, turned out to be broken at runtime despite passing the generic load bar: its `macros/lib` was a stale partial build (only 14 of 186 macros compiled/registered — everything alphabetically before `bartlett` — because `builder.sce` had a mismatched-quote parse error and an obsolete `v(2)`-based version gate that misfires under the year-based 2027 numbering, both blocking `tbx_builder_macros` before it ever ran). Fixed both builder issues plus 8 macros hit by two 2027-parser-strictness patterns (an operator touching the `..` continuation token with no space; a multi-line ``""``-escaped-quote string left open across a continuation break); rebuilt cleanly, all 186 macros now compile.

Pre-existing checkouts that pull the commits from this verification campaign serve a **stale** `toolbox_manager` macro lib (missing `tbxVerify` and friends) until a `make` or a manual `genlib` is run: the module's `.start` only regenerates `macros/lib` when it's absent, not when the `.sci` sources are newer.

## Follow-ups

Recorded for the record, per the final review pass, updated as of wave 2 (2026-07-12):

1. **Smoke coverage for external-dependency toolboxes -- RESOLVED (wave 2).** All 17 rows that used to carry no smoke and pass on `delta>=1` alone (cgal, distfun, financial, guibuilder, json, libsvm, lowdisc, nan, quapro, scicv, sciDatabase, scidoe, sciTorch, sndfile-toolbox, specfun, stixbox, xlsx) now have a real smoke -- see the Matrix above and their per-toolbox notes. Every one of the 50 catalog entries carries a smoke fixture as of this update (`scilab/tbx-smoke/`); there is no remaining delta-only PASS row. Two of the seventeen were in the higher-risk external-dependency class this item flagged: **sciTorch** (its smoke found the toolbox's native gateway was actually dead -- see its note above, now fixed and passing) and **sciDatabase** (smoke drives the toolbox's own connection path against the local test engines).
2. **Harness (still open, not undertaken):** swap `execstr("exec(...)", "errcatch")` -> the module's own `try, exec(...); catch` idiom at `tbxVerify.sci:33` and `:40` (see the comment added above the first call site in this pass for why the current shape is fail-safe as shipped), and sanitize the TSV `detail` field (strip `ascii(9)`/`ascii(10)` from `lasterror()` text) in `tbx-verify-one.sce`. Acceptance for that change is a full 50-name re-sweep.
3. **Core bugs surfaced by this campaign** (belong to the UB/hardening campaign, not here): `scivprint()` writes an unbounded `vsprintf` into a static 4096-byte buffer on POSIX (`modules/output_stream/src/c/sciprint.c:81-105`; the Windows branch is bounded) -- this was the sciQuantLib SIGTRAP mechanism; **still open**. `modules/spreadsheet`'s stale `libparquet.2400.dylib` link is **RESOLVED**: rebuilt against the current Homebrew `apache-arrow` (v25, now linking `libparquet.2500.dylib`) during the help-build work (commit b7457457475 context); confirmed via `otool -L` on the rebuilt module.
