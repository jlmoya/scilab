# Stage 2-c — the template decisions, and the gate that would catch them — Design

**Status:** design, pre-plan
**Date:** 2026-07-20
**Depends on:** Stage 2-a (`b44f14ec22d`, `localization`) and Stage 2-b (`6806634f253`, `commons`).
HEAD `8af1dda46c3`. Strategy: `docs/design/build-cmake-maven-migration.md` (Stage 2).

## 1. Why this stage exists now

Two module POMs exist. The next 22 will be written by copying one of them. Stage 2-b's review
raised two questions about that template — jar naming and how a vendored jar enters the build —
and both compound linearly with reactor size: retrofitting 22 POMs costs far more than deciding
once. So the decisions get made at N=2, not at N=19.

Investigating them turned up a third thing neither the plan nor the review anticipated, and it is
the most important item in this stage: **the `jars` parity dimension has never once looked at a
Maven-built jar.**

## 2. The gate gap (build-the-gate-first)

`parity/capture.py:377-383` collects the `jars` dimension by walking directories that end in
`/jar` — `modules/<m>/jar/*.jar`, where Ant writes. Maven writes to `modules/<m>/target/`. That
directory is not scanned, so **no Maven artifact has ever entered a whole-tree fingerprint.**

Every "parity green" claim in Stages 2-a and 2-b came from a hand-run snippet that calls
`fingerprint_jar(ant_path)` and `fingerprint_jar(maven_path)` with **both paths supplied by hand**.
That comparison is real as far as it goes — the jar contents genuinely match — but it has two
holes:

1. It is not part of the suite. Nothing re-runs it; nothing fails if it stops being true.
2. **It cannot detect a wrong filename**, because the filename is an input I choose. If
   `<finalName>` silently failed to apply, I would point the snippet at whatever file exists and
   report PARITY OK.

Hole 2 is the reason this must be fixed *before* Decision A rather than after: Decision A's entire
deliverable is a filename, and the current gate is blind to filenames by construction.

This is the fifth instance of the campaign's recurring failure class — *a gate that does not look
at what the stage produces* — after the `elementary_functions` dead-rule collision, RC-c's
`generated` resolving against the source tree, `version.h`, and RC-d's unbuilt `macros` target.
The fix follows RC-c's proven precedent exactly.

### 2.1 The `maven_jars` section

A new capture section, mirroring RC-c's `generated_cmake`:

- **Collects** `modules/*/target/*.jar` (top level of `target/` only).
- **Keys each entry under its Ant-equivalent path** — `modules/<m>/jar/<basename>` — so
  `maven_jars` and `jars` are directly comparable dicts. RC-c used
  `_GENERATED_CMAKE_PATH_OVERRIDES` for the same alignment problem.
- **Values** are `fingerprint_jar` content manifests, identical in shape to `jars`.

Because the key carries the basename, a jar named `commons-2027.0.0-SNAPSHOT.jar` and one named
`org.scilab.modules.commons.jar` occupy **different keys**. A rename therefore surfaces as an
added/removed key pair, which is precisely the assertion the hand-run snippet cannot make.

**Transition rule** (the established idiom, as for `rpaths`/`jars`): a baseline with no
`maven_jars` section → skip; a candidate that **lost** a section the baseline had → FAIL.

**Fault injection — each must be seen to FAIL, not assumed to:**

| Injection | Expected |
|---|---|
| Rename the Maven jar | FAIL (key added + key removed) |
| Flip one byte in one class entry | FAIL (content differs) |
| Delete the Maven jar | FAIL (key removed) |
| Add a stray entry to the jar | FAIL (entry added) |

## 3. Decision A — jar naming: reproduce Ant's names

**Settled: Maven must produce `org.scilab.modules.<name>.jar`.**

The review flagged this as latent. It is not latent — it is load-bearing today, in the running
application:

- `etc/classpath.xml` hardcodes **23 module jars** by full path *and* name
  (`$SCILAB/modules/<m>/jar/org.scilab.modules.<m>.jar`). This is the application's runtime
  classpath.
- `etc/jvm_options.xml:20` hardcodes the JVM bootstrap entry
  (`-Djava.class.path=$SCILAB/modules/jvm/jar/org.scilab.modules.jvm.jar`).
- `cmake/ScilabJava.cmake:54` relies on jars landing in `modules/<m>/jar/` — a *path* dependency
  that inherits whatever Ant names them.

Renaming would mean rewriting `classpath.xml` and `jvm_options.xml`. That is *improving*, and the
campaign's binding principle is **reproduce, don't improve** — CMake and Maven make the same app
as autotools and Ant, warts included. Changing the runtime classpath as a side effect of a
build-tool swap is exactly the class of change that principle exists to forbid.

**Mechanism:** one line in the **parent** POM's `<build>`, inherited by all 24 modules:

```xml
<finalName>org.scilab.modules.${project.artifactId}</finalName>
```

`${project.artifactId}` resolves per-module, and artifactId already equals the module directory
name, which already equals the jar infix. One line covers every module that will ever be added.

**Deliberately NOT changed: the output directory.** Maven keeps writing to `target/`, not
`modules/<m>/jar/`. Pointing Maven at `jar/` during coexistence would have both toolchains writing
the same file — so a stray `mvn` run could feed a Maven jar into a CMake build that believes it
received an Ant jar, undetectably. The directory flips at the CMake swap, when Ant stops writing
there. Same basename in two directories is also what makes §2.1's key alignment natural.

