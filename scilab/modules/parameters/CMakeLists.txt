# CMake build for the `parameters` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — parameters ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciparameters-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). ONE .la: libsciparameters (1 C
# TU).
#
# REPRODUCED BASELINE FOOTGUN (the 1f-a invariant is REPRODUCE, not improve):
# Makefile.am sets libsciparameters_la_CFLAGS to a bare -I list, and automake's
# per-target _CFLAGS REPLACES AM_CFLAGS ($(SCI_CFLAGS)) wholesale — so the
# baseline TU compiled with NO -O2/-fwrapv/-g1/-DNDEBUG/-mmacosx-version-min,
# only the CC driver's -std=gnu23 -arch arm64 + the -I dirs. VERIFIED on the
# rebuilt baseline object (parameters/src/c/.libs/libsciparameters_la-
# parameters.o): no DWARF at all (DW_AT_APPLE_optimized=0 → -O0, no -g) and
# LC_BUILD_VERSION minos 26.0 (host default — the dropped -mmacosx-version-min).
# This is the automake include-list-in-CFLAGS footgun, NOT an engineered
# workaround; the fold-in aggregate parity (Task 4) is codegen-BLIND (symbol/
# dep/rpath), so the flag-facts gate is the ONLY guard and MUST expect the real
# -O0 shape (flagfacts_check.py DIR_EXPECTED_OVERRIDES) — giving it the standard
# SCI_CFLAGS O2 would silently ship a DIFFERENT app that still passes parity.
# The deliberate O2/fwrapv fix is a separate re-baselined step (restore
# SCI_CFLAGS in the Makefile.am), OUT of 1f-a scope. C_FLAGS_OVERRIDE below
# reproduces the C compile exactly (CMAKE_OSX_* still supplies -arch +
# -mmacosx-version-min=11.0, harmless: the folded object's min-version is set at
# aggregate link time, and 26.0 was only a host artifact of the dropped flag).
# EXTRA_INCLUDES (libsciparameters_la_CFLAGS dirs): string.

scilab_object_module(parameters
  SOURCES
    # libsciparameters.la (1 TUs)
    src/c/parameters.c
  LANG C
  # C_FLAGS_OVERRIDE removed 2026-07-23 (register B10): un-footgunned — this module's
  # C TUs now get the full SCI_CFLAGS default (-O2 -fwrapv -g). The comment(s) above
  # describe the automake _CFLAGS footgun this used to reproduce.
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes)
