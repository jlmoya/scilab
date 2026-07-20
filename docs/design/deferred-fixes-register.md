# Deferred fixes register — what "reproduce, don't improve" is deliberately preserving

**Status:** live index, opened 2026-07-20 (Stage 2-d). Update it in the same commit that reproduces
a wart, not later.

## Why this file exists

The build-modernization campaign's binding rule is **reproduce, don't improve**: CMake and Maven
must produce the same application autotools and Ant produce, warts included. That rule is right —
it keeps "did the build change?" separable from "did the product change?", which is the only reason
the parity harness can mean anything.

But it has an obvious failure mode: **faithfully reproduced defects become permanent** if nobody
writes them down. Until this file existed, they were recorded in three places with very different
survival odds — POM comments (good at stopping an accidental "fix", useless for enumeration),
design-doc prose (buried, per-stage), and the SDD progress ledger (**git-ignored scratch**, deleted
by `git clean -fdx`). Nobody could answer "what do we fix once the migration lands?"

**The rule for contributors:** when you reproduce something wrong because parity demands it, add a
row here in the same commit, and cite the file:line. A comment at the site is necessary but not
sufficient — the site comment stops the next reader from breaking parity; this file is what makes
the defect findable afterwards.

**Scope note.** Sections 1 and 2 are defects in *Scilab*. Section 3 is limitations in *our own
harness*. Section 4 is migration scaffolding that disappears by construction. Section 5 is
divergences accepted on purpose, forever. Keep them separate — they have different owners and
different "done" conditions.

---

## 1. Product defects — user-visible or shipped

These reach users. Highest priority once the migration lands.

| # | Defect | Evidence | Why reproduced | Proposed fix |
|---|---|---|---|---|
| P1 | **Every jar manifest carries a double-encoded vendor string** — `"Dassault SystÃ¨mes"` instead of `Systèmes`. Ant's `<property file>` reads `scilab.properties`'s UTF-8 `è` (`c3 a8`) as Latin-1 and re-encodes, giving `c3 83 c2 a8`. | Any jar: `unzip -p modules/commons/jar/org.scilab.modules.commons.jar META-INF/MANIFEST.MF` | The manifest is inside the parity gate; emitting the correct string fails parity. Every migrated POM extracts the mangled bytes deliberately. | Load the properties file as UTF-8 and re-baseline the manifest hashes in one deliberate commit. |
| P2 | **22 of 24 shipped jars have `Class-Path: ${manifest.class-path}`** — an uninterpolated Ant property literal, because only `gui` and `scirenderer` define the property and `build.incl.xml` emits the attribute unconditionally. | `build.incl.xml:159`; `unzip -p modules/types/jar/org.scilab.modules.types.jar META-INF/MANIFEST.MF` | Same reason as P1 — it is a manifest byte inside the gate. | Emit `Class-Path` only when the property is set. Harmless today (the entry simply never resolves), but it is junk in every released artifact. |
| P4 | **`graphic_objects`' jar ships `Specification-Title: ${library.title}`** — an uninterpolated Ant property literal, because `graphic_objects/build.xml` is the only real Java module that never defines `library.title`, and `build.incl.xml` stamps the attribute unconditionally. Same family as P2. | `unzip -p modules/graphic_objects/jar/org.scilab.modules.graphic_objects.jar META-INF/MANIFEST.MF` | Stage 2-e reproduced it verbatim; the manifest is inside the parity gate. | Define `library.title` for the module (or omit the attribute when unset, as P2 proposes for `Class-Path`). Fix both together — they are one bug in `build.incl.xml`'s unconditional stamping, seen twice. |
| P3 | **`scirenderer`'s manifest section names a package that does not exist in the jar.** Section header is `Name: org/scilab/modules/scirenderer/`; the classes are `org.scilab.forge.scirenderer.*`. Generated mechanically from `${ant.project.name}`, so the section is inert — `Package.getPackage("org.scilab.modules.scirenderer")` never resolves. | `unzip -p modules/scirenderer/jar/scirenderer.jar META-INF/MANIFEST.MF` | Stage 2-d reproduces it exactly; "correcting" it to the real package would be an improvement and a parity divergence. | Point the section at `org/scilab/forge/scirenderer/`, or drop it — but note the per-package section is load-bearing elsewhere (xcos reads `getSpecificationVersion()`/`getImplementationVersion()` and stamps them into saved `.xcos` diagrams), so decide per module, not globally. |

## 2. Build-system defects — not user-visible, but real

