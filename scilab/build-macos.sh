#!/usr/bin/env bash
# Full from-source build of Scilab (branch 2027) on macOS arm64 with JDK 25.
#
# This is now a thin convenience wrapper: it just documents the feature flags and
# dependency locations for this machine and runs a plain `./configure && make`.
# configure is self-sufficient on macOS (it derives the Homebrew + ../xlnt-prefix
# search paths and the rpaths itself), and every former post-configure Makefile
# patch and post-build fixup lives in configure.ac / the Makefile.am files.
# See docs/design/build-modernization.md.
#
# Run from the source root:  cd scilab/scilab && ./build-macos.sh
set -e
cd "$(dirname "$0")"

JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export JAVA_HOME="$JDK"   # ant + the java checks read it during configure/make

echo "[1/3] configure (JDK 25)…"
./configure --with-jdk="$JDK" --with-ant=/Users/josemoya/.sdkman/candidates/ant/current \
  --without-tk --without-modelica --disable-ccache \
  --with-blas-library=/opt/homebrew/opt/openblas/lib --with-lapack-library=/opt/homebrew/opt/openblas/lib \
  --with-arpack-library=/opt/homebrew/opt/arpack/lib \
  --with-fftw-include=/opt/homebrew/opt/fftw/include --with-fftw-library=/opt/homebrew/opt/fftw/lib \
  --with-hdf5-include=/opt/homebrew/opt/hdf5/include --with-hdf5-library=/opt/homebrew/opt/hdf5/lib \
  --with-matio-include=/opt/homebrew/opt/libmatio/include --with-matio-library=/opt/homebrew/opt/libmatio/lib \
  --with-umfpack-include=/opt/homebrew/opt/suite-sparse/include/suitesparse --with-umfpack-library=/opt/homebrew/opt/suite-sparse/lib \
  --with-eigen-include=/opt/homebrew/opt/eigen/include/eigen3

echo "[2/3] make -j$(sysctl -n hw.ncpu)…"
make -j"$(sysctl -n hw.ncpu)"

echo "[3/3] make doc (help browser content, all languages)…"
make doc

echo
echo "Build complete."
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab     # run the GUI"
echo "  Package:   ./package-macos.sh              # build the /Applications app"
