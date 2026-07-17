# CMake build for the `helptools` module — Batch E (inter-module edge) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools helptools is always recoverable with
# `make -C modules/helptools`.
#
# VARIANT PAIR — TWO scilab_module() calls: the MAIN dylib (GUI+BUILD_HELP
# branch: the 2 GIWS JNI wrappers + the sci_buildDoc gateway) and the
# `-disable` stub (the same nogui.cpp automake builds unconditionally so the
# stub library is never empty).
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS. The -disable stub
# appears in NO aggregate link list on ANY platform (install-only; the
# manifest's `(none)` class) — the helper's CLASS keyword is omitted and this
# comment is the record (Batch B precedent).
#
# THE MODULE EDGE: libscihelptools_la_LIBADD = algo + libscicommons.la and
# NOTHING else — no XML, no JDK link (the JNI code only needs jni.h at compile
# time; all JNI symbols arrive through the JNIEnv pointer, so unlike the
# jvm/types/external_objects_java modules there is NO -ljli). MODULE_DEPS
# scicommons records the sibling at its install_name and orders the build.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscihelptools.2027.dylib          exports 31; deps
#       /usr/local/lib/scilab/libscicommons.2027.dylib + libc++ + libSystem
#   libscihelptools-disable.2027.dylib  exports 3; deps libc++ + libSystem
#       (CXX stub -> C++ driver link, unlike the pure-C Batch-B stubs)
#
# INCLUDES: la_CPPFLAGS = $(JAVA_JNI_INCLUDE) + src/jni + includes + ast +
# localization + jvm/includes + output_stream + api_scilab + commons/src/jni +
# dynamic_link + string (+ AM_CPPFLAGS); the stub's CPPFLAGS is defined = the
# main's, so both calls share one EXTRA_INCLUDES set (only the dirs beyond the
# helper base are spelled). SCILAB_JAVA_HOME comes from the toolchain (ScilabToolchain.cmake, parsed
# from config.status S["JAVA_HOME"] — the configured jdk-25, NOT
# /usr/libexec/java_home's jdk-26).

scilab_module(helptools
  ALGO_SOURCES
    src/jni/BuildDocObject.cpp
    src/jni/SciDocMain.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_buildDoc.cpp
  LANG CXX
  MODULE_DEPS scicommons
  EXTRA_INCLUDES
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS GUI_LIBS
  SYMBOLS 31)

scilab_module(helptools-disable
  GATEWAY_SOURCES
    sci_gateway/nogui/nogui.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  SYMBOLS 3)
