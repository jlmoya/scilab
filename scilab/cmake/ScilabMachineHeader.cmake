# scilab/cmake/ScilabMachineHeader.cmake — CMake generates machine.h (retire-configure RC-a).
#
# CMake COMPUTES every macro itself (never copies config.status) — that is what makes the
# harness's semantic header_defines dimension a real gate rather than a tautology. Output is
# NOT byte-identical to autoconf's machine.h (comment/#define/ordering differ); equivalence is
# semantic. ADDITIVE: the source-tree machine.h is untouched and still resolves first during
# coexistence (ScilabModule.cmake keeps core/includes ahead); this copy activates at RC-e.
#
# Scope: this stage converges the ~125 HAVE_*/SIZEOF_*/STDC_HEADERS/CLOSEDIR_VOID "probe"
# macros only (see build-parity/parity/fingerprint.py's probe() filter, mirrored in Step 5 of
# the task brief). The pkg-config (CURL_*/LIBARCHIVE_*/LIBXML_*), Fortran name-mangling
# (C2F/F2C/CNAME/WLU/WTU/F77_*), options (WITH_*/ENABLE_*/KLU_SUITESPARSE/...) and PACKAGE_*
# buckets are computed nowhere here — their CMake variables stay unset, so the template's
# #cmakedefine lines for them legitimately emit nothing (matching configure's `/* #undef X */`
# shape) until a later stage fills them in.
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
# UNCONDITIONALLY, with no AC_CHECK_* guarding it at all (the separate
# AC_CHECK_FUNC([isinf]) right below it only controls the fallback isinf(x)
# macro body, a non-probe/later-stage macro). Nothing to probe: reproduce the
# same unconditional fact.
set(HAVE_ISINF 1)

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
check_c_source_compiles("
#include <dirent.h>
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
