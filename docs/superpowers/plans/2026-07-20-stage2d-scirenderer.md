# Stage 2-d — `scirenderer`, the triple exception — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Port `modules/scirenderer` to Maven at jar parity, exercising the three special cases the
template has not yet met — a per-module `<finalName>` override, the `Class-Path` manifest attribute,
and a manifest section naming a package that does not exist.

**Why this module next.** Reconnaissance (2026-07-20) found the remaining mechanics are far narrower
than assumed, and `scirenderer` concentrates them:

| Fact | Consequence |
|---|---|
| Its jar is `scirenderer.jar` — **the only one of 24** not matching `org.scilab.modules.<dir>.jar` | Exercises a per-module `<finalName>` override. The parent's inherited value would produce `org.scilab.modules.scirenderer.jar` → an orphan key → RED. The gate catches this. |
| **Only 2 of 24 modules** declare `manifest.class-path`; this is the smaller (`jogl-all.jar gluegen-rt.jar`) | Exercises the `Class-Path` attribute, still unproven. `gui` (6 entries) follows later. |
| Its classes live in `org.scilab.forge.scirenderer`, but its manifest section is `Name: org/scilab/modules/scirenderer/` | The section names a **nonexistent package** — inert metadata, a genuine upstream wart. Must be reproduced, not fixed. |
| Zero Scilab-module imports (only `com.jogamp.opengl.*`) | A **leaf** — no reactor ordering constraint, like `localization`. |
| No non-`.class` entries in the jar | No resource-handling machinery needed. |

**Coexistence.** Ant remains the real build. Maven is additive and run by hand.

## Global Constraints

- **NO HARNESS WEAKENING.** `fingerprint_jar`, `normalize_manifest`, `diff.py`, and every existing
  section keep their behavior. If the jar fails parity, **the POM is wrong.**
- **ADDITIVE.** Create `modules/scirenderer/pom.xml` + its manifest fragment; add it to the parent's
  `<modules>`. **No edits** to `build.incl.xml`, any `build.xml`, `cmake/`, or any `Makefile.am`.
- **Never `mvn -q`** in a verification recipe — a silent `rc=0` is not evidence.
- **No AI-attribution trailers.**
- Suite green (**202** at stage start, HEAD `6dd9104725c`).

## Everything the previous three stages learned — apply, do not rediscover

1. **`<source>`/`<target>`, never `<release>`** — `<release>` breaks with `--add-exports` on `java.base`.
2. **`<manifestFile>`, never `<manifestSections>`** — the latter scrambles attribute order and turns
   `${manifest.class-path}` into the literal string `null`.
3. **An explicit `-g:none`** — `<debug>false</debug>` emits no flag; Ant really does compile `-g:none`.
4. **maven-archiver pinned 3.6.4**; **`<addMavenDescriptor>false</addMavenDescriptor>`**.
5. **XML comments cannot contain `--` anywhere** — use the em dash `—`. Verify with `xmllint --noout`.
6. **The vendor string is double-encoded** (`Ã¨`). **Extract manifest bytes from the Ant-built jar;
   never retype them.**
7. **`Implementation-Version` must be PRESENT** — a frozen `00000000 0000`. Absent means
   `getImplementationVersion()` returns null at runtime, and the harness cannot see the difference
   because it strips the line from both sides. This is Stage 2-c's Critical 1; do not repeat it.
8. **`target/` is never auto-cleaned** — `rm -rf` it before any rebuild or a stale jar produces a
   spurious failure.

Most of 1–4 are inherited from the parent POM. Verify that; put only genuine differences in the module POM.

---

### Task 1: `scirenderer` at jar parity

**Files:** Create `scilab/modules/scirenderer/pom.xml`,
`scilab/modules/scirenderer/src/main/manifest/MANIFEST-section.MF`; modify `scilab/pom.xml`.

- [ ] **Step 1: Confirm the shape before writing the POM.**

```bash
cd scilab
grep -rhE '^import (org|com)\.' modules/scirenderer/src --include='*.java' | grep -v 'org\.scilab\.forge\.scirenderer' | sort -u
grep -n 'manifest.class-path\|library.title\|ant.project.name' modules/scirenderer/build.xml
unzip -p modules/scirenderer/jar/scirenderer.jar META-INF/MANIFEST.MF
find modules/scirenderer/src -name '*.java' | wc -l
```
Expected: only `com.jogamp.opengl.*`; `manifest.class-path` = `jogl-all.jar gluegen-rt.jar`;
section `Name: org/scilab/modules/scirenderer/`; 134 files. **Any deviation — stop and report.**

