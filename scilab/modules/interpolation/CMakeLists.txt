# CMake build for the `interpolation` module — Stage-1 make→CMake roll-out #4,
# the FORTRAN / mixed-language exemplar (the last unproven build dimension;
# ~907 Fortran files across the core numerical modules follow this pattern).
#
# HYBRID COEXISTENCE: the rest of the tree stays autotools; this standalone
# project builds ONLY libsciinterpolation and its dylib is dropped into
# modules/interpolation/.libs/ in place of the autotools one. LINKING CLASS is a
# per-module fact — check modules/Makefile.am: `interpolation` is listed in
# ENGINE_LIBS_DYNAMIC_LOAD (line 218, "loaded dynamically from Scilab"), the SAME
# list as sound and parallel — it is genuinely dlopen-only. NO executable or
# engine lib records it as a load-command dependency (verified: otool -L on
# scilab-cli-bin / libscilab-cli / libscilab → 0 interpolation entries). So the
# drop-in is safe because Scilab's module machinery dlopens the gateway dylib
# from modules/interpolation/.libs/ by leaf name at load time — replace the file
# there and the next load picks it up, with no relink of anything.
#   (Contrast: an ENGINE_LIBS member like `coverage` IS linked into
#    libscilab/libscilab-cli — there the drop-in is safe for a DIFFERENT reason:
#    the recorded dep is an install_name the launcher's DYLD paths resolve to
#    .libs/. When copying this template, look up YOUR module in modules/Makefile.am
#    and write the matching story — they are different paragraphs.)
# This file is invisible to automake; the autotools interpolation is always
# recoverable with `make -C modules/interpolation`.
#
# PARITY CONTRACT (arbitrated by build-parity/): the output must be
# behaviorally identical to the autotools build —
#   file:          libsciinterpolation.2027.dylib (+ libsciinterpolation.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libsciinterpolation.2027.dylib
#   exports:       exactly 64 symbols (mixed C/Fortran/C++: the Fortran spline/
#                  mesh kernels like _bicubicspline_/_cshep2_, the C algo entry
#                  points, the 12 sci_* gateways, and the _info_hermite_ common)
#   deps:          /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib
#                  /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#                  — NOTHING else: no libgcc_s, no emutls/heapt dylib, no @rpath.
# Every compiler/linker fact below is transcribed from the CONFIGURED autotools
# build (modules/interpolation/Makefile), not invented here.
#
# THE new pattern proven here (everything else is the coverage template):
#   (C) a MIXED-LANGUAGE algo lib — the OBJECT library compiles 2 C + 4 Fortran
#       sources, so compile flags/defines/includes are applied PER LANGUAGE via
#       $<COMPILE_LANGUAGE:...> generator expressions. automake does the same
#       split: C/C++ get DEFS + the CPPFLAGS include set + SCI_C(XX)FLAGS, while
#       the F77 rule is only `$(F77) $(AM_FFLAGS) $(FFLAGS)` — SCI_FFLAGS plus a
#       single -I modules/core/includes/, NO -DHAVE_CONFIG_H, NO -Wall/-Wpedantic
#       /-fno-stack-protector/-std, NO C-only -Werrors.
#   (D) the Fortran RUNTIME link. The linker language stays CXX (libc++ comes
#       from the C++ driver — one of the baseline's deps), and CMake itself
#       appends its detected Fortran implicit link info for the mixed-language
#       target (cmComputeLinkInformation: languages ≠ linker language get their
#       CMAKE_<LANG>_IMPLICIT_LINK_LIBRARIES/DIRECTORIES added). On this
#       toolchain that is `-lemutls_w -lheapt_w -lgfortran -lgcc -lquadmath`
#       with the Homebrew gcc -L dirs — the same tail as autotools' $(FLIBS)
#       plus -lgcc. emutls_w/heapt_w/gcc resolve to static .a helpers (they
#       fold in: NO dylib dep — the gcc DYLIB is named libgcc_s.1.1.dylib,
#       which -lgcc does not match), while libgfortran/libquadmath are dylibs
#       with ABSOLUTE install_names (/opt/homebrew/opt/gcc/lib/gcc/current/...),
#       so the recorded deps are exactly the baseline's two paths — not @rpath
#       (the same mechanism that made parallel's libomp parity-clean).
#
# Build:    cmake -S . -B build-cmake && cmake --build build-cmake
#           (Makefile/Ninja generators — the per-language $<COMPILE_LANGUAGE:...>
#            includes/defines below don't express under -G Xcode/VS.)
# Drop-in:  cmake --build build-cmake --target drop-in
# Verify:   cd ../../build-parity && ./capture.sh .. /tmp/cand.json cand \
#           && ./diff.sh baseline-autotools.json /tmp/cand.json

