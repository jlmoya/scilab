# CMake build for the `data_structures` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — data_structures ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scidata_structures-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). ONE .la: libscidata_structures (5
# gateway C++ TUs).
#
# EXTRA_INCLUDES (libscidata_structures_la_CPPFLAGS): string, threads.

scilab_object_module(data_structures
  SOURCES
    # libscidata_structures.la (5 TUs)
    sci_gateway/cpp/data_structures_gw.cpp
    sci_gateway/cpp/sci_getfield.cpp
    sci_gateway/cpp/sci_setfield.cpp
    sci_gateway/cpp/sci_rlist.cpp
    sci_gateway/cpp/sci_definedfields.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes)
