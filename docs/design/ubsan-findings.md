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
| `integer/sci_gateway/cpp/sci_int.cpp` — `intN()`/`uintN()` builtins | out-of-range `double`→narrow int | shared `doubleToInt<T>()`: nan→0, inf→saturate, else narrow via **exact integer routing** — `int64_t` for `[-2^63, 2^63)`, `uint64_t` for `[2^63, 2^64)`, `fmod` only for `|d| ≥ 2^64`. (Corrected in the P2b pass: the fmod-only version first shipped here was UB for `uint64(-1)` — `2^64-1` rounds up to `2^64`.) |
| `elementary_functions/includes/elem_func_gw.hxx:137` — `toInt()` (cumsum/cumprod with an int type) | same | shared `dblToInt<IntType>()` helper: nan→0, inf→saturate, finite→wrap (mirrors `convert_int`) |
| `ast/types/tostring_common.cpp:291` (93×) + `:123` | `log10(0)`/`log10(-1)` = `-inf`/`nan` → `int` in display width calc | guard: only take `log10` for a positive argument; the value is pinned finite (dtoa gives `"0"` regardless) |
| `ast/src/c/operations/matrix_power.c:50` | `(int)exp` for an out-of-range/inf/nan exponent | range-guard the cast (`exp >= INT_MIN && exp <= INT_MAX && …`) so huge exponents take the `pow` path |
| `statistics/src/c/CdfBase.c:181` — `checkInteger` | `(int)data[i]` for inf/nan/out-of-range | `!(x >= INT_MIN && x <= INT_MAX) || …` → treat as "not an integer" |
| `string/sci_ascii.cpp:152` | `(char)double` > 127 (value already validated ∈[1,255]) | route through `unsigned char` |
| `fileio/src/cpp/mput.cpp` (`MPUT_CHAR` macro; call sites 139/163) | `(char)*res++` double→signed char > 127 | `(Type)(int)*res++` (defined for all byte values, same output bytes) |
| `output_stream/src/cpp/scilab_sprintf.cpp:{532,584}` — `%lld`/`%llu` of `Int64`/`UInt64` | `(long long)`/`(unsigned long long)` of a non-finite (or, for UInt64, negative) double; `_M_ARM64`-only guard skipped Apple/Linux | convert only when `isfinite`; UInt64 routes negatives through `int64_t` (platform-agnostic) |
| `special_functions/src/cpp/faddeeva.cpp:{1149,1699}` — `erfcx_y100`/`w_im_y100` | `(int)NaN` for `erf/erfc/dawson(%nan)` | `if (y100 != y100) return y100;` before the switch (propagates NaN, which is the correct result) |
| `sparse/sci_spzeros.cpp:{82,93}` + `sci_sparse.cpp:157` | `(unsigned int)` of a negative/nan dim in a validation check | `!(dim >= 0) || …` short-circuits before the cast (still errors cleanly) |

## Fixed — batch 2b (2026-07-09, task #93 — surfaced by the batch-2 re-verify)

Same float->int class, beyond the original catalog (the re-run exercised code the first sweep missed).
Verified: 26-check behaviour test (the addition results are preserved **bit-for-bit** — `uint8(5)+%inf`
stays 4, `+%nan` stays 5, `+300`=49, `+(-1)`=4), `integer` 34/34 ref, instrumented re-verify clean.
| Site | UB | Fix |
|------|----|-----|
| `ast/types/types_tools.cpp:{132,215,331}` — index from a scalar | `(int)` of a `nan`/`inf`/out-of-range index (e.g. `x(%nan)`) | `indexToInt()` maps such values to 0, which the existing `== 0` / `< 0` index-validity checks already reject |
| `ast/types/implicitlist.cpp:{301,316}` — `a:b:c` size | `(int)floor(count)` when the range is huge or the **unsigned** subtraction underflowed to ~2^64 | `clampImplicitSize()` (nan/neg->0, >INT_MAX->INT_MAX) + an `ullEnd < ullStart` empty-range guard on the unsigned branch |
| `ast/operations/types_addition.hxx` — matrix + scalar workers | narrow-int cast of an out-of-range/`inf`/`nan` **double** — both the scalar `(O)r` and, when an int scalar makes `O` an int type, the double matrix element `(O)l[i]` (e.g. `[300.5 -1.5] + uint8(1)`) | `castVal<O>()` on both operands: reproduces the historical int-intermediate lowering (saturate to int32, then narrow) in a *defined* way — identity for every non-(float->int) pair |

## Remaining (deferred / next batch, tracked)

