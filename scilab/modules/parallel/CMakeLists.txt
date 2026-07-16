# CMake build for the `parallel` module — migrated onto the shared scilab_module()
# helper (cmake/ScilabModule.cmake); built from the top-level scilab/CMakeLists.txt
# driver, no longer a standalone project. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools parallel is always recoverable with
# `make -C modules/parallel`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD — dlopen-only;
# no executable or engine lib records libsciparallel as a load-command
# dependency, so the drop-in swap is relink-free.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciparallel.2027.dylib (+ libsciparallel.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libsciparallel.2027.dylib
#   exports:       _gw_parallel _parallelConcurrency _parallelForbidden (exactly these 3)
#   deps:          /usr/lib/libSystem.B.dylib
#                  + /opt/homebrew/opt/libomp/lib/libomp.dylib
#                  (FIND_PACKAGES OpenMP: the helper links OpenMP::OpenMP_C —
#                  the `-fopenmp` compile fact the harness keys on, plus the
#                  libomp dep recorded at its ABSOLUTE Homebrew install_name,
#                  exactly what autotools' -lomp recorded, NOT an @rpath form.
#                  LANG C keeps the C link driver: no libc++.)
#
# Sources: the one noparallel stub (Makefile.am names it GATEWAY_CPP_SOURCES
# but it is a .c file). src/c/DllmainParallel.c and src/c/forkWindows.c are
# Windows-only (the autotools build excludes them too); the real OpenMP
# gateways (sci_gateway/cpp/) are commented out upstream — the shipped
# parallel is the noparallel stub, on every platform.
# EXTRA_INCLUDES: the autotools compile line carries parameters/includes,
# which is not in SCILAB_DEFAULT_INCLUDES.

scilab_module(parallel
  GATEWAY_SOURCES
    src/noparallel/noparallel.c
  LANG C
  FIND_PACKAGES OpenMP
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/parameters/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 3)
