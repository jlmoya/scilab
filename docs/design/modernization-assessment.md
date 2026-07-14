# Scilab modernization — the North Star

Status: **2026-07-14.** Opened as *discovery* on 2026-06-27. Discovery is over. Most of the
original north star has landed, and in landing it the goal itself sharpened. This document is the
living map: where we are, where we are going, and why the direction changed.

> **Scope = the ENTIRE application** (user, 2026-06-27): C, C++, Fortran, the `.sci` macro
> language, Java, the build system, every dependency, CI/CD, packaging — and now the *capabilities*
> the language exposes.
>
> **Operating principle** (user, 2026-07-04): there is no "pre-existing errors are not our
> responsibility." On this fork we own the whole application. If something is wrong, we fix it.
> The goal is a bug-free application that performs with excellence.

---

## The North Star, restated

The original north star (2026-06-27) was **infrastructure**: Ant→Maven, kill
`reapply-macos-fixes.sh`, update all libs, make the build just `./configure && make` everywhere.

**That was the right first goal, and it is now mostly done.** The build is plain
`./configure && make`, the band-aid script is deleted, the libraries are current, and the app is
100% native arm64. What is left of the original list is **Ant→Maven** — and honestly, it is the
least valuable item on it.

Finishing that work exposed a better question, and the user asked it directly (2026-07-13):

> *"What is truly missing in Scilab compared to, for example, MATLAB — which is a commercial
> application — or Octave? Thinking about my Financial project, what could we add/update in Scilab
> to add value?"*

That is a different north star. Not *"make the build modern"* but **"make Scilab excellent to
use."** A perfect build system that ships a tool nobody can get work done in has modernized
nothing.

And then the sharpest correction of the whole project, from the user, one message later:

> *"Aren't the business calendars and finance functions given by the sciQuantLib toolbox?"*

**Yes — they are.** sciQuantLib wraps all of QuantLib. But as a raw SWIG ABI: 21,840 exported
symbols, **zero** macros, ~20 lines and 15 object constructions to price one European call,
scalar-only, and nothing frees the handles. The capability was *already ours*. It was simply
unusable.

That reframed the entire program:

> **The job is usually not to build a new capability. It is to make usable the one we already own.**

So the North Star now has **two axes**:

| Axis | Question it answers | State |
|---|---|---|
| **1 — Foundation** | Is it correct, fast, native, and buildable by anyone? | **Largely landed** |
| **2 — Capability** | Can a 2027 user actually get their work done in it? | **Just opened** (sciFinance P0) |

Axis 1 was never the point. It was the *precondition* — you cannot credibly add capability on top
of a codebase that miscompiles `rand()` at `-O2`. That debt is now paid, which is precisely why
Axis 2 can start.

---

## Principles we have earned (not borrowed)

These were each paid for with a real bug. They are the house rules now.

1. **Everything is ours to fix.** No upstream blame, no "that's pre-existing." The `rand()`
   Inf bug had been shipping for years.
2. **A guard you have not seen FAIL is not a guard.** Mutation-test every gate: delete the `FREE`,
   comment out the assertion, strip the validation — and watch the suite go red. Several
   "verified" gates on this project were pure theater until that was done. sciFinance's leak gate
   was mathematically **incapable** of detecting the leak it was named after until it was proven by
   fault injection.
3. **Prove it on the machine, not in the argument.** The Rosetta "problem" was a two-line
   `Info.plist` gap, not an architecture problem. The Quit bug was a stale jar, not the handler —
   and "fixing" the handler on an unverified theory made it strictly worse.
4. **Intermittent means real.** A prior toolbox SIGSEGV appeared in 2 runs out of 6. One green run
   proves nothing about a memory bug. 10× or it did not happen.

---

## Axis 1 — Foundation: the scoreboard

### Landed

