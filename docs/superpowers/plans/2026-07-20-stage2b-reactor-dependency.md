# Stage 2-b — the reactor's first real dependency — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Prove the two Maven mechanics Stage 2-a could not — **inter-module dependency resolution in the reactor** and **a vendored third-party jar** — by taking `modules/commons` to jar parity.

**Why this shape.** 2-a's beachhead (`modules/localization`) was deliberately a leaf with zero imports, so it proved compiler and manifest policy and nothing else. The mechanic Maven actually exists to provide — replacing `prebuildjava`'s hand-encoded 23-module topo-sort with real dependency resolution — is still unproven. `commons` is the smallest module that exercises it: it imports `org.scilab.modules.localization` (already migrated) and exactly one third-party package.

**Scope note.** This is a compact plan rather than a separate spec + plan, because every design decision follows directly from 2-a's established pattern. The one genuinely new decision — how a permanently-vendored jar enters the build — is stated in Task 2.

## Global Constraints

- **NO HARNESS WEAKENING.** `parity/capture.py`, `parity/diff.py`, `parity/fingerprint.py` byte-unchanged. If the jar fails parity, **the POM is wrong.** Two tests now pin the harness's strictness against `META-INF/maven` exclusions (one diff-time, one capture-time) — do not defeat them.
- **ADDITIVE.** Create `scilab/modules/commons/pom.xml` and its manifest fragment; add `commons` to the parent's `<modules>`. **No edits** to `build.incl.xml`, any `build.xml`, `cmake/`, or any `Makefile.am`. Ant keeps building every jar as now; Maven is still run by hand.
- **No AI-attribution trailers.**
- Full `build-parity` suite green (183 at Stage 2-b start, HEAD `b44f14ec22d`).

## Everything 2-a learned the hard way — apply all of it, do not rediscover

Each of these cost a debugging cycle on the beachhead:

