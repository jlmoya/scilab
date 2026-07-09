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

## Remaining (tracked; each needs its own fix + re-verify)

**P1 — real memory hazards (task #90):**
- `differential_equations/IDAManager.cpp:{107,109,519,529,549}` + `CVODEManager.cpp:82` — `getImg() + j*nEq`
  forms `null + offset` when the data is real (imag ptr is null). Benign (never dereferenced when null)
  but UB. Fix: guard the offset (`p ? p + off : nullptr`).
- `patched_sundials/.../sunmatrix_sparse.c:586` — member access / load at **address `0x9`** (a genuine
  wild/uninitialised pointer). Needs investigation of the calling path (likely a degenerate sparse
  matrix in a test); NOT a mechanical fix.

**P2 — float→int conversion UB cluster (task #91):** converting out-of-range / `inf` / `nan` doubles to
integer types is UB and platform-divergent. These are the most behaviour-sensitive — Scilab's
`int8()`…`uint64()` have **documented modular/saturation semantics** that currently ride the C cast, so
each needs an explicit, spec-matching conversion + its own reference tests. Do NOT bulk-cast.
- `elementary_functions/sci_gateway/cpp/sci_int.cpp:114` — the `intN()`/`uintN()` builtins (many values:
  256→u8, 65536→u16, 9.2e18→i64, 34567→i16, …).
- `ast/types/tostring_common.cpp:291` (93×) + `:123` — `inf`/`-inf`/`nan` → `int` while **formatting for
  display** (`%d`); needs a defined sentinel.
- `elem_func_gw.hxx:137`; `string/sci_ascii.cpp:152` + `mput.cpp:163` (UTF-8 byte → signed `char`);
  `cpp/scilab_sprintf.cpp:584`; `c/operations/matrix_power.c:50`; `statistics/CdfBase.c:181`;
  `special_functions/faddeeva.cpp:{1149,1699}`; `sparse/sci_spzeros.cpp:{82,93}` + `sci_sparse.cpp:157`
  (`-1` → `unsigned`).

**P3 — misaligned load (task #92):** covered by the deserializer fix above; re-scan for any siblings.

## How to re-verify
Rebuild the affected module(s) in the instrumented worktree `~/Projects/CLionProjects/scilab-ubsan`
(`CFLAGS/CXXFLAGS += -fsanitize=undefined -fno-omit-frame-pointer`), relink, run the module's
`test_run` under `UBSAN_OPTIONS=log_path=/tmp/ubsan-scilab/report`, and confirm the target lines
disappear from the report set. Driver: scratchpad `ubsan-sweep.sh` / `ubsan-testphase.sh`.
