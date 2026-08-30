# scilab/cmake/ScilabGeneratedFiles.cmake -- the configure-substituted files (RC-c).
#
# One configure_file(@ONLY) per file. BYTE-IDENTICAL to configure's copies is the
# target, not semantic equivalence: these are scalar-substitution templates, the same
# shape as version.h, which reproduces byte-for-byte. (machine.h needed a semantic
# dimension because autoconf renders un-defined macros as `/* #undef X */`; nothing
# here has that property.)
#
# The parity harness's `generated` dimension byte-hashes configure's OWN copies of all
# of them (armed since Stage 0, grown to cover all 13 -- the 3 pre-RC-c entries plus
# these 10 -- once RC-c's baseline was recaptured). By itself it does NOT look at
# anything this file writes. The separate `generated_cmake` dimension is what actually
# checks THIS file's output: it byte-hashes build-cmake/generated/<file> and compares
# against `generated`'s same baseline hashes, so a wrong or unset variable here is
# named, never silent. (A prior version of this comment claimed `generated` alone did
# that byte-compare for all of them; it did not -- resolving GENERATED_FILES against
# the source tree on BOTH sides of a comparison means comparing configure's output to
# itself regardless of what this file wrote, which corrupting a build-cmake/generated/
# file proved end-to-end. See build-parity/parity/capture.py's `generated_cmake` map and
# diff.py's matching comparison block.)
#
# Values are ALL computed here (or upstream in CMake-native cmake/*.cmake files), never
# read out of config.status. The version triple (SCILAB_VERSION_MAJOR/MINOR/MAINTENANCE)
# used to be the one exception -- ScilabConfigure.cmake (Stage 1f-c, NOT RC-a) read it
# straight out of config.status, a real, then-open dependency documented as an RC-e
# prerequisite in docs/design/build-cmake-maven-migration.md. RC-e.1 severed it:
# cmake/ScilabVersion.cmake is now the single canonical source, include()d from
# CMakeLists.txt before ScilabConfigure.cmake AND this file, so both merely consume
# already-set variables today. This file reuses that triple for scilab.pc,
# etc/Info.plist, SciDocConf.xml, both repositories*, and Version.incl below, whose
# entire substituted content derives from it -- 7 files off the one source, none of them
# a config.status read anymore. Every OTHER scalar in this file remains genuine
# CMake-side policy, each traced to its configure.ac origin -- never a config.status
# read, same as before.
#
# NOT REPRODUCED, deliberately: configure.ac:2930-2937 compares `date +%Y` against a
# year hardcoded in banner.cpp and, on mismatch, runs `sed -i` over banner.cpp AND
# etc/Info.plist.in -- i.e. the build system rewrites its own tracked sources on a
# wall-clock trigger. That is a wart worth dropping rather than carrying forward. If
# the year bump is wanted, it belongs in a release script, not in configure.

