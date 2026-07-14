# sciFinance — P0 Implementation Plan (Architecture Gate)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `sciFinance` toolbox skeleton whose native gateway links `libQuantLib` directly, prove the four things every later phase depends on — the QuantLib link, the C++ exception boundary, the no-leak claim, and the demo pipeline — and ship one real user-facing function (`isbusday`) end to end with a demo that doubles as its acceptance test.

**Architecture:** A Scilab macro layer validates arguments and unwraps Scilab's `datetime` (an *mlist*, which can never cross into C) into plain numeric `[year month day]` triples. A C++ gateway layer receives only Scilab-native types, does the numerics in a loop against QuantLib, and returns Scilab-native types. **No SWIG pointer, and no QuantLib handle, ever crosses into user code.** Every gateway entry point is wrapped in a `try`/`catch` that converts a C++ exception into a clean `Scierror` — an uncaught exception crossing the C gateway boundary is undefined behaviour and crashes the process.

**Tech Stack:** Scilab 2027 (`api_scilab` C gateway, `tbx_build_gateway`), QuantLib 1.42.1 (Homebrew), C++17, macOS arm64.

## Scope

This plan covers **P0 only** — the architecture gate from the spec (`docs/superpowers/specs/2026-07-13-scifinance-design.md`).

P0 is deliberately its own plan. The spec states P0 "retires the architecture risk before volume," and that is a real dependency, not ceremony: **P1–P6 task code is written against patterns P0 proves** (the exception-boundary macro, the date bridge, the vectorised batch loop, the demo-as-test harness). Writing detailed P1–P6 tasks now would mean fabricating hundreds of lines of code on top of unproven assumptions — if the link, the boundary, or the mlist unwrap behaves differently than designed, all of it would be wrong.

P0 on its own produces working, testable software: a toolbox that builds, loads, exposes `isbusday()`, and shows a demo in the Demonstrations window. The P1–P6 roadmap is at the end of this document; each gets its own plan once P0 is green.

---

## Global Constraints

Every task's requirements implicitly include this section.

**Architecture (from the spec, verbatim):**
- **No SWIG pointer ever crosses into user code.** The facade takes Scilab natives, loops in C++, returns Scilab natives.
- **Curves are data (an `mlist`), not handles.** The gateway reconstitutes the QuantLib object per call and reuses it across the vectorised batch.
- **Mandatory on every gateway entry:** wrap the body in `SCIFIN_TRY ... SCIFIN_CATCH(fname)` (from `sciFinance_gw.hpp`). An uncaught C++ exception across a C gateway boundary is UB → crash.
  - **Corrected during Task 1 review (user-approved).** The boundary must rethrow Scilab's own exceptions *before* the generic catches:
    `catch (const ast::ScilabException&) { throw; }` then `catch (const std::exception& e) { Scierror(...); return 1; }` then `catch (...)`.
    `ast::InternalAbort` (**Ctrl-C**), `ast::InternalError` and `ast::RecursionException` all derive from `std::exception` via `ast::ScilabException`, and the runtime relies on them escaping a gateway (`runner.cpp` discriminates all three). A bare `catch (const std::exception&)` swallows **Ctrl-C** — fatal for the long-running P5 Monte Carlo gateway.
  - Never write a raw try/catch in a gateway. Use the macro pair, so the rule is applied structurally.
- QuantLib is linked **directly** (`/opt/homebrew/opt/quantlib`, v1.42.1) — *not* through the sciQuantLib SWIG binding.

**Dates — verified this session, and a silent-corruption trap if ignored:**
- Scilab's `datetime` is an **mlist** (`typeof(datetime(2026,7,13))` == `"datetime"`). It **cannot** be passed to a C gateway. The macro layer must decompose it.
- **Dates cross the boundary as `[year month day]` integer triples**, obtained via `datevec()` (verified: returns an N×6 `[y m d h m s]` matrix). **NEVER as serial numbers** — Scilab's `datenum` epoch is not MATLAB's (`datenum(2026,7,13)` = **740176**), so serials are an epoch trap.
- Scilab matrices are **column-major**: element `(i, c)` of an N×3 matrix is `p[i + c*N]`.

**Build (macOS arm64 — the proven recipe, do not improvise):**
- C++17 (`-std=c++17`); QuantLib requires it and its `pkg-config` already emits it.
- Resolve QuantLib via `pkg-config`, never a hardcoded Cellar path (version drift).
- `-I/opt/homebrew/opt/gettext/include` — macOS has no `libintl.h` on the default path and Scilab's `localization.h` needs it.
- Homebrew gcc runtime `-L` dirs via the **version-independent** `/opt/homebrew/lib/gcc/current` symlink (a pinned `gcc/15` path goes stale on the next gcc bump).
- Header locations (verified): `charEncoding.h` → `modules/localization/includes`; `sci_malloc.h` → `modules/core/includes`.
- New code compiles with `-Wall -Wextra` (no `-w`, no `-fpermissive` — those are legacy-toolbox crutches), and **zero warnings from our own sources**.
- **Three things this plan's Task 1 draft got wrong; the working `build_macos.sce` in the repo is now the reference — copy it, don't re-derive it:**
  1. `-I/opt/homebrew/opt/boost/include` is **required**. QuantLib's own `ql/qldefines.hpp` includes `<boost/config.hpp>`, but `QuantLib.pc` does not emit a boost `-I`.
  2. `tbx_build_gateway(..., C_Flags, "", "g++")` — the trailing `g++` forces the CC. Scilab's `dynamic_link` build runs an autoconf **C**-compiler probe and would otherwise apply `-std=c++17` to a `.c` conftest and fail. This toolbox has zero `.c` files, so forcing `g++` costs nothing.
  3. Four extra `-I` paths are needed for the exception boundary's `ast/scilabexception.hxx`: `modules/ast/includes`, `.../includes/ast`, `.../includes/exps`, `.../includes/system_env`.
- `macros/buildmacros.sce` must **not** end with `quit` — `tbx_builder_macros` `exec`s it in-process mid-build, so a `quit` there kills the build before `tbx_build_loader` runs. (The "every batch `.sce` ends with `quit`" rule applies to scripts *you* launch, not ones the build `exec`s.)

**Demos are the acceptance tests:**
- Every demo renders in the Demonstrations window **and** runs headless in CI against goldens.
- **Every filename listed in a `.dem.gateway.sce` MUST exist.** A single missing file empties the *entire* Demonstrations window (this exact bug, `20796ca5bdf`).
- Never call `stacksize()` — removed in Scilab 6.
- No bare `halt()` in a demo (it hangs CI).
- Every batch `.sce` ends with `quit`.