1. **`<source>`/`<target>`, never `<release>`.** `<release>25</release>` fails when combined with `--add-exports` on `java.base` — release mode compiles against a stripped symbol table with no internal packages to export.
2. **`<manifestFile>`, never `<manifestSections>`.** The latter scrambles attribute order (in Plexus's XML-to-Map layer) and resolves `${manifest.class-path}` to the literal string `null` instead of leaving it unresolved.
3. **An explicit `-g:none` compiler arg.** `<debug>false</debug>` emits no `-g` flag at all, leaving javac's default of line numbers + source file. Ant really does compile `-g:none`: `build.incl.xml:129` reads `debug="${build.debug}"` and `scilab.properties:3` sets it `off`, which makes the adjacent `debuglevel="lines,vars,source"` dead text.
4. **maven-archiver pinned to 3.6.4.** 3.6.5 unconditionally stamps `Java-Version`, which is not in the harness's volatile-strip list.
5. **`<addMavenDescriptor>false</addMavenDescriptor>`.** Load-bearing, not boilerplate.
6. **XML comments cannot contain `--`.**
7. **The vendor string is double-encoded.** Ant's `<property file>` reads `scilab.properties`'s UTF-8 "è" as Latin-1, so every manifest carries `Ã¨`. **Extract the bytes Ant actually emits — do not retype the string from the logically-correct source**, or you will produce a fragment that is valid, readable, and silently parity-failing.

Most of 1-6 should already be inherited from the parent POM. Verify that rather than assuming it, and put in the module POM only what genuinely differs.

---

### Task 1: `commons` into the reactor, parity-green

**Files:**
- Create: `scilab/modules/commons/pom.xml`, `scilab/modules/commons/src/main/manifest/MANIFEST-section.MF`
- Modify: `scilab/pom.xml` (add `commons` to `<modules>`)

- [ ] **Step 1: Establish what `commons` actually needs.**

```bash
cd scilab
grep -rhE '^import (org|com)\.' modules/commons/src/java --include='*.java' | grep -v '^import org\.w3c\|^import org\.xml' | sort -u
grep -n 'manifest.class-path' modules/commons/build.xml || echo "  no manifest.class-path (simple case)"
find modules/commons/src/java -name '*.java' | wc -l
```
Expected: imports of `org.scilab.modules.commons.*` (itself), `org.scilab.modules.localization.*` (the reactor dependency), and `org.flexdock.view.View` (third-party). No `manifest.class-path`. 25 files. Note `org.w3c.dom`/`org.xml.sax` are JDK (`java.xml`), not dependencies.

- [ ] **Step 2: Add the reactor dependency.** `commons` gets a `<dependency>` on the `localization` artifact declared by `scilab/modules/localization/pom.xml`, and `commons` joins the parent's `<modules>` **after** `localization`.

Maven resolves reactor order from the dependency graph, not from `<modules>` order — Step 5 proves that rather than assuming it. This is the mechanic that replaces `prebuildjava/build.xml:25`'s hand-encoded list; comment it as such.

- [ ] **Step 3: Wire flexdock as a vendored local dependency.**

`org.flexdock.view.View` comes from `thirdparty/flexdock-1.2.5.jar`. This jar is **permanently vendored** — `modules/prebuildjava/ivy.xml` lists it among its `<!-- COPY -->` entries, meaning ivy itself gave up resolving it. So it is not a placeholder awaiting a Maven coordinate; it is a jar that will still be a local file after the dependency inventory is complete.

Pick a mechanism (`<systemPath>`, an `install-file`'d artifact, or a `file://` repository), state the trade-off in a comment, and note which of those choices survives to production versus which is scaffolding. Unlike 2-a's situation, this one is **not** temporary.

- [ ] **Step 4: The manifest fragment.** Same `<manifestFile>` mechanism as `localization`, with `commons`' own package name. **Extract the vendor bytes from the Ant-built jar** rather than retyping them (constraint 7 above).

- [ ] **Step 5: Prove the reactor ORDERS the build — not just that it succeeds.**

```bash
cd scilab
# clean both modules' Maven output, then build commons ALONE from the reactor root
find modules/localization modules/commons -name target -type d -exec rm -rf {} + 2>/dev/null
mvn -q -pl modules/commons -am package 2>&1 | tail -20
ls -la modules/localization/target/*.jar modules/commons/target/*.jar
```
Expected: `-am` ("also make") builds `localization` **first** because `commons` depends on it — not because of `<modules>` ordering. Report the output showing both jars produced. **If `localization` is not built, the dependency is not real** and the reactor is not doing the job the hand topo-sort does today; stop and report.

- [ ] **Step 6: Parity, through the harness's own function.**

```bash
cd scilab
rm -f modules/commons/jar/*.jar
(cd modules/commons && ant) && cp modules/commons/jar/org.scilab.modules.commons.jar /tmp/ant-commons.jar
mvn -q -pl modules/commons -am package
cd build-parity && python3 - <<'PY'
from parity.capture import fingerprint_jar
a = fingerprint_jar("/tmp/ant-commons.jar")
m = fingerprint_jar("../modules/commons/target/org.scilab.modules.commons.jar")
only_ant = sorted(set(a) - set(m)); only_mvn = sorted(set(m) - set(a))
changed  = sorted(k for k in set(a) & set(m) if a[k] != m[k])
print("entries only in Ant  :", only_ant or "none")
print("entries only in Maven:", only_mvn or "none")
print("entries differing    :", changed or "none")
print("VERDICT:", "PARITY" if not (only_ant or only_mvn or changed) else "DIFFERS")
PY
```
Adjust the Maven jar path to whatever the POM actually produces. Expected: all three lists empty. **Report them verbatim, and report every iteration it took** — a fixed mismatch is more informative than a first-try pass, so do not hide the debugging.

- [ ] **Step 7: Confirm `localization` did not regress.** Re-run 2-a's comparison for the localization jar; adding a dependent module must not have changed it.

- [ ] **Step 8: Coexistence and the suite.**

```bash
cd scilab && git status --short -- build.incl.xml '*/build.xml' cmake/ '*/Makefile.am' | head
(cd modules/commons && ant) && echo "ant still builds commons, rc=0"
cd build-parity && python3 -m pytest -q | tail -1
```

- [ ] **Step 9: Commit.**

```bash
git add scilab/pom.xml scilab/modules/commons/pom.xml scilab/modules/commons/src/main/manifest/
git commit -m "maven: commons joins the reactor — inter-module dependency + vendored flexdock, parity-green"
```

---

### Task 2: Record what 2-b proved (CONTROLLER)

- [ ] **Step 1:** Extend `docs/design/build-cmake-maven-migration.md`'s Stage 2 section with a **Stage 2-b** entry: what is now proven (reactor inter-module resolution replacing the hand topo-sort, a permanently-vendored third-party jar, parity on a 25-file jar), what remains (the other 22 modules, the coordinate inventory, `classpath.xml`, tests, the CMake swap), and any new trap the task surfaced.

- [ ] **Step 2:** Record that **Maven Central is reachable from this environment** (verified HTTP 200 against `repo1.maven.org`), so the dependency inventory is no longer blocked on network access — an earlier reconnaissance could not confirm this and flagged it as a gap.

- [ ] **Step 3: Commit.**

---

## Self-Review

**Coverage:** the two unproven mechanics each have a step that would fail if the mechanic were absent — Step 5 for reactor ordering (proven via `-am`, not via `<modules>` order), Steps 1/3 for the vendored jar (`commons` does not compile without flexdock). Parity is measured through the harness's own `fingerprint_jar`, not a bespoke diff. Step 7 guards against the new module regressing the old one, which no other step would catch.

**Placeholders:** none — every step is a runnable command with expected output. The Maven jar path in Step 6 is deliberately left to be read off the actual build rather than guessed, since the POM's `finalName` is the implementer's choice.

**Consistency:** `fingerprint_jar` returns the `{entry: hash}` map used identically here and in 2-a; the parent POM's `<modules>` and the module's `<dependency>` are the two halves of one reactor relationship and are added in the same task.