cmake_minimum_required(VERSION 3.20)

# Autotools compiles with CC="gcc -std=gnu23 -arch arm64", CXX="g++ -arch arm64
# -std=c++17", F77="gfortran" — on this platform /usr/bin/gcc and /usr/bin/g++
# ARE Apple clang/clang++, and gfortran is Homebrew gcc's. Default to the same
# drivers (honor explicit -DCMAKE_*_COMPILER= or $CC/$CXX/$FC overrides). The
# linker language being C++ is load-bearing: the C++ driver links libc++ — one
# of the baseline's deps. Pinning gfortran matters too: the Fortran flags below
# and the implicit-runtime argument in (D) are gfortran facts; a stray flang
# would break both (different runtime, different flag spellings).
if(NOT DEFINED CMAKE_C_COMPILER AND NOT DEFINED ENV{CC})
    set(CMAKE_C_COMPILER gcc)
endif()
if(NOT DEFINED CMAKE_CXX_COMPILER AND NOT DEFINED ENV{CXX})
    set(CMAKE_CXX_COMPILER g++)
endif()
if(NOT DEFINED CMAKE_Fortran_COMPILER AND NOT DEFINED ENV{FC})
    set(CMAKE_Fortran_COMPILER gfortran)   # autotools: F77 = gfortran
endif()
set(CMAKE_OSX_ARCHITECTURES arm64)        # CC/CXX's -arch arm64
set(CMAKE_OSX_DEPLOYMENT_TARGET 11.0)     # -mmacosx-version-min=11.0 on C/C++ compile + link
# NB: CMake applies NEITHER OSX_* variable to Fortran TUs — the Fortran compile
# line carries no -arch and no -mmacosx-version-min from these. Fortran gets
# -mmacosx-version-min=11.0 ONLY from the explicit SCI_FFLAGS genex below, so that
# flag is LOAD-BEARING (not redundant) — do not drop it. Fortran arch rides on the
# host default (arm64 here); a cross-arch build must add -arch to the Fortran flags.
# NB: CMake also SEEDS CMAKE_{C,CXX}_FLAGS / CMAKE_*_LINKER_FLAGS from the ambient
# $CFLAGS/$LDFLAGS at configure (this machine's $CFLAGS adds an openssl -I, plus an
# -L on the link line). Harmless — parity is green — and the explicit transcription
# below does NOT rely on it; a copier diffing the emitted compile/link lines against
# this file will see those ambient extras (they are env, not declared here).

project(scilab-interpolation C CXX Fortran)

if(NOT CMAKE_Fortran_COMPILER_ID STREQUAL "GNU")
    message(FATAL_ERROR "Fortran compiler is ${CMAKE_Fortran_COMPILER_ID} "
                        "(${CMAKE_Fortran_COMPILER}); the parity target was built with "
                        "gfortran (Homebrew gcc). Pass -DCMAKE_Fortran_COMPILER=gfortran.")
endif()

# The parity harness reads the compile command from compile_commands.json to
# verify the semantic flag facts (opt/wrapv/min_macos/ndebug/std).
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Root of the Scilab source tree (this file lives in modules/interpolation/).
# Overridable so an out-of-tree checkout can still point at a configured tree
# (machine.h/version.h below are generated by configure, not by us).
set(SCILAB_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../.." CACHE PATH
    "Scilab source tree root (must be autotools-configured: modules/core/includes/machine.h)")
get_filename_component(SCILAB_SOURCE_DIR "${SCILAB_SOURCE_DIR}" ABSOLUTE)
if(NOT EXISTS "${SCILAB_SOURCE_DIR}/modules/core/includes/machine.h")
    message(FATAL_ERROR "SCILAB_SOURCE_DIR (${SCILAB_SOURCE_DIR}) is not a configured "
                        "Scilab tree: modules/core/includes/machine.h not found. "
                        "Run ./configure there first (machine.h is configure-generated).")
endif()

