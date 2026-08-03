# CMake build for the `arnoldi` module — Batch C (external deps) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools arnoldi is always recoverable with
# `make -C modules/arnoldi`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciarnoldi.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libsciarnoldi.2027.dylib
#   exports:       exactly 8 symbols (the eigs C entry points + 6 sci_* gateways)
#   deps:          /opt/homebrew/opt/openblas/lib/libopenblas.0.dylib
#                  /opt/homebrew/opt/arpack/lib/libarpack.2.dylib
#                  /usr/lib/libSystem.B.dylib   — NO libc++ (pure-C module,
#                  C-driver link; LANG C keeps it that way).
#
# EXTERNAL DEPS: Makefile.am LIBADD = $(BLAS_LIBS) $(LAPACK_LIBS) $(ARPACK_LIBS);
# configured: BLAS_LIBS = -L/opt/homebrew/opt/openblas/lib -lopenblas,
# ARPACK_LIBS = -larpack -L/opt/homebrew/opt/arpack/lib (LAPACK_LIBS carries the
# openblas -L only). Pinned here as ABSOLUTE keg paths (the libomp pattern): the
# recorded dep is each dylib's install_name — exactly the /opt/homebrew/opt/...
# paths the baseline recorded — with no dependence on -L search order.
#
# Sources verbatim from Makefile.am (GATEWAY_C_SOURCES lists a stray
# extensionless `sci_gateway/c/sci_zneupd` entry that automake never compiles —
# omitted). EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES: elementary_functions.

scilab_module(arnoldi
  ALGO_SOURCES
    src/c/eigs.c
    src/c/eigs_dependencies.c
  GATEWAY_SOURCES
    sci_gateway/c/sci_dseupd.c
    sci_gateway/c/sci_zneupd.c
    sci_gateway/c/sci_dnaupd.c
    sci_gateway/c/sci_dneupd.c
    sci_gateway/c/sci_dsaupd.c
    sci_gateway/c/sci_znaupd.c
  LANG C
  SYSTEM_LIBS
    ${SCILAB_BLAS_LIBFILE}                                  # see SCILAB_BLAS_BACKEND
    /opt/homebrew/opt/arpack/lib/libarpack.dylib
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
  CLASS ENGINE_LIBS
  SYMBOLS 8)
