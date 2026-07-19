# CMake build for the `types` module dir — Batch E (inter-module edge + JDK)
# on the shared scilab_module() helper (cmake/ScilabModule.cmake); built from
# the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools original is always recoverable with
# `make -C modules/types`.
#
# ONE dylib only: `libscitypes-java` (the JNI wrappers, pkglib under GUI).
# The module's own libscitypes.la is a CONVENIENCE library folded into the
# libscilab aggregates — it builds no standalone dylib, is absent from the
# parity baseline, and is deliberately NOT declared here (manifest scope
# note).
#
# LINKING CLASS (modules/Makefile.am): GUI_LIBS.
#
# MODULE EDGES + THE JDK DEP: libscitypes_java_la_LIBADD = libscicommons.la +
# libscijvm.la. The baseline's xml2/z/icucore and @rpath/libjli.dylib deps are
# TRANSITIVE — libtool expands libscijvm.la's dependency_libs ($(XML_LIBS) +
# $(JAVA_JNI_LIBS)' `-L$(JAVA_HOME)/lib -ljli`) onto this link line, but
# DROPS the -Wl,-rpath linker flag from dependency_libs. Hence: the dep
# records as the JDK-location-independent `@rpath/libjli.dylib` (libjli's
# install_name) and the dylib carries NO JDK LC_RPATH — only the helper's
# standard /usr/lib + gcc/current pair (verified by `otool -l` on the
# baseline; @rpath/libjli resolves through the hosting executable's rpath at
# load time). Reproduced verbatim: MODULE_DEPS for the two siblings +
# SYSTEM_LIBS raw `-L` + plain `jli` (the jvm Task-8 pattern) and NO post-call
# rpath (deliberately unlike jvm, whose own Makefile.am spells the -Wl,-rpath
# directly).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscitypes-java.2027.dylib  exports 79; deps
#       /usr/local/lib/scilab/libscicommons.2027.dylib
#       /usr/local/lib/scilab/libscijvm.2027.dylib
#       /usr/lib/lib{xml2.2,z.1,icucore.A}.dylib
#       @rpath/libjli.dylib + libc++ + libSystem
#
# Sources verbatim from Makefile.am (1 SWIG C wrapper + 4 C++); no algo lib.
# EXTRA_INCLUDES = la_CPPFLAGS dirs beyond the helper base (the JNI pair,
# src/jni, commons/src/jni, jvm/includes; la_CFLAGS' dirs are a subset).
# SCILAB_JAVA_HOME comes from the toolchain (cmake/ScilabToolchain.cmake —
# config.status S["JAVA_HOME"]).

scilab_module(types-java
  GATEWAY_SOURCES
    src/jni/getScilabVariable_wrap.c
    src/cpp/ScilabToJava.cpp
    src/jni/ScilabVariables.cpp
    src/jni/ScilabVariablesRefresh.cpp
    src/jni/ScilabPolynomialToJava.cpp
  LANG C CXX
  # REPRODUCES the automake _CFLAGS footgun (Makefile.am:50-58 sets
  # libscitypes_java_la_CFLAGS to a bare -I list, which REPLACES $(AM_CFLAGS)
  # wholesale -- the parameters/windows_tools/string precedent) --
  # getScilabVariable_wrap.c (this call's only C TU) compiles with none of
  # SCI_CFLAGS, only $(CC)'s own -std=gnu23. The other 4 GATEWAY_SOURCES are
  # C++ (untouched; no _CXXFLAGS override exists). Reproduce, do not improve:
  # restoring -O2 -fwrapv here is a separate stage.
  C_FLAGS_OVERRIDE -std=gnu23
  MODULE_DEPS scicommons scijvm
  SYSTEM_LIBS xml2 z icucore "-L${SCILAB_JAVA_HOME}/lib" jli
  EXTRA_INCLUDES
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
  CLASS GUI_LIBS
  SYMBOLS 79)
