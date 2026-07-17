# CMake build for the `polynomials` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — polynomials ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scipolynomials-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscipolynomials-algo
# (58 TUs: POLYNOMIALS_FORTRAN + CXX, incl. the 2 Jenkins-Traub .cc TUs) +
# libscipolynomials (12 gateway TUs).
#
# EXTRA_INCLUDES (libscipolynomials_la_CPPFLAGS): threads, string,
# console, elementary_functions, then EIGEN_CPPFLAGS verbatim.

scilab_object_module(polynomials
  SOURCES
    # libscipolynomials-algo.la (58 TUs)
    src/fortran/residu.f
    src/fortran/imptra.f
    src/fortran/dmpadj.f
    src/fortran/impins.f
    src/fortran/dpmul1.f
    src/fortran/wprxc.f
    src/fortran/dmpcnc.f
    src/fortran/wmpmu.f
    src/fortran/mpinsp.f
    src/fortran/impext.f
    src/fortran/recbez.f
    src/fortran/sfact1.f
    src/fortran/dpodiv.f
    src/fortran/dmptra.f
    src/fortran/dmpins.f
    src/fortran/dpsimp.f
    src/fortran/dmpext.f
    src/fortran/wpmul.f
    src/fortran/bezstp.f
    src/fortran/wmpad.f
    src/fortran/dprxc.f
    src/fortran/quadsd.f
    src/fortran/wmpadj.f
    src/fortran/dmpmu.f
    src/fortran/wpmul1.f
    src/fortran/wmpcnc.f
    src/fortran/mptri.f
    src/fortran/horner.f
    src/fortran/idegre.f
    src/fortran/fxshfr.f
    src/fortran/mpdiag.f
    src/fortran/dmpcle.f
    src/fortran/wpodiv.f
    src/fortran/wdmpmu.f
    src/fortran/wmptra.f
    src/fortran/wmpins.f
    src/fortran/mpdegr.f
    src/fortran/ddmpev.f
    src/fortran/realit.f
    src/fortran/dpmul.f
    src/fortran/sfact2.f
    src/fortran/dmpad.f
    src/fortran/nextk.f
    src/fortran/blktit.f
    src/fortran/dimin.f
    src/fortran/newest.f
    src/fortran/dwmpmu.f
    src/fortran/impcnc.f
    src/fortran/wdmpad.f
    src/fortran/wmpcle.f
    src/fortran/quadit.f
    src/fortran/quad.f
    src/fortran/calcsc.f
    src/fortran/writebufsfact.f
    src/fortran/chkvar.f
    src/cpp/rpoly.cpp
    src/cpp/find_polynomial_roots_jenkins_traub.cc
    src/cpp/polynomial.cc
    # libscipolynomials.la (12 TUs)
    sci_gateway/cpp/polynomials_gw.cpp
    sci_gateway/cpp/sci_poly.cpp
    sci_gateway/cpp/sci_varn.cpp
    sci_gateway/cpp/sci_roots.cpp
    sci_gateway/cpp/sci_degree.cpp
    sci_gateway/cpp/sci_coeff.cpp
    sci_gateway/cpp/sci_pppdiv.cpp
    sci_gateway/cpp/sci_simpMode.cpp
    sci_gateway/cpp/sci_polyDisplay.cpp
    sci_gateway/cpp/sci_simp.cpp
    sci_gateway/cpp/sci_sfact.cpp
    sci_gateway/cpp/sci_bezout.cpp
  LANG Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    /opt/homebrew/opt/eigen/include/eigen3)
