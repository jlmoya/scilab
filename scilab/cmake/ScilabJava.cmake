# scilab/cmake/ScilabJava.cmake — the CMake->Ant bridge (Stage 1f-b).
#
# ONE target wraps the existing prebuildjava Ant super-build: modules/prebuildjava/
# build.xml (default "all") hand-topo-sorts 23 module jars and drives Ivy (the
# 24th, terminal, is a separate GUI-gated command below). The topo-sort /
# inter-module Java deps stay INSIDE Ant, unchanged (Stage 2's Maven reactor
# replaces them wholesale). This reproduces exactly how Makefile.incl.am's
# `java:` target runs it: bare `ant` in modules/prebuildjava with JAVA_HOME exported.
#
# ANT + the two automake gates come from config.status (the configured tree's facts):
#   S["ANT"]="…/ant"           the configured Ant binary
#   S["NEED_JAVA_TRUE"]=""      automake conditional: "" when Java IS in this build,
#                               "#" when it is not (configure.ac: NEED_JAVA =
#                               jdk AND (javasci OR gui OR help)).
#   S["GUI_TRUE"]=""            same convention (GUI = jdk AND gui) — strictly
#                               NARROWER than NEED_JAVA; gates the terminal jar.
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ant_line REGEX "^S\\[\"ANT\"\\]=")
string(REGEX REPLACE "^S\\[\"ANT\"\\]=\"(.*)\"$" "\\1" SCILAB_ANT "${_sci_ant_line}")
# (no parse guard needed for ANT: a missing/format-drifted line leaves SCILAB_ANT
# empty or garbled, and the EXISTS check in scilab_java_bridge() FATALs showing it)

# The automake-conditional parses DO need a guard: were the S["<key>"] line
# absent, REGEX REPLACE would pass the empty input through, and "" reads as
# conditional-ON — silently wrong. Per ScilabToolchain.cmake's config.status
# standard, a required line that is missing or format-drifted fails loudly:
# the line must be exactly S["<key>"]="" (conditional holds) or S["<key>"]="#".
function(_scilab_parse_am_conditional key outvar)
  file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_cond_line REGEX "^S\\[\"${key}\"\\]=")
  if(NOT _sci_cond_line MATCHES "^S\\[\"${key}\"\\]=\"(#?)\"$")
    message(FATAL_ERROR "config.status has no parsable S[\"${key}\"] line "
                        "(got '${_sci_cond_line}') — cannot decide the ${key} gate; "
                        "re-run ./configure or update the parse in cmake/ScilabJava.cmake")
  endif()
  set(${outvar} "${CMAKE_MATCH_1}" PARENT_SCOPE)
endfunction()
_scilab_parse_am_conditional(NEED_JAVA_TRUE SCILAB_NEED_JAVA)
_scilab_parse_am_conditional(GUI_TRUE SCILAB_GUI)

function(scilab_java_bridge)
  add_custom_target(drop-in-jars COMMENT "The Scilab module jars (Ant)")
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
  set(_sci_java_cmds
    COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT})
  # SECOND command, same recipe, GUI-GATED: modules/terminal (the 2026 JediTerm
  # module) is NOT in prebuildjava/build.xml's hand-topo-sorted "all" list —
  # autotools builds its jar when make recurses into modules/terminal, whose
  # Makefile.am arms USEANT=1 only inside `if GUI`. NEED_JAVA is strictly
  # broader than GUI (e.g. a --without-gui --with-javasci tree has NEED_JAVA
  # on, GUI off, and autotools builds NO terminal jar), so the terminal COMMAND
  # joins sci-java-all only when GUI is on. Command order reproduces make's:
  # prebuildjava is SUBDIRS entry #1, terminal recurses later, so terminal's
  # dependency jars already exist. 23 prebuildjava jars + terminal = the
  # baseline's 24.
  if(SCILAB_GUI STREQUAL "")
    list(APPEND _sci_java_cmds
      COMMAND ${CMAKE_COMMAND} -E chdir ${SCILAB_SOURCE_DIR}/modules/terminal
              ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT})
    set(_sci_jar_summary "24 Scilab module jars via Ant (prebuildjava super-build + terminal)")
  else()
    set(_sci_jar_summary "23 Scilab module jars via Ant (prebuildjava super-build; terminal skipped: GUI off)")
    message(STATUS "GUI off — terminal jar skipped (sci-java-all = the 23 prebuildjava jars, matching autotools)")
  endif()
  add_custom_target(sci-java-all
    ${_sci_java_cmds}
    WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}/modules/prebuildjava
    USES_TERMINAL
    COMMENT "Building the ${_sci_jar_summary}")
  add_dependencies(drop-in-jars sci-java-all)
  message(STATUS "SCILAB_ANT = ${SCILAB_ANT} (jar bridge armed)")
endfunction()
