# Stage 2-a — the Maven beachhead — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove Maven's mechanics and the existing parity gate on **one real module** — a parent POM, one leaf module's POM, and a Maven-built jar that passes the harness's `jars` dimension with **no harness weakening**.

**Architecture:** Pin the no-Maven-descriptor expectation in the harness first, then write the POMs, then measure against the real gate. Ant remains the build throughout; Maven is run by hand.

**Tech Stack:** Maven (parent + one module POM), Python 3 + pytest (harness), the existing Ant build as the reference.

## Global Constraints

- **NO HARNESS WEAKENING.** `parity/capture.py` and `parity/diff.py` gain **no exclusions**. Ant's jars carry no `META-INF/maven/`, so a Maven jar that does is a real divergence and the gate flagging it is the gate working. If the jar fails parity, **the POM is wrong** — fix the POM. This is the single most important constraint: the last three stages each shipped a defect that traced to accommodating a divergence instead of eliminating it.
- **ADDITIVE and coexistence-preserving.** No edits to `build.incl.xml`, `modules/prebuildjava/build.xml`, any module's `build.xml`, `cmake/ScilabJava.cmake`, or any `Makefile.am`. Ant must keep building every jar exactly as it does now.
- **REPRODUCE, don't improve.** Preserve today's behavior for the three orphans (`output_stream` unbuilt, `scirenderer` Ant-only, `terminal` GUI-gated) — Stage 2-a touches none of them.
- **No AI-attribution trailers in any commit.**
- The full `build-parity` suite must stay green (181 passing at Stage 2-a start, HEAD `2a0ea3a7993`).

---

### Task 1: Pin the no-Maven-descriptor expectation

