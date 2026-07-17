# CMake build for the `core` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — core ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scicore-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscicore-algo (57
# TUs: CORE_C + CORE_CPP + HASH_CPP + CORE_FORTRAN) + libscicore (68 gateway
# C/C++ TUs).
#
# EXTRA_INCLUDES (libscicore_la_CPPFLAGS): the module-local src/cpp/hash,
# then the widest inter-module set of any fold-in (core historically
# "linked almost the whole world"), then HDF5_CFLAGS' Homebrew hdf5 and
# XML_FLAGS' SDK usr/include (libxml2 headers; LIBXSLT_CFLAGS is empty on
# this config).

scilab_object_module(core
  SOURCES
    # libscicore-algo.la (57 TUs)
    src/c/csignal.c
    src/c/resizesignal.c
    src/c/getmodules.c
    src/c/getmemory.c
    src/c/isanan.c
    src/c/returnanan.c
    src/c/loadversion.c
    src/c/GetXmlFileEncoding.c
    src/c/islittleendian.c
    src/c/getcommandlineargs.c
    src/c/commandwords.c
    src/c/freeArrayOfString.c
    src/c/getstaticdebuginfo.c
    src/c/getdynamicdebuginfo.c
    src/c/setPrecisionFPU.c
    src/c/getos.c
    src/c/transposeMatrix.c
    src/c/getversion.c
    src/c/backtrace.c
    src/c/signal_mgmt.c
    src/c/suspendProcess.c
    src/c/saveCWDInPreferences.c
    src/c/getmaxMALLOC.c
    src/c/sci_mem_alloc.c
    src/c/InitializePreferences.c
    src/c/hasHandleType.c
    src/c/hasUIControlStyle.c
    src/cpp/demangle.cpp
    src/cpp/backtrace_print.cpp
    src/cpp/storeCommand.cpp
    src/cpp/banner.cpp
    src/cpp/with_module.cpp
    src/cpp/runner.cpp
    src/cpp/getvariablesname.cpp
    src/cpp/getmacrosname.cpp
    src/cpp/getfunctionsname.cpp
    src/cpp/getdeprecated.cpp
    src/cpp/InitScilab.cpp
    src/cpp/md5_file.cpp
    src/cpp/parsecommand.cpp
    src/cpp/pause.cpp
    src/cpp/setformat.cpp
    src/cpp/inlinehelp.cpp
    src/cpp/hash/md5.cpp
    src/cpp/hash/crc32.cpp
    src/cpp/hash/sha1.cpp
    src/cpp/hash/sha256.cpp
    src/cpp/hash/sha3.cpp
    src/fortran/clunit.f
    src/fortran/folhp.f
    src/fortran/xerbla.f
    src/fortran/expsum.f
    src/fortran/readinter.f
    src/fortran/rewindinter.f
    src/fortran/backspaceinter.f
    src/fortran/write_inter.f
    src/fortran/read_inter.f
    # libscicore.la (68 TUs)
    sci_gateway/c/sci_getdebuginfo.c
    sci_gateway/cpp/core_gw.cpp
    sci_gateway/cpp/sci_abort.cpp
    sci_gateway/cpp/sci_analyzeroptions.cpp
    sci_gateway/cpp/sci_argn.cpp
    sci_gateway/cpp/sci_banner.cpp
    sci_gateway/cpp/sci_cell.cpp
    sci_gateway/cpp/sci_checkNamedArguments.cpp
    sci_gateway/cpp/sci_clear.cpp
    sci_gateway/cpp/sci_clearfun.cpp
    sci_gateway/cpp/sci_clearglobal.cpp
    sci_gateway/cpp/sci_debug.cpp
    sci_gateway/cpp/sci_enumeration.cpp
    sci_gateway/cpp/sci_errclear.cpp
    sci_gateway/cpp/sci_error.cpp
    sci_gateway/cpp/sci_exists.cpp
    sci_gateway/cpp/sci_exit.cpp
    sci_gateway/cpp/sci_isatty.cpp
    sci_gateway/cpp/sci_fieldnames.cpp
    sci_gateway/cpp/sci_format.cpp
    sci_gateway/cpp/sci_funcprot.cpp
    sci_gateway/cpp/sci_funptr.cpp
    sci_gateway/cpp/sci_getmd5.cpp
    sci_gateway/cpp/sci_getmemory.cpp
    sci_gateway/cpp/sci_getmodules.cpp
    sci_gateway/cpp/sci_getos.cpp
    sci_gateway/cpp/sci_getscilabmode.cpp
    sci_gateway/cpp/sci_getversion.cpp
    sci_gateway/cpp/sci_global.cpp
    sci_gateway/cpp/sci_hash.cpp
    sci_gateway/cpp/sci_ieee.cpp
    sci_gateway/cpp/sci_insert.cpp
    sci_gateway/cpp/sci_intppty.cpp
    sci_gateway/cpp/sci_isa.cpp
    sci_gateway/cpp/sci_isfield.cpp
    sci_gateway/cpp/sci_isglobal.cpp
    sci_gateway/cpp/sci_lasterror.cpp
    sci_gateway/cpp/sci_list.cpp
    sci_gateway/cpp/sci_macr2tree.cpp
    sci_gateway/cpp/sci_makecell.cpp
    sci_gateway/cpp/sci_methods.cpp
    sci_gateway/cpp/sci_mlist.cpp
    sci_gateway/cpp/sci_mode.cpp
    sci_gateway/cpp/sci_newfun.cpp
    sci_gateway/cpp/sci_null.cpp
    sci_gateway/cpp/sci_oldEmptyBehaviour.cpp
    sci_gateway/cpp/sci_pause.cpp
    sci_gateway/cpp/sci_predef.cpp
    sci_gateway/cpp/sci_properties.cpp
    sci_gateway/cpp/sci_quit.cpp
    sci_gateway/cpp/sci_recursionlimit.cpp
    sci_gateway/cpp/sci_sciargs.cpp
    sci_gateway/cpp/sci_show_vtable.cpp
    sci_gateway/cpp/sci_skipArguments.cpp
    sci_gateway/cpp/sci_struct.cpp
    sci_gateway/cpp/sci_tlist.cpp
    sci_gateway/cpp/sci_type.cpp
    sci_gateway/cpp/sci_typename.cpp
    sci_gateway/cpp/sci_typeof.cpp
    sci_gateway/cpp/sci_warning.cpp
    sci_gateway/cpp/sci_what.cpp
    sci_gateway/cpp/sci_where.cpp
    sci_gateway/cpp/sci_who.cpp
    sci_gateway/cpp/sci_getdeprecated.cpp
    sci_gateway/cpp/sci_with_module.cpp
    sci_gateway/cpp/sci_generate_inline_links.cpp
    sci_gateway/cpp/sci_loadToolboxInlineHelp.cpp
    sci_gateway/cpp/sci_percent_help.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/cpp/hash
    ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    ${SCILAB_SOURCE_DIR}/modules/tclsci/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/history_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/preferences/includes
    ${SCILAB_SOURCE_DIR}/modules/external_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/src/c
    ${SCILAB_SOURCE_DIR}/modules/gui/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/hdf5/includes
    ${SCILAB_SOURCE_DIR}/modules/fftw/includes
    ${SCILAB_SOURCE_DIR}/modules/ui_data/includes
    ${SCILAB_SOURCE_DIR}/modules/slint/includes
    ${SCILAB_SOURCE_DIR}/modules/webtools/includes
    /opt/homebrew/opt/hdf5/include
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include)
