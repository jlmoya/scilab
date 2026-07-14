# sciFinance — a Scilab-native financial toolbox over QuantLib

Design spec · 2026-07-13 · status: **approved, ready for planning**

## 1. Why this exists

The premise we started from — *"Scilab is far behind MATLAB, we must build a finance library"* — turned out to
be **wrong**, and every correction came from probing the running system rather than trusting the narrative.

What the probes actually showed:

| Assumption | Reality (verified at runtime) |
|---|---|
| Scilab has no modern data model | **`datetime`, `duration`, `table` all exist and compose.** A `table` with a `datetime` column renders correctly. |
| Scilab has no time-indexed table | **`timeseries` already exists** — it *is* MATLAB's `timetable`, with `retime`, `synchronize`, `isregular`, `diff`, `cumsum` |
| We must implement pricing/calendars | **sciQuantLib is already installed** — QuantLib 1.42.1, autoloaded |
| Scilab simulation is too slow | **1,000,000-path Monte Carlo in 52 ms** (vectorized) |

So the gap is **not** the foundation. The gap is the **domain and ergonomic layer**.

sciQuantLib exposes **21,840 symbols and 0 macros**. It is a raw SWIG ABI, not a toolbox. Pricing one European
call costs ~20 lines and 15 object constructions (`new_Date`, `new_Actual365Fixed`, `new_QuoteHandle`,
`new_BlackScholesMertonProcess`, …), it is **scalar** (no vectorised strikes), and **nothing frees the handles**.

> **This project does not build a finance library. It makes the one we already own usable.**

## 2. Architecture

### 2.1 The principle

> **No SWIG pointer ever crosses into user code.**
>
> Facade functions take Scilab natives, loop **in C++**, and return Scilab natives.

This single rule resolves all three hard problems at once:

- **Ergonomics** — 20 lines collapse to one call.
- **Vectorisation** — the loop lives in C++, so one gateway crossing serves an entire batch.
- **Lifetimes** — nothing escapes to be leaked; RAII on the C++ stack is the whole memory story.

### 2.2 Key enabling discovery

QuantLib ships **cheap free-function closed forms**. The 15-object graph is the *SWIG example's* idiom, not
QuantLib's only path:

```
blackFormula(Option::Type, strike, forward, stdDev, discount)   // vanilla pricing, one call
blackFormulaImpliedStdDev(...)                                  // implied vol
Calendar::isBusinessDay / isHoliday / adjust / advance          // cheap, trivially vectorised
```

The facade therefore **picks the cheapest correct QuantLib entry point per job** — closed form for vanilla,
heavy engines only for exotics (built once and reused across the batch).

### 2.3 Deliverable

A new toolbox, **`sciFinance`**, whose native gateway links **`libQuantLib` directly** (`/opt/homebrew/opt/quantlib`,
v1.42.1). It does **not** depend on the SWIG binding at runtime.

sciQuantLib stays installed as the **power-user escape hatch** (all 21,840 symbols remain reachable) but is
**not** the API.

### 2.4 State: curves are data, not handles

Options and calendars are pure functions. Curves are stateful — so how do we hold one without a pointer?

> **Decision: a curve is a Scilab `mlist`** (dates, rates, interpolation, day-count) — pure **data**. The
> gateway **reconstitutes** the QuantLib object inside the call and **reuses it across the whole vectorised
> batch**.

This keeps the no-pointer guarantee *and* is fast: the curve is built **once per call** and amortised over
(say) 10,000 bonds. Vectorisation is not only a speed tactic — it is what makes statelessness affordable.

**Honest trade-off:** an iterative workflow that rebuilds a curve every loop iteration pays construction each
time. Mitigation: batch. If a hot path ever demands it, add an opt-in handle registry **later** — logged, not
built now (YAGNI).

## 3. Components

### W1 — QuantLib facade (native gateway + macros)

Native gateway (C++, RAII, one crossing per call):

| Unit | Functions |
|---|---|
| `fin_calendar` | `isbusday`, `holidays`, `busdays`, `busdayadj`, `busdayoffset`, `yearfrac` (act/360, 30/360, act/act) |
| `fin_options` | `blsprice`, `blsdelta`, `blsgamma`, `blsvega`, `blstheta`, `blsrho`, `blsimpv` |
| `fin_bonds` | `bndprice`, `bndyield`, `bnddur`, `bndconvexity` |
| `fin_curve` | zero/discount curve build + evaluate (from the mlist) |
| `fin_mc` | `gbmpaths(S0,r,q,sigma,T,nsteps,npaths,"sobol"\|"pseudo")` → path matrix; `mcamerican` (Longstaff–Schwartz) |

