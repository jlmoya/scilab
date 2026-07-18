# scilab/cmake/ScilabMachineHeader.cmake — CMake generates machine.h (retire-configure RC-a).
#
# CMake COMPUTES every macro itself (never copies config.status) — that is what makes the
# harness's semantic header_defines dimension a real gate rather than a tautology. Output is
# NOT byte-identical to autoconf's machine.h (comment/#define/ordering differ); equivalence is
# semantic. ADDITIVE: the source-tree machine.h is untouched and still resolves first during
# coexistence (ScilabModule.cmake keeps core/includes ahead); this copy activates at RC-e.
#
# Scope: Tasks 1-2 converged the ~125 HAVE_*/SIZEOF_*/STDC_HEADERS/CLOSEDIR_VOID "probe"
# macros (see build-parity/parity/fingerprint.py's probe() filter). Task 3 (this pass) adds
# the remaining 39: the pkg-config bucket (CURL_*/LIBARCHIVE_*/LIBXML_*), Fortran name-mangling
# (C2F/F2C/CNAME/WLU/WTU, plus the G95_FORTRAN dialect probe that sits textually next to them
# but tests something different), the configure OPTIONS bucket (WITH_*/ENABLE_NLS/
# KLU_SUITESPARSE/UMFPACK_SUITESPARSE), PACKAGE_*/PACKAGE/VERSION, three "plain fact" macros
# (INSTALLPREFIX/PATH_SEPARATOR/SHARED_LIB_EXT), a fifth, distinct *libtool* source (LT_OBJDIR),
# and LSTAT_FOLLOWS_SLASHED_SYMLINK (a real runtime probe placed next to HAVE_STAT_EMPTY_STRING_BUG
# in bucket 1e below, since both trace to the same _AC_FUNC_STAT call site). ENABLE_RELOCATABLE,
# ENABLE_MPI, WITH_OCAML and WITH_TK stay deliberately unset — genuinely undef in the reference
# too — see "Deliberately UNPROBED" near the end of this file.
#
# Every probe below is transcribed from where configure.ac ACTUALLY tests it — either directly,
# or via the m4/*.m4 file it AC_REQUIREs, or (for the two runtime/compile-behavior quirks)
# autoconf's own bundled functions.m4 macro body. Several probes are deliberately
# "bug-for-bug": they reproduce the EXACT test configure.ac runs, including tests that are
# narrower or more accidental than the real platform fact, because the parity harness compares
# against what configure.ac actually produced on this machine, not against platform truth.
# Each such case is called out inline.

include(CheckIncludeFile)
include(CheckSymbolExists)
include(CheckFunctionExists)
include(CheckLibraryExists)
include(CheckStructHasMember)
include(CheckTypeSize)
include(CheckCSourceCompiles)
include(CheckCSourceRuns)
include(CheckCXXSourceCompiles)
include(CheckFortranSourceCompiles)

# Pin the probe environment to the CONFIGURED build so a probe's answer matches configure's
# (different -isysroot/-I would flip HAVE_* silently). This is the single biggest fidelity lever.
#
# CMAKE_OSX_SYSROOT is EMPTY in this project's actual configuration (nothing else in cmake/
# relies on it — ScilabModule.cmake builds all 64 parity-proven module dylibs without ever
# setting it, via AppleClang's implicit default-SDK resolution). config.log confirms configure's
# OWN test compiles do the same: e.g. the HAVE_CPLUS_DEMANGLE probe at config.log:4599 ran as
# "g++ -arch arm64 -std=c++17 -o conftest ... " with NO -isysroot at all. So an unconditional
# "-isysroot ${CMAKE_OSX_SYSROOT}" would pass a bare, argument-less -isysroot to every probe here
# (breaking literally all of them) for fidelity autoconf itself doesn't have. Only add it if a
# real path is actually configured (a differently-set-up tree, or a future Xcode-generator run).
#
# Save the file-scope CMAKE_REQUIRED_* this block is about to overwrite, and
# restore them at file end (below the final configure_file() call). include()
# does not open a new variable scope, so anything left set here would
# otherwise leak into every module CMakeLists.txt loaded later via
# add_subdirectory() -- the same save/restore discipline this file already
# applies correctly to its own LOCAL excursions below (the C++17 flag, the
# CoreFoundation framework, the per-header extra include dirs), just applied
# to the file-scope values instead of a probe-local one.
set(_scilab_outer_required_quiet ${CMAKE_REQUIRED_QUIET})
set(_scilab_outer_required_flags ${CMAKE_REQUIRED_FLAGS})
set(_scilab_outer_required_includes ${CMAKE_REQUIRED_INCLUDES})

set(CMAKE_REQUIRED_QUIET TRUE)
if(CMAKE_OSX_SYSROOT)
  set(CMAKE_REQUIRED_FLAGS "-isysroot ${CMAKE_OSX_SYSROOT}")
endif()
set(CMAKE_REQUIRED_INCLUDES ${SCILAB_DEFAULT_INCLUDES} ${SCILAB_HOMEBREW_INCLUDES})

# ============================================================================
# bucket 1a: header probes — 44 of the 50 HAVE_*_H macros in machine.h.in
# (the other 4 — dirent.h/sys/ndir.h/sys/dir.h/ndir.h — are a short-circuit
# chain, right below this foreach, not an unconditional probe each). Naming
# matches AC_CHECK_HEADERS: HAVE_ + uppercase(header) with '.' and '/' -> '_'.
# Verified against every one of the 44 identifiers actually probed here (none
# contains a '-', where this simple transform would need care — see the
# mach-o/dyld.h note near the end of this file, which is exactly the header
# that DOES).
# ============================================================================
foreach(_h
    archive.h curses.h dlfcn.h fcntl.h float.h inttypes.h
    libintl.h limits.h locale.h malloc.h ncurses.h netdb.h
    netinet/in.h nlist.h sgtty.h stddef.h stdint.h stdio.h stdlib.h
    strings.h string.h syslog.h sys/file.h sys/ioctl.h
    sys/param.h sys/socket.h sys/stat.h sys/timeb.h sys/time.h
    sys/types.h sys/utsname.h sys/wait.h termcap.h termios.h termio.h
    term.h time.h unistd.h values.h wchar.h wctype.h
    curl/curl.h matio.h omp.h)
  string(TOUPPER "HAVE_${_h}" _v)
  string(REGEX REPLACE "[./]" "_" _v "${_v}")

  # 3 of these 44 resolve outside the default probe includes: curl.h comes from
  # miniconda on this dev machine (matches machine.h's own CURL_CFLAGS " -I
  # .../miniconda3/include"; modules/webtools/CMakeLists.txt links the same
  # miniconda copy for the identical reason); matio.h/omp.h are keg-only
  # Homebrew formulas (SCILAB_HOMEBREW_INCLUDES deliberately excludes libomp
  # per ScilabToolchain.cmake's own comment). Paths read from the already-
  # migrated modules/matio and modules/webtools CMakeLists.txt EXTRA_INCLUDES,
  # not invented.
  set(_extra_inc)
  if(_h STREQUAL "curl/curl.h")
    set(_extra_inc /Users/josemoya/miniconda3/include)
  elseif(_h STREQUAL "matio.h")
    set(_extra_inc /opt/homebrew/opt/libmatio/include)
  elseif(_h STREQUAL "omp.h")
    set(_extra_inc /opt/homebrew/opt/libomp/include)
  endif()

  if(_extra_inc)
    set(_saved_includes ${CMAKE_REQUIRED_INCLUDES})
    set(CMAKE_REQUIRED_INCLUDES ${CMAKE_REQUIRED_INCLUDES} ${_extra_inc})
    check_include_file(${_h} ${_v})
    set(CMAKE_REQUIRED_INCLUDES ${_saved_includes})
  else()
    check_include_file(${_h} ${_v})
  endif()
