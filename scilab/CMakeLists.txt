# scilab/CMakeLists.txt — Stage-1 make→CMake driver (hybrid coexistence).
#
# Builds the migrated module dylibs ONLY; the rest of the tree stays autotools
# (this file is invisible to automake). Each migrated module is a one-call
# scilab_module() declaration (cmake/ScilabModule.cmake) whose output replaces
# the autotools dylib in modules/<name>/.libs/ via its drop-in-<name> target;
# the autotools original is always recoverable with `make -C modules/<name>`.
#
# Requires an autotools-CONFIGURED tree (machine.h/version.h are configure-
# generated) — cmake/ScilabToolchain.cmake enforces that.
#
# Build:    cmake -S . -B build-cmake -DCMAKE_BUILD_TYPE=
#           cmake --build build-cmake --target drop-in-all     (or drop-in-<name>)
# Verify:   cd build-parity && python3 -m parity.capture .. /tmp/cand.json cand \
#           && python3 -m parity.diff baseline-autotools.json /tmp/cand.json
#
# (Makefile/Ninja generators only — the per-language $<COMPILE_LANGUAGE:...>
# includes/defines in ScilabModule.cmake don't express under -G Xcode/VS.)

cmake_minimum_required(VERSION 3.20)

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_LIST_DIR}/cmake)

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

# Modules register their drop-in-<name> targets onto this aggregate:
# `cmake --build build-cmake --target drop-in-all` drops every migrated dylib
# into its modules/<name>/.libs/.
add_custom_target(drop-in-all)

# SCILAB_JAVA_HOME — the configured JDK for every JDK-consuming CMakeLists
# (jvm/types/external_objects_java/xcos/helptools/io, the aggregates,
# scilab-bin) — is resolved ONCE in cmake/ScilabToolchain.cmake (included
# above): config.status S["JAVA_HOME"] first, /usr/libexec/java_home fallback.

# Migrated modules — ALL 64 baseline module dylibs (the 4 parity-proven
# exemplars + Batch A's 8 simple leaves + Batch B's 10 variant-pair GUI
# modules + Batch C's 17 external-dependency modules + Batch D's jvm (the JNI
# module — links the JDK's libjli) + Batch E's 6 inter-module-edge dirs) are
# on scilab_module(). Each Batch-B dir declares TWO dylibs (main + `-disable`
# stub) in one CMakeLists.txt; so do Batch C's console (`-minimal`),
# preferences (`-cli`), scicos_blocks (`-cli`), Batch D's jvm (`-disable`) and
# Batch E's helptools (`-disable`), scicos (`-cli`), xcos (`-disable`) and
# differential_equations (whose SECOND dylib is the vendored libscisundials —
# no modules/sundials/ dir exists). Batch E's MODULE_DEPS edges (diff_eq ->
# sundials; helptools -> commons; types/external_objects_java -> commons+jvm;
# scicos -> sundials+scicos_blocks[-cli]; xcos -> scicos+sundials+
# scicos_blocks+commons+jvm) are CMake target links: CMake orders the build,
# so the foreach order below is documentation, not a topo-sort.
foreach(m
    sound parallel coverage interpolation                       # exemplars
    ast call_scilab completion external_objects functions       # Batch A
    functions_manager history_manager renderer                  # Batch A
    action_binding commons graphic_export graphic_objects       # Batch B
    graphics gui history_browser scinotes tclsci ui_data        # Batch B
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
set(_gcc_libdir /opt/homebrew/Cellar/gcc/16.1.0/bin/../lib/gcc/current)  # FLIBS spelling
set(_scilab_exe_search_dirs
  -L/opt/homebrew/opt/openssl/lib
  -L/opt/homebrew/lib
  -L/opt/homebrew/opt/libomp/lib
  -L/opt/homebrew/opt/libarchive/lib
  -L/Users/josemoya/miniconda3/lib
  -L${_gcc_libdir}/gcc/aarch64-apple-darwin25/16
  -L${_gcc_libdir}/gcc
  -L${_gcc_libdir}/gcc/aarch64-apple-darwin25/16/../../..
  -L/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/lib
  -L/opt/homebrew/opt/suite-sparse/lib
  -L/opt/homebrew/opt/openblas/lib
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
    -lopenblas -lemutls_w -lheapt_w -lgfortran -lquadmath -ldl
    "-framework Cocoa")

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
    -lopenblas -lemutls_w -lheapt_w -lgfortran -lquadmath -ljli -ldl -pthread
    "-framework Cocoa"
  ALIAS Scilab-${SCILAB_LIBRARY_VERSION_MAJOR}.0.0)

# THE JAVA JARS (Stage 1f-b) — one target wrapping the prebuildjava Ant super-build
# (cmake/ScilabJava.cmake). Rides drop-in-all so `--target drop-in-all` now builds
# the whole native app AND the 24 jars. Jars land in modules/<m>/jar/ (drop-in is
# automatic). The topo-sort/Ivy stay inside Ant (Stage 2's Maven reactor's job).
scilab_java_bridge()
add_dependencies(drop-in-all sci-java-all)
