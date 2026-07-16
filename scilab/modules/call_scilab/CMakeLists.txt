# CMake build for the `call_scilab` module — Batch A (simple leaf) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools call_scilab is always recoverable with
# `make -C modules/call_scilab`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscicall_scilab.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscicall_scilab.2027.dylib
#   exports:       exactly 21 symbols (the embedding API: StartScilab,
#                  SendScilabJob*, from{c,java} helpers + the 2 gateways)
#   deps:          /usr/lib/libSystem.B.dylib ONLY — pure C, the C driver
#                  links (no libc++).
#
# Sources: the 4 CALLSCILAB_C_SOURCES (noinst algo lib) + the 2
# GATEWAY_C_SOURCES, verbatim from Makefile.am.
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscicall_scilab_la_CPPFLAGS: action_binding, fileio, graphics,
# graphic_objects, core/src/c, string, console, threads. The module-local
# includes/ and src/c/ are the helper's module-local dirs.

scilab_module(call_scilab
  ALGO_SOURCES
    src/c/call_scilab.c
    src/c/fromc.c
    src/c/fromjava.c
    src/c/SendScilabJobs.c
  GATEWAY_SOURCES
    sci_gateway/c/sci_fromjava.c
    sci_gateway/c/sci_fromc.c
  LANG C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/core/src/c
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
  CLASS ENGINE_LIBS
  SYMBOLS 21)