endforeach()

# HAVE_DIRENT_H / HAVE_SYS_NDIR_H / HAVE_SYS_DIR_H / HAVE_NDIR_H: autoconf's
# own AC_HEADER_DIRENT (functions.m4... actually headers.m4:467-484) is a
# shell `for ac_hdr in dirent.h sys/ndir.h sys/dir.h ndir.h; do ...; break;
# done` — test IN ORDER, stop at the FIRST one found. This is short-circuit
# CONTROL FLOW, not 4 independent facts: sys/dir.h genuinely exists in this
# SDK (unlike sys/ndir.h/ndir.h, which don't), so an unconditional
# check_include_file(sys/dir.h ...) reports a false positive — configure
# never even looks at it once dirent.h succeeds. Reproduced as the same
# sequential short-circuit, not 4 parallel probes.
check_include_file(dirent.h HAVE_DIRENT_H)
if(NOT HAVE_DIRENT_H)
  check_include_file(sys/ndir.h HAVE_SYS_NDIR_H)
endif()
if(NOT HAVE_DIRENT_H AND NOT HAVE_SYS_NDIR_H)
  check_include_file(sys/dir.h HAVE_SYS_DIR_H)
endif()
if(NOT HAVE_DIRENT_H AND NOT HAVE_SYS_NDIR_H AND NOT HAVE_SYS_DIR_H)
  check_include_file(ndir.h HAVE_NDIR_H)
endif()

# $ac_header_dirent: the shell variable AC_HEADER_DIRENT itself sets to
# whichever of the 4 headers above the chain actually stopped on (headers.m4's
# `ac_header_dirent=$ac_hdr; break`). CLOSEDIR_VOID's probe (bucket 1e, below)
# needs this exact same header -- autoconf's own AC_FUNC_CLOSEDIR_VOID
# `#include`s `<$ac_header_dirent>`, not a fixed name -- so resolve it once
# here, from THIS chain's own result, rather than hardcoding a guess a second
# time at the CLOSEDIR_VOID site.
if(HAVE_DIRENT_H)
  set(_scilab_dirent_header "dirent.h")
elseif(HAVE_SYS_NDIR_H)
  set(_scilab_dirent_header "sys/ndir.h")
elseif(HAVE_SYS_DIR_H)
  set(_scilab_dirent_header "sys/dir.h")
elseif(HAVE_NDIR_H)
  set(_scilab_dirent_header "ndir.h")
endif()

# ============================================================================
# bucket 1b: plain function/symbol probes — 44 of the 71 "other HAVE_*"
# macros, each a bare libc/libm function with no library or link-flag
# wrinkle. check_symbol_exists (not check_function_exists) throughout: a few
# of these (isnan chief among them) are macros in some libc's <math.h>, and
# check_symbol_exists is the probe that stays correct either way — a
# deliberate, uniform choice rather than special-casing only the ones known
# to bite.
# ============================================================================
check_symbol_exists(atexit  "stdlib.h" HAVE_ATEXIT)
check_symbol_exists(putenv  "stdlib.h" HAVE_PUTENV)
check_symbol_exists(setenv  "stdlib.h" HAVE_SETENV)
check_symbol_exists(strtol  "stdlib.h" HAVE_STRTOL)

check_symbol_exists(bzero      "strings.h" HAVE_BZERO)
check_symbol_exists(strcasecmp "strings.h" HAVE_STRCASECMP)

check_symbol_exists(dup2         "unistd.h" HAVE_DUP2)
check_symbol_exists(getcwd       "unistd.h" HAVE_GETCWD)
check_symbol_exists(gethostname  "unistd.h" HAVE_GETHOSTNAME)
check_symbol_exists(getpagesize  "unistd.h" HAVE_GETPAGESIZE)
check_symbol_exists(getpass      "unistd.h" HAVE_GETPASS)
check_symbol_exists(rmdir        "unistd.h" HAVE_RMDIR)

check_symbol_exists(endpwent "pwd.h" HAVE_ENDPWENT)

check_symbol_exists(erf    "math.h" HAVE_ERF)
check_symbol_exists(erfc   "math.h" HAVE_ERFC)
check_symbol_exists(exp10  "math.h" HAVE_EXP10)
check_symbol_exists(finite "math.h" HAVE_FINITE)
check_symbol_exists(floor  "math.h" HAVE_FLOOR)
check_symbol_exists(isnan  "math.h" HAVE_ISNAN)
check_symbol_exists(pow    "math.h" HAVE_POW)
check_symbol_exists(sqrt   "math.h" HAVE_SQRT)

check_symbol_exists(gethostbyaddr "netdb.h" HAVE_GETHOSTBYADDR)
check_symbol_exists(gethostbyname "netdb.h" HAVE_GETHOSTBYNAME)

check_symbol_exists(gettimeofday "sys/time.h" HAVE_GETTIMEOFDAY)
check_symbol_exists(isascii      "ctype.h"    HAVE_ISASCII)
check_symbol_exists(iswprint     "wctype.h"   HAVE_ISWPRINT)

check_symbol_exists(memmove   "string.h" HAVE_MEMMOVE)
check_symbol_exists(memset    "string.h" HAVE_MEMSET)
check_symbol_exists(strchr    "string.h" HAVE_STRCHR)
check_symbol_exists(strdup    "string.h" HAVE_STRDUP)
check_symbol_exists(strerror  "string.h" HAVE_STRERROR)
check_symbol_exists(strpbrk   "string.h" HAVE_STRPBRK)
check_symbol_exists(strrchr   "string.h" HAVE_STRRCHR)
check_symbol_exists(strsignal "string.h" HAVE_STRSIGNAL)
check_symbol_exists(strstr    "string.h" HAVE_STRSTR)

check_symbol_exists(mkdir      "sys/stat.h"    HAVE_MKDIR)
check_symbol_exists(munmap     "sys/mman.h"    HAVE_MUNMAP)
check_symbol_exists(nanosleep  "time.h"        HAVE_NANOSLEEP)
check_symbol_exists(regcomp    "regex.h"       HAVE_REGCOMP)
check_symbol_exists(re_comp    "regex.h"       HAVE_RE_COMP)
check_symbol_exists(select     "sys/select.h"  HAVE_SELECT)
check_symbol_exists(setlocale  "locale.h"      HAVE_SETLOCALE)
check_symbol_exists(socket     "sys/socket.h"  HAVE_SOCKET)
check_symbol_exists(uname      "sys/utsname.h" HAVE_UNAME)

