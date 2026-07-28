# javasci / engine fix programme — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the two remaining javasci product defects (the `getByReference` use-after-free and the struct-reads-empty bug) and clear the harness and register debt around them, so the javasci native suite reaches zero failures and zero crashes.

**Architecture:** The by-reference view stops being a raw pointer into engine memory. New `*Ref` subclasses in javasci re-resolve their variable by name on every accessor, so a freed buffer is never touched while the view stays live. Struct marshalling stops routing `types::Struct` through the list path, which returns zero items for it. The shared `ScilabType` classes — used by the GUI as well as javasci — are not modified.

**Tech Stack:** Java 25 (JUnit 6.1.2 + surefire 3.5.6), C++ (`modules/types`, `modules/ast`), CMake, Maven.

## Global Constraints

- Do NOT modify the shared type classes in `modules/types/src/java/org/scilab/modules/types/`. All Java changes live in `modules/javasci/`.
- `PARITY OK` must hold after any change under `modules/` C/C++ (`cd scilab/build-parity && python3 -m parity.capture .. /tmp/c.json cand && python3 -m parity.diff baseline-autotools.json /tmp/c.json`).
- Default `mvn -o -pl modules/javasci test` must stay at 28 tests, 0 failures.
- Native tests need the farm symlink: `sudo ln -s "$PWD/build-cmake/test-native-libs" /usr/local/lib/scilab`. The user runs any `sudo`; never run it yourself.
- After ANY native rebuild, run `cmake --build build-cmake --target drop-in-all` before re-testing. The farm resolves through `modules/*/.libs/`, which only the drop-in targets refresh — skipping this tests the previous build.
- Commit messages carry NO AI-attribution trailers. Push to BOTH remotes: `git push https://gitlab.com/jlmoya/scilab.git main` then `git push git@github.com:jlmoya/scilab.git main` (port 22 is blocked for gitlab; github rides ssh.github.com:443 via `~/.ssh/config`).

---

## File Structure

**Create**
- `modules/javasci/src/main/java/.../ScilabReferenceException.java` — unchecked wrapper. Needed because the base accessors declare no checked exceptions, so `JavasciException` cannot escape an override.
- `modules/javasci/src/main/java/.../ScilabDoubleRef.java` — live double view.
- `modules/javasci/src/main/java/.../ScilabIntegerRef.java` — live integer view.

Note: javasci's Java sources live in `modules/javasci/src/java/org/scilab/modules/javasci/` (the module uses `src/java`, not `src/main/java`). Use that directory.

**Modify**
- `modules/javasci/src/java/org/scilab/modules/javasci/Scilab.java:585` — `getByReference` wraps the result in the Ref flavour.
- `modules/types/src/cpp/ScilabToJava.cpp:412` — the `sci_mlist` branch handles `types::Struct`.
- `scilab/pom.xml` — delete the commons quarantine property.
- `docs/design/deferred-fixes-register.md` — B16/B18 row corrections, xcos + advanced-mode causes, B20 resolution.

---

### Task 1: Live double view (`ScilabDoubleRef`)

**Files:**
- Create: `modules/javasci/src/java/org/scilab/modules/javasci/ScilabReferenceException.java`
- Create: `modules/javasci/src/java/org/scilab/modules/javasci/ScilabDoubleRef.java`
- Modify: `modules/javasci/src/java/org/scilab/modules/javasci/Scilab.java:585`
- Test: `modules/javasci/src/test/java/org/scilab/modules/javasci/ScilabDoubleRefTest.java`

**Interfaces:**
- Consumes: `Scilab.getInCurrentScilabSession(String)` → `ScilabType` (throws `JavasciException`); `Scilab.putInCurrentScilabSession(String, ScilabType)` → `boolean` (throws `JavasciException`).
- Produces: `ScilabDoubleRef(String varName, ScilabDouble snapshot)` (package-private ctor); `ScilabReferenceException extends RuntimeException` with `(String)` and `(String, Throwable)` ctors — Task 2 reuses both.

- [ ] **Step 1: Write the failing test**