**Validation bar (a task is not done until all five pass):**
1. Goldens match.
2. Vectorised result == looped result (parity).
3. Leak proof: 10,000 calls, RSS flat.
4. Adversarial args: no segfault, clean Scilab errors.
5. 10× repeat runs, all green (intermittent crashes are the ones that matter).

**Repo / commit rules:**
- Toolbox lives at `/Users/josemoya/Projects/SciLabProjects/sciFinance` (its own git repo).
- Commit directly on `main`; push both remotes.
- **No AI-attribution trailers** in any commit message (no `Co-Authored-By`, no `Claude-Session`).

---

## File Structure

```
/Users/josemoya/Projects/SciLabProjects/sciFinance/
├── DESCRIPTION                          # toolbox metadata
├── build_macos.sce                      # THE build: pkg-config QuantLib + gettext + gcc runtime
├── loader.sce                           # generated by tbx_build_loader
├── etc/
│   ├── sciFinance.start                 # registers macros + gateway + demos on load
│   └── sciFinance.quit                  # no-op teardown
├── sci_gateway/cpp/
│   ├── sciFinance_gw.hpp                # THE exception boundary (SCIFIN_TRY / SCIFIN_CATCH)
│   ├── fin_date_bridge.hpp              # [y,m,d] <-> QuantLib::Date; calendar lookup by name
│   ├── sci_fin_version.cpp              # finversion()   — proves the QuantLib link
│   └── sci_fin_isbusday.cpp             # fin_isbusday() — the vectorised batch pattern
├── macros/
│   ├── isbusday.sci                     # user-facing: datetime -> datevec -> gateway
│   └── buildmacros.sce
├── demos/
│   ├── sciFinance.dem.gateway.sce       # subdemolist — every path here MUST exist
│   └── calendars.dem.sce                # the P0 demo == the P0 acceptance test
└── tests/
    ├── run_tests.sce                    # headless runner: goldens + parity + adversarial + demo tree
    └── leakcheck.sce                    # 10k calls, RSS flat
```

**Responsibilities.** `sciFinance_gw.hpp` exists so the exception boundary is *structural* — a new gateway gets it by including the header and using the macro pair, not by remembering to. `fin_date_bridge.hpp` is the single place that knows how a Scilab date becomes a `QuantLib::Date`, so the epoch decision is made once. Each `sci_fin_*.cpp` is one gateway family. Macros never do numerics; gateways never parse mlists.

---

### Task 1: Toolbox skeleton + QuantLib link

Proves: the toolbox builds, loads, registers a gateway, and the QuantLib link resolves at runtime. This is the smallest possible thing that can fail for a build reason, so it fails *here* rather than inside a 200-line pricing gateway.

**Files:**
- Create: `sciFinance/DESCRIPTION`
- Create: `sciFinance/build_macos.sce`
- Create: `sciFinance/etc/sciFinance.start`
- Create: `sciFinance/etc/sciFinance.quit`
- Create: `sciFinance/sci_gateway/cpp/sciFinance_gw.hpp`
- Create: `sciFinance/sci_gateway/cpp/sci_fin_version.cpp`
- Create: `sciFinance/macros/buildmacros.sce`
- Test: `sciFinance/tests/run_tests.sce`

**Interfaces:**
- Consumes: nothing (first task).
- Produces:
  - `SCIFIN_TRY` / `SCIFIN_CATCH(fname)` — the exception-boundary macro pair, from `sciFinance_gw.hpp`. Every later gateway uses these.
  - Scilab function `finversion()` → a string, the linked QuantLib version (`"1.42.1"`).

- [ ] **Step 1: Write the failing test**

Create `/Users/josemoya/Projects/SciLabProjects/sciFinance/tests/run_tests.sce`:

```scilab
// sciFinance headless test runner. Exits nonzero on any failure.
mode(-1); lines(0);
here = get_absolute_file_path("run_tests.sce");
root = fullfile(here, "..");

exec(fullfile(root, "loader.sce"), -1);

failures = 0;
function check(cond, msg)
    global failures
    if cond then
        mprintf("  PASS  %s\n", msg);
    else
        mprintf("  FAIL  %s\n", msg);
        failures = failures + 1;
    end
endfunction

mprintf("\n--- link ---\n");
check(finversion() == "1.42.1", "finversion() == 1.42.1 (QuantLib is linked)");

mprintf("\n%d failure(s)\n", failures);
if failures > 0 then quit(1); end
quit(0);
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: fails — `loader.sce` does not exist yet.

- [ ] **Step 3: Create `DESCRIPTION`**

```
Toolbox: sciFinance
Title: Financial engineering for Scilab (QuantLib-backed)
Summary: Business calendars, option pricing, curves, Monte Carlo and portfolio analytics.
Version: 0.1.0
Author: Jose Moya
Maintainer: Jose Moya <jlmoya@gmail.com>
Category: Finance
Entity: sciFinance
License: GPL-2.0
ScilabVersion: >= 2027.0.0
Depends:
Date: 2026-07-13
```

- [ ] **Step 4: Create the exception boundary — `sci_gateway/cpp/sciFinance_gw.hpp`**

This header is the single most important file in the toolbox. Every gateway includes it.

```cpp
#ifndef SCIFINANCE_GW_HPP
#define SCIFINANCE_GW_HPP

// The exception boundary.
//
// QuantLib signals every error by throwing (QuantLib::Error derives from
// std::exception). A C++ exception unwinding across the C gateway boundary is
// undefined behaviour -- in practice it kills the whole Scilab process. So the
// body of EVERY gateway entry point sits between SCIFIN_TRY and SCIFIN_CATCH,
// which turn any throw into an ordinary Scilab error.
//
//   extern "C" int sci_fin_foo(scilabEnv env, int nin, scilabVar* in, ...)
//   {
//       const char fname[] = "fin_foo";
//       SCIFIN_TRY
//       ...
//       return 0;
//       SCIFIN_CATCH(fname)
//   }

#include <exception>

extern "C" {
#include "api_scilab.h"
#include "Scierror.h"
}

#define SCIFIN_TRY try {

#define SCIFIN_CATCH(fname)                                              \
    } catch (const std::exception& e) {                                  \
        Scierror(999, "%s: %s\n", (fname), e.what());                    \
        return 1;                                                        \
    } catch (...) {                                                      \
        Scierror(999, "%s: unknown C++ exception\n", (fname));           \
        return 1;                                                        \
    }                                                                    \
    return 1;   /* unreachable; silences -Wreturn-type */

