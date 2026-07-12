# Toolbox Verification Baseline (2027-0.0.0-macos-dev.1)

This document captures the toolbox-manager verification baseline for macOS arm64 Scilab 2027 on the date of the sweep (2026-07-11). The verification harness (v1.1) establishes that a toolbox is ready for adoption when:

1. **Build succeeds** (if the toolbox has native gates; many are macro-only)
2. **arch64 gate passes** (no stale non-arm64 `.so` / `.dylib` artifacts)
3. **Loader executes cleanly** (`loader.sce` runs without error)
4. **Registers ≥1 macro library** OR **smoke evidence proves functionality** — gateway-only toolboxes (those that register functions via `addinter` and produce zero macro-library delta) verify **exclusively via a smoke file** (`scilab/tbx-smoke/<name>.sce`) that must run clean and set `smoke_ok = %t`

To re-run the full sweep (50 toolboxes, budget ~1 hour):

```bash
cd scilab && ./tbx-verify-all.sh
```

Environment: `JAVA_HOME` is auto-resolved by the script; per-toolbox timeout is `TBX_TIMEOUT` (default 300 seconds).

## Matrix

| Toolbox | Status | Detail |
|---------|--------|--------|
| scimax | TIMEOUT | 300s; scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-scimax-D3uSg9 |
| accsum | FAIL | build failed |
| anova | PASS | delta=1; smoke=none |
| apifun | PASS | delta=1; smoke=OK |
| arfit | PASS | delta=1; smoke=OK |
| casci | PASS | delta=1; smoke=none |
| cgal | PASS | delta=1; smoke=none |
| cma-es | PASS | delta=1; smoke=OK |
| condnb | PASS | delta=1; smoke=none |
| conint | PASS | delta=1; smoke=none |
| csv-readwrite | PASS | delta=1; smoke=OK |
| dataint | PASS | delta=1; smoke=OK |
| dbldbl | PASS | delta=1; smoke=none |
| distfun | PASS | delta=2; smoke=none |
| financial | PASS | delta=1; smoke=none |
| fmincont | PASS | delta=3; smoke=OK |
| FOSSEE-Optimization-toolbox | PASS | delta=1; smoke=OK |
| grocer | PASS | delta=36; smoke=OK |
| guibuilder | PASS | delta=1; smoke=none |
| hypt | PASS | delta=1; smoke=none |
| intprbs | PASS | delta=1; smoke=OK |
| json | PASS | delta=1; smoke=none |
| krisp | PASS | delta=3; smoke=OK |
| libsvm | PASS | delta=1; smoke=none |
| lowdisc | PASS | delta=1; smoke=none |
| lsf_toolbox | PASS | delta=1; smoke=OK |
| makematrix | PASS | delta=1; smoke=none |
| montesci | PASS | delta=1; smoke=OK |
| nan | PASS | delta=1; smoke=none |
| neuralnetwork | PASS | delta=5; smoke=none |
| nisp | PASS | delta=3; smoke=OK |
| number | PASS | delta=1; smoke=none |
| ortpol | PASS | delta=5; smoke=none |
| parquet | PASS | delta=1; smoke=OK |
| PIMS | PASS | delta=0; smoke=OK |
| pso-toolbox | PASS | delta=1; smoke=OK |
| quapro | PASS | delta=1; smoke=none |
| regtools | PASS | delta=1; smoke=OK |
| scicv | PASS | delta=1; smoke=none |
| sciDatabase | PASS | delta=1; smoke=none |
| scidoe | PASS | delta=1; smoke=none |
| sci_gsl | PASS | delta=1; smoke=OK |
| sci-ipopt | PASS | delta=0; smoke=OK |
| sciQuantLib | PASS | delta=0; smoke=OK |
| sciSymPy | PASS | delta=1; smoke=none |
| sciTorch | PASS | delta=1; smoke=none |
| sndfile-toolbox | PASS | delta=1; smoke=none |
| specfun | PASS | delta=1; smoke=none |
| stixbox | PASS | delta=1; smoke=none |
| xlsx | PASS | delta=1; smoke=none |

**Summary:** 47 PASS / 2 FAIL / 1 TIMEOUT / 0 CRASH of 50 total

## Per-toolbox notes

### accsum

**Error:** build failed

**Analysis & fix lane:** Native C gateway was never ported to macOS arm64. The build step fails early, before loader execution. **Planned:** playbook port of the gateway module to arm64.

### arfit

