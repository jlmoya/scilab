# scilab/cmake/ScilabVersion.cmake -- the canonical Scilab version triple (RC-e.1).
#
# Single source of truth for SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE. Through Stage 1f-c
# these were read straight out of config.status (cmake/ScilabConfigure.cmake's own
# file(STRINGS ...) loop: S["SCILAB_VERSION_MAJOR"]="2027" etc, themselves AC_SUBST'd
# from configure.ac:47-49's literal `SCILAB_VERSION_MAJOR=2027` assignment) -- the last
# config.status coupling for the version triple (docs/design/build-cmake-maven-
# migration.md S12's RC-e prerequisite list, and the RC-e plan's coupling table). RC-e.1
# moves the source of truth here: CMake no longer reads config.status for the triple.
# config.status itself is untouched and stays on disk (still consumed elsewhere, e.g.
# JAVA_HOME/ANT -- RC-e.2's problem); this file only replaces ITS ONE version read.
#
# include()d from CMakeLists.txt before any consumer runs: ScilabConfigure.cmake's
# version.h generation and ScilabGeneratedFiles.cmake's SCILAB_BINARY_VERSION/
# Version.incl both need SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE already set. Deliberately
# NOT wired into project(... VERSION ...) -- that is optional CMake sugar this driver
# does not otherwise use, and adding it would not remove any dependency this file
# doesn't already remove on its own.
#
# NOT the same number as ScilabMachineHeader.cmake's PACKAGE_VERSION ("6", AC_INIT's
# own product version, hardcoded there and never config.status-sourced to begin with --
# see that file's bucket-5 comment). The two are unrelated by design; do not merge them.
#
# Bump here (and in configure.ac:47-49, until configure.ac is deleted at RC-e.4) to
# change the branch version.
set(SCILAB_VERSION_MAJOR 2027)
set(SCILAB_VERSION_MINOR 0)
set(SCILAB_VERSION_MAINTENANCE 0)
