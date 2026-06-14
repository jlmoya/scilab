# GPU acceleration for Scilab — design notes

**Status:** parked (planning only, no code). Drafted 2026-06-13.
**Owner:** Jose Moya. Pick this up when ready; everything needed to resume cold is below.

## Goal

Let Scilab run heavy numeric work on the GPU, **Metal/macOS first**, later CUDA
(NVIDIA) and ROCm (AMD). The desired UX is a **checkbox in
Edit ▸ Preferences** to switch GPU acceleration on/off globally — flip it on and
existing scripts get faster, with no code changes.

## How Scilab computes today (ground truth from the source)

- Every heavy numeric op funnels through three **CPU** libraries:
  **OpenBLAS** (BLAS + LAPACK) and **FFTW**. Confirmed via `otool -L` on
  `.libs/Scilab-2027.0.0`; configured in `build-macos.sh`
  (`--with-blas-library=/opt/homebrew/opt/openblas/lib`).
- Dispatch is **centralized**, not scattered across modules:
  - `*` (matmul): `modules/ast/src/cpp/operations/types_multiplication.cpp`
    → `modules/ast/src/c/operations/matrix_multiplication.c` (`dgemm`/`zgemm`).
  - FFT: `modules/fftw/src/cpp/fftw_common.cpp`.
  - LAPACK (solve/factorizations): `modules/linear_algebra/src/c`,
    `modules/cacsd/src/slicot`, `modules/elementary_functions`.
- **No GPU code exists yet** — greenfield.
- Default matrices are **fp64 (double)**.

The centralization is the key enabler: you target a handful of chokepoints, not
"the modules." Nobody rewrites modules — they call BLAS/LAPACK/FFTW, so you
intercept there.

## Two approaches

- **A — Transparent offload.** Intercept BLAS/LAPACK/FFTW at the central dispatch
  points and route to the GPU. Zero user/module changes. **This is what the
  preference checkbox implies.**