# libtool: -version-number $(SCILAB_LIBRARY_VERSION) with 2027:0:0 names the
# file libsciinterpolation.2027.dylib and stamps Mach-O compat/current 2028.0.0
# (darwin libtool uses major+minor+1). The 4-digit token in the FILENAME is
# what the parity harness keys on (libsciinterpolation.VER.dylib) — it must be
# exactly ".2027." with nothing after it before ".dylib".
set(SCILAB_LIBRARY_VERSION_MAJOR 2027)
# NB: this "+1" equals libtool's major+minor+1 ONLY because minor==0
# (SCILAB_LIBRARY_VERSION 2027:0:0). The harness normalizes compat/current so it
# is bit-fidelity only — but a future top-level driver that meets a nonzero minor
# must add MINOR here too.
math(EXPR _macho_version "${SCILAB_LIBRARY_VERSION_MAJOR} + 1")

# PATTERN (A) from coverage, now mixed-language — the automake convenience lib
# as an OBJECT library.
#
# Makefile.am: noinst_LTLIBRARIES = libsciinterpolation-algo.la with
# libsciinterpolation_algo_la_SOURCES = $(INTERPOLATION_C_SOURCES)
# $(INTERPOLATION_FORTRAN_SOURCES), folded into the gateway via
# libsciinterpolation_la_LIBADD. A libtool convenience lib means "compile
# these sources, fold ALL their objects into whatever links me" — the exact
# CMake equivalent is an OBJECT library consumed through $<TARGET_OBJECTS:...>.
# Do NOT model it as add_library(... STATIC) + link: a static archive only
# pulls members that resolve an outstanding reference, so algo symbols nothing
# in the gateway references directly would be silently dropped — and the
# autotools baseline exports ALL 64 symbols (the Fortran kernels are called
# from C via wrappers, but their globals must still all export).
add_library(sciinterpolation-algo OBJECT
    # INTERPOLATION_C_SOURCES
    src/c/interpolation.c
    src/c/someinterp.c
    # INTERPOLATION_FORTRAN_SOURCES (fixed-form .f; gfortran resolves their
    # `include 'constinterp.h.f'` next to the including source file)
    src/fortran/somespline.f
    src/fortran/dspfit.f
    src/fortran/cshep2d.f
    src/fortran/mesh2b.f)

# No-op on darwin (PIC is the compiler default) but required on ELF targets
# whenever object-lib objects fold into a SHARED lib — set it so the template
# copies correctly to any future non-macOS roll-out.
set_target_properties(sciinterpolation-algo PROPERTIES POSITION_INDEPENDENT_CODE ON)

# The gateway dylib: 12 C++ gateway sources + every algo object folded in.
add_library(sciinterpolation SHARED
    $<TARGET_OBJECTS:sciinterpolation-algo>
    sci_gateway/cpp/sci_lsq_splin.cpp
    sci_gateway/cpp/sci_interp.cpp
    sci_gateway/cpp/sci_interp2d.cpp
    sci_gateway/cpp/sci_splin.cpp
    sci_gateway/cpp/sci_cshep2d.cpp
    sci_gateway/cpp/sci_interp3d.cpp
    sci_gateway/cpp/sci_linear_interpn.cpp
    sci_gateway/cpp/sci_splin2d.cpp
    sci_gateway/cpp/sci_splin3d.cpp
    sci_gateway/cpp/sci_bsplin3val.cpp
    sci_gateway/cpp/sci_eval_cshep2d.cpp
    sci_gateway/cpp/sci_mesh2di.cpp)

# The C/C++ include set — automake applies the SAME list to both libs
# (libsciinterpolation_algo_la_CPPFLAGS = $(libsciinterpolation_la_CPPFLAGS));
# order mirrors the autotools compile line exactly: $(DEFAULT_INCLUDES)
# $(libsciinterpolation_la_CPPFLAGS) $(CPPFLAGS). The Makefile repeats
# modules/dynamic_link/includes inside la_CPPFLAGS; CMake de-duplicates —
# same preprocessor result. Fortran TUs get NONE of these (see pattern (C):
# the F77 rule carries no CPPFLAGS) — only the AM_FFLAGS include below.
set(INTERPOLATION_CPP_INCLUDES
    # DEFAULT_INCLUDES: -I. -I$(top_builddir)/modules/core/includes
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${SCILAB_SOURCE_DIR}/modules/core/includes  # configure-GENERATED machine.h/version.h
    # libsciinterpolation_la_CPPFLAGS:
    ${CMAKE_CURRENT_SOURCE_DIR}/includes
    ${CMAKE_CURRENT_SOURCE_DIR}/src/c
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/ast
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/exps
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/operations
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/parse
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/symbol
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/system_env
    ${SCILAB_SOURCE_DIR}/modules/ast/includes/types
    ${SCILAB_SOURCE_DIR}/modules/output_stream/includes
    ${SCILAB_SOURCE_DIR}/modules/api_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/localization/includes
    ${SCILAB_SOURCE_DIR}/modules/dynamic_link/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    # $(AM_CPPFLAGS)=$(SCI_CPPFLAGS) is empty in the configured Makefile —
    # nothing to carry. CPPFLAGS (configure-detected Homebrew paths;
    # machine-specific — the future top-level driver derives these from the
    # toolchain, not hardcoded):
    /opt/homebrew/include
    /opt/homebrew/opt/libomp/include
    /opt/homebrew/opt/libarchive/include)

