# Retire-configure RC-d — CMake drives the macros build — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-19
**Depends on:** RC-a (`machine.h`), RC-b (the flag policy + derived flag gate), RC-c (the
configure-substituted files — **including `etc/modules.xml`, which this stage consumes**). HEAD
`739fd41825d`. Strategy: `docs/design/build-cmake-maven-migration.md` §12.

## 1. Goal

Move the macros build — Scilab's ~3,500 `.sci` sources compiled to `.bin` **by running the
just-built interpreter** — under CMake, gated by a strengthened content-level parity dimension. This
is the last retire-configure sub-stage before the cutover.

## 2. The decomposition (context)

**RC-a (done)** · **RC-b (done)** · **RC-c (done)** · **RC-d (this)** — the macros build · **RC-e** —
the cutover. **RC-e is already blocked on Stage 2 (Ant→Maven)** by RC-c's deferral of the three
jar-path files, and additionally needs the `config.status` version-triple dependency resolved.

## 3. Background — measured, not assumed

### 3.1 How it runs today

`Makefile.am:246-249`:

```
macros: $(top_builddir)/scilab-cli-bin $(top_builddir)/bin/scilab-cli check-jvm-dep check-libstdcpp-dep
	-@( cd $(top_builddir) ; \
	HOME=/tmp $(top_builddir)/bin/scilab-cli -ns -noatomsautoload -nouserstartup -quit \
	    -f modules/functions/scripts/buildmacros/buildmacros.sce) || exit 1
```

**One** `scilab-cli` process in `-nwni` mode. `modules/functions/scripts/buildmacros/buildmacros.sce`
loops modules from `getmodules()`, `cd`-ing into each `macros/` dir and `exec`-ing that module's own
`buildmacros.sce`, which calls the compiled `genlib()` builtin (`modules/io/sci_gateway/cpp/
sci_genlib.cpp`). `genlib` is **non-recursive**, so nested macro dirs need explicit calls — several
modules fan out (`scicos_blocks` loops 13 subdirectories; `dynamic_link` has a `getos()=="Windows"`
branch).

**It needs no JVM and no jars.** The recipe's `check-jvm-dep` asserts `scilab-cli-bin` has *no*
`libjvm` dependency. Prerequisites are the linked interpreter and its wrapper script — nothing else.

### 3.2 Scope comes from `etc/modules.xml` — which RC-c already generates and parity-proves

`getmodules()` → `ConfigVariable::getModuleList()` → `FuncManager::AppendModules()`
(`modules/functions_manager/src/cpp/funcmanager.cpp:125-233`), which parses `etc/modules.xml` and
keeps entries with `activate="yes"` that also have `modules/<name>/etc/<name>.start`.

`etc/modules.xml` is a configure-substituted file **RC-c already generates byte-identically** and
covers in both the `generated` and `generated_cmake` dimensions. **RC-d therefore inherits its scope
from work already proven — it does not re-derive module enablement.** That is the stage's main
simplification, and it is why RC-d is small.

Live example of it mattering: `tclsci` is `activate="no"` on this machine (`WITH_TKSCI`), removing
an entire module from the loop.

### 3.3 `.bin` output is DETERMINISTIC — measured

`.bin` files embed AST node numbers from `ast::Ast::globalNodeNumber`, a **process-wide monotonic
counter that is never reset** (`modules/ast/includes/exps/ast.hxx:40-43`,
`modules/ast/src/cpp/ast/visitor_common.cpp:42`). Inspecting real bytes confirms it:
`modules/core/macros/who_user.bin`'s root node carries number ≈2030, a mid-stream value — so a
`.bin` is a function of source text **plus everything parsed earlier in that same process**, not of
source text alone.

That made cross-build byte-identity an open question. **It was tested, not assumed:** two
independent full rebuilds (all `.bin` and `lib` files deleted between) produced

```
baseline: 3516 .bin      run1: 3516      run2: 3516
run1 vs run2 differing files: 0
run1 vs the pre-existing on-disk state differing: 0
```