#endif // SCIFINANCE_GW_HPP
```

- [ ] **Step 5: Create `sci_gateway/cpp/sci_fin_version.cpp`**

```cpp
// finversion() -> string : the QuantLib version this gateway is linked against.
// Trivial on purpose -- it exists to prove the link and the gateway registration.
#include "sciFinance_gw.hpp"

#include <ql/version.hpp>

extern "C" {
#include "charEncoding.h"   // to_wide_string
#include "sci_malloc.h"     // FREE
}

extern "C" int sci_fin_version(scilabEnv env, int nin, scilabVar* in, int nopt,
                               scilabOpt opt, int nout, scilabVar* out)
{
    const char fname[] = "finversion";
    SCIFIN_TRY

    if (nin != 0)
    {
        Scierror(999, "%s: Wrong number of input arguments: %d expected.\n", fname, 0);
        return 1;
    }

    wchar_t* wver = to_wide_string(QL_VERSION);
    out[0] = scilab_createString(env, wver);
    FREE(wver);
    return 0;

    SCIFIN_CATCH(fname)
}
```

- [ ] **Step 6: Create `macros/buildmacros.sce`**

```scilab
// Builds the sciFinance macro library.
mode(-1);
macros_path = get_absolute_file_path("buildmacros.sce");
tbx_build_macros("sciFinance", macros_path);
clear tbx_build_macros;
```

- [ ] **Step 7: Create `build_macos.sce` — the build**

```scilab
// sciFinance build (macOS arm64). Links libQuantLib DIRECTLY -- not via SWIG.
mode(-1); lines(0);

tb_dir = get_absolute_file_path("build_macos.sce");
gw_dir = fullfile(tb_dir, "sci_gateway", "cpp");

// QuantLib via pkg-config -- never a hardcoded Cellar path (it moves every bump).
pc = "PKG_CONFIG_PATH=/opt/homebrew/lib/pkgconfig:/opt/homebrew/opt/quantlib/lib/pkgconfig pkg-config ";
ql_cflags = stripblanks(unix_g(pc + "--cflags QuantLib"));
ql_libs   = stripblanks(unix_g(pc + "--libs QuantLib"));
if ql_cflags == "" | ql_libs == "" then
    error("sciFinance: pkg-config cannot resolve QuantLib. Run: brew install quantlib");
end

// Scilab headers we use explicitly: charEncoding.h (localization) and
// sci_malloc.h (core) are NOT in the default gateway include set.
sci_inc = " -I" + SCI + "/modules/api_scilab/includes" + ..
          " -I" + SCI + "/modules/core/includes" + ..
          " -I" + SCI + "/modules/localization/includes" + ..
          " -I" + SCI + "/modules/output_stream/includes" + ..
          " -I" + SCI + "/modules/call_scilab/includes";

// -I gettext: macOS has no libintl.h on the default path; Scilab's localization.h needs it.
C_Flags = "-std=c++17 -Wall -Wextra -I/opt/homebrew/opt/gettext/include" + ..
          " -I" + gw_dir + sci_inc + " " + ql_cflags;

Linker_Flag = ql_libs;
// Homebrew gcc runtime (-lemutls_w / -lgfortran). Use the version-independent
// "current" symlink: a pinned gcc/15 path goes stale on the next gcc bump.
gcc_lib = stripblanks(unix_g("dirname $(ls /opt/homebrew/lib/gcc/current/gcc/*/*/libemutls_w.a 2>/dev/null | head -1)"));
if gcc_lib <> "" then Linker_Flag = Linker_Flag + " -L" + gcc_lib; end
if isdir("/opt/homebrew/lib/gcc/current") then
    Linker_Flag = Linker_Flag + " -L/opt/homebrew/lib/gcc/current";
end

disp("CFLAGS : " + C_Flags);
disp("LDFLAGS: " + Linker_Flag);

Function_Names = [
    "finversion",   "sci_fin_version",   "csci6";
];
Files = [
    "sci_fin_version.cpp";
];

tbx_build_gateway("sciFinance", Function_Names, Files, gw_dir, [], Linker_Flag, C_Flags);
tbx_build_gateway_loader(["cpp"], fullfile(tb_dir, "sci_gateway"));

tbx_builder_macros(tb_dir);
tbx_build_loader(tb_dir);

disp("=== sciFinance build OK ===");
quit;
```

- [ ] **Step 8: Create `etc/sciFinance.start`**

```scilab
// sciFinance.start -- run by loader.sce when the toolbox loads.
mprintf("Start sciFinance\n");

// get_absolute_file_path() is valid HERE because this file is the one being
// exec'd. It is NOT valid on any other path -- use fileparts() for those.
etc_tlbx  = get_absolute_file_path("sciFinance.start");
root_tlbx = fullfile(etc_tlbx, "..");

// Macros
pathmacros = fullfile(root_tlbx, "macros");
if isfile(fullfile(pathmacros, "lib")) then
    load(fullfile(pathmacros, "lib"));
end

// Gateways
verboseMode = ilib_verbose();
ilib_verbose(0);
libgw = fullfile(root_tlbx, "sci_gateway", "loader_gateway.sce");
if isfile(libgw) then
    exec(libgw, -1);
end
ilib_verbose(verboseMode);

// Demos
pathdemos = pathconvert(fullfile(root_tlbx, "demos", "sciFinance.dem.gateway.sce"), %f, %t);
if isfile(pathdemos) then
    add_demo("sciFinance", pathdemos);
end

clear etc_tlbx root_tlbx pathmacros verboseMode libgw pathdemos;
```

- [ ] **Step 9: Create `etc/sciFinance.quit`**

```scilab
// sciFinance.quit -- nothing to tear down. The gateway holds no global state:
// every QuantLib object is constructed and destroyed inside a single call.
```

- [ ] **Step 10: Build**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f build_macos.sce 2>&1 | tail -20
```
Expected: `=== sciFinance build OK ===`, and `loader.sce` now exists.

If the link fails with `library 'emutls_w' not found`, the gcc runtime `-L` did not resolve — check `ls /opt/homebrew/lib/gcc/current`.

- [ ] **Step 11: Run the test to verify it passes**

