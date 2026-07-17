# Stage 1f-b — CMake→Ant bridge for the Java jars — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-17
**Depends on:** Stage 1f-a (CMake builds the whole native app — 64 module dylibs + 21 fold-in
OBJECT libs + `libscilab`/`libscilab-cli` + `scilab-bin`/`scilab-cli-bin`; the parity harness is
rpath-aware; `JAVA_HOME` is hoisted to `SCILAB_JAVA_HOME` in `ScilabToolchain.cmake`; HEAD
`f6df1f268e5`). Strategy: `docs/design/build-cmake-maven-migration.md`; driver usage:
`docs/design/build-cmake-driver.md`.

## 1. Goal

Make CMake — not the autotools recursive `make` — the thing that invokes Ant to build the 24 Java
module jars, **keeping Ant itself unchanged** (Stage 1 keeps Ant; the Ant→Maven cutover is Stage 2).
After 1f-b, `cmake --build … --target drop-in-all` produces the whole native app **and** the 24
jars, and the jars are proven content-equivalent to their autotools originals by the parity harness
(extended with a jar dimension). Autotools stays fully functional (coexistence; rollback is free).

## 2. Background — how the Java build works today (grounded)

- **26 modules ship a `build.xml`; 24 produce a shipped jar** (`modules/<m>/jar/*.jar`):
  `action_binding, commons, completion, console, core, external_objects_java, graph,
  graphic_export, graphic_objects, gui, helptools, history_browser, history_manager, javasci, jvm,
  localization, preferences, renderer, scinotes, scirenderer, terminal, types, ui_data, xcos`.
  (`prebuildjava` is the orchestrator, not a shipped jar; `output_stream` ships a `build.xml` but no
  jar.)
- **The whole Java build is ONE Ant super-build.** `modules/prebuildjava/build.xml`
  (`default="all"`, an Ivy-enabled project) hand-topo-sorts every module: its `all` target
  `depends` on all 24 in dependency order, and each `<target name="<m>">` runs
  `<ant antfile="../<m>/build.xml" target="${target-jar}" inheritAll="false" inheritRefs="false"/>`.
  `target-jar` defaults to `jar` (build.xml:21). `graph`/`javasci`/`xcos` are conditional on the Ant
  properties `build_xcos`/`build_javasci` (resolved from the configure-substituted `build.incl.xml`,
  imported by each `build.xml`). Ivy resolves 47 first/third-party deps (incl. our
  `firstparty/swing-gpu-surface-0.1.0.jar`).
- **Autotools triggers it** by recursing into `prebuildjava` FIRST (`modules/Makefile.am` SUBDIRS
  line 22); `prebuildjava`'s `java:` target (from `Makefile.incl.am`, gated on `NEED_JAVA` +
  `USEANT`) runs **bare `$(ANT)` with `JAVA_HOME` exported** — no `-D` on the command line; the
  properties come from `build.incl.xml`. `$(ANT)` is config.status-substituted
  (`ANT = …/ant`); `USEANT=1`, `NEED_JAVA` on for a normal GUI build.
- **Consequence:** reproducing the Java build under CMake is *one bare `ant` invocation in
  `modules/prebuildjava/`* with the right `JAVA_HOME` + env. The topo-sort, Ivy, and the
  inter-module Java dependency tangle all live inside Ant and stay untouched — exactly "keep Ant
  unchanged." Jars are written to `modules/<m>/jar/` regardless of who calls Ant, so "drop-in" is
  automatic (no copy step).

## 3. Scope

**In scope (1f-b):**
- A `scilab_java_bridge()` helper (`scilab/cmake/ScilabJava.cmake`) → the **`sci-java-all`** custom
  target that runs the `prebuildjava` super-build; wired into the top-level driver and onto
  `drop-in-all`.
- A **jar dimension** in the parity harness: a normalized, timestamp-free content manifest per jar,
  captured + diffed + fault-injected + guarded by a two-build reproducibility probe.
- A **re-baseline** of `baseline-autotools.json` with the jar section, captured from a
  pure-autotools rebuild.
- The **acceptance**: whole-tree parity incl. jars + a headless `scilab -nw` JVM/jar smoke + a
  `bin/scilab` GUI launch verified by startup-log scan.
- Docs (`build-cmake-driver.md`) + CI (`.gitlab-ci.yml`).

**Out of scope (deferred):**
- Replicating the topo-sort / inter-module Java dep graph in CMake, and Ant→Maven — **Stage 2**
  (the Maven reactor replaces `prebuildjava` wholesale).