```java
package org.scilab.modules.javasci;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scilab.modules.types.ScilabDouble;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScilabDoubleRefTest {
    private Scilab sci;

    @BeforeEach
    public void open() throws Exception {
        sci = new Scilab();
        sci.open();
    }

    @AfterEach
    public void close() {
        sci.close();
    }

    /** The view must report Scilab's write, including when the assignment reallocates. */
    @Test
    public void refSeesScilabWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        sci.exec("a(2,2)=99;");
        assertEquals(99.0, ref.getRealElement(1, 1), 1e-9);
    }

    /** A write through the view must land in the engine. */
    @Test
    public void scilabSeesRefWrite() throws Exception {
        sci.put("a", new ScilabDouble(new double[][] {{1.0, 2.0}, {3.0, 4.0}}));
        ScilabDouble ref = (ScilabDouble) sci.getByReference("a");
        ref.setRealElement(1, 1, 42.5);
        sci.exec("b=a(2,2);");
        assertEquals(42.5, ((ScilabDouble) sci.get("b")).getRealPart()[0][0], 1e-9);
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest=ScilabDoubleRefTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `refSeesScilabWrite` reads the stale snapshot (1.0/4.0), not 99.0.

- [ ] **Step 3: Add the unchecked exception**

```java
package org.scilab.modules.javasci;

/**
 * Thrown when a by-reference view can no longer resolve its variable — it was
 * cleared, or its type changed underneath the view.
 *
 * Unchecked on purpose: the ScilabType accessors this is raised from declare no
 * checked exceptions, so JavasciException cannot escape an override.
 */
public class ScilabReferenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ScilabReferenceException(String message) {
        super(message);
    }

    public ScilabReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Add `ScilabDoubleRef`**

```java
package org.scilab.modules.javasci;

import org.scilab.modules.types.ScilabDouble;
import org.scilab.modules.types.ScilabType;

/**
 * A LIVE view of a Scilab double variable (register B18).
 *
 * getByReference() used to hand back a raw pointer into engine memory with no
 * invalidation protocol. A type-promoting assignment reallocates the variable,
 * after which the view read freed memory -- nondeterministic garbage, then
 * SIGTRAP on write. This re-resolves the variable by NAME on every accessor, so
 * a freed buffer is never touched while the view still reflects Scilab's
 * writes. It is no longer zero-copy; use get() when a snapshot is what you want.
 */
public final class ScilabDoubleRef extends ScilabDouble {
    private static final long serialVersionUID = 1L;

    private final String varName;

    ScilabDoubleRef(String varName, ScilabDouble snapshot) {
        super(snapshot.getRealPart(), snapshot.getImaginaryPart());
        this.varName = varName;
    }

    private ScilabDouble live() {
        try {
            ScilabType current = Scilab.getInCurrentScilabSession(varName);
            if (!(current instanceof ScilabDouble)) {
                throw new ScilabReferenceException(
                    "variable '" + varName + "' is no longer a double");
            }
            return (ScilabDouble) current;
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot resolve variable '" + varName + "'", e);
        }
    }

    private void store(ScilabDouble updated) {
        try {
            Scilab.putInCurrentScilabSession(varName, updated);
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot write variable '" + varName + "'", e);
        }
    }

    @Override
    public double getRealElement(final int i, final int j) {
        return live().getRealElement(i, j);
    }

    @Override
    public double getImaginaryElement(final int i, final int j) {
        return live().getImaginaryElement(i, j);
    }

    @Override
    public void setRealElement(final int i, final int j, final double x) {
        ScilabDouble current = live();
        current.setRealElement(i, j, x);
        store(current);
    }

    @Override
    public void setImaginaryElement(final int i, final int j, final double x) {
        ScilabDouble current = live();
        current.setImaginaryElement(i, j, x);
        store(current);
    }
}
```

If `getImaginaryPart()` returns null for a real-only variable and the two-arg
`ScilabDouble` constructor rejects null, call the single-arg
`super(snapshot.getRealPart())` when `snapshot.isReal()`. Read
`ScilabDouble.java:99-135` and pick the constructor that matches; do not guess.

- [ ] **Step 5: Wire `getByReference`**

Replace the body at `Scilab.java:585`:

```java
    public ScilabType getByReference(String varname) throws JavasciException {
        ScilabType value = getInCurrentScilabSession(varname, true);
        // Hand back a LIVE view rather than a raw pointer into engine memory:
        // see ScilabDoubleRef / register B18.
        if (value instanceof ScilabDouble) {
            return new ScilabDoubleRef(varname, (ScilabDouble) value);
        }
        return value;
    }
```

