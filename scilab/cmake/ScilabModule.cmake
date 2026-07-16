# scilab/cmake/ScilabModule.cmake — ALL per-module policy, once.
#
#   scilab_module(<name>
#     [ALGO_SOURCES <src>...]      # automake noinst convenience lib -> OBJECT lib
#     GATEWAY_SOURCES <src>...     # sources of the gateway dylib
#     [LANG <C|CXX|Fortran>...]    # languages compiled; CXX/Fortran pin the C++ linker
#     [SYSTEM_LIBS <lib>...]       # plain -l names resolved in the SDK sysroot
#                                  # (xml2 z icucore ... — NOT find_package'd: Find
#                                  # modules can resolve to Homebrew kegs, changing
#                                  # the recorded dep path vs the /usr/lib baseline).
#                                  # Also accepts absolute dylib paths (pin a
#                                  # Homebrew keg / bundled lib so the recorded dep
#                                  # is that file's install_name — the libomp
#                                  # pattern) and raw linker flags (-L<dir>) where
#                                  # a module must reproduce autotools' search-path
#                                  # side effects (webtools).
#     [FRAMEWORKS <name>...]       # macOS frameworks: linked as `-framework <name>`,
#                                  # the autotools spelling (localization's
#                                  # `-framework Cocoa`). Recorded like any dep;
#                                  # symbols resolved through an umbrella's
#                                  # reexports additionally record the sub-framework
#                                  # load command (Cocoa -> + CoreFoundation),
#                                  # matching libtool's link exactly.
#     [FIND_PACKAGES <pkg>...]     # CMake-resolved external deps (e.g. OpenMP)
#     [MODULE_DEPS <target>...]    # sibling scilab_module targets (sci<dep>): orders
#                                  # the build + records the sibling install_name
#     [EXTRA_INCLUDES <dir>...]    # include dirs beyond SCILAB_DEFAULT_INCLUDES
#     [CLASS ENGINE_LIBS|DYNAMIC_LOAD|GUI_LIBS]  # linking class per modules/Makefile.am
#                                  # (declarative metadata; all classes drop in
#                                  # relink-free — see the exemplar rationale.
#                                  # GUI_LIBS behaves like ENGINE_LIBS: its
#                                  # dylib is a DIRECT dyld dependency of the
#                                  # libscilab aggregate + the executables and
#                                  # loads at process launch (verified by otool
#                                  # -L on libscilab.2027.dylib/scilab-bin:
#                                  # libscirenderer is a direct load; renderer
#                                  # has no sci_gateway, is never dlopen'd). It
#                                  # differs from ENGINE_LIBS only in living in
#                                  # the GUI aggregate link list, not the engine
#                                  # one. DYNAMIC_LOAD is the OPPOSITE — linked
#                                  # into nothing, dlopen'd on demand. The
#                                  # drop-in stays relink-free the same way
#                                  # ENGINE_LIBS does: the dep is recorded by
#                                  # install_name and the launcher's DYLD paths
#                                  # resolve the leaf name to .libs/)
#     [SYMBOLS <n>])               # expected exported-symbol count (documentation;
#                                  # the parity harness is the enforcing check)
#
# Creates:  sci<name>        the gateway SHARED lib -> libsci<name>.2027.dylib
#           sci<name>-algo   OBJECT convenience lib (only when ALGO_SOURCES given)
#           drop-in-<name>   copies the dylib into modules/<name>/.libs/ (+ its
#                            unversioned symlink); registered onto drop-in-all.
#
# PARITY CONTRACT (arbitrated by build-parity/ against baseline-autotools.json):
# every fact below — flags, includes, defines, link options, naming — is
# transcribed from the CONFIGURED autotools build (config.status SCI_*FLAGS +
# the per-module Makefile), not invented here. The four hand-written exemplars
# this helper generalizes (sound, parallel, coverage, interpolation at commits
# 38e81564f3f/f3d3a58fade/6b43d012ae3/531436d485a) are the reference shapes.

include(CMakeParseArguments)