- Dropping the ~23 dead jars — Stage 2.
- Byte-for-byte reproducible jars (`SOURCE_DATE_EPOCH`, sorted zip entries) — Stage 2 concern.
- Help generation + retiring `configure` (CMake generating `machine.h`/`version.h`) — **Stage 1f-c**.
- Any change to `configure.ac`, any `Makefile.am`, any `build.xml`, `build.incl.xml`, or `ivy.xml`
  (only the harness + the new CMake file + docs/CI change).

## 4. Architecture — the one-target bridge (Approach A)

`scilab/cmake/ScilabJava.cmake` defines `scilab_java_bridge()`, which:
- Reads `SCILAB_ANT` from config.status (the `ANT = …` line) and the `NEED_JAVA` / `USEANT`
  facts; if `NEED_JAVA` is off or `USEANT != 1`, the target is a no-op stub (parity-neutral — that
  configuration builds no jars).
- Declares `add_custom_target(sci-java-all …)` running, from `modules/prebuildjava/`:
  `${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT}` — the byte-equivalent of
  `Makefile.incl.am`'s `java:` recipe for `prebuildjava`. No `-D` args (fidelity: the properties
  resolve from `build.incl.xml`, exactly as under `make`).
- Adds `sci-java-all` as a dependency of `drop-in-all` (so the one whole-app target now also builds
  the jars). A convenience `drop-in-jars` alias == `sci-java-all` for symmetry with the native
  `drop-in-*` names.

Rationale for A over per-module targets (B): the dependency order already lives in
`prebuildjava/build.xml`; re-encoding the "3–4-way Java dep tangle" in CMake `add_dependencies`
would create a second, drift-prone source of truth and *is* the Maven-reactor modeling that belongs
to Stage 2. One target is faithful, minimal, and non-duplicating.

