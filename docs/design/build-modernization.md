# Build-system modernization (in progress, started 2026-07-10)

**Goal (user, top priority):** the build must be **trustworthy and infallible with the *latest* installed
tools** — no pinning old versions, no manual band-aids. A plain `./configure && make` (maintainer-mode
ON) on macOS arm64 must produce a working Scilab. Eliminate `reapply-macos-fixes.sh`. This is the
NORTH-STAR (task #65) made concrete.

## What broke, and why (the trigger for this work)

Running a `./configure`/`make` regenerated ~130 autotools files with the **locally installed** tools and
broke the whole dev tree. Root cause, confirmed:

- Committed generated files (`configure`, `Makefile.in`, `libtool`) were produced with **libtool 2.4.7**.
  This machine has **libtool 2.5.4** (+ autoconf 2.73, automake 1.18.1, gettext-tools). Any regeneration
  replaces the committed files with this machine's output.
- **libtool 2.5.4 records `@rpath/lib….dylib` for OS/toolchain libs** (libc++, libcurl, libgfortran,
  libquadmath, libjli) where 2.4.7 recorded `/usr/lib/...` absolute. `@rpath` is the *modern, correct*
  relocatable form — but the dev binaries have **no `LC_RPATH` entries** to resolve it → `dyld` Abort trap
  6 at startup, tree dead.
- `make`'s **maintainer-mode** silently re-ran autoconf/automake (mtime trigger) → the regeneration was
  accidental.

## The three real defects (all must be fixed for "infallible")

1. **`@rpath` not resolvable** — binaries reference `@rpath/libc++.1.dylib` etc. but carry no rpath to
   `/usr/lib`, the Homebrew gcc dir, or the JDK lib dir. Fix at the **link** (configure.ac/Makefile.am
   LDFLAGS: add the rpaths, or keep OS libs absolute) so no post-link `install_name_tool` is needed.
2. **macOS band-aids live outside the build** — `reapply-macos-fixes.sh` post-processes every rebuild:
   deployment target, xlnt/xcos `@loader_path`, helptools activate, Vulkan classpath, macro rebuild,
   menu name, and now the `@rpath` fixup. Each is a build defect that should be fixed **in the build**.
3. **Regeneration isn't reproducible/correct** — different tool versions → different output; a *partial*
   reconfigure (`config.status`) reuses stale substitutions (e.g. `@LWJGL@`/`@SWING_GPU_SURFACE@` come out
   raw because the committed `configure` predates the Vulkan vendoring). A *full* `autoreconf` from the
   current `configure.ac` fixes the substitutions.

## Decision: modernize autotools-in-place, not a CMake rewrite (now)

A full CMake/Maven rewrite of ~3600 native objects + Ant Java + genlib macros is a multi-week epic (kept
as the long-term option). The achievable, high-value modernization: **make the autotools build
modern-correct** so latest tools + `./configure && make` just work, and delete `reapply-macos-fixes.sh`.

## Plan (staged)

- **Stage 0 — stabilize (DONE for now):** the dev tree is repaired (`@rpath` pinned to absolute on all
  images via `install_name_tool` + reapply). Maintainer-mode is **NOT** permanently disabled (user
  directive) — stability comes from the fixes below, not from freezing regeneration.
- **Stage 1 — fix the macOS link natively (eliminate reapply's binary fixups):**
  1. `@rpath`: add the resolving rpaths at link time (Homebrew, gcc, JDK, `/usr/lib`) via
     `configure.ac`'s Darwin `LDFLAGS`/`ARCH_LDFLAGS`, so libtool 2.5.4's `@rpath` binaries run natively.
  2. xlnt / xcos `@loader_path`, deployment-target, menu name → fold into the link/`configure.ac`.
  3. Vulkan classpath / macro-build ordering → fix in `configure.ac` + the Makefile targets.
- **Stage 2 — reproducible regeneration:** commit the **latest-tools** regenerated `configure`/
  `Makefile.in`/libtool as the new baseline (once Stage 1 makes it correct), and pin the toolchain
  (documented required versions or a Dockerized build env) so any environment regenerates identically.
  Then re-enable/keep maintainer-mode ON and confirm `make` regen produces a working tree.
- **Stage 3 (long-term):** CMake — declarative, no checked-in generated files, no version drift.

## Progress log — 2026-07-10 (a full `autoreconf && ./configure` with the latest tools)

Ran the actual latest-tools reconfigure. Autoreconf **succeeds** (autoconf 2.73 / automake 1.18.1 /
libtool 2.5.4). Three defects it surfaced — all fixed at the source in `configure.ac` / the vendored m4:

1. **`configure` was stale vs `configure.ac` (the Vulkan wiring).** The committed generated `configure`
   predated the Vulkan `AC_JAVA_CHECK_JAR` block, so a fresh reconfigure was the *first* time those
   checks ran. **VERIFIED FIXED** by a real reconfigure: all five (`lwjgl`, `lwjgl-vulkan`, `lwjgl-jawt`,
   `lwjgl3-awt`, `swing-gpu-surface`) resolve, and the `@LWJGL@`/`@SWING_GPU_SURFACE@` tokens in
   `etc/classpath.xml` are substituted natively. ⇒ **build-macos.sh patch (d) and reapply `[5/7]` are
   now obsolete.**
2. **`AC_JAVA_CHECK_JAR` dependency-order bug.** The Vulkan block checked module jars before core, but
   `lwjgl-jawt`'s `org.lwjgl.system.jawt.JAWT extends org.lwjgl.system.Struct` (core), and the checker
   builds a `URLClassLoader` whose parent is the accumulated `ac_java_classpath` — so a module can't
   load before core is on it. **FIXED**: reorder core `[lwjgl]` first (the checker pins each name to its
   *containing* jar via `getResource`, so the greedy `lwjgl*.jar` glob can't mis-resolve — the old
   "core last" caution was unnecessary). `configure` now completes clean.
3. **The maintainer-mode reconfigure hazard (`config.status --recheck` fails).** When `make` regenerates
   `configure` (mtime trigger) it runs `config.status --recheck`, which replays only *precious* env vars.
   The stale **vendored `m4/pkg.m4`** (a pre-`serial 13` Scilab fork) made only `PKG_CONFIG`
   precious — not `PKG_CONFIG_PATH` — so the recheck re-ran `configure` without it and died at
   `cannot find pkg-config package for xlnt`. **FIXED**: dropped the vendored fork so autoreconf uses the
   installed `serial 13 (pkgconf)` macro, which makes `PKG_CONFIG_PATH` precious ⇒ the recheck replays it.
   (Modernization principle in action: a vendored tool-macro copy is the same "borrowed time" drift as
   the libtool 2.4.7/2.5.4 split — prefer the installed tool.)

4. **`SHARED_LIB_EXT` baked libtool's raw shell expression (a *fourth* libtool-2.5.4 incompatibility).**
   `configure.ac` did `AC_DEFINE_UNQUOTED([SHARED_LIB_EXT],["$shrext_cmds"])`. libtool **2.5.4** sets
   `$shrext_cmds` to a shell *expression* — `` `test .$module = .yes && echo .so || echo .dylib` `` —
   where 2.4.7 set the literal `.dylib`. So the regenerated `machine.h` got
   `#define SHARED_LIB_EXT "`test .$module = .yes && echo .so || echo .dylib`"`, and the dynamic-library
   loader built garbage filenames (`dlopen(libsci...`test...`)`). **FIXED**: `module=no; eval
   "scilab_shrext=\"$shrext_cmds\""` before the define ⇒ `machine.h` now has `#define SHARED_LIB_EXT
   ".dylib"` (config-verified). This is the textbook "fresh reconfig with latest tools silently breaks
   the build" — invisible until you actually regenerate.

**Stage 1.1 rpath fix** (`configure.ac` Darwin `ARCH_LDFLAGS`/`LDFLAGS` += `-Wl,-rpath,/usr/lib` + the
Homebrew gcc runtime dir; libjli stays launcher-resolved): **VERIFIED** by a full race-free build
(`--disable-maintainer-mode`, one clean sequence): both `.libs/scilab-cli-bin` and `.libs/scilab-bin`
carry the `/usr/lib` + gcc-runtime rpaths, and the OS/toolchain libs (`libc++`, `libcurl`, `libgfortran`,
`libquadmath`) stay `@rpath` (not `install_name_tool`'d) — native resolution, no post-link fixup ⇒
**reapply `[3b]` obsolete**. (Rpaths land in both `LDFLAGS` and `SCI_LDFLAGS`/`ARCH_LDFLAGS`, mirroring
`-mmacosx-version-min`; a harmless `ld: duplicate -rpath ignored` warning results.)

**The maintainer-mode `make -j` race** (real, separate): a partial autoreconf/edit leaves generated
files newer than `config.status`, so `make` autoreconfs mid-build and a parallel compile hits the
momentarily-reinstalled `config/depcomp` (`Error 127`). A clean `autoreconf → configure → make` sequence
(uniform mtimes) avoids it; verification builds use `--disable-maintainer-mode` to be safe. `configure.ac`
has bare `AM_MAINTAINER_MODE` (ON) and `--disable-maintainer-mode` is **not** committed anywhere — the
normal git-checkout build (uniform mtimes) doesn't regen, so nothing to change there yet.

### Still ahead
- Confirm the rpath build result; if green, delete reapply `[3b]` and `[5/7]` + build-macos.sh patch (d).
- Fold the remaining build-time patches into the source: (a) OpenMP Apple-clang flags
  (`-Xpreprocessor -fopenmp` / `-lomp`), (b) helptools disable-stub gating, (c) spreadsheet C++20 — all
  belong in `configure.ac`/`Makefile.am`, not a post-configure `sed`.
- Then reapply's `[1/7]` deployment-target (already `-mmacosx-version-min=11.0` in `configure.ac` — verify
  redundant), `[2/7]` xlnt / `[3/7]` xcos `@loader_path`, `[6/7]` macro-build ordering, `[7/7]` menu name.
- Stage 2: commit the latest-tools regenerated baseline (`configure`, `Makefile.in`, `aclocal.m4`, m4/)
  + pin the toolchain; confirm maintainer-mode ON reconfigures cleanly.

## ⚠️ Constraints / gotchas
- The xlnt pkg-config recheck death is **FIXED** (see progress #3). The general rule stands: anything the
  build needs from the environment must be a *precious* autoconf var, or `config.status --recheck` drops
  it. xlnt still lives out-of-tree in `../xlnt-prefix` (found via `PKG_CONFIG_PATH`) — a separate
  reproducibility gap (should be a standard prefix / formula) tracked but not yet fixed.
- Full ASan/instrumented builds get OOM-killed on this machine; normal builds are fine (proven — several
  full builds this session).
- The `-fsanitize=address` worktree at `~/Projects/CLionProjects/scilab-ubsan` is a separate concern
  (ASan sweep, task #96) — don't conflate with this main-tree modernization.
