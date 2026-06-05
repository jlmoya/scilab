# Building Scilab from source on macOS (Apple Silicon / arm64)

This documents how to build and run **Scilab** (branch 2027.0) from source on macOS
arm64 (tested on macOS 26 "Tahoe"). There is currently **no macOS CI**, so the dev
branch accumulates macOS-specific gaps; the fixes below are folded into the source
where possible, with the rest applied as build-tree / runtime steps.

The official reference is the GitLab wiki
[setup Scilab repository macOS arm64](https://gitlab.com/scilab/scilab/-/wikis/Developers/setup-Scilab-repository-macOS-arm64),
which uses **conda** for dependencies. This guide uses **Homebrew**, which also works.

> Build system: GNU Autotools (`./configure` → `make`), **not** CMake.
> Java: **JDK 17** (the dev branch targets 17). ant via sdkman or Homebrew.

---

## 1. Toolchain & native dependencies (Homebrew)

```sh
brew install gcc            # provides gfortran
brew install autoconf automake libtool pkg-config
brew install openblas arpack fftw hdf5 pcre2 suite-sparse eigen
brew install libmatio apache-arrow libomp libarchive fast_float
```

Notes:
- `apache-arrow` provides both `arrow` and `parquet` pkg-config packages. The dev
  branch pins Arrow **19**; Homebrew ships **24**, whose headers require **C++20**
  (the `spreadsheet` module is built with `-std=c++20` to accommodate this).
- `libomp`, `libarchive` are **keg-only** — add their `include`/`lib` to
  `CPPFLAGS`/`LDFLAGS` (see §4).
- `fast_float` is a new, undeclared `scicos` dependency (header-only).

### xlnt 1.6.1 (no Homebrew formula)

xlnt has no formula. Build a small pkg-config prefix from Scilab's own source mirror,
pairing the headers with a prebuilt dylib (e.g. from `/Applications/scilab-2026.1.0.app`):

```sh
PREFIX="$PWD/xlnt-prefix"; mkdir -p "$PREFIX"/{include,lib/pkgconfig}
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

## 2. Java / JOGL prerequisites

The GUI needs legacy jars + JOGL native dylibs. Download the official macOS bundle
(matches branch `main`) and add JavaFX 17.0.8:

```sh
cd scilab/scilab     # the source root
curl -LO https://oos.eu-west-2.outscale.com/scilab-releases-dev/prerequirements/prerequirements-scilab-branch-main-macosx.tar.xz
tar -xJf prerequirements-scilab-branch-main-macosx.tar.xz      # -> thirdparty/ and lib/thirdparty/

curl -LO https://download2.gluonhq.com/openjfx/17.0.8/openjfx-17.0.8_osx-aarch64_bin-sdk.zip
unzip -q openjfx-17.0.8_osx-aarch64_bin-sdk.zip
cp javafx-sdk-17.0.8/lib/*.dylib lib/thirdparty/
cp javafx-sdk-17.0.8/lib/javafx.{base,graphics,swing}.jar thirdparty/
```

The configure check `lucene-analyzers-common` looks for `StandardAnalyzer`, which moved
to `lucene-core` in Lucene 9. Provide a name alias:

```sh
ln -sf lucene-core-9.10.0.jar thirdparty/lucene-analyzers-common-9.10.0.jar
```

---

## 3. Source fixes (included in this branch)

These are committed source changes that make the macOS build/run work:

| File | Fix |
|------|-----|
| `configure.ac` | **Deployment target**: default `-mmacosx-version-min=11.0` on macOS so binaries get the tolerant legacy AppKit main-thread behavior — **this is what makes the GUI/graphics work** (see below). |
| `configure.ac` | OpenMP flags: Apple clang needs `-Xpreprocessor -fopenmp` + `-lomp` (not `-fopenmp`/`-lgomp`). |
| `modules/console/src/c/cmdLine/termcapManagement.c` | `(char *)` cast for macOS's non-const `tgetstr`. |
| `modules/helptools/Makefile.am` | Define `HELPTOOLS_DISABLE_CPP_SOURCES` unconditionally so the disable-stub lib isn't empty (empty libs fail `ld` on macOS). |

---

## 4. Configure & build

```sh
cd scilab/scilab
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PKG_CONFIG_PATH="$PWD/../xlnt-prefix/lib/pkgconfig:$(brew --prefix)/opt/pcre2/lib/pkgconfig:$(brew --prefix)/opt/openblas/lib/pkgconfig:$(brew --prefix)/lib/pkgconfig"
export CPPFLAGS="-I$(brew --prefix)/include -I$(brew --prefix)/opt/libomp/include -I$(brew --prefix)/opt/libarchive/include"
export LDFLAGS="-L$(brew --prefix)/lib -L$(brew --prefix)/opt/libomp/lib -L$(brew --prefix)/opt/libarchive/lib"

./configure \
  --with-jdk="$JAVA_HOME" --with-ant=<ant-home> \
  --without-tk --without-modelica --disable-build-help --disable-ccache \
  --with-blas-library="$(brew --prefix)/opt/openblas/lib" \
  --with-lapack-library="$(brew --prefix)/opt/openblas/lib" \
  --with-arpack-library="$(brew --prefix)/opt/arpack/lib" \
  --with-fftw-include="$(brew --prefix)/opt/fftw/include" --with-fftw-library="$(brew --prefix)/opt/fftw/lib" \
  --with-hdf5-include="$(brew --prefix)/opt/hdf5/include" --with-hdf5-library="$(brew --prefix)/opt/hdf5/lib" \
  --with-matio-include="$(brew --prefix)/opt/libmatio/include" --with-matio-library="$(brew --prefix)/opt/libmatio/lib" \
  --with-umfpack-include="$(brew --prefix)/opt/suite-sparse/include/suitesparse" --with-umfpack-library="$(brew --prefix)/opt/suite-sparse/lib" \
  --with-eigen-include="$(brew --prefix)/opt/eigen/include/eigen3"

make -j"$(sysctl -n hw.ncpu)"
```

Do **not** use `--enable-stop-on-warning` (macOS produces warnings the build would
otherwise reject). `--without-tk` is mandatory on macOS.

---

## 5. Runtime dylib fixes

A few libraries reference others by an install path (`/usr/local/lib/scilab/…` or a bare
name) that isn't present in the dev tree. With **SIP enabled**, `DYLD_LIBRARY_PATH` set by
the launcher is stripped (because `/bin/sh` is a restricted binary), so these must be made
self-resolving. After `install_name_tool`, **re-sign ad-hoc** (`codesign -f -s -`) or macOS
arm64 will kill the dylib.

```sh
# xlnt (referenced by libscispreadsheet, bare name)
cp xlnt-prefix/lib/libxlnt.1.6.1.dylib modules/spreadsheet/.libs/
install_name_tool -change libxlnt.1.6.1.dylib @loader_path/libxlnt.1.6.1.dylib \
  modules/spreadsheet/.libs/libscispreadsheet.2027.dylib
codesign -f -s - modules/spreadsheet/.libs/libscispreadsheet.2027.dylib

# xcos -> scicos (referenced by /usr/local/lib/scilab path)
install_name_tool \
  -change /usr/local/lib/scilab/libsciscicos.2027.dylib @loader_path/../../scicos/.libs/libsciscicos.2027.dylib \
  -change /usr/local/lib/scilab/libsciscicos_blocks.2027.dylib @loader_path/../../scicos_blocks/.libs/libsciscicos_blocks.2027.dylib \
  modules/xcos/.libs/libscixcos.2027.dylib
codesign -f -s - modules/xcos/.libs/libscixcos.2027.dylib
```

(Disabling SIP — `csrutil disable` from Recovery — avoids the `DYLD_*`-stripping class
of problems entirely and is what the official guide recommends.)

---

## 6. Run

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./bin/scilab          # GUI console
./bin/scilab-cli      # pure command-line (no Java)
./bin/scilab-adv-cli  # command-line + JVM
```

Never run `.libs/scilab-bin` directly — that is the raw binary whose dylibs resolve to the
uninstalled `/usr/local/lib/scilab`. Always use the `bin/` launchers (or the libtool wrapper
`./scilab-bin`). A healthy GUI process has ~47 threads (JVM + AWT EDT + Swing).

---

## The GUI / plotting crash on macOS 14+/26 — and the real fix

**Symptom:** the GUI traps (`SIGTRAP` / `EXC_BREAKPOINT`) at startup, or when `plot()` first
renders. The faulting stack is JOGL realizing an onscreen `NSWindow`/CGL drawable
(`OSXUtil_*NSWindow*` → AppKit) off the Cocoa main thread.

**Why:** Scilab runs its Cocoa runloop on the OS main thread (thread 0) and the
JVM/interpreter on a **secondary** pthread; JOGL initializes graphics on that secondary
thread. **macOS 14+/26 added a hard main-thread assertion for AppKit** that turns this
(previously tolerated) off-main access into a fatal trap.

**The key insight:** macOS applies that strict assertion only to binaries built against a
**recent SDK** ("linked on or after"). The same off-main operation is **tolerated** for
binaries with an old deployment target. Compare `scilab-bin`:

```sh
otool -l <scilab-bin> | grep -A2 LC_BUILD_VERSION   # minos
```

The official release / `*.app` declares **`minos 11.0`** (the conda compilers default to it),
so macOS uses the legacy tolerant behavior and graphics work. A Homebrew/Apple-clang build
defaults to the **current SDK (26.x)** and therefore traps.

**Fix:** build with an old deployment target. `configure.ac` now defaults to
`-mmacosx-version-min=11.0` on macOS, so a fresh build is correct automatically (override
with `--with-min-macosx-version=...`). **No Scilab source change is needed** — the
interpreter/JOGL logic is fine; only the SDK gate matters.

**For an already-built tree** (to avoid a full rebuild), rewrite the launcher binaries'
deployment target in place and re-sign:

```sh
cd modules/../.libs   # the build root .libs/
for b in scilab-bin scilab-cli-bin; do
  vtool -set-build-version macos 11.0 11.0 -replace -output "$b.p" "$b" && mv "$b.p" "$b"
  chmod +x "$b"; codesign -f -s - "$b"
done
```

(The process-wide assertion is gated on the **main executable's** deployment target, so
patching `scilab-bin` is sufficient; the dylibs don't need it.)

Diagnosis tip: `JAVA_TOOL_OPTIONS=-Dnativewindow.debug=all` prints JOGL's Java stack and the
thread it runs on up to the trap — but note it also loads Xcode's Main Thread Checker, which
will make **even a working build** trap, so use it only for diagnosis.