**Error:** none (passes the generic load bar cleanly: `delta=1; smoke=none` at baseline).

**Analysis & fix lane:** arfit loads cleanly and its top-level `arfit()` fitting function never hangs on its own — but the toolbox's other core functions (`arsim()`, the AR-process simulator; `arres()`, the residual/model-adequacy check), both part of the documented fit-and-diagnose workflow (`demos/ardem.sce`), call an undefined MATLAB-compatibility shim `mtlb_repmat()` (5 call sites: `macros/arsim.sci:82,104,134`, `macros/arres.sci:81,91`). Scilab's own m2sci converter (`modules/m2sci/macros/sci_files/sci_repmat.sci`) documents `mtlb_repmat()` as its runtime-emulation fallback for `repmat()` calls it can't statically type at MATLAB→Scilab conversion time, but core never shipped that runtime macro (unlike its `mtlb_sum`/`mtlb_triu`/`mtlb_mean` siblings in `modules/m2sci/macros/compat_functions/`). The resulting undefined-variable error is uncaught by the toolbox; raised from a bare, non-harness invocation (an interactive session, or any script without its own try/catch) it leaves the Scilab process blocked reading from console instead of exiting — externally observed as a hang, matching the port-time note ("core fns HANG under 2027, mtlb_-heavy"). **Planned:** add a local `mtlb_repmat` compat macro to the toolbox, forwarding to Scilab's native `repmat()` (argument-compatible at every call site).

**Resolved (Task 8):** Added `macros/mtlb_repmat.sci` — a one-function shim (`B = mtlb_repmat(A, varargin); B = repmat(A, varargin(:));`) — and rebuilt the toolbox's macro library (`tbx_builder_macros`) so `mtlb_repmat.bin` is registered in `macros/lib` alongside the toolbox's other macros. Verified with a full-chain probe (`arsim` → `arfit` → `arres` → `arconf` → `armode`, exercising all 5 `mtlb_repmat` call sites) that now runs clean end to end and recovers known VAR(1) coefficients within tolerance. Note on the harness's own catch behavior: `tbxVerify()`'s smoke step wraps execution in `execstr(..., "errcatch")` (`tbxVerify.sci:32`), so replaying the pre-fix defect *through the official harness* actually produces a fast, clean `FAIL: smoke error: Undefined variable: mtlb_repmat` rather than a literal `TIMEOUT` — confirmed by a negative-control re-run with the fix backed out. The hang itself was independently reproduced and confirmed outside the errcatch-protected harness path (a bare, uncaught top-level script call into `arsim()`, run directly under `scilab-adv-cli -f`), which is the invocation shape the port-time note's "hang" almost certainly describes; both the harness's catchable FAIL and the raw hang share the identical root cause. Added `tbx-smoke/arfit.sce`: a known-coefficient VAR(1) simulate→fit→residual-check round trip (`arsim`+`arfit`+`arres`) that exercises every `mtlb_repmat` call site — the brief's originally-proposed bare `[w,A,C]=arfit(v,1,2)` smoke was confirmed to PASS even on the broken toolbox (`arfit()` itself never calls `mtlb_repmat`), so it would not have caught this defect.

### csv-readwrite

**Error:** loader error 10000: startModule: error on line #23: "exec: Cannot open file /Users/josemoya/Projects/SciLabProjects/csv-readwrite/sci_gateway/loader_gateway.sce."

**Analysis & fix lane:** The gateway port was left ~80% complete. The loader references `loader_gateway.sce` which is missing or incomplete. **Planned:** finish the gateway port, or delist csv-readwrite as core-redundant (core Scilab ships `csvRead` / `csvWrite` natively).