| Item | Evidence |
|---|---|
| **Build is plain `./configure && make`** | `reapply-macos-fixes.sh` **deleted**; its 12 fixes folded into `configure.ac` / `Makefile.am`. `fetch-thirdparty.sh` gives a fresh clone a pinned, sha256-verified payload. Audit: `docs/design/build-modernization.md` |
| **The `-O2` UB miscompile class — closed** | `-fwrapv` applied globally (all 3,600 native objects); CI guard (`guard:ub-miscompile`) greps the policy and diffs a `durands` O0/O2 run so the class cannot silently return |
| **UBSan sweep — complete** | P0/P1/P2/P2b/P3 all fixed + pushed (null-`this` member calls, `sexpo.c`/`md5.cpp` OOB, sundials misaligned ptr, the float→int conversion cluster, misaligned double loads) |
| **ASan sweep — complete** | Root-caused the `__tree` bug to a **heap-buffer-overflow in sparse `.^`** (`types_power.cpp`, upstream bug 14500); fixed via a new `Sparse::makeCompressed()` |
| **100% native arm64, no Rosetta** | Nothing ever required it — a missing `LSRequiresNativeExecution` / `LSArchitecturePriority` pair in `Info.plist`. Plus a per-toolbox arch gate (`tbx_arch_check.sci`) that refuses any `.dylib` without an arm64 slice |
| **Our own Vulkan/MoltenVK renderer** | Replaces the abandoned JOGL stack. M1–M8 + sprite clipping, readback-verified, **merged to main** (`d30f75059e5`). Design: `docs/design/vulkan-renderer.md` |
| **macOS app + toolbox manager** | Independent `/Applications/Scilab-2027.0.0.app` (own SCIHOME, configurable JDK) + `tbxManager` GUI with a git-driven catalog |
| **Toolbox catalog verified 50/50** | Every toolbox in the catalog builds, loads, and passes a runs-here smoke test (`tbxVerify` + `tbx-verify-all.sh`) |
| **Toolbox gateway hardening** | Whole-suite C/C++ memory-safety audit across ~17 toolboxes. **sci-ipopt was the only one with a real bug** — the rest were clean |
| **Help browser builds by default** | `make doc` works on JDK 25 (JAXP limits, `_JAVA_OPTIONS` append, per-language chapter registration) |
| **Fork-native CI + releases** | Upstream CI needs Dassault runners and can never run here; the fork has its own pipeline, badges, and releases |

### Still open

| # | Item | Effort | Why it matters (or does not) |
|---|---|---|---|
| 1 | **Ant → Maven** (26 `build.xml`, **0 `pom.xml`**) | L | *The last original north-star item, and the weakest.* Ant works today. Do it for reproducibility and dependency hygiene, not because it is blocking anything. **Deliberately deprioritized below Axis 2.** |
| 2 | **Vulkan renderer portability** — Windows/Linux Layer-1 surface + native loader | M–L | The renderer is macOS-only today. Blocks nobody here; blocks the fork being generally useful |
| 3 | **Java idiom debt** — measured today, essentially unchanged | M | 375 `printStackTrace()`, 12 `finalize()` overrides, ~111 files still using `Vector`, **1** record in 1,505 files. A JDK-25 *target* on a Java-6 *dialect* |
| 4 | **Dead Java dependencies** (~23 EOL libs) | S–XL | flexdock (2008), skinlf (2002), jgoodies, JavaHelp, JGraphX (→ maxGraph, 112 files), JUnit 4, cobertura. FlatLaf 3.4.1 is already bundled as the replacement for the Swing look-and-feel set |
| 5 | **JNI → Panama (FFM)** | L | 29 `native` methods + 61 SWIG JNI files. Best entry: `javasci`/`call_scilab` and the `graphic_objects` hot vertex path |
| 6 | Native long tail | M | Operator-family headers (sub/mul), a sundials wild pointer |
| 7 | **GPU acceleration** (Metal-first fp32 offload of GEMM+FFT) | L | **PARKED.** Design at `docs/design/gpu-acceleration.md` |

**Guardrail (unchanged, and it has held):** do **not** attempt a Swing→JavaFX rewrite. No runtime
mandate, XL cost. The leverage is dead-framework removal and the FFM boundary.

---

## Axis 2 — Capability: what a 2027 user actually needs

This is the new half, and it is where the remaining effort should go.

### The discovery that shaped it

Two assumptions I made were **wrong**, and the user corrected both:

