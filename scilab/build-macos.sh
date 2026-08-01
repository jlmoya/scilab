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
# Run from the source root:  cd scilab/scilab && ./build-macos.sh [OPTIONS]
#
#   --release      -DNDEBUG -g1 -O2 -fwrapv   (default; -fwrapv is the UB-miscompile hardening)
#   --debug        -O0 -g3                    (usable stack traces; noticeably slower)
#   --both         build BOTH, debug first so the tree is left release-ready
#   --clean        delete the CMake build trees and exit (does not build)
#   -- <args>      everything after `--` goes to the cmake configure step
#
# BUILD TYPE. The underlying switch, SCILAB_ENABLE_DEBUG (cmake/ScilabFlags.cmake,
# configure's old --enable-debug), has always existed but was unreachable from here, so
# "how do I build a debug Scilab?" had no supported answer. Each type gets its OWN build
# directory, so switching back and forth does not force a full reconfigure+rebuild.
set -e
cd "$(dirname "$0")"

usage() {
cat <<'HELPDOC'
build-macos.sh — full from-source build of Scilab 2027 on macOS arm64 (JDK 25).

USAGE
  ./build-macos.sh [--release | --debug | --both] [--clean] [-- <cmake args>]
  ./build-macos.sh --help

BUILD TYPE  (default: --release)
  --release   -DNDEBUG -g1 -O2 -fwrapv     the shipping build.
              -fwrapv is the UB-miscompile hardening: several legacy C/Fortran
              routines rely on wrapping signed overflow and are miscompiled at
              -O2 without it.
  --debug     -O0 -g3                      unoptimised, full debug info.
              Use for readable stack traces and stepping. -fwrapv is absent by
              design here: that class of UB is benign at -O0.
  --both      builds BOTH, debug first and release last.
              The order matters. Both types drop into the same
              modules/*/.libs/, so whichever runs last is what the tree ends up
              holding. Finishing on release leaves it packageable.

  Each type has its own build directory, so alternating between them does not
  force a full reconfigure and recompile:
      --release  ->  build-cmake/          (historic name; the parity harness,
                                            the drop-in targets and the design
                                            docs all refer to it)
      --debug    ->  build-cmake-debug/

OTHER OPTIONS
  --clean     delete both CMake build trees and the build-type stamp, then EXIT
              without building. Deliberately keeps modules/*/.libs/,
              modules/*/target/ (the 24 module jars AND the help jars) and the
              compiled macro .bin files: the doc step that produces the help
              jars is the slowest part of the build, and discarding it on a
              routine clean costs a lot for no benefit. Removing the build trees
              already forces a full native reconfigure and recompile.
  --          everything after this is passed verbatim to the cmake configure
              step, e.g.   ./build-macos.sh --debug -- -DSOME_OPTION=ON
  -h, --help  this text.

WHAT THE BUILD DOES  (four steps, same for either type)
  1. cmake configure       locates the Homebrew deps, computes what ./configure
                           used to (machine.h, version.h, the compiler flags).
  2. drop-in-all           64 module dylibs + 21 fold-in OBJECT libs + the two
                           aggregates + both executables + the 24 Maven jars,
                           each copied into place so the tree is runnable.
  3. macros                .sci -> .bin. Separate from step 2 because it runs
                           the just-built scilab-cli-bin (a bootstrap loop).
  4. doc                   DocBook -> JavaHelp jars. Also circular, and by far
                           the slowest step.

BUILD TYPE AND PACKAGING
  Both types drop into the SAME modules/*/.libs/, which is where bin/scilab and
  package-macos.sh read their binaries. A debug build therefore replaces the
  release one silently. To stop an unoptimised Scilab being shipped by accident,
  this script writes .scilab-build-type after step 2, and package-macos.sh
  refuses a debug payload unless given --allow-debug. A missing stamp is
  reported as "unknown" rather than assumed to be release.

AFTERWARDS
  Run:      JAVA_HOME=<jdk25> ./bin/scilab
  Package:  ./package-macos.sh                 (release)
            ./package-macos.sh --allow-debug   (debug, deliberately)

EXAMPLES
  ./build-macos.sh                     release build
  ./build-macos.sh --debug             debug build, for stack traces
  ./build-macos.sh --both              both; tree left release-ready
  ./build-macos.sh --clean             wipe the build trees, build nothing
  ./build-macos.sh --clean && ./build-macos.sh --both     clean, then rebuild both

  Requires: CMake, JDK 25, gfortran (a stray flang on PATH otherwise wins and
  the driver hard-fails), and the Homebrew dependency set. See
  docs/design/build-cmake-maven-migration.md and build-cmake-driver.md.
HELPDOC
}

