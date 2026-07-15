# Scilab modernization — the North Star

Status: **direction confirmed with the user 2026-07-14.** Opened as *discovery* 2026-06-27;
discovery is over. This is the living strategic map.

> **The why (user, 2026-07-14):** Scilab is an old system with a lot of old infrastructure that
> nobody dared to update because there was no will to. The user took it upon themselves — if no one
> else would, they would. This is the same principle that has driven the whole fork ("everything is
> ours to fix"), now aimed at the build and the glue.

---

## The mission

**Modernize the infrastructure of a polyglot codebase — the build system and the way the languages
interface — provably and incrementally, keeping the same languages.** Not "add capability"; make the
old machine maintainable.

**Scope boundary (user, 2026-07-14):** same languages. Python/Java/C++ interop is good enough for
now; R is parked; a polyglot *runtime* (GraalVM/Truffle) is explicitly **too far**. We are not
adding languages — we are modernizing how the ones we have are built and bound.

**The diagnosis:** almost everything that has hurt this project is **hand-maintained glue across a
polyglot codebase.** Ant builds the Java (imperative XML, no dependency management — which is *why*
~23 libraries rotted to EOL). Autotools+make builds the native side ("working" still means
per-platform hand-maintenance — the entire `reapply-macos-fixes.sh` saga was autotools fighting a
platform it did not anticipate). And the boundaries are a zoo: SWIG generates the JNI and the
unusable sciQuantLib binding, hand-written JNI for the rest, a hand-written gateway per toolbox
function, PIMS for Python. Four aging, per-language, hand-tended mechanisms. **The build
fragmentation and the interface pain are the same disease** — and a modern build system is also the
thing that generates the interfaces.

---

## The agreed sequence (user, 2026-07-14)

1. **Ant → Maven** (Java) and **autotools/make → CMake** (native). Stabilize this first. A CMake
   description reads the same on every OS — that is the multiplatform maintenance win. Maven brings
   Java the dependency management Ant never had (consistent with the earlier Maven choice for the
   renderer POC).
2. **Then** replace the SWIG/JNI hand-glue with **generated modern FFI** (JDK Panama /
   `jextract` — auto-generates Java↔native bindings from C headers, deleting JNI rather than
   maintaining it).
3. **Throughout**, every toolbox must keep working under the new build — a **gate on each step**,
   not a cleanup at the end.
4. Then reassess.

**The coupling that makes this ordering correct:** the SWIG/JNI build steps phase 1 would otherwise
reproduce are *exactly what phase 2 deletes*. So phase 1 reproduces them only enough for parity, and
FFI-heavy modules may wait for phase 2 rather than getting a throwaway CMake-SWIG port.

---

## How "provable, incremental" is made real

A build migration is not a code migration: **"it compiles" proves almost nothing.** The failure
modes — a dropped flag, a missing symbol, a differently-linked dylib — surface weeks later as
runtime bugs. So:

- **The first artifact is a parity harness, not a build file.** Capture what the current build
  emits — the set of libs/jars, their exported symbols, the test suite, the user-verified
  GUI-surface checklist — so each migrated module can be diffed new-against-old and certified
  *behaviorally identical* with evidence.
- **Module by module along the dependency graph.** One leaf native module to CMake, still consumed
  by the autotools build, proven at parity, then rolled outward; one jar to Maven likewise. The two
  build systems coexist during the migration — the price of incremental over big-bang.
- Method is the project's own: **characterize → change one axis → prove parity.**

### Open design decisions for phase 1 (settle in a phase-1 design)

- **Coexistence:** hybrid (both builds live, module-by-module) vs. a parallel full build cut over at
  parity.
- **Orchestration:** CMake and Maven as two builds under a thin top-level driver, or one drives the
  other — i.e. "how do I build Scilab," one command or two.
- **Beachhead:** which module goes first — a tested leaf, ideally one whose macOS handling is
  currently a band-aid so the migration visibly *removes* debt.
- **Parity definition:** exactly what the harness captures and diffs to certify "no regression."

**Next concrete step:** a grounded **map of the current build** — what Ant does across the 26
`build.xml` files, what `configure.ac`/`Makefile.am` do across the ~160 native modules, the
Java↔native ordering coupling, and every generated-code choke point. Neither migration can be
designed without it; it is the same characterize-first pass that produced the original assessment
for deps and Java, now aimed at the build. That map feeds the phase-1 design (brainstorm → spec →
plan), starting with the parity harness and the beachhead.

---

## Where we are — what has already landed (the precondition)

You cannot credibly modernize the build of a codebase that still miscompiles `rand()` at `-O2`. That
debt is paid, which is why the build+interop work can start on solid ground.

| Item | Evidence |
|---|---|
| **Build is plain `./configure && make`** | `reapply-macos-fixes.sh` **deleted**; its 12 fixes folded into `configure.ac` / `Makefile.am`. `fetch-thirdparty.sh` gives a fresh clone a pinned, sha256-verified payload. Audit: `docs/design/build-modernization.md` |
| **The `-O2` UB miscompile class — closed** | `-fwrapv` applied globally (all 3,600 native objects); CI guard (`guard:ub-miscompile`) greps the policy and diffs a `durands` O0/O2 run |
| **UBSan + ASan sweeps — complete** | P0–P3 UB fixed (null-`this` calls, OOB, misaligned ptrs, the float→int cluster); the `__tree` ASan bug root-caused to a heap overflow in sparse `.^` and fixed |
| **100% native arm64, no Rosetta** | It never required Rosetta — a missing `LSRequiresNativeExecution` / `LSArchitecturePriority` pair. Plus a per-toolbox arch gate (`tbx_arch_check.sci`) |
| **Our own Vulkan/MoltenVK renderer** | Replaces the abandoned JOGL stack. M1–M8 + sprite clipping, readback-verified, merged (`d30f75059e5`). `docs/design/vulkan-renderer.md` |
| **macOS app + toolbox manager** | Independent `/Applications/Scilab-2027.0.0.app` + `tbxManager` GUI with a git-driven catalog |
| **Toolbox catalog verified 50/50** | Every toolbox builds, loads, and passes a runs-here smoke test |
| **Toolbox gateway hardening** | Whole-suite C/C++ memory-safety audit; sci-ipopt was the only one with a real bug |
| **Help browser builds by default; fork-native CI + releases** | `make doc` works on JDK 25; the fork has its own pipeline, badges, releases |

---

## Other open threads

**Ranked against the mission:**

- **Vulkan renderer portability — Windows/Linux Layer-1 surface + native loader** (task #103). The
  *one* item that rivals the build work for priority: everything else here is macOS-only by
  circumstance, but the renderer is macOS-only **by construction**. Do it alongside phase 1.
- **JNI → Panama/FFM** is not a separate thread — it **is phase 2** of the mission.
- **Java idiom debt** (375 `printStackTrace`, 12 `finalize()`, **1 record in 1,505 files** — a
  JDK-25 target on a Java-6 dialect) and **~23 dead Java deps** (JGraphX→maxGraph, flexdock,
  JavaHelp, JUnit 4): these fall out *naturally* once Maven brings dependency management and the
  build is modern. Sequence them into the Maven work rather than as standalone chores.
- **GPU acceleration** (Metal-first fp32 offload): PARKED. `docs/design/gpu-acceleration.md`.

**Guardrail (held all project):** do **not** attempt a Swing→JavaFX rewrite — no runtime mandate,
XL cost.

---

## Secondary / opportunistic track — Capability

A worthwhile detour from the mission, not the mission. **sciFinance** (P0 complete + pushed) proved
a reusable pattern for "make usable what we already own" — sciQuantLib had all of QuantLib as an
unusable 21,840-symbol SWIG ABI; the fix was a direct-linked facade where no handle crosses into
user code. It continues opportunistically (P1–P6), and note **P3's data-spine verbs
(`movmean`/`timerange`/`lag`/`resample`) are core gaps** to build in a toolbox and upstream into
core `timeseries`. Spec: `docs/superpowers/specs/2026-07-13-scifinance-design.md`.

It also surfaced the Scilab scripting traps that silently produce green-but-broken suites — recorded
so the migration's parity harness does not fall into them.

---

## Principles earned (each paid for with a real bug)

1. **Everything is ours to fix** — no upstream blame, no "that's pre-existing."
2. **A guard you have not seen FAIL is not a guard.** Mutation-test every gate. (sciFinance's leak
   gate was mathematically incapable of catching its own named bug until proven by fault injection.)
3. **Prove it on the machine, not in the argument.** Rosetta was an `Info.plist` gap; the Quit bug
   was a stale jar — and "fixing" the handler on a theory made it strictly worse.
4. **Intermittent means real.** 10× or it did not happen.

---

## Reference

- Build: `docs/design/build-modernization.md` · Renderer: `docs/design/vulkan-renderer.md`
- GPU (parked): `docs/design/gpu-acceleration.md` · Packaging: `docs/design/macos-app-packaging.md`
- Toolbox verification: `docs/design/toolbox-verification.md` · UBSan: `docs/design/ubsan-findings.md`
- sciFinance: `docs/superpowers/specs/2026-07-13-scifinance-design.md`
- Build map + phase-1 design: *to be produced (next step).*
