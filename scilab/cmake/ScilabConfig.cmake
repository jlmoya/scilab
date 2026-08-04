# find_package(Scilab) -- the package a toolbox's generated CMakeLists.txt
# consumes on an END USER's machine. Step 2b of
# docs/design/dynamic-link-cmake-migration.md.
#
# Deliberately NOT generated from a .cmake.in. The Scilab app bundle is produced
# by rsyncing the tree (package-macos.sh), not by `cmake --install`, so a
# configured file would have to be written back into the source tree to survive
# packaging. Everything here is instead derived at load time from files the
# bundle already ships, which also means the package cannot go stale against the
# app it sits inside.
#
# Usage from a generated toolbox build:
#
#   find_package(Scilab REQUIRED)
#   scilab_gateway(mytoolbox SOURCES foo.c bar.cpp)
#
# Sets: Scilab_FOUND, Scilab_VERSION{,_MAJOR,_MINOR,_PATCH}, SCILAB_ROOT,
#       SCILAB_MODULES_DIR, and the scilab_gateway() command.

cmake_minimum_required(VERSION 3.20)

# <root>/cmake/ScilabConfig.cmake -> <root>. Holds in the source tree and in
# <app>/Contents/Resources/scilab alike; that sameness is the point (see the
# header of ScilabGatewayPolicy.cmake).
get_filename_component(SCILAB_ROOT "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
set(SCILAB_MODULES_DIR "${SCILAB_ROOT}/modules")

set(_scilab_version_h "${SCILAB_MODULES_DIR}/core/includes/version.h")
if(NOT EXISTS "${_scilab_version_h}")
  # Fail here, with the path, rather than let a half-resolved root produce a
  # gateway that compiles against nothing and fails at dlopen.
  set(Scilab_FOUND FALSE)
  set(Scilab_NOT_FOUND_MESSAGE
      "SCILAB_ROOT resolved to '${SCILAB_ROOT}' but ${_scilab_version_h} does not "
      "exist, so this is not a Scilab installation. ScilabConfig.cmake must sit in "
      "<scilab-root>/cmake/.")
  return()
endif()

# Read the version from the header the bundle already ships, so the package can
# never disagree with the Scilab it belongs to.
file(READ "${_scilab_version_h}" _scilab_version_src)
foreach(_part MAJOR MINOR MAINTENANCE)
  if(NOT _scilab_version_src MATCHES "#define[ \t]+SCI_VERSION_${_part}[ \t]+([0-9]+)")
    set(Scilab_FOUND FALSE)
    set(Scilab_NOT_FOUND_MESSAGE
        "Could not parse SCI_VERSION_${_part} from ${_scilab_version_h}")
    return()
  endif()
  set(_scilab_v_${_part} "${CMAKE_MATCH_1}")
endforeach()

set(Scilab_VERSION_MAJOR ${_scilab_v_MAJOR})
set(Scilab_VERSION_MINOR ${_scilab_v_MINOR})
set(Scilab_VERSION_PATCH ${_scilab_v_MAINTENANCE})
set(Scilab_VERSION "${_scilab_v_MAJOR}.${_scilab_v_MINOR}.${_scilab_v_MAINTENANCE}")

include("${CMAKE_CURRENT_LIST_DIR}/ScilabGatewayPolicy.cmake")

if(NOT COMMAND scilab_gateway)
  set(Scilab_FOUND FALSE)
  set(Scilab_NOT_FOUND_MESSAGE
      "ScilabGatewayPolicy.cmake loaded from ${CMAKE_CURRENT_LIST_DIR} but did not "
      "define scilab_gateway()")
  return()
endif()

set(Scilab_FOUND TRUE)