1. **"Scilab has no `datetime`/`table`."** It does. Scilab 2027 already ships `datetime`,
   `duration`, `calendarDuration`, `table`, and **`timeseries`** (MATLAB's `timetable`), all as
   macro-level mlists in `modules/spreadsheet/macros/`, with `retime`, `synchronize`, `readtable`,
   `groupcounts`, `varfun`, `sortrows`, `pivot`, `join`.
2. **"We need a finance library."** We have one — sciQuantLib, wrapping all of QuantLib. It is
   just unusable as a raw SWIG ABI.

**So the gap is rarely the capability. It is the ergonomics, the vectorization, and the lifetimes.**

The genuinely missing verbs, once you look properly: `timerange`, `lag`, `resample`,
`movmean`/`movstd`/`movsum`/`movmax`, `categorical`, `innerjoin` — plus the entire finance layer
(`blsprice`, `bndprice`, `irr`, `npv`, `movavg`, `tick2ret`) and `parallel_run` / `gpuArray`.

### The pattern (proven, and reusable well beyond finance)

**sciFinance** is the first instance, and P0 — the architecture gate — is **complete and pushed**
(`gitlab.com/jlmoya/sciFinance`, 18 commits, 80 checks green). Spec:
`docs/superpowers/specs/2026-07-13-scifinance-design.md`.

The rules it established are the template for *any* "make what we own usable" project:

- **Link the C++ library directly. No SWIG pointer, no handle, ever crosses into user code.** The
  facade takes Scilab natives, loops in C++, returns Scilab natives — which solves ergonomics,
  vectorization, and object lifetimes in one move.
- **Macros validate and unwrap; gateways do numerics.** (`datetime` is an *mlist* and can never
  reach C — the macro decomposes it.)
- **Data, not handles.** A curve is an mlist the gateway reconstitutes per call.
- **The exception boundary is structural**, not a convention: a macro pair every gateway uses,
  which rethrows Scilab's own control-flow exceptions first (so **Ctrl-C still works**) and
  converts anything else to a clean error. An uncaught C++ exception across the C gateway boundary
  is UB.
- **The demos ARE the acceptance tests** — they render in the Demonstrations window *and* run
  headless in CI against goldens.

The Scilab-specific traps this uncovered are recorded, because every one of them silently produces
a green test suite: `quit(n)` **ignores its argument** (always exits 0 — use `exit(n)`); a `global`
must be declared in *every* scope or the counter you increment is not the one you print; `'` inside
a double-quoted string kills the whole script; `add_demo()` is a no-op under `-nwni`.

### Next on this axis

**sciFinance P1–P6**, each its own plan, each written only once its predecessor is green — because
they reuse patterns P0 *proved* rather than patterns P0 *assumed*:

| Phase | Deliverable |
|---|---|
| P1 | `fin_calendar` — `holidays`, `busdays`, `busdayadj`, `busdayoffset`, `yearfrac` |
| P2 | `fin_options` — `blsprice`, the greeks, `blsimpv` (on QuantLib's free `blackFormula` — no object graph) |
| P3 | The **W2 data spine** — `movmean`/`movstd`/`movsum`/`movmax`, `timerange`, `lag`, `resample`. **Built here, then upstreamed into core `timeseries`** |
| P4 | `fin_bonds` + `fin_curve` (curves as data) |
| P5 | `fin_mc` — `gbmpaths` (Sobol/pseudo), Longstaff-Schwartz. Measured: **1,000,000 paths in 0.052 s** |
| P6 | Portfolio + risk — efficient frontier on FOSSEE `quadprog`, VaR/ES |

Note P3: the data-spine verbs are *core* gaps, not finance gaps. Building them inside a toolbox
first and upstreaming once proven is how Axis 2 should generally work — it de-risks a core change
by shipping it somewhere reversible first.

---

## Priority call

**Axis 2 outranks the remaining Axis 1 work**, with one exception.

The foundation is sound: it builds anywhere with `./configure && make`, it is native, the UB class
is closed, the renderer is ours, and the toolbox catalog is verified. Further foundation work
(Ant→Maven, Java idiom cleanup, dead-framework removal) is *hygiene* — real, worth doing, and
invisible to every user.

Capability work is what makes the fork worth having.

**The exception: Vulkan renderer portability (Axis 1, item 2).** Everything else here is macOS-only
by circumstance, but the renderer is macOS-only *by construction* — and it is the piece most likely
to matter to anyone else who ever uses this fork.

---

## Reference

- Build: `docs/design/build-modernization.md`
- Renderer: `docs/design/vulkan-renderer.md`
- GPU (parked): `docs/design/gpu-acceleration.md`
- Packaging + toolbox manager: `docs/design/macos-app-packaging.md`
- Toolbox verification: `docs/design/toolbox-verification.md`
- UBSan findings: `docs/design/ubsan-findings.md`
- sciFinance spec: `docs/superpowers/specs/2026-07-13-scifinance-design.md`
- sciFinance P0 plan: `docs/superpowers/plans/2026-07-13-scifinance-p0.md`
