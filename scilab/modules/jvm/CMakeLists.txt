# CMake build for the `jvm` module — Batch D (JNI/Java-native) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools jvm is always recoverable with `make -C modules/jvm`.
#
# VARIANT PAIR — TWO scilab_module() calls: the MAIN dylib and the `-disable`
# stub (the nojvm alternate, loaded when Scilab runs without a JVM). Neither
# links the other (no libsci edge on either baseline dylib) — no MODULE_DEPS.
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS. The -disable stub is
# NO_GUI_LIBS, which is EMPTY on macOS (`if IS_MACOSX`): the stub is built +
# installed but linked into NO aggregate. The helper's CLASS keyword has no
# NO_GUI_LIBS value, so the stub call omits CLASS — this comment is the record
# (same handling as Batch B's stubs).
#
# THE BATCH-D DIMENSION — linking the JDK. Autotools:
#   JAVA_JNI_LIBS    = -L$(JAVA_HOME)/lib -ljli -Wl,-rpath,$(JAVA_HOME)/lib
#   JAVA_JNI_INCLUDE = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/darwin
#   libscijvm_la_LIBADD = libscijvm-algo.la $(XML_LIBS) $(JAVA_JNI_LIBS)
# libjli.dylib's install_name is @rpath/libjli.dylib, so the -ljli link records
# the JDK-location-INDEPENDENT dep `@rpath/libjli.dylib` (the baseline fact);
# the -Wl,-rpath stamps the JDK lib dir as a third LC_RPATH (after the helper's
# /usr/lib + gcc/current pair) so dyld can resolve it at run time. Reproduced
# verbatim below: raw -L + plain `jli` in SYSTEM_LIBS (the webtools search-path
# pattern) + a post-call target_link_options rpath.
#
# JAVA_HOME is MACHINE-CONFIG-SPECIFIC — discovered, not hardcoded:
# SCILAB_JAVA_HOME comes from the shared toolchain (cmake/ScilabToolchain.cmake
# — config.status S["JAVA_HOME"] first, i.e. the EXACT JDK the autotools
# baseline linked, jdk-25 here; /usr/libexec/java_home fallback with a
# warning). This module's own discovery block (Task 8, which predated the
# shared variable) was hoisted there — Stage-1f-a Task 6.
#
# PARITY CONTRACT (arbitrated by build-parity/ — the harness-gated facts:
# file / install_name / exported symbols / recorded deps / LC_RPATHs):
#   libscijvm.2027.dylib          exports 42; deps @rpath/libjli.dylib,
#                                 /usr/lib/lib{xml2.2,z.1,icucore.A,c++.1,System.B}
#   libscijvm-disable.2027.dylib  exports 9; deps libSystem only (pure-C stub,
#                                 C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename> (+ unversioned symlinks)
#
# The three LC_RPATHs — /usr/lib, /opt/homebrew/opt/gcc/lib/gcc/current,
# $(JAVA_HOME)/lib — ARE harness-gated since Stage-1f-a (LC_RPATH is
# fingerprinted order-significantly; the baseline was re-captured with it).
# The JDK lib-dir rpath is LOAD-BEARING: @rpath/libjli.dylib resolves through
# it, so a drop would now fail parity, not just JVM startup.
#
# Sources verbatim from Makefile.am: algo = the 18 JVM_C_SOURCES; gateway =
# 2 GATEWAY_C_SOURCES + 2 GATEWAY_CPP_SOURCES; stub = src/nojvm/nojvm.c.
# EXTRA_INCLUDES beyond the helper base = the JNI pair + the la_CPPFLAGS module
# dirs not in SCILAB_DEFAULT_INCLUDES (call_scilab, string, functions_manager,
# fileio, preferences) + XML_FLAGS' SDK usr/include. The stub's automake
# include list is a subset needing only functions_manager beyond the base.

scilab_module(jvm
  ALGO_SOURCES
    src/c/InitializeJVM.c
    src/c/TerminateJVM.c
    src/c/JVM_Unix.c
    src/c/JVM.c
    src/c/addToClasspath.c
    src/c/loadOnUseClassPath.c
    src/c/loadBackGroundClassPath.c
    src/c/getClasspath.c
    src/c/system_getproperty.c
    src/c/system_setproperty.c
    src/c/loadClasspath.c
    src/c/createMainScilabObject.c
    src/c/JVM_commons.c
    src/c/catchIfJavaException.c
    src/c/loadLibrarypath.c
    src/c/getLibrarypath.c
    src/c/addToLibrarypath.c
    src/c/getJvmOptions.c
  GATEWAY_SOURCES
    sci_gateway/c/sci_javaclasspath.c
    sci_gateway/c/sci_javalibrarypath.c
    sci_gateway/cpp/sci_system_getproperty.cpp
    sci_gateway/cpp/sci_system_setproperty.cpp
  LANG C CXX
  SYSTEM_LIBS xml2 z icucore "-L${SCILAB_JAVA_HOME}/lib" jli
  EXTRA_INCLUDES
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
    ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/preferences/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS GUI_LIBS
  SYMBOLS 42)

# JAVA_JNI_LIBS' -Wl,-rpath,$(JAVA_HOME)/lib — the third LC_RPATH the baseline
# dylib carries (dyld resolves @rpath/libjli.dylib through it at run time).
target_link_options(scijvm PRIVATE "LINKER:-rpath,${SCILAB_JAVA_HOME}/lib")

scilab_module(jvm-disable
  GATEWAY_SOURCES
    src/nojvm/nojvm.c
  LANG C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
  SYMBOLS 9)
