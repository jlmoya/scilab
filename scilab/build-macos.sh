#!/usr/bin/env bash
# Full from-source build of Scilab (branch 2027) on macOS arm64 with JDK 25.
#
# The dev branch's committed generated Makefiles are stale w.r.t. a few macOS fixes,
# so this wraps: configure -> three build-time Makefile patches -> make. Afterwards run
# ./reapply-macos-fixes.sh (runtime dylib/deployment-target fixes), then ./bin/scilab.
#
# Run from the source root:  cd scilab/scilab && ./build-macos.sh
set -e
cd "$(dirname "$0")"

JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
export JAVA_HOME="$JDK"
export PKG_CONFIG_PATH="$PWD/../xlnt-prefix/lib/pkgconfig:$(brew --prefix)/opt/pcre2/lib/pkgconfig:$(brew --prefix)/opt/openblas/lib/pkgconfig:$(brew --prefix)/lib/pkgconfig"
export CPPFLAGS="-I$(brew --prefix)/include -I$(brew --prefix)/opt/libomp/include -I$(brew --prefix)/opt/libarchive/include"
export LDFLAGS="-L$(brew --prefix)/lib -L$(brew --prefix)/opt/libomp/lib -L$(brew --prefix)/opt/libarchive/lib"

echo "[1/3] configure (JDK 25)…"
./configure --with-jdk="$JDK" --with-ant=/Users/josemoya/.sdkman/candidates/ant/current \
  --without-tk --without-modelica --disable-build-help --disable-ccache \
  --with-blas-library=/opt/homebrew/opt/openblas/lib --with-lapack-library=/opt/homebrew/opt/openblas/lib \
  --with-arpack-library=/opt/homebrew/opt/arpack/lib \
  --with-fftw-include=/opt/homebrew/opt/fftw/include --with-fftw-library=/opt/homebrew/opt/fftw/lib \
  --with-hdf5-include=/opt/homebrew/opt/hdf5/include --with-hdf5-library=/opt/homebrew/opt/hdf5/lib \
  --with-matio-include=/opt/homebrew/opt/libmatio/include --with-matio-library=/opt/homebrew/opt/libmatio/lib \
  --with-umfpack-include=/opt/homebrew/opt/suite-sparse/include/suitesparse --with-umfpack-library=/opt/homebrew/opt/suite-sparse/lib \
  --with-eigen-include=/opt/homebrew/opt/eigen/include/eigen3

echo "[2/3] macOS build-time Makefile fixes…"
export LC_ALL=C LANG=C
# (a) OpenMP: Apple clang needs '-Xpreprocessor -fopenmp' and links '-lomp' (not -fopenmp/-lgomp)
find . -name Makefile -exec sed -i '' \
  -e 's/^OPENMP_CFLAGS = -fopenmp$/OPENMP_CFLAGS = -Xpreprocessor -fopenmp/' \
  -e 's/^OPENMP_CXXFLAGS = -fopenmp$/OPENMP_CXXFLAGS = -Xpreprocessor -fopenmp/' \
  -e 's/^OPENMP_LIBS = -lgomp$/OPENMP_LIBS = -lomp/' {} +
# (b) helptools: the disable-stub lib is gated wrong for --disable-build-help; give it its
#     source+object so it isn't empty (an empty .la fails to link on macOS: "no object files")
sed -i '' \
  -e 's|^#am__objects_2 = sci_gateway/nogui/libscihelptools_disable_la-nogui.lo|am__objects_2 = sci_gateway/nogui/libscihelptools_disable_la-nogui.lo|' \
  -e 's|^#HELPTOOLS_DISABLE_CPP_SOURCES = sci_gateway/nogui/nogui.cpp|HELPTOOLS_DISABLE_CPP_SOURCES = sci_gateway/nogui/nogui.cpp|' \
  modules/helptools/Makefile
# (c) spreadsheet: Apache Arrow 24 headers require C++20 (this module only)
sed -i '' 's/-std=c++17/-std=c++20/g' modules/spreadsheet/Makefile

echo "[3/3] make -j$(sysctl -n hw.ncpu)…"
make -j"$(sysctl -n hw.ncpu)"

echo
echo "Build complete. Next:"
echo "  ./reapply-macos-fixes.sh                          # macOS runtime fixes"
echo "  JAVA_HOME=$JDK ./bin/scilab                       # run the GUI from the terminal"
