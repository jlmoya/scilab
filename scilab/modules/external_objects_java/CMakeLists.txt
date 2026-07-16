# CMake build for the `external_objects_java` module — Batch E (inter-module
# edge + JDK) on the shared scilab_module() helper (cmake/ScilabModule.cmake);
# built from the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE:
# this file is invisible to automake; the autotools original is always
# recoverable with `make -C modules/external_objects_java`.
#
# LINKING CLASS (modules/Makefile.am): GUI_LIBS.
#
# MODULE EDGES + THE JDK DEP: libsciexternal_objects_java_la_LIBADD = algo +
# libscicommons.la + libscijvm.la (+ X_LIBS/X_EXTRA_LIBS, configured empty on
# macOS). Exactly like types-java: xml2/z/icucore and @rpath/libjli.dylib ride
# in transitively through libscijvm.la's dependency_libs, whose -Wl,-rpath is
# DROPPED by libtool — so the baseline records `@rpath/libjli.dylib` but
# carries NO JDK LC_RPATH (only the standard /usr/lib + gcc/current pair;
# verified by `otool -l`). Reproduced: MODULE_DEPS scicommons scijvm +
# SYSTEM_LIBS raw `-L` + plain `jli`, and NO post-call rpath.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libsciexternal_objects_java.2027.dylib  exports 318; deps
#       /usr/local/lib/scilab/libscicommons.2027.dylib
#       /usr/local/lib/scilab/libscijvm.2027.dylib
#       /usr/lib/lib{xml2.2,z.1,icucore.A}.dylib
#       @rpath/libjli.dylib + libc++ + libSystem
#
# Sources verbatim from Makefile.am: algo = the 11 GIWS/JNI C++ sources;
# gateway = the 26 sci_j* gateways (automake lists them under the misnamed
# GATEWAY_C_SOURCES — they are all .cpp; GATEWAY_CPP_SOURCES is empty). All
# C++ -> LANG CXX. EXTRA_INCLUDES = the la_CPPFLAGS dirs beyond the helper
# base. SCILAB_JAVA_HOME comes from the driver (config.status
# S["JAVA_HOME"] — the configured jdk-25).

scilab_module(external_objects_java
  ALGO_SOURCES
    src/cpp/ScilabJavaEnvironment.cpp
    src/cpp/NoMoreScilabMemoryException.cpp
    src/cpp/JavaOptionsSetter.cpp
    src/cpp/ScilabJavaEnvironmentWrapper.cpp
    src/jni/ScilabJavaArray.cpp
    src/jni/ScilabJavaCompiler.cpp
    src/jni/ScilabJarCreator.cpp
    src/jni/ScilabJavaClass.cpp
    src/jni/ScilabJavaObject.cpp
    src/jni/ScilabClassLoader.cpp
    src/jni/ScilabOperations.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_jimport.cpp
    sci_gateway/cpp/sci_jgetclassname.cpp
    sci_gateway/cpp/sci_jwrap.cpp
    sci_gateway/cpp/sci_jgetmethods.cpp
    sci_gateway/cpp/sci_jgetfields.cpp
    sci_gateway/cpp/sci_jgetfield.cpp
    sci_gateway/cpp/sci_jcompile.cpp
    sci_gateway/cpp/sci_jcreatejar.cpp
    sci_gateway/cpp/sci_jnewInstance.cpp
    sci_gateway/cpp/sci_jremove.cpp
    sci_gateway/cpp/sci_jexists.cpp
    sci_gateway/cpp/sci_junwrap.cpp
    sci_gateway/cpp/sci_jautoUnwrap.cpp
    sci_gateway/cpp/sci_junwraprem.cpp
    sci_gateway/cpp/sci_jenableTrace.cpp
    sci_gateway/cpp/sci_jdisableTrace.cpp
    sci_gateway/cpp/sci_jdeff.cpp
    sci_gateway/cpp/sci_jinvoke.cpp
    sci_gateway/cpp/sci_jarray.cpp
    sci_gateway/cpp/sci_jcast.cpp
    sci_gateway/cpp/sci_jallowClassReloading.cpp
    sci_gateway/cpp/sci_jwrapinfloat.cpp
    sci_gateway/cpp/sci_jsetfield.cpp
    sci_gateway/cpp/sci_jautoTranspose.cpp
    sci_gateway/cpp/sci_jgetinfo.cpp
    sci_gateway/cpp/sci_jinvoke_db.cpp
  LANG CXX
  MODULE_DEPS scicommons scijvm
  SYSTEM_LIBS xml2 z icucore "-L${SCILAB_JAVA_HOME}/lib" jli
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/core/src/c
    ${SCILAB_SOURCE_DIR}/modules/external_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 318)
