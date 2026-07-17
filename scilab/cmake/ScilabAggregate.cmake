# scilab/cmake/ScilabAggregate.cmake — the libscilab / libscilab-cli aggregates.
#
#   scilab_aggregate(<name>        # libscilab | libscilab-cli
#     FOLD_OBJECTS <sci*-obj>...   # fold-in OBJECT libs (the 21 Task-3 core
#                                  # modules): their objects FOLD into this
#                                  # dylib via $<TARGET_OBJECTS:...> and record
#                                  # NO dep — automake noinst convenience-.la
#                                  # semantics (on macOS those modules ship no
#                                  # standalone dylib).
#     LINK_MODULES <sci*>...       # pkglib module targets (Stage-1e dylibs):
#                                  # LINKED, recording each dylib's
#                                  # /usr/local/lib/scilab install_name as an
#                                  # LC_LOAD_DYLIB dep — automake pkglib-.la
#                                  # semantics. The fold-vs-link split is THE
#                                  # parity-defining classification; the
#                                  # harness diffs the exact dep set.
#     [SYSTEM_LIBS <lib>...]       # same contract as scilab_module(): plain -l
#                                  # names resolved in the SDK sysroot (xslt
#                                  # xml2 z icucore curses), or absolute dylib
#                                  # paths pinning a keg/miniconda/JDK file so
#                                  # the recorded dep is that file's
#                                  # install_name.
#     [FRAMEWORKS <name>...])      # `-framework <name>` link items (Cocoa).
#
# Creates:  <name>            SHARED lib -> <name>.2027.dylib (e.g.
#                             libscilab.2027.dylib — the version is baked into
#                             OUTPUT_NAME exactly like scilab_module()).
#           drop-in-<name>    copies the dylib into modules/.libs/ (the
#                             CALLER's dir — the aggregates are declared in
#                             modules/CMakeLists.txt) + the unversioned
#                             symlink; registered onto drop-in-all.
#
# WHY the link shape below is exactly what it is (all baseline-verified on
# modules/.libs/libscilab.2027.dylib + libscilab-cli.2027.dylib):
#
#  * NO libc++ dep. modules/Makefile.am declares `libscilab_la_SOURCES =`
#    (empty), so automake emitted the plain-C link rule: libtool linked the
#    aggregates with the C driver, and the folded C++ objects' libc++/libc++abi
#    references simply stayed UNDEFINED (verified: `nm -u` shows __ZNSt...
#    symbols; `otool -L` records no libc++), resolving at load time through
#    libtool's darwin `-undefined dynamic_lookup`. Under CMake the linker
#    language is CXX (the migration's pinned rule for mixed-language targets),
#    whose clang++ driver would add -lc++ implicitly — `-nostdlib++` suppresses
#    exactly that, reproducing the C-driver link.
#
#  * NO CMake-implicit Fortran runtime. The aggregates contain Fortran objects
#    (cacsd/elementary_functions/sparse/... fold in), so CMake would append
#    CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES (emutls_w;heapt_w;gfortran;gcc;
#    quadmath) + its -L dirs to the link line. That is WRONG here twice over:
#    (1) it resolves -lgfortran/-lquadmath in the Homebrew gcc keg, whose
#    dylibs carry ABSOLUTE /opt/homebrew/opt/gcc/... install_names — but the
#    baseline records @rpath/libgfortran.5.dylib + @rpath/libquadmath.0.dylib
#    (see the FLIBS note below); (2) the emutls_w/heapt_w archives would fold
#    members in and EXPORT __emutls symbols the baseline does not have. The
#    caller must therefore clear CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES/
#    _DIRECTORIES in its directory scope (guarded by a FATAL_ERROR below) and
#    pass the Fortran runtime explicitly through SYSTEM_LIBS.
#
#  * FLIBS -> @rpath gfortran/quadmath. The autotools LIBADD ends with
#    $(FLIBS) $(LAPACK_LIBS) $(BLAS_LIBS); by that point the link line already
#    carries `-L/Users/<user>/miniconda3/lib` (ridden in from webtools'
#    .la dependency_libs), and ld resolves EVERY -l against ALL -L dirs in
#    command-line order — so the baseline's -lgfortran/-lquadmath landed on
#    miniconda's copies, whose install_names are @rpath/libgfortran.5.dylib /
#    @rpath/libquadmath.0.dylib. At RUN time those @rpath deps resolve through
#    the LC_RPATH /opt/homebrew/opt/gcc/lib/gcc/current stamped below — i.e.
#    the app actually loads the Homebrew gcc runtime. The aggregates reproduce
#    the recorded fact by linking the miniconda files explicitly (SYSTEM_LIBS
#    absolute-path pattern; same story as webtools' @rpath/libcurl.4.dylib).
#    -lemutls_w/-lheapt_w contributed no member to the baseline (zero emutls/
#    heapt symbols in it, defined or undefined) and are deliberately omitted.
#
#  * `-undefined dynamic_lookup` + `-no_fixup_chains`: libtool's darwin
#    allow_undefined_flag, exactly as in scilab_module(). The baseline
#    aggregates have undefined symbols (libc++, dlopen'd DYNAMIC_LOAD-module
#    gateways) and NO chained fixups (verified: zero LC_DYLD_CHAINED_FIXUPS).
#
#  * Identity rules (OUTPUT_NAME with the baked 4-digit version / PREFIX /
#    SUFFIX / absolute INSTALL_NAME_DIR / MACHO 2028.0.0 versions / the two
#    SCI_LDFLAGS rpaths in order) mirror scilab_module() — the parity harness
#    keys on lib<stem>.VER.dylib and checks install_name + ordered rpaths.
#
# PARITY CONTRACT (arbitrated by build-parity/ against baseline-autotools.json):
# libscilab.VER.dylib must record exactly the baseline's 59 deps (36 module
# dylibs + 23 system/keg libs incl. Cocoa + libSystem) and libscilab-cli.VER
# exactly its 39 (18 + 21); both export the identical 3543-symbol set (the
# union of the folded objects' non-hidden globals); folded modules must NOT
# appear as deps, linked modules MUST.