All option/calendar functions **broadcast**: any argument may be scalar or vector; scalars expand, vectors must
conform.

**Monte Carlo design (deliberate):** generate paths **natively** (fast, and Sobol-capable), let the **payoff stay
in Scilab** — vectorised, where 1M paths cost 52 ms. Native where it must be, open where you want it.

Macro layer (`.sci`): argument checking, defaults, broadcasting, `timeseries` integration (a `timeseries` of
spots in → a `timeseries` out), and composition.

### W2 — Data spine (macros only) — and half of it belongs upstream

`timeseries` already exists. We fill gaps, we do not build a type. The gaps split by ownership:

- **W2a — generic time-series verbs**: `movmean`, `movstd`, `movsum`, `movmax`, `timerange`, `lag`, `resample`.
  These are **not** finance-specific; their natural home is Scilab core (`modules/spreadsheet/macros/`, beside
  `retime`/`synchronize`), and putting them there is real "modernise Scilab" value — it improves the platform
  for everyone, not just us.
  **Decision: build them in `sciFinance` first, then upstream to core once the API has settled.** Rationale:
  the toolbox is self-contained and fast to iterate, whereas touching core means a fork rebuild + genlib on
  every change. Upstreaming is a mechanical move once the signatures stop moving — and it is an explicit
  deliverable, not a someday (see §9 P3).
- **W2b — finance-specific, stays in `sciFinance`**: `tick2ret`, `ret2tick`, `movavg`, `emaavg`, `maxdrawdown`.

### W3 — Portfolio, risk & simulation (macros)

- `portopt` / efficient frontier — **on FOSSEE `quadprog`** (which we hardened; QuantLib does not do
  mean-variance)
- `portstats`, Sharpe, Sortino
- VaR / ES — historical, parametric (Distfun), and Monte Carlo (via `gbmpaths`)
- Quasi-Monte-Carlo via **lowdisc** (Sobol/Halton)

### Engines already owned (nothing to build)

| Need | Engine | Status |
|---|---|---|
| Pricing, calendars, curves, MC | QuantLib 1.42.1 | installed |
| Portfolio optimisation | FOSSEE `quadprog` / `fmincon` | ported + hardened |
| Distributions | Distfun | ported |
| Econometrics (GARCH/ARIMA/VAR) | GROCER | ported |
| Low-discrepancy (Sobol/Halton) | lowdisc | ported |
| Special functions | Specfun | ported |
| Market data | sciDatabase | ported |
| Escape hatch | PIMS → QuantLib-Python, pandas, `arch` | available |

## 4. Data flow

```
user
  → macro        validate args, broadcast, unwrap timeseries/table
  → gateway      ONE crossing: build QuantLib objects once,
                 loop in C++ over the batch, RAII
  → Scilab matrix
  → macro        re-wrap into timeseries/table if the input was one
```

## 5. Error handling

**The hazard that will crash us if ignored:**

> QuantLib throws C++ exceptions. An **uncaught C++ exception crossing a C gateway boundary is undefined
> behaviour** — a hard crash.

**Mandatory on every gateway entry point:**

```cpp
try { /* ... */ }
catch (const std::exception& e) { Scierror(999, "%s: %s\n", fname, e.what()); return 1; }
```

Additional rules:

- **No SWIG pointers ⇒ no leaks by construction** (RAII, stack-scoped).
- **Explicit broadcast rules**: scalars expand; non-conforming vectors are a clean error, never silent truncation.
- Friendly validation in the macro layer; **defensive** validation again in the gateway (never trust the caller).

## 6. Demonstrations — and they *are* the acceptance tests

Each demo is written so it **both** renders in the Scilab **Demonstrations window** (visual, explorable) **and**
runs **headlessly in CI asserting a golden value**. One artifact, two jobs.

Registration follows the standard contract:

```scilab
// etc/sciFinance.start
pathdemos = pathconvert(root_tlbx + "/demos/sciFinance.dem.gateway.sce", %f, %t);
add_demo("sciFinance", pathdemos);
```

### The suite

