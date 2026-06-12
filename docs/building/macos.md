# Building & running Scilab from source on macOS (Apple Silicon / arm64)

The complete handbook for building and running **Scilab** (branch 2027.0) from source on
macOS arm64 (tested on macOS 26 "Tahoe", **JDK 25**). There is **no macOS CI**, so the dev
branch accumulates macOS-specific gaps. Some fixes are committed to the source; the rest are
applied by two helper scripts in the source root (`build-macos.sh`, `reapply-macos-fixes.sh`).

The official reference is the GitLab wiki
[setup Scilab repository macOS arm64](https://gitlab.com/scilab/scilab/-/wikis/Developers/setup-Scilab-repository-macOS-arm64)
(which uses **conda**). This guide uses **Homebrew**.

> - Build system: GNU **Autotools** (`./configure` → `make`), **not** CMake.
> - Java: **JDK 25** (the upgrade from the branch's JDK 17 is validated — see §7). `ant` via sdkman/Homebrew.
> - Source root is the inner `scilab/scilab/` directory.

---

## TL;DR — the whole build in three steps

```sh
cd scilab/scilab
./build-macos.sh            # configure + macOS build-time Makefile fixes + make   (~45 min)
./reapply-macos-fixes.sh    # runtime fixes (deployment target, @loader_path, helptools, macros, menu name)
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./bin/scilab   # run the GUI
```

Both scripts are **idempotent**. After any `make`/relink, re-run `./reapply-macos-fixes.sh`
(a relink drops the deployment-target/`@loader_path`/menu-name tweaks **and** corrupts the
macros — see §6). The one-time prerequisites (§1–§2) must already be in place.

---

## 0. Why it's more than `./configure && make`

The committed `configure.ac`/`Makefile.am` files contain the macOS fixes, **but the committed
*generated* files (`configure`, the `Makefile.in`s) are stale** — they predate those fixes and
were never regenerated. So a plain `./configure` produces Makefiles **without** the OpenMP,
helptools, and related fixes, and the build fails. Two ways out:

- **`build-macos.sh` (recommended):** keeps the committed (working) build system — including
  **libtool 2.4.7** — and re-applies the fixes as targeted Makefile patches after `configure`.
- **`autoreconf -fi`:** regenerates everything from `configure.ac`/`Makefile.am`, making the
  fixes "real". **Caveat:** it pulls in your system's **libtool 2.5.4**, whose shared-extension
  handling leaks an unevaluated `` `…echo .so || echo .dylib` `` into the dev-tree `dlopen`
  path and breaks macro generation. **Don't autoreconf** unless you also pin libtool 2.4.7.

---

## 1. Toolchain & native dependencies (Homebrew)

```sh
brew install gcc            # provides gfortran
brew install autoconf automake libtool pkg-config
brew install openblas arpack fftw hdf5 pcre2 suite-sparse eigen
brew install libmatio apache-arrow libomp libarchive fast_float
```

Notes:
- `apache-arrow` provides the `arrow`/`parquet` pkg-config packages. The dev branch pins Arrow
  **19**; Homebrew ships **24**, whose headers need **C++20** — handled by building only the
  `spreadsheet` module at `-std=c++20` (see §4). The global standard stays **C++17** (bumping
  it globally breaks C++17-only code in `ast`/`sparse`).
- `libomp`, `libarchive` are **keg-only** — their `include`/`lib` go into `CPPFLAGS`/`LDFLAGS`
  (handled inside `build-macos.sh`).
- `fast_float` is a new, undeclared `scicos` dependency (header-only).

### xlnt 1.6.1 (no Homebrew formula)

xlnt has no formula. Build a small pkg-config prefix at the repo root (`../xlnt-prefix`
relative to the source root), pairing the headers with a prebuilt dylib:

```sh
PREFIX="$PWD/../xlnt-prefix"; mkdir -p "$PREFIX"/{include,lib/pkgconfig}
curl -LO https://oos.eu-west-2.outscale.com/scilab-releases-dev/prerequirements-sources/xlnt-1.6.1_with_submodules.tar.gz
tar -xzf xlnt-1.6.1_with_submodules.tar.gz
cp -R xlnt-1.6.1/include/xlnt "$PREFIX/include/"
# The CMake-generated export header is not in the source tarball; recreate it:
cat > "$PREFIX/include/xlnt/utils/xlnt_cmake_export.h" <<'EOF'
#ifndef XLNT_API_H
#define XLNT_API_H
#ifndef XLNT_API
#  define XLNT_API __attribute__((visibility("default")))
#endif
#ifndef XLNT_NO_EXPORT
#  define XLNT_NO_EXPORT __attribute__((visibility("hidden")))
#endif
#ifndef XLNT_DEPRECATED
#  define XLNT_DEPRECATED __attribute__ ((__deprecated__))
#endif
#endif /* XLNT_API_H */
EOF
cp /Applications/scilab-2026.1.0.app/Contents/lib/thirdparty/libxlnt.1.6.1.dylib "$PREFIX/lib/"
ln -sf libxlnt.1.6.1.dylib "$PREFIX/lib/libxlnt.dylib"
cat > "$PREFIX/lib/pkgconfig/xlnt.pc" <<EOF
prefix=$PREFIX
Name: xlnt
Version: 1.6.1
Cflags: -I\${prefix}/include
Libs: -L\${prefix}/lib -lxlnt
EOF
```

---

## 2. Java / JOGL / JavaFX prerequisites

The GUI needs legacy jars + JOGL native dylibs (incl. **`libgluegen_rt.dylib`**, loaded via
`java.library.path` — see §7). Download the official macOS bundle and add JavaFX 17.0.8:

```sh
cd scilab/scilab     # the source root
curl -LO https://oos.eu-west-2.outscale.com/scilab-releases-dev/prerequirements/prerequirements-scilab-branch-main-macosx.tar.xz
tar -xJf prerequirements-scilab-branch-main-macosx.tar.xz      # -> thirdparty/ and lib/thirdparty/

curl -LO https://download2.gluonhq.com/openjfx/17.0.8/openjfx-17.0.8_osx-aarch64_bin-sdk.zip
unzip -q openjfx-17.0.8_osx-aarch64_bin-sdk.zip
cp javafx-sdk-17.0.8/lib/*.dylib lib/thirdparty/
cp javafx-sdk-17.0.8/lib/javafx.{base,graphics,swing}.jar thirdparty/
```

The configure check `lucene-analyzers-common` looks for `StandardAnalyzer`, which moved to
`lucene-core` in Lucene 9. Provide a name alias:

```sh
ln -sf lucene-core-9.10.0.jar thirdparty/lucene-analyzers-common-9.10.0.jar
```

> `make clean` does **not** remove `lib/thirdparty/`, so these survive rebuilds.

---

## 3. The build — `build-macos.sh`

`build-macos.sh` (in the source root) does **configure → build-time Makefile patches → make**.
It pins JDK 25, sets the keg-only `CPPFLAGS`/`LDFLAGS` and `PKG_CONFIG_PATH` (including
`../xlnt-prefix`), runs the full `./configure` (with `--without-tk --without-modelica
--disable-build-help --disable-ccache`), applies the patches in §4, then `make -j`.

```sh
cd scilab/scilab
./build-macos.sh
```

A full build is ~45 min. Do **not** pass `--enable-stop-on-warning` (macOS emits warnings the
build would otherwise reject); `--without-tk` is mandatory on macOS.

---

## 4. Build-time Makefile fixes (what `build-macos.sh` patches — and why)

Because the generated Makefiles are stale (§0), `build-macos.sh` patches them after
`configure`:

| Fix | What & why |
|-----|------------|
| **OpenMP** | `OPENMP_CFLAGS`/`CXXFLAGS = -fopenmp` and `OPENMP_LIBS = -lgomp` → **`-Xpreprocessor -fopenmp`** / **`-lomp`** in every Makefile. Apple clang rejects a bare `-fopenmp`. (`configure.ac` has the correct Darwin branch, but the stale generated `configure` doesn't apply it.) Patch byte-safely with `LC_ALL=C` — some Makefiles contain non-UTF-8 bytes that abort macOS `sed`. |
| **helptools stub** | The `libscihelptools-disable` stub lib is gated on `@BUILD_HELP_TRUE@`, so under `--disable-build-help` it has **no sources/objects** → `ld: no object files specified`. Uncomment `am__objects_2` and `HELPTOOLS_DISABLE_CPP_SOURCES` in `modules/helptools/Makefile`. |
| **spreadsheet C++20** | Apache Arrow 24 headers (`std::span`/`popcount`/`bit_width`) need **C++20**. Bump only `modules/spreadsheet/Makefile` from `-std=c++17` to `-std=c++20`. (Doing this globally breaks C++17-only code in `ast`/`sparse` — keep it module-local.) |

These are the exact `sed`s inside `build-macos.sh`; read it for the literal commands.

---

## 5. Runtime fixes — `reapply-macos-fixes.sh`

Several libraries reference others by an install path (`/usr/local/lib/scilab/…` or a bare
name) absent in the dev tree. With **SIP enabled**, the launcher's `DYLD_LIBRARY_PATH` is
stripped (because `/bin/sh` is restricted), so these must self-resolve via `@loader_path`.
After any `install_name_tool`/`vtool`, **re-sign ad-hoc** (`codesign -f -s -`) or macOS arm64
kills the dylib. `reapply-macos-fixes.sh` automates all of it (idempotent):

1. **Deployment target → macOS 11.0** on `scilab-bin`/`scilab-cli-bin` (`vtool`) — the GUI/plot fix (see appendix).
2. **xlnt → `@loader_path`** for `libscispreadsheet`.
3. **xcos → scicos `@loader_path`**.
4. **Activate helptools** in `etc/modules.xml` (for the Help window).
5. **Build all macros** (see §6).
6. **Menu-bar / Dock name → `Scilab-2027.0.0`** (see §9).

Run it after **every** `make`/relink:

```sh
./reapply-macos-fixes.sh
```

(Disabling SIP — `csrutil disable` from Recovery — avoids the `DYLD_*`-stripping class of
problems entirely, and is what the official guide recommends.)

---

## 6. Building the macros (and why ordering matters)

A large part of Scilab's library is `.sci` macros, "compiled" by `scilab-cli` via `genlib`
into per-module `macros/lib` index files. The macro build runs during `make` — **but too
early**: before `reapply-macos-fixes.sh` heals `scilab-cli`'s runtime, `scilab-cli` crashes
mid-build and leaves a **0-byte `modules/core/macros/lib`**, which then breaks *every* later
startup (`load: … is not a valid lib file`). `make` marks this `(ignored)`, so the build still
exits 0 — easy to miss.

**Therefore macros must be (re)built *after* the runtime fixes.** `reapply-macos-fixes.sh`
step [5/6] does this; to do it by hand:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./bin/scilab-cli -ns -noatomsautoload -nouserstartup -quit \
  -f modules/functions/scripts/buildmacros/buildmacros.sce
```

`-ns` (no startup) is essential — it stops `scilab-cli` from trying to *load* the macros it's
about to *build*. A healthy run produces ~47 `macros/lib` files. For a **single** module from
inside Scilab: `genlib("foolib", SCI+"/modules/foo/macros", %t)`.

---

## 7. JDK 25 notes

The branch targets JDK 17; building/running on **JDK 25** is validated (compiles + links, CLI,
and GUI with graphics all work). Two JDK-25 specifics:

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

To switch the build to 25: it's already the default in `build-macos.sh`/`reapply-macos-fixes.sh`
(and the `.app` launcher). For a different JDK, edit the `JDK`/`JAVA_HOME` in those three files.
**The terminal/GUI runs in Scilab's JVM, so the JDK is process-wide — there's no per-component
JDK.**

---

## 8. Running

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./bin/scilab          # GUI console (also prints console output to the terminal)
./bin/scilab-cli      # pure command-line, no JVM (interpreter only)
./bin/scilab-adv-cli  # command-line + JVM
```

Never run `.libs/scilab-bin` directly — that raw binary resolves dylibs to the uninstalled
`/usr/local/lib/scilab`. Always use the `bin/` launchers (or the libtool wrapper `./scilab-bin`).
A healthy GUI process has ~47 threads (JVM + AWT EDT + Swing). For a **no-Terminal** Finder
launch, double-click `Scilab-2027.0.0.app` (§9).

Quick non-GUI sanity check:
```sh
./bin/scilab-cli -nb -e "disp(6*7); disp(getversion()); exit"   # -> 42, scilab-branch-2027.0
```

---

## 9. Launch from Finder with no Terminal — the `.app` bundle and menu-bar name

Two visible-but-cosmetic issues once the GUI runs:

- Double-clicking any `bin/` launcher (or a `.command`) **opens a Terminal window**. Only an
  **application bundle** (`.app`) launches with no terminal.
- The macOS menu bar (next to the  logo) and the Dock read the raw process name `scilab-bin`.

**The menu-bar title is the running process's executable _filename_** — not `argv[0]`, and not
the `apple.awt.application.name` JVM property (that property *is* honored for the **About/Quit**
items, but macOS 26 / the JVM ignore it for the bold title). So we make the GUI process's
executable a file literally named `Scilab-2027.0.0`. The exec chain is `bin/scilab` → libtool
wrapper `./scilab-bin` → `.libs/scilab-bin`; `reapply-macos-fixes.sh` step [6/6] adds the
versioned name at the tail:

```sh
NAME="Scilab-2027.0.0"                               # = SCI_VERSION (getversion("scilab"))
ln -f .libs/scilab-bin ".libs/$NAME" || { cp -f .libs/scilab-bin ".libs/$NAME"; codesign -f -s - ".libs/$NAME"; }
sed -i '' "s/program='scilab-bin'/program='$NAME'/" scilab-bin   # the libtool wrapper (regenerated on relink)
```

The `.app` provides the no-Terminal launch (a `.command` always spawns a terminal; only a
bundle does not):

```text
Scilab-2027.0.0.app/Contents/
├── Info.plist                 # CFBundleName/CFBundleExecutable = Scilab-2027.0.0,
│                              #   CFBundleIconFile = scilab, CFBundleShortVersionString = 2027.0.0,
│                              #   LSMinimumSystemVersion = 11.0
├── MacOS/Scilab-2027.0.0      # tiny launcher: sets JAVA_HOME (jdk-25), cd's to the build, exec ./bin/scilab
└── Resources/scilab.icns      # copied from /Applications/scilab-2026.1.0.app
```

Sign it (`codesign -f -s - Scilab-2027.0.0.app`). Result:

| Launch | Terminal window | Menu bar / Dock |
|--------|-----------------|-----------------|
| Double-click `Scilab-2027.0.0.app` (Finder / Dock / Spotlight) | **none** | **Scilab-2027.0.0** + Scilab icon |
| `./bin/scilab` from a terminal | shows console output | **Scilab-2027.0.0** |

The bundle hard-codes the build path (machine-specific), so it is **not** committed — recreate
it from these steps. Steps [6/6] above are re-applied by `reapply-macos-fixes.sh`.

---

## 10. Troubleshooting (the failures this branch actually hits)

| Symptom | Cause → fix |
|---------|-------------|
| `clang: error: unsupported option '-fopenmp'` | Stale OpenMP flag → §4 (re-run `build-macos.sh`; it patches `OPENMP_*`). |
| `sed: RE error: illegal byte sequence` while patching | macOS `sed` + non-UTF-8 Makefile → prefix with `LC_ALL=C`. |
| `error: no template named 'span' …apache-arrow…` | Arrow 24 needs C++20 → §4 (spreadsheet `-std=c++20`). |
| `no matching function for call to 'cwiseOp'` in `ast/sparse` | C++20 applied **globally** → keep it module-local to spreadsheet. |
| `ld: no object files specified` for `libscihelptools-disable` | helptools stub empty → §4. |
| `make[1]: [macros] Error 1 (ignored)` / `load: …/macros/lib is not a valid lib file` / 0-byte `core/macros/lib` | Macros built before runtime fixes → §6 (rebuild after `reapply`). |
| GUI: `UnsupportedOperationException: set` at `LibraryPath.addPath` + `UnsatisfiedLinkError: no gluegen_rt` | JDK-25 final-field write blocked → §7 (the committed `Unsafe` fix; rebuild `modules/jvm`). |
| GUI traps `SIGTRAP`/`EXC_BREAKPOINT` at startup or first `plot()` | Deployment target too new → run `reapply-macos-fixes.sh` (sets minos 11.0); see appendix. |
| `Library not loaded: /usr/local/lib/scilab/…` or a bare-name dylib | `@loader_path` not applied → run `reapply-macos-fixes.sh`. |
| ``dlopen(…libsci…`test .$module = .yes && echo .so || echo .dylib`)`` | You ran `autoreconf` and got libtool 2.5.4 → §0 (revert to committed build system / libtool 2.4.7). |

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
("linked on or after"). The same operation is **tolerated** for an old deployment target:

```sh
otool -l <scilab-bin> | grep -A2 LC_BUILD_VERSION   # minos
```

The official release / `.app` declares **`minos 11.0`** (conda compilers default to it), so
graphics work; a Homebrew/Apple-clang build defaults to the **current SDK (26.x)** and traps.

**Fix:** `configure.ac` defaults to `-mmacosx-version-min=11.0` on macOS (override with
`--with-min-macosx-version=…`). **No Scilab source change is needed** — only the SDK gate
matters. For an already-built tree, `reapply-macos-fixes.sh` rewrites it in place:

```sh
for b in scilab-bin scilab-cli-bin; do
  vtool -set-build-version macos 11.0 11.0 -replace -output ".libs/$b.p" ".libs/$b" && mv ".libs/$b.p" ".libs/$b"
  chmod +x ".libs/$b"; codesign -f -s - ".libs/$b"
done
```

(The assertion is gated on the **main executable's** deployment target, so patching
`scilab-bin` is sufficient; dylibs don't need it.) Diagnosis tip:
`JAVA_TOOL_OPTIONS=-Dnativewindow.debug=all` prints JOGL's Java stack and thread up to the trap
— but it also loads Xcode's Main Thread Checker, which makes **even a working build** trap, so
use it only for diagnosis.

---

## Appendix B — committed source fixes

These are committed and survive across machines (only the *generated* files are stale, §0):

| File | Fix |
|------|-----|
| `configure.ac` | Default `-mmacosx-version-min=11.0` on macOS (the GUI/graphics fix, Appendix A). |
| `configure.ac` | OpenMP Darwin branch: `-Xpreprocessor -fopenmp` + `-lomp`. |
| `modules/console/src/c/cmdLine/termcapManagement.c` | `(char *)` cast for macOS's non-const `tgetstr`. |
| `modules/helptools/Makefile.am` | `HELPTOOLS_DISABLE_CPP_SOURCES` unconditional (empty libs fail `ld` on macOS). |
| `modules/jvm/src/java/org/scilab/modules/jvm/LibraryPath.java` | JDK-25 `java.library.path` patch via `Unsafe` instead of a `static final` reflective write (§7). |

> Helper scripts in the source root: **`build-macos.sh`** (build) and **`reapply-macos-fixes.sh`**
> (runtime fixes + macros). The `Scilab-2027.0.0.app` bundle is machine-specific and untracked.
