# CMake build for the `string` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — string ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scistring-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscistring-algo (28
# TUs: STRING_C + STRING_CPP) + libscistring (30 gateway TUs).
#
# REPRODUCED BASELINE FOOTGUN, C TUs ONLY (the 1f-a invariant is REPRODUCE, not
# improve): Makefile.am sets BOTH libscistring_la_CPPFLAGS and
# libscistring_la_CFLAGS (bare -I lists, shared by the algo lib). The per-target
# _CFLAGS REPLACES AM_CFLAGS ($(SCI_CFLAGS)) — but per-target _CXXFLAGS is NOT
# set, so C and C++ DIVERGE in the baseline:
#   * C TUs (all 25 under src/c) → -O0, no -fwrapv, no -g, host min-version.
#     VERIFIED: their .libs objects have DW_AT_APPLE_optimized=0 and
#     LC_BUILD_VERSION minos 26.0 (host default — the dropped
#     -mmacosx-version-min).
#   * C++ TUs (33, under src/cpp + sci_gateway/cpp) → kept AM_CXXFLAGS =
#     SCI_CXXFLAGS → -O2, minos 11.0. VERIFIED: e.g. sci_gateway/cpp/
#     sci_strindex.cpp's object has DW_AT_APPLE_optimized set, minos 11.0.
# So the FAITHFUL reproduction is asymmetric: C_FLAGS_OVERRIDE drops the C TUs
# to their real un-optimized shape (flagfacts_check.py DIR_EXPECTED_OVERRIDES
# scopes /modules/string/src/c/), while the C++ gateways keep the standard
# SCI_CXXFLAGS (O2) — matching their baseline. Because the C compile then
# carries NO -Werror (as the baseline C line did not), pcre2_private.c's two
# const-discarding wchar_t* initializations compile clean WITHOUT any
# -Wno-error dial-back — the earlier workaround is removed, this is strictly
# more faithful. The deliberate O2 fix for the C TUs = restore SCI_CFLAGS in the
# Makefile.am + re-baseline, a separate step OUT of 1f-a.
# EXTRA_INCLUDES (union of the two flag lists, their order): PCRE2_CFLAGS'
# keg dir first, then functions (the _CFLAGS-only dir), functions_manager,
# threads, console.

scilab_object_module(string
  SOURCES
    # libscistring-algo.la (28 TUs)
    src/c/convstr.c
    src/c/men_Sutils.c
    src/c/tokens.c
    src/c/strsplit.c
    src/c/partfunction.c
    src/c/cvstr.c
    src/c/getfastcode.c
    src/c/pcre2_private.c
    src/c/isletter.c
    src/c/isdigit.c
    src/c/strsubst.c
    src/c/stristr.c
    src/c/stringsstrrev.c
    src/c/stringsCompare.c
    src/c/isalphanum.c
    src/c/StringConvert.c
    src/c/os_wcsdup.c
    src/c/os_wcstok.c
    src/c/os_wcstod.c
    src/c/scistrtostr.c
    src/c/stringToDouble.c
    src/c/stringToComplex.c
    src/c/complex_array.c
    src/c/isascii.c
    src/c/isnum.c
    src/cpp/stripblanks.cpp
    src/cpp/sort_inter.cpp
    src/cpp/os_wtoi.cpp
    # libscistring.la (30 TUs)
    sci_gateway/cpp/sci_stripblanks.cpp
    sci_gateway/cpp/sci_grep.cpp
    sci_gateway/cpp/string_gw.cpp
    sci_gateway/cpp/sci_regexp.cpp
    sci_gateway/cpp/sci_part.cpp
    sci_gateway/cpp/sci_length.cpp
    sci_gateway/cpp/sci_strindex.cpp
    sci_gateway/cpp/sci_strsubst.cpp
    sci_gateway/cpp/sci_ascii.cpp
    sci_gateway/cpp/sci_strcat.cpp
    sci_gateway/cpp/sci_string.cpp
    sci_gateway/cpp/sci_convstr.cpp
    sci_gateway/cpp/sci_strncpy.cpp
    sci_gateway/cpp/sci_strrchr.cpp
    sci_gateway/cpp/sci_strchr.cpp
    sci_gateway/cpp/sci_strstr.cpp
    sci_gateway/cpp/sci_strrev.cpp
    sci_gateway/cpp/sci_strtod.cpp
    sci_gateway/cpp/sci_tokens.cpp
    sci_gateway/cpp/sci_strcmp.cpp
    sci_gateway/cpp/sci_isletter.cpp
    sci_gateway/cpp/sci_strcspn.cpp
    sci_gateway/cpp/sci_strtok.cpp
    sci_gateway/cpp/sci_strspn.cpp
    sci_gateway/cpp/sci_isalphanum.cpp
    sci_gateway/cpp/sci_isdigit.cpp
    sci_gateway/cpp/sci_isascii.cpp
    sci_gateway/cpp/sci_strsplit.cpp
    sci_gateway/cpp/sci_isnum.cpp
    sci_gateway/cpp/sci_emptystr.cpp
  LANG C CXX
  # C TUs reproduce the footgun (bare -std=gnu23, no SCI_CFLAGS); the 33 C++
  # gateways are untouched by this and keep SCI_CXXFLAGS (O2) — see the header.
  C_FLAGS_OVERRIDE -std=gnu23
  EXTRA_INCLUDES
    /opt/homebrew/Cellar/pcre2/10.47_1/include
    ${SCILAB_SOURCE_DIR}/modules/functions/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes)
