# CMake build for the `fftw` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — fftw ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scifftw-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's (FFTW conditional ON):
# libscifftw-algo (5 TUs) + libscifftw (11 gateway TUs).
#
# EXTRA_INCLUDES (libscifftw_la_CPPFLAGS): threads, console, fileio,
# string, elementary_functions. FFTW3_CFLAGS is EMPTY on this config
# (fftw3.h resolves via the Homebrew base).

scilab_object_module(fftw
  SOURCES
    # libscifftw-algo.la (5 TUs)
    src/c/callfftw.c
    src/c/fftwlibname.c
    src/c/fftw_utilities.c
    src/c/with_fftw.c
    src/cpp/fftw_common.cpp
    # libscifftw.la (11 TUs)
    sci_gateway/c/sci_fftwlibraryisloaded.c
    sci_gateway/c/sci_disposefftwlibrary.c
    sci_gateway/c/sci_fftw_flags.c
    sci_gateway/c/sci_loadfftwlibrary.c
    sci_gateway/c/sci_get_fftw_wisdom.c
    sci_gateway/c/sci_set_fftw_wisdom.c
    sci_gateway/c/sci_fftw_forget_wisdom.c
    sci_gateway/cpp/fftw_gw.cpp
    sci_gateway/cpp/sci_fftw.cpp
    sci_gateway/cpp/sci_dst.cpp
    sci_gateway/cpp/sci_dct.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes)
