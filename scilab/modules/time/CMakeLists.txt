# CMake build for the `time` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — time ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scitime-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscitime-algo (3 C
# TUs) + libscitime (7 gateway TUs).
#
# EXTRA_INCLUDES (libscitime_la_CPPFLAGS): functions_manager, string.

scilab_object_module(time
  SOURCES
    # libscitime-algo.la (3 TUs)
    src/c/realtime.c
    src/c/timer.c
    src/c/getdate.c
    # libscitime.la (7 TUs)
    sci_gateway/c/sci_realtime.c
    sci_gateway/c/sci_timer.c
    sci_gateway/c/sci_calendar.c
    sci_gateway/c/sci_getdate.c
    sci_gateway/cpp/time_gw.cpp
    sci_gateway/cpp/sci_tictoc.cpp
    sci_gateway/cpp/sci_sleep.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes)