```bash
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: `PASS  finversion() == 1.42.1 (QuantLib is linked)`, `0 failure(s)`, `rc=0`.

- [ ] **Step 12: Confirm the dylib really links QuantLib (not a stale/static surprise)**

```bash
otool -L sci_gateway/cpp/libsciFinance.dylib | grep -i quantlib
```
Expected: a line naming `libQuantLib...dylib`. If the path points into `/tmp` or a build dir, stop — that is a reboot time-bomb (the bonmin lesson); fix it with `install_name_tool -change` before proceeding.

- [ ] **Step 13: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
git init 2>/dev/null; true
cat > .gitignore <<'EOF'
loader.sce
macros/lib
macros/*.bin
sci_gateway/loader_gateway.sce
sci_gateway/cpp/*.dylib
sci_gateway/cpp/*.o
sci_gateway/cpp/libsci*
EOF
git add -A
git commit -m "sciFinance: toolbox skeleton + direct QuantLib link + gateway exception boundary

finversion() returns the linked QuantLib version (1.42.1), proving the
pkg-config link and gateway registration before any real numerics land.
SCIFIN_TRY/SCIFIN_CATCH make the C++ exception boundary structural: an
uncaught QuantLib throw across the C boundary is UB, so no gateway may
omit it."
```

---

### Task 2: Date bridge + the `fin_isbusday` gateway

Proves: the `[y,m,d]` date bridge, the calendar lookup, the **vectorised batch** pattern (build the QuantLib object once, reuse it across N rows), and — via a deliberately bad calendar name — that the exception boundary actually catches a QuantLib throw instead of crashing.

**Files:**
- Create: `sciFinance/sci_gateway/cpp/fin_date_bridge.hpp`
- Create: `sciFinance/sci_gateway/cpp/sci_fin_isbusday.cpp`
- Modify: `sciFinance/build_macos.sce` (register the new gateway)
- Test: `sciFinance/tests/run_tests.sce`

**Interfaces:**
- Consumes: `SCIFIN_TRY` / `SCIFIN_CATCH(fname)` from `sciFinance_gw.hpp` (Task 1).
- Produces:
  - `scifin::toDate(double y, double m, double d)` → `QuantLib::Date`
  - `scifin::calendarByName(const std::string&)` → `QuantLib::Calendar` (throws `std::invalid_argument` on an unknown name)
  - Scilab gateway `fin_isbusday(ymd, calName)` — `ymd` is an N×3 double matrix `[y m d]`, `calName` a string; returns an **N×1 boolean** column. Known calendars: `"TARGET"`, `"US"`, `"UK"`, `"None"`.

- [ ] **Step 1: Write the failing test**

Append to `tests/run_tests.sce`, immediately **before** the `mprintf("\n%d failure(s)\n", failures);` line:

```scilab
mprintf("\n--- fin_isbusday (gateway) ---\n");
// Goldens. Jan 1 (Thu), May 1 (Fri) and Dec 25 (Fri) are WEEKDAYS that are
// TARGET holidays -- they prove the calendar is really consulted, and that a
// weekend-only check cannot pass this test by accident.
ymd = [2026 1  1        // Thursday, New Year        -> holiday
       2026 5  1        // Friday,   Labour Day      -> holiday
       2026 7 11        // Saturday                  -> weekend
       2026 7 12        // Sunday                    -> weekend
       2026 7 13        // Monday                    -> BUSINESS DAY
       2026 12 25];     // Friday,   Christmas       -> holiday
expected = [%f; %f; %f; %f; %t; %f];

b = fin_isbusday(ymd, "TARGET");
check(and(size(b) == [6 1]), "fin_isbusday returns an N x 1 column");
check(type(b) == 4, "fin_isbusday returns booleans");
check(and(b == expected), "TARGET goldens (weekday holidays included)");

// Vectorisation parity: the batch must equal row-by-row calls.
looped = [];
for i = 1:size(ymd, 1)
    looped = [looped; fin_isbusday(ymd(i, :), "TARGET")];
end
check(and(looped == b), "vectorised == looped (parity)");

// Calendars differ: 2026-07-04 (US Independence Day) falls on a Saturday, so
// use Thanksgiving 2026-11-26 (Thursday) -- a US holiday, an ordinary TARGET
// business day. This proves calendarByName actually switches calendars.
thx = [2026 11 26];
check(fin_isbusday(thx, "TARGET") == %t, "2026-11-26 is a TARGET business day");
check(fin_isbusday(thx, "US") == %f, "2026-11-26 is a US holiday (Thanksgiving)");

mprintf("\n--- exception boundary ---\n");
// THE crash test: an unknown calendar makes C++ throw. If the boundary is
// missing, this kills the process instead of returning an error code.
ierr = execstr("dummy = fin_isbusday(ymd, ""Atlantis"");", "errcatch");
check(ierr <> 0, "unknown calendar raises a Scilab error (did not crash)");
check(grep(lasterror(), "Atlantis") <> [], "the error message names the bad calendar");
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: `rc=1`, with failures on the `fin_isbusday` checks — the function is undefined.

- [ ] **Step 3: Create `sci_gateway/cpp/fin_date_bridge.hpp`**

```cpp
#ifndef FIN_DATE_BRIDGE_HPP
#define FIN_DATE_BRIDGE_HPP

// The one place that knows how a Scilab date becomes a QuantLib::Date.
//
// Dates cross the gateway boundary as [year, month, day] integer triples, which
// the macro layer produces with datevec(). They are NEVER passed as serial day
// numbers: Scilab's datenum epoch is not MATLAB's (datenum(2026,7,13) = 740176),
// so a serial would silently mean a different day. A y/m/d triple has no epoch.

#include <string>
#include <stdexcept>

#include <ql/time/date.hpp>
#include <ql/time/calendar.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/time/calendars/unitedstates.hpp>
#include <ql/time/calendars/unitedkingdom.hpp>
#include <ql/time/calendars/nullcalendar.hpp>

namespace scifin
{

// Throws QuantLib::Error on an impossible date (month 13, Feb 30, ...); the
// gateway's exception boundary turns that into a clean Scilab error.
inline QuantLib::Date toDate(double y, double m, double d)
{
    return QuantLib::Date(static_cast<QuantLib::Day>(d),
                          static_cast<QuantLib::Month>(static_cast<int>(m)),
                          static_cast<QuantLib::Year>(y));
}

// Unknown names throw -- deliberately. That throw is what proves the gateway's
// exception boundary is wired up (tests/run_tests.sce calls this with "Atlantis").
inline QuantLib::Calendar calendarByName(const std::string& name)
{
    if (name == "TARGET")                              return QuantLib::TARGET();
    if (name == "US" || name == "UnitedStates")        return QuantLib::UnitedStates(QuantLib::UnitedStates::Settlement);
    if (name == "UK" || name == "UnitedKingdom")       return QuantLib::UnitedKingdom();
    if (name == "None" || name == "NullCalendar")      return QuantLib::NullCalendar();

    throw std::invalid_argument("unknown calendar '" + name +
                                "' (known: TARGET, US, UK, None)");
}

} // namespace scifin

