# Stage 2-a — the Maven beachhead — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-19
**Depends on:** Stage 1 (CMake drives the native build and invokes Ant via `sci-java-all`) and
retire-configure RC-a…RC-d. HEAD `554d38cf1c8`. Strategy:
`docs/design/build-cmake-maven-migration.md` (Stage 2).

## 1. Goal

Prove the Maven mechanics and the parity gate on **one real module**, end to end, before any
large-scale migration work is committed to. Stage 2-a produces a parent POM, one leaf module's POM,
and a **byte-parity-green Maven-built jar** measured by the existing harness — nothing else.

This is deliberately a beachhead, not a phase. The full Stage 2 decomposition (§8) stays unbuilt
until this proves the approach.

## 2. Why a beachhead, and why this shape

Stage 2 replaces the build system for 24 jars whose ordering is hand-encoded, whose dependencies live
in four disagreeing places, and whose 78 vendored jars mostly have no recorded Maven coordinates. The
expensive part — researching coordinates for ~32 undeclared jars — is **decoupled** from the
question "do the reactor mechanics and the parity gate work on real Maven output?"

So: answer the cheap question first, using the existing `thirdparty/*.jar` files as local artifacts.
If the beachhead is green, the coordinate research becomes ordinary work on a proven foundation. If
it is not, we learn that before spending days on dependency archaeology.

## 3. Background — verified findings that shape this stage

### 3.1 A runtime behavior dependency hides inside the manifest

`build.incl.xml:148-163` stamps each jar with a **per-package manifest section**
(`<section name="org/scilab/modules/${ant.project.name}/">`) carrying `Specification-Title/Version/
Vendor` and `Implementation-Title/Version/Vendor`.

That is **not cosmetic**. `modules/xcos/src/java/org/scilab/modules/xcos/io/codec/
XcosDiagramCodec.java:304-305` and `.../io/writer/CustomWriter.java:121` call
`Package.getSpecificationVersion()` / `getImplementationVersion()` — populated by the JVM *from that
per-package section* — and stamp the result into saved `.xcos` files.

Maven's default is a flat manifest. A naive POM compiles cleanly, passes casual inspection, and
silently changes what gets written into users' saved diagrams. `maven-jar-plugin`'s
`<manifestSections>` is required, not optional.

### 3.2 The parity gate will reject Maven's default output — and that is correct

