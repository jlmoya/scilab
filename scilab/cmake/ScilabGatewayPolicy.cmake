# Build policy for a TOOLBOX GATEWAY -- the shared library `ilib_build` produces
# on an end user's machine. Step 2b of docs/design/dynamic-link-cmake-migration.md.
#
# This file is deliberately loadable from TWO roots, from ONE definition:
#
#   * in-tree   -- included by the top-level CMakeLists, SCILAB_ROOT = the source tree;
#   * installed -- included by ScilabConfig.cmake out of the app bundle, where
#                  SCILAB_ROOT = <app>/Contents/Resources/scilab.
#
# The relative layout is identical under both (modules/<m>/includes exists in the
# bundle -- verified), so the ONLY thing that varies is the root. Nothing here may
# read a build-tree-only variable (SCILAB_SOURCE_DIR, SCILAB_DEFAULT_INCLUDES,
# CMAKE_BINARY_DIR): those do not exist on a user's machine, which is exactly the
# gap that made a copy-paste of scilab_module() impossible.
#
# WHY NOT just call scilab_module()? Different contract. scilab_module() builds
# Scilab's OWN 64 dylibs and is arbitrated by build-parity/ against the autotools
# baseline: versioned filename libsci<name>.2027.dylib, drop-in into .libs/,
# HAVE_CONFIG_H, the 12-entry SCILAB_DEFAULT_INCLUDES. A gateway shares the
# LINK policy and nothing else -- see the include-set note below.

if(DEFINED _SCILAB_GATEWAY_POLICY_INCLUDED)
  return()
endif()
set(_SCILAB_GATEWAY_POLICY_INCLUDED TRUE)

# The gateway include set, RELATIVE to SCILAB_ROOT, in the order the current
# runtime path emits it.
#
# This is NOT SCILAB_DEFAULT_INCLUDES. That list (cmake/ScilabToolchain.cmake) has
# 12 entries and serves Scilab's own modules; the runtime gateway set has 16 --
# it adds mexlib, threads, string and console, which a toolbox gateway needs and
# a core module does not. Both were derived independently and the difference is
# real: captured from a live ilib_build in
# modules/dynamic_link/tests/oracle/oracle-commands-macos-arm64.txt, where the
# C/C++ compile lines carry <SCI-INCLUDES x16>. Keep the two lists separate; the
# oracle is the arbiter for THIS one.
set(SCILAB_GATEWAY_INCLUDES_RELATIVE
  modules/core/includes
  modules/mexlib/includes
  modules/api_scilab/includes
  modules/output_stream/includes
  modules/localization/includes
  modules/dynamic_link/includes
  modules/threads/includes
  modules/string/includes
  modules/console/includes
  modules/ast/includes/ast
  modules/ast/includes/exps
  modules/ast/includes/operations
  modules/ast/includes/parse
  modules/ast/includes/symbol
  modules/ast/includes/system_env
  modules/ast/includes/types)

# Fortran gets core/includes and NOTHING else -- automake's F77 rule feeds from
# FFLAGS alone, which gencompilationflags_unix populates far more sparsely than
# CFLAGS. The oracle records this as <SCI-INCLUDES x1> against the C/C++ x16.
# Handing Fortran the full C set would be a silent behaviour change, so it is
# spelled out separately rather than derived by slicing the list above.
set(SCILAB_GATEWAY_Fortran_INCLUDES_RELATIVE modules/core/includes)

# gettext. Scilab's OWN public header modules/localization/includes/localization.h
# does `#include <libintl.h>`, so every gateway that reaches any Scilab header
# needs gettext's include directory -- yet nothing in the toolchain provided it.
#
# Measured across the 23 toolboxes that go through ilib_build: every one that
# ships a hand-written build_macos.sce builds, and every one that does not fails
# with "fatal error: 'libintl.h' file not found" -- on BOTH the autotools and the
# CMake path. Those build_macos.sce files exist largely to do
# `setenv("CPATH", "/opt/homebrew/opt/gettext/include")`. That is a per-toolbox
# workaround for a gap in the platform, and it means a third-party ATOMS author
# hits a compile error in Scilab's own header with nothing to tell them why.
#
# Searched rather than hardcoded to one path, because Homebrew installs gettext
# both as a keg (opt/gettext/include) and linked into the shared prefix, and the
# prefix differs on Intel (/usr/local). find_path caches the result, so this
# costs nothing after the first configure.
find_path(SCILAB_LIBINTL_INCLUDE_DIR libintl.h
  HINTS /opt/homebrew/opt/gettext/include /opt/homebrew/include
        /usr/local/opt/gettext/include /usr/local/include
  DOC "Directory containing libintl.h, required by Scilab's localization.h")

