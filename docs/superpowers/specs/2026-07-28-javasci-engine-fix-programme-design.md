# javasci / engine fix programme — design

**Date:** 2026-07-28
**Status:** approved, ready for implementation planning
**Register items:** B18 (remainder), B20, plus harness items surfaced during diagnosis

## Why this exists

Register items B16 and B17 made the 40 legacy javasci integration tests runnable for the
first time in years. Running them exposed real defects. B18's segfault and two stale-error-code
tests are already fixed; this document covers everything that remains, and it covers it as one
programme so the pieces that share a code path are designed together rather than patched
three times.

Every defect below was root-caused before this design was written. That order mattered: two of
the five turned out not to be product bugs at all, and one turned out to be already working.
Designing first would have produced fixes for problems that do not exist.

## Diagnosed inventory

| # | Item | Root cause | Nature |
|---|---|---|---|
| 1 | `getByReference` use-after-free | View is a raw pointer into engine memory with no invalidation protocol. A type-promoting assignment (`c(2,3)=123` into an int8 array) reallocates the variable, stranding the view. | product |
| 2 | B20: struct reads back empty | `struct()` creates a distinct `types::Struct` (`modules/ast/includes/types/struct.hxx`) that *reports* `sci_mlist` but is not an MList. `ScilabToJava.cpp:412`'s mlist branch calls `getListItemNumber`, which yields 0. | product |
| 3 | xcos tests hang a JVM | `XcosCellFactory.createBlock` → `ScilabInterpreterManagement.synchronousScilabExec` → `Signal.wait()` → `Object.wait()` forever. It posts a command to the interpreter and waits for completion; the harness never starts an interpreter thread to consume it. | harness |
| 4 | commons tests quarantined | Nothing wrong with them. 4/4 pass, module total 146, exit 0. | none |
| 5 | javasci advanced-mode tests (4 files) | Need the GUI-linked `libjavasci2` *and* the full `etc/classpath.xml` jar set (`NoClassDefFoundError: org/scilab/modules/core/Scilab`). | harness |

### Evidence

1. Nondeterministic garbage through a stale view (126 on one run, 50 on the next) then SIGTRAP
   on write. `c(2,3)=int8(123)` — no type promotion, no reallocation — keeps the view valid and
   reads 123 correctly. All 6 failures are integer types; the 3 double variants pass.
2. Scilab-created `mlist(...)`, `tlist(...)` and `list(...)` all marshal correctly; only
   `struct(...)` returns empty. So container marshalling is sound and the defect is
   struct-specific.
3. `sample` showed zero Scilab frames executing (libs loaded but idle, all time in
   `__psynch_cvwait`/`Unsafe_Park`); `jcmd Thread.print` gave the exact Java stack.
4. Measured directly by lifting the exclusion.
5. Advanced mode clears the B16 linker check and fails later and differently.

## Design

### Unit 1 — Live variable views

New `*Ref` subclasses in javasci: `ScilabDoubleRef extends ScilabDouble`,
`ScilabIntegerRef extends ScilabInteger`, and the same for the other by-reference-capable types.
Each holds `(Scilab session, String varName)` and overrides **only** the element accessors to
re-resolve the variable and read/write the buffer that is current *at that moment*.
`getByReference` returns the `*Ref` flavour; `get()` is untouched.

Feasibility is confirmed: `ScilabDouble` and `ScilabInteger` are `public class` (not final) and
their accessors are non-final (`final` appears on parameters, not methods).

**Why subclasses and not a change to `ScilabType`:** the shared type classes are used by all of
javasci *and* the GUI. Subclassing confines the change to javasci, and callers still receive a
`ScilabDouble`/`ScilabInteger`, so the public contract does not move.

**Semantics:** memory-safe by construction — a freed buffer is never touched — while remaining a
genuine live view, which is what the passing double tests depend on. After a reallocation the
accessor resolves the new buffer, which is why the 6 integer tests pass rather than merely
failing cleanly.

**Accepted cost:** no longer strictly zero-copy; each accessor costs a name lookup. For an API
that already crosses JNI per call this is proportionate, and the copying `get()` remains
available for hot paths.