#endif // FIN_DATE_BRIDGE_HPP
```

- [ ] **Step 4: Create `sci_gateway/cpp/sci_fin_isbusday.cpp`**

```cpp
// fin_isbusday(ymd, calendarName) -> N x 1 boolean
//
//   ymd          N x 3 double matrix, [year month day] per row (from datevec)
//   calendarName string: "TARGET" | "US" | "UK" | "None"
//
// The vectorised-batch pattern every sciFinance gateway follows: build the
// QuantLib object ONCE, then loop the rows against it. That is the whole reason
// the facade exists -- a per-element Scilab->QuantLib round trip would be slow
// and would leak object lifetimes into user code.
#include "sciFinance_gw.hpp"
#include "fin_date_bridge.hpp"

#include <string>
#include <vector>

extern "C" {
#include "charEncoding.h"   // wide_string_to_UTF8
#include "sci_malloc.h"     // FREE
}

extern "C" int sci_fin_isbusday(scilabEnv env, int nin, scilabVar* in, int nopt,
                                scilabOpt opt, int nout, scilabVar* out)
{
    const char fname[] = "fin_isbusday";
    SCIFIN_TRY

    if (nin != 2)
    {
        Scierror(999, "%s: Wrong number of input arguments: %d expected.\n", fname, 2);
        return 1;
    }
    if (nout > 1)
    {
        Scierror(999, "%s: Wrong number of output arguments: %d expected.\n", fname, 1);
        return 1;
    }
    if (!scilab_isDouble(env, in[0]) || !scilab_isMatrix2d(env, in[0]))
    {
        Scierror(999, "%s: Wrong type for input argument #1: an N x 3 [y m d] real matrix expected.\n", fname);
        return 1;
    }
    if (!scilab_isString(env, in[1]))
    {
        Scierror(999, "%s: Wrong type for input argument #2: a calendar name string expected.\n", fname);
        return 1;
    }

    double* ymd = nullptr;
    int rows = 0;
    int cols = 0;
    scilab_getDoubleArray(env, in[0], &ymd);
    scilab_getDim2d(env, in[0], &rows, &cols);

    if (cols != 3)
    {
        Scierror(999, "%s: Wrong size for input argument #1: N x 3 [y m d] expected, got %d x %d.\n",
                 fname, rows, cols);
        return 1;
    }
    if (rows == 0)
    {
        out[0] = scilab_createBooleanMatrix2d(env, 0, 0);
        return 0;
    }

    wchar_t* wname = nullptr;
    scilab_getString(env, in[1], &wname);
    char* cname = wide_string_to_UTF8(wname);
    const std::string calName(cname ? cname : "");
    FREE(cname);

    // Built once, reused for every row. Throws on an unknown name.
    const QuantLib::Calendar cal = scifin::calendarByName(calName);

    std::vector<int> flags(static_cast<size_t>(rows), 0);
    for (int i = 0; i < rows; ++i)
    {
        // Scilab matrices are COLUMN-major: element (i, c) is ymd[i + c*rows].
        const QuantLib::Date d = scifin::toDate(ymd[i],
                                                ymd[i + rows],
                                                ymd[i + 2 * rows]);
        flags[static_cast<size_t>(i)] = cal.isBusinessDay(d) ? 1 : 0;
    }

    out[0] = scilab_createBooleanMatrix2d(env, rows, 1);
    scilab_setBooleanArray(env, out[0], flags.data());
    return 0;

    SCIFIN_CATCH(fname)
}
```

- [ ] **Step 5: Register the gateway in `build_macos.sce`**

Replace the `Function_Names` / `Files` block:

```scilab
Function_Names = [
    "finversion",   "sci_fin_version",   "csci6";
    "fin_isbusday", "sci_fin_isbusday",  "csci6";
];
Files = [
    "sci_fin_version.cpp";
    "sci_fin_isbusday.cpp";
];
```

- [ ] **Step 6: Rebuild and run the test to verify it passes**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
SCILAB=/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab
$SCILAB -nwni -f build_macos.sce 2>&1 | tail -5
$SCILAB -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: every check `PASS`, `0 failure(s)`, `rc=0`. In particular `unknown calendar raises a Scilab error (did not crash)` — that line passing *is* the exception boundary working.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "sciFinance: date bridge + vectorised fin_isbusday gateway

Dates cross as [y m d] triples, never serials -- Scilab's datenum epoch is
not MATLAB's (datenum(2026,7,13)=740176), so a serial would silently mean a
different day. The calendar is built once and reused across the batch.
Goldens use weekday holidays (Jan 1 Thu, May 1 Fri, Dec 25 Fri) so a
weekend-only implementation cannot pass by accident, and an unknown calendar
name exercises the exception boundary end to end."
```

---

### Task 3: The `isbusday` macro (user-facing `datetime` path)

Proves: the mlist unwrap. This is the layer that makes the toolbox usable — `isbusday(datetime(...))` — and it is the only place that knows `datetime` is an mlist.

**Files:**
- Create: `sciFinance/macros/isbusday.sci`
- Test: `sciFinance/tests/run_tests.sce`

**Interfaces:**
- Consumes: `fin_isbusday(ymd, calName)` (Task 2).
- Produces: `b = isbusday(d)` / `b = isbusday(d, cal)` — `d` is a `datetime` (any shape) **or** an N×3 `[y m d]` matrix; `cal` is a calendar name string, default `"TARGET"`. Returns an N×1 boolean column.

- [ ] **Step 1: Write the failing test**

Append to `tests/run_tests.sce`, before the `failures` summary:

```scilab
mprintf("\n--- isbusday (macro, datetime path) ---\n");
d = datetime(2026, [1; 5; 7; 7; 7; 12], [1; 1; 11; 12; 13; 25]);
check(and(isbusday(d) == [%f; %f; %f; %f; %t; %f]), "isbusday(datetime) matches the goldens");
check(isbusday(datetime(2026, 7, 13)) == %t, "isbusday(scalar datetime)");
check(isbusday(datetime(2026, 11, 26), "US") == %f, "isbusday(datetime, ""US"") honours the calendar");

// The macro must also accept a raw [y m d] matrix (no datetime round trip).
check(isbusday([2026 7 13]) == %t, "isbusday accepts a raw [y m d] row");

// Default calendar is TARGET.
check(isbusday(datetime(2026, 11, 26)) == isbusday(datetime(2026, 11, 26), "TARGET"), ..
      "default calendar is TARGET");
```

