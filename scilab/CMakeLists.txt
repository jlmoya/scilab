# scilab/CMakeLists.txt — the CMake build driver (make/autotools retired 2026-07-21).
#
# Builds the whole native app: every module dylib via a one-call scilab_module()
# declaration (cmake/ScilabModule.cmake), the libscilab / libscilab-cli aggregates,
# the two executables, and the Maven module jars. `--target drop-in-all` yields a
# runnable tree; each artifact is copied into modules/<name>/.libs/ — the libtool
# layout bin/scilab and package-macos.sh still read from — by its drop-in target.
#
# SELF-CONTAINED headers: machine.h and version.h are GENERATED at configure time
# into build-cmake/generated-includes/ (machine.h from cmake/machine.h.cmake.in via
# ScilabMachineHeader.cmake, version.h from modules/core/includes/version.h.in via
# ScilabConfigure.cmake), and that dir is prepended to the include path, so the
# compile consumes the generated copies. A fresh clone needs only the two tracked
# templates — no configure, no pre-generated headers. (The old gitignored
# modules/core/includes/{machine.h,version.h} the retired configure produced are
# legacy now, present-but-unused; consumption cutover done, register §5a2.)
#
# Build:    cmake -S . -B build-cmake -DCMAKE_Fortran_COMPILER="$(which gfortran)"
#           cmake --build build-cmake --target drop-in-all     (or drop-in-<name>)
# Verify:   cd build-parity && python3 -m pytest -q
#
# (Makefile/Ninja generators only — the per-language $<COMPILE_LANGUAGE:...>
# includes/defines in ScilabModule.cmake don't express under -G Xcode/VS.)

cmake_minimum_required(VERSION 3.20)

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/cmake)

# RC-e.1 — the canonical version triple, CMake-native (no config.status read). Has no
# dependency on anything (not even project()/SCILAB_SOURCE_DIR), so it can and does go
# first: include()d before its two consumers, ScilabConfigure.cmake's version.h
# generation and ScilabGeneratedFiles.cmake's SCILAB_BINARY_VERSION/Version.incl.
include(cmake/ScilabVersion.cmake)

# Environment discovery (source root, compiler defaults, arch, include bases)
# — must precede project().
include(cmake/ScilabToolchain.cmake)

project(scilab-native C CXX Fortran)

# The parity target was built with gfortran (Homebrew gcc): the Fortran flag
# spellings and the implicit-runtime link argument in ScilabModule.cmake are
# gfortran facts; a stray flang would break both.
if(NOT CMAKE_Fortran_COMPILER_ID STREQUAL "GNU")
  message(FATAL_ERROR "Fortran compiler is ${CMAKE_Fortran_COMPILER_ID} "
                      "(${CMAKE_Fortran_COMPILER}); the parity target was built with "
                      "gfortran (Homebrew gcc). Pass -DCMAKE_Fortran_COMPILER=gfortran.")
endif()

# All per-module policy, once; the aggregate (libscilab/libscilab-cli) policy
# lives in its own helper.
include(cmake/ScilabModule.cmake)
include(cmake/ScilabAggregate.cmake)
include(cmake/ScilabJava.cmake)

# Stage 1f-c — CMake generates version.h into build-cmake/generated-includes/ and
# prepends it to SCILAB_DEFAULT_INCLUDES so the module builds below consume it.
include(cmake/ScilabConfigure.cmake)

# RC-a — CMake computes + generates machine.h beside the generated version.h.
include(cmake/ScilabMachineHeader.cmake)

# RC-b -- the compiler-flag policy, computed in CMake rather than transcribed.
include(cmake/ScilabFlags.cmake)