| # | Defect | Evidence | Why reproduced | Proposed fix |
|---|---|---|---|---|
| B1 | **`output_stream` is built by nothing.** It has a `build.xml`, `prebuildjava/build.xml` defines an unreachable target for it, its `Makefile.am` sets no `USEANT`, and `scilab-lib.properties:170-172` points at a jar no build produces (`modules/output_stream/jar/` does not exist). | `scilab-lib.properties:170-172`; `ls modules/output_stream/jar` → absent | A reactor forces the question; Stage 2-a chose to preserve today's behavior rather than answer it mid-migration. | Decide: revive it or delete the module and its dangling properties. |
| B2 | **`scirenderer` is Ant-only** — a `build.xml` with no `Makefile.am` and no `SUBDIRS` entry, reached solely through `prebuildjava`. | `modules/scirenderer/` has no `Makefile.am` | Same as B1. | Fold into the normal module shape, or document it as intentionally special. |
| B3 | **`terminal` is GUI-gated and absent from the topo-sort.** It is the 24th jar but not in `prebuildjava/build.xml:25`'s 23-module `depends=` list; built only via its own `USEANT=1` path, gated `if GUI`. | `modules/terminal/Makefile.am:34` | Same as B1. | Bring it into the reactor uniformly once Maven owns ordering. |
| B4 | **`debuglevel="lines,vars,source"` is dead text.** `build.incl.xml:129` reads `debug="${build.debug}"`, and `scilab.properties:3` sets `build.debug=off`, so `debuglevel` never applies — the real compile carries `-g:none`. | `build.incl.xml:129`, `scilab.properties:3` | Migrated POMs reproduce `-g:none`, which is what actually happens. | Delete the misleading attribute, or turn debug on deliberately. Cost a debugging cycle in Stage 2-a because the attribute was read without tracing the property. |
| B5 | **`ivy.xml` is dead.** No ivy jar exists anywhere and `~/.ant/lib` does not exist, so `ant download` cannot run; its `<!-- COPY -->` entries are hand-copy instructions, not resolution. | `modules/prebuildjava/ivy.xml`; migration doc §"ivy.xml is dead" | Untouched during migration — deleting it is out of scope for a parity-gated stage. | Delete once Maven owns dependencies. |
| B6 | **FlatLaf is fetched but unused.** `fetch-thirdparty.sh` pulls `flatlaf-3.4.1.jar`; its only reference in the tree is a comment. | `modules/ui_data/.../ScilabFileSelectorFilter.java:162` — `// TODO uncomment if using FlatLaf` | Not a build concern; recorded because the migration doc claimed it was "already bundled to replace the Swing L&F set", which overstated it. | Adopt it or stop fetching it. |
| B7 | **Cobertura is checked in but broken** — it calls `cobertura-*` tasks with no `taskdef`. | migration doc §5 | Out of scope for parity. | Drop it; JaCoCo if coverage is wanted. |
| B8 | **JUnit is 4.10 with thin coverage** (~40 Java classes). | migration doc §1 | Test migration is its own Stage 2 sub-task. | JUnit 5 + surefire. |
| B9 | **`etc/classpath.xml` carries machine-absolute paths** and is generated by configure. | `etc/classpath.xml` | Regenerating it is a named later Stage 2 task. | Emit relative/`$SCILAB`-rooted paths. |
| B10 | **~30 translation units silently lack `-fwrapv`** — the `_CFLAGS` footgun, where a per-target `_CFLAGS` overrides the global set. Relevant because this codebase has a documented class of UB miscompiled at `-O2`. | RC-b `tu_flag_facts`; see `docs/design/build-cmake-driver.md` | Reproducing autotools' behavior exactly is the rule; changing it would be an improvement. | Add the flag to the offending targets, then re-baseline. **Highest-risk item in this table** — it is a correctness exposure, not cosmetics. |
| B11 | **Dead Makefile rules that collide with live ones** — `elementary_functions`' `libdummy`, and `CommandHistory_Wrap_Fake.c` (commented out by `config.status`). | RC-b live-rule filter | Not reproduced — worked around in the harness. Recorded so nobody "fixes" the filter without knowing why it exists. | Delete the dead rules. |

## 3. Harness limitations — gaps in our own gate

Not Scilab defects. These bound what parity can currently prove; each fails **red** or **silent** as noted, and silent ones are the dangerous kind.

