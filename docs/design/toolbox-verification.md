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
| sciQuantLib | CRASH | rc=133; scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-sciQuantLib-wV7nAM |
| scimax | TIMEOUT | 300s; scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-scimax-D3uSg9 |
| accsum | FAIL | build failed |
| csv-readwrite | FAIL | loader error 10000: startModule: error on line #23: "exec: Cannot open file /Users/josemoya/Projects/SciLabProjects/csv-readwrite/sci_gateway/loader_gateway.sce." |
| krisp | FAIL | non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libkrisp_c.so, /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libskeleton_c.so |
| parquet | FAIL | loader error 10000: exec: error on line #13: "link: The shared archive was not loaded: dlopen(/Users/josemoya/Projects/SciLabProjects/parquet/sci_gateway/cpp//../../src/cpp/libarrow.dylib, 0x000A): Library not loaded: /opt/homebrew/opt/apache-arrow/lib/libarrow.2400.dylib" |
| PIMS | FAIL | loader registered no new library |
| pso-toolbox | FAIL | loader error 10000: add_help_chapter: error on line #71: "add_help_chapter: Wrong value for input argument #2: An existing directory expected." |
| regtools | FAIL | loader error 10000: exec: error on line #29: "Failed to install guimaker from atoms." |
| sci_gsl | FAIL | non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libMC_toolbox.so, /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libsci_gsl.so |
| sci-ipopt | FAIL | loader registered no new library |
| anova | PASS | delta=1; smoke=none |
| apifun | PASS | delta=1; smoke=OK |
| arfit | PASS | delta=1; smoke=none |
| casci | PASS | delta=1; smoke=none |
| cgal | PASS | delta=1; smoke=none |
| cma-es | PASS | delta=1; smoke=OK |
| condnb | PASS | delta=1; smoke=none |
| conint | PASS | delta=1; smoke=none |
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
| quapro | PASS | delta=1; smoke=none |
| scicv | PASS | delta=1; smoke=none |
| sciDatabase | PASS | delta=1; smoke=none |
| scidoe | PASS | delta=1; smoke=none |
| sciSymPy | PASS | delta=1; smoke=none |
| sciTorch | PASS | delta=1; smoke=none |
| sndfile-toolbox | PASS | delta=1; smoke=none |
| specfun | PASS | delta=1; smoke=none |
| stixbox | PASS | delta=1; smoke=none |
| xlsx | PASS | delta=1; smoke=none |

**Summary:** 39 PASS / 9 FAIL / 1 TIMEOUT / 1 CRASH of 50 total

## Per-toolbox notes

### accsum

**Error:** build failed

**Analysis & fix lane:** Native C gateway was never ported to macOS arm64. The build step fails early, before loader execution. **Planned:** playbook port of the gateway module to arm64.

### csv-readwrite

**Error:** loader error 10000: startModule: error on line #23: "exec: Cannot open file /Users/josemoya/Projects/SciLabProjects/csv-readwrite/sci_gateway/loader_gateway.sce."

**Analysis & fix lane:** The gateway port was left ~80% complete. The loader references `loader_gateway.sce` which is missing or incomplete. **Planned:** finish the gateway port, or delist csv-readwrite as core-redundant (core Scilab ships `csvRead` / `csvWrite` natively).

### krisp

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libkrisp_c.so, /Users/josemoya/Projects/SciLabProjects/krisp/sci_gateway/c/libskeleton_c.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/c/`. **Planned:** remove stale artifacts, rebuild for arm64, and fix `corr_*` registration.

### parquet

**Error:** loader error 10000: exec: error on line #13: "link: The shared archive was not loaded: dlopen(/Users/josemoya/Projects/SciLabProjects/parquet/sci_gateway/cpp//../../src/cpp/libarrow.dylib, 0x000A): Library not loaded: /opt/homebrew/opt/apache-arrow/lib/libarrow.2400.dylib"

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. Homebrew's apache-arrow library has been bumped past the `libarrow.2400.dylib` ABI that the cached gateway was built against. **Planned:** rebuild the gateway against the current apache-arrow version.

### PIMS

**Error:** loader registered no new library

**Analysis & fix lane:** Gateway-only toolbox (addinter natives, zero macro-library delta). Under harness v1.1, gateway-only toolboxes must provide a smoke file to verify; without one, the delta==0 result is correctly reported as FAIL. **Planned:** author a smoke file at `scilab/tbx-smoke/PIMS.sce`.

### pso-toolbox

**Error:** loader error 10000: add_help_chapter: error on line #71: "add_help_chapter: Wrong value for input argument #2: An existing directory expected."

**Analysis & fix lane:** The loader calls `add_help_chapter()` but the help directory is missing in this build. The help registration is unconditional and aborts when the directory doesn't exist. **Planned:** guard the help registration call in the loader to skip gracefully if the directory is absent.

### regtools

**Error:** loader error 10000: exec: error on line #29: "Failed to install guimaker from atoms."

**Analysis & fix lane:** The loader auto-installs the `guimaker` toolbox from ATOMS as a dependency, but `guimaker` fails to build on 2027/arm64. This blocks regtools even though the batch functions don't inherently require guimaker. **Planned:** decouple guimaker; ensure batch functions work standalone.

### sci_gsl

**Error:** non-arm64 native lib: /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libMC_toolbox.so, /Users/josemoya/Projects/SciLabProjects/sci_gsl/sci_gateway/cpp/libsci_gsl.so

**Analysis & fix lane:** Arch gate detected stale x86_64 binaries in `sci_gateway/cpp/`. **Planned:** clean stale artifacts and rebuild for arm64.

### sci-ipopt

**Error:** loader registered no new library

**Analysis & fix lane:** Gateway-only toolbox (addinter natives, zero macro-library delta), same as PIMS. Under harness v1.1, requires a smoke file. **Planned:** author a smoke file at `scilab/tbx-smoke/sci-ipopt.sce` that actually **solves** (historical failure mode was at solve time, not load).

### scimax

**Error:** TIMEOUT (300s); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-scimax-D3uSg9

**Analysis & fix lane:** The builder errors into an interactive REPL prompt loop, which the harness cannot exit; the 300-second timeout is exceeded. **Planned:** run the builder with `mode(3)` to locate the failing line; also requires Homebrew maxima at runtime.

### sciQuantLib

**Error:** CRASH (rc=133 = SIGTRAP); scratch=/var/folders/9g/wdn7gl9s15b3_r5vggg4yzvc0000gn/T//tbxverify-sciQuantLib-wV7nAM

**Analysis & fix lane:** REGRESSION in a previously-verified toolbox. The process dies with a trap signal during load, producing zero console output. This is a dylib-level issue. **Planned:** retrieve the crash report from `~/Library/Logs/DiagnosticMessages/` and run `otool -L` on the gateway dylibs to diagnose link failures or symbol mismatches.

## Delist ledger

- **scidb** — deleted 2026-07-11 by user decision. Legacy Qt4-based database toolbox, superseded by sciDatabase (Qt5/6-compatible). Unbuildable on modern macOS. A local unpushed commit (75f5bc6) preserving the deletion snapshot has been pushed to jlmoya GitLab and GitHub mirrors for provenance.

## Closure

**arfit** passes the load bar (delta=1) but has a **known runtime hang** in its fitting core that was discovered post-load. Verification is gated on a smoke file (`scilab/tbx-smoke/arfit.sce`) that exercises the fitting loop — in progress as of this baseline.

The **10 macro-only unknowns** (anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol) all pass the generic load bar (each contributes delta≥1 macro library), and have not yet been smoke-tested. Task 9 will author the 10 smoke files and register them in cfg.verified.