| Demo | Shows | Asserts |
|---|---|---|
| Options & Greeks | price/greeks vs spot & strike | golden `npv=10.450584`, `delta=0.636831`, `vega=37.524035` |
| Implied-vol smile | `blsimpv` over a strike vector | round-trip `blsimpv(blsprice(σ)) == σ` |
| Business calendars | settlement schedule, holidays highlighted | known TARGET / US holidays |
| Bonds & yield curve | curve build, bond price, duration/convexity | QuantLib reference values |
| **MC: pseudo vs Sobol** | convergence error vs *n*, log-log | quasi-MC converges faster (showcases `lowdisc`) |
| **Efficient frontier** | frontier + asset scatter, via FOSSEE `quadprog` | min-variance portfolio matches the analytic solution |
| VaR / ES | loss distribution, VaR & ES marked | historical ≈ parametric ≈ MC, within tolerance |
| Market-data workflow | `timeseries` → returns → rolling vol → drawdown | `maxdrawdown` vs hand-computed |

### Demo hygiene — rules earned the hard way (2026-07-13)

Each of these caused a real, user-visible failure **today**:

1. **Every filename listed in `.dem.gateway.sce` MUST exist.** FOSSEE listed 14 of 15 demos that did not exist
   (pre-rename names). A **single** missing file made `demo_gui_resolve_path` throw, which aborted the tree
   build and left the **entire Demonstrations window empty — for every toolbox**.
2. **Never call `stacksize()`** — removed in Scilab 6 (memory is dynamic). It killed 7 demos across 5 toolboxes
   with `Undefined variable: stacksize`.
3. **No bare `halt()`** in anything CI must run headlessly — it blocks forever.
4. **CI must build the demo *tree*, not merely run demos** — assert the `sciFinance` node appears with N
   children, every one resolving to a real file. *This check would have caught the empty-window bug instantly.*

## 7. Validation — prove, don't assert

| Check | Bar |
|---|---|
| Goldens | QuantLib reference values (`npv=10.450584`, …) |
| Cross-check | closed form ↔ Monte Carlo (measured: MC 10.4686 vs BS 10.4506 ✓) |
| Vectorisation parity | `f(vector)` ≡ loop of `f(scalar)` |
| **Leak proof** | 10,000 calls with **RSS flat** — the RAII claim is *demonstrated*, not asserted |
| **Adversarial args** | `T=0`, `σ=0`, negative, empty, NaN → **graceful Scilab error, never a segfault** |
| **10× repeat runs** | every suite run ten times |

The last two are not paranoia. sci-ipopt's CWE-120 stack overflow **only** appeared under an adversarial input —
the happy path passed the entire time. And the FOSSEE quadprog crash was **intermittent at 2-in-6**, so a single
green run proves nothing about memory bugs.

## 8. Explicitly out of scope

- A JIT compiler for the Scilab interpreter (wrong battle; vectorisation already gives 1M paths in 52 ms).
- Reimplementing QuantLib (it is the industry standard — better than anything we would write).
- Cloning MATLAB's toolbox catalogue.
- `parallel_run` / `gpuArray` — **logged as the known simulation gap.** Monte Carlo is embarrassingly parallel
  and we currently have no multicore path. Escape hatches: sciTorch (GPU tensors), PIMS (Python), and the parked
  Metal GPU plan. Revisit as its own project.

## 9. Delivery phases

Architecture first, then the crank. The three hard decisions (§2) are the design; once fixed, each function is
mechanical.

| Phase | Content | Proves |
|---|---|---|
| **P0** | Toolbox skeleton + build linking QuantLib + the try/catch gateway harness + one function (`isbusday`) end-to-end + its demo | the architecture works, the exception boundary holds, the demo pipeline renders |
| **P1** | `fin_calendar` complete (calendars, day-counts) | the vectorised-date pattern |
| **P2** | `fin_options` (prices, greeks, implied vol) + goldens + smile demo | the `blackFormula` path + broadcasting |
| **P3** | W2a generic verbs (`movmean`, `timerange`, `lag`, …) in `sciFinance` + W2b finance ops; **then upstream W2a to core** once signatures settle | the data spine |
| **P4** | `fin_bonds` + `fin_curve` (curve-as-mlist) | the stateless-curve decision |
| **P5** | `fin_mc` (`gbmpaths`, Sobol, Longstaff–Schwartz) + convergence demo | the native-paths / Scilab-payoff split |
| **P6** | W3 portfolio + risk (frontier on FOSSEE `quadprog`, VaR/ES) + demos | the engines compose |

**P0 is the real risk-retirement step** — it proves the exception boundary, the RAII/no-leak claim, the build,
and the demo pipeline before any volume of functions is written.

## 10. Open questions (deliberately deferred)

- Curve **handle registry** — only if batching proves insufficient for a hot calibration loop (§2.4).
- `parallel_run` — the multicore Monte Carlo gap (§8). Its own project.

*(The W2a home question is **decided**, not open: build in `sciFinance`, upstream to core once signatures
settle — see §3.)*
