# CMake build for the `mexlib` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — mexlib ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scimexlib-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). automake declares THREE noinst
# .la's — libmat/libmex/libmx — with IDENTICAL sources (MEXLIB_CPP_SOURCES) and
# IDENTICAL per-target flags (MEXLIB_FLAGS + AM_CPPFLAGS), so their objects are
# byte-equivalent triplicates. ONLY libmex.la is in modules/Makefile.am's
# ENGINE_LIBS (folds into libscilab); libmat/libmx fold into nothing on macOS.
# This OBJECT lib compiles the 2 TUs ONCE = libmex's objects.
#
# EXTRA_INCLUDES (MEXLIB_FLAGS): call_scilab, elementary_functions,
# string, threads, console.

scilab_object_module(mexlib
  SOURCES
    # libmex.la (2 TUs)
    src/cpp/mexlib.cpp
    src/cpp/sci_gateway.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes)
