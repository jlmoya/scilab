# modules/javasci/CMakeLists.txt — the javasci native half: libjavasci2.
#
# WHY THIS FILE EXISTS (register B17): the Ant->Maven/CMake migration ported
# javasci's JAVA half but never its NATIVE half. Until this file, there was no
# javasci CMake target at all — `cmake --build build-cmake --target help` listed
# none, the generated build-cmake/Makefile contained zero javasci mentions, and
# build-cmake/compile_commands.json held zero javasci TUs. The
# libjavasci2.2027.dylib sitting in .libs/ was a leftover from the last autotools
# build (Jul 19 2026, months newer siblings all around it), and .libs/ is
# untracked, so a FRESH CLONE had no libjavasci2 whatsoever. Parity still passed
# only because that leftover lingered: hide it and parity.diff reports
# "dylib missing in candidate: libjavasci2.VER.dylib". Proven by fault injection.
#
# NOT scilab_module(): that helper REQUIRES GATEWAY_SOURCES and emits
# libsci<name>.2027.dylib. javasci ships no Scilab gateway — its native half is
# purely the JNI bridge that Java's Call_ScilabJNI.loadLibrary("javasci2") opens,
# and the artifact is libjavasci2.2027.dylib (no "sci" prefix). So the target is
# declared directly here, but it REUSES the helper's own flag/include environment
# (_scilab_module_flag_env + _scilab_module_apply) rather than transcribing
# flags: build-parity's per-TU flagfacts_check gate walks compile_commands.json
# and expects the tree-default C facts (-O2 -fwrapv -g1 -DNDEBUG) for these TUs —
# the frozen baseline records ZERO javasci overrides — so the flags must come
# from the one shared source or the gate fails the moment these TUs appear in the
# compile DB for the first time.
#
# ORDERING: this directory is added AFTER modules/CMakeLists.txt (see the top-level
# CMakeLists.txt), because libjavasci2 links the libscilab AGGREGATE and so must be
# declared once that target exists. That mirrors autotools, where modules/Makefile.am
# ordered javasci last for exactly the same reason (top-level CMakeLists.txt:130-134
# already documents libjavasci2 as "a SEPARATE baseline artifact that links AGAINST
# libscilab.la and is built AFTER it").
#
# LINK TOPOLOGY — deliberately libscilab (the GUI aggregate), reproducing the
# baseline artifact rather than improving it. This is ALSO the subject of register
# B16: javasci's DEFAULT mode is non-advanced, which routes through
# DisableInteractiveMode() -> SCILAB_NWNI, and InitScilab.cpp's
# checkForLinkerErrors() then requires the DISABLED jvm lib and calls exit(1)
# when it does not get it — which is what libscilab supplies, since it carries
# the real libscijvm. Advanced mode (new Scilab(true)) is the combination this
# link serves correctly. Changing it is B16's job, as a deliberate, re-baselined
# divergence; B17 (this file) restores the artifact faithfully first so parity
# stays meaningful while that decision is made.

set(_dir ${CMAKE_CURRENT_SOURCE_DIR})

# Beyond SCILAB_DEFAULT_INCLUDES: call_scilab (call_scilab.h, fromjava.h),
# graphic_objects (FigureList.h), this module's own src/c (javasci2_helper.h),
# and the JDK's JNI headers (jni.h + the darwin jni_md.h).
set(M_EXTRA_INCLUDES
  ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
  ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
  ${_dir}/src/c
  ${SCILAB_JAVA_HOME}/include
  ${SCILAB_JAVA_HOME}/include/darwin)

_scilab_module_flag_env()

# SOURCES — the three POSIX TUs, matched against the baseline artifact's own debug
# map (`nm -a` OSO entries): call_scilab_wrap.o, javasci2_helper.o, putLists.o.
#
# src/c/DllmainJavasci.c is deliberately ABSENT: it is wholly Windows-only (46
# lines, `#include <windows.h>` + `int WINAPI DllMain(HINSTANCE, ...)`, with no
# POSIX guard around either), so it cannot compile here at all — and the baseline
# dylib confirms autotools never compiled it (`nm -a` finds ZERO DllMain symbols).
# Listing it produced exactly the expected `fatal error: 'windows.h' file not found`.
#
# FOLD-IN — the baseline exports 885 symbols of which only 28 are JNI entry
# points; the other ~857 (FindOpt, FirstOpt, the api_stack_*/api_template_* set,
# returnProperty*) come from autotools folding the api_scilab noinst convenience
# library into this dylib. Its objects are reused here via the SAME OBJECT lib the
# aggregates consume, so the symbol set is reproduced rather than re-derived.
# Yes, these objects therefore exist in both libscilab and libjavasci2 — that
# duplication is what the baseline does, and reproduce-don't-improve keeps it.
add_library(javasci2 SHARED
  src/jni/call_scilab_wrap.c   # SWIG-generated from src/jni/call_scilab.i
  src/jni/putLists.c
  src/c/javasci2_helper.c
  $<TARGET_OBJECTS:sciapi_scilab-obj>)

