# CMake build for the `functions` module — Batch A (simple leaf) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools functions is always recoverable with
# `make -C modules/functions`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscifunctions.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscifunctions.2027.dylib
#   exports:       exactly 12 symbols (the 8 sci_* gateways + gw helpers)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources: gateway-only module — no noinst algo lib; the 8
# GATEWAY_CPP_SOURCES verbatim from Makefile.am.
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscifunctions_la_CPPFLAGS: functions_manager, string, fileio, console,
# threads, elementary_functions.

scilab_module(functions
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_exec.cpp
    sci_gateway/cpp/sci_functionlist.cpp
    sci_gateway/cpp/sci_whereis.cpp
    sci_gateway/cpp/sci_execstr.cpp
    sci_gateway/cpp/sci_macrovar.cpp
    sci_gateway/cpp/sci_libraryinfo.cpp
    sci_gateway/cpp/sci_librarieslist.cpp
    sci_gateway/cpp/sci_idempotence.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
  CLASS ENGINE_LIBS
  SYMBOLS 12)
