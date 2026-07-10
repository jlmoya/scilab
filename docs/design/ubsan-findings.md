# UBSan sweep — findings & triage (native track, 2026-07-09)

Phase 2 of the UB-miscompile hardening (after the tree-wide `-fwrapv`, see
`modernization-assessment.md`). Built the fork with `-fsanitize=undefined` on C/C++ (Fortran left
uninstrumented — mixing gcc's libubsan with clang's runtime is unsound), then ran `test_run` over 15
numeric-core modules (`elementary_functions`, `linear_algebra`, `polynomials`, `sparse`, `statistics`,
`randlib`, `special_functions`, `differential_equations`, `cacsd`, `signal_processing`, `integer`,
`string`, `data_structures`, `fileio`, `time`) with `UBSAN_OPTIONS=halt_on_error=0`. ~20 unique UB
sites (the raw report counts double because each site is one library read + one inline-header read).

## Fixed (2026-07-09, verified: the instrumented rebuild reports 0 at these sites)

| Site | UB | Fix |
|------|----|-----|
| `ast/symbol/context.cpp:89` (+ `internal.hxx:374`) — **fired once per process start** | member call on a **null `this`** (`object->getAs<Object>()` with `object==nullptr` in a plain `scope_begin()`) | pass the raw `InternalType*` to `scope_object_begin`, which already does a checked `isObject()`/`getAs` |
| `randlib/sexpo.c:92` | **read `q[8]` out of bounds** of `double[8]` — the exponential RNG's table walk (reachable because `u` is `double` here, unlike the single-precision Fortran original) | bound the loop test `i <= 8 && u > q[i-1]` |
| `core/hash/md5.cpp:294` | **pointer overflow** — `extra + paddedLength - BlockSize` forms the intermediate `extra+120`, past one-past-end of `extra[64]`, before `-BlockSize` returns it in-bounds | parenthesise: `extra + (paddedLength - BlockSize)` |
| `ast/deserializervisitor.hxx:219` + `serializervisitor.hxx:180` | **misaligned 8-byte load/store** — `*(double*)buf` on a non-8-aligned byte stream | `memcpy` (same codegen on arm64, well-defined) |

## Fixed — batch 2 (2026-07-09, tasks #90 P1 + #91 P2)

Correctness verified: the 33-check smoke test passes and the reference suites for the affected modules
are green (`integer` 34/34 **with** ref check — the decisive gate for the behaviour-sensitive int
conversion; `cumsum`/`cumprod` 2/2 with ref check; `string`/`sparse`/`statistics`/`special_functions`/
`output_stream`/`fileio`/`differential_equations`/`ast` clean). Instrumented re-verify (worktree)
confirms the flagged sites no longer report.

