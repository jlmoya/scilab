# CMake build for the `statistics` module — Batch C (Fortran runtime) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools statistics is always recoverable with
# `make -C modules/statistics`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscistatistics.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscistatistics.2027.dylib
#   exports:       exactly 114 symbols
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: only $(FLIBS) — LANG Fortran rides the runtime in
# automatically (the interpolation-exemplar pattern).
#
# Sources verbatim from Makefile.am: algo = 3 C + pairwise_distances.cpp +
# the 65 dcdflib .f; gateway = 12 C + 7 CPP.

scilab_module(statistics
  ALGO_SOURCES
    src/c/CdfBase.c
    src/c/ipmpar1.c
    src/c/sci_string_matrix.c
    src/cpp/pairwise_distances.cpp
    src/dcdflib/psi.f
    src/dcdflib/alngam.f
    src/dcdflib/erfc1.f
    src/dcdflib/cdff.f
    src/dcdflib/spmpar.f
    src/dcdflib/cumnor.f
    src/dcdflib/cdfpoi.f
    src/dcdflib/bcorr.f
    src/dcdflib/cumchi.f
    src/dcdflib/apser.f
    src/dcdflib/gamln1.f
    src/dcdflib/cdft.f
    src/dcdflib/cdftnc.f
    src/dcdflib/gaminv.f
    src/dcdflib/cumnbn.f
    src/dcdflib/cdffnc.f
    src/dcdflib/rlog1.f
    src/dcdflib/cdfchn.f
    src/dcdflib/dt1.f
    src/dcdflib/bgrat.f
    src/dcdflib/dinvnr.f
    src/dcdflib/rlog.f
    src/dcdflib/basym.f
    src/dcdflib/bfrac.f
    src/dcdflib/cumbin.f
    src/dcdflib/cumbet.f
    src/dcdflib/rcomp.f
    src/dcdflib/cdfgam.f
    src/dcdflib/gratio.f
    src/dcdflib/gam1.f
    src/dcdflib/exparg.f
    src/dcdflib/ipmpar.f
    src/dcdflib/cumf.f
    src/dcdflib/cumpoi.f
    src/dcdflib/gamln.f
    src/dcdflib/betaln.f
    src/dcdflib/stvaln.f
    src/dcdflib/cumt.f
    src/dcdflib/cumtnc.f
    src/dcdflib/esum.f
    src/dcdflib/bpser.f
    src/dcdflib/cdfnor.f
    src/dcdflib/cumfnc.f
    src/dcdflib/erf.f
    src/dcdflib/alnrel.f
    src/dcdflib/cumchn.f
    src/dcdflib/rexp.f
    src/dcdflib/fpser.f
    src/dcdflib/bratio.f
    src/dcdflib/cdfchi.f
    src/dcdflib/gamma.f
    src/dcdflib/dinvr.f
    src/dcdflib/brcomp.f
    src/dcdflib/devlpl.f
    src/dcdflib/cdfnbn.f
    src/dcdflib/dzror.f
    src/dcdflib/gsumln.f
    src/dcdflib/algdiv.f
    src/dcdflib/cumgam.f
    src/dcdflib/grat1.f
    src/dcdflib/brcmp1.f
    src/dcdflib/cdfbin.f
    src/dcdflib/bup.f
    src/dcdflib/cdfbet.f
  GATEWAY_SOURCES
    sci_gateway/c/sci_cdfchi.c
    sci_gateway/c/sci_cdft.c
    sci_gateway/c/sci_cdftnc.c
    sci_gateway/c/sci_cdfgam.c
    sci_gateway/c/sci_cdff.c
    sci_gateway/c/sci_cdffnc.c
    sci_gateway/c/sci_cdfpoi.c
    sci_gateway/c/sci_cdfbin.c
    sci_gateway/c/sci_cdfchn.c
    sci_gateway/c/sci_cdfbet.c
    sci_gateway/c/sci_cdfnbn.c
    sci_gateway/c/sci_cdfnor.c
    sci_gateway/cpp/sci_percent_meanshift.cpp
    sci_gateway/cpp/sci_percent_cov.cpp
    sci_gateway/cpp/sci_nansum.cpp
    sci_gateway/cpp/sci_mean.cpp
    sci_gateway/cpp/sci_nanmean.cpp
    sci_gateway/cpp/sci_pdist.cpp
    sci_gateway/cpp/sci_pdist2.cpp
  LANG C CXX Fortran
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS ENGINE_LIBS
  SYMBOLS 114)
