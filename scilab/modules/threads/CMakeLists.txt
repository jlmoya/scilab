# CMake build for the `threads` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — threads ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scithreads-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). ONE .la: libscithreads (1 C++ TU).
#
# libscithreads_la_CPPFLAGS is just -Iincludes/ (+ empty AM_CPPFLAGS);
# the helper base is a strict superset — no EXTRA_INCLUDES.

scilab_object_module(threads
  SOURCES
    # libscithreads.la (1 TUs)
    src/cpp/Thread_Wrapper.cpp
  LANG CXX)
