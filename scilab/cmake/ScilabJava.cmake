# scilab/cmake/ScilabJava.cmake — the CMake->Ant/Maven bridge.
#
# Stage 1f-b wired the (back then unconditional) Ant path. This adds the
# CMake<->Maven swap the migration doc's remaining-work item 1 names
# (docs/design/build-cmake-maven-migration.md, "What remains before Ant can
# be deleted"): a SCILAB_JAVA_BUILD switch selects which toolchain the
# sci-java-all target actually runs.
#
#   SCILAB_JAVA_BUILD = ant (CACHE STRING, opt-in since RC-e.4a) — modules/prebuildjava/
#     build.xml (default "all") hand-topo-sorts 23 module jars and drives Ivy
#     (the 24th, terminal, is a separate GUI-gated command). The topo-sort /
#     inter-module Java deps stay INSIDE Ant. Reproduces exactly how
#     Makefile.incl.am's `java:` target runs it: bare `ant` in
#     modules/prebuildjava with JAVA_HOME exported. Jars land in
#     modules/<m>/jar/ (the same place `make` writes them). Through RC-e.3 this
#     was the default; RC-e.4a made maven the default (jar/ is no longer what
#     classpath.xml loads), so ant now runs only when -DSCILAB_JAVA_BUILD=ant
#     is passed.
#
#   SCILAB_JAVA_BUILD = maven (CACHE STRING, DEFAULT since RC-e.4a) — runs
#     `mvn package` at the reactor root (${SCILAB_SOURCE_DIR}/pom.xml, Stage
#     2-f/Wave F — all 24 modules, terminal included). That is the whole
#     build action: Maven writes every jar to modules/<m>/target/ and it
#     STAYS there — no copy to jar/, no POM change. RC-e.4a completed the
#     consumption cutover that repoints classpath.xml at target/ instead of
#     jar/ (both etc/classpath.xml and etc/classpath.xml.in), so target/ is now
#     the path the running app loads from.
#
# RC-e.2: ANT and the two automake gates no longer come from config.status —
# each is resolved natively (see the code below); this paragraph now records
# what they MEAN (the autotools facts being reproduced), not where CMake reads
# them from:
#   ANT                 the ant binary — find_program(ant) (was config.status
#                        S["ANT"]="…/ant", the configured Ant; Ant's whole
#                        machinery goes away at RC-e.4, so this is a bridge,
#                        not a permanent home).
#   NEED_JAVA_TRUE       automake conditional: "" when Java IS in this build,
#                        "#" when it is not (configure.ac: NEED_JAVA =
#                        jdk AND (javasci OR gui OR help)) — now ENABLE_JAVA,
#                        a CACHE BOOL.
#   GUI_TRUE             same convention (GUI = jdk AND gui) — strictly
#                        NARROWER than NEED_JAVA; gates the terminal jar
#                        in BOTH backends (see scilab_java_bridge() below) —
#                        now ENABLE_GUI, a CACHE BOOL.
# Maven has no config.status entry of its own: there is no autotools-configured
# "the exact Maven binary" the way S["ANT"] pinned Ant, so `mvn` is resolved off
# PATH via find_program() below instead — unchanged by RC-e.2, it was already native.

# SCILAB_JAVA_BUILD — the jar-build backend switch. A CACHE STRING (not a
# plain option() BOOL) because there are two named backends, not an on/off
# toggle. RC-e.4a: "maven" is now the DEFAULT (was "ant" through Stage 1f-b /
# RC-e.3). This is the consumption cutover: etc/classpath.xml now loads the 24
# module jars from modules/<m>/target/ (Maven's natural output — its home), and
# ONLY the Maven backend fills target/, so the default backend and the path the
# running app loads from MUST agree. "ant" stays selectable
# (-DSCILAB_JAVA_BUILD=ant), but its modules/<m>/jar/ output is no longer what
# classpath.xml points at; jar/ retires together with Ant at the RC-e.4
# deletion. The STRINGS property below is only a ccmake/cmake-gui dropdown
# hint — CMake does not itself reject an off-list value for a CACHE STRING, so
# the FATAL guard right after it is what actually catches a typo rather than
# letting it silently fall into a backend branch below.
set(SCILAB_JAVA_BUILD "maven" CACHE STRING
    "Java module jar build backend: maven (default, modules/<m>/target/) or ant (opt-in, modules/<m>/jar/)")
