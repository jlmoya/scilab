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
  # drop-in pattern. .libs/ is the libtool layout bin/scilab and package-macos.sh
  # read from; the location stays even though autotools, which invented it, is gone.
  add_custom_target(drop-in-${NAME}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_SOURCE_DIR}/.libs
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/$<TARGET_FILE_NAME:${NAME}>
    COMMAND ${CMAKE_COMMAND} -E create_symlink
            $<TARGET_FILE_NAME:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/${NAME}.dylib
    DEPENDS ${NAME} VERBATIM
    COMMENT "Dropping CMake-built ${NAME} into modules/.libs/ (launcher + packager read here)")
  if(TARGET drop-in-all)
    add_dependencies(drop-in-all drop-in-${NAME})
  endif()
endfunction()

# -----------------------------------------------------------------------------
# scilab_executable(<name>          # scilab-bin | scilab-cli-bin
#   SOURCES <src>...                # modules/startup/src/cpp/scilab.cpp (both;
#                                   # +initMPI.c only under MPI — macOS-inert)
#   LINK <target>...                # the scilab_aggregate target, preceded by
#                                   # any module targets the LDADD names BEFORE
#                                   # it (scilab-bin: sciconsole scijvm
#                                   # scicommons libscilab — libtool's dedupe
#                                   # kept the LAST libscilab.la occurrence, so
#                                   # console/jvm/commons genuinely precede the
#                                   # aggregate on the baseline link line)
#   LDADD_LIBS <item>...            # the ordered tail of the link line: module
#                                   # targets, -l<name>, -L<dir>, -framework
#                                   # pairs — the scilab_module SYSTEM_LIBS
#                                   # spelling conventions apply
#   [LDFLAGS <opt>...]              # extra link options after the two base
#                                   # rpaths (scilab-bin: the JDK LC_RPATH)
#   [COMPILE_DEFINITIONS <def>...]  # scilab-cli-bin: WITHOUT_GUI
#   [EXTRA_INCLUDES <dir>...]       # scilab_bin_CPPFLAGS dirs beyond the
#                                   # shared Scilab include base
#   [ALIAS <name>])                 # also drop a copy under this second name
#                                   # (scilab-bin: Scilab-<version>, the
#                                   # macos-process-name hardlink the top-level
#                                   # libtool wrapper was sed'd to exec)
#
# Creates:  <name>            executable in the top build dir
#           drop-in-<name>    copies it into <caller-source-dir>/.libs/ (the
#                             executables are declared at the TOP level, so
#                             .libs/ = the autotools bin_PROGRAMS location);
#                             registered onto drop-in-all.
#
# THE LINK SHAPE (ground truth: `libtool --dry-run --mode=link` on the exact
# `make scilab-cli-bin`/`make scilab-bin` invocations, then byte-identical
# LC_LOAD_DYLIB/LC_RPATH/LC_BUILD_VERSION verified against .libs/* — libtool
# REWRITES the automake LDADD, so the Makefile.am order alone is NOT the truth):
#
#  * `-Wl,-framework -Wl,CoreFoundation` FIRST. LTLIBINTL spells CoreFoundation
#    as -Wl,* tokens, so libtool classifies it a linker FLAG (not a deplib) and
#    emits it right after the objects — BEFORE every library. It is therefore
#    the executables' first LC_LOAD_DYLIB, i.e. the fingerprint's install_name
#    slot (executables have no LC_ID_DYLIB; the harness records the first dep
#    there and diffs it). Emitted from LINK_OPTIONS: CMake places those before
#    the objects, which preserves "first dylib mentioned".
#
#  * `-Wl,-bind_at_load`: libtool darwin appends it to every program link
#    (hardcode_ld_flag). Kept for bit-level faithfulness.
#
#  * `-lstdc++` immediately after: from the executables' *_LDFLAGS (the
#    "Clang needs an explicit reference" Makefile.am branch). The Apple clang++
#    driver REWRITES -lstdc++ to -lc++ (verified via -###), which resolves in
#    the -L path to miniconda's libc++ — recording @rpath/libc++.1.dylib as dep
#    slot 2 (the same machine-config accident class as gfortran/curl, see the
#    aggregate header) — then ld warns `ignoring duplicate libraries: '-lc++'`
#    when the driver's own implicit -lc++ arrives at the end of the line. That
#    warning is baseline-authentic noise, not a defect. The paired -lgfortran
#    from the same LDFLAGS is NOT emitted here: libtool's dedupe kept only the
#    FLIBS occurrence near the end of the line, so it belongs in LDADD_LIBS.
#
#  * LC_RPATH = /usr/lib, gcc/current (the SCI_LDFLAGS pair, order-checked by
#    the harness), then any LDFLAGS extras (scilab-bin's JDK dir — JAVA_JNI_LIBS
#    carries it as -Wl,-rpath, which libtool also hoists into linker_flags, so
#    it lands third). SKIP_BUILD_RPATH keeps CMake from growing the list.
#
#  * LC_BUILD_VERSION minos=sdk=$(MIN_MACOSX_VERSION): the executables' own
#    `-Wl,-platform_version,macos,min,min` (Makefile.am pins the SDK stamp to
#    the deployment target — macOS gates AppKit main-thread assertions on the
#    main executable's SDK stamp; a current-SDK stamp SIGTRAPs Scilab's
#    off-main-thread graphics init). Last -platform_version on the line wins
#    over the driver's computed one. MIN_MACOSX_VERSION == the toolchain's
#    CMAKE_OSX_DEPLOYMENT_TARGET (11.0).
#
#  * No -undefined dynamic_lookup, no -nostdlib++: programs are NOT dylibs —
#    libtool gave them neither; the C++ driver's implicit -lc++ is authentic
#    here (it deduped against the -lstdc++ rewrite above).
#
#  * `-sectcreate __TEXT __info_plist etc/macos-usage-descriptions.plist`: the
#    TCC usage descriptions, carried in the binary because Scilab's Mach-O sits
#    outside Contents/MacOS/ and so has no bundle Info.plist. Not part of the
#    autotools baseline -- a deliberate, parity-neutral addition (a section, not
#    a load command).
# -----------------------------------------------------------------------------
function(scilab_executable NAME)
  cmake_parse_arguments(E "" "ALIAS"
    "SOURCES;LINK;LDADD_LIBS;LDFLAGS;COMPILE_DEFINITIONS;EXTRA_INCLUDES" ${ARGN})
  if(E_UNPARSED_ARGUMENTS)
    message(FATAL_ERROR "scilab_executable(${NAME}): unparsed arguments: ${E_UNPARSED_ARGUMENTS}")
  endif()
  if(NAME MATCHES "^lib")
    message(FATAL_ERROR "scilab_executable(${NAME}): NAME is an executable, not a lib")
  endif()
  if(NOT E_SOURCES OR NOT E_LINK OR NOT E_LDADD_LIBS)
    message(FATAL_ERROR "scilab_executable(${NAME}): SOURCES, LINK and LDADD_LIBS are required")
  endif()
  # Typo guard, same spirit as scilab_aggregate(): LINK entries are always
  # targets; LDADD_LIBS entries that don't look like linker flags must be too.
  foreach(t IN LISTS E_LINK E_LDADD_LIBS)
    if(NOT t MATCHES "^-" AND NOT TARGET ${t})
      message(FATAL_ERROR "scilab_executable(${NAME}): '${t}' is not a target — "
                          "declare the executables after the aggregates")
    endif()
  endforeach()

  add_executable(${NAME} ${E_SOURCES})

  # Compile scilab.cpp with the ONE transcribed flag truth (SCI_CXXFLAGS facts:
  # O2/fwrapv/g1/min-macos/NDEBUG/c++17) + the shared include machinery.
  # _dir = the source root: automake's `-I.` on this in-tree build IS the top
  # dir, and the _dir/includes|src/c|src/cpp extras it implies don't exist at
  # the top level — harmless, exactly like modules that lack src/cpp.
  set(_dir ${SCILAB_SOURCE_DIR})
  set(M_EXTRA_INCLUDES ${E_EXTRA_INCLUDES})
  _scilab_module_flag_env()
  _scilab_module_apply(${NAME})
  if(E_COMPILE_DEFINITIONS)
    target_compile_definitions(${NAME} PRIVATE ${E_COMPILE_DEFINITIONS})
  endif()

  set_target_properties(${NAME} PROPERTIES
    LINKER_LANGUAGE CXX      # scilab.cpp is C++; the CXX driver is load-bearing
                             # (its implicit -lc++ dedupes the -lstdc++ rewrite)
    SKIP_BUILD_RPATH TRUE)   # LC_RPATH is EXACTLY the list below, in order

  target_link_options(${NAME} PRIVATE
    "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current"
    ${E_LDFLAGS}
    "LINKER:-platform_version,macos,${CMAKE_OSX_DEPLOYMENT_TARGET},${CMAKE_OSX_DEPLOYMENT_TARGET}"
    "LINKER:-framework,CoreFoundation"
    "LINKER:-bind_at_load")

  # macOS TCC: this process is the one that opens a camera (scicv/OpenCV's
  # AVFoundation backend), and TCC aborts it with
  # __TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__ unless it can read a usage
  # description for the REQUESTING BINARY. The app-bundle route never reaches
  # us: CFBundleExecutable is a shell script in Contents/MacOS/ that execs
  # Contents/Resources/scilab/.libs/Scilab-<version>, so [NSBundle mainBundle]
  # resolves to .libs/ -- no Info.plist there. Apple's route for an executable
  # outside a bundle is to carry the plist inside the Mach-O.
  #
  # PARITY: -sectcreate adds a __TEXT SECTION, not a load command. The
  # executables dimension fingerprints build_version, first dep (install_name
  # slot), the sorted dep set and the ordered rpaths (build-parity/parity/
  # capture.py::_fingerprint_exe) -- none of which a section touches. Expect a
  # clean diff; if it is not clean, something else changed, so investigate
  # rather than re-baseline.
  #
  # LINK_DEPENDS makes an edit to the plist relink the executable; without it
  # CMake sees no changed input and the stale section survives.
  if(APPLE)
    set(_usage_plist ${SCILAB_SOURCE_DIR}/etc/macos-usage-descriptions.plist)
    if(NOT EXISTS ${_usage_plist})
      message(FATAL_ERROR "scilab_executable(${NAME}): missing ${_usage_plist}")
    endif()
    target_link_options(${NAME} PRIVATE
      "LINKER:-sectcreate,__TEXT,__info_plist,${_usage_plist}")
    set_property(TARGET ${NAME} APPEND PROPERTY LINK_DEPENDS ${_usage_plist})
  endif()

  # Post-object order: -lstdc++, the LINK head (aggregate + its predecessors),
  # then the transcribed LDADD tail — LC_LOAD_DYLIB order IS this order, and
  # two-level-namespace symbol bindings follow it (the Makefile.am xerbla note:
  # BLAS/LAPACK stay at the END).
  #
  # Targets are linked by $<TARGET_FILE:> path, NOT by name — deliberately.
  # Linked by name they enter CMake's link-dependency graph, which reorders a
  # library to sit AFTER every already-listed library that depends on it; the
  # baseline order genuinely violates that rule (scilab-bin lists console/jvm/
  # commons BEFORE their dependents libscilab/types-java/external_objects_java/
  # helptools), so name-linking demonstrably shuffled those four to the end of
  # the line (dep-SET-equal — the harness stayed green — but LC_LOAD_DYLIB
  # order drifted from the baseline, and load order is what flat-namespace
  # `dynamic_lookup` resolution searches when two images export one symbol:
  # sciconsole must stay ahead of sciconsole-minimal). File-path items are
  # emitted exactly where declared; the recorded dep is still the dylib's
  # install_name, and add_dependencies() restores the build ordering that
  # name-linking would have given.
  set(_link_items "")
  set(_link_dep_targets "")
  foreach(t IN LISTS E_LINK E_LDADD_LIBS)
    if(t MATCHES "^-")
      list(APPEND _link_items "${t}")
    else()
      list(APPEND _link_items "$<TARGET_FILE:${t}>")
      list(APPEND _link_dep_targets ${t})
    endif()
  endforeach()
  target_link_libraries(${NAME} PRIVATE -lstdc++ ${_link_items})
  add_dependencies(${NAME} ${_link_dep_targets})

  # Drop-in: copy into the caller's .libs/ — the top-level .libs/ where
  # automake's bin_PROGRAMS land and where the libtool wrapper scripts (and
  # bin/scilab*) exec them. ALIAS additionally refreshes the process-name copy
  # (.libs/Scilab-<version>): the GUI wrapper was sed'd by macos-process-name
  # to exec THAT file, so without it a drop-in would leave a wrapper launching
  # the stale autotools binary. The autotools originals are recoverable with
  # `make scilab-bin scilab-cli-bin macos-process-name`.
  set(_alias_copy "")
  if(E_ALIAS)
    set(_alias_copy COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/${E_ALIAS})
  endif()
  add_custom_target(drop-in-${NAME}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_SOURCE_DIR}/.libs
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:${NAME}> ${CMAKE_CURRENT_SOURCE_DIR}/.libs/${NAME}
    ${_alias_copy}
    DEPENDS ${NAME} VERBATIM
    COMMENT "Dropping CMake-built ${NAME} into .libs/ (launcher + packager read here)")
  if(TARGET drop-in-all)
    add_dependencies(drop-in-all drop-in-${NAME})
  endif()
endfunction()