`maven-jar-plugin` embeds `META-INF/maven/<groupId>/<artifactId>/pom.xml` and `pom.properties` by
default (`addMavenDescriptor`, on unless disabled). The harness hashes jar entries by name
(`parity/capture.py`'s `fingerprint_jar`) and `parity/diff.py` reports any candidate-only entry as
`entry added`.

**The fix belongs in the POM, not the harness.** Ant's jars contain no `META-INF/maven/`, so a jar
that carries one is a genuine divergence from the artifact we are reproducing — the gate flagging it
is the gate working. Excluding the path in the harness would weaken a strict check to accommodate a
divergence, which is the exact pattern that produced this campaign's last three defects. So:
`<addMavenDescriptor>false</addMavenDescriptor>`, and **the harness stays untouched**.

What the harness *does* need is a **test that pins this expectation**, so a future reader
understands why the flag exists and does not remove it as boilerplate.

The harness is otherwise already Maven-ready on the axis it was built for: `normalize_manifest`
strips `Archiver-Version` — precisely what Maven's plexus-archiver stamps — alongside
`Ant-Version`/`Created-By`/`Built-By` and the `${DSTAMP} ${TSTAMP}` form of `Implementation-Version`.
Entry order and timestamps are never read.

### 3.3 The module graph is a known quantity

`modules/prebuildjava/build.xml:25` hand-encodes the topological order as a single `depends=` list of
**23 modules** (the migration doc says 22 in one place and 23 in another; the code is consistent at
23). `terminal` is the 24th and is **absent** from that list — built only via its own `USEANT=1` path,
gated `if GUI` (`modules/terminal/Makefile.am:34`).

Transcribing that graph into a reactor is mechanical when the time comes. Stage 2-a does **not** do
it — writing 24 POMs that cannot yet build is speculative work.

### 3.4 Three orphans the reactor would force into the open

A Maven reactor makes explicit what the current tangle decides by omission. Per the scope decision,
Stage 2-a **preserves today's behavior exactly** and flags each for a later, deliberate call:

- **`output_stream` is built by nothing.** It has a `build.xml`, `prebuildjava/build.xml:85-87`
  defines an unreachable target for it, its `Makefile.am` sets no `USEANT` — and
  `scilab-lib.properties:170-172` still points at a jar nothing produces.
- **`scirenderer` is Ant-only** — a `build.xml` with no `Makefile.am` and no `SUBDIRS` entry, reached
  solely through `prebuildjava`.
- **`terminal` is GUI-gated and separate**, as above.

### 3.5 What Stage 2 does not need

No codegen plugin. JFlex's 9 grammars have **zero** build wiring — their lexers are hand-committed
and nothing regenerates them. SWIG (32 `.i`) and GIWS (34 `.giws.xml`) run only under explicit
`--enable-build-swig`/`--enable-build-giws` flags, and both are slated for deletion in the later FFI
phase, so wiring Maven codegen for them would be effort spent on code about to be removed.

### 3.6 Two documentation corrections

`docs/design/build-cmake-maven-migration.md` says the topo-sort is 22 modules (it is 23), and that
"FlatLaf already bundled to replace the Swing L&F set" — `flatlaf-3.4.1.jar` is fetched but
**unused**, its only reference being `// TODO uncomment if using FlatLaf` in
`modules/ui_data/.../ScilabFileSelectorFilter.java:162`.

## 4. Scope

**In scope:**
- A **parent POM** carrying the shared build configuration: `--release 25`, the `--add-exports`/
  `--add-opens` arguments, `<addMavenDescriptor>false</addMavenDescriptor>`, and the
  `<manifestSections>` shape.
- **One leaf module's POM**, resolving its dependencies from the existing `thirdparty/*.jar` files as
  local artifacts (no Maven Central research).
- A **harness test** pinning the no-Maven-descriptor expectation.
- The two doc corrections (§3.6).

**Out of scope — deliberately, and each is its own later sub-stage:** the other 23 module POMs; Maven
Central coordinate research for the ~32 undeclared jars; `etc/classpath.xml` regeneration; the
JUnit 4→5 port and `surefire` wiring; swapping `cmake/ScilabJava.cmake`'s Ant invocation for Maven;
resolving the three orphans; deleting `ivy.xml`.

## 5. Architecture

### 5.1 Module choice

`modules/commons` — a leaf in `build.incl.xml`'s classpath shape and the migration doc's own
suggested beachhead. **Verify it is genuinely a leaf before writing the POM**; if its imports say
otherwise, switch to `modules/localization` and record why. A beachhead that needs a dependency graph
is not a beachhead.

### 5.2 The parent POM

Carries what `build.incl.xml` supplies today, and nothing more:

- **`--release 25`** matching `build.incl.xml:128-145`'s `source="25" target="25"`, `encoding=utf-8`,
  and `debug=true debuglevel="lines,vars,source"` (Maven's default, so free).
- **`--add-exports java.base/jdk.internal.loader=ALL-UNNAMED`** plus the two `--add-opens`
  (`java.base/jdk.internal.loader`, `java.base/java.lang.reflect`). These are load-bearing:
  `modules/jvm/.../LibraryPath.java:25-26` imports `jdk.internal.loader.NativeLibraries` and
  `sun.misc.Unsafe` and reflectively rewrites `LibraryPaths.USER_PATHS`. Without them that file does
  not compile under JDK 17+ encapsulation. (Not needed by `commons` itself, but the parent is where
  the policy belongs.)
- **`<addMavenDescriptor>false</addMavenDescriptor>`** — §3.2.
- **`<manifestSections>`** reproducing the per-package section — §3.1.

### 5.3 Dependencies as local files

The beachhead resolves against the existing `thirdparty/*.jar` files directly, so no coordinate
research blocks it. Whether that is `<systemPath>`, an `install-file`'d local repository, or a
`file://` repo is an implementation choice — pick one, state the trade-off, and note that it is
**scaffolding to be replaced** once coordinates are known, not the intended end state.

### 5.4 Coexistence

Ant remains the build. Nothing in `cmake/ScilabJava.cmake`, `build.incl.xml`, `prebuildjava/
build.xml`, or any module's `build.xml` changes. The Maven build is run **by hand** for this stage;
wiring it into CMake is a later sub-stage. Both toolchains must be able to produce the jar, and the
Ant one stays the one that actually does.

## 6. The gate & acceptance

1. **The Maven-built `commons` jar is byte-parity-green against the Ant-built one**, measured by the
   existing `jars` dimension through the real harness — not a bespoke comparison.
2. **No harness weakening.** `parity/capture.py` and `parity/diff.py` gain no exclusions. If the jar
   fails, the POM is wrong.
3. **The manifest matches**, including the per-package section — the `MANIFEST.MF` entry hash is part
   of (1), so this is proven rather than asserted.
4. **The harness test pins the no-descriptor expectation** and has been seen to fail without it.
5. **Ant still builds the jar identically** — coexistence intact.

## 7. Risks & mitigations

| Risk | Mitigation |
|---|---|
| The chosen module turns out not to be a leaf | §5.1: verify first, switch to `localization` and record why. |
| Maven's manifest differs subtly (ordering, blank lines, attribute spelling) | The `MANIFEST.MF` hash is inside the acceptance gate, so a difference is named. Fix the POM, never the normalizer. |
| Bytecode differs from Ant's javac output | Same compiler, same `--release`/flags — but if `.class` entries differ, the gate names them, and the cause is a compiler-arg mismatch worth finding rather than normalizing. |
| The local-file dependency scaffolding gets mistaken for the intended design | Called out in §5.3 and to be repeated in the POM's own comments. |
| The beachhead succeeds and invites skipping straight to bulk migration | The out-of-scope list (§4) is explicit; each item is its own sub-stage with its own gate. |

## 8. Success criteria

- A Maven-built `commons` jar passes the existing `jars` parity dimension, with no harness change.
- The parent POM encodes the compiler policy, the `--add-exports`/`--add-opens` set, the disabled
  Maven descriptor, and the per-package manifest sections — each traced to what it reproduces.
- A harness test pins the no-descriptor expectation and has been seen to fail.
- Ant still builds everything; nothing in the Ant or CMake build changed.
- The three orphans, the two doc corrections, and the deferred sub-stages are recorded where the next
  reader will find them.