# ---- BLAS/LAPACK backend ----------------------------------------------------
# -DSCILAB_BLAS_BACKEND=armpl builds against Arm Performance Libraries instead of
# Homebrew OpenBLAS. Both are lp64 -- Fortran INTEGER is 32-bit -- and both export
# the same unsuffixed names (dgemm_, dgesv_, ...), so this is a pure link change:
# no source edits, and flipping the value back restores OpenBLAS exactly.
#
# ONLY the lp64 variant is safe here. ArmPL also ships ilp64/int64 builds that
# export THE SAME symbol names with 64-bit integer arguments. Those link, load and
# start without a single diagnostic, then read every matrix dimension from the
# wrong width -- silent wrong answers, which is the worst failure this file can
# produce. Checked before wiring any of this up: a standalone program calling
# dgemm_/dgesv_ against libarmpl_lp64 returned results identical to OpenBLAS.
#
# Defined here because modules/ is added below and its BLAS_LIBS entry reads it.
set(SCILAB_ARMPL_DIR "/opt/arm/armpl_26.07_flang-22" CACHE PATH "Arm Performance Libraries root")

# Default to ArmPL when it is actually installed, else OpenBLAS. Auto-detection
# rather than a hardcoded default because both have to keep working: a machine
# without ArmPL (a fresh clone, CI) must still configure and build, and a plain
# rebuild here must not silently revert the swap. -DSCILAB_BLAS_BACKEND=openblas
# forces the old backend back at any time.
if(EXISTS "${SCILAB_ARMPL_DIR}/lib/libarmpl_lp64_mp.dylib")
  set(_blas_default "armpl")
else()
  set(_blas_default "openblas")
endif()
set(SCILAB_BLAS_BACKEND "${_blas_default}" CACHE STRING "BLAS/LAPACK backend: openblas | armpl")

if(SCILAB_BLAS_BACKEND STREQUAL "armpl")
  # _mp is the OpenMP-threaded build. Homebrew's OpenBLAS is threaded too, so the
  # single-threaded libarmpl_lp64 would be a downgrade rather than a swap.
  set(SCILAB_BLAS_LIBFILE "${SCILAB_ARMPL_DIR}/lib/libarmpl_lp64_mp.dylib")
  set(SCILAB_BLAS_LINKDIR "${SCILAB_ARMPL_DIR}/lib")
  set(SCILAB_BLAS_LINKFLAG "-larmpl_lp64_mp")
  # libarmpl_lp64_mp records its OpenMP dependency as @rpath/libomp.dylib. Another
  # module already pulls Homebrew's libomp in by absolute path, but @rpath still has
  # to resolve for THIS library, so its directory has to be on the rpath.
  set(SCILAB_BLAS_RPATHS "${SCILAB_ARMPL_DIR}/lib" "/opt/homebrew/opt/libomp/lib")
else()
  set(SCILAB_BLAS_LIBFILE "/opt/homebrew/opt/openblas/lib/libopenblas.dylib")
  set(SCILAB_BLAS_LINKDIR "/opt/homebrew/opt/openblas/lib")
  set(SCILAB_BLAS_LINKFLAG "-lopenblas")
  set(SCILAB_BLAS_RPATHS "")
endif()
# dyld resolves an @rpath reference against the LC_RPATHs of the referring library
# AND of the main executable, so putting these on the two executables covers the
# module dylibs that link ArmPL by absolute path as well.
set(SCILAB_BLAS_RPATH_FLAGS "")
foreach(_blas_rpath IN LISTS SCILAB_BLAS_RPATHS)
  list(APPEND SCILAB_BLAS_RPATH_FLAGS "-Wl,-rpath,${_blas_rpath}")
endforeach()
message(STATUS "Scilab BLAS backend: ${SCILAB_BLAS_BACKEND} -> ${SCILAB_BLAS_LIBFILE}")

# RC-c -- the configure-substituted files, generated by CMake.
include(cmake/ScilabGeneratedFiles.cmake)

# B9 -- etc/classpath.xml, the runtime JVM classpath manifest. Generated $SCILAB-rooted
# from the actual thirdparty/ jars (previously frozen configure output with machine-
# absolute paths); relocatable, so it drops out of the ScilabInstall.cmake relocation.
include(cmake/ScilabClasspath.cmake)
scilab_generate_classpath(${SCILAB_SOURCE_DIR})

# RC-d -- the macros build (opt-in post-step; needs a built interpreter).
include(cmake/ScilabMacros.cmake)

# Modules register their drop-in-<name> targets onto this aggregate:
# `cmake --build build-cmake --target drop-in-all` drops every migrated dylib
# into its modules/<name>/.libs/.
add_custom_target(drop-in-all)

