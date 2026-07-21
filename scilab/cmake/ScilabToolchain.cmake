# scilab/cmake/ScilabToolchain.cmake — shared environment discovery (NOT policy).
#
# Included by the top-level CMakeLists.txt BEFORE project(): everything here is
# either a pre-project() knob (compiler defaults, OSX arch/deployment target) or
# a plain variable consumed later by scilab_module() (source root, include
# bases). Per-module policy (flags, link options, naming, drop-in) lives in
# ScilabModule.cmake.

# SCILAB_SOURCE_DIR: the configured autotools source tree (has the generated
# machine.h/version.h). Default to this file's ../.. ; overridable via -D.
if(NOT DEFINED SCILAB_SOURCE_DIR)
  get_filename_component(SCILAB_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
endif()
if(NOT EXISTS "${SCILAB_SOURCE_DIR}/modules/core/includes/machine.h")
  message(FATAL_ERROR "machine.h not found under ${SCILAB_SOURCE_DIR}; run ./configure there first.")
endif()

# SCILAB_JAVA_HOME: the ONE shared JDK location — consumed by every
# JDK-touching CMakeLists (the libjli linkers jvm/types/external_objects_java/
# xcos, the JNI-header includers helptools/io, the aggregates, scilab-bin).
#
# RC-e.2: natively resolved, no config.status read. Tier 1 is $ENV{JAVA_HOME},
# REALPATH-canonicalized — the exact mechanism configure.ac's own JDK probe
# tries first (m4/java.m4 AC_JAVA_DETECT_JVM: "check if JAVA_HOME is set...
# use it as JVM root directory"), so this is the same algorithm's first step,
# just run natively instead of transcribed off config.status. REALPATH matters
# on a machine where JAVA_HOME is a version-manager shim (jenv, sdkman, etc):
# autotools' own probe resolves it with `cd $JAVA_HOME; pwd` (plain, non-`-P`
# cd+pwd), which does NOT fully resolve a multi-level symlink chain — yet this
# tree's config.status S["JAVA_HOME"] IS the fully-resolved
# /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home, meaning configure
# was run in a shell where $JAVA_HOME already held that literal path.
# get_filename_component(... REALPATH) reproduces that same end state
# regardless of which shell/tool set $ENV{JAVA_HOME} — verified to land on the
# byte-identical string here (jenv's ~/.jenv/versions/25 shim resolves to the
# same real jdk-25.jdk directory). Tier 2 (JAVA_HOME unset or unusable) falls
# back to /usr/libexec/java_home — the find_program(java)-derived resolution
# the RC-e plan names as the native alternative — kept verbatim from before
# RC-e.2, WARNING included: it answers jdk-26 on this machine, a DIFFERENT
# major version than the configured jdk-25, so it stays the fallback tier, not
# the primary.
if(NOT "$ENV{JAVA_HOME}" STREQUAL "")
  get_filename_component(SCILAB_JAVA_HOME "$ENV{JAVA_HOME}" REALPATH)
endif()
if(NOT SCILAB_JAVA_HOME OR NOT EXISTS "${SCILAB_JAVA_HOME}/lib/libjli.dylib")
  execute_process(COMMAND /usr/libexec/java_home
                  OUTPUT_VARIABLE SCILAB_JAVA_HOME
                  OUTPUT_STRIP_TRAILING_WHITESPACE
                  RESULT_VARIABLE _scilab_java_home_rc)
  if(NOT _scilab_java_home_rc EQUAL 0
     OR NOT EXISTS "${SCILAB_JAVA_HOME}/lib/libjli.dylib")
    message(FATAL_ERROR "no JDK with lib/libjli.dylib found — \$ENV{JAVA_HOME} "
                        "is unset/unusable and /usr/libexec/java_home failed")
  endif()
  message(WARNING "\$ENV{JAVA_HOME} unset or unusable; falling back to "
                  "/usr/libexec/java_home (${SCILAB_JAVA_HOME}) — may differ "
                  "from the JDK the autotools baseline linked (compare against "
                  "config.status's S[\"JAVA_HOME\"] if the two builds must match)")
endif()
message(STATUS "SCILAB_JAVA_HOME = ${SCILAB_JAVA_HOME}")

# Autotools compiles with CC="gcc -std=gnu23 -arch arm64" and CXX="g++ -arch
# arm64 -std=c++17" — on this platform /usr/bin/gcc and /usr/bin/g++ ARE Apple
# clang/clang++. Default to the same drivers (honor explicit -DCMAKE_*_COMPILER=
# or $CC/$CXX overrides).
if(NOT DEFINED CMAKE_C_COMPILER AND NOT DEFINED ENV{CC})
  set(CMAKE_C_COMPILER gcc)
endif()
if(NOT DEFINED CMAKE_CXX_COMPILER AND NOT DEFINED ENV{CXX})
  set(CMAKE_CXX_COMPILER g++)
endif()
# gfortran (Homebrew gcc), not flang — enable_language(Fortran) is in the driver.
if(NOT DEFINED CMAKE_Fortran_COMPILER AND NOT DEFINED ENV{FC})
  set(CMAKE_Fortran_COMPILER gfortran)
endif()

set(CMAKE_OSX_ARCHITECTURES arm64)        # CC/CXX's -arch arm64
set(CMAKE_OSX_DEPLOYMENT_TARGET 11.0)     # -mmacosx-version-min=11.0 (compile + link)
# NB: CMake applies NEITHER OSX_* variable to Fortran TUs — Fortran gets its
# -mmacosx-version-min only from scilab_module()'s explicit Fortran flags, and
# its arch rides on the host default (arm64 here).

# The parity harness reads compile commands from compile_commands.json to
# verify the semantic flag facts (opt/wrapv/min_macos/ndebug/std).
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# The default Scilab include base: the intersection of the 4 exemplars' include
# sets, PLUS modules/dynamic_link/includes (which most gateways use — sound's
# autotools include line is the one of the four that lacks it, so it is not a
# true intersection member; carried here because the broader roll-out needs it,
# and it is harmless/de-duplicated for a module that does not). Modules add
# further extras via EXTRA_INCLUDES. Order preserved.
set(SCILAB_DEFAULT_INCLUDES
  ${SCILAB_SOURCE_DIR}/modules/core/includes
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/ast
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/exps
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/operations
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/parse
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/symbol
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/system_env
  ${SCILAB_SOURCE_DIR}/modules/ast/includes/types
  ${SCILAB_SOURCE_DIR}/modules/api_scilab/includes
  ${SCILAB_SOURCE_DIR}/modules/localization/includes
  ${SCILAB_SOURCE_DIR}/modules/output_stream/includes
  ${SCILAB_SOURCE_DIR}/modules/dynamic_link/includes)

# Homebrew CPPFLAGS base (configure-detected on this machine; the future
# de-autotools driver derives these). Do NOT put libomp/libxml2 here — those come
# from find_package / SYSTEM_LIBS per module.
set(SCILAB_HOMEBREW_INCLUDES /opt/homebrew/include /opt/homebrew/opt/libarchive/include)