- [ ] **Step 2: Run it to verify it fails**

```bash
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: `rc=1` — `isbusday` is undefined.

- [ ] **Step 3: Create `macros/isbusday.sci`**

```scilab
// Copyright (C) 2026 - Jose Moya
// This file is released under the GPL-2.0 license.

function b = isbusday(d, cal)
    // b = isbusday(d)         business-day flags, TARGET calendar
    // b = isbusday(d, cal)    ... on calendar cal ("TARGET" | "US" | "UK" | "None")
    //
    // d is a datetime, or a raw N x 3 [year month day] matrix.
    //
    // Examples:
    //   isbusday(datetime(2026, 7, 13))            // %t  (a Monday)
    //   isbusday(datetime(2026, 11, 26), "US")     // %f  (Thanksgiving)

    if argn(2) < 2 then
        cal = "TARGET";
    end
    if type(cal) <> 10 then
        error(msprintf(_("%s: Wrong type for input argument #%d: a string expected.\n"), "isbusday", 2));
    end

    // datetime is an mlist -- it cannot cross into the gateway. Decompose it into
    // plain [y m d] numbers here; datevec() gives N x 6 [y m d h mi s].
    if typeof(d) == "datetime" then
        v   = datevec(d);
        ymd = v(:, 1:3);
    elseif type(d) == 1 then
        ymd = d;
        if size(ymd, 2) <> 3 then
            error(msprintf(_("%s: Wrong size for input argument #%d: an N x 3 [y m d] matrix expected.\n"), "isbusday", 1));
        end
    else
        error(msprintf(_("%s: Wrong type for input argument #%d: a datetime or an N x 3 [y m d] matrix expected.\n"), "isbusday", 1));
    end

    b = fin_isbusday(ymd, cal);
endfunction
```

- [ ] **Step 4: Rebuild the macros and run the test to verify it passes**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
SCILAB=/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab
$SCILAB -nwni -f build_macos.sce 2>&1 | tail -3
$SCILAB -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: all `isbusday` checks `PASS`, `0 failure(s)`, `rc=0`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "sciFinance: isbusday macro -- the datetime (mlist) unwrap

datetime is an mlist and can never be passed to a C gateway. The macro layer
decomposes it with datevec() into [y m d] numbers; the gateway never parses an
mlist and the macro never does numerics."
```

---

### Task 4: The hardening gates (leak, adversarial, repeat)

Proves: the claims the spec makes about safety. These are the checks that catch the bug classes that actually bit this project — leaks, missing NULL/type guards, and *intermittent* crashes that a single run hides.

**Files:**
- Create: `sciFinance/tests/leakcheck.sce`
- Modify: `sciFinance/tests/run_tests.sce` (adversarial block)

**Interfaces:**
- Consumes: `isbusday` (Task 3), `fin_isbusday` (Task 2).
- Produces: no new API — two executable gates.

- [ ] **Step 1: Write the failing adversarial test**

Append to `tests/run_tests.sce`, before the `failures` summary:

```scilab
mprintf("\n--- adversarial arguments (must error, must NOT crash) ---\n");
// Each of these must come back as a Scilab error. Reaching the end of this
// block at all is the proof: a segfault would have taken the process with it.
bad = [
    "dummy = fin_isbusday();"                          , "no arguments"
    "dummy = fin_isbusday([2026 7 13]);"               , "one argument"
    "dummy = fin_isbusday([2026 7 13], ""TARGET"", 1);", "three arguments"
    "dummy = fin_isbusday(""nope"", ""TARGET"");"      , "arg #1 is a string"
    "dummy = fin_isbusday([2026 7 13], 42);"           , "arg #2 is a number"
    "dummy = fin_isbusday([2026 7], ""TARGET"");"      , "arg #1 is N x 2"
    "dummy = fin_isbusday([2026 7 13], ""Atlantis"");" , "unknown calendar"
    "dummy = fin_isbusday([2026 13 1], ""TARGET"");"   , "month 13"
    "dummy = fin_isbusday([2026 2 30], ""TARGET"");"   , "Feb 30"
    "dummy = isbusday(list(1, 2));"                    , "isbusday(list)"
    "dummy = isbusday(datetime(2026,7,13), 42);"       , "isbusday cal is a number"
];
for k = 1:size(bad, 1)
    ierr = execstr(bad(k, 1), "errcatch");
    check(ierr <> 0, "rejected: " + bad(k, 2));
end

// An empty date set is legal and must return empty, not error and not crash.
check(isempty(fin_isbusday(zeros(0, 3), "TARGET")), "empty N x 3 returns empty");

mprintf("\n--- survived every adversarial call ---\n");
```

- [ ] **Step 2: Run it**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: **all `PASS`, `rc=0`** — the guards written in Task 2 already cover these. If any line *crashes* Scilab rather than failing a check, that is a real bug: fix the guard in `sci_fin_isbusday.cpp` before continuing. If any check merely FAILs, add the missing guard.

- [ ] **Step 3: Write the leak check — `tests/leakcheck.sce`**

```scilab
// 10,000 calls must not grow RSS. The gateway allocates a QuantLib Calendar and
// a std::vector per call; if either leaked, this is where it shows up.
mode(-1); lines(0);
here = get_absolute_file_path("leakcheck.sce");
exec(fullfile(here, "..", "loader.sce"), -1);

function r = rss_kb()
    r = evstr(stripblanks(unix_g("ps -o rss= -p " + string(getpid()))));
endfunction

ymd = [2026 7 13];

// Warm-up: QuantLib builds each calendar's holiday tables lazily on first use,
// and Scilab grows its own pools. Measure the STEADY state, not the first touch.
for i = 1:500
    dummy = fin_isbusday(ymd, "TARGET");
end

r0 = rss_kb();
for i = 1:10000
    dummy = fin_isbusday(ymd, "TARGET");
end
r1 = rss_kb();

growth = r1 - r0;
mprintf("RSS before: %d kB\nRSS after : %d kB\ngrowth    : %d kB over 10000 calls\n", r0, r1, growth);

// A real per-call leak of even one Calendar would add megabytes over 10k calls.
if growth > 1024 then
    mprintf("FAIL: RSS grew %d kB -- suspected leak\n", growth);
    quit(1);
end
mprintf("PASS: RSS flat\n");
quit(0);
```

- [ ] **Step 4: Run the leak check**

