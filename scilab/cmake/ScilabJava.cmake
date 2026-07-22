# scilab/cmake/ScilabJava.cmake — the CMake->Maven bridge for the module jars.
#
# sci-java-all runs `mvn package` at the reactor root (${SCILAB_SOURCE_DIR}/pom.xml,
# all 24 modules, terminal included). That is the whole build action: Maven writes
# every jar to modules/<m>/target/ and it STAYS there — no copy step. etc/classpath.xml
# (and .in) load from target/, so the backend and the path the running app reads agree.
#
# HISTORY (2026-07-21, tag `autotools-ant-retired`): this file used to bridge to Ant as
# well, via a SCILAB_JAVA_BUILD switch whose "ant" value drove modules/prebuildjava/'s
# hand-topo-sorted build.xml super-build into modules/<m>/jar/. Ant, prebuildjava/, all
# 26 build.xml and every jar/ directory are now deleted, so that backend and its
# find_program(ant) lookup are gone with them. The switch itself is deliberately kept as
# a cache variable that FATALs on any non-maven value — an existing CMakeCache carrying
# SCILAB_JAVA_BUILD=ant then gets a diagnosis naming the retirement, instead of silently
# selecting a backend that no longer exists.
#
# The two automake gates this file reproduces (RC-e.2 severed them from config.status;
# each is now a native CACHE BOOL, and this records what they MEAN):
#   NEED_JAVA_TRUE  Java is in this build (configure.ac: jdk AND (javasci OR gui OR
#                   help)) — now ENABLE_JAVA.
#   GUI_TRUE        GUI is in this build (jdk AND gui) — strictly NARROWER than
#                   NEED_JAVA; gates the terminal jar. Now ENABLE_GUI.
# Maven never had a config.status entry of its own — there was no autotools-configured
# "the exact Maven binary" the way S["ANT"] pinned Ant — so `mvn` resolves off PATH via
# find_program() below. That was already native before the retirement.
set(SCILAB_JAVA_BUILD "maven" CACHE STRING
    "Java module jar build backend: maven (the only supported value; jars land in modules/<m>/target/)")
set_property(CACHE SCILAB_JAVA_BUILD PROPERTY STRINGS maven)
if(NOT SCILAB_JAVA_BUILD STREQUAL "maven")
  message(FATAL_ERROR
    "SCILAB_JAVA_BUILD='${SCILAB_JAVA_BUILD}' is not supported — 'maven' is the only backend. "
    "Ant was retired 2026-07-21: modules/prebuildjava/ and every build.xml were deleted, so the "
    "ant backend cannot build anything. See docs/design/build-cmake-maven-migration.md.")
endif()

# mvn resolution: plain PATH lookup (file scope, unconditional, like SCILAB_ANT
# mvn resolution: plain PATH lookup at file scope, FATAL-guarded at point of use
# inside scilab_java_bridge() — so merely configuring on a machine without Maven
# still succeeds; only building the jars requires it.
find_program(SCILAB_MVN mvn)

# _scilab_parse_am_conditional (the automake-conditional config.status parser)
# lived here for cmake/ScilabHelp.cmake's BUILD_HELP_TRUE gate -- RC-e.2
# deliberately left that one read untouched (see below). RC-e.2b finishes the
# job: BUILD_HELP_TRUE is now ENABLE_HELP, a native CACHE BOOL
# (cmake/ScilabHelp.cmake), so the helper has zero remaining callers and is
# deleted here. That was CMake's LAST config.status read --
# `grep -rn config.status cmake/*.cmake CMakeLists.txt` now turns up comments
# only (RC-e.3 proves it by reconfiguring with config.status renamed away).

