# CMake build for the `differential_equations` module — Batch E (inter-module
# edge) on the shared scilab_module() helper (cmake/ScilabModule.cmake); built
# from the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this
# file is invisible to automake; the autotools originals are always
# recoverable with `make -C modules/differential_equations`.
#
# TWO dylibs from ONE module dir (the manifest's ground truth — there is NO
# modules/sundials/ directory): `libscisundials` is the vendored
# patched_sundials library (its own pkglib .la in THIS Makefile.am), and
# `libscidifferential_equations` is the module proper, which links it. Both
# scilab_module() calls therefore live here, and both drop-ins land in
# modules/differential_equations/.libs/ — exactly where autotools puts them.
# sundials is declared FIRST (differential_equations MODULE_DEPS it); scicos
# and xcos (their own Batch-E files) link the scisundials target too.
#
# LINKING CLASS (modules/Makefile.am): differential_equations = ENGINE_LIBS.
# libscisundials appears in NO aggregate link list — it is linked directly by
# differential_equations/scicos/xcos — so the helper's CLASS keyword is
# omitted on the sundials call; this comment is the record (the manifest's
# `(none)` class, same handling as Batch B's stubs).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscisundials.2027.dylib               exports 2371; deps
#       /opt/homebrew/opt/suite-sparse/lib/lib{klu.2,amd.3,umfpack.6}.dylib
#       + /opt/homebrew/opt/libomp/lib/libomp.dylib + libSystem ONLY (pure-C
#       link — no libc++, LANG C keeps the C driver)
#   libscidifferential_equations.2027.dylib exports 505; deps = the sundials
#       set + /usr/local/lib/scilab/libscisundials.2027.dylib (the MODULE_DEPS
#       edge, recorded at the sibling's install_name) + libgfortran.5 +
#       libquadmath.0 (Fortran objects -> CMake's implicit Fortran runtime,
#       the interpolation pattern) + libc++ + libSystem
#
# EXTERNAL DEPS, from the autotools LIBADDs:
#   libscisundials_la_LIBADD                 = $(KLU_LIBS) $(UMFPACK_LIBS) $(OPENMP_LIBS)
#   libscidifferential_equations_la_LIBADD   = algo + libscisundials.la
#                                              + $(KLU_LIBS) $(UMFPACK_LIBS)
#                                              + $(OPENMP_LIBS) + $(FLIBS)
# config.status: KLU_LIBS = `-lklu -lamd` (the -L rides on UMFPACK_LIBS =
# `-L/opt/homebrew/opt/suite-sparse/lib -lumfpack`); OPENMP_LIBS = `-lomp`.
# Reproduced as ABSOLUTE keg paths in SYSTEM_LIBS (the matio pattern — the
# recorded dep is each file's install_name, identical to what -l resolution
# recorded) + FIND_PACKAGES OpenMP (records libomp's absolute install_name and
# contributes the OpenMP compile flag to C TUs, the parallel pattern; the
# baseline compiled with OPENMP_CFLAGS/CXXFLAGS = `-Xpreprocessor -fopenmp`).
#
# INCLUDES: sundials' la_CPPFLAGS is a standalone 15-dir list (no
# AM_CPPFLAGS!); `#include <suitesparse/klu.h>` resolved through the GLOBAL
# configure CPPFLAGS' /opt/homebrew/include (verified in the baseline .Plo
# depfile) — the helper's SCILAB_HOMEBREW_INCLUDES covers it (KLU_CFLAGS is
# configured EMPTY). The main module adds the sparse/ColPack tree, console/
# threads/string/elementary_functions and EIGEN_CPPFLAGS
# (/opt/homebrew/opt/eigen/include/eigen3) per its la_CPPFLAGS.
#
# Sources verbatim from Makefile.am. GATEWAY_C_SOURCES is referenced in
# libscidifferential_equations_la_SOURCES but never defined -> empty (the
# matio GATEWAY_CPP_SOURCES precedent). The manifest's language census counts
# files on disk; the build lists below are the authoritative TU sets.