_scilab_module_apply(javasci2)

# LINK LIST — reproduces libtool's FLATTENED dependency set. The baseline dylib
# records 60 direct deps; measured, that set is exactly `libscilab` PLUS
# libscilab's own 59, i.e. libtool re-listed the whole transitive closure as
# DIRECT deps of javasci2. Linking libscilab alone builds and exports an
# identical 885-symbol set, but records only 3 deps and fails parity with
# "link dependencies changed".
#
# The closure is DERIVED from the aggregate's own LINK_LIBRARIES rather than
# transcribed: a hand-copied 59-name list would silently drift the first time the
# aggregate's composition changes, whereas this follows it by construction.
# Reproduce-don't-improve applies with force here: LC_LOAD_DYLIB ORDER is what
# decides flat-namespace symbol resolution under -undefined dynamic_lookup, and
# that is the very mechanism register B16 turns on (libscijvm vs
# libscijvm-disable). Leaning out this list is B16's deliberate, re-baselined
# call, not a side effect of porting the target.
get_target_property(_scilab_agg_links libscilab LINK_LIBRARIES)
target_link_libraries(javasci2 PRIVATE libscilab ${_scilab_agg_links})

# Same darwin link contract as every other Scilab dylib (scilab_module():374,
# scilab_aggregate():145): libtool's `-undefined dynamic_lookup`, i.e. leave
# cross-module symbols to be resolved at LOAD time, plus -no_fixup_chains because
# dynamic_lookup is incompatible with chained fixups. Without these the folded
# api_scilab objects' cross-module references (and the JNI entry points' own
# call_scilab references) fail at link time with "symbol(s) not found".
#
# The LC_RPATH pair is the SCI_LDFLAGS pair every sibling stamps, in this order;
# the baseline artifact carries exactly ['/usr/lib', '/opt/homebrew/opt/gcc/lib/
# gcc/current'] and the parity rpath gate is order-significant.
#
# -nostdlib++ for the same reason scilab_aggregate() uses it (ScilabAggregate.cmake:146):
# javasci's OWN sources are all C, so autotools linked this dylib with the C driver
# and recorded NO libc++ dep. Here the folded api_scilab objects are C++, so CMake
# picks the CXX linker language and clang++ would add -lc++ implicitly — which showed
# up as exactly one extra dep, @rpath/libc++.1.dylib (61 vs the baseline's 60). The
# libc++ symbols still resolve at load time via dynamic_lookup, as they do for the
# aggregates.
target_link_options(javasci2 PRIVATE
  -nostdlib++
  "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains"
  "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")

# Same naming/versioning contract as scilab_module()'s dylibs: the version is
# baked into OUTPUT_NAME (NOT CMake VERSION/SOVERSION, which would produce
# libjavasci2.dylib.2027), the install-name is the absolute /usr/local/lib/scilab
# path every sibling records, and the Mach-O versions match the tree-wide value.
set_target_properties(javasci2 PROPERTIES
  OUTPUT_NAME "javasci2.${SCILAB_LIBRARY_VERSION_MAJOR}"
  PREFIX "lib" SUFFIX ".dylib"
  INSTALL_NAME_DIR "/usr/local/lib/scilab"
  BUILD_WITH_INSTALL_NAME_DIR TRUE
  MACHO_COMPATIBILITY_VERSION "${SCILAB_MACHO_VERSION}.0.0"
  MACHO_CURRENT_VERSION "${SCILAB_MACHO_VERSION}.0.0"
  # LC_RPATH is EXACTLY the two -rpath flags above, in order — without this CMake
  # appends its own build-tree rpaths and the order-significant gate fails.
  SKIP_BUILD_RPATH TRUE)

