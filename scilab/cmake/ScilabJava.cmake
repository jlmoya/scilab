# scilab/cmake/ScilabJava.cmake — the CMake->Ant/Maven bridge.
#
# Stage 1f-b wired the (back then unconditional) Ant path. This adds the
# CMake<->Maven swap the migration doc's remaining-work item 1 names
# (docs/design/build-cmake-maven-migration.md, "What remains before Ant can
# be deleted"): a SCILAB_JAVA_BUILD switch selects which toolchain the
# sci-java-all target actually runs.
#
#   SCILAB_JAVA_BUILD = ant (CACHE STRING, DEFAULT) — modules/prebuildjava/
#     build.xml (default "all") hand-topo-sorts 23 module jars and drives Ivy
#     (the 24th, terminal, is a separate GUI-gated command). The topo-sort /
#     inter-module Java deps stay INSIDE Ant. Reproduces exactly how
#     Makefile.incl.am's `java:` target runs it: bare `ant` in
#     modules/prebuildjava with JAVA_HOME exported. Jars land in
#     modules/<m>/jar/ (the same place `make` writes them) so the drop-in is
#     automatic — no copy step. UNCHANGED from Stage 1f-b: this is still
#     exactly what runs when nobody passes the flag at all.
#
#   SCILAB_JAVA_BUILD = maven (opt-in via -DSCILAB_JAVA_BUILD=maven) — runs
#     `mvn package` at the reactor root (${SCILAB_SOURCE_DIR}/pom.xml, Stage
#     2-f/Wave F — all 24 modules, terminal included). That is the whole
#     build action: Maven writes every jar to modules/<m>/target/ and it
#     STAYS there. No copy to jar/, no POM change, no classpath.xml change.
#     Repointing consumers (classpath.xml) at target/ instead of jar/ is a
#     separate, later increment — out of scope here.
#
# ANT + the two automake gates come from config.status (the configured tree's facts):
#   S["ANT"]="…/ant"           the configured Ant binary
#   S["NEED_JAVA_TRUE"]=""      automake conditional: "" when Java IS in this build,
#                               "#" when it is not (configure.ac: NEED_JAVA =
#                               jdk AND (javasci OR gui OR help)).
#   S["GUI_TRUE"]=""            same convention (GUI = jdk AND gui) — strictly
#                               NARROWER than NEED_JAVA; gates the terminal jar
#                               in BOTH backends (see scilab_java_bridge() below).
# Maven has no config.status entry of its own: there is no autotools-configured
# "the exact Maven binary" the way S["ANT"] pins Ant, so `mvn` is resolved off
# PATH via find_program() below instead.

# SCILAB_JAVA_BUILD — the jar-build backend switch. A CACHE STRING (not a
# plain option() BOOL) because there are two named backends, not an on/off
# toggle. "ant" is the default and, unchanged from Stage 1f-b, is what runs
# when nobody passes the flag at all. The STRINGS property below is only a
# ccmake/cmake-gui dropdown hint — CMake does not itself reject an off-list
# value for a CACHE STRING, so the FATAL guard right after it is what actually
# catches a typo rather than letting it silently fall into the ant branch below.
set(SCILAB_JAVA_BUILD "ant" CACHE STRING
    "Java module jar build backend: ant (default, modules/<m>/jar/) or maven (opt-in, modules/<m>/target/)")
set_property(CACHE SCILAB_JAVA_BUILD PROPERTY STRINGS ant maven)
if(NOT SCILAB_JAVA_BUILD STREQUAL "ant" AND NOT SCILAB_JAVA_BUILD STREQUAL "maven")
  message(FATAL_ERROR "SCILAB_JAVA_BUILD must be 'ant' or 'maven' (got '${SCILAB_JAVA_BUILD}')")
endif()

file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ant_line REGEX "^S\\[\"ANT\"\\]=")
string(REGEX REPLACE "^S\\[\"ANT\"\\]=\"(.*)\"$" "\\1" SCILAB_ANT "${_sci_ant_line}")
# (no parse guard needed for ANT: a missing/format-drifted line leaves SCILAB_ANT
# empty or garbled, and the EXISTS check in scilab_java_bridge() FATALs showing it)

