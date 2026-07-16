# CMake build for the `interpolation` module — migrated onto the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver, no longer a standalone project. HYBRID
# COEXISTENCE: this file is invisible to automake; the autotools interpolation
# is always recoverable with `make -C modules/interpolation`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD — dlopen-only;
# no executable or engine lib records libsciinterpolation as a load-command
# dependency, so the drop-in swap is relink-free.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciinterpolation.2027.dylib (+ libsciinterpolation.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libsciinterpolation.2027.dylib
#   exports:       exactly 64 symbols (mixed C/Fortran/C++: the Fortran spline/
#                  mesh kernels like _bicubicspline_/_cshep2_, the C algo entry
#                  points, the 12 sci_* gateways, the _info_hermite_ common)
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#                  — NOTHING else: no libgcc_s, no emutls/heapt dylib, no @rpath.
#
# Sources: the 2 INTERPOLATION_C_SOURCES + 4 INTERPOLATION_FORTRAN_SOURCES
# (the noinst algo lib; fixed-form .f — gfortran resolves their
# `include 'constinterp.h.f'` next to the including source file) + the 12
# GATEWAY_CPP_SOURCES, verbatim from Makefile.am.
# LANG C Fortran CXX drives everything mixed-language in the helper: Fortran
# TUs get SCI_FFLAGS + core/includes ONLY (no DEFS, no C/C++ include set); the
# C++ driver links (libc++ dep); and because the target contains Fortran
# objects, CMake appends its detected Fortran implicit-link info — the
# $(FLIBS) equivalent: libgfortran/libquadmath ride in at their absolute
# Homebrew install_names, the emutls_w/heapt_w/gcc helpers fold in as static
# .a's (no dylib dep). Nothing Fortran-specific to declare here.
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES: threads, string,
# elementary_functions.

scilab_module(interpolation
  ALGO_SOURCES
    src/c/interpolation.c
    src/c/someinterp.c
    src/fortran/somespline.f
    src/fortran/dspfit.f
    src/fortran/cshep2d.f
    src/fortran/mesh2b.f
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_lsq_splin.cpp
    sci_gateway/cpp/sci_interp.cpp
    sci_gateway/cpp/sci_interp2d.cpp
    sci_gateway/cpp/sci_splin.cpp
    sci_gateway/cpp/sci_cshep2d.cpp
    sci_gateway/cpp/sci_interp3d.cpp
    sci_gateway/cpp/sci_linear_interpn.cpp
    sci_gateway/cpp/sci_splin2d.cpp
    sci_gateway/cpp/sci_splin3d.cpp
    sci_gateway/cpp/sci_bsplin3val.cpp
    sci_gateway/cpp/sci_eval_cshep2d.cpp
    sci_gateway/cpp/sci_mesh2di.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 64)