# Drop-in, mirroring drop-in-<name>: the launcher, the packager and build-parity
# all read modules/<m>/.libs/, so the built dylib lands there with its
# unversioned symlink beside it.
add_custom_target(drop-in-javasci
  COMMAND ${CMAKE_COMMAND} -E make_directory ${_dir}/.libs
  COMMAND ${CMAKE_COMMAND} -E copy
          $<TARGET_FILE:javasci2> ${_dir}/.libs/$<TARGET_FILE_NAME:javasci2>
  COMMAND ${CMAKE_COMMAND} -E create_symlink
          $<TARGET_FILE_NAME:javasci2> ${_dir}/.libs/libjavasci2.dylib
  DEPENDS javasci2 VERBATIM
  COMMENT "Dropping CMake-built javasci2 into modules/javasci/.libs/ (launcher + packager read here)")
if(TARGET drop-in-all)
  add_dependencies(drop-in-all drop-in-javasci)
endif()

# ---------------------------------------------------------------------------
# javasci2-cli — the NWNI-capable variant (register B16).
#
# THE PROBLEM: javasci's DEFAULT mode is non-advanced (Scilab.java:67
# advancedMode=false), which routes through DisableInteractiveMode() ->
# setScilabMode(SCILAB_NWNI) (call_scilab.c:62-65). InitScilab.cpp's
# checkForLinkerErrors() then REQUIRES isItTheDisabledLib() == TRUE in NWNI mode
# and calls exit(1) otherwise. That symbol is undefined (dynamic_lookup) in the
# aggregates and DEFINED by both jvm variants with opposite answers —
# modules/jvm/src/nojvm/nojvm.c:109 returns TRUE (libscijvm-disable),
# modules/jvm/src/c/JVM.c:314 returns FALSE (libscijvm) — so which one the load
# chain pulls decides it, exactly as scilab-cli-bin vs scilab-bin decide it for
# the executables. The main libjavasci2 above links libscilab, which carries the
# real libscijvm, so NWNI always exit(1)s. Advanced mode is the combination that
# link serves correctly, which is why BOTH are needed: the legacy suite has 14
# `new Scilab()` (NWNI) tests and 5 `new Scilab(true)` (advanced) ones.
#
# THE SHAPE: same sources and same folded objects, but linked against
# libscilab-cli (0 scijvm deps — it folds the -disable objects) PLUS an explicit
# scijvm-disable, mirroring how scilab-cli-bin links. The file keeps the name
# libjavasci2.dylib and differs only by DIRECTORY, so Java needs no change at all:
# System.loadLibrary("javasci2") resolves per java.library.path, and a test run
# selects the variant purely by pointing that at this directory instead of the farm.
#
# NOT a parity artifact ON PURPOSE: build-parity's dylib walk keys off directories
# named exactly ".libs" (capture.py:211), so emitting here adds nothing to the
# fingerprint and needs no baseline change — this variant is test infrastructure,
# not a shipped library. It is deliberately NOT registered on drop-in-all for the
# same reason.
add_library(javasci2-cli SHARED
  src/jni/call_scilab_wrap.c
  src/jni/putLists.c
  src/c/javasci2_helper.c
  $<TARGET_OBJECTS:sciapi_scilab-obj>)

_scilab_module_apply(javasci2-cli)

get_target_property(_scilab_cli_links libscilab-cli LINK_LIBRARIES)
target_link_libraries(javasci2-cli PRIVATE
  libscilab-cli ${_scilab_cli_links}
  scijvm-disable)          # THE point of this variant: isItTheDisabledLib() -> TRUE

target_link_options(javasci2-cli PRIVATE
  -nostdlib++
  "LINKER:-undefined,dynamic_lookup" "LINKER:-no_fixup_chains"
  "LINKER:-rpath,/usr/lib" "LINKER:-rpath,/opt/homebrew/opt/gcc/lib/gcc/current")

# Unversioned name: System.loadLibrary("javasci2") looks for exactly
# libjavasci2.dylib on the java.library.path, and nothing links this by
# install-name, so no versioned soname is needed.
set_target_properties(javasci2-cli PROPERTIES
  OUTPUT_NAME "javasci2"
  PREFIX "lib" SUFFIX ".dylib"
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/test-native-libs-cli"
  INSTALL_NAME_DIR "/usr/local/lib/scilab"
  BUILD_WITH_INSTALL_NAME_DIR TRUE
  SKIP_BUILD_RPATH TRUE)
