# scilab/cmake/ScilabConfigure.cmake — CMake generates version.h (Stage 1f-c).
#
# version.h is EXACTLY version.h.in with three @SCILAB_VERSION_*@ substitutions (the
# revision/timestamp are literals in the template, not substituted), so
# configure_file(@ONLY) with the config.status version values reproduces configure's
# version.h BYTE-FOR-BYTE — the harness keeps byte-hashing it, unchanged. Generated into
# ${CMAKE_BINARY_DIR}/generated-includes/ and added to the module include path. During
# COEXISTENCE the byte-identical source-tree copy still resolves first (ScilabModule.cmake
# deliberately keeps modules/core/includes ahead of everything, reproducing automake's
# parity-critical -I order); this generated copy becomes the resolver automatically once the
# source-tree version.h is DELETED at retire-configure. Byte-identity makes the two
# interchangeable meanwhile. machine.h is NOT generated here (entangled with configure
# options/substitutions — retire-configure stage). Included AFTER project() (CMAKE_BINARY_DIR).

foreach(_v MAJOR MINOR MAINTENANCE)
  file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ver_line
       REGEX "^S\\[\"SCILAB_VERSION_${_v}\"\\]=")
  if(NOT _sci_ver_line)
    message(FATAL_ERROR "config.status has no S[\"SCILAB_VERSION_${_v}\"] — cannot generate version.h")
  endif()
  string(REGEX REPLACE "^S\\[\"SCILAB_VERSION_${_v}\"\\]=\"(.*)\"$" "\\1"
         SCILAB_VERSION_${_v} "${_sci_ver_line}")
endforeach()

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
