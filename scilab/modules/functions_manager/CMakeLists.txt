# CMake build for the `functions_manager` module — Batch A (simple leaf) on
# the shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools functions_manager is always recoverable
# with `make -C modules/functions_manager`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscifunctions_manager.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscifunctions_manager.2027.dylib
#   exports:       exactly 93 symbols (FuncManager, ModulesManagement,
#                  dynamic-module bookkeeping)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources: no noinst algo lib — the 3 FUNCTION_MANAGER_CPP_SOURCES sit on the
# pkglib directly, verbatim from Makefile.am. (testFunctionManager is a
# check_PROGRAM, not part of the dylib.)
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscifunctions_manager_la_CPPFLAGS: console, elementary_functions, fileio,
# jvm, signal_processing, string, threads — plus XML_FLAGS' SDK usr/include
# (the SDK ships the libxml/ headers directly under usr/include).
# MACHINE-CONFIG-SPECIFIC: an Xcode.app SDK path — a CLT-only machine differs;
# the future de-autotools driver derives it from `xcrun --show-sdk-path`.

scilab_module(functions_manager
  GATEWAY_SOURCES
    src/cpp/funcmanager.cpp
    src/cpp/dynamic_modules.cpp
    src/cpp/functions_manager.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/signal_processing/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS
  SYMBOLS 93)