# --- the vendored patched_sundials library (pure C, 103 TUs) ---
scilab_module(sundials
  GATEWAY_SOURCES
    src/patched_sundials/src/arkode/arkode.c
    src/patched_sundials/src/arkode/arkode_user_controller.c
    src/patched_sundials/src/arkode/arkode_sunstepper.c
    src/patched_sundials/src/arkode/arkode_sprkstep.c
    src/patched_sundials/src/arkode/arkode_sprkstep_io.c
    src/patched_sundials/src/arkode/arkode_sprk.c
    src/patched_sundials/src/arkode/arkode_splittingstep.c
    src/patched_sundials/src/arkode/arkode_splittingstep_coefficients.c
    src/patched_sundials/src/arkode/arkode_root.c
    src/patched_sundials/src/arkode/arkode_relaxation.c
    src/patched_sundials/src/arkode/arkode_mristep.c
    src/patched_sundials/src/arkode/arkode_mristep_nls.c
    src/patched_sundials/src/arkode/arkode_mristep_io.c
    src/patched_sundials/src/arkode/arkode_mristep_controller.c
    src/patched_sundials/src/arkode/arkode_mri_tables.c
    src/patched_sundials/src/arkode/arkode_lsrkstep.c
    src/patched_sundials/src/arkode/arkode_lsrkstep_io.c
    src/patched_sundials/src/arkode/arkode_ls.c
    src/patched_sundials/src/arkode/arkode_io.c
    src/patched_sundials/src/arkode/arkode_interp.c
    src/patched_sundials/src/arkode/arkode_forcingstep.c
    src/patched_sundials/src/arkode/arkode_erkstep.c
    src/patched_sundials/src/arkode/arkode_erkstep_io.c
    src/patched_sundials/src/arkode/arkode_butcher.c
    src/patched_sundials/src/arkode/arkode_butcher_erk.c
    src/patched_sundials/src/arkode/arkode_butcher_dirk.c
    src/patched_sundials/src/arkode/arkode_bbdpre.c
    src/patched_sundials/src/arkode/arkode_bandpre.c
    src/patched_sundials/src/arkode/arkode_arkstep.c
    src/patched_sundials/src/arkode/arkode_arkstep_nls.c
    src/patched_sundials/src/arkode/arkode_arkstep_io.c
    src/patched_sundials/src/arkode/arkode_adapt.c
    src/patched_sundials/src/cvodes/cvodea.c
    src/patched_sundials/src/cvodes/cvodea_io.c
    src/patched_sundials/src/cvodes/cvodes.c
    src/patched_sundials/src/cvodes/cvodes_bandpre.c
    src/patched_sundials/src/cvodes/cvodes_bbdpre.c
    src/patched_sundials/src/cvodes/cvodes_diag.c
    src/patched_sundials/src/cvodes/cvodes_io.c
    src/patched_sundials/src/cvodes/cvodes_ls.c
    src/patched_sundials/src/cvodes/cvodes_nls.c
    src/patched_sundials/src/cvodes/cvodes_nls_sim.c
    src/patched_sundials/src/cvodes/cvodes_nls_stg.c
    src/patched_sundials/src/cvodes/cvodes_nls_stg1.c
    src/patched_sundials/src/cvodes/cvodes_proj.c
    src/patched_sundials/src/idas/idaa.c
    src/patched_sundials/src/idas/idaa_io.c
    src/patched_sundials/src/idas/idas.c
    src/patched_sundials/src/idas/idas_bbdpre.c
    src/patched_sundials/src/idas/idas_ic.c
    src/patched_sundials/src/idas/idas_io.c
    src/patched_sundials/src/idas/idas_ls.c
    src/patched_sundials/src/idas/idas_nls.c
    src/patched_sundials/src/idas/idas_nls_sim.c
    src/patched_sundials/src/idas/idas_nls_stg.c
    src/patched_sundials/src/kinsol/kinsol.c
    src/patched_sundials/src/kinsol/kinsol_bbdpre.c
    src/patched_sundials/src/kinsol/kinsol_io.c
    src/patched_sundials/src/kinsol/kinsol_ls.c
    src/patched_sundials/src/nvector/openmp/nvector_openmp.c
    src/patched_sundials/src/nvector/serial/nvector_serial.c
    src/patched_sundials/src/nvector/manyvector/nvector_manyvector.c
    src/patched_sundials/src/sunadaptcontroller/imexgus/sunadaptcontroller_imexgus.c
    src/patched_sundials/src/sunadaptcontroller/mrihtol/sunadaptcontroller_mrihtol.c
    src/patched_sundials/src/sunadaptcontroller/soderlind/sunadaptcontroller_soderlind.c
    src/patched_sundials/src/sundials/sundials_adaptcontroller.c
    src/patched_sundials/src/sundials/sundials_adjointcheckpointscheme.c
    src/patched_sundials/src/sundials/sundials_band.c
    src/patched_sundials/src/sundials/sundials_context.c
    src/patched_sundials/src/sundials/sundials_dense.c
    src/patched_sundials/src/sundials/sundials_direct.c
    src/patched_sundials/src/sundials/sundials_errors.c
    src/patched_sundials/src/sundials/sundials_extension.c
    src/patched_sundials/src/sundials/sundials_futils.c
    src/patched_sundials/src/sundials/sundials_hashmap.c
    src/patched_sundials/src/sundials/sundials_iterative.c
    src/patched_sundials/src/sundials/sundials_linearsolver.c
    src/patched_sundials/src/sundials/sundials_logger.c
    src/patched_sundials/src/sundials/sundials_math.c
    src/patched_sundials/src/sundials/sundials_matrix.c
    src/patched_sundials/src/sundials/sundials_memory.c
    src/patched_sundials/src/sundials/sundials_nonlinearsolver.c
    src/patched_sundials/src/sundials/sundials_nvector.c
    src/patched_sundials/src/sundials/sundials_nvector_senswrapper.c
    src/patched_sundials/src/sundials/sundials_stepper.c
    src/patched_sundials/src/sundials/sundials_adjointstepper.c
    src/patched_sundials/src/sundials/sundials_version.c
    src/patched_sundials/src/sunlinsol/band/sunlinsol_band.c
    src/patched_sundials/src/sunlinsol/dense/sunlinsol_dense.c
    src/patched_sundials/src/sunlinsol/lapackband/sunlinsol_lapackband.c
    src/patched_sundials/src/sunlinsol/lapackdense/sunlinsol_lapackdense.c
    src/patched_sundials/src/sunlinsol/pcg/sunlinsol_pcg.c
    src/patched_sundials/src/sunlinsol/klu/sunlinsol_klu.c
    src/patched_sundials/src/sunlinsol/spbcgs/sunlinsol_spbcgs.c
    src/patched_sundials/src/sunlinsol/spfgmr/sunlinsol_spfgmr.c
    src/patched_sundials/src/sunlinsol/spgmr/sunlinsol_spgmr.c
    src/patched_sundials/src/sunlinsol/sptfqmr/sunlinsol_sptfqmr.c
    src/patched_sundials/src/sunmatrix/band/sunmatrix_band.c
    src/patched_sundials/src/sunmatrix/dense/sunmatrix_dense.c
    src/patched_sundials/src/sunmatrix/sparse/sunmatrix_sparse.c
    src/patched_sundials/src/sunmemory/system/sundials_system_memory.c
    src/patched_sundials/src/sunnonlinsol/fixedpoint/sunnonlinsol_fixedpoint.c
    src/patched_sundials/src/sunnonlinsol/newton/sunnonlinsol_newton.c
  LANG C
  SYSTEM_LIBS
    /opt/homebrew/opt/suite-sparse/lib/libklu.dylib
    /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
    /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
  FIND_PACKAGES OpenMP
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/cvodes
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/idas
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/arkode
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/kinsol
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/nvector
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/sundials
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/sunmatrix
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/sunlinsol
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include/sunnonlinsol
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/sundials
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/cvodes
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/idas
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/arkode
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/kinsol
  SYMBOLS 2371)