- **B — Explicit GPU type.** Add a first-class `gpu()` array type + operator
  overloads (MATLAB's `gpuArray`). User opts in by moving data to the GPU; data
  stays resident across a chain of ops. Power-user control; not a global toggle.

## Two constraints that decide the design

1. **Data residency.** BLAS is stateless on CPU memory, so per-call transparent
   offload copies in/out *every call* — a net loss except for large matrices on a
   discrete (PCIe) GPU. **Apple Silicon unified memory** (shared physical RAM,
   zero-copy buffers) largely removes this cost — a real reason to start on Mac.
2. **fp64.** **Metal has no fp64 at all** (Apple GPUs lack double-precision ALUs;
   MPS is fp16/fp32). Consumer NVIDIA throttles fp64 1/32–1/64. So Scilab's
   default fp64 matmul **cannot be transparently accelerated on an Apple GPU** —
   only fp32. Dropping fp64→fp32 changes results (~1e-7 relative) and therefore
   must be a **user-visible, opt-in** decision.

Consequence: on Metal, a "Use GPU" checkbox that preserves fp64 would do almost
nothing (everything falls back to CPU). For it to accelerate anything, it must
compute in **fp32**. So on Mac, "Use GPU" effectively *means* "allow fp32 GPU
math" — the preference pane needs a precision row, not just one checkbox.

## Preference pane (the headline feature)

Reuses the same XConfiguration framework the Terminal pane uses
(`etc/XConfiguration-gpu.xml` + `.xsl`).

```
Edit ▸ Preferences ▸ GPU Acceleration
────────────────────────────────────────────────────
☑ Use GPU acceleration when available
    Device:  Apple M-series · Metal            [detected]

Precision
   ○ Accuracy first — full double precision (fp64)
        On this Mac the GPU has no fp64, so this falls
        back to CPU almost always.
   ● Speed first — allow single precision (fp32) on GPU
        ⚠ GPU results differ from CPU by ~1e-7 relative.

Advanced
   Offload only operations larger than [ 256 ] per dim
────────────────────────────────────────────────────
Status:  GPU active · accelerating  matmul, fft
```

Wiring: checkbox sets a value → an `XConfigurationListener` caches it into a C
global (`gpuEnabled`, `gpuPrecision`, `gpuThreshold`) → the dispatch points test
`if (gpuEnabled && size > threshold && precisionOK)` and call the Metal backend,
else fall through to OpenBLAS/FFTW unchanged. One flag, two checkpoints to start
(matmul, fft).

## Recommended architecture

- **In-tree module** `modules/gpu/` (not an ATOMS toolbox). The old `sciGPGPU`
  toolbox (~2012) was explicit-type-only *because* it couldn't touch core; we can.
  Keep the heavy Metal/CUDA code in a separately-compiled backend lib; the
  preference + dispatch hooks live in core.
- **Cross-platform interface, Apple-only implementation first.** Define a minimal
  backend seam (`Device / Buffer / gemm / fft / elementwise / solve`) now;
  implement only Metal. Add CUDA/ROCm once Metal proves the model — the
  abstraction will be wrong until a second backend exists anyway.
- **v1 = transparent, checkbox-driven, fp32, GEMM + FFT.** Smallest thing that
  makes the checkbox real.
- **Explicit `gpu()` type = later/optional**, for power users who want resident
  chains and to amortize transfer.

## Op priority (by compute-density × accelerability = transfer tolerance)

1. **GEMM** — O(n³) on O(n²) data, most transfer-tolerant, native MPS. Beachhead.
2. **Elementwise + reductions** (`.*`, `+`, `sin`, `sum`) — trivial, but
   memory-bound; only pay off with resident data (explicit type).
3. **FFT** — high value (signal work), MPS/vDSP; FFTW already fast, so GPU wins on
   large/batched.
4. **Solve / factorizations (LU, QR, eig, svd)** — most valuable, hardest:
   thin Metal coverage, most fp32-sensitive. **Phase 2.** Mixed-precision
   (fp32 compute + fp64 CPU iterative refinement) recovers accuracy for solves.

## Phased roadmap

- **Phase 0 — de-risk (one experiment).** Standalone Metal fp32 GEMM + FFT vs
  OpenBLAS on the M-series: size crossover *with and without* the unified-memory
  copy, and the fp32-vs-fp64 error. Decides how aggressive offload can be.
- **Phase 1 — checkbox v1.** `modules/gpu/`, Metal context/buffers, MPS GEMM +
  FFT, the preference pane + C flag, offload hooks in `matrix_multiplication.c`
  and `fftw_common.cpp` (fp32, size-gated).
- **Phase 2 — coverage.** Solves/factorizations (MPSMatrixDecomposition; mixed
  precision), more elementwise/reductions, optional explicit `gpu()` type.
- **Phase 3 — other platforms.** CUDA (cuBLAS/cuSOLVER/cuFFT), ROCm.

## Decisions settled in discussion

- Cross-platform *interface*, Metal *implementation* first.
- In-tree module, not ATOMS toolbox.
- GEMM-led op priority; solves deferred to Phase 2.
- Global checkbox ⇒ transparent offload is the v1 model (explicit type later).
- On Metal the checkbox necessarily implies an fp32 precision policy.
- Build for the author's own workloads first; let upstreamability be a
  consequence of a clean interface, not a day-one constraint.

## Open questions to answer before implementing

1. **What does PIMS actually compute, and what dominates its runtime** — dense
   matmuls? FFTs? linear solves / eigenvalues (control-theory style)? optimization
   loops? At what matrix sizes? This decides whether **fp32 is acceptable** (the
   gate for the whole checkbox being useful on Metal).
2. **Scope: personal vs upstream.** Just-for-the-author (Metal-only, fp32, minimal,
   weeks) vs contributed to official Scilab (cross-platform, defensible fp64 story,
   broad coverage, review process, multi-quarter). Recommendation: personal first,
   structured to keep upstream open.

## Prior art

- Scilab `sciGPGPU` ATOMS toolbox (~2012): explicit GPU handles (`gpuMult`,
  `gpuFFT`, …) — chose the explicit model for exactly these residency/precision
  reasons.
- MATLAB Parallel Computing Toolbox `gpuArray`: explicit residency model.
- NVBLAS: drop-in `dgemm` interceptor (only viable because CUDA has real fp64) —
  the transparent model done right on capable hardware.
