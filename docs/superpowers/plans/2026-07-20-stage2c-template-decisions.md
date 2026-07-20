# Stage 2-c — the template decisions, and the gate that would catch them — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Close the gap that lets a wrong Maven jar *name* pass parity, then settle the two
module-POM template decisions before the remaining 22 modules copy them.

**Design:** `docs/superpowers/specs/2026-07-20-stage2c-template-decisions-design.md` — read it for
rationale. This plan is task decomposition and exact commands only.

**Order is load-bearing.** Task 1 (the gate) must land before Task 2 (the rename), because Task 2's
entire deliverable is a filename and today's gate cannot see filenames.

## Global Constraints

- **NO HARNESS WEAKENING.** `fingerprint_jar`, `normalize_manifest`, and every existing section of
  `parity/diff.py` keep their current behavior. Task 1 is **additive** — a new section, not a
  relaxation. Two existing tests pin the harness against `META-INF/maven` exclusions; do not defeat
  them.
- **ADDITIVE to the build.** No edits to `build.incl.xml`, any `build.xml`, `cmake/`, or any
  `Makefile.am`. Ant keeps building every jar; Maven is still run by hand.
- **Do not pass `mvn -q` in any verification recipe.** It hid the warning that prompted Decision B.
  A silent `rc=0` is not evidence.
- **No AI-attribution trailers.**
- Full `build-parity` suite green (183 at stage start, HEAD `53fdef655ba`) plus new tests.

---

### Task 1: the `maven_jars` dimension (THE GATE — lands first)

**Files:** Modify `scilab/build-parity/parity/capture.py`, `scilab/build-parity/parity/diff.py`;
create `scilab/build-parity/tests/test_maven_jars.py`.

- [ ] **Step 1: Confirm the gap is real before fixing it.** Show that today's capture ignores
  Maven's output entirely:

```bash
cd scilab/build-parity && python3 -c "
from parity.capture import fingerprint_build, _default_roots
fp = fingerprint_build('..', _default_roots('..'))
print('jars keys mentioning target/ :', [k for k in fp['jars'] if 'target' in k] or 'NONE')
print('maven_jars section present   :', 'maven_jars' in fp)
"
```
(`_default_roots(build_dir)` returns a **dict** and is passed as the second positional argument —
see `tests/test_acceptance.py:31` for the canonical call. An earlier draft of this plan named a
non-existent `capture_fingerprint`; corrected here.)
Expected: `NONE` and `False`. **If either differs, stop and report** — the premise is wrong.

- [ ] **Step 2: Add the capture section.** Collect `modules/*/target/*.jar` (top level of `target/`
  only — not `classes/`, not `maven-archiver/`). Key each entry under its **Ant-equivalent** path,
  `modules/<m>/jar/<basename>`, so `maven_jars` and `jars` are directly comparable dicts. Follow
  RC-c's `_GENERATED_CMAKE_PATH_OVERRIDES` precedent and comment it the same way — the key is
  deliberately synthetic, and a future reader must not mistake it for the real on-disk location.
  Values are `fingerprint_jar` content manifests, identical in shape to `jars`.

- [ ] **Step 3: Add the transition rule** in `diff.py`, matching the established `rpaths`/`jars`
  idiom exactly: baseline without a `maven_jars` section → **skip**; candidate that **lost** a
  section the baseline had → **FAIL**.

- [ ] **Step 4: Fault injection — every case must be SEEN to fail.** Write
  `tests/test_maven_jars.py` covering all four, each asserting the diff reports a failure:

| Injection | Expected |
|---|---|
| Rename the Maven jar | FAIL — key added + key removed |
| Flip one byte in one class entry | FAIL — content differs |
| Delete the Maven jar | FAIL — key removed |
| Add a stray entry to the jar | FAIL — entry added |

Plus one transition-rule test per direction (baseline-absent → skip; candidate-lost → FAIL).

- [ ] **Step 5: Prove the new tests fail without the implementation.** Stash Step 2+3, run the new
  tests, confirm they fail; restore. Report the failure output. **A guard you have not seen fail is
  not a guard.**

- [ ] **Step 6: Suite + commit.**

```bash
cd scilab/build-parity && python3 -m pytest -q | tail -3
```

---

### Task 2: Decision A — reproduce Ant's jar names, and ARM the dimension

**Files:** Modify `scilab/pom.xml` (parent), `scilab/build-parity/tests/test_acceptance.py`.

> **Why this task leads with a test.** Task 1's review found that `maven_jars` is **dormant**:
> `baseline-autotools.json` has no `maven_jars` key, so the transition rule skips the section, and
> *nothing in the repo compares `jars` to `maven_jars`*. The dimension can therefore report `ok`
> however wrong Maven's output is. Arming it is this task's first job, not its last — and the check
> must be seen to FAIL before `<finalName>` exists, which is exactly what makes it a real gate.
>
> Re-baselining `baseline-autotools.json` is **the wrong fix**: its README says refresh it only when
> the autotools build legitimately changes, and doing so would fail for anyone who has not run
> `mvn`. A self-arming acceptance check is the right shape.

- [ ] **Step 1: Write the armed consumer FIRST — it must fail today.** In
  `scilab/build-parity/tests/test_acceptance.py`, gated on `maven_jars` being non-empty so it
  self-arms rather than needing a baseline refresh:

```python
def test_maven_jars_align_with_ant_jars(candidate_fingerprint):
    """Every Maven-built jar must have an Ant counterpart at the same key, with identical content.

    This is what makes the maven_jars dimension a GATE rather than a recorded observation:
    diff.py's transition rule only detects regression across runs, never disagreement between
    the two toolchains. Skips when no Maven jars are present so a pure-autotools tree is
    unaffected; fires the moment anyone runs `mvn package`.
    """
    mj = candidate_fingerprint.get("maven_jars", {})
    if not mj:
        pytest.skip("no Maven-built jars in this tree -- nothing to align")
    j = candidate_fingerprint["jars"]
    orphans = sorted(set(mj) - set(j))
    assert not orphans, f"Maven jars with no Ant counterpart (naming divergence?): {orphans}"
    differing = sorted(k for k in mj if mj[k] != j[k])
    assert not differing, f"Maven and Ant jars differ in content at: {differing}"
```

Match the file's existing fixture/idiom for obtaining the fingerprint rather than copying the
parameter name above verbatim — read `tests/test_acceptance.py` and follow what is there.

- [ ] **Step 2: Run it and watch it FAIL.** Expected: `orphans` lists
  `modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar` and the localization equivalent. **Quote the
  failure verbatim.** A guard you have not seen fail is not a guard. If it passes, stop and report —
  either the jars are absent (build them) or the check is not testing what it claims.

- [ ] **Step 3: One line in the parent POM's `<build>`**, inherited by all 24 modules:

```xml
<finalName>org.scilab.modules.${project.artifactId}</finalName>
```

Comment it with *why* (design §3): `etc/classpath.xml` hardcodes 23 module jars by path and name;
`etc/jvm_options.xml:20` hardcodes the JVM bootstrap entry; renaming would rewrite the running
application's classpath, which "reproduce, don't improve" forbids. Note explicitly that the output
**directory** stays `target/` during coexistence and flips at the CMake swap, and why (a shared
`jar/` would let a stray `mvn` run feed CMake a Maven jar undetectably).

- [ ] **Step 4: Rebuild both modules and confirm the names changed.**

```bash
cd scilab
# The rm -rf is LOAD-BEARING CORRECTNESS, not hygiene. target/ is never auto-cleaned, so a
# package without it leaves BOTH the old and new finalName jars on disk -- maven_jars then
# carries two keys against jars' one, and Step 5 fails on a spurious orphan that has nothing
# to do with <finalName>. Do not drop this line.
find modules/localization modules/commons -name target -type d -exec rm -rf {} + 2>/dev/null
mvn -pl modules/commons -am package 2>&1 | tail -15
ls -1 modules/localization/target/*.jar modules/commons/target/*.jar
```
Expected: `org.scilab.modules.localization.jar` and `org.scilab.modules.commons.jar`. Note (do not
suppress) any warnings now visible without `-q`.

- [ ] **Step 5: Re-run the Step 1 check — it must now PASS**, through the dimension rather than a
  hand-run snippet. Report the result. This is the transition the whole task exists to produce:
  a check that failed for a real reason now passes for a real reason.

- [ ] **Step 6: Prove the armed check still bites.** Rename one Maven jar on disk by hand, re-run,
  confirm **FAIL**, then restore. This is the assertion the old hand-run snippet could not make,
  and it guards against the check having been accidentally neutered while making it pass.

- [ ] **Step 7: Suite + commit.**

---

### Task 3: Decision B — record why `systemPath` stays

**Files:** Modify `scilab/modules/commons/pom.xml` (the flexdock comment only).

- [ ] **Step 1: Rewrite the flexdock rationale.** The current comment rejects a `file://` repository
  for the wrong reason (duplication/sync cost). Replace with the measured one (design §4): Maven
  mirrors match on repository **id**, not URL scheme, so this machine's
  `<mirrorOf>*,!maven.oracle.com,!smartnow-tech</mirrorOf>` intercepts a declared `file://` repo —
  a probe showed Maven never reading the local path, rewriting to Nexus (401) and an Azure feed →
  `BUILD FAILURE`. `systemPath` needs no resolution, so it is immune to mirror configuration.

- [ ] **Step 2: Record the expiry, do not bury it.** State that Maven 4 drops `system` scope, so
  these ~10 permanently-vendored jars need a different mechanism then — a genuine internal
  repository or `build-helper:attach-artifact` — and that this is a known dated cost, not a
  surprise.

- [ ] **Step 3: Confirm the warning is real and quote it.** Run without `-q`, capture the actual
  `systemPath ... should not point at files within the project directory` warning text, and cite it
  in the comment so a future reader knows it is expected rather than new breakage.

- [ ] **Step 4: Verify nothing moved.** Comment-only change → both jars must stay parity-green
  through the Task 1 dimension. Suite green. Commit.

---

## Self-Review

**Coverage:** design §2 → Task 1; §3 → Task 2; §4 → Task 3; §5 (doc correction) already committed in
`53fdef655ba`. Every design section maps to a task or is already done.

**Ordering:** Task 1 gates Task 2 by construction — stated at the top and enforced by Task 2 Step 4,
which cannot pass unless Task 1 works.

**Placeholders:** none. Every step is a runnable command or an exact XML line. The one value left to
the implementer is the `capture.py` insertion point, which must be read off the current file rather
than guessed.

**Consistency:** `maven_jars` keys use the same `modules/<m>/jar/<basename>` shape as `jars`, which
is what makes Task 2 Step 3's comparison a dict-to-dict diff rather than a bespoke mapping.