- [ ] **Step 6: Run the new test and the existing by-ref doubles**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest='ScilabDoubleRefTest+testReadWriteBuf' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `ScilabDoubleRefTest` 2/2 PASS. `testReadWriteBuf` still 9 tests with the 3 double cases passing; the 6 integer cases still fail (Task 2 fixes those).

- [ ] **Step 7: Confirm the default path is untouched**

Run: `cd scilab && mvn -o -pl modules/javasci test`
Expected: `Tests run: 30, Failures: 0` (28 existing + the 2 new).

- [ ] **Step 8: Commit**

```bash
git add scilab/modules/javasci/src/java/org/scilab/modules/javasci/ScilabReferenceException.java \
        scilab/modules/javasci/src/java/org/scilab/modules/javasci/ScilabDoubleRef.java \
        scilab/modules/javasci/src/java/org/scilab/modules/javasci/Scilab.java \
        scilab/modules/javasci/src/test/java/org/scilab/modules/javasci/ScilabDoubleRefTest.java
git commit -F - <<'EOF'
fix(javasci): make by-reference double views live instead of raw pointers (B18)

getByReference handed back a raw pointer into engine memory with no
invalidation protocol, so a reallocating assignment left the view reading freed
memory. ScilabDoubleRef re-resolves the variable by name on each accessor:
memory-safe by construction, still a live view. No shared ScilabType class is
modified -- the GUI uses those too.
EOF
```

---

### Task 2: Live integer view (`ScilabIntegerRef`)

**Files:**
- Create: `modules/javasci/src/java/org/scilab/modules/javasci/ScilabIntegerRef.java`
- Modify: `modules/javasci/src/java/org/scilab/modules/javasci/Scilab.java` (extend the `getByReference` dispatch added in Task 1)
- Test: existing `modules/javasci/src/test/java/org/scilab/tests/modules/javasci/testReadWriteBuf.java` — this is the acceptance; write no new test.

**Interfaces:**
- Consumes: `ScilabReferenceException` (Task 1); the same static session accessors.
- Produces: `ScilabIntegerRef(String varName, ScilabInteger snapshot)`.

- [ ] **Step 1: Confirm the 6 integer failures are the current state**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest=testReadWriteBuf -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 9, Failures: 6` — Int8/UInt8/Int16/UInt16/Int32/UInt32 fail, the 3 double cases pass. This is the failing test for this task.

- [ ] **Step 2: Add `ScilabIntegerRef`**

```java
package org.scilab.modules.javasci;

import org.scilab.modules.types.ScilabInteger;
import org.scilab.modules.types.ScilabType;

/**
 * A LIVE view of a Scilab integer variable (register B18).
 *
 * The integer case is what exposed the use-after-free: `c(2,3)=123` puts a
 * DOUBLE into an int8 array, so Scilab reallocates the variable on conversion
 * and the old view pointed at freed memory (observed: 126, then 50 on a rerun,
 * then SIGTRAP on write). `c(2,3)=int8(123)` needs no conversion, keeps the
 * buffer, and read correctly -- which is how the trigger was identified.
 */
public final class ScilabIntegerRef extends ScilabInteger {
    private static final long serialVersionUID = 1L;

    private final String varName;

    ScilabIntegerRef(String varName, ScilabInteger snapshot) {
        super(snapshot.getDataAsLong(), snapshot.isUnsigned());
        this.varName = varName;
    }

    private ScilabInteger live() {
        try {
            ScilabType current = Scilab.getInCurrentScilabSession(varName);
            if (!(current instanceof ScilabInteger)) {
                throw new ScilabReferenceException(
                    "variable '" + varName + "' is no longer an integer");
            }
            return (ScilabInteger) current;
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot resolve variable '" + varName + "'", e);
        }
    }

    @Override
    public long getElement(final int i, final int j) {
        return live().getElement(i, j);
    }

