# CMake build for the `special_functions` module — Batch C (Fortran runtime)
# on the shared scilab_module() helper (cmake/ScilabModule.cmake); built from
# the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools special_functions is always recoverable
# with `make -C modules/special_functions`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscispecial_functions.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscispecial_functions.2027.dylib
#   exports:       exactly 45 symbols
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: only $(FLIBS) — LANG Fortran rides the runtime in
# automatically (the interpolation-exemplar pattern).
#
# Sources verbatim from Makefile.am: algo = zbeshv.c + faddeeva.cpp + 9 .f;
# gateway = 7 C + 4 CXX. EXTRA_INCLUDES: sci_gateway/cpp (module-local; the
# helper's default set has src/c + src/cpp only) + core/src/c + the usual
# module set.

scilab_module(special_functions
  ALGO_SOURCES
    src/c/zbeshv.c
    src/fortran/dbesig.f
    src/fortran/dbesjg.f
    src/fortran/dbeskg.f
    src/fortran/dbesyg.f
    src/fortran/psi.f
    src/fortran/zbesig.f
    src/fortran/zbesjg.f
    src/fortran/zbeskg.f
    src/fortran/zbesyg.f
    src/cpp/faddeeva.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_bessely.c
    sci_gateway/c/sci_beta.c
    sci_gateway/c/sci_legendre.c
    sci_gateway/c/sci_besselh.c
    sci_gateway/c/sci_besseli.c
    sci_gateway/c/sci_besselj.c
    sci_gateway/c/sci_besselk.c
    sci_gateway/cpp/sci_faddeeva.cpp
    sci_gateway/cpp/sci_gamma.cpp
    sci_gateway/cpp/sci_lgamma.cpp
    sci_gateway/cpp/sci_dlgamma.cpp
  LANG C CXX Fortran
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/sci_gateway/cpp
    ${SCILAB_SOURCE_DIR}/modules/core/src/c
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
  CLASS ENGINE_LIBS
  SYMBOLS 45)