if(NOT SCILAB_LIBINTL_INCLUDE_DIR)
  # Not fatal: a platform whose libc provides gettext natively (glibc) needs no
  # extra -I at all, and failing the configure there would be wrong. Warn only
  # where it is actually needed.
  if(APPLE)
    message(WARNING
      "scilab_gateway: libintl.h not found. Scilab's localization.h includes it, "
      "so gateway compilation will fail. Install it with: brew install gettext")
  endif()
endif()

# Resolve a relative include list against a root, dropping entries that do not
# exist. A missing directory is a WARNING, not an error: a stripped-down install
# that omits, say, modules/mexlib/includes should still build gateways that do
# not use it, and -I on a nonexistent directory is merely noise on the command
# line. Silence would be worse -- that is how a truncated install turns into a
# confusing "file not found" three layers down.
function(_scilab_gateway_resolve_includes root relative_list out_var)
  set(_resolved "")
  set(_missing "")
  foreach(rel IN LISTS ${relative_list})
    if(IS_DIRECTORY "${root}/${rel}")
      list(APPEND _resolved "${root}/${rel}")
    else()
      list(APPEND _missing "${rel}")
    endif()
  endforeach()
  if(_missing)
    message(WARNING "scilab_gateway: ${root} is missing include directories, "
                    "gateways using them will not compile: ${_missing}")
  endif()
  set(${out_var} "${_resolved}" PARENT_SCOPE)
endfunction()

