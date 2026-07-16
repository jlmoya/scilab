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

# Migrated modules — grows as batches land (the 4 parity-proven exemplars +
# Batch A's 8 simple leaves + Batch B's 10 variant-pair GUI modules + Batch C's
# 17 external-dependency modules are on scilab_module(); later tasks add the
# remaining catalog). Each Batch-B dir declares TWO dylibs (main + `-disable`
# stub) in one CMakeLists.txt; so do Batch C's console (`-minimal`),
# preferences (`-cli`) and scicos_blocks (`-cli`).
foreach(m
    sound parallel coverage interpolation                       # exemplars
    ast call_scilab completion external_objects functions       # Batch A
    functions_manager history_manager renderer                  # Batch A
    action_binding commons graphic_export graphic_objects       # Batch B
    graphics gui history_browser scinotes tclsci ui_data        # Batch B
    arnoldi console hdf5 localization matio optimization        # Batch C
    preferences randlib scicos_blocks signal_processing slint   # Batch C
    special_functions spreadsheet statistics umfpack webtools   # Batch C
    xml)                                                        # Batch C
  add_subdirectory(modules/${m})
endforeach()