# libtool: -version-number $(SCILAB_LIBRARY_VERSION) with 2027:0:0 names the
# file libsci<name>.2027.dylib and stamps Mach-O compat/current 2028.0.0
# (darwin libtool uses major+minor+1; minor==0 today — a future nonzero minor
# must be added to the math below too). The 4-digit token in the FILENAME is
# what the parity harness keys on (libsci<name>.VER.dylib): it must be exactly
# ".2027." with nothing after it before ".dylib".
set(SCILAB_LIBRARY_VERSION_MAJOR 2027)
math(EXPR SCILAB_MACHO_VERSION "${SCILAB_LIBRARY_VERSION_MAJOR} + 1")

# Apply the shared per-language defines/includes/flags to one target. Called
# only from scilab_module(); CMake's dynamic scoping makes the caller's
# _incs/_cflags/_cxxflags/_fflags (and SCILAB_SOURCE_DIR) visible here. The
# genexes MUST stay quoted: unquoted, the ;-lists inside would be split into
# separate arguments mid-genex.
function(_scilab_module_apply tgt)
  # DEFS from the autotools compile line — C/C++ only: automake's F77 rule
  # carries no $(DEFS) (no -DHAVE_CONFIG_H on Fortran TUs).
  target_compile_definitions(${tgt} PRIVATE
    "$<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:HAVE_CONFIG_H>")

  # C/C++ include set. Fortran TUs get NONE of it — automake's F77 rule is
  # $(F77) $(AM_FFLAGS) $(FFLAGS) with AM_FFLAGS = $(SCI_FFLAGS)
  # -I modules/core/includes/ ONLY.
  target_include_directories(${tgt} PRIVATE
    "$<$<NOT:$<COMPILE_LANGUAGE:Fortran>>:${_incs}>"
    "$<$<COMPILE_LANGUAGE:Fortran>:${SCILAB_SOURCE_DIR}/modules/core/includes>")

  target_compile_options(${tgt} PRIVATE
    "$<$<COMPILE_LANGUAGE:C>:${_cflags}>"
    "$<$<COMPILE_LANGUAGE:CXX>:${_cxxflags}>"
    "$<$<COMPILE_LANGUAGE:Fortran>:${_fflags}>")

  # DEFINE_SYMBOL "": drop CMake's automatic <target>_EXPORTS define —
  # autotools defines no such symbol (it would only matter under _MSC_VER).
  # PIC: a no-op on darwin (compiler default) but required on ELF targets
  # whenever OBJECT-lib objects fold into a SHARED lib — set for portability.
  set_target_properties(${tgt} PROPERTIES
    DEFINE_SYMBOL "" POSITION_INDEPENDENT_CODE ON)
endfunction()