**P1 — real memory hazards (task #90):**
| Site | UB | Fix |
|------|----|-----|
| `differential_equations/IDAManager.cpp:{107,109,519,529,549}` + `CVODEManager.cpp:{82,441,461}` | `getImg() + j*nEq` forms `null + offset` when the data is real (imag ptr null) — UB even though never dereferenced | `offsetOrNull(p, off)` helper in `complexHelpers.hxx` (`return p ? p + off : nullptr`) |

**P2 — float→int conversion cluster (task #91):** converting out-of-range / `inf` / `nan` doubles to
integer types is UB and platform-divergent. Scilab's `int8()`…`uint64()` are **documented modular** —
`int8(300)==44`, `uint8(-1)==255`, `int16(1e6)==16960` — so the fix removes UB *and* makes arm64 match
the docs (previously `fcvtz*` saturated, e.g. `int32(2^40)` gave `INT_MAX`; now `0`). No reference test
asserted an out-of-range result, so nothing regressed.
| Site | UB | Fix |
|------|----|-----|
| `integer/sci_gateway/cpp/sci_int.cpp:114` — `intN()`/`uintN()` builtins | out-of-range `double`→narrow int | `#if …__aarch64__` wrap path (was `_M_ARM64`-only): route <0 through `int64_t`, ≥max+1 through `uint64_t`, let the narrowing cast wrap |
| `elementary_functions/includes/elem_func_gw.hxx:137` — `toInt()` (cumsum/cumprod with an int type) | same | shared `dblToInt<IntType>()` helper: nan→0, inf→saturate, finite→wrap (mirrors `convert_int`) |
| `ast/types/tostring_common.cpp:291` (93×) + `:123` | `log10(0)`/`log10(-1)` = `-inf`/`nan` → `int` in display width calc | guard: only take `log10` for a positive argument; the value is pinned finite (dtoa gives `"0"` regardless) |
| `ast/src/c/operations/matrix_power.c:50` | `(int)exp` for an out-of-range/inf/nan exponent | range-guard the cast (`exp >= INT_MIN && exp <= INT_MAX && …`) so huge exponents take the `pow` path |
| `statistics/src/c/CdfBase.c:181` — `checkInteger` | `(int)data[i]` for inf/nan/out-of-range | `!(x >= INT_MIN && x <= INT_MAX) || …` → treat as "not an integer" |
| `string/sci_ascii.cpp:152` | `(char)double` > 127 (value already validated ∈[1,255]) | route through `unsigned char` |
| `fileio/src/cpp/mput.cpp` (`MPUT_CHAR` macro; call sites 139/163) | `(char)*res++` double→signed char > 127 | `(Type)(int)*res++` (defined for all byte values, same output bytes) |
| `output_stream/src/cpp/scilab_sprintf.cpp:{532,584}` — `%lld`/`%llu` of `Int64`/`UInt64` | `(long long)`/`(unsigned long long)` of a non-finite (or, for UInt64, negative) double; `_M_ARM64`-only guard skipped Apple/Linux | convert only when `isfinite`; UInt64 routes negatives through `int64_t` (platform-agnostic) |
| `special_functions/src/cpp/faddeeva.cpp:{1149,1699}` — `erfcx_y100`/`w_im_y100` | `(int)NaN` for `erf/erfc/dawson(%nan)` | `if (y100 != y100) return y100;` before the switch (propagates NaN, which is the correct result) |
| `sparse/sci_spzeros.cpp:{82,93}` + `sci_sparse.cpp:157` | `(unsigned int)` of a negative/nan dim in a validation check | `!(dim >= 0) || …` short-circuits before the cast (still errors cleanly) |

## Remaining (deferred / next batch, tracked)

**Newly surfaced by the batch-2 re-verify** (same float->int class, beyond the original catalog — the
re-run exercised code the first sweep's coverage missed). Next UBSan increment:
- `ast/types/types_tools.cpp:{132,215,331}` — `nan` -> `int`.
- `ast/types/types_addition.hxx:209` — `-1` -> `unsigned char`.
- `ast/types/implicitlist.cpp:316` — out-of-range (`2^64`) -> `int` in an implicit-list (`a:b:c`) bound.

**Deferred:**
- `patched_sundials/.../sunmatrix_sparse.c:586` — member access / load at **address `0x9`** (a genuine
  wild/uninitialised pointer, `arkls_mem->savedJ` in the ARKODE Jacobian-copy path). Needs investigation
  of the calling path (likely a degenerate sparse matrix in a test); NOT a mechanical fix, and it lives
  in vendored SUNDIALS — deferred rather than blind-patched.

**P3 — misaligned load (task #92):** covered by the deserializer fix above; re-scan for any siblings.

## How to re-verify
Rebuild the affected module(s) in the instrumented worktree `~/Projects/CLionProjects/scilab-ubsan`
(`CFLAGS/CXXFLAGS += -fsanitize=undefined -fno-omit-frame-pointer`), relink, run the module's
`test_run` under `UBSAN_OPTIONS=log_path=/tmp/ubsan-scilab/report`, and confirm the target lines
disappear from the report set. Driver: scratchpad `ubsan-sweep.sh` / `ubsan-testphase.sh`.