- [ ] **Step 2: The per-module `<finalName>` override.** The parent sets
  `org.scilab.modules.${project.artifactId}`; this module must emit `scirenderer.jar`. Override it
  in the module POM and comment *why* — it is the one module of 24 that breaks the convention, and
  `etc/classpath.xml` does not list it (it is an Ant-only orphan reached via `prebuildjava`).

- [ ] **Step 3: The two vendored JOGL jars.** `scilab-lib.properties:21,35` point `jogl2.jar` and
  `gluegen2.jar` at `thirdparty/jogl-all-2.5.0.jar` and `thirdparty/gluegen-rt-2.5.0.jar`;
  `build.incl.xml:81,84` puts both on the compile classpath as raw `<pathelement>`s. Use the same
  `<scope>system</scope>` + `<systemPath>` mechanism `commons` uses for flexdock, and **cross-
  reference that POM's comment rather than restating its whole rationale.**

- [ ] **Step 4: The manifest fragment — including `Class-Path`.**

  Extract every byte from the Ant jar (`unzip -p`), never retype. Three things here are traps:
  - `Class-Path: jogl-all.jar gluegen-rt.jar` names **unversioned** jars, while the files on disk
    are `jogl-all-2.5.0.jar` / `gluegen-rt-2.5.0.jar`. Reproduce the unversioned spelling exactly.
  - The section is `Name: org/scilab/modules/scirenderer/`, which **does not match any package in
    the jar** (`org.scilab.forge.scirenderer`). It is inert metadata generated mechanically from
    `${ant.project.name}`. **Reproduce it; do not "correct" it** — that is `reproduce, don't improve`.
    Comment it so the next reader does not fix it either.
  - `Implementation-Version` must be present as a frozen `00000000 0000` (constraint 7).

- [ ] **Step 5: Parity through the DIMENSION.** Build with Ant, then Maven from a cleaned `target/`,
  and compare via the harness — `test_maven_jars_align_with_ant_jars`, not a hand-run snippet.

```bash
cd scilab
find modules/scirenderer -name target -type d -exec rm -rf {} + 2>/dev/null
(cd modules/scirenderer && ant) && mvn -pl modules/scirenderer package 2>&1 | tail -15
ls -1 modules/scirenderer/target/*.jar
cd build-parity && python3 -m pytest tests/test_acceptance.py -q -k maven_jars 2>&1 | tail -3
```
Expected: `modules/scirenderer/target/scirenderer.jar`, and the alignment test PASSES.
**Report every iteration it took** — a fixed mismatch is more informative than a first-try pass.

- [ ] **Step 6: Prove the `<finalName>` override is load-bearing.** Comment it out, rebuild, and
  confirm the alignment test FAILS with an orphan `org.scilab.modules.scirenderer.jar`; then
  restore. This is the assertion that proves the override matters rather than merely being present.

- [ ] **Step 7: Coexistence + suite.**

```bash
cd scilab && git status --short -- build.incl.xml '*/build.xml' cmake/ '*/Makefile.am' | head
(cd modules/scirenderer && ant) && echo "ant still builds scirenderer, rc=0"
cd build-parity && python3 -m pytest -q | tail -2
```

- [ ] **Step 8: Commit.**

---

### Task 2: Record what 2-d proved (CONTROLLER)

- [ ] **Step 1:** Extend `docs/design/build-cmake-maven-migration.md`'s Stage 2 section with 2-d:
  the per-module `<finalName>` override, the `Class-Path` attribute, the dead manifest section
  preserved deliberately, and the reconnaissance finding that **only 2 of 24 modules** declare
  `manifest.class-path` (so this mechanic is narrow, not pervasive).

- [ ] **Step 2:** Record the **72-byte continuation-wrapping risk** for `gui`, which is next:
  `normalize_manifest` is line-oriented and never joins continuation lines, so a wrapped
  `Class-Path` is compared **literally**. `gui`'s 6-entry value wraps mid-token (`javafx.b` /
  `ase.jar`). Both Ant and Maven write via `java.util.jar.Manifest`, which wraps at 72 bytes, so
  they *should* agree — but this campaign has been burned by "should" before, and the gate will
  catch it. Name it before `gui` starts.

- [ ] **Step 3: Commit.**

---

## Self-Review

**Coverage:** each of the three exceptions has a step that fails if the exception is mishandled —
Step 2/6 for `<finalName>` (Step 6 proves it load-bearing), Step 4 for `Class-Path` and the dead
section, Step 3 for the vendored jars (the module does not compile without JOGL). Parity is measured
through the harness's own armed test.

**Placeholders:** none — every step is a runnable command with expected output. The Maven jar path in
Step 5 is read off the build rather than guessed.

**Consistency:** the `<finalName>` override interacts with Stage 2-c's parent-POM default, and Step 6
verifies the interaction rather than assuming inheritance resolves the way the comment claims.