# PATTERN (C) — per-language flags on the mixed-language targets, transcribed
# VERBATIM from the configured Makefile (do NOT replace with CMAKE_BUILD_TYPE
# presets — parity is on these exact semantic facts: O2 + fwrapv + min_macos
# 11.0 + NDEBUG per language):
#   C       $(CC)  $(DEFS) $(DEFAULT_INCLUDES) $(la_CPPFLAGS) $(CPPFLAGS)
#           $(SCI_CFLAGS) $(CFLAGS)  — SCI_CFLAGS has the C-only -Werrors;
#           CFLAGS appends a trailing -I/opt/homebrew/opt/openssl/include.
#   C++     $(CXX) same-CPPFLAGS + $(SCI_CXXFLAGS), CXXFLAGS empty — NO
#           C-only -Werrors (they are C diagnostics).
#   Fortran $(F77) $(AM_FFLAGS) $(FFLAGS) = SCI_FFLAGS +
#           -I modules/core/includes/ ONLY — no DEFS (-DHAVE_CONFIG_H), no
#           CPPFLAGS includes, no -Wall/-Wpedantic/-fno-stack-protector, no
#           -std, and FFLAGS is empty. gfortran accepts the -DNDEBUG (a
#           preprocessor define, inert for non-preprocessed .f) exactly as it
#           did under autotools.
# Do NOT add -fvisibility=hidden: autotools didn't, and it would drop exported
# symbols (the baseline records all 64).
foreach(_tgt IN ITEMS sciinterpolation-algo sciinterpolation)
    target_compile_definitions(${_tgt} PRIVATE
        $<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:HAVE_CONFIG_H>)

    target_include_directories(${_tgt} PRIVATE
        "$<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:${INTERPOLATION_CPP_INCLUDES}>"
        # AM_FFLAGS = $(SCI_FFLAGS) -I$(top_srcdir)/modules/core/includes/
        "$<$<COMPILE_LANGUAGE:Fortran>:${SCILAB_SOURCE_DIR}/modules/core/includes>")

    target_compile_options(${_tgt} PRIVATE
        # CC's -std=gnu23, then SCI_CFLAGS, then CFLAGS' trailing openssl -I
        "$<$<COMPILE_LANGUAGE:C>:-std=gnu23;-DNDEBUG;-g1;-O2;-fwrapv;-mmacosx-version-min=11.0;-fno-stack-protector;-Wall;-Wpedantic;-Werror=implicit;-Werror=incompatible-pointer-types;-I/opt/homebrew/opt/openssl/include>"
        # CXX's -std=c++17, then SCI_CXXFLAGS (no C-only -Werrors; CXXFLAGS empty)
        "$<$<COMPILE_LANGUAGE:CXX>:-std=c++17;-DNDEBUG;-g1;-O2;-fwrapv;-mmacosx-version-min=11.0;-fno-stack-protector;-Wall;-Wpedantic>"
        # SCI_FFLAGS (FFLAGS empty)
        "$<$<COMPILE_LANGUAGE:Fortran>:-DNDEBUG;-g1;-O2;-fwrapv;-mmacosx-version-min=11.0>")

    # Drop CMake's automatic <target>_EXPORTS define: autotools defines no such
    # symbol (it would only matter under _MSC_VER anyway — dynlib_interpolation.h).
    # (A no-op for the OBJECT lib, which gets no such define; kept uniform in
    # the foreach so the SHARED target — which DOES get one — is covered.)
    set_target_properties(${_tgt} PROPERTIES DEFINE_SYMBOL "")
