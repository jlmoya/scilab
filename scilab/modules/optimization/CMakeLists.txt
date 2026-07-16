# CMake build for the `optimization` module — Batch C (Fortran runtime) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools optimization is always recoverable with
# `make -C modules/optimization`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscioptimization.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscioptimization.2027.dylib
#   exports:       exactly 154 symbols
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: only $(FLIBS) — the Fortran runtime. LANG includes Fortran, so
# CMake appends its detected Fortran implicit-link info to the C++ link line
# (the interpolation-exemplar pattern): libgfortran/libquadmath ride in at
# their absolute Homebrew install_names, the emutls_w/heapt_w helpers fold in
# as static .a's. Nothing to declare.
#
# Sources verbatim from Makefile.am: algo = sp.c + 3 cpp + 65 OPTIMIZATION
# .f + 17 minpack .f; gateway = 5 cpp + 2 c + 3 Ex-*.f.

scilab_module(optimization
  ALGO_SOURCES
    src/c/sp.c
    src/cpp/optimizationfunctions.cpp
    src/cpp/checkoptimerror.cpp
    src/cpp/scioptimfunctions.cpp
    src/fortran/gcbd.f
    src/fortran/qpgen2.f
    src/fortran/n1qn2a.f
    src/fortran/fuclid.f
    src/fortran/fmc11b.f
    src/fortran/ql0001.f
    src/fortran/shanph.f
    src/fortran/fajc1.f
    src/fortran/majysa.f
    src/fortran/icsei.f
    src/fortran/majour.f
    src/fortran/n1qn1.f
    src/fortran/n1qn1a.f
    src/fortran/n1qn3.f
    src/fortran/fmc11z.f
    src/fortran/icse0.f
    src/fortran/icse2.f
    src/fortran/fmuls1.f
    src/fortran/icsec2.f
    src/fortran/gcp.f
    src/fortran/n1fc1a.f
    src/fortran/n1gc2.f
    src/fortran/proj.f
    src/fortran/n1gc2a.f
    src/fortran/ddd2.f
    src/fortran/fremf2.f
    src/fortran/dcube.f
    src/fortran/fcube.f
    src/fortran/bfgsd.f
    src/fortran/fcomp1.f
    src/fortran/fmulb1.f
    src/fortran/n1fc1o.f
    src/fortran/relvar.f
    src/fortran/satur.f
    src/fortran/strang.f
    src/fortran/fmc11a.f
    src/fortran/fmani1.f
    src/fortran/fmc11e.f
    src/fortran/zqnbd.f
    src/fortran/ajour.f
    src/fortran/qnbd.f
    src/fortran/nlis0.f
    src/fortran/nlis2.f
    src/fortran/n1qn2.f
    src/fortran/majz.f
    src/fortran/n1qn3a.f
    src/fortran/frdf1.f
    src/fortran/icse1.f
    src/fortran/ctonb.f
    src/fortran/icscof.f
    src/fortran/fmlag1.f
    src/fortran/n1fc1.f
    src/fortran/ffinf1.f
    src/fortran/ctcab.f
    src/fortran/n1gc2b.f
    src/fortran/fprf2.f
    src/fortran/icse.f
    src/fortran/rlbd.f
    src/fortran/calmaj.f
    src/fortran/calbx.f
    src/fortran/rednor.f
    src/fortran/zgcbd.f
    src/fortran/writebuf.f
    src/fortran/qpgen1sci.f
    src/fortran/rdmps1.f
    src/fortran/rdmpsz.f
    src/fortran/minpack/lmdif.f
    src/fortran/minpack/qrfac.f
    src/fortran/minpack/hybrj1.f
    src/fortran/minpack/dogleg.f
    src/fortran/minpack/lmder.f
    src/fortran/minpack/hybrd.f
    src/fortran/minpack/dpmpar.f
    src/fortran/minpack/lmpar.f
    src/fortran/minpack/hybrj.f
    src/fortran/minpack/r1updt.f
    src/fortran/minpack/enorm.f
    src/fortran/minpack/qform.f
    src/fortran/minpack/qrsolv.f
    src/fortran/minpack/fdjac1.f
    src/fortran/minpack/hybrd1.f
    src/fortran/minpack/fdjac2.f
    src/fortran/minpack/r1mpyq.f
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_optim.cpp
    sci_gateway/cpp/sci_fsolve.cpp
    sci_gateway/cpp/sci_semidef.cpp
    sci_gateway/cpp/sci_lsqrsolve.cpp
    sci_gateway/cpp/sci_readmps.cpp
    sci_gateway/c/sci_qld.c
    sci_gateway/c/sci_qp_solve.c
    sci_gateway/fortran/Ex-optim.f
    sci_gateway/fortran/Ex-fsolve.f
    sci_gateway/fortran/Ex-lsqrsolve.f
  LANG C CXX Fortran
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 154)