# RC-e.2: GUI_TRUE/NEED_JAVA_TRUE become native CACHE BOOL options instead of a
# config.status parse -- a plain BOOL, not a string-valued automake
# conditional, so downstream reads if(ENABLE_JAVA)/if(ENABLE_GUI) rather than
# the old STREQUAL "" dance. Defaults are ON/ON, hardcoded from what this
# tree's config.status currently records (S["NEED_JAVA_TRUE"]="" and
# S["GUI_TRUE"]="" -- automake's empty-string-is-true convention: "" means the
# conditional holds, "#" means off; same convention ENABLE_HELP's default
# transcribes, cmake/ScilabHelp.cmake) -- the same one-time transcription RC-e.1 used for
# the version triple (cmake/ScilabVersion.cmake), and that WITH_GUI, a few
# lines of the file below, already uses for its own ON default
# (cmake/ScilabMachineHeader.cmake). A tree configured --without-gui or
# --without-javasci needs -DENABLE_GUI=OFF / -DENABLE_JAVA=OFF on the cmake
# command line; rediscovering an autotools --without flag without reading
# config.status is exactly the coupling this increment removes.
#
# Deliberately a DIFFERENT variable from WITH_GUI (ScilabMachineHeader.cmake,
# RC-a, also default ON): WITH_GUI feeds machine.h's WITH_GUI #define and, by
# that file's own honest-disclosure comment (cmake/ScilabGeneratedFiles.cmake),
# is not load-bearing anywhere in this driver yet. ENABLE_GUI here IS
# load-bearing -- it gates the terminal jar below. Reconciling the two names
# is a real future simplification, not attempted in this same-value severing.
option(ENABLE_JAVA "Java is part of this build (autotools: NEED_JAVA = jdk AND (javasci OR gui OR help))" ON)
option(ENABLE_GUI  "GUI is part of this build (autotools: GUI = jdk AND gui); narrower than ENABLE_JAVA, gates the terminal jar in both backends" ON)

function(scilab_java_bridge)
  add_custom_target(drop-in-jars COMMENT "The Scilab module jars (Maven)")
  if(NOT ENABLE_JAVA)
    # NEED_JAVA off (ENABLE_JAVA OFF) — this configuration builds no jars, in
    # EITHER backend: the NEED_JAVA fact is about whether a JDK is in this
    # build at all, independent of which tool would build the jars.
    message(STATUS "Java disabled in this configuration (NEED_JAVA off) — jar bridge is a no-op")
    add_custom_target(sci-java-all COMMENT "Java disabled (NEED_JAVA off) — no-op")
    add_dependencies(drop-in-jars sci-java-all)
    return()
  endif()

  if(SCILAB_JAVA_BUILD STREQUAL "maven")
    # Bare `mvn package` at the reactor root, JAVA_HOME exported — the whole
    # build action. No -D args, no profile, and deliberately no -q (keep
    # Maven's normal reactor output in the
    # USES_TERMINAL run below). Maven writes every jar straight to
    # modules/<m>/target/; nothing here copies, moves, or symlinks it anywhere
    # else.
    if(NOT SCILAB_MVN)
      message(FATAL_ERROR "mvn not found on PATH — cannot build the Java jars (SCILAB_JAVA_BUILD=maven)")
    endif()
    set(_sci_java_cmds
      COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_MVN} package)
    # GUI-GATED, preserving the autotools semantics (same ENABLE_GUI
    # reasoning), even though the mechanism differs: the reactor POM's
    # <modules> already lists all 24, terminal included (Wave F), so a bare
    # `mvn package` always builds it. Matching autotools' "terminal only
    # when GUI is on" therefore means EXCLUDING it when GUI is off, via
    # Maven's documented "build the whole reactor minus this project"
    # selector: `-pl '!:terminal'` (modules/terminal/pom.xml's artifactId is
    # "terminal"; the leading "!" negates the selection, the leading ":"
    # matches by artifactId regardless of groupId). Safe to exclude — per
    # modules/terminal/pom.xml, terminal is a leaf consumer (action_binding,
    # commons, gui, localization), nothing else in the reactor depends on it.
    if(ENABLE_GUI)
      set(_sci_jar_summary "24 Scilab module jars via Maven (reactor package; jars land in modules/<m>/target/, not jar/)")
    else()
      list(APPEND _sci_java_cmds -pl !:terminal)
      set(_sci_jar_summary "23 Scilab module jars via Maven (reactor package minus terminal; jars land in modules/<m>/target/, not jar/; terminal skipped: GUI off)")
      message(STATUS "GUI off — terminal excluded from the Maven reactor build (sci-java-all = the 23 non-terminal modules, matching autotools)")
    endif()
    set(_sci_java_workdir ${SCILAB_SOURCE_DIR})
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