**Why no `-D` transcription (unlike 1f-a's LDFLAGS):** autotools runs `prebuildjava` Ant *bare*;
all properties are in `build.incl.xml`. The bridge reproduces the bare invocation, so there is no
per-property transcription risk — the only inputs are `JAVA_HOME`, the `ant` binary, and the CWD.

## 5. The jar-parity harness dimension

**Fingerprint (`parity/fingerprint.py`):** `fingerprint_jar(path) -> {entry_name: sha256(bytes)}`,
a jar being a zip — read each entry's *content* (not the zip container metadata), hash it, key by
entry name (sorted). This strips zip ordering and per-entry mod-times. For `META-INF/MANIFEST.MF`,
normalize a configured set of volatile lines (`Ant-Version`, `Created-By`, and any `Built-*`/date
line) before hashing.

**Capture (`parity/capture.py`):** a `jars` section — walk `modules/*/jar/*.jar` under the tree
root, record `{jar_relpath: {entry: hash}}`. Written whenever jars are present (a `--no-jars` escape
for native-only captures).

**Diff (`parity/diff.py`):** compare (a) the jar *set* (relpaths) and (b) each jar's entry→hash
map; report `jar added/removed`, `entry added/removed in <jar>`, `entry <name> changed in <jar>`.
Transition rule (mirrors the rpath rollout): a baseline lacking a `jars` section ⇒ skip with a
"not-yet-armed" note; capture always emits `jars`, so the skip branch is unreachable once the
baseline is re-captured.

**Reproducibility probe (the guard that must be seen to fail):** a test that captures two
independent autotools jar builds and asserts their `jars` sections are identical *except* for the
configured MANIFEST normalization. If any *other* entry differs between two identical builds, the
probe FAILS — surfacing a volatile entry (an embedded build stamp) that must be added to the
normalize list explicitly, never silently. This makes the normalize list empirical and complete
(cf. "a guard you have not seen FAIL is not a guard").

**Fault injection (acceptance test):** on a real captured fingerprint, mutating a `.class` byte,
adding an entry, and removing an entry each make the diff FAIL, naming the jar + entry; and a
candidate that lost its `jars` key against an armed baseline must FAIL (reverse direction).

**Baseline:** re-capture `baseline-autotools.json` (native + `jars`) from a **pure-autotools
rebuild** — the independent autotools reference (the 1f-a discipline). The jar bytecode depends on
the JDK; the baseline is captured with the configured jdk-25, and the CMake bridge uses the same JDK
via `SCILAB_JAVA_HOME` (1f-a) ⇒ identical bytecode. Documented dependency: a JDK change requires a
re-baseline (like the machine-path debt).

## 6. The gate & acceptance

1. **Whole-tree parity OK incl. the `jars` section** (24 jars, content-matched) after
   `drop-in-all`. This is the primary arbiter.
2. **Headless smoke** (CI-able, on the macOS runner): the CMake-built app in **`-nw`** mode
   (`bin/scilab -nw -nb -e "disp(1+1); exit(0)"`) starts the JVM, loads the startup jar set,
   computes, and exits 0 with a startup log free of `ClassNotFound`/`NoClassDefFound`/`Exception`
   — proving the JVM + jars wire up from a working build. (**`-nwni` cannot be used**: it disables
   the JVM entirely — *"jimport function disabled in -nwni mode"* — so it loads no jars. `-nw` is
   "no window, JVM on". A fragile `jimport` call is deliberately avoided: on a Java error `-nw`
   drops to an interactive prompt and hangs, so the robust smoke is startup+compute+exit, and the
   content manifest already proves each jar's content.)
3. **GUI launch** (local): `bin/scilab` loads the full jar set (gui/scinotes/xcos/history_browser/
   …); the startup log is scanned for `ClassNotFoundException`/`NoClassDefFoundError`/jar-load
   errors (no screen-capture); a clean log passes and the instance is left open for user testing
   (per the one-app-instance rule — the stale instance is killed first).
4. **Coexistence:** autotools `make` still builds the jars independently (exercised during the
   re-baseline); rollback is free (the CMake file is invisible to automake).

## 7. Migration mechanics & rollback

- **Order:** (1) add the jar dimension to the harness + fault-injection + reproducibility probe.
  (2) Autotools rebuild → re-capture the jar-aware baseline → commit; confirm the current tree is
  PARITY OK. (3) `scilab_java_bridge()` → `sci-java-all` → wire into `drop-in-all` → build the jars
  under CMake → drop-in (automatic) → PARITY OK incl. jars. (4) Acceptance (headless smoke + GUI
  launch). (5) docs + CI.
- **Rollback is free:** the bridge is additive; `make` still builds the jars via `prebuildjava`. No
  `configure.ac`/`Makefile.am`/`build.xml` change.

## 8. Testing

- The **parity harness (jar-aware)** is the primary test; the jar diff is fault-injected (mutate/
  add/remove an entry; drop the `jars` key) and guarded by the two-build reproducibility probe.
- The **headless smoke** (§6.2) is the CI-able behavioral gate; the **GUI launch** (§6.3) is the
  local end-to-end check.
- CI: the shared-runner `sanity:cmake-driver` extends to assert the `sci-java-all` target exists and
  the 24-jar module set is declared; the native-runner `parity:cmake-drop-in` gains the jar section
  automatically (it rides `drop-in-all` + the capture walks `modules/*/jar/`).

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Volatile jar entries beyond MANIFEST.MF (embedded `version.properties`, build stamps) | The two-build reproducibility probe surfaces every non-deterministic entry empirically; each is normalized explicitly (never silently) or fixed. |
| A different JDK produces different bytecode → spurious jar mismatch | The bridge uses `SCILAB_JAVA_HOME` (1f-a) = the configured jdk-25, the same JDK the baseline was built with; a JDK change is a documented re-baseline trigger. |
| Ivy resolution differs when Ant is invoked by CMake vs make | Same bare `ant`, same CWD, same `build.incl.xml`/`ivy.xml`, same pre-fetched thirdparty payload (`fetch-thirdparty.sh`); the content manifest catches any resolved-classpath difference. |
| CMake runs `ant` with a different env (JAVA_HOME/PATH) than `make` | The target exports `JAVA_HOME=${SCILAB_JAVA_HOME}` exactly as `Makefile.incl.am` does; the headless smoke + GUI launch catch a mis-wired runtime. |
| `sci-java-all` builds jars twice (prebuildjava vs a stray per-module call) | Only `prebuildjava` is invoked (one super-build); no per-module CMake Java targets exist. |
| The jar set is configuration-dependent (`build_xcos`/`build_javasci`) | The baseline + candidate are captured under the same configuration; a wrong property shows as a jar added/removed in the diff. |

## 10. Success criteria

- `cmake --build … --target drop-in-all` produces the 24 jars (+ the native app), all in
  `modules/<m>/jar/`.
- Whole-tree **PARITY OK including the `jars` section** (24 content-matched jars); the harness
  catches a mutated/added/removed jar entry and a JDK-bytecode drift.
- The CMake-built app passes the headless `-nw` JVM/jar smoke (rc=0, jar-error-free log);
  `bin/scilab` launches with a jar-error-free startup log.
- The autotools build still builds the jars via `make`.
- The baseline is a jar-aware pure-autotools reference; the reproducibility probe has been seen to
  fail on an injected volatile entry.