    @Override
    public void setElement(final int i, final int j, final long x) {
        ScilabInteger current = live();
        current.setElement(i, j, x);
        try {
            Scilab.putInCurrentScilabSession(varName, current);
        } catch (JavasciException e) {
            throw new ScilabReferenceException(
                "cannot write variable '" + varName + "'", e);
        }
    }
}
```

`getDataAsLong()` is an assumption — `ScilabInteger` stores per-width arrays and
its constructors take `byte[][]`/`short[][]`/`int[][]`/`long[][]` with an
unsigned flag. Read `ScilabInteger.java:56-160` and `getPrec()` at line 430, then
build the snapshot with the constructor matching `snapshot.getPrec()`. The
precision MUST be preserved — `testReadWriteBuf` asserts on int8 vs int16 vs
int32 behaviour, so a widened snapshot would pass the read and corrupt the write.

- [ ] **Step 3: Extend the `getByReference` dispatch**

```java
        if (value instanceof ScilabDouble) {
            return new ScilabDoubleRef(varname, (ScilabDouble) value);
        }
        if (value instanceof ScilabInteger) {
            return new ScilabIntegerRef(varname, (ScilabInteger) value);
        }
        return value;
```

- [ ] **Step 4: Run the acceptance**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest=testReadWriteBuf -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 9, Failures: 0`.

- [ ] **Step 5: Run the whole javasci native suite**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test`
Expected: 94 tests, 0 crashes, exactly 1 failure left — `testReadWrite.ReadStructTest` (Task 3).

- [ ] **Step 6: Commit**

```bash
git add scilab/modules/javasci/src/java/org/scilab/modules/javasci/ScilabIntegerRef.java \
        scilab/modules/javasci/src/java/org/scilab/modules/javasci/Scilab.java
git commit -F - <<'EOF'
fix(javasci): make by-reference integer views live (B18) - testReadWriteBuf 9/9

The integer case is what exposed the use-after-free: assigning a double into an
int8 array reallocates on conversion, stranding the view. Re-resolving by name
fixes all 6 failures. Precision is carried through the snapshot, which the
per-width assertions depend on.
EOF
```

---

### Task 3: Struct marshalling (B20)

**Files:**
- Modify: `modules/types/src/cpp/ScilabToJava.cpp:412` (the `sci_mlist` case) and its `listtype` block at 422-435
- Test: existing `testReadWrite.ReadStructTest` — the acceptance; write no new test.

**Interfaces:**
- Consumes: `types::Struct` from `modules/ast/includes/types/struct.hxx` — `ArrayOf<SingleStruct*>` with `getFieldNames()` (returns `String*`) and `exists(const std::wstring&)`.
- Produces: no new API. Behaviour change only.

- [ ] **Step 1: Confirm the failure and its shape**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest=testReadWrite -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 15, Failures: 1` — `ReadStructTest`, because `sci.get("myDate")` yields `mlist()` with height 0, width 0, `getMListType()` null and zero fields, while `getVariableType` correctly says `sci_mlist`.

- [ ] **Step 2: Read before writing**

Read `modules/ast/includes/types/struct.hxx` (the class is `Struct : public ArrayOf<SingleStruct*>`, line 30) and `modules/types/src/cpp/ScilabToJava.cpp:398-440`.

Confirm two things and record them in the commit message:
1. whether `ScilabToJava.cpp`'s current include set can see `types::Struct`, adding the include if not;
2. how to reach a `SingleStruct`'s field values — `getFieldNames()` gives names only.

Do NOT use `modules/api_scilab/includes/api_struct.h`. It exposes only
`getFields(scilabEnv, scilabVar, wchar_t***)` — names, no values — and belongs to
the `scilabEnv`/`scilabVar` API generation, while this file is written against
`pvApiCtx`/`addr`. Mixing generations here would be worse than the bug.

- [ ] **Step 3: Handle the struct case in the mlist branch**

At `ScilabToJava.cpp:412`, the `sci_mlist` case currently just sets `listtype = 'm'` and falls into the shared block that calls `getListItemNumber` — which returns 0 for a `types::Struct` because it is not a list. Detect the struct and marshal its fields explicitly instead, emitting the same `'m'` list shape the Java side already assembles (header String of field names, then one item per field) so `ScilabMList` needs no change.

Leave the `sci_list` and `sci_tlist` cases untouched. Both marshal correctly today — verified by round-tripping Scilab-created `list(...)`, `mlist(...)` and `tlist(...)`, all of which came back populated while only `struct(...)` was empty.

