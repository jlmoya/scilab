# CMake build for the `dynamic_link` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — dynamic_link ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scidynamic_link-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscidynamic_link-algo
# (6 TUs) + libscidynamic_link (8 gateway TUs).
#
# EXTRA_INCLUDES (libscidynamic_link_la_CPPFLAGS): io,
# functions_manager, string, fileio, threads.

scilab_object_module(dynamic_link
  SOURCES
    # libscidynamic_link-algo.la (6 TUs)
    src/c/dl_genErrorMessage.c
    src/c/GetFunctionByName.c
    src/c/ilib_verbose.c
    src/c/dynamiclibrary_others.c
    src/cpp/dynamic_link.cpp
    src/cpp/addinter.cpp
    # libscidynamic_link.la (8 TUs)
    sci_gateway/cpp/dynamic_link_gw.cpp
    sci_gateway/cpp/sci_link.cpp
    sci_gateway/cpp/sci_c_link.cpp
    sci_gateway/cpp/sci_ulink.cpp
    sci_gateway/cpp/sci_ilib_verbose.cpp
    sci_gateway/cpp/sci_getdynlibext.cpp
    sci_gateway/cpp/sci_addinter.cpp
    sci_gateway/cpp/sci_call.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes)