BUILD_TYPES=(release)
DO_CLEAN=0
EXTRA_CMAKE_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --release)  BUILD_TYPES=(release) ;;
        --debug)    BUILD_TYPES=(debug) ;;
        # Debug FIRST, release LAST, and the order is the whole point: both types drop
        # into the same modules/*/.libs/, so whichever runs last is what the tree is left
        # holding. Ending on release leaves it packageable; ending on debug would leave a
        # tree that package-macos.sh refuses.
        --both)     BUILD_TYPES=(debug release) ;;
        --clean)    DO_CLEAN=1 ;;
        --)         shift; EXTRA_CMAKE_ARGS=("$@"); break ;;
        -h|--help)  usage; exit 0 ;;
        *)
            echo "build-macos.sh: unknown argument '$1'" >&2
            echo >&2
            usage >&2
            exit 2 ;;
    esac
    shift
done

JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export JAVA_HOME="$JDK"          # the Maven reactor + the JNI probes read it
GFORTRAN=/opt/homebrew/bin/gfortran
JOBS="$(sysctl -n hw.ncpu)"

# The release tree keeps the historic name: the parity harness, the drop-in targets and
# every doc reference say build-cmake.
build_dir_for() { [ "$1" = debug ] && echo build-cmake-debug || echo build-cmake; }

# ---- --clean ---------------------------------------------------------------------
# Removes the CMake build trees and the build-type stamp, then exits WITHOUT building:
# "clean" and "build" are separate intentions, and folding them together makes a bare
# --clean ambiguous about what it would go on to build.
#
# What it deliberately does NOT remove: the artefacts already dropped into
# modules/*/.libs/, modules/*/target/ (the 24 module jars AND the help jars) and the
# compiled macro .bin files. Those are gitignored build output too, but the doc target
# that produces the help jars is by far the slowest step in the whole build, and blowing
# it away on a routine "clean" would turn a 30-minute rebuild into a much longer one for
# no benefit -- deleting the build trees already forces a full native reconfigure and
# recompile, which is what a clean is normally wanted for.
#
# CAVEAT worth knowing: because .libs/ survives, a tree cleaned and then only partially
# rebuilt still LOOKS runnable -- bin/scilab will happily load the previous build's
# binaries. The stamp is removed for exactly that reason, so package-macos.sh reports
# the build type as unknown rather than vouching for stale artefacts.
if [ "$DO_CLEAN" -eq 1 ]; then
    echo "Cleaning build trees…"
    removed=0
    for d in build-cmake build-cmake-debug; do
        if [ -e "$d" ]; then
            echo "  removing $d/ ($(du -sh "$d" 2>/dev/null | cut -f1))"
            rm -rf "${d:?}"
            removed=1
        fi
    done
    if [ -e .scilab-build-type ]; then
        echo "  removing .scilab-build-type"
        rm -f .scilab-build-type
        removed=1
    fi
    [ "$removed" -eq 0 ] && echo "  nothing to remove — already clean"
    echo
    echo "Kept (regenerating these is far slower than recompiling):"
    echo "  modules/*/.libs/     dropped-in binaries from the previous build"
    echo "  modules/*/target/    the 24 module jars + the help jars (the doc step)"
    echo "  modules/*/macros/    compiled .bin macro libraries"
    echo
    echo "Now run ./build-macos.sh [--release|--debug|--both] for a full rebuild."
    exit 0