**Operator-family long tail (same `castVal` pattern):** `types_addition.hxx` still has the matrix+matrix
`(O)r[i]` and complex-part `(O)rc` casts unconverted, and `types_subtraction.hxx` / `types_multiplication.hxx`
/ … carry the identical latent (currently-benign) UB. Not flagged by the sweep (unexercised with
out-of-range operands) — a mechanical follow-up once the pattern is proven on addition.

**Misalignment class (task #95) — ROOT-CAUSED + FIXED via an AddressSanitizer campaign.**
The UBSan symptom was a **corrupted child pointer** (`0x…7e`) read while tearing down a
`std::set<std::wstring>` — non-deterministic (heap-layout-dependent), appearing only at `destroy`, so it
was a *downstream* effect of a heap-buffer-overflow scribbling over whatever allocation sat next to a
set node. UBSan shows only the symptom, so I built the worktree with `-fsanitize=address` (poisons
redzones → reports the overflow *at the write*). ASan caught it **deterministically (3×)**:
**`operations/types_power.cpp:408` `DotPowerSpaseByDouble` — a heap-buffer-overflow in `sparse .^ double`.**
Scilab's `Sparse` is Eigen **row-major**; `getNbItemByRow()` reads `outerIndexPtr()` (allocated slots),
which for an **uncompressed** matrix sums to more than `nonZeros()`, so the position-walk loop runs past
`Col[]`/`iPositVal[]` (both `new int[nonZeros()]`), and `getColPos()` reads gappy `innerIndexPtr()` too.
Trigger is literally `ast/tests/nonreg_tests/bug_14500.tst`: `sprand(100,100,0.001).^2` — a known crash
whose original fix was incomplete. **Fix:** new `Sparse::makeCompressed()` (pure compression, no
prune — unlike `finalize()`) called before the walk, so `nonZeros()`/`getNbItemByRow()`/`getColPos()`
are consistent; plus `i < iSize && j < iRows` loop bounds as defense. Verified: `sparse.^` correct incl.
uncompressed matrices; ASan re-run clean at `types_power.cpp`. **Lesson: keep the ASan worktree — it
finds heap-corruption UBSan can't.**

## Broad ASan heap sweep (task #96) — inventory over 24 CLI modules

After the `.^` fix, ran the ASan worktree over 24 modules (driver `scratchpad/asan-sweep-broad.sh`) to
enumerate heap bugs. Found 8 distinct sites.

**FIXED — the sparse "uncompressed matrix" class (same root cause as `.^`).** Every site extracts a
sparse matrix's structure via `nonZeros()` + `getNbItemByRow()`/`getColPos()`/`outputValues()`/raw
`outerIndexPtr()`, which disagree for an uncompressed Eigen matrix. Fix = `pSp->makeCompressed()` before
the extraction (+ loop bounds where a walk is involved):
- `ast/operations/types_divide.cpp:453` `RDivideSparseByDouble` (`sparse ./ double`) — fired **5×**.
- `differential_equations/complexHelpers.cpp:244` `copyMatrixToSUNMatrix` — the CSR row-copy into the
  SUNMatrix Jacobian overruns for an uncompressed input.
- `sparse/sci_lusolve.cpp` + `sci_lufact.cpp` — inconsistent `dbl`/`itemsRow`/`colPos` handed to the
  Fortran `lufact1`, which then mis-indexes (the `sci_lusolve` heap-use-after-free is this).
- `matio/GetSparseVariable.cpp` (`GetSparseMatVar`) — same `iPositVal` walk, when saving a sparse var to
  a `.mat`. Latent (the sweep's matio test didn't save an *uncompressed* sparse) but fixed proactively.
  (`api_scilab` `getSparseVariable`/boolean twin use the same accessors — same latent risk, note.)
  **Separate PRE-EXISTING bug (not from this change, confirmed by reverting):** `savematfile`+`loadmatfile`
  of *any* sparse fails with "No variable read" (dense round-trips fine) — a matio-sparse save-format /
  round-trip defect, own follow-up.

**FIXED — the previously-deferred individual sites (2026-07-10, agent-diagnosed, rebuilt + behaviorally
verified on the normal build):**
- `console/cmdLine/getKey.c` `getCmdLine` heap-buffer-overflow — the fix: only advance
  `nextLineLocationInWideString` past the consumed command when it was terminated by a newline; when the
  scan ended on the final NUL, stay ON it (advancing past it read uninitialised heap beyond the
  terminator and, if non-zero, scanned unbounded for a newline). Also frees `commandLine` before the
  too-long early-return (was a leak) and resets the index. Verified: interactive console + multi-command
  paste behave.
- `special_functions/zbeshv.c:80` — off-by-one: the C port kept `l`/`j` **1-based** and applied `-1` at
  every real array access *except* this consecutive-order test, so `alpha[j]` read one past a
  `na`-element buffer when `j == na`. Fixed to `alpha[j-2]`/`alpha[j-1]` (matches the Fortran original
  `zbesiv`: `abs((1+alpha(j-1))-alpha(j))`). Unique to the C `besselh` port; besseli/j/k/y call the
  correct Fortran drivers. Verified: `besselh([0 1 2 3],1)` returns correct values, no OOB.
- `xml/XMLNodeList.cpp` `~XMLNodeList` heap-use-after-free — the destructor dereferenced the **borrowed**
  `parent->children` node, which the document may already have freed at teardown. Fix: cache the exact
  registered libxml pointer (`registeredFirstChild`, kept in sync at every `registerPointers` call site)
  and unregister with the cached key — mirrors how `XMLElement` caches `node`; the scope maps use the
  pointer only as an opaque key. Verified: children+`xmlDelete`, `xmlRemove(list)` self-delete, and
  multi-doc `xmlDelete("all")` all complete cleanly.
- `patched_sundials/.../arkode/arkode_ls.c` `arkLsLinSys` (the wild pointer that surfaced as
  `sunmatrix_sparse.c:586`, member load at address `~0x9`): `arkls_mem->savedJ` reaches `SUNMatCopy`
  unallocated on the **sparse-Jacobian** path (`arkLsInitialize` clones savedJ only for dense/band A and
  errors out earlier for a difference-quotient sparse Jacobian). Fix: lazily `SUNMatClone(A)` into savedJ
  at the top of `arkLsLinSys` if NULL, mirroring `arkLsInitialize`. Root-cause fix in the ARKODE glue;
  the vendored matrix primitive is untouched. Verified: `sundials_jacpattern.tst` runs to completion.

**NOT A BUG — investigated + dismissed:**
- `fileio/fscanfMat.c` `itCanBeMatrixLine` — deep read (agent) found **no reachable overflow**: the
  function is `sscanf` + `strdup` + bounded `strncmp` over NUL-terminated lines (`wide_string_to_UTF8`
  guarantees termination). The original ASan hit was a false positive or an upstream non-terminated line.
  Repro (empty / all-separator / special-token / no-trailing-newline lines) parses correctly, no fault.
  Left unchanged (a needless `strncmp`→length "fix" would risk parse behaviour).
- **matio sparse `.mat` "No variable read"** — was a regression in the matio *library* (the tree linked
  Homebrew matio 1.5.30; Scilab's `mat_sparse_t` construction is correct). Resolved with the current
  matio 1.5.30_1 after the full relink: `savematfile`/`loadmatfile` round-trip a sparse matrix in `-v6`,
  `-v7`, and `-v7.3` (values verified). No Scilab change, no version pin — the "latest tools" outcome.
  (A real but separate latent issue the agent noted: sparse **inside a struct/cell** is written
  transposed because the top-level CSR-as-CSC + dims-swap is only compensated by savematfile's
  top-level pre-transpose — tracked for a true-CSC follow-up, not the "No variable read" defect.)

**grayplot blank (#102) — NOT a source defect; FIXED by a clean rebuild.** The data path (graphic_objects
DataManager → NgonGridGrayplotDataDecomposer) is byte-identical to `main` and correct; the blank came
from a **stale native `libscigraphic_objects` JNI lib vs the SWIG/Makefiles regenerated by the
build-modernization commits**. A full rebuild resolves it: headless PNG is 14456 bytes vs 2153 for an
empty figure (real content rendered).

**Still deferred:**
- `patched_sundials/.../sunmatrix_sparse.c:586` is now guarded by the arkode_ls fix above (savedJ is
  always valid before the copy). If a *different* path ever feeds a corrupt SUNMatrix, add the
  `A/B/content` NULL guard the agent proposed — cheap defense, not currently needed.

**P3 — misaligned load (task #92):** covered by the deserializer fix above; re-scan for any siblings.

## How to re-verify
Rebuild the affected module(s) in the instrumented worktree `~/Projects/CLionProjects/scilab-ubsan`
(`CFLAGS/CXXFLAGS += -fsanitize=undefined -fno-omit-frame-pointer`), relink, run the module's
`test_run` under `UBSAN_OPTIONS=log_path=/tmp/ubsan-scilab/report`, and confirm the target lines
disappear from the report set. Driver: scratchpad `ubsan-sweep.sh` / `ubsan-testphase.sh`.