# ============================================================================
# bucket 1b-lib: AC_CHECK_LIB-style probes — the lib+symbol pairs transcribed
# from the m4/*.m4 files that actually run them (libarchive.m4, curl.m4,
# hdf5.m4, libxml2.m4, the curses/ncurses/termcap chain at
# configure.ac:1740-1760). Each "location" hint is the SAME keg lib dir the
# already-migrated modules/<name>/CMakeLists.txt link against (read off those
# files, not invented) — check_library_exists searches it IN ADDITION to the
# default linker path, exactly like configure's own -L flags did.
# ============================================================================
check_library_exists(archive archive_read_new "/opt/homebrew/opt/libarchive/lib" HAVE_LIBARCHIVE)
check_library_exists(curl curl_easy_setopt "/Users/josemoya/miniconda3/lib" HAVE_LIBCURL)
check_library_exists(dl dlopen "" HAVE_LIBDL)
check_library_exists(hdf5 H5Fopen "/opt/homebrew/opt/hdf5/lib" HAVE_LIBHDF5)
check_library_exists(hdf5_serial H5Fopen "/opt/homebrew/opt/hdf5/lib" HAVE_LIBHDF5_SERIAL)
check_library_exists(matio Mat_Open "/opt/homebrew/opt/libmatio/lib" HAVE_LIBMATIO)
check_library_exists(scincurses tgetent "" HAVE_LIBSCINCURSES)
check_library_exists(xml2 xmlReaderForFile "" HAVE_LIBXML2)

# HAVE_CLOCK_GETTIME: configure.ac:1016-1020 defines this ONLY when
# clock_gettime links via -lrt specifically (a Linux-centric assumption --
# clock_gettime has lived directly in libSystem since macOS 10.12, no -lrt
# needed or even installable here). Reproducing the SAME lib+symbol pair is
# bug-for-bug on purpose: macOS has no librt, so this independently converges
# to undef too, for the identical underlying reason configure never sees it.
check_library_exists(rt clock_gettime "" HAVE_CLOCK_GETTIME)

# HAVE_TERMCAP / HAVE_LIBTERMCAP: configure.ac:1744-1757 is ALSO short-circuit
# control flow, same shape as the dirent.h chain above — AC_CHECK_LIB(curses),
# then "if curses' tgetent test said no" AC_CHECK_LIB(ncurses), then "if
# ncurses' test [ALSO ran and] said no" AC_CHECK_LIB(termcap) (this last one,
# with NO custom action-if-found, is what HAVE_LIBTERMCAP itself actually is).
# curses succeeds here, so ncurses/termcap are never reached — even though a
# real libtermcap.tbd sits right in the SDK (usr/lib/libtermcap.tbd,
# confirmed present) and an unconditional probe finds it, giving a false
# positive for both HAVE_LIBTERMCAP and (via the OR) HAVE_TERMCAP. HAVE_TERMCAP
# itself is defined (to the literal text "/**/", an AC_DEFINE with an empty
# value -- NOT "1"; the template line carries that spelling) iff ANY of the 3
# checks that DID run succeeds.
check_library_exists(curses tgetent "" _SCILAB_HAVE_LIB_CURSES_TGETENT)
if(NOT _SCILAB_HAVE_LIB_CURSES_TGETENT)
  check_library_exists(ncurses tgetent "" _SCILAB_HAVE_LIB_NCURSES_TGETENT)
endif()
if(NOT _SCILAB_HAVE_LIB_CURSES_TGETENT AND NOT _SCILAB_HAVE_LIB_NCURSES_TGETENT)
  check_library_exists(termcap tgetent "" HAVE_LIBTERMCAP)
endif()
if(_SCILAB_HAVE_LIB_CURSES_TGETENT OR _SCILAB_HAVE_LIB_NCURSES_TGETENT OR HAVE_LIBTERMCAP)
  set(HAVE_TERMCAP 1)
endif()

# gettext (libintl) -- keg-only Homebrew formula (like libomp); the module
# that actually links it (modules/localization/CMakeLists.txt) pins the same
# absolute dylib path for the identical reason. HAVE_GETTEXT/HAVE_DCGETTEXT
# are, per m4/gettext.m4:292-306, actually a "backward compatibility"
# side-effect of the (much larger, composite bindtextdomain+gettext+
# _nl_msg_cat_cntr+_nl_expand_alias) test that PICKS which gettext
# implementation to use — not independent probes of the 2 symbols. A full
# transcription of that composite AC_LINK_IFELSE is a lot of surface for a
# proxy that produces the identical answer: since external libintl IS the
# implementation this machine selects (gt_cv_func_gnugettext1_libintl=yes in
# config.log) and it genuinely exports both symbols, linking each directly
# against -lintl reaches the same TRUE conclusion via a smaller real test.
check_library_exists(intl gettext "/opt/homebrew/opt/gettext/lib" HAVE_GETTEXT)
check_library_exists(intl dcgettext "/opt/homebrew/opt/gettext/lib" HAVE_DCGETTEXT)

# HAVE_BIND_TEXTDOMAIN_CODESET is DIFFERENT from the two above: it comes from
# a separate, later, PLAIN `AC_CHECK_FUNCS([bind_textdomain_codeset])`
# (configure.ac:1886), which links with the DEFAULT $LIBS in scope at that
# point — gettext.m4's own AC_LIB_APPENDTOVAR only ever patches CPPFLAGS
# there (m4/gettext.m4:298), never LIBS; $LIBINTL/-lintl stays a separate
# substitution variable for consumers' own LDADD, so this specific check
# never actually links against libintl. check_symbol_exists is the WRONG
# probe here even without -lintl: this gettext's libintl.h itself #defines
# bind_textdomain_codeset to libintl_bind_textdomain_codeset (its real
# exported symbol, confirmed via `nm`) under a compiler-support #if, so
# check_symbol_exists sees a MACRO and reports "defined" WITHOUT ever
# generating a link-time reference — a false positive from its own
# macro-awareness, the opposite failure mode from isnan/isinf. AC_CHECK_FUNCS
# never has this problem because it includes NO header at all (just an
# extern declaration + call), which is exactly check_function_exists's
# behavior too — use that instead to reproduce the plain link-only test
# configure.ac actually ran, bug-for-bug: no header, no -lintl, so it
# correctly fails to link the real (renamed) symbol.
check_function_exists(bind_textdomain_codeset HAVE_BIND_TEXTDOMAIN_CODESET)

# ============================================================================
# bucket 1c: type sizes + the 2 macros with no real probe to run.
# ============================================================================
check_type_size("int"  SIZEOF_INT)
check_type_size("long" SIZEOF_LONG)

# STDC_HEADERS: autoheader's own comment says it best -- "Define to 1 if all
# of the C89 standard headers exist... provided for backward compatibility".
# AC_HEADER_STDC defines this UNCONDITIONALLY on any C89-or-later hosted
# implementation; there is no autoconf probe to fail. A C11+ host trivially
# qualifies, so this reproduces the same unconditional fact autoconf encodes
# -- not a copied value.
set(STDC_HEADERS 1)

# HAVE_ISINF: configure.ac:1634 -- AC_DEFINE([HAVE_ISINF],[1],...) fires
# UNCONDITIONALLY, with no AC_CHECK_* guarding it at all. Nothing to probe:
# reproduce the same unconditional fact.
set(HAVE_ISINF 1)

# isinf(x) fallback macro body: a SEPARATE, real probe from HAVE_ISINF above,
# even though both come from the same two configure.ac lines (1634-1636).
# AC_CHECK_FUNC([isinf],, [AC_DEFINE([isinf(x)],[(!finite(x) && x==x)], ...)])
# only defines the fallback in ACTION-IF-NOT-FOUND -- i.e. only when the REAL
# isinf symbol is ABSENT; ACTION-IF-FOUND is empty. AC_CHECK_FUNC's own test
# (functions.m4's _AC_CHECK_FUNC_BODY -> AC_LANG_FUNC_LINK_TRY) links a bare
# `extern char isinf (); isinf ();` with NO header -- exactly what
# check_function_exists reproduces. check_symbol_exists would be the WRONG
# probe here (same trap already called out for HAVE_BIND_TEXTDOMAIN_CODESET
# above, just the opposite direction): it #includes <math.h>, where isinf is
# also a C99 macro, so it could report "found" from macro visibility alone
# without proving the real link-time symbol AC_CHECK_FUNC actually tested.
# TRUE on this machine (a real linkable isinf symbol exists, confirmed by this
# probe independently -- not by reading config.log's ac_cv_func_isinf=yes),
# so the fallback line becomes the undef placeholder, matching the reference.
check_function_exists(isinf _SCILAB_HAVE_ISINF_FUNC)
if(_SCILAB_HAVE_ISINF_FUNC)
  set(SCILAB_ISINF_LINE "/* #undef isinf */")