endforeach()

# PATTERN (D) — the Fortran runtime link, without giving up the C++ driver.
#
# Autotools: libsciinterpolation_la_LIBADD = libsciinterpolation-algo.la
# $(FLIBS), linked by $(CXXLINK) — the C++ driver, so libc++ rides along, and
# FLIBS' tail (-lemutls_w -lheapt_w -lgfortran -lquadmath) supplies the Fortran
# runtime. In CMake: LINKER_LANGUAGE CXX is the automatic choice (CXX linker
# preference 30 beats Fortran's 20) but is pinned here because it is
# load-bearing, and the Fortran side needs NO explicit target_link_libraries —
# because this target contains Fortran objects, CMake appends its own detected
# ${CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES}/${CMAKE_Fortran_IMPLICIT_LINK_DIRECTORIES}
# (= the FLIBS equivalent, verified: -lemutls_w -lheapt_w -lgfortran -lgcc
# -lquadmath) to the C++ link line. Net deps recorded: exactly
# libgfortran.5 + libquadmath.0 at their absolute /opt/homebrew/opt/gcc/...
# install_names — the static .a helpers fold in depless (see the header block).
# If a future toolchain's implicit set ever drags in a real extra dylib
# (libgcc_s & co.), the fallback is to override the two variables right after
# project() with the explicit FLIBS tail: -L/opt/homebrew/opt/gcc/lib/gcc/current
# + gfortran;quadmath.
set_target_properties(sciinterpolation PROPERTIES LINKER_LANGUAGE CXX)

# libtool's darwin allow_undefined_flag, VERBATIM: the gateways call Scilab
# API symbols (types::Function, Scierror, ...) that live in the hosting
# process — they resolve when dyld loads this dylib (Scilab's module machinery
# dlopens it on demand), never at static link time. This is precisely why the
# autotools dylib's deps stop at the Fortran runtime + two system libs.
# -no_fixup_chains rides along (dynamic_lookup is incompatible with chained
# fixups; libtool disables them, and it changes the dyld load commands).
target_link_options(sciinterpolation PRIVATE
    "LINKER:-undefined,dynamic_lookup"
    "LINKER:-no_fixup_chains"
    # SCI_LDFLAGS rpaths — libtool stamped these two LC_RPATHs into the dylib.
    "LINKER:-rpath,/usr/lib"
    "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")

set_target_properties(sciinterpolation PROPERTIES
    # EXACT filename libsciinterpolation.2027.dylib: version baked into
    # OUTPUT_NAME. Deliberately NOT CMake VERSION/SOVERSION — those would name
    # it libsciinterpolation.2027.0.0.dylib, which breaks the harness key.
    OUTPUT_NAME "sciinterpolation.${SCILAB_LIBRARY_VERSION_MAJOR}"
    SUFFIX ".dylib"
    # libtool's `-rpath $(pkglibdir)` == absolute install_name, not @rpath.
    INSTALL_NAME_DIR "/usr/local/lib/scilab"
    BUILD_WITH_INSTALL_NAME_DIR TRUE
    # libtool 2027:0:0 → compat/current 2028.0.0 (harness strips these, but
    # match them anyway for bit-level load-command fidelity).
    MACHO_COMPATIBILITY_VERSION "${_macho_version}.0.0"
    MACHO_CURRENT_VERSION "${_macho_version}.0.0")

# libtool also leaves an unversioned symlink next to the versioned dylib.
add_custom_command(TARGET sciinterpolation POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:sciinterpolation> ${CMAKE_CURRENT_BINARY_DIR}/libsciinterpolation.dylib
    COMMENT "Symlinking libsciinterpolation.dylib -> $<TARGET_FILE_NAME:sciinterpolation>")

# The hybrid drop-in: put the CMake-built dylib where the engine loads it
# (modules/interpolation/.libs/) and where the parity harness fingerprints it.
# A REAL copy, not a symlink: the harness skips symlinked dylibs, and the
# autotools original must remain recoverable by simply rebuilding
# (`make -C modules/interpolation`).
add_custom_target(drop-in
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_SOURCE_DIR}/.libs
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:sciinterpolation> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/$<TARGET_FILE_NAME:sciinterpolation>
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:sciinterpolation> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/libsciinterpolation.dylib
    DEPENDS sciinterpolation
    COMMENT "Dropping CMake-built interpolation into modules/interpolation/.libs/ (hybrid coexistence)")
