# Building & running Scilab from source on macOS (Apple Silicon / arm64)

The complete handbook for building and running **Scilab** (branch 2027.0) from source on
macOS arm64 (tested on macOS 26 "Tahoe", **JDK 25**). There is **no macOS CI**, so this fork
carries the macOS support itself — all of it **in the build system**: a plain
`./configure && make` produces a fully working tree. There are no post-configure patches and
no post-build fixup scripts (the old `reapply-macos-fixes.sh` is gone; see
[`docs/design/build-modernization.md`](../design/build-modernization.md) for how).

The official reference is the GitLab wiki
[setup Scilab repository macOS arm64](https://gitlab.com/scilab/scilab/-/wikis/Developers/setup-Scilab-repository-macOS-arm64)
(which uses **conda**). This guide uses **Homebrew**.

> - Build system: GNU **Autotools** (`./configure` → `make`), **not** CMake.
> - Java: **JDK 25** at every level — runtime, build toolchain, and language level (`source/target=25`, Java-25 bytecode; see §6). `ant` via sdkman/Homebrew.
> - Source root is the inner `scilab/scilab/` directory.

---

## TL;DR — the whole build (fresh clone)

```sh
cd scilab/scilab
./fetch-thirdparty.sh       # one-time: pinned third-party payload (jars, dylibs, xlnt)  (~5 min)
./build-macos.sh            # = plain ./configure <flags> && make                        (~45 min)
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./bin/scilab   # run the GUI
```

`fetch-thirdparty.sh` populates everything git doesn't carry — `thirdparty/`, `lib/thirdparty/`,
and the out-of-tree `../xlnt-prefix` (which it builds from source) — from version-pinned,
sha256-verified URLs, with an offline cache in `~/.cache/scilab-thirdparty`. Run it again anytime
(`--verify-only` audits the payload; `--force` reinstalls). `build-macos.sh` is a thin convenience
wrapper: it only pins `JAVA_HOME` and documents this machine's `--with-…` dependency locations,
then runs a plain `./configure && make`. Nothing is patched before, during, or after the build.
The Homebrew toolchain (§1) must be installed once.

---

## 0. The build system is modern-native

The committed **generated** autotools files (`configure`, the `Makefile.in`s, `aclocal.m4`,
`config/`, `m4/`) are regenerated with the **latest tools** (autoconf 2.73, automake 1.18.1,
libtool 2.5.4, pkgconf's `pkg.m4` serial 13) and match `configure.ac`/`Makefile.am` exactly.
`autoreconf -fi` is **safe and supported** — regeneration produces the same behavior as the
committed files. Maintainer mode is ON (the automake default); a source-tree edit that touches
`configure.ac`/`Makefile.am` regenerates cleanly during `make`.

Everything macOS needs is part of the build itself:

- **configure is self-sufficient on macOS** — it derives the Homebrew paths (`CPPFLAGS`/
  `LDFLAGS`/`PKG_CONFIG_PATH`, including keg-only `libomp`/`libarchive`) and the out-of-tree
  `../xlnt-prefix` pkg-config path itself. No environment exports needed.
- **Runtime link correctness is done at link time** — rpaths for the `@rpath` OS/toolchain
  libs, the executables' deployment-target SDK stamp, xlnt/xcos resolution, the GUI process
  name. Details in §4.

---

## 1. Toolchain & native dependencies (Homebrew)

```sh
brew install gcc            # provides gfortran
brew install autoconf automake libtool pkg-config cmake gettext
brew install openblas arpack fftw hdf5 pcre2 suite-sparse eigen
brew install libmatio apache-arrow libomp libarchive fast_float
```

Notes:
- `apache-arrow` provides the `arrow`/`parquet` pkg-config packages. The dev branch pins Arrow
  **19**; Homebrew ships **24**, whose headers need **C++20** — the `spreadsheet` module builds
  at `-std=c++20` (per-target flags in its `Makefile.am`). The global standard stays **C++17**
  (bumping it globally breaks C++17-only code in `ast`/`sparse`).
- `libomp`, `libarchive` are **keg-only** — configure adds their `include`/`lib` paths itself.
- `fast_float` is a new, undeclared `scicos` dependency (header-only).
- `cmake` is used once by `fetch-thirdparty.sh` to build xlnt from source (§2).

---

## 2. Third-party payload — `fetch-thirdparty.sh`

Everything git doesn't carry is installed by one script, from **version-pinned, sha256-verified**
sources (downloads cached in `~/.cache/scilab-thirdparty`; idempotent; `--verify-only` audits,
`--force` reinstalls):

```sh
cd scilab/scilab
./fetch-thirdparty.sh
```

What it installs, and from where:

| Payload | Source |
|---------|--------|
| `thirdparty/*.jar` bulk (JOGL, flexdock, batik/fop, lucene, …) + `fonts/`, `docbook/` + JOGL dylibs in `lib/thirdparty/` | the official Scilab **prerequirements** tarball (pinned; the script strips its duplicate versioned JavaFX/jcef jars) |
| **JavaFX 25.0.2** — `javafx.{base,graphics,swing}.jar` + all JavaFX dylibs (incl. `libprism_mtl`) | Gluon SDK zip. Kept at **JDK parity** (the JVM is JDK 25); its one consumer is `JFXScilabFileChooser`. |
| JediTerm 3.70 (terminal), gson, jna, kotlin-stdlib, annotations, directory-watcher, slf4j | Maven Central + JetBrains intellij-dependencies |
| LWJGL 3.3.4 (core/vulkan/jawt/natives) + lwjgl3-awt 0.2.4 (Vulkan renderer) | Maven Central |
| `libMoltenVK.dylib` 1.4.1 (universal) | Khronos MoltenVK GitHub release |
| `jcef-api.jar` + the CEF framework/helpers under `lib/thirdparty/jcef/` (embedded browser) | Maven Central (`me.friwi:jcef-api`) + jcefbuild 1.0.66 — same release tag, so the jar and natives always match |
| `swing-gpu-surface-0.1.0.jar` (first-party Layer-1 GPU surface) | vendored **in this repo** (`modules/prebuildjava/firstparty/`) |
| compatibility symlinks (`lucene-analyzers-common` → `lucene-core` for the Lucene-9 class move, `jogl2`/`gluegen2-rt` aliases) | created by the script |
| `../xlnt-prefix` — headers, `libxlnt.1.6.1.dylib` with an `@rpath` install name, correct `xlnt.pc` | **built from source** (pinned xlnt 1.6.1 tarball, cmake ≈1 min) — no installed-Scilab dependency, and it fixes xlnt 1.6.1's two packaging bugs (ignored `INSTALL_NAME_DIR`, relative `libdir` in its .pc) |

> The prerequirements URL is a *mutable* "branch-main" artifact — pinned by sha256, so an upstream
> republish fails loudly instead of drifting silently; bump the pin deliberately.
> `make clean` does **not** remove `thirdparty/` or `lib/thirdparty/`, so the payload survives rebuilds.

---

## 3. The build

```sh
cd scilab/scilab
./build-macos.sh        # or run the same ./configure line by hand, then make
```

A full build is ~45 min. Do **not** pass `--enable-stop-on-warning` (macOS emits warnings the
build would otherwise reject); `--without-tk` is mandatory on macOS. `--disable-build-help`
skips building the documentation (the helptools *module* stays active — help infrastructure
works, the doc pages just aren't compiled).

> **Flag policy — `-fwrapv` (do not remove).** All C/C++/Fortran compiles at `-O2` with
> `-fwrapv` (signed integer overflow wraps), set in `configure.ac`. Decades-old numerical code
> in this tree overflows signed integers; modern clang/gcc at `-O2` miscompile that undefined
> behaviour — `rand()` returned `Inf` for every element because `durands()`'s init loop
> terminated via signed overflow (see `docs/design/modernization-assessment.md`). The flag
> removes the optimizer's licence to exploit it, at zero measurable cost. It is deliberately
> `-fwrapv` and **not** `-fno-strict-overflow`: clang expands the latter to `-fwrapv-pointer`
> as well, which explodes compile times on template-heavy TUs. CI (`guard:ub-miscompile`)
> greps the policy into place and diffs a `durands` O0/O2 run so the class cannot silently
> return.

---

## 4. How the macOS specifics are handled (all in the build)

Everything that used to be a post-configure patch or a post-build fixup now lives in
`configure.ac` / the `Makefile.am` files:

| Concern | Where it lives now |
|---------|--------------------|
| **OpenMP flags** (Apple clang needs `-Xpreprocessor -fopenmp` + `-lomp`) | `configure.ac` Darwin branch of the OpenMP check. |
| **helptools disable-stub** (empty convenience lib fails `ld`) | `modules/helptools/Makefile.am` defines the nogui stub source unconditionally. |
| **spreadsheet C++20** (Arrow 24 headers) | `modules/spreadsheet/Makefile.am` per-target `-std=c++20` (the project stays C++17; the per-target flag comes later on the compile line and wins). |
| **Vulkan-renderer jars** (`@LWJGL@`…`@SWING_GPU_SURFACE@`) | `AC_JAVA_CHECK_JAR` in `configure.ac` (dependency order: core `[lwjgl]` first) substitutes the found jars into `scilab-lib.properties` (absolute, for `javac`) and `etc/classpath.xml` (relocated by `package-macos.sh` for the app). |
| **`@rpath` OS/toolchain libs** (libtool 2.5.4 records `@rpath/libc++.1.dylib` etc.) | `configure.ac` adds `-Wl,-rpath,/usr/lib` + the Homebrew gcc runtime dir to every link. |
| **Deployment target** (AppKit "linked on or after" gate — Appendix A) | `-mmacosx-version-min=11.0` (all compiles/links) **plus** `-Wl,-platform_version,macos,11.0,11.0` on the two executables (top `Makefile.am`), which pins the **SDK stamp** too — the actual gate. |
| **xlnt resolution** (out-of-tree dylib) | The dylib's install name is `@rpath/libxlnt.1.6.1.dylib` (§1); `modules/spreadsheet/Makefile.am` adds rpaths (`@loader_path/`, the xlnt libdir) and keeps a copy of the dylib next to the module (dev tree + packaged app both resolve). |
| **xcos → scicos** (dlopen'ed module; deps recorded under the not-yet-installed `pkglibdir`) | `modules/xcos/Makefile.am` rewrites the two scicos deps to `@rpath/…` post-link and adds rpaths covering the build tree (`@loader_path/../../scicos*/.libs`) and the installed layout (`$(pkglibdir)`). |
| **helptools activation** | `etc/modules.xml.in` activates helptools unconditionally (building docs and running the help machinery are independent). |
| **Macros** | Built by `make`'s own `macros` target — the runtime is already fully resolved at that point, so the in-make build produces all ~49 valid `macros/lib` files. |
| **GUI process name** (menu bar/Dock show the executable filename) | Top `Makefile.am` `macos-process-name` target: hardlinks `.libs/scilab-bin` to `.libs/Scilab-<version>` and points the libtool wrapper at it, on every `make`. |

---

## 5. Building the macros by hand (rarely needed)

The macro build runs as part of `make`. To rebuild by hand (e.g. after editing `.sci` files
without a full `make`):

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./bin/scilab-cli -ns -noatomsautoload -nouserstartup -quit \
  -f modules/functions/scripts/buildmacros/buildmacros.sce
```

`-ns` (no startup) is essential — it stops `scilab-cli` from trying to *load* the macros it's
about to *build*. A healthy run produces ~49 `macros/lib` files. For a **single** module from
inside Scilab: `genlib("foolib", SCI+"/modules/foo/macros", %t)`.

> Known cosmetic issue: the full buildmacros run currently exits with a nonzero status even
> though every library builds correctly (the `macros` make target tolerates it). Tracked as a
> runtime bug, not a build defect.

---

## 6. JDK 25 notes

The branch is fully migrated to **JDK 25 at all levels** — runtime, build toolchain, *and* Java
language level. `source="25" target="25"` is set in `build.incl.xml.in` (the template that
generates `build.incl.xml`), `build.qa.incl.xml`, and `modules/javasci/build.xml`; emitted
bytecode is **major version 69** (Java 25), not 61 (Java 17). `configure` detects and accepts
JDK 17–25 — `m4/java.m4` probes `java.util.SequencedCollection` (21) and `java.lang.IO` (25),
and the version gate accepts `17|…|25`. Validated end-to-end: compiles + links, all jars at
bytecode 69, CLI, GUI with graphics, and virtual threads (`Thread.ofVirtual()`) all work. Two
JDK-25 specifics worth knowing:

- **`LibraryPath.addPath` (committed fix).** `org.scilab.modules.jvm.LibraryPath.addPath`
  augments `java.library.path` at runtime (the launcher seeds it empty, then the C side adds
  each native dir via JNI) so JOGL can find `gluegen_rt`. It did so by reflectively writing the
  `static final` `NativeLibraries$LibraryPaths.USER_PATHS`. **JDK 18+ (JEP 416) forbids
  reflective writes to `static final` fields** → `UnsupportedOperationException: set` → the GUI
  dies with `UnsatisfiedLinkError: no gluegen_rt`. Fixed by writing the field via **`Unsafe`**
  (works on JDK 17–25), with a graceful fallback. This is the one genuine code incompatibility
  the upgrade surfaced.
- **`--enable-native-access`.** JDK 24+ restricts JNI; Scilab loads many native libs, so 25
  prints `WARNING: A restricted method … has been called`. Harmless today (warnings only); add
  `--enable-native-access=ALL-UNNAMED` to `etc/jvm_options.xml` to silence and future-proof.

For a different JDK, edit `JDK` in `build-macos.sh` (and the `.app` launcher's pin, §8).
**The terminal/GUI runs in Scilab's JVM, so the JDK is process-wide — there's no per-component
JDK.**

---

## 7. Running

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./bin/scilab          # GUI console (also prints console output to the terminal)
./bin/scilab-cli      # pure command-line, no JVM (interpreter only)
./bin/scilab-adv-cli  # command-line + JVM
```

Never run `.libs/scilab-bin` directly — that raw binary resolves dylibs to the uninstalled
`/usr/local/lib/scilab`. Always use the `bin/` launchers (or the libtool wrapper `./scilab-bin`).
A healthy GUI process has ~47 threads (JVM + AWT EDT + Swing). For a **no-Terminal** Finder
launch, use the packaged app (§8).

Quick non-GUI sanity check:
```sh
./bin/scilab-cli -nb -e "disp(6*7); disp(getversion()); exit"   # -> 42, scilab-branch-2027.0
```

---

## 8. A standalone, relocatable app + toolbox manager (`package-macos.sh`)

**`package-macos.sh`** (source root) produces an **independent**
`/Applications/Scilab-2027.0.0.app`: a relocated copy you can use daily, decoupled from the dev
tree, with a git-driven toolbox manager. It still uses the machine's Homebrew dylibs + a system
JDK (it is **not** a notarized, dependency-vendored redistributable for other Macs).

### Build / refresh

```sh
cd scilab/scilab
./build-macos.sh        # a healthy dev build first
./package-macos.sh      # -> /Applications/Scilab-2027.0.0.app  (~1 min)
```

`package-macos.sh` is **idempotent and incremental**: it `rsync`s the dev build into
`Contents/Resources/scilab/` (skipping `*.o`/`*.lo`), rewrites the dev abs-path → app path in the
~17 text configs (`classpath.xml`, `*.properties`, `*.la`, the libtool wrapper scripts — the
Mach-O binaries hold it only as harmless cruft and resolve siblings relatively), writes the
launcher + `Info.plist` + icon, installs the toolbox manager, and creates the `scilab2027` CLI.
**Re-run it after any rebuild** — seconds, and your toolboxes/config (which live *outside* the
bundle) are untouched. `--rebuild-toolboxes` also rebuilds native toolboxes (for a core-ABI change);
`--jdk-version N` pins a different default JDK.

### Configurable JDK

The launcher resolves `JAVA_HOME` in order: a one-line file **`~/.config/scilab-app/java_home`**
→ the inherited `$JAVA_HOME` → `/usr/libexec/java_home -v 25`. When you upgrade Java, edit that
one line (default pin: 25 — `bin/scilab` honors `JAVA_HOME`, and its own fallback probes JDK 17,
which you may not have, so the launcher sets it explicitly).

### Isolated config (its own SCIHOME)

The app launches with **`-scihome ~/.Scilab/scilab-app-2027`** (Scilab **ignores the `SCIHOME`
env var** — only the launch flag works), so its preferences + installed-toolbox set never mix
with the dev build's `~/.Scilab/scilab-branch-2027.0`.

### Toolbox manager

Built-from-source toolboxes (e.g. the ports under `~/Projects/SciLabProjects`) are managed by an
intrinsic Scilab module — **`modules/toolbox_manager/`** — registered in `etc/modules.xml.in`, so
the verbs load in **every** Scilab session (dev tree *and* the app), not just the packaged app.
`tbxHelp()` prints a console reference, and the module ships a `help/en_US` chapter that compiles
into the Help browser on a help-enabled build.

| Verb | Action |
|------|--------|
| `tbxManager()` | GUI check-list — pick toolboxes, then **Apply** (save) or **Apply & Relaunch** (save + restart so they load now). Verified set pre-ticked. |
| `tbxInstall("name"[, "local"｜"remote"])` | git clone/pull (prefers a local `SciLabProjects/<name>` clone, else `jlmoya` GitLab→GitHub) → build → register for autoload. |
| `tbxUpdate(["name"])` | git pull + rebuild (all, or one — always the latest). |
| `tbxRemove("name")` ／ `tbxList()` ／ `tbxHelp([name])` | unregister+delete clone ／ show the set ／ console help. |

A manifest `$SCIHOME/installed_toolboxes.tbx` (TSV: `name⇥path⇥source⇥autoload`) is the source of
truth; `.scilab` autoloads every `autoload=1` entry at startup. **First launch** (empty manifest)
auto-opens `tbxManager()` with the verified set pre-checked — pick, **Apply & Relaunch**, done.

### `scilab2027` — the console from any terminal

`package-macos.sh` installs **`scilab2027`** on your `PATH` (`/usr/local/bin`): the app's console
(`bin/scilab -nw`) with the same JDK resolution + isolated SCIHOME. Your toolboxes autoload here too.

### Two platform constraints worth knowing

- **Macros activate at launch, not on the spot.** Scilab makes a toolbox's *macros* global only
  when its `loader.sce` is exec'd at **top level** (the `.scilab` autoload), never from inside a
  function — so enabling a toolbox via the GUI/verbs takes effect on the **next launch**. That is
  exactly why the GUI has **Apply & Relaunch** (it `open -n`s the bundle and `exit`s). Native
  *gateways* load immediately; only the macro wrappers need the relaunch.
- **Help is suppressed during autoload.** Locally-built toolboxes have no built `jar/`, so in
  GUI/console mode `add_help_chapter` would error and abort the load (a `scilab-cli -nb`/NWNI run
  skips help, which hides it). `.scilab` shadows the help loaders as no-ops during the autoload
  loop, then restores them (functions work fully; per-toolbox `help` pages are not loaded — build
  them separately if wanted).

### Files

The toolbox manager is the Scilab module `modules/toolbox_manager/` (macros + `help/en_US` +
`etc/toolbox_manager.start`, registered in `etc/modules.xml.in`); `package-macos.sh` +
`macos-app/dot-scilab.template` (the app's autoload startup) live in the source root. All are
committed; the built macro lib (`macros/lib`) is gitignored and rebuilt on demand, and the
generated `/Applications/…app` is machine-specific and is not committed. Full design + rationale:
[`docs/design/macos-app-packaging.md`](../design/macos-app-packaging.md).

### The GUI process name

macOS shows the *executable filename* of the GUI process in the menu bar and Dock — not
`argv[0]`, and not the `apple.awt.application.name` JVM property. The build's
`macos-process-name` target (top `Makefile.am`) therefore hardlinks `.libs/scilab-bin` to
`.libs/Scilab-<version>` and points the libtool wrapper at it on every `make`, so both the dev
tree and the packaged app show **Scilab-2027.0.0**.

---

## 9. Troubleshooting

| Symptom | Cause → fix |
|---------|-------------|
| `configure: error: cannot find pkg-config package for xlnt` | `../xlnt-prefix` missing → §1 (configure finds it there by itself). |
| `Library not loaded: @rpath/libxlnt.1.6.1.dylib` | The prefix dylib still has its old bare install name → §1 (`install_name_tool -id @rpath/… + codesign`), then relink spreadsheet (`touch modules/spreadsheet/src/c/*.c && make`). |
| `error: no template named 'span' …apache-arrow…` | Arrow 24 needs C++20 → already per-target in `modules/spreadsheet/Makefile.am`; make sure the tree is reconfigured (fresh `./configure`). |
| `no matching function for call to 'cwiseOp'` in `ast/sparse` | C++20 applied **globally** → keep it module-local to spreadsheet. |
| GUI traps `SIGTRAP`/`EXC_BREAKPOINT` at startup or first `plot()` | Deployment target/SDK stamp too new → Appendix A. Fresh binaries are stamped 11.0/11.0 natively; check with `otool -l .libs/scilab-bin \| grep -A4 LC_BUILD_VERSION`. |
| `Library not loaded: /usr/local/lib/scilab/…` on xcos launch | The scicos dep rewrite didn't run → `make` (the xcos `all-local` hook does it); verify with `otool -L modules/xcos/.libs/libscixcos.2027.dylib \| grep scicos` (should show `@rpath/…`). |
| `load: …/macros/lib is not a valid lib file` | Stale/corrupt macro lib → rebuild macros (§5). |
| GUI: `UnsupportedOperationException: set` at `LibraryPath.addPath` + `UnsatisfiedLinkError: no gluegen_rt` | JDK-25 final-field write blocked → §6 (the committed `Unsafe` fix; rebuild `modules/jvm`). |
| `sed: RE error: illegal byte sequence` in your own scripts | macOS `sed` + non-UTF-8 file → prefix with `LC_ALL=C`. |

---

## Appendix A — the GUI / plotting crash on macOS 14+/26 (deployment-target deep-dive)

**Symptom:** the GUI traps (`SIGTRAP` / `EXC_BREAKPOINT`) at startup or when `plot()` first
renders. The faulting stack is JOGL realizing an onscreen `NSWindow`/CGL drawable
(`OSXUtil_*NSWindow*` → AppKit) off the Cocoa main thread.

**Why:** Scilab runs its Cocoa runloop on the OS main thread (thread 0) and the
JVM/interpreter on a **secondary** pthread; JOGL initializes graphics there. **macOS 14+/26
added a hard main-thread assertion for AppKit** that turns this (previously tolerated) off-main
access into a fatal trap.

**Key insight:** macOS applies that assertion only to binaries built against a **recent SDK**
("linked on or after") — the gate reads the **SDK version stamp** of the **main executable**
(`otool -l <bin> | grep -A4 LC_BUILD_VERSION`). The same operation is tolerated for an old
stamp. The official release declares 11.0 (conda compilers default to it); a Homebrew/Apple-
clang build defaults to the current SDK (26.x) and traps.

**Fix (all at link time, in the build):** `configure.ac` defaults to
`-mmacosx-version-min=11.0` on macOS (sets `minos`; override with
`--with-min-macosx-version=…`), and the top `Makefile.am` adds
`-Wl,-platform_version,macos,11.0,11.0` to the two executables, which also pins the **SDK**
stamp (the linker accepts a duplicate `-platform_version`; the last one wins). No `vtool`
post-processing, no re-signing — the linker signs the final binary. Only the main executable's
stamp matters; dylibs don't need it.

Diagnosis tip: `JAVA_TOOL_OPTIONS=-Dnativewindow.debug=all` prints JOGL's Java stack and thread
up to the trap — but it also loads Xcode's Main Thread Checker, which makes **even a working
build** trap, so use it only for diagnosis.

---

## Appendix B — the macOS support, by file

| File | Fix |
|------|-----|
| `configure.ac` | macOS self-sufficiency: Homebrew + `../xlnt-prefix` search paths derived at configure time; `-mmacosx-version-min=11.0` default; rpaths for the `@rpath` OS/toolchain libs; OpenMP Darwin flags; `SHARED_LIB_EXT` evaluated from libtool 2.5.4's `$shrext_cmds` expression; LWJGL jar checks in dependency order; `XLNT_LIBDIR`/`MIN_MACOSX_VERSION` substitutions. |
| `Makefile.am` | Executables' SDK stamp (`-platform_version`); `macos-process-name` (menu-bar/Dock name). |
| `modules/spreadsheet/Makefile.am` | Per-target `-std=c++20` (Arrow 24); xlnt rpaths + adjacent dylib copy. |
| `modules/xcos/Makefile.am` | scicos deps → `@rpath` + rpaths for uninstalled/installed layouts. |
| `modules/helptools/Makefile.am` | Disable-stub source defined unconditionally (empty libs fail `ld` on macOS). |
| `etc/modules.xml.in` | helptools active regardless of `--disable-build-help`. |
| `modules/console/src/c/cmdLine/termcapManagement.c` | `(char *)` cast for macOS's non-const `tgetstr`. |
| `modules/jvm/src/java/org/scilab/modules/jvm/LibraryPath.java` | JDK-25 `java.library.path` patch via `Unsafe` instead of a `static final` reflective write (§6). |

> Helper scripts in the source root: **`fetch-thirdparty.sh`** (pinned third-party payload, §2),
> **`build-macos.sh`** (thin configure+make wrapper), and **`package-macos.sh`** (the relocatable
> app). The old `reapply-macos-fixes.sh` is gone — its every step moved into the build (see §4).