fi

build_one() {
    local build_type="$1"
    local build enable_debug
    build="$(build_dir_for "$build_type")"
    [ "$build_type" = debug ] && enable_debug=ON || enable_debug=OFF

    echo
    echo "=================================================================="
    echo " Building Scilab — $build_type  ($build/)"
    echo "=================================================================="

    echo "[1/4] cmake configure ($build_type, JDK 25, gfortran) -> $build/…"
    cmake -S . -B "$build" -DCMAKE_Fortran_COMPILER="$GFORTRAN" \
          -DSCILAB_ENABLE_DEBUG="$enable_debug" "${EXTRA_CMAKE_ARGS[@]}"

    # drop-in-all = the 64 module dylibs + the 21 fold-in OBJECT libs + the two aggregates
    # (libscilab / libscilab-cli) + both executables + sci-java-all (the 24 Maven module
    # jars, built into modules/<m>/target/). Each artifact is copied into place, so the
    # tree is runnable straight afterwards.
    echo "[2/4] build the app — native + Maven jars (-j$JOBS)…"
    cmake --build "$build" --target drop-in-all -j"$JOBS"

    # Stamp what now sits in modules/*/.libs/.
    #
    # Both build types drop into the SAME .libs/ layout, because that is where bin/scilab
    # and package-macos.sh read their binaries from. So a debug build silently replaces
    # the release one there, and nothing downstream could tell -- you could build debug,
    # forget, package, and ship an unoptimised Scilab with no warning anywhere. The stamp
    # is what lets package-macos.sh notice (it refuses a debug payload unless told
    # otherwise).
    #
    # Written AFTER drop-in, so it describes what is actually in .libs/ rather than what
    # was merely configured. Running `cmake --build <dir> --target drop-in-all` by hand
    # bypasses this and leaves the stamp stale -- package-macos.sh treats a missing stamp
    # as unknown and says so rather than assuming release.
    printf '%s\n' "$build_type" > .scilab-build-type

    # The macro library (.sci -> .bin) is a SEPARATE target, deliberately not part of
    # drop-in-all: it runs the just-built scilab-cli-bin over the .sci sources, so it
    # depends on the executable already existing (the bootstrap loop).
    echo "[3/4] macros (.sci -> .bin)…"
    cmake --build "$build" --target macros -j"$JOBS"

    # Help content (DocBook XML -> JavaHelp jars in modules/helptools/target/). Also
    # circular — it drives the built scilab-adv-cli headless via xmltojar — and the
    # slowest step. ScilabHelp.cmake supplies the JDK-25 -Djdk.xml.*Limit=0 lifts the
    # DocBook pipeline needs; without them the parse dies on jdk.xml.totalEntitySizeLimit.
    echo "[4/4] doc (help browser content, all locales)…"
    cmake --build "$build" --target doc
}

for t in "${BUILD_TYPES[@]}"; do
    build_one "$t"
done

# What the tree is LEFT holding is the last type built (see --both above).
FINAL_TYPE="${BUILD_TYPES[${#BUILD_TYPES[@]}-1]}"

echo
if [ "${#BUILD_TYPES[@]}" -gt 1 ]; then
    echo "Build complete — ${BUILD_TYPES[*]}."
    echo "  Trees:     build-cmake-debug/ and build-cmake/ both kept, so either can be"
    echo "             rebuilt incrementally."
    echo "  In place:  modules/*/.libs/ holds the $FINAL_TYPE build (the last one built)."
else
    echo "Build complete ($FINAL_TYPE, from $(build_dir_for "$FINAL_TYPE")/)."
fi
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab     # run the GUI"
if [ "$FINAL_TYPE" = debug ]; then
    echo "  Package:   ./package-macos.sh --allow-debug   # refuses a debug payload otherwise"
    echo
    echo "  NOTE: modules/*/.libs/ now holds DEBUG binaries (-O0 -g3). Re-run"
    echo "        ./build-macos.sh   to put the release build back."
else
    echo "  Package:   ./package-macos.sh              # build the /Applications app"
fi
