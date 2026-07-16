# CMake build for the `completion` module — Batch A (simple leaf) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools completion is always recoverable with
# `make -C modules/completion`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscicompletion.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscicompletion.2027.dylib
#   exports:       exactly 107 symbols (completion engine + fields getters +
#                  the SWIG JNI wrapper's Java_* entry points)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources (noinst algo lib): COMPLETION_JNI_SOURCES (src/jni/Completion_wrap.c
# — GUI=on in the baseline config, so the SWIG wrapper compiles) +
# 9 COMPLETION_C_SOURCES + 15 COMPLETION_CPP_SOURCES; gateway:
# 1 GATEWAY_CPP_SOURCES — all verbatim from Makefile.am.
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscicompletion_la_CPPFLAGS: graphics, fileio, string, xml/{includes,
# src/cpp}, external_objects — plus JAVA_JNI_INCLUDE (the configured JDK's
# include dirs; jni.h for the SWIG wrapper) and XML_FLAGS' SDK usr/include
# (libxml/ headers ship in the SDK). MACHINE-CONFIG-SPECIFIC: the JDK-25 and
# Xcode-SDK paths are configure-detected on this machine; the future
# de-autotools driver derives them (FindJNI / xcrun --show-sdk-path).

scilab_module(completion
  ALGO_SOURCES
    src/jni/Completion_wrap.c
    src/c/completion.c
    src/c/completion_generic.c
    src/c/getfilesdictionary.c
    src/c/toolsdictionary.c
    src/c/completion_wrap_java.c
    src/c/getPartLine.c
    src/c/getCommonPart.c
    src/c/completeLine.c
    src/c/completeLine_wrap_java.c
    src/cpp/FieldsManager.cpp
    src/cpp/XMLFieldsGetter.cpp
    src/cpp/getfields.cpp
    src/cpp/getfieldsdictionary.cpp
    src/cpp/XMLDocFieldsGetter.cpp
    src/cpp/XMLElemFieldsGetter.cpp
    src/cpp/XMLAttrFieldsGetter.cpp
    src/cpp/XMLNsFieldsGetter.cpp
    src/cpp/XMLListFieldsGetter.cpp
    src/cpp/XMLSetFieldsGetter.cpp
    src/cpp/EOFieldsGetter.cpp
    src/cpp/UnknownMlistFieldsGetter.cpp
    src/cpp/StructFieldsGetter.cpp
    src/cpp/HandleFieldsGetter.cpp
    src/cpp/DllmainCompletion.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_completion.cpp
  LANG CXX C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/xml/includes
    ${SCILAB_SOURCE_DIR}/modules/xml/src/cpp
    ${SCILAB_SOURCE_DIR}/modules/external_objects/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS
  SYMBOLS 107)