**Known exception to record:** `scirenderer` is Ant-only and does not follow the
`org.scilab.modules.*` jar convention. It needs a per-module `<finalName>` override when its turn
comes. It is already one of the three tracked orphans.

## 4. Decision B — vendored jars: keep `systemPath`, for a better reason than we had

The review recommended replacing `<scope>system</scope>` + `<systemPath>` with a project-local
`file://` repository, noting correctly that Maven's own tooling warns the POM is "malformed" and
that Maven 4 strips system-scope entries. The `-q` flag in the plan's own recipe had hidden that
warning.

**Measurement overturned the recommendation.** A `file://` repository does not work in this
environment:

`~/.m2/settings.xml` declares a mirror with `<mirrorOf>*,!maven.oracle.com,!smartnow-tech</mirrorOf>`.
Maven mirrors match on repository **id**, not URL scheme, so a declared `file://` repository is
intercepted like any other. A probe confirmed it directly: Maven never read the local path, it
rewrote the request to `http://localhost:7910/...` (Nexus, which answers **401**) and to an Azure
DevOps feed → `BUILD FAILURE`. Making `file://` work would require every developer to hand-edit a
personal `settings.xml` that is not in version control — strictly worse for a fresh clone than
what we have.

`<systemPath>` needs no repository resolution at all, so it is **immune to mirror configuration**.
That is a materially stronger justification than the "direct analogue of Ant's raw pathelement"
argument currently in the POM comment, and it is the one that should be recorded.

**The forward risk is real and gets recorded rather than dismissed:** Maven 4 does drop system
scope. When the reactor moves to Maven 4, these ~10 permanently-vendored jars need a different
mechanism — most likely a genuine internal repository, or `build-helper:attach-artifact`. That is
a known, dated migration cost, not a surprise waiting in the dark.

**Also fixed:** stop passing `-q` in verification recipes. It suppressed exactly the diagnostic
that mattered. A silent `rc=0` is not evidence.

## 5. A documentation correction — and instance six of the same failure class

`docs/design/build-cmake-maven-migration.md:309` currently states:

> **Maven Central is reachable from this environment** (verified HTTP 200 against `repo1.maven.org`).

The conclusion drawn from it — that the dependency inventory is unblocked — happens to be true,
but **the stated evidence does not support it**, and the mechanism is entirely different from what
the sentence implies. `curl` to `repo1.maven.org` does not exercise Maven's resolution path.
Maven's actual path goes through the wildcard mirror to Nexus, which returns **401**. Resolution
works only because the mirror list excludes `smartnow-tech`, an Azure DevOps feed that proxies
Central and answers anonymously — verified by resolving a genuinely uncached artifact
(`commons-imaging:1.0-alpha3`, 785 kB, fetched over the wire from that feed).

Two consequences worth writing down:

1. The working path is a **third-party corporate feed**, not Central. If that feed's access
   changes, the inventory work stops.
2. Resolvability depends on a machine-local `~/.m2/settings.xml` that **the repository does not
   carry**. A fresh clone on another machine has none of it. That is a real portability gap for a
   migration whose endgame is deleting autotools.

Recording this as instance six is the point: a check that does not exercise the real mechanism is
not a check. `curl` ≠ Maven, in exactly the way `nm` symbol *names* ≠ `#ifdef` branch *bodies*
(RC-b) and a source-tree path ≠ a build-tree path (RC-c).

## 6. Scope

**In scope:** the `maven_jars` capture section + transition rule + fault-injection tests; the
parent-POM `<finalName>`; re-verification that both existing modules stay parity-green under their
new names; the POM comment rewrite for Decision B; the §5 doc correction.

**Out of scope:** the other 22 module POMs; the coordinate inventory; `etc/classpath.xml`
regeneration; JUnit 4→5 and surefire; the CMake Ant→Maven swap; moving Maven's output directory to
`modules/<m>/jar/`; anything about Maven 4.

## 7. Gate & acceptance

1. `maven_jars` exists as a real capture section, and **each of the four fault injections in §2.1
   has been observed to FAIL** — not asserted to.
2. Both `localization` and `commons` build as `org.scilab.modules.<name>.jar` and remain
   content-parity-green against their Ant counterparts, measured **through the dimension**, not a
   hand-run snippet.
3. No harness weakening: `fingerprint_jar`, `normalize_manifest`, and `diff.py`'s existing
   sections are unchanged in behavior. A new section is additive.
4. Ant still builds both jars unchanged; `git status` clean for `build.incl.xml`, `*/build.xml`,
   `cmake/`, `*/Makefile.am`.
5. Full suite green (183 at stage start, plus the new tests).
6. No AI-attribution trailers.

## 8. Risks

| Risk | Mitigation |
|---|---|
| `<finalName>` in the parent silently not inherited | §2.1's key alignment makes a wrong name a FAIL; that is the whole reason the gate lands first. |
| `maven_jars` captures a stale `target/` jar from an earlier run | Verification rebuilds from a cleaned `target/`; the fault-injection "delete" case pins that a missing jar is a FAIL rather than a skip. |
| The alignment key hides a genuine directory divergence | The key is deliberately synthetic and documented as such; the directory flip at the CMake swap is where real locations get compared. |
| Reading §4 later as "systemPath is fine forever" | §4 states the Maven-4 expiry explicitly and names the replacement candidates. |