```bash
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/leakcheck.sce; echo "rc=$?"
```
Expected: `PASS: RSS flat`, `rc=0`, growth well under 1024 kB.

If it fails, the leak is a missing `FREE(cname)` or a raw `new` — find it before moving on. (RAII is the design: `std::vector` and a stack `Calendar` free themselves; the only manual free in the gateway is the `wide_string_to_UTF8` result.)

- [ ] **Step 5: Run everything 10× — the intermittent-crash gate**

A single green run means nothing for memory bugs; the quadprog SIGSEGV in this project showed up 2 runs in 6.

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
SCILAB=/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab
fails=0
for i in $(seq 1 10); do
  $SCILAB -nwni -f tests/run_tests.sce >/dev/null 2>&1 || { fails=$((fails+1)); echo "run $i: FAILED"; }
  $SCILAB -nwni -f tests/leakcheck.sce >/dev/null 2>&1 || { fails=$((fails+1)); echo "run $i: LEAK FAILED"; }
done
echo "=== $fails failure(s) across 10 repeats ==="
```
Expected: `=== 0 failure(s) across 10 repeats ===`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "sciFinance: hardening gates -- leak proof, adversarial args, 10x repeat

10k calls hold RSS flat (RAII: the only manual free is the UTF-8 conversion).
Eleven adversarial calls must all raise Scilab errors and none may segfault --
reaching the end of that block is the proof. The 10x loop is not paranoia: the
quadprog SIGSEGV this project already fixed only appeared in 2 runs of 6."
```

---

### Task 5: The demo — which *is* the acceptance test

Proves: the demo pipeline. The demo must appear in the Demonstrations window **and** run headless against goldens. It also carries the guard for the bug that emptied the whole Demonstrations window: a `.dem.gateway.sce` listing a file that does not exist.

**Files:**
- Create: `sciFinance/demos/sciFinance.dem.gateway.sce`
- Create: `sciFinance/demos/calendars.dem.sce`
- Modify: `sciFinance/tests/run_tests.sce` (demo-tree guard)

**Interfaces:**
- Consumes: `isbusday` (Task 3).
- Produces: a demo registered by `etc/sciFinance.start` (Task 1 already calls `add_demo`).

- [ ] **Step 1: Write the failing demo-tree guard**

Append to `tests/run_tests.sce`, before the `failures` summary:

```scilab
mprintf("\n--- demo tree ---\n");
// THE guard. A .dem.gateway.sce that names a file which does not exist does not
// just break its own demo -- it empties the ENTIRE Demonstrations window (the
// bug fixed in scilab 20796ca5bdf, triggered by FOSSEE's stale demo list). CI
// must catch that here, not a user staring at a blank window.
global subdemolist;
subdemolist = [];
gw = fullfile(root, "demos", "sciFinance.dem.gateway.sce");
check(isfile(gw), "demos/sciFinance.dem.gateway.sce exists");

ierr = execstr("exec(gw, -1);", "errcatch");
check(ierr == 0, "the demo gateway executes cleanly");
check(~isempty(subdemolist), "the demo gateway populates subdemolist");

for k = 1:size(subdemolist, 1)
    check(isfile(subdemolist(k, 2)), "demo file exists: " + subdemolist(k, 1));
end

// Every demo must actually RUN headless -- a demo that errors is a failed test.
for k = 1:size(subdemolist, 1)
    ierr = execstr("exec(subdemolist(k, 2), -1);", "errcatch");
    check(ierr == 0, "demo runs headless: " + subdemolist(k, 1));
end
clearglobal subdemolist;
```

- [ ] **Step 2: Run it to verify it fails**

```bash
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: `rc=1`, `FAIL  demos/sciFinance.dem.gateway.sce exists`.

- [ ] **Step 3: Create `demos/sciFinance.dem.gateway.sce`**

```scilab
// sciFinance demo gateway.
//
// RULE: every filename below MUST exist. A missing file does not merely break
// one demo -- it empties the entire Demonstrations window. tests/run_tests.sce
// asserts this on every run; do not add an entry before adding the file.

demopath = get_absolute_file_path("sciFinance.dem.gateway.sce");

subdemolist = [
    "Business calendars", "calendars.dem.sce"
];

subdemolist(:, 2) = demopath + subdemolist(:, 2);
```

- [ ] **Step 4: Create `demos/calendars.dem.sce`**

```scilab
// sciFinance -- business calendars.
//
// Runs in the Demonstrations window AND headless in CI, so it must assert its
// own goldens and must never call halt() (which would hang CI) or stacksize()
// (removed in Scilab 6).

mprintf("\n=== sciFinance: business calendars ===\n\n");

