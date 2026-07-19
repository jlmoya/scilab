# Retire-configure RC-c — CMake generates the configure-substituted files — Design

**Status:** approved design, pre-plan
**Date:** 2026-07-19
**Depends on:** RC-a (CMake computes `machine.h`; the semantic `header_defines` dimension) and RC-b
(the flag policy computed in `cmake/ScilabFlags.cmake`; the flag gate's expectations derived from the
autotools Makefiles and frozen in the baseline). HEAD `a3d0dc09257`. Strategy:
`docs/design/build-cmake-maven-migration.md` §12.

## 1. Goal

Make CMake generate the files `config.status` substitutes — the ones that are neither `Makefile`s nor
headers — so that `./configure`'s remaining jobs shrink to the macros build (RC-d) and the cutover
itself (RC-e). Gated by extending the parity harness's existing byte-hash `generated` dimension, which
today covers **one** of them.

## 2. The retire-configure decomposition (context)

**RC-a (done)** — `machine.h` + the semantic header dimension · **RC-b (done)** — the compiler-flag
policy + the derived per-TU flag gate · **RC-c (this)** — the generated files · **RC-d** — the macros
build · **RC-e** — the cutover that deletes `./configure`. **Binding invariant: coexistence is
TEMPORARY — the destination is autotools DELETED (§12).**

## 3. Background — what is actually true (verified, not assumed)

### 3.1 The inventory is 12 files, not "~21"

`AC_CONFIG_FILES` declares 102 outputs; 89 are `Makefile`s and **13 are not**. One of the 13
(`modules/core/includes/version.h`) was already migrated in Stage 1f-c, so the live scope is **12**.
Cross-checked three ways and in agreement: `config.status:369` (`config_files=`),
`config.status:925-937` (the `case $ac_config_target` dispatch), and `configure.ac:2426-2523`.

The "~21" in RC-b's design doc (line 19, the decomposition summary) is unreconcilable — even generously adding `machine.h` (an
`AC_CONFIG_HEADERS` entry, not `CONFIG_FILES`) and the generated `libtool` script reaches only 15.
This repo has a pattern of loose `~N` estimates that measurement later corrects by a wide margin
(RC-b's own "~40 overrides" measured 211); treat the measured 12 as the number.

### 3.2 The difficulty is concentrated, and most files are trivial

| Tier | Files | Substitutions |
|---|---|---|
| Zero substitutions | `build.incl.xml` | **0** — output is byte-identical to its template |
| Scalar (1–5 vars) | `scilab.pc`, `scilab.properties`, `etc/logging.properties`, `etc/Info.plist`, `SciDocConf.xml`, both `repositories*` | 1–5 each |
| Real logic | `etc/modules.xml` | 12 booleans, with one deliberate exception (§3.4) |
| **Deferred (§4)** | `etc/classpath.xml`, `scilab-lib.properties`, `scilab-lib-doc.properties` | **115 of 142 total** |

`build.incl.xml` deserves note: `grep -c '@' build.incl.xml.in` is 0 and `diff build.incl.xml.in
build.incl.xml` is empty. Both the template and the output are tracked in git (unlike `scilab.pc` and
the `*.properties` files, which are git-ignored) — someone already treats it as effectively static.

### 3.3 `Version.incl` is invisible to any `config.status`-derived inventory

It is written by a **raw shell `echo` inside `configure.ac`**, conditionally, not through
`AC_CONFIG_FILES`:

```
configure.ac:2965:  echo "SCIVERSION=scilab-branch-$MAJOR.$MINOR" >$SCI_SRCDIR/Version.incl
```

guarded by `configure.ac:2961`'s comparison against a version string scraped out of
`modules/gui/images/icons/aboutscilab.svg`. `build.incl.xml:154` stamps every jar's
`Specification-Version` from `${SCIVERSION}`, sourced from this file. **It is in scope precisely
because an inventory built the obvious way misses it.**

### 3.4 Two traps a mechanical generator falls into

- **`etc/modules.xml`'s `helptools` entry is hardcoded**, not `@HELP_ENABLE@` — the template's own
  comment (`etc/modules.xml.in:79-82`) explains that `--disable-build-help` only skips *building* the
  documentation while the module still provides the runtime `help()` machinery, and that gating it on
  the flag "forced a post-configure fixup to reactivate." A generator that maps each module to its
  matching `*_ENABLE` flag gets this one wrong.
- **Ant's `<property file=…/>` fails silently.** A missing properties file leaves its properties
  unset rather than erroring, so a partially-complete generation surfaces as `javac` "cannot find
  symbol" rather than "file not found." (`<import file=…>` at `build.incl.xml:165` does fail loudly —
  the asymmetry matters when reasoning about failure modes.)

