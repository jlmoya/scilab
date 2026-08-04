# Version file for find_package(Scilab <version>). Hand-written for the same
# reason as ScilabConfig.cmake: the app bundle is rsynced, not `cmake --install`ed,
# so write_basic_package_version_file() output would have to be committed back
# into the tree to survive packaging. Reading the shipped header instead means
# this can never disagree with the Scilab it sits inside.
#
# Contract: set PACKAGE_VERSION, PACKAGE_VERSION_COMPATIBLE, PACKAGE_VERSION_EXACT.
# CMake includes this BEFORE ScilabConfig.cmake and skips the config entirely if
# COMPATIBLE is not set, so a wrong answer here silently hides the package.

get_filename_component(_scilab_root "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
set(_scilab_version_h "${_scilab_root}/modules/core/includes/version.h")

if(NOT EXISTS "${_scilab_version_h}")
  # Leave PACKAGE_VERSION unset: find_package then reports "found unsuitable
  # version" rather than claiming a version it could not read.
  set(PACKAGE_VERSION_COMPATIBLE FALSE)
  return()
endif()

file(READ "${_scilab_version_h}" _scilab_version_src)
set(_scilab_ok TRUE)
foreach(_part MAJOR MINOR MAINTENANCE)
  if(_scilab_version_src MATCHES "#define[ \t]+SCI_VERSION_${_part}[ \t]+([0-9]+)")
    set(_scilab_v_${_part} "${CMAKE_MATCH_1}")
  else()
    set(_scilab_ok FALSE)
  endif()
endforeach()

if(NOT _scilab_ok)
  set(PACKAGE_VERSION_COMPATIBLE FALSE)
  return()
endif()

set(PACKAGE_VERSION "${_scilab_v_MAJOR}.${_scilab_v_MINOR}.${_scilab_v_MAINTENANCE}")

# SameMajorVersion semantics: the gateway ABI is the Scilab API surface, which
# moves with the major version. A 2027 gateway must not be handed to 2028.
if(PACKAGE_FIND_VERSION_MAJOR STREQUAL _scilab_v_MAJOR)
  set(PACKAGE_VERSION_COMPATIBLE TRUE)
else()
  set(PACKAGE_VERSION_COMPATIBLE FALSE)
endif()

if(PACKAGE_FIND_VERSION STREQUAL PACKAGE_VERSION)
  set(PACKAGE_VERSION_EXACT TRUE)
endif()
