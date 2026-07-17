# CMake build for the `io` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — io ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciio-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libsciio-algo (9 TUs) +
# libsciio (10 gateway TUs).
#
# EXTRA_INCLUDES (libsciio_la_CPPFLAGS): functions_manager,
# elementary_functions, fileio, string, threads, console, hdf5, tclsci,
# jvm, then JAVA_JNI_INCLUDE (the toolchain's SCILAB_JAVA_HOME pair — the
# config.status jdk-25) and XML_FLAGS' SDK usr/include.

scilab_object_module(io
  SOURCES
    # libsciio-algo.la (9 TUs)
    src/c/getpidc.c
    src/c/setenvc.c
    src/c/getenvc.c
    src/c/v2cuniterror.c
    src/cpp/io_gw.cpp
    src/cpp/loadlib.cpp
    src/cpp/readwrite.cpp
    src/cpp/spawncommand.cpp
    src/cpp/spawncommand_tools.cpp
    # libsciio.la (10 TUs)
    sci_gateway/c/sci_getenv.c
    sci_gateway/c/sci_setenv.c
    sci_gateway/cpp/sci_genlib.cpp
    sci_gateway/cpp/sci_file.cpp
    sci_gateway/cpp/sci_getpid.cpp
    sci_gateway/cpp/sci_host.cpp
    sci_gateway/cpp/sci_read.cpp
    sci_gateway/cpp/sci_write.cpp
    sci_gateway/cpp/sci_lib.cpp
    sci_gateway/cpp/sci_getio.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/hdf5/includes
    ${SCILAB_SOURCE_DIR}/modules/tclsci/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include)
