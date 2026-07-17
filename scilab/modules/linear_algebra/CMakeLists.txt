# CMake build for the `linear_algebra` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — linear_algebra ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scilinear_algebra-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscilinear_algebra-
# algo (17 TUs: FORTRAN + C + CPP) + libscilinear_algebra (17 gateway TUs).
#
# EXTRA_INCLUDES (libscilinear_algebra_la_CPPFLAGS): console, threads,
# string, elementary_functions, arnoldi.

scilab_object_module(linear_algebra
  SOURCES
    # libscilinear_algebra-algo.la (17 TUs)
    src/fortran/DGELSY1.f
    src/fortran/ZGELSY1.f
    src/fortran/find.f
    src/c/schurtable.c
    src/c/lu.c
    src/c/qr.c
    src/c/hess.c
    src/c/eigen.c
    src/c/balanc.c
    src/c/chol.c
    src/c/svd.c
    src/c/rcond.c
    src/c/det.c
    src/c/lsq.c
    src/c/norm.c
    src/cpp/scischur.cpp
    src/cpp/schurSelect.cpp
    # libscilinear_algebra.la (17 TUs)
    sci_gateway/c/assembleEigenvectors.c
    sci_gateway/c/sci_norm.c
    sci_gateway/c/sci_eigs.c
    sci_gateway/cpp/linear_algebra_gw.cpp
    sci_gateway/cpp/sci_chol.cpp
    sci_gateway/cpp/sci_bdiag.cpp
    sci_gateway/cpp/sci_inv.cpp
    sci_gateway/cpp/sci_svd.cpp
    sci_gateway/cpp/sci_rcond.cpp
    sci_gateway/cpp/sci_qr.cpp
    sci_gateway/cpp/sci_lu.cpp
    sci_gateway/cpp/sci_lsq.cpp
    sci_gateway/cpp/sci_hess.cpp
    sci_gateway/cpp/sci_spec.cpp
    sci_gateway/cpp/sci_det.cpp
    sci_gateway/cpp/sci_balanc.cpp
    sci_gateway/cpp/sci_schur.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/arnoldi/includes)