**Resolved (Task 7):** the real blocker wasn't the previously-recorded file-I/O API churn alone but a chain of five further issues never reached before: a mismatched-quote parse bug in `builder.sce` (line 37) that failed before any builder macro ran, an obsolete `getversion()`-based version gate that misfires under the year-based 2027 numbering, missing standard-library includes (`stddef.h`/`stdio.h`/`ctype.h`/`string.h`) that are hard errors under modern clang, a wholly-fictional `IsValidUTF8()` call in `latintoutf.c` (never a real API, replaced with a small local implementation), and two gateway files (`sci_csv_default.c`'s RHS-count helpers, `gw_csv_helpers.c`) whose bodies referenced a bare `pvApiCtx` that was never in scope; the mopen/mgetl/mclose narrow-to-wide rewrite in `csv_read.c` followed core's own modern `csvRead.c` as a template, and `sci_csv_write.c`'s `CheckLhs(1,1)` was loosened to `CheckLhs(0,1)` to match its void-return success path and the toolbox's own shipped test convention (bare, uncaptured `csv_write(x,filename)` call). Function names are `csv_read`/`csv_write`/`csv_textscan`/`csv_stringtodouble`/`csv_default`/`csv_isnum` (confirmed from `sci_gateway/c/builder_gateway_c.sce`'s own table) -- no collision with core's `csvRead`/`csvWrite`/`csvTextScan` (different case/underscore convention). Verified with a real round-trip smoke (`tbx-smoke/csv-readwrite.sce`): a 2x3 matrix written and read back via the toolbox's own `csv_write`/`csv_read` compares exactly equal.

### krisp

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libkrisp_c.so, /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libskeleton_c.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/c/`. **Planned:** remove stale artifacts, rebuild for arm64, and fix `corr_*` registration.

**Resolved (Task 6):** Removed the two tracked stale non-arm64 build artifacts (`libkrisp_c.so`, a 32-bit x86 ELF; `libskeleton_c.so`, a dead unused legacy gateway-registration template never referenced by the actual builder) from `sci_gateway/c/`, clearing the arch gate. Rebuilt the `corr_*` native gateway from source in a clean room (`sci_gateway/c/builder_gateway_c.sce` with the standard `CPATH`/`LIBRARY_PATH` env recipe) and confirmed `c_corr_D`/`c_corr_X`/`c_corr_vector` register and compute correct values (`corr(0)=1`, closed-form gaussian kernel match) — the "natives build but don't register" issue logged in FINANCE-TOOLBOX-PORTING.md at port time no longer reproduces; the real blocker was purely the arch-gate artifacts. Added `tbx-smoke/krisp.sce` (RLHS bounds/shape check + a real `c_corr_D` call verified against the gateway's own closed-form kernel).

### parquet

**Error:** loader error 10000: exec: error on line #13: "link: The shared archive was not loaded: dlopen(/Users/josemoya/Projects/SciLabProjects/parquet/sci_gateway/cpp//../../src/cpp/libarrow.dylib, 0x000A): Library not loaded: /opt/homebrew/opt/apache-arrow/lib/libarrow.2400.dylib"

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. Homebrew's apache-arrow library has been bumped past the `libarrow.2400.dylib` ABI that the cached gateway was built against. **Planned:** rebuild the gateway against the current apache-arrow version.

**Resolved (Task 4b):** re-ran `build_macos.sce` against the now-current Homebrew apache-arrow (v25.0.0, `libarrow.2500.dylib`); the Darwin builder branch links generically (`-larrow -lparquet` against whatever `/opt/homebrew/opt/apache-arrow` resolves to), so no source change was needed, only a rebuild of the gitignored native artifacts. Verified with a real round-trip smoke (`tbx-smoke/parquet.sce`): a mixed-type table (double/int32/string/bool) written to and read back from a `.parquet` file compares equal. Also surfaced (out of scope for this task, flagged for awareness): Scilab core's own `modules/spreadsheet` links the same old `libparquet.2400.dylib` and logs a non-fatal dlopen error at shutdown on every session — pre-existing, unrelated to the toolbox fix, not remediated here.

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

### sci_gsl

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libMC_toolbox.so, /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libsci_gsl.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/cpp/`. **Planned:** clean stale artifacts and rebuild for arm64.

**Resolved (Task 4b):** `libMC_toolbox.so` and `libsci_gsl.so` were leftover Linux ELF binaries (`file` confirms `ELF 64-bit LSB shared object, x86-64` -- not even Mach-O) tracked in git since the initial port commit, evidently bundled in the original off-forge source tarball and never touched by the Darwin build branch, which only ever produces `libsci_gsl.dylib` (the correct arm64 Mach-O, already present and gitignored). `git rm`'d both stale files; no rebuild needed. The existing smoke (`tbx-smoke/sci_gsl.sce`, `phyconst(1)==299792458`) now passes.

### sci-ipopt

**Error:** loader registered no new library

**Analysis & fix lane:** Gateway-only toolbox (addinter natives, zero macro-library delta), same as PIMS. Under harness v1.1, requires a smoke file. **Planned:** author a smoke file at `scilab/tbx-smoke/sci-ipopt.sce` that actually **solves** (historical failure mode was at solve time, not load).

