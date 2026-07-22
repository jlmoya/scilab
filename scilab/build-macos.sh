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
# Run from the source root:  cd scilab/scilab && ./build-macos.sh
set -e
cd "$(dirname "$0")"

JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export JAVA_HOME="$JDK"          # the Maven reactor + the JNI probes read it
GFORTRAN=/opt/homebrew/bin/gfortran
BUILD=build-cmake
JOBS="$(sysctl -n hw.ncpu)"

echo "[1/4] cmake configure (JDK 25, gfortran)…"
cmake -S . -B "$BUILD" -DCMAKE_Fortran_COMPILER="$GFORTRAN"

# drop-in-all = the 64 module dylibs + the 21 fold-in OBJECT libs + the two aggregates
# (libscilab / libscilab-cli) + both executables + sci-java-all (the 24 Maven module
# jars, built into modules/<m>/target/). Each artifact is copied into place, so the
# tree is runnable straight afterwards.
echo "[2/4] build the app — native + Maven jars (-j$JOBS)…"
cmake --build "$BUILD" --target drop-in-all -j"$JOBS"

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
echo "Build complete."
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab     # run the GUI"
echo "  Package:   ./package-macos.sh              # build the /Applications app"