set(SCILAB_GENERATED_DIR ${CMAKE_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${SCILAB_GENERATED_DIR})

# --- values ---------------------------------------------------------------
# The version triple is set by cmake/ScilabVersion.cmake (RC-e.1), include()d from
# CMakeLists.txt ahead of both this file and ScilabConfigure.cmake -- no longer a
# config.status read (see this file's header comment above). Every other scalar below
# is genuine CMake-side policy, each traced to its configure.ac origin -- never a
# config.status read, same as before RC-e.1.

# configure.ac:58 -- SCILAB_BINARY_VERSION is the dot-joined triple, nothing more.
set(SCILAB_BINARY_VERSION "${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}.${SCILAB_VERSION_MAINTENANCE}")

# scilab.pc's 4 directory variables -- standard autoconf installation-directory
# boilerplate (present in every AC_INIT'd configure; not configure.ac-authored logic).
# prefix defaults to ac_default_prefix ("/usr/local") when --prefix is not given;
# exec_prefix/includedir/libdir default to the LITERAL, UNEXPANDED strings below,
# built from GNUInstallDirs (RC-e.1) rather than hand-transcribed -- config.status held
# S["exec_prefix"]="${prefix}", S["includedir"]="${prefix}/include",
# S["libdir"]="${exec_prefix}/lib"; CMAKE_INSTALL_INCLUDEDIR/CMAKE_INSTALL_LIBDIR
# default to the SAME "include"/"lib" on this non-multiarch, prefix=/usr/local, Darwin
# tree (GNUInstallDirs' lib64 override is Linux-only -- CMAKE_SYSTEM_NAME MATCHES
# "^(Linux|GNU)$" -- never Darwin), so the substituted text is unchanged; verified by
# the `generated_cmake` parity dimension, not merely asserted. exec_prefix has no
# GNUInstallDirs analogue (autoconf-only two-prefix boilerplate) and stays the literal
# "${prefix}" reference, matching config.status's own S["exec_prefix"]="${prefix}"
# exactly (this build never differentiates the two). pkg-config resolves ${prefix}-style
# refs itself when a .pc file is consumed, so autoconf (and this file) leaves them as
# text rather than expanding at generation time. CMAKE_INSTALL_PREFIX is CMake's own
# equivalent of prefix and defaults to the SAME "/usr/local" (ScilabMachineHeader.cmake's
# INSTALLPREFIX already established this fact for machine.h). The backslash before each
# leading $ is load-bearing: it stops CMake evaluating ${prefix}/${exec_prefix} as ITS
# OWN variable reference at set()-time, so the literal token survives into the
# substituted output, matching autoconf's own unexpanded style (round-trip verified
# against the live scilab.pc.in). GNUInstallDirs is included here, at first use, rather
# than at the top of the file: nothing above this point needs it, and no install() rule
# exists anywhere in this CMake tree yet for it to interact with.
include(GNUInstallDirs)
set(prefix "${CMAKE_INSTALL_PREFIX}")
set(exec_prefix "\${prefix}")
set(includedir "\${prefix}/${CMAKE_INSTALL_INCLUDEDIR}")
set(libdir "\${exec_prefix}/${CMAKE_INSTALL_LIBDIR}")

# configure.ac:112-127 declares 6 independent --enable-debug-* sub-flags. JAVAC_DEBUG
# (scilab.properties) and LOGGING_LEVEL (etc/logging.properties) both key off
# debug-java alone (:373,396-401,1522-1527) -- a DIFFERENT axis from
# SCILAB_ENABLE_DEBUG (ScilabFlags.cmake, RC-b; configure's plain --enable-debug). No
# CMake option models --enable-debug-java yet; this driver reproduces its default
# (off) build, matching this tree's config.status (JAVAC_DEBUG=off, LOGGING_LEVEL=
# SEVERE).
set(JAVAC_DEBUG "off")
set(LOGGING_LEVEL "SEVERE")

# The 12 module-activation booleans etc/modules.xml substitutes (11 real gates + the
# @HELP_ENABLE@ token that only appears inside a comment -- see the note below).
#
# Four reuse RC-a's OWN policy verbatim: each underlying AC_DEFINE(WITH_*) fires
# unconditionally once its library is found (ScilabMachineHeader.cmake's bucket-4
# comment) -- exactly the fact WITH_FFTW/WITH_MATIO/WITH_UMFPACK/WITH_XCOS already
# encode, so re-deriving them independently here would risk drifting from that
# established policy instead of sharing it.
if(WITH_FFTW)
  set(FFTW_ENABLE "yes")
else()
  set(FFTW_ENABLE "no")
endif()
if(WITH_MATIO)
  set(MATIO_ENABLE "yes")
else()
  set(MATIO_ENABLE "no")
endif()
if(WITH_UMFPACK)
  set(UMFPACK_ENABLE "yes")
else()
  set(UMFPACK_ENABLE "no")
endif()
if(WITH_XCOS)
  set(XCOS_ENABLE "yes")
else()
  set(XCOS_ENABLE "no")
endif()

# JAVA_ENABLE (configure.ac:1548-1553) answers "was a JDK found" ($JAVAC non-empty).
# This driver requires one unconditionally -- ScilabToolchain.cmake FATAL_ERRORs
# before project() even runs if SCILAB_JAVA_HOME has no usable JDK -- so a successful
# CMake configure has already proven the analogue of that check. Computed from
# SCILAB_JAVA_HOME rather than WITH_GUI (a related but distinct user-facing toggle
# that happens to be ON here too).
if(SCILAB_JAVA_HOME)
  set(JAVA_ENABLE "yes")
else()
  set(JAVA_ENABLE "no")
endif()

# GUI_ENABLE (configure.ac:1566-1576) = yes if JAVA_ENABLE!=no OR with_gui!=no.
#
# HONEST DISCLOSURE: in THIS driver GUI_ENABLE is STRUCTURALLY always "yes", and
# the "OR WITH_GUI" clause below is dead -- it is never the deciding term, because
# JAVA_ENABLE alone already satisfies the OR (see the JAVA_ENABLE comment above:
# a JDK is mandatory here, not merely on-by-default). Upstream's JAVA_ENABLE=no
# branch IS reachable -- autoconf treats Java as optional (--without-jdk) -- so
# configure's OR genuinely has two live arms; this driver's toolchain FATAL_ERRORs
# before project() runs if no usable JDK exists, so only one arm is ever live
# here. WITH_GUI (ScilabMachineHeader.cmake, RC-a option, default ON) is a real
# cache variable, but no CMake option currently makes WITH_GUI=OFF skip anything
# downstream -- it is consumed nowhere else in the build -- so this clause has
# never been exercised as an actual switch. The condition is kept in this
# OR-shaped form only to mirror configure.ac's own formula for traceability, not
# because WITH_GUI does any work. (Making GUI_ENABLE track WITH_GUI directly
# would DIVERGE from configure in a configuration -- with_gui=no, Java found --
# this coexistence-stage driver cannot test; that is an "improve", not a
# "reproduce", and does not belong here.)
#
# FORWARD HAZARD: etc/modules.xml therefore ALWAYS activates the 10 modules keyed
# on @GUI_ENABLE@ (gui, graphic_objects, scinotes, guibuilder, ui_data, terminal)
# or on the DEMOTOOLS_ENABLE/GRAPHICS_ENABLE cascade below (demo_tools, graphics,
# renderer, graphic_export) regardless of WITH_GUI's value, TODAY. The moment a
# future stage wires WITH_GUI=OFF to actually skip BUILDING the GUI-side jars, this
# generated file will still tell the runtime to activate those 10 modules --
# producing a ClassNotFoundException, not a clean headless configuration.
# Whoever makes WITH_GUI load-bearing MUST revisit this block (and the cascade).
if(JAVA_ENABLE STREQUAL "yes" OR WITH_GUI)
  set(GUI_ENABLE "yes")
else()
  set(GUI_ENABLE "no")
endif()

# HELP_ENABLE (configure.ac:2159-2171) = yes unless --disable-build-help or
# JAVA_ENABLE=no. No CMake option models --disable-build-help as a module-activation
# gate here (ScilabHelp.cmake's separate BUILD_HELP switch governs the `doc`
# post-step target, a different concern); this driver reproduces the JAVA_ENABLE
# half, which is what this tree actually exercises (both flags are "yes" here).
# NOTE: etc/modules.xml.in's helptools <module> entry is hardcoded activate="yes",
# NOT @HELP_ENABLE@ (see the template's own comment, :79-82) -- this variable is
# still required because @HELP_ENABLE@ appears a second time, inside that same
# comment's prose, and must substitute or the byte-comparison fails on the comment.
if(JAVA_ENABLE STREQUAL "yes")
  set(HELP_ENABLE "yes")
else()
  set(HELP_ENABLE "no")
endif()

# JAVASCI_ENABLE (configure.ac:2193-2197) = yes unless JAVA_ENABLE=no or $JAVASCI=no;
# $JAVASCI has no CMake analogue (it is itself unset on this tree, so that clause
# never fires there either) -- reproduces the JAVA_ENABLE half.
if(JAVA_ENABLE STREQUAL "yes")
  set(JAVASCI_ENABLE "yes")
else()
  set(JAVASCI_ENABLE "no")
endif()

# DEMOTOOLS_ENABLE / GRAPHICS_ENABLE (configure.ac:2236-2242, :2248-2254) both
# = yes unless GUI_ENABLE=no. Same honest disclosure as GUI_ENABLE above, one
# level down the cascade: since GUI_ENABLE is structurally always "yes" here,
# so are these two -- demo_tools (@DEMOTOOLS_ENABLE@) and graphics/renderer/
# graphic_export (@GRAPHICS_ENABLE@) activate unconditionally today, and the
# SAME forward hazard applies transitively (see GUI_ENABLE's comment for the
# full 9-module list and the ClassNotFoundException risk once WITH_GUI becomes
# load-bearing) -- nothing further to add here, this is not an independent gate.
if(GUI_ENABLE STREQUAL "yes")
  set(DEMOTOOLS_ENABLE "yes")
else()
  set(DEMOTOOLS_ENABLE "no")
endif()
if(GUI_ENABLE STREQUAL "yes")
  set(GRAPHICS_ENABLE "yes")
else()
  set(GRAPHICS_ENABLE "no")
endif()

# MPI_ENABLE (configure.ac:1809-1821): "no" unless --with-mpi is explicitly passed
# (never is here -- "hard to package", the comment there says); no CMake option
# exists for it, matching ScilabMachineHeader.cmake's own ENABLE_MPI, "deliberately
# left OFF -- genuinely undef in the reference too."
set(MPI_ENABLE "no")

# WITH_TKSCI (configure.ac:2034-2037): NOT just a default -- on macOS (this driver's
# only target platform) configure hard AC_MSG_ERRORs if --with-tk is passed at all
# ("Tcl/Tk must be disabled under Mac OS X"), so "no" is the only value this build can
# ever produce. Mirrors ScilabMachineHeader.cmake's WITH_TK, likewise never declared.
set(WITH_TKSCI "no")

# --- the files ------------------------------------------------------------
foreach(_f scilab.pc scilab.properties etc/logging.properties
           etc/modules.xml etc/Info.plist
           modules/helptools/etc/SciDocConf.xml
           modules/atoms/etc/repositories
           modules/atoms/tests/unit_tests/repositories.orig)
  get_filename_component(_d ${SCILAB_GENERATED_DIR}/${_f} DIRECTORY)
  file(MAKE_DIRECTORY ${_d})
  configure_file(${SCILAB_SOURCE_DIR}/${_f}.in ${SCILAB_GENERATED_DIR}/${_f} @ONLY)
endforeach()

# Version.incl -- NOT an AC_CONFIG_FILES entry. configure.ac:2965 writes it with a raw
# shell echo, guarded (configure.ac:2961) by a comparison against a version string
# scraped out of modules/gui/images/icons/aboutscilab.svg -- and when that guard fires,
# the SAME block also `sed`s modules/core/includes/version.h{,.in,.vc} and
# .gitlab-ci.yml's DOCKER_TAG (configure.ac:2966-2975), a broader side effect this
# generator does not reproduce (nor does anything else in this file -- out of scope,
# not merely missed).
#
# RC-c final-review Finding (Minor 3): this CMake write below is UNCONDITIONAL -- no
# equivalent guard, every configure. Today the guard never fires (SCILAB_BINARY_VERSION
# already matches the scraped aboutscilab.svg string), so both paths produce the same
# bytes; this is a real control-flow divergence, not (yet) an observable one, recorded
# here so a future version bump that DOES trip the guard is not a surprise -- the
# generated Version.incl would still update (this write always runs), but nothing here
# would touch version.h/.gitlab-ci.yml the way configure's guarded block does.
#
# build.incl.xml:154 stamps every jar's Specification-Version from it, so it matters
# despite being invisible to any inventory built from config.status.
file(WRITE ${SCILAB_GENERATED_DIR}/Version.incl
     "SCIVERSION=scilab-branch-${SCILAB_VERSION_MAJOR}.${SCILAB_VERSION_MINOR}\n")