# scilab_gateway(<name>
#   SOURCES   foo.c bar.cpp baz.f     # required
#   LIBS      ...                     # link items, verbatim
#   C_FLAGS / CXX_FLAGS / Fortran_FLAGS / LINK_FLAGS
#   OUTPUT_DIRECTORY <dir>            # default: CMAKE_CURRENT_BINARY_DIR
# )
#
# Produces lib<name>.dylib carrying the policy the oracle records. See the
# numbered invariants in section 11 of the design doc; the load-bearing ones are
# repeated at the point they are implemented below.
function(scilab_gateway NAME)
  cmake_parse_arguments(G "" "OUTPUT_DIRECTORY"
    "SOURCES;LIBS;C_FLAGS;CXX_FLAGS;Fortran_FLAGS;LINK_FLAGS" ${ARGN})
  if(G_UNPARSED_ARGUMENTS)
    message(FATAL_ERROR "scilab_gateway(${NAME}): unparsed arguments: ${G_UNPARSED_ARGUMENTS}")
  endif()
  if(NOT G_SOURCES)
    message(FATAL_ERROR "scilab_gateway(${NAME}): SOURCES is required")
  endif()
  if(NOT SCILAB_ROOT)
    message(FATAL_ERROR "scilab_gateway(${NAME}): SCILAB_ROOT is not set. Load this "
                        "via find_package(Scilab) or set SCILAB_ROOT before including "
                        "ScilabGatewayPolicy.cmake.")
  endif()

  _scilab_gateway_resolve_includes("${SCILAB_ROOT}" SCILAB_GATEWAY_INCLUDES_RELATIVE _incs)
  _scilab_gateway_resolve_includes("${SCILAB_ROOT}" SCILAB_GATEWAY_Fortran_INCLUDES_RELATIVE _fincs)

  add_library(${NAME} SHARED ${G_SOURCES})

  # INVARIANT 1: the link driver is ALWAYS C++, even for a gateway whose every
  # source is C. ilib_build generates a C++ wrapper (lib<name>.cpp) into every
  # gateway, so libc++ is always on the link line. Letting CMake infer C here
  # would drop it and the gateway would fail to load with missing C++ runtime
  # symbols -- and only for the pure-C toolboxes, which is the worst way to find
  # out. Set unconditionally.
  set_target_properties(${NAME} PROPERTIES LINKER_LANGUAGE CXX)

  # __SCILAB_TOOLBOX__ on C/C++ only; the oracle's gfortran line carries no -D.
  target_compile_definitions(${NAME} PRIVATE
    "$<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:__SCILAB_TOOLBOX__>")

  # The build directory itself, FIRST. This is the `-I.` that opens every one of
  # the oracle's compile lines, and it is not decoration: ilib_build copies the
  # toolbox's sources AND its private headers into this directory, so a gateway
  # that writes `#include <its_own_header.h>` (angle brackets, which do not
  # search the including file's directory) resolves only because of it.
  #
  # Omitting it made csv-readwrite fail with "unknown type name
  # 'csv_complexArray'" -- its own type, from its own header, invisible. The
  # autotools path compiled the same file with nothing worse than a warning.
  # Both source and binary dir are listed so an out-of-tree build by hand (which
  # the generated CMakeLists invites) behaves the same as the in-source one
  # ilib_compile drives.
  set(_c_incs "${CMAKE_CURRENT_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}" ${_incs})
  if(SCILAB_LIBINTL_INCLUDE_DIR)
    list(APPEND _c_incs "${SCILAB_LIBINTL_INCLUDE_DIR}")
  endif()

  # Quoting the genexes is load-bearing: unquoted, the ;-lists inside would be
  # split into separate arguments mid-genex.
  target_include_directories(${NAME} PRIVATE
    "$<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:${_c_incs}>"
    "$<$<COMPILE_LANGUAGE:Fortran>:${_fincs}>")

  if(G_C_FLAGS OR G_CXX_FLAGS OR G_Fortran_FLAGS)
    target_compile_options(${NAME} PRIVATE
      "$<$<COMPILE_LANGUAGE:C>:${G_C_FLAGS}>"
      "$<$<COMPILE_LANGUAGE:CXX>:${G_CXX_FLAGS}>"
      "$<$<COMPILE_LANGUAGE:Fortran>:${G_Fortran_FLAGS}>")
  endif()

  if(G_LIBS)
    target_link_libraries(${NAME} PRIVATE ${G_LIBS})
  endif()

  # INVARIANT 3: -undefined dynamic_lookup. A gateway calls Scierror,
  # types::Function and friends, which live in the hosting process and resolve at
  # dlopen time, never at static link time. -no_fixup_chains rides along because
  # dynamic_lookup is incompatible with chained fixups. Identical to what
  # scilab_module() applies to Scilab's own dylibs and to what libtool did here.
  target_link_options(${NAME} PRIVATE
    "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains" ${G_LINK_FLAGS})

  # Name it lib<name>.dylib -- what loader.sce link()s and what cleaner.sce
  # deletes. libtool additionally produced lib<name>.0.dylib plus an unversioned
  # symlink to it; nothing reads the versioned name (the generated loader
  # references the plain one), so the single file is the whole contract.
  #
  # INVARIANT 4: install_name /usr/local/lib/scilab/lib<name>.dylib -- a path
  # that does not exist in the bundle. Preserved deliberately. It is harmless
  # only because gateways are loaded by EXPLICIT path via link(); the value is
  # kept identical to libtool's so that nothing which happens to read it starts
  # seeing a different answer.
  set_target_properties(${NAME} PROPERTIES
    OUTPUT_NAME "${NAME}" PREFIX "lib" SUFFIX ".dylib"
    INSTALL_NAME_DIR "/usr/local/lib/scilab"
    BUILD_WITH_INSTALL_NAME_DIR TRUE)
  if(G_OUTPUT_DIRECTORY)
    set_target_properties(${NAME} PROPERTIES LIBRARY_OUTPUT_DIRECTORY "${G_OUTPUT_DIRECTORY}")
  endif()

  # Re-sign on macOS. The linker's own ad-hoc signature can PASS
  # `codesign --verify` and still be killed by AMFI at dlopen -- SIGKILL with an
  # empty stderr and a CODESIGNING/Invalid Page entry in
  # ~/Library/Logs/DiagnosticReports. It cost a full debugging session on
  # sciTorch. Making it part of the shared policy rather than something each
  # toolbox author remembers is the whole point of having a shared policy;
  # cmake/ has no codesign anywhere today (noted in section 2 of the design doc).
  if(APPLE)
    add_custom_command(TARGET ${NAME} POST_BUILD
      COMMAND codesign --force --sign - $<TARGET_FILE:${NAME}>
      VERBATIM
      COMMENT "Re-signing $<TARGET_FILE_NAME:${NAME}> (linker ad-hoc signatures can be AMFI-killed at dlopen)")
  endif()
endfunction()
