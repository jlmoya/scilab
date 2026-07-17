# CMake build for the `windows_tools` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — windows_tools ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciwindows_tools-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). ONE .la: libsciwindows_tools (1 C
# TU — the src/nowindows_tools stub; the real Windows sources never compile on
# macOS).
#
# REPRODUCED BASELINE FOOTGUN (the 1f-a invariant is REPRODUCE, not improve):
# libsciwindows_tools_la_CFLAGS is a bare -I list, and per-target _CFLAGS
# REPLACES AM_CFLAGS ($(SCI_CFLAGS)) wholesale — the baseline TU compiled
# unoptimized, without -fwrapv/-g/-DNDEBUG/-mmacosx-version-min, only the CC
# driver's -std=gnu23 -arch arm64 + the -I dirs. VERIFIED on the rebuilt
# baseline object (windows_tools/src/nowindows_tools/.libs/libsciwindows_tools_
# la-nowindows_tools.o): no DWARF (DW_AT_APPLE_optimized=0 → -O0, no -g) and
# LC_BUILD_VERSION minos 26.0 (host default — the dropped -mmacosx-version-min).
# The automake include-list-in-CFLAGS footgun, NOT a workaround; the fold-in
# aggregate parity is codegen-BLIND, so the flag-facts gate is the ONLY guard
# and MUST expect the real -O0 shape (flagfacts_check.py DIR_EXPECTED_OVERRIDES).
# The deliberate O2 fix = restore SCI_CFLAGS in the Makefile.am + re-baseline, a
# separate step OUT of 1f-a. C_FLAGS_OVERRIDE reproduces the compile (CMAKE_OSX_*
# still adds -arch + -mmacosx-version-min=11.0, harmless as above).
# Its _CFLAGS -I dirs are all in the helper base — no EXTRA_INCLUDES.

scilab_object_module(windows_tools
  SOURCES
    # libsciwindows_tools.la (1 TUs)
    src/nowindows_tools/nowindows_tools.c
  LANG C
  C_FLAGS_OVERRIDE -std=gnu23)
