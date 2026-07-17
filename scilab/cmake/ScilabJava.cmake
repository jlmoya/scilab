# scilab/cmake/ScilabJava.cmake — the CMake->Ant bridge (Stage 1f-b).
#
# ONE target wraps the existing prebuildjava Ant super-build: modules/prebuildjava/
# build.xml (default "all") hand-topo-sorts all 24 module jars and drives Ivy. The
# topo-sort / inter-module Java deps stay INSIDE Ant, unchanged (Stage 2's Maven
# reactor replaces them wholesale). This reproduces exactly how Makefile.incl.am's
# `java:` target runs it: bare `ant` in modules/prebuildjava with JAVA_HOME exported.
#
# ANT + the NEED_JAVA gate come from config.status (the configured tree's facts):
#   S["ANT"]="…/ant"           the configured Ant binary
#   S["NEED_JAVA_TRUE"]=""      automake conditional: "" when Java IS in this build,
#                               "#" when it is not.
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ant_line REGEX "^S\\[\"ANT\"\\]=")
string(REGEX REPLACE "^S\\[\"ANT\"\\]=\"(.*)\"$" "\\1" SCILAB_ANT "${_sci_ant_line}")
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_needjava_line REGEX "^S\\[\"NEED_JAVA_TRUE\"\\]=")
string(REGEX REPLACE "^S\\[\"NEED_JAVA_TRUE\"\\]=\"(.*)\"$" "\\1" SCILAB_NEED_JAVA "${_sci_needjava_line}")

function(scilab_java_bridge)
  add_custom_target(drop-in-jars COMMENT "The 24 Scilab module jars (Ant)")
  if(NOT SCILAB_NEED_JAVA STREQUAL "")
    # NEED_JAVA off (NEED_JAVA_TRUE is "#") — this configuration builds no jars.
    message(STATUS "Java disabled in this configuration (NEED_JAVA off) — jar bridge is a no-op")
    add_custom_target(sci-java-all COMMENT "Java disabled (NEED_JAVA off) — no-op")
    add_dependencies(drop-in-jars sci-java-all)
    return()
  endif()
  if(NOT SCILAB_ANT OR NOT EXISTS "${SCILAB_ANT}")
    message(FATAL_ERROR "config.status ANT unusable ('${SCILAB_ANT}') — cannot build the Java jars")
  endif()
  # Bare `ant` in modules/prebuildjava, JAVA_HOME exported — byte-equivalent to
  # Makefile.incl.am's `java:` recipe. No -D args: target-jar defaults to "jar" and
  # build_xcos/build_javasci resolve from the configure-substituted build.incl.xml.
  # Jars land in modules/<m>/jar/ (the same place `make` writes them) so the drop-in
  # is automatic — no copy step.
  #
  # SECOND command, same recipe: modules/terminal (the 2026 JediTerm module) is NOT
  # in prebuildjava/build.xml's hand-topo-sorted "all" list — autotools builds its
  # jar when make recurses into modules/terminal and the per-module `java:` recipe
  # runs bare $(ANT) there (prebuildjava is SUBDIRS entry #1, terminal recurses
  # later, so its dependency jars already exist — the same order reproduced here).
  # 23 prebuildjava jars + terminal = the baseline's 24.
  add_custom_target(sci-java-all
    COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT}
    COMMAND ${CMAKE_COMMAND} -E chdir ${SCILAB_SOURCE_DIR}/modules/terminal
            ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT}
    WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}/modules/prebuildjava
    USES_TERMINAL
    COMMENT "Building the 24 Scilab module jars via Ant (prebuildjava super-build + terminal)")
  add_dependencies(drop-in-jars sci-java-all)
  message(STATUS "SCILAB_ANT = ${SCILAB_ANT} (jar bridge armed)")
endfunction()
