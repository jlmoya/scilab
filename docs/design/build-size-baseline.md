# Build size baseline — before the make→CMake / Ant→Maven migration

Recorded **2026-07-14**, at git tag **`build-autotools-ant`** (the last state of the project built
entirely with the old method: autotools/make + Ant). Compare against the same measurements taken at
the future tag `build-cmake-maven` when the migration is done.

**Read this first:** the `scilab-bin` executable is a *thin launcher stub* — it is not where the
code lives. The real footprint is the module dynamic libraries, the Java jars, and the packaged
app. So the meaningful before/after comparison is the **build footprint** and the **packaged app**,
not the 92 KB executable.

## Measurements (macOS arm64, autotools/Ant build)

| Artifact | Size (bytes) | Human |
|---|---:|---:|
| `scilab-bin` (GUI launcher stub) | 90,184 | 92 KB |
| `scilab-cli-bin` (CLI launcher stub) | 89,952 | 88 KB |
| Module dynamic libraries (68 `.dylib`) | — | **38.0 MB** |
| Module Java jars (30 `.jar`) | — | **74.7 MB** |
| Packaged app `/Applications/Scilab-2027.0.0.app` | — | **1.4 GB** |

(The packaged app includes the bundled JDK, all thirdparty jars/dylibs, the built help, and the
seeded toolboxes — most of which the build-system migration should *not* change. A large swing in
the app size after migration would itself be a signal worth investigating.)

## How these were measured (repeat verbatim at `build-cmake-maven` for apples-to-apples)

Run from the source tree root (`scilab/`):

```bash
# Launcher executables (the real binaries, not the libtool wrapper scripts)
stat -f%z .libs/scilab-bin
stat -f%z .libs/scilab-cli-bin

# Module dynamic-library footprint (real files only, not the version symlinks)
find modules -path '*/.libs/*.dylib' -type f -exec stat -f%z {} \; \
  | awk '{s+=$1; n++} END{printf "%d files, %.1f MB\n", n, s/1048576}'

# Module jar footprint
find modules -path '*/jar/*.jar' -type f -exec stat -f%z {} \; \
  | awk '{s+=$1; n++} END{printf "%d files, %.1f MB\n", n, s/1048576}'

# Packaged app
du -sh /Applications/Scilab-2027.0.0.app
```

## What we expect to learn

The migration's goal is maintainability, not size — but the comparison is worth having:
- **Dylib footprint** should be roughly unchanged (same compiler, same `-O2`/`-fwrapv`, same
  sources). A large delta would flag a lost optimization flag or a changed link.
- **Jar footprint** could shrink once Maven's dependency management lets us drop the ~23 dead/EOL
  jars that the hand-rolled classpath currently carries.
- **Packaged app** should be stable; the migration touches how things are built, not what ships.

See `docs/design/build-cmake-maven-migration.md` for the migration itself.