# SCILAB_JAVA_HOME — the configured JDK for every JDK-consuming CMakeLists
# (jvm/types/external_objects_java/xcos/helptools/io, the aggregates,
# scilab-bin) — is resolved ONCE in cmake/ScilabToolchain.cmake (included
# above): RC-e.2, natively -- $ENV{JAVA_HOME} (REALPATH-canonicalized) first,
# /usr/libexec/java_home fallback. No config.status read (was: S["JAVA_HOME"]).

# Migrated modules — ALL 64 baseline module dylibs (the 4 parity-proven
# exemplars + Batch A's 8 simple leaves + Batch B's 10 variant-pair GUI
# modules + Batch C's 17 external-dependency modules + Batch D's jvm (the JNI
# module — links the JDK's libjli) + Batch E's 6 inter-module-edge dirs) are
# on scilab_module(), PLUS guibuilder — the GUI designer's Phase 1 module,
# never part of the autotools tree and so outside that "ALL 64 baseline"
# count and its parity-harness arbitration. Each Batch-B dir declares TWO
# dylibs (main + `-disable` stub) in one CMakeLists.txt; so do Batch C's
# console (`-minimal`), preferences (`-cli`), scicos_blocks (`-cli`),
# Batch D's jvm (`-disable`) and Batch E's helptools (`-disable`), scicos
# (`-cli`), xcos (`-disable`) and differential_equations (whose SECOND dylib
# is the vendored libscisundials — no modules/sundials/ dir exists).
# guibuilder shares Batch B's GUI_LIBS dependency shape but declares only ONE
# dylib — see its own CMakeLists.txt for why a `-disable` stub does not apply
# to a module with no legacy variant to reproduce. Batch E's MODULE_DEPS
# edges (diff_eq -> sundials; helptools -> commons; types/
# external_objects_java -> commons+jvm; scicos -> sundials+
# scicos_blocks[-cli]; xcos -> scicos+sundials+scicos_blocks+commons+jvm) are
# CMake target links: CMake orders the build, so the foreach order below is
# documentation, not a topo-sort.
foreach(m
    sound parallel coverage interpolation                       # exemplars
    ast call_scilab completion external_objects functions       # Batch A
    functions_manager history_manager renderer                  # Batch A
    action_binding commons graphic_export graphic_objects       # Batch B
    graphics gui history_browser scinotes guibuilder tclsci     # Batch B
    ui_data                                                     # Batch B
    arnoldi console hdf5 localization matio optimization        # Batch C
    preferences randlib scicos_blocks signal_processing slint   # Batch C
    special_functions spreadsheet statistics umfpack webtools   # Batch C
    xml                                                         # Batch C
    jvm                                                         # Batch D
    differential_equations helptools types                      # Batch E
    external_objects_java scicos xcos)                          # Batch E
  add_subdirectory(modules/${m})
endforeach()

# FOLD-IN core modules (Stage 1f) — the 21 dirs that build NO standalone dylib
# on macOS: automake declares their .la's noinst (MAINTAINER_MODE off), so ALL
# their objects fold into the libscilab/libscilab-cli aggregates, which will
# consume $<TARGET_OBJECTS:sci<m>-obj>. Each is one scilab_object_module()
# declaration; every sci<m>-obj self-registers onto sci-foldin-all (declared
# BEFORE the foreach so the registration sees it), the aggregating build/gate
# target: `cmake --build build-cmake --target sci-foldin-all`.
#
# Deliberately ABSENT from the fold-in set:
#   mpi     — macOS-inert: the MPI automake conditional is off, modules/mpi is
#             not even in SUBDIRS and compiles ZERO objects in the baseline.
#   javasci — its ONLY native output is libjavasci2 (pkglib, a SEPARATE
#             baseline artifact that links AGAINST libscilab.la and is built
#             AFTER it — modules/Makefile.am orders it last for exactly that
#             reason). libscilab folds no javasci object and records no javasci
#             dep (otool -L libscilab.2027.dylib: zero javasci entries), so
#             javasci needs no fold-in OBJECT lib.
add_custom_target(sci-foldin-all)
foreach(m
    api_scilab boolean cacsd core data_structures dynamic_link
    elementary_functions fftw fileio integer io linear_algebra mexlib
    output_stream parameters polynomials sparse string threads time
    windows_tools)
  add_subdirectory(modules/${m})