| # | Limitation | Fails | Fix when |
|---|---|---|---|
| H1 | **`maven_jars` is a permanent skip in CI.** The pytest job runs on `debian:bookworm-slim` (self-skips); the native parity job runs `capture`+`diff`, not pytest; **no CI job runs `mvn` at all**. | Silent | Maven is wired into CI — pair the alignment test with the completeness assertion, or the dimension goes dormant exactly where it matters most. |
| H2 | **Completeness is module-granular** — `_missing_reactor_jars` asks only whether a module produced ≥1 Maven jar. A module whose Ant build emits two jars and Maven one would pass. | Silent | A module emits more than one jar (none do today). |
| H3 | **Profile-conditional `<modules>` are invisible** to the completeness parse (`root.findall("{ns}modules/{ns}module")` matches only a direct child of `<project>`). | Silent | Migrations get gated behind an opt-in profile. Update the exact-list test; **never** weaken it to a truthiness check. |
| H4 | **Nested `target/` is invisible** — only `modules/<m>/target` is walked, so an extra artifact at `modules/<m>/sub/target/` escapes. A *declared* nested reactor module fails loudly via completeness; only extra artifacts are silent. | Silent (extras only) | A multi-artifact module appears. Do **not** broaden the walk without re-reading the laundering bug it would reintroduce. |
| H5 | **`flags` captures only global per-language flags** — per-TU overrides (e.g. `colnew.f` forced to `-O0`) are invisible to it. Superseded in practice by RC-b's `tu_flag_facts`. | Silent | Retiring or merging the older dimension. |
| H6 | **Manifest comparison cannot distinguish "attribute absent" from "attribute present with a volatile value"** — `normalize_manifest` strips volatile lines from both sides. This is how P-class defect Stage 2-c Critical 1 hid: the template dropped `Implementation-Version`, the harness saw no difference, and `xcos` would have written `null` into user diagrams. | Silent | Now mitigated by convention (every manifest fragment carries a frozen `00000000 0000`), not by the harness. A structural fix would compare attribute *presence* separately from value. |

## 4. Migration scaffolding — removed by construction

Recorded only so nobody mistakes them for permanent design. All disappear at the endgame
(**autotools deleted** — migration doc §12).

- Ant and Maven both build every jar; Ant is the real build, Maven is run by hand.
- Maven writes to `modules/<m>/target/`, not `modules/<m>/jar/`, deliberately — a shared directory
  would let a stray `mvn` run feed CMake a Maven jar undetectably. The directory flips at the CMake swap.
- `<scope>system</scope>` + `<systemPath>` for permanently-vendored jars. Correct today and
  immune to mirror interception, but `system` scope is deprecated; a Maven 4 move needs a different
  mechanism (an internal repository, or `build-helper:attach-artifact`).
- The `config.status` version-triple dependency, which blocks RC-e (deleting `./configure`).

## 5. Accepted divergences — differences that are permanent and harmless

Not a defect in Scilab, not a harness gap, and not scaffolding that disappears at the endgame.
These are places where Maven's output provably differs from Ant's at the byte level, **forever**,
with no behavioral consequence — so parity is measured on the reconstituted *meaning*, not the raw
bytes that encode it.

| # | Divergence | Why accepted | Harness handling | Sensitivity proof |
|---|---|---|---|---|
| A1 | **Long manifest attribute values wrap at a different byte offset.** Ant's `org.apache.tools.ant.taskdefs.Manifest` breaks continuation lines at 70 bytes (`MAX_LINE_LENGTH - 2`, reserving room for the trailing CRLF); Maven's archiver stack (maven-archiver/plexus-archiver) breaks at the full 72. **Neither writes through `java.util.jar.Manifest`** — an earlier draft of the Stage 2-d doc claimed they did and would "therefore" agree; a reviewer measured otherwise. Reproduced with `gui`'s real six-entry `Class-Path` (`flexdock.jar jrosetta-engine.jar jrosetta-API.jar javafx.base.jar javafx.swing.jar javafx.graphics.jar`, 114 bytes): Ant breaks mid-token at `javafx.b` / `ase.jar`, Maven at `javafx.bas` / `e.jar`. | A manifest's meaning is `{attribute: value}`; the wrap position is a serialization artifact of the 72-byte-per-line limit, invisible to every real consumer — `java.util.jar.Manifest` reconstitutes the logical value when a jar is read, so the JVM classloader resolving `Class-Path` never sees where the writer broke the line. **No POM content can change either break position** — verified by feeding Maven both the pre-wrapped and the unwrapped form of the same value and getting the identical 72-byte break both times, so there is nothing to fix on our side. | `normalize_manifest` (`build-parity/parity/fingerprint.py`) now joins continuation lines — a single leading space starts a continuation, stripped when joined — **before** the volatile-line filter, then compares the reconstituted value instead of the literal bytes. A correction, not a weakening: it only makes two *different* wrap positions of the *same* value compare equal. | `build-parity/tests/test_jar.py`: `test_normalize_manifest_same_value_different_wrap_position_compares_equal` (same value, different wrap → equal) plus three siblings (`..._changed_value_still_caught_when_wrapped`, `..._removed_attribute_still_caught_when_wrapped`, `..._added_attribute_still_caught_when_wrapped`) proving a changed, removed, or added wrapped attribute still fails. |

---

## Known-unknown, worth stating

**Maven resolution here depends on machine-local configuration the repository does not carry.**
`~/.m2/settings.xml` mirrors everything to a Nexus that answers 401; resolution succeeds only via
an Azure DevOps feed excluded from that mirror. A fresh clone on another machine has none of this.
That is not a Scilab defect and not a harness gap — it is a portability gap that must close before
the endgame, since "delete autotools" implies the Maven build works for someone who is not us.