// A week around Independence Day 2026 (July 4 falls on a Saturday).
d = datetime(2026, 7, (1:10)');

mprintf("Date          weekday        TARGET   US\n");
mprintf("----------------------------------------\n");
bt = isbusday(d, "TARGET");
bu = isbusday(d, "US");
names = ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"];
for i = 1:size(d, "*")
    v = datevec(d(i));
    mprintf("%4d-%02d-%02d     %s            %s        %s\n", ..
            v(1), v(2), v(3), names(weekday(d(i))), ..
            string(bt(i)), string(bu(i)));
end

// The two calendars disagree, and that is the point of having both.
mprintf("\nThanksgiving 2026-11-26 (a Thursday):\n");
t = datetime(2026, 11, 26);
mprintf("  TARGET business day? %s\n", string(isbusday(t, "TARGET")));
mprintf("  US     business day? %s\n", string(isbusday(t, "US")));

// Goldens -- this demo is also the acceptance test.
assert_checktrue(isbusday(datetime(2026, 7, 13)) == %t);   // Monday
assert_checktrue(isbusday(datetime(2026, 7, 11)) == %f);   // Saturday
assert_checktrue(isbusday(datetime(2026, 1,  1)) == %f);   // New Year (a Thursday)
assert_checktrue(isbusday(t, "TARGET") == %t);
assert_checktrue(isbusday(t, "US")     == %f);

mprintf("\nAll calendar goldens OK.\n");
```

Note: `weekday()` is used only for the printed table. If it is not available in this Scilab build, drop the column rather than adding a dependency — the goldens below it are what the test asserts.

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab -nwni -f tests/run_tests.sce; echo "rc=$?"
```
Expected: `PASS  demo file exists: Business calendars`, `PASS  demo runs headless: Business calendars`, `0 failure(s)`, `rc=0`.

- [ ] **Step 6: Verify it in the Demonstrations window (the GUI half of the acceptance test)**

Per the standing rule, kill any running Scilab first — exactly one instance, the newest build.

```bash
pkill -f 'scilab-bin' 2>/dev/null; true
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./bin/scilab &
```
Then in the Scilab console:
```scilab
exec('/Users/josemoya/Projects/SciLabProjects/sciFinance/loader.sce');
demo_gui();
```
Expected: the Demonstrations window opens **with its full tree intact** (every other toolbox still listed — this is the regression that matters), containing a **sciFinance → Business calendars** entry that runs and prints the table.

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/sciFinance
git add -A
git commit -m "sciFinance: calendars demo -- the demo IS the acceptance test

The demo asserts its own goldens, so it fails CI headless and shows correct
numbers in the Demonstrations window from one source. run_tests.sce also
guards the tree: every file named in the .dem.gateway.sce must exist, because
one missing name empties the whole Demonstrations window."
```

- [ ] **Step 8: Push both remotes**

```bash
git remote -v   # expect SSH for GitHub (git@github.com:jlmoya/...), NOT https
git push -u origin main
git push gitlab main 2>/dev/null || true
```

If GitHub rejects with 403, the osxkeychain entry is handing it the Energy Transfer work account. Convert the remote to SSH (`git remote set-url origin git@github.com:jlmoya/sciFinance.git`) — **do not edit or erase the `github.com` keychain entry**, that would break real work authentication.

---

## P0 Exit Criteria

P0 is done — and P1 may start — when all of these hold:

| Gate | Command | Expected |
|---|---|---|
| Builds | `scilab -nwni -f build_macos.sce` | `=== sciFinance build OK ===` |
| Links QuantLib | `otool -L sci_gateway/cpp/libsciFinance.dylib \| grep -i quantlib` | a `libQuantLib` line, no `/tmp` path |
| Goldens + parity | `scilab -nwni -f tests/run_tests.sce` | `0 failure(s)`, rc=0 |
| Exception boundary | (in `run_tests.sce`) | `PASS  unknown calendar raises a Scilab error (did not crash)` |
| No leak | `scilab -nwni -f tests/leakcheck.sce` | `PASS: RSS flat`, rc=0 |
| Adversarial | (in `run_tests.sce`) | all 11 rejected, process survives |
| 10× repeat | the loop in Task 4 Step 5 | `0 failure(s) across 10 repeats` |
| Demo (headless) | (in `run_tests.sce`) | `PASS  demo runs headless` |
| Demo (GUI) | `demo_gui()` | full tree intact + sciFinance entry runs |

---

## Roadmap: P1–P6

Each gets its own plan, written once P0 is green — because each reuses the patterns P0 proves (`SCIFIN_TRY`/`SCIFIN_CATCH`, `scifin::toDate`, `calendarByName`, the build-once-loop-many batch, and the demo-as-test harness).

| Phase | Deliverable | Reuses from P0 |
|---|---|---|
| **P1** | `fin_calendar`: `holidays`, `busdays`, `busdayadj`, `busdayoffset`, `yearfrac` (day-count conventions) | date bridge, calendar lookup |
| **P2** | `fin_options`: `blsprice`, `blsdelta`/`blsgamma`/`blsvega`/`blstheta`/`blsrho`, `blsimpv` — on QuantLib's free functions `blackFormula` / `blackFormulaImpliedStdDev`, so no object graph is built. Goldens already fixed by the spec: `npv=10.450584, delta=0.636831, vega=37.524035` | exception boundary, batch loop |
| **P3** | W2 data spine: `movmean`/`movstd`/`movsum`/`movmax`, `timerange`, `lag`, `resample` (built here, then upstreamed to core `timeseries`); plus `tick2ret`, `ret2tick`, `movavg`, `emaavg`, `maxdrawdown` | pure macros — no gateway |
| **P4** | `fin_bonds` + `fin_curve`: `bndprice`, `bndyield`, `bnddur`; a **curve is an mlist of data**, reconstituted per call — never a handle | the "curves are data" rule |
| **P5** | `fin_mc`: `gbmpaths(..., "sobol"\|"pseudo")`, `mcamerican` (Longstaff-Schwartz). Measured: 1,000,000 paths in 0.052 s (price 10.4686 vs BS 10.4506) | batch loop, leak gate |
| **P6** | W3 portfolio + risk: `portopt` / efficient frontier on **FOSSEE `quadprog`**, plus `VaR` / `ES` | FOSSEE dependency, demos |

Known gaps logged, not in scope: `parallel_run` / `gpuArray` (multicore + GPU Monte Carlo).

---

## Self-Review

**Spec coverage (P0 scope).** The spec's P0 is "skeleton + QuantLib link + try/catch harness + `isbusday` end-to-end + its demo." Task 1 covers skeleton + link, Task 2 the harness (and proves it fires), Tasks 2–3 `isbusday` end to end, Task 5 the demo. The spec's five-part validation bar maps to: goldens (Task 2 Step 1), vectorisation parity (Task 2 Step 1), leak proof (Task 4 Step 3), adversarial args (Task 4 Step 1), 10× repeats (Task 4 Step 5). The three architecture rules — no SWIG pointer, curves-as-data, mandatory try/catch — are in Global Constraints; the first and third are enforced by P0's code, and curves-as-data is carried into P4 where curves first appear.

**Placeholder scan.** No TBD/TODO. Every code step contains the complete file or the exact replacement block. The one soft spot is Task 5 Step 4's `weekday()` call, which I flag inline with a concrete fallback (drop the column) rather than leaving it as a guess — it is cosmetic and the asserted goldens beneath it do not depend on it.

**Type consistency.** `fin_isbusday(ymd, calName)` — N×3 double + string → N×1 boolean — is declared identically in Task 2's Interfaces, its C++ signature, the macro that calls it in Task 3, and every test. `scifin::toDate(y, m, d)` and `scifin::calendarByName(name)` are defined in Task 2 Step 3 and used only in Task 2 Step 4. `SCIFIN_TRY`/`SCIFIN_CATCH(fname)` are defined in Task 1 Step 4 and used with the same names in Task 1 Step 5 and Task 2 Step 4. `check(cond, msg)` is defined once in Task 1 Step 1 and used with that signature throughout.

**One gap I closed while reviewing:** the spec's exception-boundary rule says "wrap the body," but a `catch` that `return 1`s leaves the compiler unable to see a return on the fall-through path under `-Wall -Wextra`. `SCIFIN_CATCH` therefore ends with an unreachable `return 1;` — noted in the macro so nobody "cleans it up."
