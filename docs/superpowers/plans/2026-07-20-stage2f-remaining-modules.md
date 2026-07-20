# Stage 2-f — the remaining 15 modules, in dependency waves — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Finish Stage 2 — every one of the 23 `prebuildjava` modules building under Maven at jar
parity.

**State at start:** 8 of 23 migrated (`localization`, `commons`, `scirenderer`, `history_manager`,
`jvm`, `action_binding`, `graphic_objects`, `completion`). Suite **208**. HEAD `0e2e1d0f199`.

## The waves

Derived from actual imports, not from the topo list's ordering alone. Each wave's dependencies are
fully satisfied by the waves before it.

| Wave | Modules | Files | Notes |
|---|---|---|---|
| **A** | `console`, `helptools`, `types`, `external_objects_java` | 130 | All ready now — no unmigrated deps |
| **B** | `renderer`, `javasci`, `graphic_export` | 80 | `renderer`←console, `javasci`←types, `graphic_export`←renderer |
| **C** | `gui` | 351 | The `Class-Path` wrap case, and the largest single module until xcos |
| **D** | `core`, `history_browser`, `graph`, `ui_data` | 211 | All ←gui |
| **E** | `scinotes`, `preferences`, `xcos` | 450 | `scinotes`←core+gui+helptools; `preferences`←scinotes; `xcos`←core+graph+javasci+types |

**`gui` is the one to watch.** It is the only remaining module declaring `manifest.class-path`, and
its six-entry value **wraps at 72 bytes mid-token** (`javafx.b` / `ase.jar`). Ant wraps at 70
(`org.apache.tools.ant.taskdefs.Manifest` reserves room for CRLF); Maven's plexus-archiver uses the
full 72, and **no POM content can change that** — measured, both wrapped and unwrapped input give
the same break. `normalize_manifest` now joins continuation lines, so this should simply pass. **If
`gui`'s manifest fails parity, that is a real finding — report it, do not work around it.**

## Global Constraints

- **NO HARNESS WEAKENING.** `fingerprint_jar`, `normalize_manifest`, `diff.py` unchanged. If a jar
  fails parity, **the POM is wrong.**
- **Do NOT re-baseline `baseline-autotools.json`**, and never add a `maven_jars` section to it —
  `test_committed_baseline_carries_no_maven_jars_section` enforces this; read its docstring.
- **ADDITIVE.** No edits to `build.incl.xml`, any `build.xml`, `cmake/`, or any `Makefile.am`.
- **Never `mvn -q`.** Report warnings.
- **No AI-attribution trailers.**

## The settled template — apply, do not rediscover

1. **Run `./maven-module-deps.sh <module>` first, every time.** It reports imports, reflection
   targets that must **not** become dependencies (they would cycle the reactor), and
   fully-qualified uses with no import — the class that has twice cost a build iteration. Section 3
   over-reports by design (it matches comments and javadoc); open the file before believing it.
2. `<source>`/`<target>`, **never** `<release>`. 3. `<manifestFile>`, **never**
   `<manifestSections>`. 4. Explicit `-g:none`. 5. maven-archiver pinned 3.6.4.
   6. `<addMavenDescriptor>false</...>`. Most are inherited — verify, don't assume.
7. **XML comments cannot contain `--` anywhere** — use `—`. This has landed in **five consecutive
   stages**. `xmllint --noout` every POM, and grep your own comment bodies before committing.
8. **Extract manifest bytes from the Ant jar; never retype.** Vendor string is double-encoded (`Ã¨`).
9. **`Implementation-Version` must be PRESENT** as frozen `00000000 0000`, between
   `Implementation-Title` and `Implementation-Vendor`. The harness **cannot see** its absence;
   `getImplementationVersion()` returns null without it and Scilab reads it at runtime.
10. **Build the FULL reactor** (`mvn package`, no `-pl`) after any broad `target/` clean — the
    completeness check requires every reactor module to have a jar.
11. **The pinned reactor test goes red by design.** UPDATE its exact list and counter; **never**
    weaken it to a truthiness check.

Per-module `<finalName>` overrides are **not** expected here — `scirenderer` was the only one of 24
breaking the `org.scilab.modules.<dir>.jar` convention. If any module needs one, say so loudly.

---

### Task 1 — Wave A: `console`, `helptools`, `types`, `external_objects_java`

- [ ] **Step 1:** `./maven-module-deps.sh <m>` for each; record output. Any dependency on an
  unmigrated module means the wave analysis is wrong — **stop and report**, do not reorder.
- [ ] **Step 2:** Write four POMs + manifest fragments; add to `<modules>` in topo order.
- [ ] **Step 3:** Update the pinned reactor test (8 → 12 entries, counter 16 → 12).
- [ ] **Step 4:** Full-reactor build from cleaned `target/`; `test_maven_jars_align_with_ant_jars`
  must pass. Verify `Implementation-Version` in each **built** jar.
- [ ] **Step 5:** Coexistence check, full suite, commit.

### Task 2 — Wave B: `renderer`, `javasci`, `graphic_export`

- [ ] Same five steps. Pinned test 12 → 15, counter 12 → 8.
- [ ] `graphic_export` imports `renderer`, so both land in this wave; confirm the reactor orders
  them correctly via the **Reactor Build Order preamble** of a full `mvn clean package`.

### Task 3 — Wave C: `gui`

- [ ] Same five steps. Pinned test 15 → 16, counter 8 → 7.
- [ ] **Report the `Class-Path` manifest entry explicitly** — both the Ant bytes and the Maven
  bytes, and whether parity passed. This is the one module the continuation-line work was done for.
- [ ] `gui` has six vendored jars on its class-path (`flexdock`, `jrosetta-engine`, `jrosetta-API`,
  and three JavaFX). Use the established `systemPath` mechanism; cross-reference `commons`' comment.

### Task 4 — Wave D: `core`, `history_browser`, `graph`, `ui_data`

- [ ] Same five steps. Pinned test 16 → 20, counter 7 → 3.

### Task 5 — Wave E: `scinotes`, `preferences`, `xcos`

- [ ] Same five steps. Pinned test 20 → 23, counter 3 → 0.
- [ ] **This completes the reactor.** Verify all 23 modules build from a single
  `mvn clean package` at the reactor root, and quote the Reactor Build Order preamble in full.

### Task 6 — Record completion (CONTROLLER)

- [ ] Update `docs/design/build-cmake-maven-migration.md`: Stage 2 module migration complete,
  23 of 23. Record what remains before Ant can be deleted — the CMake↔Maven swap,
  `etc/classpath.xml` regeneration, JUnit 4→5 + surefire, and the CI gap (H1 in the register:
  no CI job runs `mvn`, so the alignment test is a permanent skip there today).
- [ ] Update the register with any new warts the waves surfaced.

---

## Self-Review

**Coverage:** all 15 remaining modules appear exactly once, in a wave whose dependencies are
satisfied by earlier waves. Counts were derived from actual imports.

**Ordering:** waves are a dependency partial order, not the topo list read literally — `helptools`,
`types` and `external_objects_java` are ready immediately despite sitting late in `prebuildjava`'s
sequence, because that sequence is one valid linearization, not the only one.

**Placeholders:** none. The one open outcome is Task 3's `gui` manifest, which is a genuine question
the continuation-line fix was built to answer.