- [ ] **Step 4: Rebuild and drop in**

Run: `cd scilab && cmake --build build-cmake --target drop-in-all`
Expected: BUILD SUCCESS. This step is mandatory — the tests load `modules/*/.libs/`, which only the drop-in targets refresh.

- [ ] **Step 5: Run the acceptance and the container regressions**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/javasci test -Dtest=testReadWrite -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `Tests run: 15, Failures: 0` — `ReadStructTest` passes and `putAndGetListTest` / `putAndGetXCOSMListTest` still pass, proving list/mlist marshalling did not regress.

- [ ] **Step 6: Gate the engine change**

```bash
cd scilab/build-parity
python3 -m parity.capture .. /tmp/cand-b20.json cand
python3 -m parity.diff baseline-autotools.json /tmp/cand-b20.json
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
```
Expected: `PARITY OK`, flagfacts exit 0. An internal branch moves no exported symbol; if parity reports a change, stop and investigate rather than re-baselining.

- [ ] **Step 7: Commit**

```bash
git add scilab/modules/types/src/cpp/ScilabToJava.cpp
git commit -F - <<'EOF'
fix(types): marshal struct() to Java instead of returning an empty mlist (B20)

struct() creates a distinct types::Struct that REPORTS sci_mlist but is not an
MList, so the marshaller's getListItemNumber returned 0 and Java received an
empty mlist -- silent total data loss on a very common type. Scilab-created
list(), mlist() and tlist() all marshalled correctly, which is what localised
this to structs.

Verified: ReadStructTest passes, list/mlist round-trips unchanged, PARITY OK,
flagfacts rc=0.
EOF
```

---

### Task 4: Harness items

**Files:**
- Modify: `scilab/pom.xml` (delete the commons quarantine property and its `<exclude>`)
- Modify: `docs/design/deferred-fixes-register.md`

**Interfaces:** none — configuration and documentation only.

- [ ] **Step 1: Prove commons passes before removing its quarantine**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/commons -Dscilab.test.exclude.engine.commons='**/__none__/**' test`
Expected: `Tests run: 146, Failures: 0` including `org.scilab.tests.modules.commons.CommonsTests` 4/4. Console noise about missing files and icons is expected — those tests assert on absent inputs deliberately.

- [ ] **Step 2: Delete the commons quarantine**

In `scilab/pom.xml`, remove the `<scilab.test.exclude.engine.commons>` property and its `<exclude>${scilab.test.exclude.engine.commons}</exclude>` line. Leave the xcos and javasci-advanced entries in place.

- [ ] **Step 3: Correct the register entries for what stays excluded**

In `docs/design/deferred-fixes-register.md`, record the real causes so nobody re-diagnoses them:

- **xcos** (10 files, still excluded): `XcosCellFactoryTest.createOneSpecificBlock` → `XcosCellFactory.createBlock` (nested) → `ScilabInterpreterManagement.synchronousScilabExec` → `action_binding.utils.Signal.wait` → `Object.wait()` forever. It posts a command to the interpreter and waits for completion, but the harness never starts an interpreter thread to consume it. Not native and not a Scilab deadlock — `sample` showed zero Scilab frames executing. Unblocking these means giving the harness a live interpreter loop, which is separate work.
- **javasci advanced-mode** (4 files, still excluded): `testBug10801`, `testGraphics`, `testExportOffscreen`, `testBug9544` construct `new Scilab(true)`, which needs the GUI-linked `libjavasci2` AND the full `etc/classpath.xml` jar set (without it: `NoClassDefFoundError: org/scilab/modules/core/Scilab`).
- Add the diagnostic note: `jstack` fails on these wedged JVMs ("state is not ready to participate in attach handshake") and SIGQUIT is ignored, but `jcmd <pid> Thread.print` works. Select the JVM by `comm=*/bin/java` — `pgrep -f` also matches the `/bin/sh` wrapper whose command line contains the java path.

- [ ] **Step 4: Verify commons now runs under the profile by default**

Run: `cd scilab && mvn -o -Pnative-tests -pl modules/commons test`
Expected: `Tests run: 146, Failures: 0`, with `CommonsTests` present in the output.

- [ ] **Step 5: Commit**

```bash
git add scilab/pom.xml docs/design/deferred-fixes-register.md
git commit -F - <<'EOF'
test: lift the commons quarantine, record the real xcos and advanced-mode causes