Both runs exited rc=0. So the counter's pre-`genlib` trajectory is itself deterministic — same
startup path, same module order, same alphabetical order within each directory. **A content-level
byte gate is viable**; RC-d needs no semantic dimension of the kind `machine.h` required.

### 3.4 The existing gate is presence-only — the weak spot

`build-parity/parity/capture.py:72-76` and `:310-318`: the harness hashes a sorted **path list** of
`.bin` files, with no read of their bytes. Its own comment says so: *"presence of the SET of compiled
macro files, not their content."*

It would catch a module's macros vanishing wholesale — the exact `rc=231` shape — but **not** a
`.bin` present at the right path with wrong bytes. Since RC-d's whole job is changing what compiles
those 3,516 files, that is the same necessary-but-insufficient gate shape this campaign has now been
bitten by three times. **Strengthen it before porting.**

### 3.5 Failure is silent at the orchestration level, and partial failure is usable

- **`make` ignores the exit status.** The `-` prefix on `Makefile.am:247` turns a non-zero exit into
  "Error 1 (ignored)" and the build continues. Nothing downstream re-validates completeness.
- **This shipped a real bug.** Commit `7303c43690e`: `toolbox_manager` lacked its
  `macros/buildmacros.sce`, the master loop's unguarded `exec` failed, `scilab-cli` exited 231 —
  after building every other macro library fine — and `make` swallowed it.
- **Partial failure leaves a half-built state two ways.** A parse error aborts the rest of that
  *directory* (later files get no `.bin`, while the `lib` manifest still closes out
  validly-but-incomplete, `sci_genlib.cpp:288-312,431`); and because the master loop has no
  `try/catch` either, an error anywhere aborts every module ordered after it.

### 3.6 Two incidental findings, both resolved

- **`windows_tools` had `.bin` files on disk but no `etc/modules.xml` entry.** Resolved: they were
  **stale**. It has `macros/buildmacros.sce` and two Windows-only sources (`dos.sci`,
  `powershell.sci`), but `getmodules()` never reaches it, so nothing rebuilds them. The determinism
  probe's delete+rebuild swept them, and parity stayed `PARITY OK` — they were not in the baseline.
- **Dead fallback code.** `modules/dynamic_link/macros/buildmacros.sce:14-16` falls back to
  `exec`-ing `modules/functions/macros/genlib.sci`, which **does not exist** anywhere in the tree —
  vestigial from before `genlib` became a C++ builtin. Harmless while the `io` module's builtin is
  registered; a trap for anyone reasoning about a reduced interpreter build. Documented, not touched.

## 4. Scope

**In scope:**
- Strengthen the macro parity dimension from **path-presence** to **path→content**.
- `scilab/cmake/ScilabMacros.cmake` — a `macros` target running the built `scilab-cli` headless
  against the existing `buildmacros.sce`, mirroring 1f-c's `doc` post-step.
- Docs + CI.

**Out of scope:** RC-e (the cutover); rewriting `buildmacros.sce` or `genlib` (the CMake target
*invokes* the existing machinery, it does not reimplement it); the dead `loadgenlib.sce` fallback;
removing `windows_tools`' unreachable macros.

## 5. Architecture

### 5.1 Content-level macro gate (build FIRST — it gates §5.2)

The `generated` map's `macros/*.bin (manifest)` entry becomes a hash over
`path + "\0" + sha256(bytes)` per file rather than paths alone. Same key, same single-entry shape —
so the baseline grows by one changed hash, not 3,516 entries — but a wrong-content `.bin` now moves
it.

Armed from the current tree (which §3.3 proved reproducible). Fault-injected two ways: **delete** a
`.bin` (the presence property the old gate had — must not regress) and **corrupt** one (the new
property). Both must fail naming the manifest.

### 5.2 `scilab/cmake/ScilabMacros.cmake`