# --- per-TU override, verbatim from Makefile.am (`if IS_MACOSX` / "enforce
# -O0 for some files"): colnew.f compiles with -O0 APPENDED after the global
# -O2 (last-wins). Upstream added it in a45812e728f (2019, "macOS: fix some
# crash while using gfortran from homebrew") — the legacy-Fortran-miscompiled-
# at-O2 class; dropping it would silently reintroduce the crash while every
# harness fact stays green. The baseline object's DWARF producer reads
# `-g1 -O2 -O0 -fwrapv`; this reproduces that exact shape.
# KNOWN flag-facts exception: parity/flagfacts_check.py expects opt=O2 on
# every TU and has no per-file exception table, so it reports exactly this one
# line (colnew.f: opt='O0'). That report is CORRECT-by-baseline; teaching the
# checker about legitimate per-TU overrides is harness work outside this
# batch's file set.
set_source_files_properties(src/fortran/colnew.f PROPERTIES COMPILE_OPTIONS "-O0")

# --- the module proper ---
scilab_module(differential_equations
  ALGO_SOURCES
    src/c/colnewmsgs.c
    src/c/dassl.c
    src/c/feval.c
    src/c/arnol.c
    src/c/rk4.c
    src/c/Ex-odedc.c
    src/c/Ex-ode.c
    src/c/Ex-daskr.c
    src/c/errmds.c
    src/c/externals_for_tests.c
    src/fortran/rscar1.f
    src/fortran/bcomp.f
    src/fortran/lcomp.f
    src/fortran/loren.f
    src/fortran/prja.f
    src/fortran/vnorm.f
    src/fortran/lsoda.f
    src/fortran/lsodar.f
    src/fortran/ainvg.f
    src/fortran/lsode.f
    src/fortran/svcom1.f
    src/fortran/dqk21.f
    src/fortran/solsy.f
    src/fortran/lsodi.f
    src/fortran/ddassl.f
    src/fortran/dqpsrt.f
    src/fortran/dqelg.f
    src/fortran/cfode.f
    src/fortran/rscma1.f
    src/fortran/colnew.f
    src/fortran/dcutet.f
    src/fortran/xsetf.f
    src/fortran/dgbsl.f
    src/fortran/rkf45.f
    src/fortran/rchek.f
    src/fortran/dqags.f
    src/fortran/xerrwv.f
    src/fortran/twodq.f
    src/fortran/dqagse.f
    src/fortran/greatr.f
    src/fortran/hpdel.f
    src/fortran/hpins.f
    src/fortran/svcar1.f
    src/fortran/rscom1.f
    src/fortran/rksimp.f
    src/fortran/roots.f
    src/fortran/stoda.f
    src/fortran/bnorm.f
    src/fortran/rchek2.f
    src/fortran/stode.f
    src/fortran/vmnorm.f
    src/fortran/prepj.f
    src/fortran/lsdisc.f
    src/fortran/fnorm.f
    src/fortran/ddasrt.f
    src/fortran/ddaskr.f
    src/fortran/daux.f
    src/fortran/stodi.f
    src/fortran/xsetun.f
    src/fortran/intdy.f
    src/fortran/ewset.f
    src/fortran/roots2.f
    src/fortran/prepji.f
    src/fortran/svcma1.f
    src/fortran/lsrgk.f
    src/fortran/odeint.f
    src/fortran/rkqc.f
    src/fortran/writbufode.f
    src/cpp/scifunctions.cpp
    src/cpp/checkodeerror.cpp
    src/cpp/differentialequationfunctions.cpp
    src/cpp/SUNDIALSManager.cpp
    src/cpp/CVODEManager.cpp
    src/cpp/KINSOLManager.cpp
    src/cpp/IDAManager.cpp
    src/cpp/ARKODEManager.cpp
    src/cpp/OdeManagerInit.cpp
    src/cpp/OdeManagerCompute.cpp
    src/cpp/OdeManagerParse.cpp
    src/cpp/OdeManagerHelpers.cpp
    src/cpp/OdeManagerSolve.cpp
    src/cpp/odeparameters.cpp
    src/cpp/complexHelpers.cpp
  GATEWAY_SOURCES
    sci_gateway/fortran/Ex-impl.f
    sci_gateway/fortran/Ex-int2d.f
    sci_gateway/fortran/Ex-int3d.f
    sci_gateway/fortran/Ex-intg.f
    sci_gateway/fortran/Ex-dasrt.f
    sci_gateway/fortran/Ex-daskr.f
    sci_gateway/fortran/Ex-dassl.f
    sci_gateway/fortran/Ex-bvode.f
    sci_gateway/cpp/differential_equations_gw.cpp
    sci_gateway/cpp/sci_int2d.cpp
    sci_gateway/cpp/sci_int3d.cpp
    sci_gateway/cpp/sci_intg.cpp
    sci_gateway/cpp/sci_ode.cpp
    sci_gateway/cpp/sci_odedc.cpp
    sci_gateway/cpp/sci_feval.cpp
    sci_gateway/cpp/sci_bvode.cpp
    sci_gateway/cpp/sci_impl.cpp
    sci_gateway/cpp/sci_dassl.cpp
    sci_gateway/cpp/sci_dasrt.cpp
    sci_gateway/cpp/sci_daskr.cpp
    sci_gateway/cpp/sci_cvode.cpp
    sci_gateway/cpp/sci_ida.cpp
    sci_gateway/cpp/sci_arkode.cpp
    sci_gateway/cpp/sci_kinsol.cpp
    sci_gateway/cpp/sci_percent_odeSolution.cpp
  LANG C CXX Fortran
  MODULE_DEPS scisundials
  SYSTEM_LIBS
    /opt/homebrew/opt/suite-sparse/lib/libklu.dylib
    /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
    /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
  FIND_PACKAGES OpenMP
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src
    ${CMAKE_CURRENT_SOURCE_DIR}/src/patched_sundials/src/sundials
    ${SCILAB_SOURCE_DIR}/modules/sparse/includes
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/includes
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/include
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/src/Utilities
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/src/Recovery
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/src/GeneralGraphColoring
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/src/BipartiteGraphPartialColoring
    ${SCILAB_SOURCE_DIR}/modules/sparse/src/ColPack/src/BipartiteGraphBicoloring
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    /opt/homebrew/opt/eigen/include/eigen3
  CLASS ENGINE_LIBS
  SYMBOLS 505)