set_property(CACHE SCILAB_JAVA_BUILD PROPERTY STRINGS ant maven)
if(NOT SCILAB_JAVA_BUILD STREQUAL "ant" AND NOT SCILAB_JAVA_BUILD STREQUAL "maven")
  message(FATAL_ERROR "SCILAB_JAVA_BUILD must be 'ant' or 'maven' (got '${SCILAB_JAVA_BUILD}')")
endif()

# RC-e.2: find_program, not config.status's S["ANT"] -- verified to resolve to
# the identical binary path this tree's config.status recorded (both are the
# same version-manager shim already on PATH). No parse guard needed here
# either: find_program leaves SCILAB_ANT as SCILAB_ANT-NOTFOUND (a falsy
# value) when ant isn't found, and the EXISTS check in scilab_java_bridge()
# below FATALs showing it -- same discipline as before, just a native lookup
# instead of a config.status parse.
find_program(SCILAB_ANT ant)

# mvn resolution: plain PATH lookup (file scope, unconditional, like SCILAB_ANT
# above), but only FATAL-guarded at point of use inside scilab_java_bridge() —
# so an ant-mode configure on a machine with no Maven installed is completely
# unaffected, matching "ant default behaves exactly as today, zero change".
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
  add_custom_target(drop-in-jars COMMENT "The Scilab module jars (Ant)")
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
    # Maven's normal reactor output, same as the ant path's unsilenced
    # USES_TERMINAL run below). Maven writes every jar straight to
    # modules/<m>/target/; nothing here copies, moves, or symlinks it anywhere
    # else.
    if(NOT SCILAB_MVN)
      message(FATAL_ERROR "mvn not found on PATH — cannot build the Java jars (SCILAB_JAVA_BUILD=maven)")
    endif()
    set(_sci_java_cmds
      COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_MVN} package)
    # GUI-GATED, matching the ant branch's semantics below (same ENABLE_GUI
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
    if(ENABLE_GUI)
      set(_sci_jar_summary "24 Scilab module jars via Maven (reactor package; jars land in modules/<m>/target/, not jar/)")
    else()
      list(APPEND _sci_java_cmds -pl !:terminal)
      set(_sci_jar_summary "23 Scilab module jars via Maven (reactor package minus terminal; jars land in modules/<m>/target/, not jar/; terminal skipped: GUI off)")
      message(STATUS "GUI off — terminal excluded from the Maven reactor build (sci-java-all = the 23 non-terminal modules, matching autotools)")
    endif()
    set(_sci_java_workdir ${SCILAB_SOURCE_DIR})
  else()
    # RETIRED 2026-07-21 (tag `autotools-ant-retired`). This branch drove `ant` in
    # modules/prebuildjava, but that directory and all 26 build.xml were deleted, so
    # -DSCILAB_JAVA_BUILD=ant can no longer succeed: it would locate ant, launch it,
    # and fail deep inside ant with "Buildfile: build.xml does not exist!" — a message
    # that points at ant rather than at the retirement that actually caused it.
    # Fail here instead, at configure time, naming the real reason. Kept as an explicit
    # error rather than deleting the switch so an existing CMakeCache carrying
    # SCILAB_JAVA_BUILD=ant gets an explanation instead of silently building nothing.
    message(FATAL_ERROR
      "SCILAB_JAVA_BUILD=ant is no longer supported: Ant was retired 2026-07-21 and "
      "modules/prebuildjava/ plus every build.xml were deleted. Use the default "
      "(-DSCILAB_JAVA_BUILD=maven), which builds the 24 module jars into "
      "modules/<m>/target/. See docs/design/build-cmake-maven-migration.md.")
    if(NOT SCILAB_ANT OR NOT EXISTS "${SCILAB_ANT}")
      message(FATAL_ERROR "ant not found on PATH (SCILAB_ANT='${SCILAB_ANT}') — cannot build the Java jars")
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
    if(ENABLE_GUI)
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
