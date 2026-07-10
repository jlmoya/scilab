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

## Where to continue (concrete next steps)

1. Verify a **normal** (non-ASan) `./configure && make` cycle completes on this machine (the earlier
   OOM-kills were the memory-heavy ASan runs; normal builds should be fine — confirm).
2. Stage 1.1: add the Darwin rpaths in `configure.ac` (`ARCH_LDFLAGS` on `*-darwin*`), regenerate with
   the latest tools, `./configure && make`, and confirm the binaries run with **no** `install_name_tool`
   post-fixup. Then delete reapply's `[3b]` `@rpath` step.
3. Work through the other reapply steps one by one, each time proving the native build no longer needs it,
   deleting it from reapply, until reapply is empty → delete it.
4. Stage 2: commit the latest-tools baseline + pin the toolchain; confirm maintainer-mode ON is safe.

## ⚠️ Constraints / gotchas
- Editing tracked `configure` trips make's maintainer rule → dies at the xlnt pkg-config check (env not
  recorded) — see [[scilab-ub-miscompile-class]] REBUILD GOTCHAS for the mtime-ascend workaround.
- Full ASan/instrumented builds get OOM-killed on this machine; normal builds should be OK — verify.
- The `-fsanitize=address` worktree at `~/Projects/CLionProjects/scilab-ubsan` is a separate concern
  (ASan sweep, task #96) — don't conflate with this main-tree modernization.