**Resolved (Task 4b):** the historical solve-time MPI abort fix (`macos-fix-arpack-mpi.sh`, redirecting arpack's OpenMPI deps to IPOPT's sequential `libmpiseq` stub) is still intact on this machine -- no rebuild needed. Authored `tbx-smoke/sci-ipopt.sce`: lifts the toolbox's own constrained-Rosenbrock regression test (`tests/unit_tests/ipopt_rosenbrock.tst`, exact Hessian, nonlinear inequality constraint) and asserts the solution against the test's own documented reference (`[0.90723379674169202, 0.82275515858492032]`). Solves cleanly; the MPI blocker did not resurface.

### scimax

**Error:** TIMEOUT (300s); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-scimax-D3uSg9

**Analysis & fix lane:** The builder errors into an interactive REPL prompt loop, which the harness cannot exit; the 300-second timeout is exceeded. **Planned:** run the builder with `mode(3)` to locate the failing line; also requires Homebrew maxima at runtime.

### sciQuantLib

**Error:** CRASH (rc=133 = SIGTRAP); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-sciQuantLib-wV7nAM

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. The process dies with a trap signal during load, producing zero console output. This is a dylib-level issue. **Planned:** retrieve the crash report from `~/Library/Logs/DiagnosticMessages/` and run `otool -L` on the gateway dylibs to diagnose link failures or symbol mismatches.

**Resolved (Task 4b):** root cause was NOT a dylib/codesign/deployment-target issue (the `otool -l` LC_BUILD_VERSION precedent didn't apply here). The crash report's faulting thread showed `sciprint`->`scivprint`->`__vsprintf_chk`->`__chk_fail_overflow` ("detected buffer overflow") called from Scilab core's `AddInterfaceToScilab`/`scilabLink` (`modules/dynamic_link/src/cpp/dynamic_link.cpp`) during `addinter()`. Root cause traced to `SciLabProjects/sciQuantLib/loader.sce` (the toolbox-root entry point the harness execs) being a **stale, untracked leftover** from an early SWIG "baseline" (Adder/twice) proof-of-concept spike: it referenced a `libbaseline.dylib` that was never built or committed anywhere in the tree. Its `dlopen()` failure produced a dyld "tried: ..." error message (lists every fallback search path -- confirmed independently to run into multiple KB via the analogous `libparquet.2400.dylib` message logged by `modules/spreadsheet/etc/spreadsheet.quit` on every session) that overflowed `scivprint`'s fixed 4096-byte static buffer (`modules/output_stream/src/c/sciprint.c` uses unbounded `vsprintf` on POSIX, unlike the bounded `vsnprintf` on the Windows branch) -- SIGTRAP, zero output, because the crash happened while *forming* the first diagnostic message. The real, working toolbox has lived all along in the `quantlib-swig` git submodule (`quantlib-swig/Scilab/toolbox/`), confirmed to load cleanly (61 registrations) when exec'd directly. Fixed by replacing the stale root `loader.sce` with a small delegator to the submodule's real loader (force-added past `.gitignore`'s generic `loader.sce` rule, since this one is deliberate plumbing, not generated build output). Smoke (`tbx-smoke/sciQuantLib.sce`) prices a canonical European call under Black-Scholes (lifted from `quantlib-swig/Scilab/test/t_european.sce`) and checks NPV/delta/gamma/vega/theta/rho against that test's documented golden values -- reproduced exactly (npv=10.450584 etc.).

## Delist ledger

- **scidb** — deleted 2026-07-11 by user decision. Legacy Qt4-based database toolbox, superseded by sciDatabase (Qt5/6-compatible). Unbuildable on modern macOS. A local unpushed commit (75f5bc6) preserving the deletion snapshot has been pushed to jlmoya GitLab and GitHub mirrors for provenance.

## Closure

**arfit**'s runtime hang is resolved (Task 8) — see the `### arfit` note above. Root cause was an undefined `mtlb_repmat()` MATLAB-compatibility shim called by `arsim()`/`arres()` (not by `arfit()` itself, which is why the generic load bar and a naive smoke both missed it); fixed with a small local compat macro forwarding to Scilab's native `repmat()`. `cfg.verified` includes `arfit` as of this update.

The **10 macro-only unknowns** (anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol) all pass the generic load bar (each contributes delta≥1 macro library), and have not yet been smoke-tested. Task 9 will author the 10 smoke files and register them in cfg.verified.