A `macros` target reproducing `Makefile.am:246-249`'s environment exactly — `HOME=/tmp`, the same
five flags (`-ns -noatomsautoload -nouserstartup -quit -f`), the same script path — depending on the
built `scilab-cli-bin`, and **not** on the jars (§3.1: the macros build is JVM-independent, and
asserting otherwise would invent a dependency autotools does not have).

**It fails loudly.** `make`'s `-` prefix is *not* reproduced: CMake never had it, a build step that
can fail invisibly already shipped one real bug (§3.5), and the campaign's "reproduce" mandate is
about the *artifact*, not about inheriting a swallow-the-error habit into a system that never had it.
The divergence is documented where the target lives — the same treatment given configure's
wall-clock year-bump in RC-c.

Whether `macros` rides `drop-in-all` or stays opt-in follows 1f-c's `doc` precedent: opt-in, because
it requires a fully built interpreter and would otherwise fail on an unbuilt tree in a way that
looks like a CMake bug rather than a missing prerequisite.

### 5.3 Coexistence

`make macros` continues to work unchanged; nothing in `configure.ac`, any `Makefile.am`, or
`buildmacros.sce` changes. Both drivers invoke the same script against the same interpreter and
write to the same in-tree locations, so they are interchangeable rather than parallel — which is
what makes the content gate meaningful.

## 6. The gate & acceptance

1. The strengthened dimension is armed and **seen to fail** on both a deleted and a corrupted `.bin`.
2. CMake's `macros` target produces `.bin` output **byte-identical** to `make macros`' — verified by
   building each way from a cleaned macro tree and comparing all ~3,516 files, not a sample.
3. The target **fails non-zero** on a genuine macro error — verified by injecting one, not asserted.
4. From-scratch whole-tree **PARITY OK** + the RC-b flag gate rc=0 + the suite green.
5. `make macros` still works (coexistence).

## 7. Migration mechanics & rollback

- **Order:** (1) strengthen + arm + fault-inject the gate. (2) `ScilabMacros.cmake`. (3) from-scratch
  parity + docs + CI.
- **Rollback is free:** the harness change is additive; the CMake file is new; no autotools or
  Scilab-script edits. `make` recovers everything.
- **Note for the implementer:** `genlib` is **incremental** — it skips a file whose md5 matches the
  previous `lib` manifest and whose `.bin` still exists (`sci_genlib.cpp:263-279`). Comparing the two
  drivers therefore requires deleting `.bin` **and** `lib` first, or the second run is a no-op that
  proves nothing.

## 8. Testing

- The content gate is the primary test, fault-injected before the port.
- The two-driver byte comparison (§6.2) is the port's own proof.
- A negative test that the target's exit status actually propagates (§6.3).
- CI: `sanity:cmake-driver` gains a check that `ScilabMacros.cmake` is wired.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `.bin` determinism does not hold on another machine or after an interpreter change | Measured here (§3.3) and now continuously enforced by the content gate — a drift becomes a named parity failure instead of a silent divergence. |
| The content hash makes parity flaky rather than strict | §3.3 measured zero drift across independent rebuilds *including* against pre-existing state. If drift ever appears, the correct response is to investigate it, not to weaken the gate back to presence. |
| CMake's target silently no-ops because `genlib` skipped everything | §7's note: the acceptance comparison deletes `.bin` *and* `lib` first. A no-op run would otherwise "pass" trivially. |
| The macros target runs against a half-built tree | Opt-in (§5.2) + an explicit dependency on the built interpreter; a missing binary fails at exec with a clear error. |
| Reproducing `make`'s silent-failure habit | Deliberately not reproduced, with the reasoning recorded at the target (§5.2). |

## 10. Success criteria

- CMake builds the macros; output byte-identical to `make macros` across all ~3,516 files.
- The macro dimension gates **content**, not just presence, and has been seen to fail both ways.
- The CMake target fails loudly on a real macro error, verified by injection.
- From-scratch whole-tree **PARITY OK** + flag gate rc=0 + suite green; `make macros` still works.
- The silent-failure divergence, the dead `loadgenlib.sce` fallback, and `windows_tools`'
  unreachable macros are all recorded where the next reader will find them.