include(CMakeParseArguments)

function(scilab_aggregate NAME)
  cmake_parse_arguments(A "" ""
    "FOLD_OBJECTS;LINK_MODULES;SYSTEM_LIBS;FRAMEWORKS" ${ARGN})
  if(A_UNPARSED_ARGUMENTS)
    message(FATAL_ERROR "scilab_aggregate(${NAME}): unparsed arguments: ${A_UNPARSED_ARGUMENTS}")
  endif()
  if(NOT NAME MATCHES "^lib")
    message(FATAL_ERROR "scilab_aggregate(${NAME}): NAME must start with 'lib' "
                        "(it is the target AND the on-disk stem: lib<stem>.2027.dylib)")
  endif()
  if(NOT A_FOLD_OBJECTS OR NOT A_LINK_MODULES)
    message(FATAL_ERROR "scilab_aggregate(${NAME}): FOLD_OBJECTS and LINK_MODULES are required")
  endif()

  # The caller's directory scope must have cleared the Fortran implicit link
  # info (see the header — absolute-gfortran deps + emutls exports otherwise).
  # Checked HERE so the requirement cannot silently rot: this reads the
  # caller's scope at call time, the same values the generator will use.
  if(CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES OR CMAKE_Fortran_IMPLICIT_LINK_DIRECTORIES)
    message(FATAL_ERROR "scilab_aggregate(${NAME}): the calling scope must set "
      "CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES and CMAKE_Fortran_IMPLICIT_LINK_DIRECTORIES "
      "to empty — CMake's implicit Fortran runtime would otherwise ride onto the "
      "aggregate link line and break dep/symbol parity (header comment).")
  endif()

  # Typo guard on the fold/link classification: every name must be a target
  # (the aggregates are declared AFTER all module subdirectories).
  foreach(t IN LISTS A_FOLD_OBJECTS A_LINK_MODULES)
    if(NOT TARGET ${t})
      message(FATAL_ERROR "scilab_aggregate(${NAME}): '${t}' is not a target — "
                          "declare the aggregates after every module add_subdirectory()")
    endif()
  endforeach()

  set(_objs "")
  foreach(o IN LISTS A_FOLD_OBJECTS)
    list(APPEND _objs $<TARGET_OBJECTS:${o}>)
  endforeach()

  add_library(${NAME} SHARED ${_objs})

  # Mixed C/C++/Fortran objects: pin the link language (Fortran would win
  # CMake's preference contest and link with gfortran — wrong driver, wrong
  # implicit runtime). -nostdlib++ below strips the clang++ driver's -lc++.
  set_target_properties(${NAME} PROPERTIES LINKER_LANGUAGE CXX)

  set(_link_libs "")
  foreach(fw IN LISTS A_FRAMEWORKS)
    list(APPEND _link_libs "-framework ${fw}")
  endforeach()

  target_link_libraries(${NAME} PRIVATE ${A_LINK_MODULES} ${_link_libs} ${A_SYSTEM_LIBS})

  target_link_options(${NAME} PRIVATE
    -nostdlib++
    "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains"
    "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")

  # lib<stem>.2027.dylib — identical identity rules to scilab_module():
  # version baked into OUTPUT_NAME (never CMake VERSION/SOVERSION), libtool's
  # absolute install_name, libtool 2027:0:0 -> Mach-O 2028.0.0.
  string(REGEX REPLACE "^lib" "" _stem "${NAME}")
  set_target_properties(${NAME} PROPERTIES
    OUTPUT_NAME "${_stem}.${SCILAB_LIBRARY_VERSION_MAJOR}"
    PREFIX "lib" SUFFIX ".dylib"
    INSTALL_NAME_DIR "/usr/local/lib/scilab"
    BUILD_WITH_INSTALL_NAME_DIR TRUE
    MACHO_COMPATIBILITY_VERSION "${SCILAB_MACHO_VERSION}.0.0"
    MACHO_CURRENT_VERSION "${SCILAB_MACHO_VERSION}.0.0"
    # LC_RPATH must be EXACTLY the two SCI_LDFLAGS entries above, in order
    # (the harness checks rpaths order-significantly). CMake auto-appends the
    # directory of any @rpath-install_name'd dylib linked by file path to the
    # build rpath (miniconda/JDK dirs would sneak in) — hard-off. SYSTEM_LIBS
    # therefore spells such libs as raw `-L<dir>` + plain name (the
    # webtools/jvm pattern), never as file paths.
    SKIP_BUILD_RPATH TRUE)

  # libtool also leaves the unversioned symlink next to the versioned dylib.
  add_custom_command(TARGET ${NAME} POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:${NAME}> ${CMAKE_CURRENT_BINARY_DIR}/${NAME}.dylib
    VERBATIM
    COMMENT "Symlinking ${NAME}.dylib -> $<TARGET_FILE_NAME:${NAME}>")

  # Drop-in: a REAL copy into <caller-source-dir>/.libs — modules/.libs/ for
  # the aggregates — plus the unversioned symlink, exactly the scilab_module()
  # hybrid-coexistence pattern (autotools original recoverable via
  # `make -C modules libscilab.la libscilab-cli.la`).
  add_custom_target(drop-in-${NAME}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_SOURCE_DIR}/.libs
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/$<TARGET_FILE_NAME:${NAME}>
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/${NAME}.dylib
    DEPENDS ${NAME} VERBATIM
    COMMENT "Dropping CMake-built ${NAME} into modules/.libs/ (hybrid coexistence)")
  if(TARGET drop-in-all)
    add_dependencies(drop-in-all drop-in-${NAME})
  endif()
endfunction()