endforeach()

# THE AGGREGATES (Stage 1f) — libscilab + libscilab-cli on scilab_aggregate():
# fold the 21 OBJECT libs above, link the Stage-1e module dylibs + system
# libs. modules/CMakeLists.txt is deliberately added LAST: the aggregate
# declarations validate every FOLD_OBJECTS/LINK_MODULES name against the
# already-declared targets, and consume SCILAB_JAVA_HOME (libjli). Their
# drop-ins land in modules/.libs/ (drop-in-libscilab, drop-in-libscilab-cli;
# both also ride drop-in-all).
add_subdirectory(modules)

# javasci's NATIVE half (register B17) — declared AFTER modules/ for the same
# reason the executables below are: libjavasci2 links the libscilab AGGREGATE, so
# that target must already exist. This mirrors autotools, where
# modules/Makefile.am ordered javasci last for exactly that reason (see the
# fold-in note above, which already described libjavasci2 as "a SEPARATE baseline
# artifact that links AGAINST libscilab.la and is built AFTER it" — that artifact
# simply never got a CMake target until now, so a fresh clone built no
# libjavasci2 at all and only a stale autotools leftover kept parity green).
add_subdirectory(modules/javasci)

# THE EXECUTABLES (Stage 1f) — scilab-bin + scilab-cli-bin on
# scilab_executable() (declared after modules/ so the aggregates exist).
#
# TRANSCRIPTION SOURCE — the top-level Makefile.am's scilab_(cli_)bin_LDADD/
# LDFLAGS as libtool ACTUALLY linearized them (`libtool --dry-run --mode=link`
# on the `make -n scilab-bin scilab-cli-bin` invocations; every list below is
# that final command in declaration order, byte-verified to reproduce the
# baseline executables' LC_LOAD_DYLIB/LC_RPATH/LC_BUILD_VERSION exactly).
# Libtool expanded each LDADD .la into its dylib + dependency_libs and DEDUPED
# repeated libraries keeping the LAST occurrence — which is why these lists
# don't visually match the Makefile.am LDADD (COMMON_LIBS' -lintl survives only
# at its localization position; scilab-bin's first libscilab.la lost to the
# post-console/jvm/commons repeat, putting those three BEFORE the aggregate).
# Repeated DYLIB mentions (GUI: console/jvm/commons ride in again via
# libscilab.la's dependency_libs) are spelled ONCE at their first position —
# ld records one LC_LOAD_DYLIB at the first mention; repeats are no-ops.

# The merged -L search path, in first-appearance order (ld resolves every -l
# against ALL -L dirs positionally, so ONE merged list is exactly equivalent to
# autotools' scattered groups). MACHINE-CONFIG-SPECIFIC, the webtools/miniconda
# ledger debt again: miniconda3/lib PRECEDES the gcc keg + SDK dirs, which is
# what lands -lstdc++(->c++)/-lgfortran/-lquadmath/-lcurl on miniconda's
# @rpath-install_name'd copies — the baseline's recorded deps.
# The gcc runtime dir, ASKED OF THE COMPILER rather than written down.
#
# This was hardcoded to /opt/homebrew/Cellar/gcc/16.1.0/... and broke the moment
# Homebrew moved to 16.2.0: the keg path stopped existing, ld could no longer find
# libemutls_w, and BOTH executables failed to link while every other target and the
# whole Maven reactor still reported success -- so the build looked healthy right up
# to the final link. Any future gcc upgrade would have reproduced it exactly.
#
# -print-file-name resolves against the compiler actually in use, so the answer
# tracks whatever gcc is installed. libemutls_w.a is the right probe because it is
# precisely the library whose disappearance caused the failure; asking for it returns
# the arch-specific directory the three -L entries below are built from.
#
# If the probe fails the value is left empty rather than guessed: an unresolvable -L
# is harmless, whereas a stale one silently searches a directory that is not there.
execute_process(
  COMMAND "${CMAKE_Fortran_COMPILER}" -print-file-name=libemutls_w.a
  OUTPUT_VARIABLE _emutls_lib OUTPUT_STRIP_TRAILING_WHITESPACE
  ERROR_QUIET)