**Rejected alternatives.** A version stamp plus `StaleReferenceException` is strictly safe and
explicit, but needs an engine-side generation counter and a new JNI entry point, and would leave
the 6 integer tests failing (cleanly) and needing rewrites. `IncreaseRef` on hand-out is the
smallest change and removes the UAF, but turns the view into a silent stale snapshot — wrong data
with no signal, worse than throwing — and leaks unless Java reliably releases on GC.

### Unit 2 — Struct marshalling

In `ScilabToJava.cpp`'s `sci_mlist` branch, detect a real `types::Struct` and marshal its fields
explicitly rather than routing it through the list path. The `mlist`/`tlist`/`list` paths are not
touched; they already work, and the fix must not disturb them.

**How the fields are reached — settled, not assumed.** `modules/api_scilab/includes/api_struct.h`
looks like the natural counterpart to the `api_list.h` this branch already uses, but it is a dead
end: it exposes a single `getFields(scilabEnv, scilabVar, wchar_t***)` which returns field *names*
only, and it belongs to the newer `scilabEnv`/`scilabVar` API generation rather than the
`pvApiCtx`/`addr` one the marshaller is written against. Mixing generations here would be worse
than the bug.

So the branch uses the `types::Struct` C++ interface directly, which is available because
`ScilabToJava.cpp` is C++: `Struct` is an `ArrayOf<SingleStruct*>`
(`modules/ast/includes/types/struct.hxx:30`) exposing `getFieldNames()`, `exists(key)` and normal
`ArrayOf` element access. Implementation must first confirm `ScilabToJava.cpp` can include the ast
types headers in its current include set, and add the include if not — that is a build detail, not
a design fork.

### Unit 3 — Harness items

- **commons:** delete `scilab.test.exclude.engine.commons`. Verified 4/4 green.
- **xcos:** stays excluded; the register entry is corrected to the real cause (an interpreter
  thread the harness never starts) so nobody re-diagnoses it. Giving the harness a live
  interpreter loop is separate work, not a fix to xcos.
- **advanced-mode javasci:** stays excluded; register the two concrete prerequisites.

### Unit 4 — Register and deployment hygiene

- B16's row still reads "Interim state (2026-07-26)" though it was resolved on the 27th.
- B18's row predates the split into B20 plus the remaining UAF.
- `/Applications/Scilab-2027.0.0.app` carries its own engine from Jul 26 and therefore has
  neither the B18 mlist fix nor the B19 SIGTERM fix. It needs a repackage to benefit.
- The B19 SIGTERM fix is verified on the CLI (rc=143). Whether it also cures the GUI's
  force-quit symptom is **unverified** and must not be claimed until tested after a repackage.

## Acceptance

Each unit is accepted by tests that already exist, not by new ones written to match the
implementation:

- Unit 1 — the 6 `testReadWriteBuf` failures pass, and the 3 double variants still pass.
- Unit 2 — `testReadWrite.ReadStructTest` passes; `mlist`/`tlist`/`list` round-trips unchanged.
- Unit 3 — commons legacy tests run green in `-Pnative-tests`.
- Whole programme — javasci native suite reaches 0 failures and 0 crashes, `PARITY OK` holds,
  default `mvn test` stays at 28/28, and the harness self-tests stay green.

Unit 2 touches engine code, so parity and the flag gate run on it specifically.

## Out of scope

- Giving the test harness a live Scilab interpreter loop (unblocks xcos).
- The GUI-linked javasci variant plus full jar classpath (unblocks advanced-mode tests).
- B6 (FlatLaf — arrives in the upstream tarball; a UI decision).
- H1–H4, H6 — harness limitations, not defects. H1 is blocked while fork CI is disabled.
- Task #103 Vulkan Windows/Linux portability — no test machine.

## Reproduction prerequisite

Everything here needs the native harness:

```
./build-test-native-libs.sh
sudo ln -s "$PWD/build-cmake/test-native-libs" /usr/local/lib/scilab
mvn -o -Pnative-tests -pl modules/javasci test
```

Remove with `sudo rm /usr/local/lib/scilab` when finished. Two traps worth carrying forward:
the farm resolves through `modules/*/.libs/`, which only the `drop-in-*` targets refresh — always
`drop-in-all` before re-testing a native fix; and surefire's `reuseForks=false` is load-bearing,
because the Scilab engine is not re-entrant within a process.
