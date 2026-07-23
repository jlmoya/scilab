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
| B3 | **`terminal` is GUI-gated and absent from the `prebuildjava` topo-sort.** It is the 24th jar but not in `prebuildjava/build.xml:25`'s 23-module `depends=` list; built only via its own `USEANT=1` path, gated `if GUI`. This is a fact about *Ant* and is permanent — out of scope for Maven to change (*reproduce, don't improve*). | `modules/terminal/Makefile.am:34` | Same as B1. | **RESOLVED for the Maven reactor (Wave F, `commit 44845dbf98c`):** `modules/terminal/pom.xml` added as the 24th `<module>` entry in the parent POM, byte-parity-green against the Ant jar. This closes the consequence the original row tracked — a shipping module the *reactor* never picked up — without touching the Ant-side fact in the Defect column, which stays true and unreproduced-away. Caught by a final review after Stage 2-f's Waves A-E had declared the reactor complete at 23/23; see `build-parity/tests/test_acceptance.py::_ant_modules_without_reactor_entry`, the bidirectional completeness check added in the same commit that closed this row. |
| B4 | **`debuglevel="lines,vars,source"` is dead text.** `build.incl.xml:129` reads `debug="${build.debug}"`, and `scilab.properties:3` sets `build.debug=off`, so `debuglevel` never applies — the real compile carries `-g:none`. | `build.incl.xml:129`, `scilab.properties:3` | Migrated POMs reproduce `-g:none`, which is what actually happens. | Delete the misleading attribute, or turn debug on deliberately. Cost a debugging cycle in Stage 2-a because the attribute was read without tracing the property. |
| B5 | **`ivy.xml` is dead.** No ivy jar exists anywhere and `~/.ant/lib` does not exist, so `ant download` cannot run; its `<!-- COPY -->` entries are hand-copy instructions, not resolution. | `modules/prebuildjava/ivy.xml`; migration doc §"ivy.xml is dead" | Untouched during migration — deleting it is out of scope for a parity-gated stage. | Delete once Maven owns dependencies. |
| B6 | **FlatLaf is fetched but unused.** `fetch-thirdparty.sh` pulls `flatlaf-3.4.1.jar`; its only reference in the tree is a comment. | `modules/ui_data/.../ScilabFileSelectorFilter.java:162` — `// TODO uncomment if using FlatLaf` | Not a build concern; recorded because the migration doc claimed it was "already bundled to replace the Swing L&F set", which overstated it. | Adopt it or stop fetching it. |
| B7 | **Cobertura is checked in but broken** — it calls `cobertura-*` tasks with no `taskdef`. | migration doc §5 | Out of scope for parity. | Drop it; JaCoCo if coverage is wanted. |
| B8 | **JUnit is 4.10 with thin coverage** (~40 Java classes). | migration doc §1 | Test migration is its own Stage 2 sub-task. | JUnit 5 + surefire. |
| B9 | **`etc/classpath.xml` carries machine-absolute paths** and is generated by configure. | `etc/classpath.xml` | Regenerating it is a named later Stage 2 task. | Emit relative/`$SCILAB`-rooted paths. |
| B10 | **~30 translation units silently lack `-fwrapv`** — the `_CFLAGS` footgun, where a per-target `_CFLAGS` overrides the global set. Relevant because this codebase has a documented class of UB miscompiled at `-O2`. | RC-b `tu_flag_facts`; see `docs/design/build-cmake-driver.md` | Reproducing autotools' behavior exactly is the rule; changing it would be an improvement. | Add the flag to the offending targets, then re-baseline. **Highest-risk item in this table** — it is a correctness exposure, not cosmetics. |
| B12 | **`javasci`'s `build.xml` overrides the shared `jar` target**, so its jar has a bare manifest — no per-package `Name:` section, no `Specification-*`/`Implementation-*`, only a computed `Class-Path`. `Package.getImplementationVersion("org.scilab.modules.javasci")` therefore returns null at runtime. | `unzip -p modules/javasci/jar/org.scilab.modules.javasci.jar META-INF/MANIFEST.MF` (vs any other module) | Stage 2-f Wave B reproduced it exactly — its POM's manifest fragment is deliberately bare and carries **no** `Implementation-Version`, the one documented exception to that constraint. Adding one would fabricate content Ant never emits. | Decide whether javasci should use the shared jar target like every other module (it would then gain the per-package section). Pre-existing; the migration only preserves it. |
| ~~B13~~ | **RETRACTED 2026-07-23 — the survey was wrong.** The original claim ("completion, graphic_objects, graphic_export declare a JOGL dependency they never use") rested on `grep -li jogl modules/*/pom.xml` → 6, which matches **comment prose**, not `<dependency>` elements. Checked properly: `grep -cE '<artifactId>(jogl-all\|gluegen-rt)</artifactId>'` → JOGL is declared **only** in `gui` (2), `renderer` (2), `scirenderer` (2) — all three are real `com.jogamp` consumers. The three "dead" modules declare **zero** JOGL deps; their jogl mentions are accurate explanatory comments (POM-ordering analogies; graphic_export's use of the Scilab `JoGLView`/`implementation.jogl.*` classes via reactor siblings). **There is no dead-dependency cleanup** — the whole `opengl-removal.md` Phase 1 was a mirage. Lesson: never conflate a comment-matching grep with a dependency audit. | | | Nothing to do; row kept as a correction so the mistaken claim is not re-derived. |
| B14 | **25 Ant-era `modules/*/build/` directories (14M) linger in the dev tree and were being copied into every `.app`.** Untracked and gitignored, zero files newer than `pom.xml`, and no runtime reference (`etc/classpath.xml` has zero `/build/` entries) — pure leftovers from before the Ant retirement. | `find modules -maxdepth 2 -type d -name build` → 25; `git ls-files 'modules/*/build/*'` → 0; same 25 dirs found inside `/Applications/Scilab-2027.0.0.app` | Not reproduced — a genuine leak. The Ant retirement deleted `modules/*/jar/` but left the sibling `build/` output dirs, and `package-macos.sh` rsyncs the dev tree, so they rode into the bundle. | **Half-fixed:** `package-macos.sh` now excludes `modules/*/build/`, so no future bundle carries them. The dev-tree copies are still on disk and safe to delete — deliberately left for a moment when a full rebuild can follow, rather than deleting 14M of untracked output mid-session. |
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

## 5a2. `machine.h` / `version.h` consumption cutover — RESOLVED 2026-07-23

Found while cleaning stale "hybrid coexistence" labels; fixed the same day. RC-a made CMake
**compute** `machine.h`/`version.h` into `build-cmake/generated-includes/` and PREPEND that dir to
the module include path (`ScilabConfigure.cmake`), the intent being that the compile consumes the
generated copies. It didn't — the compile fell through to the *source*
`modules/core/includes/{machine.h,version.h}`, which are **gitignored and untracked**, produced only
by the now-deleted `./configure`. A fresh clone has neither file, so it could not build.

**The real blocker was not the include path — it was that the generated `machine.h` did not compile.**
`cmake/machine.h.cmake.in` carried CMake-porting rationale in `/* … */` comments, and two token
classes leaked through `configure_file`:

1. the literal **`#cmakedefine`** in comment prose (10 lines across the CURL/LIBARCHIVE/LIBXML/
   PACKAGE_* blocks) — `configure_file` treats it as a directive and rewrote e.g.
   `/* Plain #define, not #cmakedefine … */` into `/* #undef  */`, spilling the rest as bare C tokens;
2. a comment that spelled out C block-comment delimiters literally (`` `/* ` `` … `` ` */` ``) — the
   embedded `*/` closed the comment early.

So the generated header had syntax errors (`unknown type name 'different'` at line 33), which is why
the compile silently kept using the source copy and why the cutover looked "done but inert."

**Fix:** stripped the `#` from `#cmakedefine` on comment lines only (never on the 169 real
directives, which start at column 0), and reworded the delimiter-spelling comment. The generated
`machine.h` now compiles clean (`clang -fsyntax-only`, 0 errors/warnings) and stays
**macro-for-macro identical to the source** (header-parity: 0 `#define`/`#undef` differences).
`ScilabToolchain.cmake`'s guard, which required the source `machine.h`, now checks a *tracked*
template (`version.h.in`) instead, so a fresh clone passes.

**Verified:** with both source headers renamed away (fresh-clone simulation) `cmake` configures
(exit 0, no stale FATAL) and `scicore-obj` + `scijvm` compile against the generated copies; a full
`drop-in-all` build with the source headers absent is the acceptance test. The gitignored source
copies are now legacy — present but unused (the prepended generated dir wins), and safe to delete.

---

## 5b. Capability lost in the migration — `make install` has no replacement

Found 2026-07-21 while auditing the scripts. **The CMake build defines zero `install()` rules**
(`grep -c '^\s*install(' CMakeLists.txt cmake/*.cmake` → 0). Retiring autotools therefore removed
`make install` and nothing took it over: the build is in-tree only.

This was invisible during the migration because the parity harness compares *build outputs* in the
tree, and macOS packaging (`package-macos.sh`) rsyncs a relocated copy of that tree — neither ever
calls install. It is a genuine capability regression, not a cosmetic one:

| Consumer | Effect |
|---|---|
| `.gitlab-ci/build.sh` | **Structurally broken past the install step**, not merely stale. Everything from the `patch binary` section on reads `/tmp/${SCI_VERSION_STRING}`, which only `make install DESTDIR=` populated. Now fails loudly with an explanatory message instead of silently packaging an empty tree. |
| `bin/scilab` | Its runtime search logic still supports the split-prefix layout (`/usr/local/{bin,share/scilab,lib/scilab,include/scilab}`) and the DESTDIR variant Linux packagers use. That code is live and must not be deleted — but **no build path in this tree can emit those layouts any more.** Header comment corrected to say so. |
| Linux/distro packaging | Not possible from this tree until install rules exist. macOS is unaffected (`package-macos.sh`). |

**Deferred: add `install()` rules to the CMake build.** Not urgent for this fork (macOS packaging
works without them), but it is the honest scope of what "autotools retired" cost, and it must be
closed before anyone packages this tree for a distro.

---

## 5c. CI pipeline — validated statically, and it CANNOT build this tree

Audited 2026-07-21 after an initial dismissal of `.gitlab-ci/prebuild.sh` as "third-party, not
ours" was correctly challenged. It is in this repository, so it is ours. Validating it found two
hard blockers that the dismissal would have missed.

**What static validation proves.** All four `.gitlab-ci/*.sh` parse (`bash -n`) and are
**shellcheck-clean at warning severity and above** — 0 findings; `prebuild.sh` has only 11
style-level notes and carries 14 explicit `shellcheck disable` directives, i.e. upstream lints it
deliberately. The shellcheck run was itself verified against a known-bad script (it reported
SC2154) rather than trusted for reporting zero. The scripts are well-maintained.

**What static validation cannot prove — and here does not hold: that they still WORK.** The image
they build predates the migration and is now incompatible with it:

| # | Blocker | Evidence | Effect |
|---|---|---|---|
| CI-1 | **No Maven anywhere in CI provisioning.** | Zero case-insensitive matches for `maven`/`mvn` in `prebuild.sh` and `docker_setup.sh`; `Dockerfile.linux`'s apt list has none. | `cmake/ScilabJava.cmake` resolves `find_program(SCILAB_MVN mvn)` off PATH and FATALs at point of use. The 24 module jars cannot be built. Repointing `build.sh` to CMake is necessary but **not sufficient** — the image lacks the tool. |
| CI-2 | **JDK 17, but the reactor compiles at source 25.** | `prebuild.sh:84` `JDK_VERSION=17.0.7+7`; `Dockerfile.linux:136` `openjdk-17-jdk`. Root `pom.xml:132` `<source>25</source>`. | javac 17 rejects source 25. Not a one-line bump: Debian bookworm has no openjdk-25, so it needs a JDK 25 tarball on the Scilab artifact mirror — **infrastructure this fork does not control**. |
| CI-3 | **Apache Ant still downloaded and installed** (`prebuild.sh:182,629-640`), and `ANT_HOME` exported. | Nothing consumes it: all 26 `build.xml` were deleted. | Dead weight in the image. Harmless, but it misleads a reader into thinking Ant is still part of the build. |

CMake itself **is** provisioned (`Dockerfile.linux.prebuild` builds it from source), so that half of
the migration is already satisfied. The autotools packages (`autotools-dev automake libtool`) stay:
third-party dependencies built from source in the image still need them, even though Scilab does not.

**Not fixed here, deliberately.** CI-1 and CI-2 are Linux-image changes that cannot be verified on
this machine — this fork's pipeline is disabled and upstream CI needs Dassault runners — and CI-2 is
additionally blocked on an artifact mirror we do not control. Writing plausible-but-unverified
Dockerfile edits would replace a *documented* blocker with a *hidden* one. Documented instead, so
re-enabling CI starts from a known list rather than a mystery.

**Fixed here:** the reachable-but-dead `-DSCILAB_JAVA_BUILD=ant` path in `cmake/ScilabJava.cmake`.
It would have located ant, launched it, and died inside ant with "Buildfile: build.xml does not
exist!" — blaming ant instead of the retirement. It now fails at configure time naming the real
cause. Verified in both directions: ant mode exits 1 with the new message, default maven mode still
configures clean (exit 0).

---

## 5d. JOGL is still the production graphics path — do not remove it

Raised 2026-07-21: since the Vulkan renderer landed, is JOGL still needed? **Yes.** Measured:

- 4 JOGL/GlueGen jars in `thirdparty/`, referenced by `etc/classpath.xml`
- **6** module POMs depend on JOGL
- **30** Java sources import `com.jogamp`
- `scirenderer` carries **both** implementations side by side — `implementation.jogl.*` and
  `implementation.vulkan.*` (see `modules/gui/pom.xml:36`)

The Vulkan renderer ([[realtime-3d-renderer]], merged `d30f75059e5`) was added **alongside** JOGL,
not in place of it. Removing JOGL is not a build-script cleanup; it requires porting 30 source files
across 6 modules and proving feature parity on every graphics surface. So `prebuild.sh` building
JOGL is **correct and still required** — the right call for the right reason, which is that it is
load-bearing for us, not that it is somebody else's code.

---

## 6. Downstream scope — the toolbox ecosystem (audited 2026-07-21)

Asked after the endgame: *"are the toolboxes built with Ant? If so they should be built with Maven
too."* Measured across the ~50 toolboxes in `SciLabProjects/`. **The intuitive half of the answer is
the empty one, and the real exposure is somewhere else.**

| Finding | Measurement | Consequence |
|---|---|---|
| **Toolboxes never used Ant.** | **0** Ant build files. The only 3 `build.xml` on disk are vendored **SWIG Android examples** under `sciQuantLib/swig/Examples/` — third-party sample code, never executed by any Scilab build. | Retiring Ant (`42e58dd3707`) cost the toolbox ecosystem **nothing**. No follow-up needed. |
| **Toolbox Java barely exists.** | **2** of ~50 toolboxes contain any `.java`: `swing-gpu-surface` (first-party, **already Maven**) and that same vendored SWIG tree. In-tree, `contrib/toolbox_skeleton` has exactly **one** `.java`. | **Do not Mavenize toolbox Java.** Forcing toolbox authors to install Maven to ship a jar is a usability regression against a population of one file. `ilib_build_jar` (`modules/dynamic_link/macros/`, a Scilab macro — no Ant, and no `javac` shell-out; a grep for "javac" false-positives on `javaclasspath`) is the correct Scilab-native API. |
| **The real legacy dependency is autotools — and it is LIVE, at runtime.** | `modules/dynamic_link/src/scripts/` ships a complete skeleton: `configure`, `configure.ac`, `Makefile.am/.in`, `aclocal.m4`, `ltmain.sh`, `config.guess`, `config.sub`, `depcomp`, `compile`, `missing`. Toolboxes compiling native code via `ilib_build`/`tbx_build_src` run **`./configure && make` on the end user's machine** (`ilib_compile.sci:157,165`). Preserved deliberately in the autotools purge — `1ed4171fad1` deleted **0** files under that path. | **This is the last live autotools in the tree** and the true parallel to the finished core migration. Deleting it breaks every toolbox's native build, so it was correctly kept. |

**Deferred: `ilib_build` → CMake.** Needs its own spec, not a task on this register. It is
higher-stakes than anything in the core migration for two reasons the core work never faced:
it **executes on end-user machines** (so a defect ships as a broken toolbox build, not a broken
local tree), and **toolboxes ship prebuilt binaries in the wild**, making backward compatibility a
hard constraint rather than a nicety. The parity question also inverts: the artifact to hold
constant is a *third-party toolbox's* built dylib, which this repository does not contain — so the
gate must be built from the toolbox catalog before any code moves.

---

## Known-unknown, worth stating

**Maven resolution here depends on machine-local configuration the repository does not carry.**
`~/.m2/settings.xml` mirrors everything to a Nexus that answers 401; resolution succeeds only via
an Azure DevOps feed excluded from that mirror. A fresh clone on another machine has none of this.
That is not a Scilab defect and not a harness gap — it is a portability gap that must close before
the endgame, since "delete autotools" implies the Maven build works for someone who is not us.