**Files:**
- Modify: `scilab/build-parity/tests/test_jar.py` (or the harness's existing jar-test file — find it first)

**Interfaces:**
- Consumes: `fingerprint_jar` (`parity/capture.py`) and `diff_fingerprints` (`parity/diff.py`), both unchanged.
- Produces: a regression test asserting the harness treats `META-INF/maven/**` as a real difference.

- [ ] **Step 1: Read how jar fingerprinting and diffing work today.** `parity/capture.py`'s `fingerprint_jar`, `parity/fingerprint.py`'s `normalize_manifest` and `_MANIFEST_VOLATILE`, and `parity/diff.py`'s jar comparison block. Note what *is* normalized (`Ant-Version`, `Created-By`, `Built-By`, `Archiver-Version`, the `${DSTAMP} ${TSTAMP}` form of `Implementation-Version`) and confirm for yourself that nothing excludes `META-INF/maven/`.

- [ ] **Step 2: Write the failing-by-construction test.**

```python
def test_maven_descriptor_entries_are_a_real_difference():
    """A Maven-built jar must NOT smuggle META-INF/maven/ past parity.

    maven-jar-plugin embeds META-INF/maven/<g>/<a>/pom.xml + pom.properties by
    default. Ant's jars have no such entries, so a jar carrying them differs from
    the artifact we reproduce -- the gate reporting that is the gate WORKING.

    The fix belongs in the POM (<addMavenDescriptor>false</addMavenDescriptor>),
    never in this harness: excluding the path here would weaken a strict check to
    accommodate a divergence, which is how the last three stages shipped defects.
    This test exists so that a future reader does not "helpfully" add that
    exclusion -- it fails the moment anyone does.
    """
    base = {"jars": {"modules/commons/jar/org.scilab.modules.commons.jar": {
        "org/scilab/modules/commons/Foo.class": "aaa",
        "META-INF/MANIFEST.MF": "bbb"}}}
    cand = {"jars": {"modules/commons/jar/org.scilab.modules.commons.jar": {
        "org/scilab/modules/commons/Foo.class": "aaa",
        "META-INF/MANIFEST.MF": "bbb",
        "META-INF/maven/org.scilab/commons/pom.xml": "ccc",
        "META-INF/maven/org.scilab/commons/pom.properties": "ddd"}}}
    result = diff_fingerprints(base, cand)
    assert not result["ok"], "a Maven descriptor slipped past the jar gate"
    joined = " ".join(result["differences"])
    assert "META-INF/maven" in joined, f"the difference did not name the culprit: {joined}"
```

Adapt the fingerprint-dict shape and the `diff_fingerprints` import to match what the existing tests in that file actually use — read a neighbouring test first rather than assuming this skeleton's shape is right.

- [ ] **Step 3: Run it.**

```bash
cd scilab/build-parity && python3 -m pytest tests/ -q -k maven_descriptor
```
Expected: **passes immediately** — the harness is already strict; this test documents and locks that. That is the point: it is a guard against a future weakening, not a fix for a present bug.

- [ ] **Step 4: Prove it is load-bearing.** Temporarily add a `META-INF/maven/` exclusion to the jar comparison (the "helpful fix" this test exists to prevent), re-run, and confirm the test **fails**. Then revert the exclusion and confirm it passes again. Report both outputs. A guard you have not seen fail is not a guard.

- [ ] **Step 5: Full suite, then commit.**

```bash
cd scilab/build-parity && python3 -m pytest -q | tail -1
git add scilab/build-parity/tests/test_jar.py
git commit -m "build-parity: pin that META-INF/maven entries fail the jar gate"
```

---

### Task 2: The parent POM and one leaf module

**Files:**
- Create: `scilab/pom.xml` (parent), `scilab/modules/commons/pom.xml` (or `localization` — see Step 1)

**Interfaces:**
- Consumes: the existing `thirdparty/*.jar` files as local artifacts; the Ant-built jar as the parity reference.
- Produces: a Maven-built module jar that passes the `jars` dimension.

- [ ] **Step 1: Verify the module is genuinely a leaf — before writing anything.**

```bash
cd scilab && M=modules/commons
grep -rhoE '^import org\.scilab\.modules\.[a-z_]+' $M/src/java --include='*.java' | sort -u
```
Expected: no imports of *other* Scilab modules (or only ones whose jars a leaf may legitimately need). **If it is not a leaf, switch to `modules/localization`, re-run this check, and record why in your report.** A beachhead that needs a dependency graph is not a beachhead — that is the whole point of picking a leaf.

- [ ] **Step 2: Extract the exact build settings you must reproduce.** From `build.incl.xml`:

```bash
cd scilab && sed -n '128,163p' build.incl.xml
```
Record verbatim: the `javac` `source`/`target`/`encoding`/`debug`/`debuglevel` attributes, every `--add-exports`/`--add-opens` compiler arg, and the full `<manifest>`/`<section>` block with its attribute names. These are the contract — the parity gate compares the resulting `MANIFEST.MF` and `.class` entries, so anything you paraphrase rather than reproduce will be named as a difference.

- [ ] **Step 3: Write the parent POM,** encoding exactly what Step 2 recorded, with each setting commented with what it reproduces:

- `maven-compiler-plugin` with `<release>25</release>`, UTF-8 encoding, and `<compilerArgs>` carrying `--add-exports java.base/jdk.internal.loader=ALL-UNNAMED`, `--add-opens java.base/jdk.internal.loader=ALL-UNNAMED`, `--add-opens java.base/java.lang.reflect=ALL-UNNAMED`. **Comment why they exist:** `modules/jvm/.../LibraryPath.java:25-26` imports `jdk.internal.loader.NativeLibraries` and `sun.misc.Unsafe` and reflectively rewrites `LibraryPaths.USER_PATHS`; without them that file does not compile under JDK 17+ module encapsulation. (`commons` does not need them — the parent is where the policy belongs.)
- `maven-jar-plugin` with **`<addMavenDescriptor>false</addMavenDescriptor>`**, commented with §3.2's reasoning: Ant's jars have no `META-INF/maven/`, so carrying one is a divergence the parity gate correctly rejects; the flag is load-bearing, not boilerplate, and Task 1's test fails if anyone removes the gate's strictness.
- `maven-jar-plugin`'s **`<manifestSections>`** reproducing the per-package section. **Comment that this is a runtime dependency, not cosmetics:** `modules/xcos/.../XcosDiagramCodec.java:304-305` and `.../CustomWriter.java:121` read `Package.getSpecificationVersion()`/`getImplementationVersion()` — populated by the JVM from that section — and stamp the result into saved `.xcos` files. Maven's default flat manifest would compile fine and silently change what users' saved diagrams contain.

- [ ] **Step 4: Write the module POM,** resolving its dependencies from the existing `thirdparty/*.jar` files. Pick one mechanism (a `file://` local repository, `install-file`'d artifacts, or `<systemPath>`), and **comment prominently that this is scaffolding**: it exists so the beachhead is not blocked on researching Maven coordinates for ~32 jars that have none recorded, and it is to be replaced once those coordinates are known. It is not the intended end state.

- [ ] **Step 5: Build both jars from the same sources and compare through the REAL harness.**

```bash
cd scilab
# the Ant reference (rebuild so both come from identical sources)
rm -f modules/commons/jar/*.jar
(cd modules/commons && ant)
cp modules/commons/jar/org.scilab.modules.commons.jar /tmp/ant-commons.jar

# the Maven candidate
mvn -q -f modules/commons/pom.xml package
find modules/commons -name '*.jar' -newer /tmp/ant-commons.jar
```
Then compare them with the harness's own function, not a bespoke diff:

```bash
cd build-parity && python3 - <<'PY'
from parity.capture import fingerprint_jar
a = fingerprint_jar("/tmp/ant-commons.jar")
m = fingerprint_jar("<path to the Maven-built jar>")
only_ant  = sorted(set(a) - set(m))
only_mvn  = sorted(set(m) - set(a))
changed   = sorted(k for k in set(a) & set(m) if a[k] != m[k])
print("entries only in Ant :", only_ant or "none")
print("entries only in Maven:", only_mvn or "none")
print("entries differing    :", changed or "none")
print("VERDICT:", "PARITY" if not (only_ant or only_mvn or changed) else "DIFFERS")
PY
```
Expected: **all three lists empty.** Report them verbatim.

**If anything differs, fix the POM — never the harness.** Likely causes, in order: a missing `addMavenDescriptor=false` (shows as `META-INF/maven/...` in *only in Maven*), a manifest-section mismatch (shows as `META-INF/MANIFEST.MF` differing — dump both manifests and diff the text), or a compiler-arg mismatch (shows as `.class` entries differing).

- [ ] **Step 6: Confirm coexistence, then commit.**

```bash
cd scilab && git status --short -- build.incl.xml '*/build.xml' cmake/ '*/Makefile.am' | head
(cd modules/commons && ant) && echo "ant still builds, rc=0"
cd build-parity && python3 -m pytest -q | tail -1
```
Expected: no Ant/CMake/automake files modified; Ant rc=0; suite green.

```bash
git add scilab/pom.xml scilab/modules/commons/pom.xml
git commit -m "maven: parent POM + commons beachhead, jar parity-green vs Ant"
```

---

### Task 3: Docs, corrections, and the deferred map (CONTROLLER-executed)

**Files:**
- Modify: `docs/design/build-cmake-maven-migration.md`

- [ ] **Step 1: Record the beachhead result** in the Stage 2 section — what was proven (Maven mechanics + the parity gate on real Maven output, one leaf module, byte-parity-green), and explicitly what was **not** (the other 23 POMs, coordinate research, `classpath.xml` regeneration, tests, the CMake swap).

- [ ] **Step 2: Correct the two documented errors.** The `prebuildjava` topo-sort is **23** modules, not 22 (the doc says both in different places; the code is consistent at 23), and `terminal` is a 24th outside that list. FlatLaf is **fetched but unused** — its only reference is `// TODO uncomment if using FlatLaf` at `modules/ui_data/.../ScilabFileSelectorFilter.java:162` — so "already bundled to replace the Swing L&F set" overstates it.

- [ ] **Step 3: Record the findings a later sub-stage will need**, so they are not rediscovered:
  - **The Xcos manifest dependency** (§3.1 of the spec) — the highest-value finding in the reconnaissance, because it makes a build-cosmetics change into a user-data change.
  - **`ivy.xml` is dead** — no ivy jar exists anywhere in the tree and `~/.ant/lib` does not exist, so `ant download` would fail immediately on an unresolved antlib. `fetch-thirdparty.sh` reimplements the job with `curl` + sha256.
  - **Manifest `Class-Path` chaining works for 2 of 23 modules** — only `gui` and `scirenderer` set `manifest.class-path`; the rest ship the literal string `${manifest.class-path}` unresolved, because Ant leaves undefined property references as text.
  - **22 of 23 jars are built twice** under plain autotools (once via `prebuildjava`, again via each module's own `USEANT=1`). Stage 1's CMake bridge already collapsed this to 2 Ant invocations.
  - **The three orphans** — `output_stream` built by nothing yet referenced by `scilab-lib.properties:170-172`; `scirenderer` Ant-only with no `Makefile.am`; `terminal` GUI-gated and outside the topo-sort. A reactor forces each into the open; Stage 2-a deliberately preserved all three.
  - **~32 of the 78 vendored jars have no recorded Maven coordinate anywhere** — including `junit-4.10`. At least ~10 must stay vendored (`ivy.xml`'s own `<!-- COPY -->` entries name `flexdock`, `jgraphx`, `jogl-all`, `gluegen-rt`, `jhall`, `jrosetta-API`, and 3 JavaFX jars).
  - **No codegen plugin is needed** — JFlex has zero build wiring; SWIG/GIWS are opt-in and slated for deletion in the FFI phase.

- [ ] **Step 4: Commit.**

```bash
git add docs/design/build-cmake-maven-migration.md
git commit -m "docs: Stage 2-a beachhead result + Ant-topology corrections and deferred findings"
```

---

## Self-Review

**Spec coverage:** §3.2 (POM fix, harness untouched, expectation pinned) → Task 1; §5.1 (leaf verification with a switch path) → Task 2 Step 1; §5.2 (parent POM: compiler policy, add-exports/opens, no descriptor, manifest sections) → Task 2 Steps 2-3; §5.3 (local-file scaffolding, labelled as such) → Task 2 Step 4; §5.4 (coexistence) → Task 2 Step 6; §6.1-6.3 (parity through the real harness, no weakening, manifest proven not asserted) → Task 2 Step 5; §6.4 (the test seen to fail) → Task 1 Step 4; §6.5 (Ant still builds) → Task 2 Step 6; §3.4/§3.6/§8 (orphans, corrections, deferred map) → Task 3. No spec requirement lacks a task.

**Placeholder scan:** Task 1's test skeleton is explicitly marked to be adapted to the existing fingerprint-dict shape after reading a neighbouring test — the alternative would be transcribing a shape into the plan that could drift from the harness. Task 2 Step 2 extracts the build settings mechanically from `build.incl.xml` rather than hardcoding them here, for the same reason. Every other step carries a runnable command with expected output.

**Type consistency:** `fingerprint_jar` returns the `{entry_name: hash}` map used identically in Task 1's synthetic fixture and Task 2's real comparison; `diff_fingerprints` takes the whole-fingerprint dict with a `jars` key, which is the shape Task 1's test constructs.
