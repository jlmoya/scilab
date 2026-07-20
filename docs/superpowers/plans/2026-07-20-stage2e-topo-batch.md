# Stage 2-e — the bulk phase begins: modules 4–8 in topological order — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Migrate the next five modules in `prebuildjava`'s proven topological order —
`history_manager`, `jvm`, `action_binding`, `graphic_objects`, `completion` — batched rather than
one stage each, because the template is settled and no unproven mechanic remains.

## Why batched now, and why these five

Stages 2-a…2-d were beachheads: each existed to prove one mechanic, and each found real traps worth
the isolation. That phase is **over**. Reconnaissance (2026-07-20) confirms:

- **The topo-sort is already solved for us.** `modules/prebuildjava/build.xml:25` hand-encodes a
  23-module order that has built correctly for years. The three modules migrated so far —
  `scirenderer`, `localization`, `commons` — are *exactly* its first three. Following it means
  every module's dependencies are already in the reactor when its turn comes.
- **These five are next, and small.** 3, 4, 6, 112 and 4 source files. Every dependency is already
  migrated: `history_manager`→commons; `jvm`→none; `action_binding`→commons+localization;
  `graphic_objects`→action_binding+commons+scirenderer+localization; `completion`→localization.
- **No new mechanic appears.** None declares `manifest.class-path` (only `gui` and `scirenderer`
  ever did). None has real resources — the non-`.class` jar entries are **directory entries**, and
  Maven already reproduces those exactly (verified: commons 10/10, localization 5/5,
  scirenderer 44/44 against Ant).

**`gui` is NOT next**, despite being the obvious candidate. It imports **nine** Scilab modules and
sits **12th** in the topo order. It is a milestone, not an increment.

## The one genuinely new thing: `jvm` exercises the JDK-internals policy

`modules/jvm/.../LibraryPath.java` imports `jdk.internal.loader.NativeLibraries` and
`sun.misc.Unsafe`, and reflectively rewrites `LibraryPaths.USER_PATHS`. It does not compile under
JDK 17+ encapsulation without `--add-exports java.base/jdk.internal.loader=ALL-UNNAMED` plus the
two `--add-opens`. Those are **already in the parent POM** — but no migrated module has needed them,
so this is the first time the policy is actually exercised rather than merely present. If `jvm`
compiles, the policy is proven; if it does not, the parent POM is wrong and that is the finding.

## Global Constraints

- **NO HARNESS WEAKENING.** `fingerprint_jar`, `normalize_manifest`, `diff.py` unchanged. If a jar
  fails parity, **the POM is wrong.**
- **ADDITIVE.** Create each module's `pom.xml` + manifest fragment; add each to the parent's
  `<modules>`. **No edits** to `build.incl.xml`, any `build.xml`, `cmake/`, or any `Makefile.am`.
- **Never `mvn -q`.** A silent `rc=0` is not evidence.
- **No AI-attribution trailers.**
- Suite green (**207** at stage start, HEAD `65baec3b4c8`).

## The settled template — apply it, do not rediscover it

Copy `modules/commons/pom.xml` (has a vendored dep) or `modules/localization/pom.xml` (simplest).
Everything below is inherited from the parent unless noted; verify inheritance rather than assuming.

1. `<source>`/`<target>`, **never** `<release>` — breaks with `--add-exports` on `java.base`.
2. `<manifestFile>`, **never** `<manifestSections>` — scrambles attribute order, and turns
   `${manifest.class-path}` into the literal `null`.
3. Explicit `-g:none`. 4. maven-archiver pinned 3.6.4. 5. `<addMavenDescriptor>false</...>`.
6. **XML comments cannot contain `--` anywhere** — use `—`. This has landed in **four consecutive
   stages**, once via a shell flag quoted literally. Run `xmllint --noout` on every POM.
7. **Extract manifest bytes from the Ant jar; never retype.** The vendor string is double-encoded
   (`Ã¨`) — a correct `è` compiles, looks right, and silently fails parity.
8. **`Implementation-Version` must be PRESENT** as a frozen `00000000 0000`, positioned exactly
   where Ant puts it (between `Implementation-Title` and `Implementation-Vendor`). Absent means
   `getImplementationVersion()` returns null at runtime and **the harness cannot see it** — it
   strips the line from both sides. This shipped undetected in 2-a/2-b; do not repeat it.
9. `target/` is never auto-cleaned — `rm -rf` before any rebuild or a stale jar fails spuriously.
10. **Adding a module turns `test_reactor_modules_parses_real_pom_non_vacuously` red by design.**
    UPDATE its pinned exact list and the "N more migrations" counter. **Never** weaken it to a
    truthiness check — that is the check's whole point.

---

### Task 1: `history_manager`, `jvm`, `action_binding`