### 3.5 `etc/Info.plist` is vestigial — proven, not inferred

`package-macos.sh:106-133` writes its **own** `Info.plist` into the bundle via heredoc, with a
different key set and a hardcoded version. Diffing the configure-generated file against the one in
the shipped `.app` shows two unrelated documents. Grep finds no consumer of `etc/Info.plist` beyond
autoconf's own bookkeeping in `Makefile.in`.

### 3.6 `configure` mutates tracked source files based on the wall clock

`configure.ac:2930-2937` compares `date "+%Y"` against a year hardcoded in a comment in
`banner.cpp`, and on mismatch runs `sed -i` over `banner.cpp` **and `etc/Info.plist.in`** — one of our
templates. Dormant today (both read 2026), it fires the first time someone configures in a new year.

### 3.7 The harness gates 1 of the 12

`build-parity/parity/capture.py:14-18`'s `GENERATED_FILES` lists `etc/classpath.xml` plus the two
already-migrated headers. Every other file in the inventory has **no byte hash, no semantic check,
nothing** — confirmed by grepping the whole `build-parity/` tree. `etc/classpath.xml`'s entry already
runs `normalize_path(..., roots)` before hashing, because it embeds absolute checkout paths.

## 4. Scope

**In scope — 9 files + `Version.incl`:** `build.incl.xml`, `scilab.pc`, `scilab.properties`,
`etc/logging.properties`, `etc/modules.xml`, `etc/Info.plist`,
`modules/helptools/etc/SciDocConf.xml`, `modules/atoms/etc/repositories`,
`modules/atoms/tests/unit_tests/repositories.orig`, and `Version.incl`.

**Deferred to Stage 2 (Ant→Maven), deliberately:** `etc/classpath.xml`, `scilab-lib.properties`,
`scilab-lib-doc.properties`. They carry 115 of the 142 substitutions, all jar paths produced by
`AC_JAVA_CHECK_JAR`'s filesystem search (`m4/java-thirdparty.m4:234-256`), and they are consumed
**only** by the Ant build that Stage 2 replaces outright. Reimplementing that search in CMake now
would be work aimed at files Maven is expected to obsolete.

**Consequence, recorded as a hard dependency:** RC-e cannot delete `./configure` until Stage 2 has
replaced the consumer of those three files. This converts a loose ordering into an explicit
constraint in the migration doc's endgame.

**Out of scope:** the macros build (RC-d); the cutover (RC-e); `etc/Info.plist`'s *removal* (§6.3);
reproducing the year-bump (§6.4); the generated `libtool` script (pure GNU boilerplate — CMake does
not shell out through libtool).

## 5. Architecture

### 5.1 Extend the byte-hash gate first (it gates §5.2)

`GENERATED_FILES` grows from 3 entries to 13 (the existing 3 + the 9 + `Version.incl`). The mechanism
is unchanged — `normalize_path(read(), roots)` then sha256 — so this is the cheapest of the three
gates this campaign has built, and deliberately so: these are scalar-substitution templates, the
`version.h` shape, not the `machine.h` shape.

Baseline armed from configure's own copies. Fault-injected: perturb one generated file in the
candidate → parity fails naming it. The existing transition rule applies unchanged (baseline lacking
an entry → skip; candidate that lost one against an armed baseline → FAIL).

### 5.2 `scilab/cmake/ScilabGeneratedFiles.cmake`

One `configure_file(<template> <output> @ONLY)` per file, plus `Version.incl`'s conditional write.
Values come from what RC-a's `ScilabConfigure.cmake` already computes (the version triple) and from
CMake-side policy for the boolean gates — **never from `config.status`**, per the constraint every
RC stage has carried.

