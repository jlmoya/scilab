# CMake build for the `output_stream` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — output_stream ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scioutput_stream-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscioutput_stream-
# algo (33 TUs: C + CPP + FORTRAN; JNI_SOURCES resolves empty on this config) +
# libscioutput_stream (6 gateway TUs).
#
# EXTRA_INCLUDES (libscioutput_stream_la_CPPFLAGS): string,
# functions_manager, threads, console, fileio.

scilab_object_module(output_stream
  SOURCES
    # libscioutput_stream-algo.la (33 TUs)
    src/c/erro.c
    src/c/cerro.c
    src/c/Scierror.c
    src/c/msgstore.c
    src/c/msgout.c
    src/c/msgs.c
    src/c/msgstxt.c
    src/c/sciprint.c
    src/c/sciprint_full.c
    src/c/error.c
    src/cpp/Diary.cpp
    src/cpp/scilabWrite.cpp
    src/cpp/DiaryList.cpp
    src/cpp/diary_manager.cpp
    src/cpp/getDiaryDate.cpp
    src/cpp/getUniqueFilename.cpp
    src/cpp/scilab_sprintf.cpp
    src/cpp/basout.cpp
    src/cpp/Sciwarning.cpp
    src/fortran/wspdsp.f
    src/fortran/wmdsp.f
    src/fortran/strdsp.f
    src/fortran/lspdsp.f
    src/fortran/fmttyp.f
    src/fortran/fmt.f
    src/fortran/dspdsp.f
    src/fortran/dmrdsp.f
    src/fortran/dmpdsp.f
    src/fortran/dmdspf.f
    src/fortran/dmdsp.f
    src/fortran/dldsp.f
    src/fortran/basouttofile.f
    src/fortran/formatnumber.f
    # libscioutput_stream.la (6 TUs)
    sci_gateway/cpp/sci_diary.cpp
    sci_gateway/cpp/sci_disp.cpp
    sci_gateway/cpp/output_stream_gw.cpp
    sci_gateway/cpp/sci_msprintf.cpp
    sci_gateway/cpp/sci_mprintf.cpp
    sci_gateway/cpp/sci_print.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes)
