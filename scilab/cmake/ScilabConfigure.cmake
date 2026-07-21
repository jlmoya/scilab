# scilab/cmake/ScilabConfigure.cmake — CMake generates version.h (Stage 1f-c).
#
# version.h is EXACTLY version.h.in with three @SCILAB_VERSION_*@ substitutions (the
# revision/timestamp are literals in the template, not substituted), so
# configure_file(@ONLY) with cmake/ScilabVersion.cmake's version values reproduces
# configure's version.h BYTE-FOR-BYTE — the harness keeps byte-hashing it, unchanged.
# Generated into ${CMAKE_BINARY_DIR}/generated-includes/ and added to the module include
# path. During COEXISTENCE the byte-identical source-tree copy still resolves first
# (ScilabModule.cmake deliberately keeps modules/core/includes ahead of everything,
# reproducing automake's parity-critical -I order); this generated copy becomes the
# resolver automatically once the source-tree version.h is DELETED at retire-configure.
# Byte-identity makes the two interchangeable meanwhile. machine.h is NOT generated here
# (entangled with configure options/substitutions — retire-configure stage). Included
# AFTER project() (CMAKE_BINARY_DIR).
#
# SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE are set by cmake/ScilabVersion.cmake (RC-e.1),
# include()d from CMakeLists.txt before this file — this used to be a
# file(STRINGS ... config.status ...) read here directly (Stage 1f-c); RC-e.1 severed
# that, config.status's last version-triple reader. This file only CONSUMES the triple
# now; the guard below exists to fail loudly (not silently substitute empty strings) if
# a future reordering ever breaks that include-before-use contract.
if(NOT DEFINED SCILAB_VERSION_MAJOR OR NOT DEFINED SCILAB_VERSION_MINOR
   OR NOT DEFINED SCILAB_VERSION_MAINTENANCE)
  message(FATAL_ERROR "SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE not set — "
                      "include(cmake/ScilabVersion.cmake) must run before ScilabConfigure.cmake")
endif()

set(SCILAB_GENERATED_INCLUDES ${CMAKE_BINARY_DIR}/generated-includes)
configure_file(${SCILAB_SOURCE_DIR}/modules/core/includes/version.h.in
               ${SCILAB_GENERATED_INCLUDES}/version.h @ONLY)

# Add the generated-includes dir; it becomes the version.h resolver once the source-tree copy
# is deleted at retire-configure (until then core/includes leads — byte-identical, so it does
# not matter). machine.h (absent here) resolves to modules/core/includes as before. Directory
# scope — consumed by the module add_subdirectory() calls that follow.
list(PREPEND SCILAB_DEFAULT_INCLUDES ${SCILAB_GENERATED_INCLUDES})
message(STATUS "CMake-generated version.h -> ${SCILAB_GENERATED_INCLUDES}/version.h "
               "(v${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}.${SCILAB_VERSION_MAINTENANCE})")