function(scilab_module NAME)
  cmake_parse_arguments(M "" "CLASS;SYMBOLS"
    "ALGO_SOURCES;GATEWAY_SOURCES;LANG;SYSTEM_LIBS;FIND_PACKAGES;MODULE_DEPS;EXTRA_INCLUDES;FRAMEWORKS" ${ARGN})
  if(M_UNPARSED_ARGUMENTS)
    message(FATAL_ERROR "scilab_module(${NAME}): unparsed arguments: ${M_UNPARSED_ARGUMENTS}")
  endif()
  if(NOT M_GATEWAY_SOURCES)
    message(FATAL_ERROR "scilab_module(${NAME}): GATEWAY_SOURCES is required")
  endif()
  if(M_CLASS AND NOT M_CLASS MATCHES "^(ENGINE_LIBS|DYNAMIC_LOAD|GUI_LIBS)$")
    message(FATAL_ERROR "scilab_module(${NAME}): CLASS must be ENGINE_LIBS, "
                        "DYNAMIC_LOAD or GUI_LIBS, got '${M_CLASS}'")
  endif()
  set(_dir ${CMAKE_CURRENT_SOURCE_DIR})

  # --- flags, per language (transcribed SCI_*FLAGS; semantic parity facts:
  # O2 + fwrapv + min_macos 11.0 + NDEBUG). The -Werror pair is C-only (they
  # are C diagnostics; SCI_CXXFLAGS omits them). Language standards are pinned
  # by the migration constraint: C gnu23, C++ c++17 (do NOT raise). ---
  set(_cflags   -std=gnu23 -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
                -fno-stack-protector -Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types)
  set(_cxxflags -std=c++17 -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0
                -fno-stack-protector -Wall -Wpedantic)
  # SCI_FFLAGS. -mmacosx-version-min is LOAD-BEARING here: CMake applies
  # neither CMAKE_OSX_* variable to Fortran TUs, so this flag is Fortran's only
  # source of it. No -Wall/-Wpedantic/-fno-stack-protector/-std — the F77 rule
  # has none. (Fortran's single include dir is applied per-language in
  # _scilab_module_apply, not spelled as a -I flag here.)
  set(_fflags   -DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0)

  # The C/C++ include set, in automake's order: DEFAULT_INCLUDES first —
  # `-I. -I$(top_builddir)/modules/core/includes` PRECEDES every per-target
  # CPPFLAGS dir on the automake compile line, so core/includes must beat the
  # module-local dirs (load-bearing: console keeps a STALE local
  # includes/initMacOSXEnv.h that core/includes' current one always shadowed) —
  # then the la_CPPFLAGS dirs (includes/ src/c src/cpp, the shared Scilab base,
  # the module's extras), then the configure-detected Homebrew base. CMake
  # de-duplicates repeats keeping the first position — same preprocessor result
  # (core/includes leads SCILAB_DEFAULT_INCLUDES too, so it simply collapses).
  set(_incs ${_dir} ${SCILAB_SOURCE_DIR}/modules/core/includes
            ${_dir}/includes ${_dir}/src/c ${_dir}/src/cpp
            ${SCILAB_DEFAULT_INCLUDES} ${M_EXTRA_INCLUDES} ${SCILAB_HOMEBREW_INCLUDES})

  # --- find_package deps (e.g. OpenMP) ---
  set(_link_libs "")
  foreach(pkg IN LISTS M_FIND_PACKAGES)
    if(pkg STREQUAL "OpenMP")
      # Apple clang ships no libomp: default to the Homebrew keg (an explicit
      # -DOpenMP_ROOT= or env override still wins, CMP0074). Linking the
      # imported target records the dep at libomp's ABSOLUTE install_name —
      # /opt/homebrew/opt/libomp/lib/libomp.dylib — exactly what autotools'
      # -lomp recorded (NOT an @rpath form), and contributes the compile flag
      # (the harness's openmp=True fact).
      if(NOT DEFINED OpenMP_ROOT AND NOT DEFINED ENV{OpenMP_ROOT})
        set(OpenMP_ROOT /opt/homebrew/opt/libomp)
      endif()
      find_package(OpenMP REQUIRED COMPONENTS C CXX)
      list(APPEND _link_libs OpenMP::OpenMP_C)
    else()
      # Fail loudly rather than find_package()-and-drop: a bare find_package
      # here would locate the package but never link its imported target (the
      # dep would surface only as a confusing downstream parity diff, not at the
      # declaration site). The next author must wire the target deliberately.
      message(FATAL_ERROR "scilab_module: FIND_PACKAGES currently supports only "
                          "OpenMP; wire ${pkg} into ScilabModule.cmake (link its "
                          "imported target) before using it here")
    endif()
  endforeach()

  # --- OBJECT convenience lib (never STATIC) ---
  # automake's noinst .la means "compile these sources, fold ALL their objects
  # into whatever links me"; the exact CMake equivalent is an OBJECT library
  # consumed via $<TARGET_OBJECTS:...>. A STATIC lib would only pull members
  # that resolve an outstanding reference, silently dropping algo-only exports
  # the baseline records.
  set(_algo_obj "")
  if(M_ALGO_SOURCES)
    add_library(sci${NAME}-algo OBJECT ${M_ALGO_SOURCES})
    _scilab_module_apply(sci${NAME}-algo)
    set(_algo_obj $<TARGET_OBJECTS:sci${NAME}-algo>)
  endif()

  # --- the gateway SHARED lib ---
  add_library(sci${NAME} SHARED ${_algo_obj} ${M_GATEWAY_SOURCES})
  _scilab_module_apply(sci${NAME})

  # The linker language is load-bearing: the C++ driver links libc++ (a
  # baseline dep of every C++/Fortran module), while a pure-C module must NOT
  # get it (deps stay libSystem-only — so the C driver links). For targets
  # containing Fortran objects, CMake additionally appends its detected
  # Fortran implicit-link info (the $(FLIBS) equivalent) to the C++ link line:
  # libgfortran/libquadmath ride in at their absolute Homebrew install_names,
  # the emutls_w/heapt_w/gcc helpers fold in as static .a's (no dylib dep).
  if("CXX" IN_LIST M_LANG OR "Fortran" IN_LIST M_LANG)
    set_target_properties(sci${NAME} PROPERTIES LINKER_LANGUAGE CXX)
  endif()

  # Each FRAMEWORKS name becomes one `-framework <name>` link item (the single
  # list element keeps the pair together; CMake emits it verbatim on the link
  # line). Kept out of SYSTEM_LIBS so the call sites stay self-documenting.
  foreach(fw IN LISTS M_FRAMEWORKS)
    list(APPEND _link_libs "-framework ${fw}")
  endforeach()

  target_link_libraries(sci${NAME} PRIVATE ${_link_libs} ${M_SYSTEM_LIBS} ${M_MODULE_DEPS})

  # libtool's darwin allow_undefined_flag, VERBATIM: gateways call Scilab API
  # symbols (Scierror, types::Function, ...) that live in the hosting process —
  # they resolve at dlopen/dyld-load time, never at static link time. This is
  # precisely why the autotools dylibs' deps stop at system/runtime libs.
  # -no_fixup_chains rides along (dynamic_lookup is incompatible with chained
  # fixups; libtool disables them, and it changes the dyld load commands).
  # The two rpaths are the SCI_LDFLAGS LC_RPATHs libtool stamped into every
  # module dylib.
  target_link_options(sci${NAME} PRIVATE
    "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains"
    "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")

  set_target_properties(sci${NAME} PROPERTIES
    # EXACT filename libsci<name>.2027.dylib: version baked into OUTPUT_NAME.
    # Deliberately NOT CMake VERSION/SOVERSION — those would name it
    # libsci<name>.2027.0.0.dylib, which breaks the harness key.
    OUTPUT_NAME "sci${NAME}.${SCILAB_LIBRARY_VERSION_MAJOR}"
    PREFIX "lib" SUFFIX ".dylib"
    # libtool's `-rpath $(pkglibdir)` == absolute install_name, not @rpath.
    INSTALL_NAME_DIR "/usr/local/lib/scilab"
    BUILD_WITH_INSTALL_NAME_DIR TRUE
    # libtool 2027:0:0 -> compat/current 2028.0.0 (harness strips these, but
    # match them anyway for bit-level load-command fidelity).
    MACHO_COMPATIBILITY_VERSION "${SCILAB_MACHO_VERSION}.0.0"
    MACHO_CURRENT_VERSION "${SCILAB_MACHO_VERSION}.0.0")

  # libtool also leaves an unversioned symlink next to the versioned dylib.
  add_custom_command(TARGET sci${NAME} POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:sci${NAME}> ${CMAKE_CURRENT_BINARY_DIR}/libsci${NAME}.dylib
    VERBATIM
    COMMENT "Symlinking libsci${NAME}.dylib -> $<TARGET_FILE_NAME:sci${NAME}>")

  # --- drop-in: copy the dylib into modules/<name>/.libs/ + recreate the
  # symlink. A REAL copy, not a symlink: the parity harness skips symlinked
  # dylibs, and the autotools original stays recoverable by simply rebuilding
  # (`make -C modules/<name>`). ---
  add_custom_target(drop-in-${NAME}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${_dir}/.libs
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:sci${NAME}> ${_dir}/.libs/$<TARGET_FILE_NAME:sci${NAME}>
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:sci${NAME}> ${_dir}/.libs/libsci${NAME}.dylib
    DEPENDS sci${NAME} VERBATIM
    COMMENT "Dropping CMake-built ${NAME} into modules/${NAME}/.libs/ (hybrid coexistence)")
  if(TARGET drop-in-all)
    add_dependencies(drop-in-all drop-in-${NAME})
  endif()
endfunction()