if(IS_ABSOLUTE "${_emutls_lib}" AND EXISTS "${_emutls_lib}")
  get_filename_component(_gcc_archdir "${_emutls_lib}" DIRECTORY)
  # <keg>/lib/gcc/current/gcc/<triple>/<ver>  ->  <keg>/lib/gcc/current
  get_filename_component(_gcc_libdir "${_gcc_archdir}/../../.." ABSOLUTE)
  message(STATUS "gcc runtime dir (from ${CMAKE_Fortran_COMPILER}): ${_gcc_libdir}")
else()
  set(_gcc_archdir "")
  set(_gcc_libdir "")
  message(WARNING "Could not locate libemutls_w.a via ${CMAKE_Fortran_COMPILER}; "
                  "the executables may fail to link.")
endif()
set(_scilab_exe_search_dirs
  -L/opt/homebrew/opt/openssl/lib
  -L/opt/homebrew/lib
  -L/opt/homebrew/opt/libomp/lib
  -L/opt/homebrew/opt/libarchive/lib
  -L/Users/josemoya/miniconda3/lib
  -L${_gcc_archdir}
  -L${_gcc_archdir}/../..
  -L${_gcc_libdir}
  -L/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/lib
  -L/opt/homebrew/opt/suite-sparse/lib
  -L${SCILAB_BLAS_LINKDIR}
  -L/opt/homebrew/opt/arpack/lib
  -L/opt/homebrew/opt/hdf5/lib
  -L/opt/homebrew/opt/fftw/lib)

# scilab_bin_CPPFLAGS include dirs not already in the shared Scilab base.
set(_scilab_exe_extra_includes
  ${SCILAB_SOURCE_DIR}/modules/console/includes
  ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
  ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
  ${SCILAB_SOURCE_DIR}/modules/fileio/includes
  ${SCILAB_SOURCE_DIR}/modules/string/includes
  ${SCILAB_SOURCE_DIR}/modules/history_manager/includes
  ${SCILAB_SOURCE_DIR}/modules/threads/includes
  ${SCILAB_SOURCE_DIR}/modules/core/src/c
  ${SCILAB_SOURCE_DIR}/modules/startup/src/c)

# The aggregate .la's dependency_libs expansion, common to both executables:
# each linked engine module dylib interleaved with the keg/SDK -l's exactly
# where libtool emitted them (klu/amd/umfpack after sundials; -lintl after
# localization — the COMMON_LIBS occurrence deduped INTO this one; hdf5/fftw
# after scihdf5; ...). Ends at scicoverage — after it the two executables
# diverge (the cli line keeps the .la's -lxml2 group before webtools; the GUI
# exe's OWN $(XML_LIBS) occurrence won the dedupe and sits much later).
set(_scilab_exe_engine_tail
  -larchive scifunctions -lxslt scicall_scilab
  scidifferential_equations scisundials -lklu -lamd -lumfpack -lomp
  sciarnoldi -larpack scilocalization -lintl scispecial_functions
  scicompletion scihistory_manager -lpcre2-32 scihdf5 -lhdf5 -lhdf5_hl -lfftw3
  scistatistics sciast scifunctions_manager scixml sciexternal_objects
  scislint scicoverage)

# scilab-cli-bin — LDADD = $(COMMON_LIBS) libscilab-cli.la + the 14 IS_MACOSX
# -disable/-cli stubs + $(BLAS_LIBS) $(LAPACK_LIBS) $(FLIBS). Baseline: 57
# recorded libs (CoreFoundation first = the install_name slot), 2 rpaths,
# LC_BUILD_VERSION 11.0/11.0.
scilab_executable(scilab-cli-bin
  SOURCES ${SCILAB_SOURCE_DIR}/modules/startup/src/cpp/scilab.cpp
  COMPILE_DEFINITIONS WITHOUT_GUI
  EXTRA_INCLUDES ${_scilab_exe_extra_includes}
  LINK libscilab-cli
  LDADD_LIBS
    ${_scilab_exe_search_dirs}
    ${_scilab_exe_engine_tail}
    -lxml2 -lz -lpthread -licucore -lm sciwebtools -lcurl
    scigui-disable scigraphics-disable scigraphic_export-disable
    sciconsole-minimal -lcurses sciaction_binding-disable scijvm-disable
    scixcos-disable scigraphic_objects-disable sciscinotes-disable
    sciui_data-disable scihistory_browser-disable scicommons-disable
    scipreferences-cli scitclsci-disable
    ${SCILAB_BLAS_LINKFLAG} -lemutls_w -lheapt_w -lgfortran -lquadmath -ldl
    ${SCILAB_BLAS_RPATH_FLAGS}
    "-framework Cocoa")