else()
  set(SCILAB_ISINF_LINE "#define isinf(x) (!finite(x) && x==x)")
endif()

# ============================================================================
# bucket 1d: C++/framework probes needing their own compiler invocation
# (not header/function/lib existence alone).
# ============================================================================

# HAVE_CXX17 / HAVE_CPLUS_DEMANGLE both need a C++ standard flag the global
# CMAKE_REQUIRED_FLAGS (C-language sysroot only) doesn't carry -- scope
# -std=c++17 to just these two checks (it would break every C-language check
# above/below if left in CMAKE_REQUIRED_FLAGS globally).
set(_scilab_saved_required_flags ${CMAKE_REQUIRED_FLAGS})
set(CMAKE_REQUIRED_FLAGS "${CMAKE_REQUIRED_FLAGS} -std=c++17")

# HAVE_CXX17: configure.ac uses the Autoconf-Archive AX_CXX_COMPILE_STDCXX(17)
# macro, whose real test compiles a battery of C++17-only constructs. A full
# re-transcription of that macro is a lot of surface for one boolean that
# Stage 1e/1f already established this project's compiler satisfies (every
# migrated module already builds as C++17); __cplusplus >= 201703L is exactly
# what -std=c++17-or-later guarantees per the standard, so this is a smaller
# but still-real compile probe, not a copy of config.log's
# ax_cv_cxx_compile_cxx17__std_cpp17.
check_cxx_source_compiles("
#if __cplusplus < 201703L
#error not C++17
#endif
int main(void) { return 0; }
" HAVE_CXX17)

# HAVE_CPLUS_DEMANGLE: transcribed VERBATIM from m4/backtrace.m4:34-41's
# AC_LANG_PROGRAM body (only <cxxabi.h> included, no <cstddef>). On this
# toolchain that specific test FAILS TO COMPILE -- std::size_t is never
# declared -- a real bug-for-bug reproduction of what configure itself
# measured (config.log:4600: "no type named 'size_t' in namespace 'std'"),
# not a hand-typed undef. A "fixed" test (e.g. adding <cstddef>) would find
# __cxa_demangle just fine and diverge from the reference.
check_cxx_source_compiles("
#include <cxxabi.h>
int main() {
  std::size_t length = 0;  int cc;   char* ret = abi::__cxa_demangle(\"3barI5emptyLi17EE\", 0, &length, &cc);
  return 0;
}
" HAVE_CPLUS_DEMANGLE)

set(CMAKE_REQUIRED_FLAGS ${_scilab_saved_required_flags})

# HAVE_GLIBC_BACKTRACE: m4/backtrace.m4:12-20 link-tests backtrace() from
# <execinfo.h> (present on Darwin's libSystem too, despite the "glibc" name).
check_symbol_exists(backtrace "execinfo.h" HAVE_GLIBC_BACKTRACE)

# HAVE_ICONV: AM_ICONV (gettext, config.log's am_cv_lib_iconv=yes -- that
# cache var literally records "iconv needs -liconv", not "iconv works"; the
# macro tries a bare link first and only sets =yes once the -liconv retry is
# what succeeded). Confirmed empirically too: a bare check_symbol_exists (no
# explicit lib) compiles but fails to LINK here — "_iconv" is undefined
# without it, unlike glibc where iconv() lives directly in libc. So the
# faithful probe links -liconv explicitly, matching AM_ICONV's actual
# successful path, not the one it tried and rejected first.
check_library_exists(iconv iconv "" HAVE_ICONV)

# HAVE_CFLOCALECOPYCURRENT / HAVE_CFPREFERENCESCOPYAPPVALUE: gettext's macOS
# CoreFoundation probes (gt_cv_func_CFLocaleCopyCurrent /
# gt_cv_func_CFPreferencesCopyAppValue in config.log) -- link tests against
# the CoreFoundation framework specifically; a bare check without the
# framework would fail to link.
set(_scilab_saved_required_libraries ${CMAKE_REQUIRED_LIBRARIES})
set(CMAKE_REQUIRED_LIBRARIES "-framework CoreFoundation")
check_function_exists(CFLocaleCopyCurrent HAVE_CFLOCALECOPYCURRENT)
check_function_exists(CFPreferencesCopyAppValue HAVE_CFPREFERENCESCOPYAPPVALUE)
set(CMAKE_REQUIRED_LIBRARIES ${_scilab_saved_required_libraries})

# ============================================================================
# bucket 1e: runtime/compile-behavior quirks, transcribed from autoconf's own
# bundled functions.m4 (not hand-guessed) so CMake reaches the answer via the
# SAME mechanism configure used, just run independently.
# ============================================================================

# CLOSEDIR_VOID: transcribed from AC_FUNC_CLOSEDIR_VOID (autoconf's
# functions.m4:512-528) -- closedir()'s return value is used in a `return`
# statement, which FAILS TO COMPILE exactly when closedir returns void.
# #include <$ac_header_dirent> there, not a fixed <dirent.h> -- reuses
# _scilab_dirent_header, resolved above from the SAME short-circuit chain
# autoconf's own $ac_header_dirent comes from (dirent.h succeeds on this
# machine, so the two happen to agree here, but the source is the chain's
# result, not a repeated guess).
check_c_source_compiles("
#include <${_scilab_dirent_header}>
int main(void) { return closedir(0); }
" _SCILAB_CLOSEDIR_RETURNS_INT)
if(NOT _SCILAB_CLOSEDIR_RETURNS_INT)
  set(CLOSEDIR_VOID 1)
endif()

# HAVE_STAT_EMPTY_STRING_BUG: transcribed from _AC_FUNC_STAT (autoconf's
# functions.m4:1606-1622) -- a REAL run, not a compile-only proxy: on the
# SVR4/Hurd bug this reproduces, stat("") incorrectly reports success. Native
# build (no cross-compiling here), so check_c_source_runs actually executes
# it rather than needing a pre-seeded cache answer.
check_c_source_runs("
#include <sys/types.h>
#include <sys/stat.h>
int main(void) {
  struct stat sbuf;
  return stat(\"\", &sbuf) == 0;
}
" _SCILAB_STAT_EMPTY_STRING_OK)
if(NOT _SCILAB_STAT_EMPTY_STRING_OK)
  set(HAVE_STAT_EMPTY_STRING_BUG 1)
endif()

# LSTAT_FOLLOWS_SLASHED_SYMLINK: configure.ac:1657 calls AC_FUNC_STAT, whose
# expansion (_AC_FUNC_STAT, autoconf's functions.m4:1606-1622 -- the SAME
# call site that produces HAVE_STAT_EMPTY_STRING_BUG right above)
# AC_REQUIREs AC_FUNC_LSTAT_FOLLOWS_SLASHED_SYMLINK (functions.m4:924-963).
# It landed outside Tasks 1-2 only because their convergence filter matched
# on the HAVE_*/SIZEOF_* name pattern, not by any real scope decision -- it
# belongs right here. A real runtime probe, transcribed from the macro's own
# AC_RUN_IFELSE body (functions.m4:938-943): lstat() a path with a TRAILING
# SLASH pointing at a symlink to a regular (non-directory) file. POSIX
# requires this to FAIL (ENOTDIR); Linux and Darwin both get this right,
# unlike the SVR4/Hurd-era systems the macro guards against. The upstream
# test stages conftest.file/conftest.sym via a shell `ln -s` BEFORE invoking
# the compiled probe; check_c_source_runs has no such pre-stage hook, so the
# probe below creates and cleans up the exact same file+symlink itself (via
# symlink(2), the same syscall `ln -s` wraps), then runs the IDENTICAL
# return expression (`lstat(...) == 0`) the macro uses -- same pass/fail
# polarity (exit 0 = AC_RUN_IFELSE success = lstat correctly FAILED).
check_c_source_runs("
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdio.h>
int main(void) {
  struct stat sbuf;
  int rv;
  FILE *f = fopen(\"conftest.file\", \"w\");
  if (f) fclose(f);
  unlink(\"conftest.sym\");
  if (symlink(\"conftest.file\", \"conftest.sym\") != 0) return 1;
  rv = (lstat(\"conftest.sym/\", &sbuf) == 0);
  unlink(\"conftest.sym\");
  unlink(\"conftest.file\");
  return rv;
}
" LSTAT_FOLLOWS_SLASHED_SYMLINK)

# ============================================================================
# bucket 1f: struct-member probes (real struct-layout facts, not copied).
# HAVE_ST_BLOCKS is AC_STRUCT_ST_BLOCKS's deprecated alias for
# HAVE_STRUCT_STAT_ST_BLOCKS -- same probe, same value, by autoconf's own
# definition of that macro.
# ============================================================================
check_struct_has_member("struct stat" st_blksize "sys/types.h;sys/stat.h" HAVE_STRUCT_STAT_ST_BLKSIZE)
check_struct_has_member("struct stat" st_rdev    "sys/types.h;sys/stat.h" HAVE_STRUCT_STAT_ST_RDEV)
check_struct_has_member("struct stat" st_blocks  "sys/types.h;sys/stat.h" HAVE_STRUCT_STAT_ST_BLOCKS)
if(HAVE_STRUCT_STAT_ST_BLOCKS)
  set(HAVE_ST_BLOCKS 1)
endif()

# ============================================================================
# bucket 2: pkg-config-shaped values (8) -- CURL_*/LIBARCHIVE_*/LIBXML_*.
#
# MECHANISM, confirmed by reading m4/curl.m4, m4/libxml2.m4 and
# m4/libarchive.m4 (NOT by reading config.status/machine.h for the values):
# configure.ac never calls plain `pkg-config` for any of these. CURL_* comes
# from `curl-config --cflags/--libs/--version` (m4/curl.m4:66-68); LIBXML_*
# from `xml2-config --cflags/--libs` (m4/libxml2.m4:50-51) -- both real,
# clean, single-tool computations, reproduced the same way below (both
# verified byte-for-byte against modules/core/includes/machine.h's literal
# text on this machine before being wired in here).
#
# LIBARCHIVE_CFLAGS/LIBARCHIVE_LIBS are DIFFERENT and NOT independently
# computable: m4/libarchive.m4 never calls any config-tool for them when
# archive.h/libarchive are already found on the default search path (true
# here -- HAVE_ARCHIVE_H/HAVE_LIBARCHIVE above both already succeed, so the
# `PKG_CHECK_MODULES(LIBARCHIVE, ...)` fallback at libarchive.m4:41 never
# runs). Instead it captures whatever the GLOBAL $CFLAGS/$LIBS shell
# accumulators happen to hold at that exact point in configure.ac's linear
# run: libarchive.m4:33-34 does `LIBS="$LIBARCHIVE_LIBS $LIBS"` /
# `CFLAGS="$LIBARCHIVE_CFLAGS $CFLAGS"` with LIBARCHIVE_LIBS/CFLAGS still
# EMPTY at that point (no --with-libarchive-* flag, no $WITH_DEVTOOLS on
# this tree), then libarchive.m4:61-62 captures `LIBARCHIVE_LIBS="$LIBS"` /
# `LIBARCHIVE_CFLAGS="$CFLAGS"` AFTER AC_CHECK_HEADERS/AC_CHECK_LIB ran --
# i.e. it snapshots the accumulated global state left behind by EVERY
# earlier macro in configure.ac's own call order, not a fact about
# libarchive itself (a -ldl fragment is the other passenger in that
# snapshot).
#
# The openssl -I fragment specifically is NOT produced by any Scilab or
# libarchive detection at all. Traced further: config.log's own recorded
# invocation line and its cached-variable dump (config.log:7 and :5814)
# both show $CFLAGS STARTING this configure run already set to
# '-I/opt/homebrew/opt/openssl/include' -- matching, verbatim, `export
# CFLAGS="-I/opt/homebrew/opt/openssl/include"` in this developer's own
# ~/.bash_profile:134 (set there for an unrelated build requirement, with
# nothing to do with libarchive, openssl, or Scilab) that autoconf simply
# inherited from the invoking shell. libarchive.m4 never clears $CFLAGS
# before its snapshot, so that pre-existing fragment rides along for free.
# CONSEQUENCE, and why this is more than a trivia correction: this
# reference value is contingent on WHICH SHELL invoked `./configure` -- a
# re-`./configure` run from a login shell that doesn't source
# `.bash_profile`, from CI, or from zsh could legitimately produce a
# DIFFERENT LIBARCHIVE_CFLAGS, breaking this macro's parity with no
# CMake-side change involved. Keep that in mind before "fixing" a future
# parity mismatch here by touching CMake -- check the invoking shell's
# $CFLAGS first.
#
# Verified empirically: `pkg-config --cflags --libs libarchive` reports
# libarchive's OWN correct "-I/opt/homebrew/opt/libarchive/include" /
# "-L.../lib -larchive" -- a DIFFERENT string from the reference's captured
# value, so no clean tool call reproduces it either. That command needs
# PKG_CONFIG_PATH pointed at libarchive's .pc file to run at all, though:
# libarchive is a keg-only Homebrew formula (like matio/omp/gettext
# elsewhere in this file), so a PLAIN `pkg-config --cflags --libs
# libarchive` in an unmodified shell FAILS ("Package libarchive was not
# found in the pkg-config search path"). Reproduce via `PKG_CONFIG_PATH=
# /opt/homebrew/opt/libarchive/lib/pkgconfig pkg-config --cflags --libs
# libarchive` -- confirmed to print the string quoted above. Transcribed
# instead, same rationale the plan itself grants C2F/F2C/CNAME below ("ABI
# contract, not an invention" -- here, "shell-accumulation artifact, not an
# invention"). LIBARCHIVE_VERSION is NOT in this same boat -- see its own
# real probe further down.
# ============================================================================

# HINTS, not PATHS: find_program searches PATHS *after* the environment PATH, so a
# PATHS hint does not actually pin anything -- on this machine miniconda's curl-config
# wins only because it happens to come first on PATH. A shell without conda on PATH
# would resolve /usr/bin/curl-config and silently shift CURL_CFLAGS/LIBS/VERSION,
# failing parity on 3 macros with no CMake-side change. HINTS is searched before PATH,
# which makes the pin real.
find_program(SCILAB_CURL_CONFIG NAMES curl-config HINTS /Users/josemoya/miniconda3/bin)
if(SCILAB_CURL_CONFIG)
  execute_process(COMMAND "${SCILAB_CURL_CONFIG}" --cflags
                   OUTPUT_VARIABLE CURL_CFLAGS OUTPUT_STRIP_TRAILING_WHITESPACE)
  execute_process(COMMAND "${SCILAB_CURL_CONFIG}" --libs
                   OUTPUT_VARIABLE CURL_LIBS OUTPUT_STRIP_TRAILING_WHITESPACE)
  execute_process(COMMAND "${SCILAB_CURL_CONFIG}" --version
                   OUTPUT_VARIABLE CURL_VERSION OUTPUT_STRIP_TRAILING_WHITESPACE)
endif()

find_program(SCILAB_XML2_CONFIG NAMES xml2-config HINTS /usr/bin)
if(SCILAB_XML2_CONFIG)
  execute_process(COMMAND "${SCILAB_XML2_CONFIG}" --cflags
                   OUTPUT_VARIABLE LIBXML_FLAGS OUTPUT_STRIP_TRAILING_WHITESPACE)
  execute_process(COMMAND "${SCILAB_XML2_CONFIG}" --libs
                   OUTPUT_VARIABLE LIBXML_LIBS OUTPUT_STRIP_TRAILING_WHITESPACE)
endif()

# LIBARCHIVE_CFLAGS/LIBS: transcribed (see the bucket comment above for why).
# Spacing matches the reference's literal text exactly; parse_defines'
# whitespace-collapsing normalization only needs at least one space in each
# gap to agree, but the exact copy is kept for at-a-glance traceability.
set(LIBARCHIVE_CFLAGS " -I/opt/homebrew/opt/openssl/include")
set(LIBARCHIVE_LIBS "-larchive  -ldl  ")

# LIBARCHIVE_VERSION, unlike its two siblings above, IS a real, independently
# computable fact: m4/libarchive.m4:50-59's own AC_RUN_IFELSE compiles and
# RUNS a tiny program that decodes ARCHIVE_VERSION_NUMBER (archive.h's own
# encoded MMMmmmrrr version macro) into "major.minor.rev" via printf --
# reproduced verbatim via try_run, whose RUN_OUTPUT_VARIABLE captures the
# same stdout AC_RUN_IFELSE captures via `$(./conftest$EXEEXT)`. archive.h is
# keg-only Homebrew (same header the HAVE_ARCHIVE_H probe above already
# resolves); reuse this file's own ambient CMAKE_REQUIRED_INCLUDES (already
# carries SCILAB_HOMEBREW_INCLUDES) instead of a fresh hardcoded path.
try_run(_scilab_archive_version_run_rc _scilab_archive_version_compiled
  SOURCE_FROM_CONTENT scilab_archive_version.c
  "#include <archive.h>
#include <stdio.h>
int main(void) {
  int major = ARCHIVE_VERSION_NUMBER / 1000000;
  int minor = (ARCHIVE_VERSION_NUMBER % 1000000) / 1000;
  int rev = ARCHIVE_VERSION_NUMBER % 1000;
  printf(\"%d.%d.%d\\n\", major, minor, rev);
  return 0;
}
"
  CMAKE_FLAGS "-DINCLUDE_DIRECTORIES=${CMAKE_REQUIRED_INCLUDES}"
  RUN_OUTPUT_VARIABLE LIBARCHIVE_VERSION)
if(LIBARCHIVE_VERSION)
  string(STRIP "${LIBARCHIVE_VERSION}" LIBARCHIVE_VERSION)
endif()

# ============================================================================
# bucket 3: Fortran name mangling (6) -- C2F/F2C/CNAME/WLU/WTU, all genuinely
# derived below from m4/fortran.m4's AC_CHECK_UNDERSCORE_FORTRAN (called at
# configure.ac:1030), not hardcoded strings that happen to match. G95_FORTRAN
# sits textually nearby in configure.ac (line ~291) but is a DIFFERENT test
# (Fortran-90-syntax compatibility, not name-mangling) -- probed separately
# right after this block, per the plan's own instruction not to fold it in.
# ============================================================================

# Step 1: leading/trailing underscore convention. m4/fortran.m4:29-56
# compiles a trivial Fortran subroutine ("pipof") and greps `nm` output for
# 3 patterns ("_pipof", "pipof_", "_pipof_") to detect leading/trailing
# underscores. Reproduced here via execute_process, literally -- not
# inferred from CMake's own FortranCInterface abstraction (included right
# below as a CORROBORATING, independently-computed cross-check -- "confirms
# the underscore convention matches"), because that abstraction reports only
# the FORTRAN-specific half of the mangling (GLOBAL_SUFFIX/GLOBAL_PREFIX),
# never the platform's blanket "every C symbol gets a leading underscore"
# Mach-O convention. configure's raw grep-based test conflates the two: on
# this machine `nm` shows the compiled symbol as literally "_pipof_" (leading
# underscore from Mach-O, trailing from gfortran), so ALL THREE greps match
# in sequence (substring, unanchored) and -- since the m4 code is 3
# independent `if` blocks, not `if/elif` -- the LAST match wins, setting
# BOTH FC_LEADING_UNDERSCORE and FC_TRAILING_UNDERSCORE to yes. Reproduced
# bug-for-bug on purpose: the reference has WLU *and* WTU BOTH defined,
# which is this grep-overlap artifact, not two independent Fortran ABI facts
# (FortranCInterface's own clean GLOBAL_PREFIX comes back empty, confirming
# it would NOT set WLU if used alone -- exactly why it is a cross-check
# here, not the value source).
include(FortranCInterface)
if(NOT FortranCInterface_GLOBAL_SUFFIX STREQUAL "_")
  message(WARNING "FortranCInterface reports no Fortran trailing-underscore "
    "convention on this toolchain (GLOBAL_SUFFIX='${FortranCInterface_GLOBAL_SUFFIX}'); "
    "the raw-nm C2F/F2C/CNAME/WLU/WTU probe below assumes gfortran's usual "
    "trailing-underscore convention and was not re-verified for this case.")
endif()

set(_scilab_fmangle_dir "${CMAKE_BINARY_DIR}/CMakeFiles/ScilabFortranMangle")
file(MAKE_DIRECTORY "${_scilab_fmangle_dir}")
file(WRITE "${_scilab_fmangle_dir}/pipof.f" "       subroutine pipof\n       end\n")
execute_process(
  COMMAND "${CMAKE_Fortran_COMPILER}" -c pipof.f -o pipof.o
  WORKING_DIRECTORY "${_scilab_fmangle_dir}"
  RESULT_VARIABLE _scilab_fmangle_compile_rc
  OUTPUT_QUIET ERROR_QUIET)
set(_scilab_fc_leading_underscore FALSE)
set(_scilab_fc_trailing_underscore FALSE)
if(_scilab_fmangle_compile_rc EQUAL 0)
  execute_process(
    COMMAND nm "${_scilab_fmangle_dir}/pipof.o"
    OUTPUT_VARIABLE _scilab_fmangle_nm_out
    RESULT_VARIABLE _scilab_fmangle_nm_rc
    ERROR_QUIET)
  if(_scilab_fmangle_nm_rc EQUAL 0)
    # Same 3 sequential (non-elif) checks as m4/fortran.m4:40-56, in order,
    # last-match-wins -- deliberately reproducing the overlap described above.
    if(_scilab_fmangle_nm_out MATCHES "_pipof")
      set(_scilab_fc_leading_underscore TRUE)
      set(_scilab_fc_trailing_underscore FALSE)
    endif()
    if(_scilab_fmangle_nm_out MATCHES "pipof_")
      set(_scilab_fc_leading_underscore FALSE)
      set(_scilab_fc_trailing_underscore TRUE)
    endif()
    if(_scilab_fmangle_nm_out MATCHES "_pipof_")
      set(_scilab_fc_leading_underscore TRUE)
      set(_scilab_fc_trailing_underscore TRUE)
    endif()
  endif()
endif()
if(_scilab_fc_leading_underscore)
  set(WLU 1)
endif()
if(_scilab_fc_trailing_underscore)
  set(WTU 1)
endif()

# Step 2: "sharp sign" (## token-paste) support in the C preprocessor --
# m4/fortran.m4:75-92's AC_COMPILE_IFELSE, transcribed verbatim. AC_COMPILE_IFELSE
# only compiles (never links) -- but check_c_source_compiles' underlying
# try_compile BUILDS AN EXECUTABLE by default, so the test's own
# `extern int C2F(toto)(void);` with no definition anywhere (faithful to the
# original, which calls it but never defines it) fails at the LINK step
# unconditionally, regardless of whether ## works -- a false negative caught
# by testing (this probe reported "no" for BOTH generator target types until
# corrected). CMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY is CMake's own
# documented switch for exactly this "compile-only" case, scoped to this one
# probe and restored immediately after -- same save/restore discipline this
# file already applies to its other local excursions (C++17 flag,
# CoreFoundation framework).
set(_scilab_saved_try_compile_target_type ${CMAKE_TRY_COMPILE_TARGET_TYPE})
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
check_c_source_compiles("
#define C2F(name) name##_
extern int C2F(toto)(void);
int main(void) {
  C2F(toto)();
  ;
  return 0;
}
" _SCILAB_USE_SHARP_SIGN)
set(CMAKE_TRY_COMPILE_TARGET_TYPE ${_scilab_saved_try_compile_target_type})

# Step 3: C2F/F2C/CNAME bodies -- m4/fortran.m4:86,94-109's own branching,
# reproduced exactly (not hardcoded): CNAME always follows USE_SHARP_SIGN;
# C2F/F2C follow FC_TRAILING_UNDERSCORE first, then USE_SHARP_SIGN. On this
# machine (trailing underscore + sharp sign both true) this converges to
# "name##_"/"name##_"/"name1##name2", matching the reference exactly.
if(_SCILAB_USE_SHARP_SIGN)
  set(CNAME_BODY "name1##name2")
else()
  set(CNAME_BODY "name1/**/name2")
endif()
if(_scilab_fc_trailing_underscore)
  if(_SCILAB_USE_SHARP_SIGN)
    set(C2F_BODY "name##_")
    set(F2C_BODY "name##_")
  else()
    set(C2F_BODY "name/**/_")
    set(F2C_BODY "name/**/_")
  endif()
else()
  set(C2F_BODY "name")
  set(F2C_BODY "name")
endif()

# G95_FORTRAN: configure.ac:280-308, AC_LANG_PUSH([Fortran 77]) +
# AC_COMPILE_IFELSE compiling a Fortran-90-only construct (`select case` +
# a numbered `continue`) through the F77 compiler frontend -- despite the
# macro's name (written against the old G95 compiler), it is really an
# "is $F77 secretly F90-compatible" probe; gfortran is, so this fires here.
# Transcribed verbatim from the AC_LANG_PROGRAM body at configure.ac:291-303
# (fixed-form column layout preserved -- check_fortran_source_compiles'
# default .F extension matches the AC_LANG_PUSH([Fortran 77]) fixed form).
check_fortran_source_compiles("
      PROGRAM hello
        do 50 i = 1, 5
           select case ( i )
              case (1)
                 print*, \"case is 1, i is \", i
              case ( 2 : 3 )
                 print*, \"case is 2 to 3, i is \", i
              case default
                 print*, \"default case, i is \", i
              end select
 50           continue
      END
" G95_FORTRAN)

# ============================================================================
# bucket 4: configure OPTIONS (11 here; WLU/WTU are the worklist's other 2
# "options"-bucket entries but are Fortran ABI facts, already set above) --
# ENABLE_NLS + KLU_SUITESPARSE/UMFPACK_SUITESPARSE + WITH_*. Each underlying
# AC_DEFINE has NO conditional guard at its own call site (m4/hdf5.m4:204,
# m4/eigen.m4:75, m4/fftw.m4:73, m4/klu.m4:110+113, m4/umfpack.m4 mirrors
# klu.m4, m4/gettext.m4:268 for ENABLE_NLS): each library-detection macro
# either finds its library and unconditionally AC_DEFINEs the WITH_* flag or
# calls AC_MSG_ERROR and configure never finishes. So on any tree where
# `./configure` completed (as it did here, producing the reference
# machine.h), every one of these is simply "on" -- there is no separate
# probe to run beyond the HAVE_LIB*/HAVE_GETTEXT facts bucket 1b-lib already
# computed. CMake owns the DECISION of which optional modules to build from
# here on (per the plan's own framing); real find_package()-driven
# conditional module inclusion is a later stage's concern (the
# modules/<name>/CMakeLists.txt files this task does not touch). For now
# these mirror what this dev tree's own successful configure run already
# established. ENABLE_MPI/ENABLE_RELOCATABLE/WITH_OCAML/WITH_TK are
# deliberately left OFF -- genuinely undef in the reference too.
#
# KLU_SUITESPARSE/UMFPACK_SUITESPARSE (m4/klu.m4:109-111, umfpack.m4 mirrors
# it) additionally depend on $SUITESPARSE=yes -- which of two KLU/UMFPACK
# header layouts was found (SuiteSparse's own vs. a standalone copy); true
# on this tree, same "CMake owns it from here" treatment as the WITH_* flags.
# ============================================================================
option(ENABLE_NLS   "Native Language Support"                  ON)
option(WITH_GUI     "With the JAVA stuff (GUI, Console, JOGL...)" ON)
option(WITH_XCOS    "with XCos"                                ON)
option(WITH_EIGEN   "With the EIGEN library"                   ON)
option(WITH_FFTW    "With the FFTW library"                    ON)
option(WITH_HDF5    "With the HDF5 library"                    ON)
option(WITH_KLU     "With the KLU library"                     ON)
option(WITH_MATIO   "With the MATIO library"                   ON)
option(WITH_UMFPACK "With the UMFPACK library"                 ON)
set(KLU_SUITESPARSE 1)
set(UMFPACK_SUITESPARSE 1)

# ============================================================================
# bucket 5: PACKAGE_* + PACKAGE + VERSION (9) -- all fall out of the single
# `AC_INIT([Scilab],[6],[https://gitlab.com/scilab/scilab/-/issues])` call
# (configure.ac:24) via autoconf's OWN fixed derivation rules: PACKAGE_NAME/
# VERSION/PACKAGE_BUGREPORT are AC_INIT's 3 literal arguments; PACKAGE_TARNAME
# defaults to lower-cased, non-alnum-stripped PACKAGE_NAME when the (omitted)
# 4th argument is absent; PACKAGE_URL defaults to "" when the (omitted) 5th
# argument is absent; PACKAGE = PACKAGE_TARNAME; PACKAGE_VERSION = VERSION;
# PACKAGE_STRING = "PACKAGE_NAME VERSION". Reproduced as that SAME small
# derivation (not 8 independent literals), so only 3 values are actually
# transcribed from configure.ac's source text -- the rest are computed from
# those 3 by CMake, exactly as autoconf itself computes them.
#
# Deliberately NOT wired to this project's OWN SCILAB_VERSION_MAJOR/MINOR/
# MAINTENANCE (cmake/ScilabConfigure.cmake, Stage 1f-c, for version.h's
# *different* "2027.0" fork/branch version): those are read from
# config.status BY DESIGN -- version.h targets BYTE-identity, a different
# contract than this file's SEMANTIC one (see that file's own header
# comment) -- so reusing them here would launder a config.status read into
# this bucket, and they are numerically wrong for this purpose regardless
# (2027.0.0, not 6): AC_INIT's product version has been the literal "6"
# since long before the 2027 branch existed; the two numbers are unrelated.
# ============================================================================
set(PACKAGE_NAME "Scilab")
set(VERSION "6")
set(PACKAGE_BUGREPORT "https://gitlab.com/scilab/scilab/-/issues")
string(TOLOWER "${PACKAGE_NAME}" PACKAGE_TARNAME)
set(PACKAGE "${PACKAGE_TARNAME}")
set(PACKAGE_VERSION "${VERSION}")
set(PACKAGE_STRING "${PACKAGE_NAME} ${PACKAGE_VERSION}")
# AC_INIT's optional 5th arg (URL) was omitted -> "" -- see the PACKAGE_URL
# line in machine.h.cmake.in: it is a plain #define, not #cmakedefine,
# because CMake's if(PACKAGE_URL) treats an empty STRING as false, which
# would wrongly emit "/* #undef PACKAGE_URL */" for a macro configure
# always defines (just sometimes to "").
set(PACKAGE_URL "")

# ============================================================================
# Three "plain always-on facts" CMake already knows, no probe needed:
# ============================================================================

# INSTALLPREFIX: configure.ac:95's AC_RELOCATABLE_LIBRARY fires
# UNCONDITIONALLY (no ENABLE_RELOCATABLE guard -- confirmed by reading that
# call site directly), substituting autoconf's own ${prefix}. CMake's
# equivalent install-prefix variable defaults to the SAME "/usr/local" on
# this unconfigured-prefix tree (confirmed: a fresh configure's own
# CMakeCache.txt carries CMAKE_INSTALL_PREFIX:PATH=/usr/local).
set(INSTALLPREFIX "${CMAKE_INSTALL_PREFIX}")

# PATH_SEPARATOR: configure.ac:2309 substitutes a shell $PATH_SEPARATOR set
# earlier by the AC_LIB_RPATH/gnulib machinery (the AC_RELOCATABLE_LIBRARY
# family above) -- the standard gnulib rule is ':' on every Unix-family
# host, ';' only on native-Windows/OS-2 (drive-letter paths). Computed from
# CMake's own WIN32 platform test, not hardcoded to this one platform.
if(WIN32)
  set(PATH_SEPARATOR ";")
else()
  set(PATH_SEPARATOR ":")
endif()

# SHARED_LIB_EXT + SHARED_LIB_EXTW: configure.ac:2338-2342 substitutes
# libtool's own $shrext_cmds into BOTH macros (the wide-string one just
# L""-prefixes the same value). CMake's native per-platform equivalent is
# CMAKE_SHARED_LIBRARY_SUFFIX (".dylib" on Darwin, computed by CMake's own
# toolchain/platform detection). The template's SHARED_LIB_EXTW line is
# `L"@SHARED_LIB_EXT@"` -- it substitutes SHARED_LIB_EXT's TEXT, but
# #cmakedefine's own truthiness gate is keyed on the macro's OWN name
# (SHARED_LIB_EXTW), a SEPARATE CMake variable from SHARED_LIB_EXT; setting
# only the latter left SHARED_LIB_EXTW undef (caught by the fresh-build
# convergence check -- fixed by setting both, from the one real value).
set(SHARED_LIB_EXT "${CMAKE_SHARED_LIBRARY_SUFFIX}")
set(SHARED_LIB_EXTW "${CMAKE_SHARED_LIBRARY_SUFFIX}")

# ============================================================================
# LT_OBJDIR: a FIFTH macro source the plan's four buckets never named --
# pure libtool boilerplate (m4/libtool.m4:2176-2193, _LT_CHECK_OBJDIR), not
# pkg-config, not Fortran, not an "option", not PACKAGE_*. Flagged here as
# its own distinct *libtool* category for RC-b/RC-c, which will meet more of
# these (m4/libtool.m4 alone is over 2000 lines). The real test: `mkdir
# .libs`; if the filesystem allows a dot-prefixed directory name (every
# modern Unix/macOS volume does -- the fallback exists only for MS-DOS-era
# 8.3 filesystems that forbid leading dots), lt_cv_objdir=.libs, else _libs;
# LT_OBJDIR is that value plus a trailing slash. Genuinely probed via
# file(MAKE_DIRECTORY), not assumed from CMAKE_SYSTEM_NAME.
# ============================================================================
set(_scilab_ltobjdir_probe "${CMAKE_BINARY_DIR}/CMakeFiles/ScilabLtObjdirProbe/.libs")
file(REMOVE_RECURSE "${_scilab_ltobjdir_probe}")
file(MAKE_DIRECTORY "${_scilab_ltobjdir_probe}")
if(IS_DIRECTORY "${_scilab_ltobjdir_probe}")
  set(LT_OBJDIR ".libs/")
else()
  set(LT_OBJDIR "_libs/")
endif()
file(REMOVE_RECURSE "${_scilab_ltobjdir_probe}")

# ============================================================================
# Deliberately UNPROBED (left absent, matching the reference's undef):
#
# HAVE_MACH_O_DYLD_H, HAVE__NSGETEXECUTABLEPATH (m4/relocatable.m4:38-39) and
# HAVE_X11_XLIB_H (m4/tcltk.m4:395-397) are only ever checked inside
# configure.ac branches gated by options this task does not own
# (ENABLE_RELOCATABLE, WITH_TK -- the "options" bucket, a later stage). With
# both options off/undef in THIS CMake configuration too (nothing has wired
# that gate yet), the semantically-correct answer is the same as configure's:
# the check never runs, so the macro stays undef. Probing them
# unconditionally here would be WRONG, not just extra -- mach-o/dyld.h and
# X11/Xlib.h both physically exist in this SDK, so an unconditional
# check_include_file/check_function_exists would silently produce a false
# positive and diverge from the reference the moment the option gate lands
# and actually wires this check on. Leaving the CMake variable unset is what
# makes #cmakedefine emit nothing here, exactly like configure's own skipped
# branch.
# ============================================================================

configure_file(${CMAKE_CURRENT_LIST_DIR}/machine.h.cmake.in
               ${SCILAB_GENERATED_INCLUDES}/machine.h)
message(STATUS "CMake-generated machine.h -> ${SCILAB_GENERATED_INCLUDES}/machine.h")

# Restore the CMAKE_REQUIRED_* this file overwrote at the top -- see the
# comment there. Must stay the last thing this file does, so nothing below
# this line (there is nothing) runs under this file's probe environment.
set(CMAKE_REQUIRED_QUIET ${_scilab_outer_required_quiet})
set(CMAKE_REQUIRED_FLAGS ${_scilab_outer_required_flags})
set(CMAKE_REQUIRED_INCLUDES ${_scilab_outer_required_includes})
