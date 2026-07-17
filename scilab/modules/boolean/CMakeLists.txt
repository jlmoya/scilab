# CMake build for the `boolean` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — boolean ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciboolean-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libsciboolean-algo (7
# TUs: 2 C + boolean_gw + 4 gateway cpp) + libsciboolean (GATEWAY_C_SOURCES —
# resolves EMPTY on this config, 0 TUs; the .la exists only to LIBADD the algo
# objects).
#
# EXTRA_INCLUDES (libsciboolean_la_CPPFLAGS): functions_manager,
# elementary_functions, console, string, threads.

scilab_object_module(boolean
  SOURCES
    # libsciboolean-algo.la (7 TUs)
    src/c/vect_or.c
    src/c/vect_and.c
    src/cpp/boolean_gw.cpp
    sci_gateway/cpp/sci_bool2s.cpp
    sci_gateway/cpp/sci_find.cpp
    sci_gateway/cpp/sci_and.cpp
    sci_gateway/cpp/sci_or.cpp
    # libsciboolean.la (0 TUs)
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes)
