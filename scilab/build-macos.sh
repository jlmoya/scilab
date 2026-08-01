#!/usr/bin/env bash
# Full from-source build of Scilab (branch 2027) on macOS arm64 with JDK 25.
#
# CMake (native + orchestration) + Maven (Java). autotools and Ant were retired
# 2026-07-21 (tag `autotools-ant-retired`): there is no ./configure, no Makefile.am,
# no build.xml. CMake computes what configure used to (machine.h, version.h, the
# compiler flags, the generated files) and drives Maven for the 24 module jars.
#
# No --with-<dep> flags are needed any more: CMake locates the Homebrew dependencies
# (openblas/arpack/fftw/hdf5/matio/suite-sparse/eigen/pcre/curl/libxml2) itself, along
# with the rpaths and the macOS deployment target. Fortran is the one thing worth
# pinning — a stray flang on PATH otherwise wins and the driver hard-fails.
# See docs/design/build-cmake-maven-migration.md and build-cmake-driver.md.
#
# Run from the source root:  cd scilab/scilab && ./build-macos.sh [--release|--debug]
#
# BUILD TYPE. Defaults to release. The underlying switch, SCILAB_ENABLE_DEBUG
# (cmake/ScilabFlags.cmake, configure's old --enable-debug), has always existed but was
# unreachable from here, so "how do I build a debug Scilab?" had no supported answer.
#
#   --release   -DNDEBUG -g1 -O2 -fwrapv   (default; -fwrapv is the UB-miscompile hardening)
#   --debug     -O0 -g3                    (usable stack traces; noticeably slower)
#
# Each type gets its OWN build directory, so switching back and forth does not force a
# full reconfigure+rebuild each time.
#
# Anything after `--` is passed straight to the cmake configure step, e.g.
#   ./build-macos.sh --debug -- -DSOME_OPTION=ON
set -e
cd "$(dirname "$0")"

BUILD_TYPE=release
EXTRA_CMAKE_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --release)  BUILD_TYPE=release ;;
        --debug)    BUILD_TYPE=debug ;;
        --)         shift; EXTRA_CMAKE_ARGS=("$@"); break ;;
        -h|--help)
            sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *)
            echo "build-macos.sh: unknown argument '$1'" >&2
            echo "usage: ./build-macos.sh [--release|--debug] [-- <extra cmake args>]" >&2
            exit 2 ;;
    esac
    shift
done

JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export JAVA_HOME="$JDK"          # the Maven reactor + the JNI probes read it
GFORTRAN=/opt/homebrew/bin/gfortran
JOBS="$(sysctl -n hw.ncpu)"

if [ "$BUILD_TYPE" = debug ]; then
    BUILD=build-cmake-debug
    ENABLE_DEBUG=ON
else
    # The release tree keeps the historic name: the parity harness, the drop-in targets
    # and every doc reference say build-cmake.
    BUILD=build-cmake
    ENABLE_DEBUG=OFF
fi

echo "[1/4] cmake configure ($BUILD_TYPE, JDK 25, gfortran) -> $BUILD/…"
cmake -S . -B "$BUILD" -DCMAKE_Fortran_COMPILER="$GFORTRAN" \
      -DSCILAB_ENABLE_DEBUG="$ENABLE_DEBUG" "${EXTRA_CMAKE_ARGS[@]}"

# drop-in-all = the 64 module dylibs + the 21 fold-in OBJECT libs + the two aggregates
# (libscilab / libscilab-cli) + both executables + sci-java-all (the 24 Maven module
# jars, built into modules/<m>/target/). Each artifact is copied into place, so the
# tree is runnable straight afterwards.
echo "[2/4] build the app — native + Maven jars (-j$JOBS)…"
cmake --build "$BUILD" --target drop-in-all -j"$JOBS"

# Stamp what now sits in modules/*/.libs/.
#
# Both build types drop into the SAME .libs/ layout, because that is where bin/scilab and
# package-macos.sh read their binaries from. So a debug build silently replaces the release
# one there, and nothing downstream could tell -- you could build debug, forget, package,
# and ship an unoptimised Scilab with no warning anywhere. The stamp is what lets
# package-macos.sh notice (it refuses a debug payload unless told otherwise).
#
# Written AFTER drop-in, so it describes what is actually in .libs/ rather than what was
# merely configured. Running `cmake --build <dir> --target drop-in-all` by hand bypasses
# this and leaves the stamp stale -- package-macos.sh treats a missing stamp as unknown
# and says so rather than assuming release.
printf '%s\n' "$BUILD_TYPE" > .scilab-build-type

# The macro library (.sci -> .bin) is a SEPARATE target, deliberately not part of
# drop-in-all: it runs the just-built scilab-cli-bin over the .sci sources, so it
# depends on the executable already existing (the bootstrap loop).
echo "[3/4] macros (.sci -> .bin)…"
cmake --build "$BUILD" --target macros -j"$JOBS"

# Help content (DocBook XML -> JavaHelp jars in modules/helptools/target/). Also
# circular — it drives the built scilab-adv-cli headless via xmltojar — and the slowest
# step. ScilabHelp.cmake supplies the JDK-25 -Djdk.xml.*Limit=0 lifts the DocBook
# pipeline needs; without them the parse dies on jdk.xml.totalEntitySizeLimit.
echo "[4/4] doc (help browser content, all locales)…"
cmake --build "$BUILD" --target doc

echo
echo "Build complete ($BUILD_TYPE, from $BUILD/)."
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab     # run the GUI"
if [ "$BUILD_TYPE" = debug ]; then
    echo "  Package:   ./package-macos.sh --allow-debug   # refuses a debug payload otherwise"
    echo
    echo "  NOTE: modules/*/.libs/ now holds DEBUG binaries (-O0 -g3). Re-run"
    echo "        ./build-macos.sh   to put the release build back."
else
    echo "  Package:   ./package-macos.sh              # build the /Applications app"
fi
