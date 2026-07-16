# CMake build for the `signal_processing` module — Batch C (Fortran runtime)
# on the shared scilab_module() helper (cmake/ScilabModule.cmake); built from
# the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools signal_processing is always recoverable
# with `make -C modules/signal_processing`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscisignal_processing.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscisignal_processing.2027.dylib
#   exports:       exactly 110 symbols
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: only $(FLIBS) — LANG Fortran rides the runtime in
# automatically (the interpolation-exemplar pattern).
#
# Sources verbatim from Makefile.am: algo = 58 .f (incl. Ex-corr.f) + 2 C +
# 1 CPP; gateway = 3 C + 6 CPP.

scilab_module(signal_processing
  ALGO_SOURCES
    src/fortran/degree.f
    src/fortran/parcha.f
    src/fortran/ouch.f
    src/fortran/dfftbi.f
    src/fortran/dsqrtc.f
    src/fortran/tranze.f
    src/fortran/tscccf.f
    src/fortran/dsn2.f
    src/fortran/hammin.f
    src/fortran/bounn.f
    src/fortran/nstabl.f
    src/fortran/ino.f
    src/fortran/desia.f
    src/fortran/desi11.f
    src/fortran/cmpse3.f
    src/fortran/cheby.f
    src/fortran/trbize.f
    src/fortran/r2tx.f
    src/fortran/deli2.f
    src/fortran/gee.f
    src/fortran/r4tx.f
    src/fortran/transn.f
    src/fortran/r8tx.f
    src/fortran/deli11.f
    src/fortran/compel.f
    src/fortran/filbut.f
    src/fortran/desi01.f
    src/fortran/desi22.f
    src/fortran/amell.f
    src/fortran/desi24.f
    src/fortran/dellk.f
    src/fortran/syredi.f
    src/fortran/coeft.f
    src/fortran/tranpo.f
    src/fortran/blnumz.f
    src/fortran/sn.f
    src/fortran/bldenz.f
    src/fortran/desib.f
    src/fortran/freque.f
    src/fortran/desi12.f
    src/fortran/cmpse2.f
    src/fortran/desi14.f
    src/fortran/delip.f
    src/fortran/deli1.f
    src/fortran/trbipo.f
    src/fortran/rpem.f
    src/fortran/dfft2.f
    src/fortran/dgee01.f
    src/fortran/romeg.f
    src/fortran/snell.f
    src/fortran/fft842.f
    src/fortran/tg02ad.f
    src/fortran/remez.f
    src/fortran/poles.f
    src/fortran/desi00.f
    src/fortran/desi21.f
    src/fortran/dfftmx.f
    src/fortran/Ex-corr.f
    src/c/corrtable.c
    src/c/conv2.c
    src/cpp/signalprocessingfunctions.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_remez.c
    sci_gateway/c/sci_amell.c
    sci_gateway/c/sci_conv2.c
    sci_gateway/cpp/sci_rpem.cpp
    sci_gateway/cpp/sci_corr.cpp
    sci_gateway/cpp/sci_delip.cpp
    sci_gateway/cpp/sci_fft.cpp
    sci_gateway/cpp/sci_syredi.cpp
    sci_gateway/cpp/sci_filter.cpp
  LANG C CXX Fortran
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 110)