13 source files total. Add all three in topo order.

- [ ] **Step 1: Confirm each module's shape before writing its POM.**

```bash
cd scilab
for m in history_manager jvm action_binding; do
  echo "--- $m"
  grep -rhE '^import (org|com|jdk|sun)\.' modules/$m/src/java --include='*.java' | sort -u
  grep -n 'manifest.class-path\|library.title' modules/$m/build.xml
  unzip -p modules/$m/jar/org.scilab.modules.$m.jar META-INF/MANIFEST.MF
done
```
Expected: no `manifest.class-path` anywhere; `jvm` shows `jdk.internal.loader` / `sun.misc`.
**Any module importing something not yet in the reactor — stop and report**, do not reorder.

- [ ] **Step 2: Write the three POMs + manifest fragments**, extracting manifest bytes per
  constraint 7, and add all three to the parent `<modules>` **after `commons`** in topo order.

- [ ] **Step 3: `jvm` is the JDK-internals proof.** Report explicitly whether it compiled, and
  quote any `--add-exports`/`--add-opens`-related warning. If it fails, **stop** — the parent POM's
  policy is wrong and that is a finding worth more than finishing the batch.

- [ ] **Step 4: Update the pinned reactor test** per constraint 10 (3 → 6 entries, counter 21 → 18).

- [ ] **Step 5: Parity through the dimension**, from a cleaned `target/`:

```bash
cd scilab
find modules -maxdepth 2 -name target -type d -exec rm -rf {} + 2>/dev/null
for m in history_manager jvm action_binding; do (cd modules/$m && ant) || exit 1; done
mvn -pl modules/history_manager,modules/jvm,modules/action_binding -am package 2>&1 | tail -20
cd build-parity && python3 -m pytest tests/test_acceptance.py -q -k maven_jars 2>&1 | tail -3
```
Expected: three jars named `org.scilab.modules.<m>.jar`, alignment test PASSES.
**Report every iteration it took.**

- [ ] **Step 6: Coexistence + suite + commit.**

```bash
cd scilab && git status --short -- build.incl.xml '*/build.xml' cmake/ '*/Makefile.am' | head
cd build-parity && python3 -m pytest -q | tail -2
```

---

### Task 2: `graphic_objects`, `completion`

116 source files. `graphic_objects` depends on `action_binding` from Task 1, so this task must
follow it.

- [ ] **Step 1: Confirm shape** as in Task 1 Step 1, for both modules.
  `graphic_objects` has **52 directory entries** in its jar — the largest so far. Maven reproduces
  directory entries correctly (verified on scirenderer's 44), but confirm it here rather than
  assuming, since this is the first module where they outnumber anything else notable.

- [ ] **Step 2: Write both POMs + fragments**, add to `<modules>` in topo order
  (`graphic_objects` then `completion`).

- [ ] **Step 3: Update the pinned reactor test** (6 → 8 entries, counter 18 → 16).

- [ ] **Step 4: Parity through the dimension**, cleaned `target/`, as Task 1 Step 5.

- [ ] **Step 5: Prove the reactor still orders correctly at eight modules.** Run
  `mvn clean package` from the **reactor root** (not `-pl`) and confirm the printed
  *"Reactor Build Order"* respects the dependency graph — `action_binding` before
  `graphic_objects`. That preamble is computed from the resolved graph before any module builds,
  so it is direct evidence rather than an after-the-fact summary.

- [ ] **Step 6: Coexistence + suite + commit.**

---

### Task 3: Record the phase change (CONTROLLER)

- [ ] **Step 1:** Add a Stage 2-e entry to `docs/design/build-cmake-maven-migration.md`: eight of
  23 modules migrated, following `prebuildjava`'s topo order; the beachhead phase is over and the
  remainder is mechanical; `jvm` proved (or disproved) the JDK-internals policy.
- [ ] **Step 2:** Record that **`gui` is 12th, not next** — it imports nine Scilab modules — so
  nobody reaches for it again as "the obvious next one".
- [ ] **Step 3: Commit.**

---

## Self-Review

**Coverage:** every module named has a POM step and is gated by the same armed alignment test; the
one genuinely new thing (`jvm`'s JDK internals) has its own step with an explicit stop condition.

**Ordering:** Task 2 follows Task 1 because `graphic_objects` imports `action_binding`. Task 2
Step 5 verifies ordering is still real at eight modules rather than assuming it scales.

**Placeholders:** none. The one deliberately open outcome is Task 1 Step 3 — whether `jvm` compiles
under the inherited `--add-exports` policy — which is a real question this stage exists to answer,
not an unspecified step.

**Consistency:** constraint 10 appears once and both tasks reference it with their own exact
counts, so neither implementer has to derive them.
