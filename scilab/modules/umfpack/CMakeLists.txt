# CMake build for the `umfpack` module — Batch C on the shared scilab_module()
# helper (cmake/ScilabModule.cmake); built from the top-level scilab/CMakeLists.txt
# driver. HYBRID COEXISTENCE: this file is invisible to automake; the autotools
# umfpack is always recoverable with `make -C modules/umfpack`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciumfpack.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libsciumfpack.2027.dylib
#   exports:       exactly 43 symbols
#   deps:          /usr/lib/libSystem.B.dylib ONLY — pure C (LANG C -> C-driver
#                  link, no libc++) and NO suite-sparse lib (see below).
#
# NO-LINK EXTERNAL DEP (UMFPACK=on): the suite-sparse HEADERS are required at
# compile time (UMFPACK_CFLAGS = -I/opt/homebrew/opt/suite-sparse/include/
# suitesparse -> EXTRA_INCLUDES), but the baseline records NO libumfpack dep:
# Makefile.am's LIBADD appends $(UMFPACK_LIB) — a variable configure never
# defines (the configured var is UMFPACK_LIBS, with an S) — so the autotools
# link carried no suite-sparse lib and the 18 undefined umfpack_* symbols
# resolve at dlopen time via -undefined dynamic_lookup (against the process's
# already-loaded copy). Reproduced by simply linking nothing.
#
# Sources verbatim from Makefile.am (`if UMFPACK` branch): algo = taucs_scilab.c
# + common_umfpack.c + with_umfpack.c; gateway = the 12 sci_* C files.

scilab_module(umfpack
  ALGO_SOURCES
    src/c/taucs_scilab.c
    src/c/common_umfpack.c
    src/c/with_umfpack.c
  GATEWAY_SOURCES
    sci_gateway/c/sci_res_with_prec.c
    sci_gateway/c/sci_taucs_chdel.c
    sci_gateway/c/sci_taucs_chfact.c
    sci_gateway/c/sci_taucs_chget.c
    sci_gateway/c/sci_taucs_chinfo.c
    sci_gateway/c/sci_taucs_chsolve.c
    sci_gateway/c/sci_umf_ludel.c
    sci_gateway/c/sci_umf_lufact.c
    sci_gateway/c/sci_umf_luget.c
    sci_gateway/c/sci_umf_luinfo.c
    sci_gateway/c/sci_umf_lusolve.c
    sci_gateway/c/sci_umfpack.c
  LANG C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/mexlib/includes
    /opt/homebrew/opt/suite-sparse/include/suitesparse
  CLASS DYNAMIC_LOAD
  SYMBOLS 43)