# mvn resolution: plain PATH lookup (file scope, unconditional, like SCILAB_ANT
# above), but only FATAL-guarded at point of use inside scilab_java_bridge() —
# so an ant-mode configure on a machine with no Maven installed is completely
# unaffected, matching "ant default behaves exactly as today, zero change".
find_program(SCILAB_MVN mvn)

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
    # NEED_JAVA off (NEED_JAVA_TRUE is "#") — this configuration builds no jars,
    # in EITHER backend: config.status's NEED_JAVA fact is about whether a JDK
    # is in this build at all, independent of which tool would build the jars.
    message(STATUS "Java disabled in this configuration (NEED_JAVA off) — jar bridge is a no-op")
    add_custom_target(sci-java-all COMMENT "Java disabled (NEED_JAVA off) — no-op")
    add_dependencies(drop-in-jars sci-java-all)
    return()
  endif()

  if(SCILAB_JAVA_BUILD STREQUAL "maven")
    # Bare `mvn package` at the reactor root, JAVA_HOME exported — the whole
    # build action. No -D args, no profile, and deliberately no -q (keep
    # Maven's normal reactor output, same as the ant path's unsilenced
    # USES_TERMINAL run below). Maven writes every jar straight to
    # modules/<m>/target/; nothing here copies, moves, or symlinks it anywhere
    # else.
    if(NOT SCILAB_MVN)
      message(FATAL_ERROR "mvn not found on PATH — cannot build the Java jars (SCILAB_JAVA_BUILD=maven)")
    endif()
    set(_sci_java_cmds
      COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_MVN} package)
    # GUI-GATED, matching the ant branch's semantics below (same S["GUI_TRUE"]
    # reasoning), even though the mechanism differs: the reactor POM's
    # <modules> already lists all 24, terminal included (Wave F), so a bare
    # `mvn package` always builds it. Matching the ant branch's "terminal only
    # when GUI is on" therefore means EXCLUDING it when GUI is off, via
    # Maven's documented "build the whole reactor minus this project"
    # selector: `-pl '!:terminal'` (modules/terminal/pom.xml's artifactId is
    # "terminal"; the leading "!" negates the selection, the leading ":"
    # matches by artifactId regardless of groupId). Safe to exclude — per
    # modules/terminal/pom.xml, terminal is a leaf consumer (action_binding,
    # commons, gui, localization), nothing else in the reactor depends on it.
    if(SCILAB_GUI STREQUAL "")
      set(_sci_jar_summary "24 Scilab module jars via Maven (reactor package; jars land in modules/<m>/target/, not jar/)")
    else()
      list(APPEND _sci_java_cmds -pl !:terminal)
      set(_sci_jar_summary "23 Scilab module jars via Maven (reactor package minus terminal; jars land in modules/<m>/target/, not jar/; terminal skipped: GUI off)")
      message(STATUS "GUI off — terminal excluded from the Maven reactor build (sci-java-all = the 23 non-terminal modules, matching autotools)")
    endif()
    set(_sci_java_workdir ${SCILAB_SOURCE_DIR})
  else()
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
    set(_sci_java_workdir ${SCILAB_SOURCE_DIR}/modules/prebuildjava)
  endif()

  add_custom_target(sci-java-all
    ${_sci_java_cmds}
    WORKING_DIRECTORY ${_sci_java_workdir}
    USES_TERMINAL
    COMMENT "Building the ${_sci_jar_summary}")
  add_dependencies(drop-in-jars sci-java-all)
  if(SCILAB_JAVA_BUILD STREQUAL "maven")
    message(STATUS "SCILAB_MVN = ${SCILAB_MVN} (jar bridge armed, maven mode — jars land in modules/<m>/target/, NOT jar/)")
  else()
    message(STATUS "SCILAB_ANT = ${SCILAB_ANT} (jar bridge armed)")
  endif()
endfunction()
