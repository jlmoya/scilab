# CMake build for the `integer` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — integer ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciinteger-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libsciinteger-algo (32
# TUs) + libsciinteger (7 gateway TUs).
#
# EXTRA_INCLUDES (libsciinteger_la_CPPFLAGS): threads, string, console.

scilab_object_module(integer
  SOURCES
    # libsciinteger-algo.la (32 TUs)
    src/c/genconcatcol.c
    src/c/genprod.c
    src/c/genrdiv.c
    src/c/genextrac1.c
    src/c/genextrac2.c
    src/c/gencopy.c
    src/c/gentril.c
    src/c/gencheck.c
    src/c/tpconv.c
    src/c/genscal.c
    src/c/gentriu.c
    src/c/genvmul.c
    src/c/genconcatrow.c
    src/c/geninsert1.c
    src/c/geninsert2.c
    src/c/gendot.c
    src/c/db2int.c
    src/c/genadd.c
    src/c/genset.c
    src/c/gencusum.c
    src/c/gensubst.c
    src/c/genmdsp.c
    src/c/gensum.c
    src/c/genmmul.c
    src/c/genmsum.c
    src/c/genmprod.c
    src/c/genmtran.c
    src/c/genisany.c
    src/c/genmcopy.c
    src/c/gencuprod.c
    src/c/genbitops.c
    src/c/genimpl.c
    # libsciinteger.la (7 TUs)
    src/cpp/DllmainInteger_gw.cpp
    src/cpp/iconvert_converter.cpp
    sci_gateway/cpp/integer_gw.cpp
    sci_gateway/cpp/sci_inttype.cpp
    sci_gateway/cpp/sci_double.cpp
    sci_gateway/cpp/sci_int.cpp
    sci_gateway/cpp/sci_iconvert.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes)
