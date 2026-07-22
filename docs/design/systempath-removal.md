# Removing `systemPath`: let Maven resolve the third-party jars

**Status:** IN PROGRESS. Evidence gathered and Tier A implemented 2026-07-21.
**Goal:** stop using `<scope>system</scope>` + `<systemPath>` so Maven resolves and updates
third-party jars normally — the point of using Maven at all.

## Why this is worth fixing

`system` scope is deprecated. It excludes the dependency from resolution, from transitive
handling, and from `versions:use-latest-versions`, which is why the versions-plugin audit could
*report* `lucene 9.10.0 -> 10.5.0` but never apply it. Every upgrade today is manual: replace the
jar in `thirdparty/`, edit `fetch-thirdparty.sh`, and edit both `<version>` and the version
embedded in `<systemPath>`.

It exists because the Ant→Maven migration was parity-gated: `thirdparty/` was Ant's layout, and
transcribing it verbatim was the *reproduce, don't improve* choice. That constraint is now gone.

## What makes this tractable (measured, not assumed)

- **`thirdparty/` is already generated, not vendored** — gitignored, **0** tracked jars, 81 on
  disk, populated by `fetch-thirdparty.sh`. Swapping the *fetcher* changes no tracked content.
- **`etc/classpath.xml` enumerates 87 explicit `<path value>` entries**, so any extra transitive
  jar Maven resolves is **inert** at runtime rather than silently joining the classpath.
- **`~/.m2` already caches these artifacts**, so offline builds keep working after a first resolve.

## Tier classification — 27 distinct jars, SHA-1 verified against Maven Central

| Tier | Jars | Verdict |
|---|---|---|
| **A — byte-identical to Central** | 13 | gson 2.10.1, jna 5.14.0, lucene-core + lucene-queryparser 9.10.0, batik-all 1.17, fop-core 2.9, xmlgraphics-commons 2.9, jlatexmath + jlatexmath-fop 1.0.7, jaxb-api 2.3.1, jakarta.activation-api 2.1.3, javax.annotation-api 1.3.2, **jcef-api**. SHA-1 identical, so conversion cannot change behavior. |
| **B — artifact exists, our version does not** | 4 | `javax.help:javahelp` (Central has only **2.0.05**; we pin 2.0), `jeuclid-core` 3.1.14, `gluegen-rt` 2.5.0, `jogl-all` 2.5.0. Requires a *version* decision, not a repo. |
| **C — absent from Central entirely** | 7 | `cc.sosonline:swing-gpu-surface` (first-party, unpublished), `jrosetta-API`/`jrosetta-engine`, `jgraphx`, `flexdock`, `jediterm-core`/`jediterm-ui`. Needs another repository, publication, or stays vendored. |
| **D — differs from Central** | 3 | `javafx-base`/`graphics`/`swing` 25.0.2. Ours are renamed SDK jars (`javafx.base.jar`, 662 classes, **0 natives**); Central splits classes from per-platform natives via classifiers. |

## Two traps found while designing this

**1. Filename mismatch would silently break the runtime.** `copy-dependencies` writes
`artifactId-version.jar`. 23 of 28 mappings already match today's names, but **5 do not** and
`classpath.xml` resolves by filename:

| Current | Maven's default name |
|---|---|
| `javafx.base.jar` / `.graphics` / `.swing` | `javafx-base-25.0.2.jar` … |
| `jcef-api.jar` | `jcef-api-jcef-d3de827+cef-146.0.10+…jar` |
| `jhall-2.0.jar` (as `javax.help:javahelp`) | `javahelp-2.0.jar` |

This is why staging uses `maven-dependency-plugin:copy` with explicit `<destFileName>` per
artifact, centralized in the parent POM, rather than per-module `copy-dependencies`.

**2. One jar is declared under two different coordinates.** `thirdparty/jhall-2.0.jar` is
`javax.help:jhall:2.0` in `modules/gui/pom.xml` but `javax.help:javahelp:2.0` in
`modules/helptools/pom.xml`. Neither resolves (`dependency:get` fails for `jhall:2.0`). Recorded
as a defect in its own right — the same file must not have two identities.

## Design

Maven resolves each dependency normally; a single `maven-dependency-plugin:copy` execution in the
parent stages the resolved jars into `thirdparty/` under their existing filenames. `classpath.xml`,
`bin/scilab` and `package-macos.sh` are untouched, so the runtime layout and the `.app` bundle do
not change. `fetch-thirdparty.sh` keeps only what Maven cannot supply: natives (jcef, JOGL) and
Tier C.

## Remaining decisions (not taken unilaterally)

- **Tier B is a version bump, not a repo fix.** `javahelp` 2.0 → 2.0.05 touches the **help
  browser**, which was verified working on 2026-07-21. It needs its own test pass.
- **Tier C needs external repositories** (JogAmp, JetBrains) — a supply-chain surface increase —
  and `swing-gpu-surface` is *ours* and unpublished, so no repository can ever supply it. Its real
  options are: install to the local repo, promote it to a reactor module, or keep it vendored.
- **JOGL/GlueGen are slated for deletion** (`docs/design/opengl-removal.md`), so effort spent
  resolving them from a repository is likely wasted.

## Related

- `docs/design/deferred-fixes-register.md` — B13/B14 and §5b–5d
- `docs/design/opengl-removal.md` — why JOGL investment should be deferred