commons was never broken -- 4/4 pass, module total 146 -- so its exclusion is
deleted. xcos stays excluded but with its actual cause recorded:
synchronousScilabExec blocks on a Signal waiting for an interpreter thread the
harness never starts, which is a harness gap rather than an xcos defect.
Advanced-mode javasci stays excluded with its two prerequisites named.
EOF
```

---

### Task 5: Register and deployment hygiene

**Files:**
- Modify: `docs/design/deferred-fixes-register.md`

**Interfaces:** none — documentation only.

- [ ] **Step 1: Correct the stale rows**

- **B16** still reads "Interim state (2026-07-26)" though it was resolved on the 27th (commit `d1aa8d11ad9`): the javasci2-cli variant landed and the engine tests run. Rewrite the resolution column to say so.
- **B18** predates the split: its remaining scope is now Task 1 + Task 2 here, with the struct half tracked as B20. Update it to match.
- **B20** — mark RESOLVED once Task 3 lands, citing `ReadStructTest`.

- [ ] **Step 2: Record the deployment gap**

Add to the register that `/Applications/Scilab-2027.0.0.app` carries its own engine dated Jul 26 and therefore has neither the B18 mlist fix nor the B19 SIGTERM fix; it needs a repackage to benefit. State plainly that B19 is verified on the CLI (rc=143) and that its effect on the GUI force-quit symptom is UNVERIFIED — it must be tested after a repackage before any claim is made.

- [ ] **Step 3: Verify the register table did not break**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab && python3 -c "
import re
rows=[l for l in open('docs/design/deferred-fixes-register.md') if re.match(r'^\| (P|B|H)\d+ ', l)]
bad=[l.split('|')[1].strip() for l in rows if l.rstrip('\n').count('|') != 6]
print('rows:', len(rows), 'malformed:', bad or 'none')"
```
Expected: `malformed: none` — every row keeps 6 pipes, matching the header.

- [ ] **Step 4: Commit and push both remotes**

```bash
git add docs/design/deferred-fixes-register.md
git commit -F - <<'EOF'
register: correct B16/B18 rows, resolve B20, record the app deployment gap

B16's row still described the 2026-07-26 interim state though it was resolved on
the 27th; B18's framing predated the split into B20 plus the by-reference work.
Also records that the installed app has neither the B18 nor B19 fix until it is
repackaged, and that B19's effect on the GUI force-quit symptom is unverified.
EOF
git push https://gitlab.com/jlmoya/scilab.git main
git push git@github.com:jlmoya/scilab.git main
```

- [ ] **Step 5: Final whole-programme verification**

```bash
cd scilab
mvn -o -Pnative-tests -pl modules/javasci test          # expect 94 tests, 0 failures, 0 crashes
mvn -o -pl modules/javasci test                          # expect 30 tests, 0 failures
cd build-parity && python3 -m pytest -q                  # expect 170 passed, 1 skipped
```

Then confirm both remotes match local HEAD with `git ls-remote`.

---

## Self-Review

**Spec coverage.** Unit 1 → Tasks 1-2. Unit 2 → Task 3. Unit 3 → Task 4. Unit 4 → Task 5. The spec's acceptance criteria appear as concrete run/expect steps (Task 2 Step 5, Task 3 Steps 5-6, Task 4 Step 4, Task 5 Step 5). Out-of-scope items are not given tasks, by design.

**Placeholders.** None. Two steps deliberately require reading source before writing (Task 2 Step 2's `ScilabInteger` constructor selection, Task 3 Step 2's struct field access). Both name the exact file and line range and state what to decide, rather than hiding a guess in code that would compile and be wrong.

**Type consistency.** `ScilabReferenceException(String)` and `(String, Throwable)` are defined in Task 1 and used in Task 2. `getRealElement`/`setRealElement`/`getImaginaryElement`/`setImaginaryElement` match `ScilabDouble`; `getElement`/`setElement(long)` match `ScilabInteger`. `getInCurrentScilabSession`/`putInCurrentScilabSession` are the real static signatures. The `getByReference` dispatch added in Task 1 is extended, not redefined, in Task 2.