**Byte-identical is the target.** `version.h` set the precedent: `configure_file(@ONLY)` reproduces
autoconf's `@VAR@` substitution exactly when the values match. A file that turns out not to be
byte-identical is a **finding to investigate**, not grounds to relax the gate to semantic comparison —
`machine.h` needed that for a specific, understood reason (autoconf's `#undef` formatting) and none of
these share it.

Output location follows the established split: files consumed from the source tree during coexistence
keep resolving to configure's copies, while CMake writes its own alongside, as `version.h` does in
`build-cmake/generated-includes/`. Where a file has no include-path analogue (`scilab.pc`,
`build.incl.xml`), the generated copy goes to a `build-cmake/generated/` mirror and activates at RC-e.

### 5.3 Coexistence

`make` continues to generate every file exactly as today; nothing in `configure.ac`, any
`Makefile.am`, or any `*.in` template changes. The CMake copies are additive and unconsumed until
RC-e, which is what makes rollback free.

## 6. The gate & acceptance

1. The extended `generated` dimension covers all 13 entries, armed from configure's copies, and
   **fault injection fails naming the perturbed file**.
2. **Byte-identity**: each CMake-generated file is byte-identical to configure's, after the existing
   root normalization. Any divergence is investigated and explained, not normalized away.
3. From-scratch whole-tree **PARITY OK** (68 dylibs + 2 executables + 24 jars + the semantic header +
   the derived flag facts) and the RC-b flag gate still rc=0.
4. **`etc/Info.plist` is reproduced faithfully** and recorded as dead weight with the §3.5 evidence,
   for a later cleanup pass — not deleted here. Deleting it would be an "improve" inside a "reproduce"
   stage and would require editing `configure.ac`, which every RC stage has kept off-limits.
5. **The year-bump is deliberately not reproduced.** A build system that `sed -i`s its own tracked
   sources is a wart worth dropping; the divergence is documented where the generator lives, so a
   future reader sees a decision rather than an omission.

## 7. Migration mechanics & rollback

- **Order:** (1) extend the gate + arm + fault-inject. (2) generate the 9 + `Version.incl`. (3)
  from-scratch parity + docs + CI.
- **Rollback is free:** the harness change is additive; the CMake files are new and unconsumed; no
  `configure.ac`/`Makefile.am`/`*.in` edits. `make` recovers everything.

## 8. Testing

- The extended byte-hash dimension is the primary gate, fault-injected before the generation lands.
- Unit coverage for `Version.incl`'s conditional write, including the branch where the version matches
  and the file is not rewritten.
- From-scratch whole-tree parity proves the generated files did not disturb the build.
- CI: `sanity:cmake-driver` gains a check that `ScilabGeneratedFiles.cmake` is wired.

## 9. Risks & mitigations

| Risk | Mitigation |
|---|---|
| A file is not byte-identical for an unforeseen reason (line endings, trailing newline, autoconf quoting) | The gate names the exact file; investigate rather than relax. `version.h` is the existence proof that byte-identity is achievable for this template shape. |
| The `helptools` exception is flattened by a mechanical module→flag mapping | Called out in §3.4 with the template's own comment; the gate catches it (`modules.xml` byte-compares). |
| A partially-generated set silently degrades the Ant build | §3.4: `<property file=…>` fails silently. The gate covers every file in scope, so a missing one fails parity rather than surfacing as a `javac` error. |
| `Version.incl` is forgotten because it is not in `AC_CONFIG_FILES` | It is named explicitly in scope (§4) and in the gate (§5.1). |
| Deferring the 3 jar files leaves RC-e blocked in a way nobody notices | Recorded as a hard dependency in the migration doc's endgame (§4). |

## 10. Success criteria

- CMake generates 9 configure-substituted files plus `Version.incl`, byte-identical to configure's.
- The `generated` dimension covers all 13 entries and has been seen to fail.
- From-scratch whole-tree **PARITY OK** + the RC-b flag gate rc=0 + the suite green.
- `make` still generates everything (coexistence intact).
- The Stage-2 dependency for the 3 deferred files, `Info.plist`'s dead-weight status, and the
  deliberate year-bump divergence are all recorded where the next reader will find them.
