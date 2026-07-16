# CMake build for the `randlib` module — Batch C (Fortran runtime) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools randlib is always recoverable with
# `make -C modules/randlib`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscirandlib.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscirandlib.2027.dylib
#   exports:       exactly 58 symbols
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: only $(FLIBS) — LANG Fortran rides the runtime in
# automatically (the interpolation-exemplar pattern).
#
# Sources verbatim from Makefile.am: algo = grand.cpp + 11 C + 15 F;
# gateway = sci_grand.cpp.

scilab_module(randlib
  ALGO_SOURCES
    src/cpp/grand.cpp
    src/c/mt.c
    src/c/igngeom.c
    src/c/kiss.c
    src/c/urand.c
    src/c/clcg2.c
    src/c/clcg4.c
    src/c/sexpo.c
    src/c/ignbin.c
    src/c/ignpoi.c
    src/c/sgamma.c
    src/c/genbet.c
    src/fortran/gennf.f
    src/fortran/genchi.f
    src/fortran/setgmn.f
    src/fortran/gengam.f
    src/fortran/snorm.f
    src/fortran/gennch.f
    src/fortran/genmn.f
    src/fortran/phrtsd.f
    src/fortran/ignnbn.f
    src/fortran/genf.f
    src/fortran/genunf.f
    src/fortran/genexp.f
    src/fortran/genmul.f
    src/fortran/genprm.f
    src/fortran/gennor.f
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_grand.cpp
  LANG C CXX Fortran
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 58)