# RC-d's `macros` target (cmake/ScilabMacros.cmake, included above) execs
# bin/scilab-cli with no built-in awareness of whether that interpreter is
# fresh. Wire the dependency here rather than there: drop-in-scilab-cli-bin
# -- declared by scilab_executable() just above, which compiles, links, and
# copies scilab-cli-bin into .libs/ -- does not exist yet when
# ScilabMacros.cmake is include()'d earlier in this file, so calling
# add_dependencies() from inside that file would fail at configure time with
# "target not found". This does NOT put `macros` on drop-in-all: the edge
# runs FROM macros TO the interpreter, the opposite direction, so
# drop-in-all's own dependency closure is unchanged.
add_dependencies(macros drop-in-scilab-cli-bin)

# scilab-bin — LDADD = libscilab.la + $(XML_LIBS) $(COMMON_LIBS) $(FLIBS) +
# console/jvm/commons + libscilab.la again + BLAS/LAPACK/FLIBS +
# $(JAVA_JNI_LIBS). Baseline: 62 recorded libs, 3 rpaths (JDK third),
# LC_BUILD_VERSION 11.0/11.0. ALIAS keeps the macos-process-name copy (the
# `./scilab-bin` wrapper execs .libs/Scilab-<version>, not .libs/scilab-bin)
# pointing at the CMake build too.
scilab_executable(scilab-bin
  SOURCES ${SCILAB_SOURCE_DIR}/modules/startup/src/cpp/scilab.cpp
  EXTRA_INCLUDES ${_scilab_exe_extra_includes}
  LINK sciconsole scijvm scicommons libscilab
  LDFLAGS "LINKER:-rpath,${SCILAB_JAVA_HOME}/lib"
  LDADD_LIBS
    ${_scilab_exe_search_dirs}
    -L${SCILAB_JAVA_HOME}/lib
    ${_scilab_exe_engine_tail}
    sciwebtools -lcurl
    scigui scirenderer scigraphics scigraphic_export sciconsole-minimal
    -lcurses sciaction_binding scigraphic_objects sciscinotes sciui_data
    scihistory_browser scitypes-java scipreferences sciexternal_objects_java
    -lxml2 -lz -lpthread -licucore -lm scihelptools scitclsci
    ${SCILAB_BLAS_LINKFLAG} -lemutls_w -lheapt_w -lgfortran -lquadmath -ljli -ldl -pthread
    ${SCILAB_BLAS_RPATH_FLAGS}
    "-framework Cocoa"
  ALIAS Scilab-${SCILAB_LIBRARY_VERSION_MAJOR}.0.0)

# THE JAVA JARS — one target wrapping the Maven reactor (cmake/ScilabJava.cmake).
# Rides drop-in-all so `--target drop-in-all` builds the whole native app AND the
# 24 jars. Jars land in modules/<m>/target/, which is where etc/classpath.xml loads
# them from, so no drop-in copy is needed. Inter-module ordering is the reactor's.
scilab_java_bridge()
add_dependencies(drop-in-all sci-java-all)

# Stage 1f-c — the help build as an opt-in CMake post-step (needs the built app).
include(cmake/ScilabHelp.cmake)
scilab_help_target()

# `cmake --install` support — install a runnable tree to CMAKE_INSTALL_PREFIX and
# relocate the source paths baked into the config (register §5b). Build first
# (drop-in-all + macros + doc), then install.
include(cmake/ScilabInstall.cmake)
