# RC-e — the cutover that deletes `./configure` — Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Checkbox (`- [ ]`) steps.
> **Working mode:** drive continuously (see [[push-forward-flow]]); fix gate findings inline, don't checkpoint per increment.

**Goal:** Sever CMake's last dependencies on `config.status`, so `./configure` (and eventually the whole autotools/Ant tree) can be deleted — the migration endgame (migration doc §12).

**State:** Stage 2 module migration complete (24/24 on Maven); the CMake→Maven jar swap is in (flag-gated). RC-a…RC-d done (CMake computes machine.h, flags, generated files, macros). HEAD `85fe92ee094`. Rollback tag `stage2-modules-complete` @ `d5cb8c17eab`.

## The last coupling: exactly 8 things CMake reads from `config.status`

| config.status key | used by | CMake-native replacement |
|---|---|---|
| version triple (`SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE`, `2027.0.0`) | `ScilabGeneratedFiles.cmake:50`, `ScilabMachineHeader.cmake:837` (`PACKAGE_VERSION`) | Declare canonically in CMake — a `cmake/ScilabVersion.cmake` holding the triple, `include()`d early; `project(... VERSION ...)` optional. Canonical value stays `2027.0.0` (matches `configure.ac:47-49`). |
| `libdir`, `includedir`, `exec_prefix` | `ScilabGeneratedFiles.cmake:56` (path substitutions) | `GNUInstallDirs` (`CMAKE_INSTALL_LIBDIR` etc.) + `CMAKE_INSTALL_PREFIX`. |
| `GUI_TRUE`, `NEED_JAVA_TRUE` | `ScilabJava.cmake` gates | CMake cache options `ENABLE_GUI` / `ENABLE_JAVA` (defaults matching the configured tree). |
| `JAVA_HOME` | `ScilabJava.cmake` | Already hoisted into the driver (Stage 1f); resolve via `find_package(JNI)`/`$ENV{JAVA_HOME}`. |
| `ANT` | `ScilabJava.cmake` (ant path) | Goes away when Ant is deleted (RC-e.4). Until then `find_program(ANT ant)`. |

**Binding gate throughout:** the `generated`, `generated_cmake`, `header_defines`, and `flags` parity dimensions must stay GREEN — CMake's natively-sourced values must be byte/semantically identical to what config.status supplied. That is how we prove "no behavior changed, only the source of the value."

## Increments (drive continuously; each gated)

### RC-e.1 — version triple + install dirs, CMake-native (ADDITIVE)
- [ ] Create `cmake/ScilabVersion.cmake`: the canonical `SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE = 2027/0/0` (single source of truth), `include()`d before any consumer. Comment it as the post-configure home of the version, replacing the `config.status` read.
- [ ] Replace CMake's `config.status` version read with the `ScilabVersion.cmake` values. Wire `GNUInstallDirs` for `libdir`/`includedir`/`exec_prefix`.
- [ ] **config.status STILL PRESENT** — this is additive; CMake just stops *reading version/paths from it*.
- [ ] Gate: `generated`, `generated_cmake`, `header_defines` parity all green (version.h, machine.h, Version.incl etc. byte/semantically unchanged). Full build-parity suite green.
- [ ] Commit + push.

### RC-e.2 — the GUI/Java/ANT gates, CMake-native (ADDITIVE)
- [ ] `ENABLE_GUI` / `ENABLE_JAVA` cache options (default to the current configured tree's values); `JAVA_HOME` via find_package/env; `ANT` via find_program. Replace the `config.status` `*_TRUE` / `JAVA_HOME` / `ANT` reads.
- [ ] Gate: the Java bridge + `flags` parity green under both `ENABLE_*` settings; suite green.
- [ ] Commit + push.

### RC-e.3 — prove `config.status` is deletable (the readiness gate)
- [ ] Configure + build the CMake tree with `config.status` **renamed away**; confirm `cmake -S . -B` succeeds and the whole-tree parity holds. This is the proof that nothing still reads it.
- [ ] If anything FATALs on the missing file, that is a found coupling — replace it (loop back), do not force.
- [ ] Commit the proof (a CI/smoke note), push.

### RC-e.4 — the endgame cutover (ONE-WAY DOOR — the deletion)
- [ ] Flip `SCILAB_JAVA_BUILD` default to `maven` (the swap's Maven path becomes the default).
- [ ] Consumption cutover: repoint `etc/classpath.xml.in` (+ regenerate `classpath.xml`) module paths `jar/` → `target/`; re-baseline `classpath.xml` (predict→diff→re-baseline: only that file's `generated` hash moves).
- [ ] Delete `./configure`, `config.status`, `configure.ac`, the `Makefile.am` tree, `m4/`.
- [ ] Delete Ant: `build.incl.xml`, every module `build.xml`, `modules/prebuildjava/`, `ivy.xml`.
- [ ] Headless CLI smoke (controller); **GUI smoke = user** (the one check tools can't run).
- [ ] This unblocks the deferred-fixes remediation plan (harness role inverts — §12).

## Notes
- RC-e.1–3 are additive/reversible; RC-e.4 is the irreversible bundle. The rollback tag protects it.
- JUnit 4→5 + surefire (register B8) is a parallel prerequisite for Ant deletion (Ant's test target needs a Maven runner) — fold into RC-e.4 or do just before.
- "Work with the tool's natural order": the consumption cutover points at Maven's `target/`, not a copy back into Ant's `jar/`.
