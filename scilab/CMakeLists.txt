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

# All per-module policy, once.
include(cmake/ScilabModule.cmake)

# Modules register their drop-in-<name> targets onto this aggregate:
# `cmake --build build-cmake --target drop-in-all` drops every migrated dylib
# into its modules/<name>/.libs/.
add_custom_target(drop-in-all)

# JAVA_HOME for the JDK-consuming Batch-E modules (helptools/types/
# external_objects_java/xcos): parsed from the configured tree's config.status
# (S["JAVA_HOME"]) — the EXACT JDK the autotools baseline used (jdk-25). The
# /usr/libexec/java_home fallback answers jdk-26 on this machine, which is
# precisely why config.status is the primary source. jvm (Task 8) predates
# this shared variable and keeps its own identical discovery block.
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _scilab_java_home_line
     REGEX "^S\\[\"JAVA_HOME\"\\]=")
string(REGEX REPLACE "^S\\[\"JAVA_HOME\"\\]=\"(.*)\"$" "\\1"
       SCILAB_JAVA_HOME "${_scilab_java_home_line}")
if(NOT SCILAB_JAVA_HOME OR NOT EXISTS "${SCILAB_JAVA_HOME}/lib/libjli.dylib")
  execute_process(COMMAND /usr/libexec/java_home
                  OUTPUT_VARIABLE SCILAB_JAVA_HOME
                  OUTPUT_STRIP_TRAILING_WHITESPACE
                  RESULT_VARIABLE _scilab_java_home_rc)
  if(NOT _scilab_java_home_rc EQUAL 0
     OR NOT EXISTS "${SCILAB_JAVA_HOME}/lib/libjli.dylib")
    message(FATAL_ERROR "no JDK with lib/libjli.dylib found — config.status "
                        "has no usable JAVA_HOME and /usr/libexec/java_home failed")
  endif()
  message(STATUS "config.status JAVA_HOME unusable; using /usr/libexec/java_home")
endif()
message(STATUS "JAVA_HOME = ${SCILAB_JAVA_HOME}")

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
