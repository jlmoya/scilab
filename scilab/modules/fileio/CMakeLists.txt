# CMake build for the `fileio` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — fileio ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scifileio-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscifileio-algo (50
# TUs: FILEIO_C + FILEIO_CPP + FILEIO_FORTRAN) + libscifileio (50 gateway TUs).
#
# EXTRA_INCLUDES (libscifileio_la_CPPFLAGS): functions_manager, string,
# io, threads, console, preferences, then the external CFLAGS verbatim
# from config.status — HDF5_CFLAGS (Homebrew hdf5), XML_FLAGS (SDK
# usr/include), LIBARCHIVE_CFLAGS (Homebrew openssl include) and
# CURL_CFLAGS (the miniconda include the configure run detected).

scilab_object_module(fileio
  SOURCES
    # libscifileio-algo.la (50 TUs)
    src/c/FileExist.c
    src/c/scanf_functions.c
    src/c/do_xxscanf.c
    src/c/findfiles.c
    src/c/Status2Mode.c
    src/c/getdrives.c
    src/c/deleteafile.c
    src/c/getshortpathname.c
    src/c/getlongpathname.c
    src/c/FindFileExtension.c
    src/c/URIFileToFilename.c
    src/c/createdirectory.c
    src/c/isdir.c
    src/c/isDrive.c
    src/c/removedir.c
    src/c/cluni0.c
    src/c/scicurdir.c
    src/c/fileinfo.c
    src/c/splitpath.c
    src/c/movefile.c
    src/c/basename.c
    src/c/pathconvert.c
    src/c/copyfile_others.c
    src/c/getFullFilename.c
    src/c/os_wfopen.c
    src/c/fscanfMat.c
    src/c/fprintfMat.c
    src/c/createtempfilename.c
    src/c/dlManager.c
    src/c/convert_tools.c
    src/cpp/expandPathVariable.cpp
    src/cpp/filemanager_interface.cpp
    src/cpp/fullpath.cpp
    src/cpp/mopen.cpp
    src/cpp/mclose.cpp
    src/cpp/mput.cpp
    src/cpp/mget.cpp
    src/cpp/mputl.cpp
    src/cpp/mgetl.cpp
    src/cpp/mgetstr.cpp
    src/cpp/filemanager.cpp
    src/cpp/mseek.cpp
    src/cpp/mtell.cpp
    src/cpp/newest.cpp
    src/cpp/scilab_sscanf.cpp
    src/cpp/addfile.cpp
    src/cpp/delfile.cpp
    src/cpp/getfiledesc.cpp
    src/cpp/getfileinfo.cpp
    src/cpp/getfiletype.cpp
    # libscifileio.la (50 TUs)
    sci_gateway/c/sci_fprintfMat.c
    sci_gateway/c/sci_fscanfMat.c
    sci_gateway/c/sci_chdir.c
    sci_gateway/c/sci_pwd.c
    sci_gateway/c/sci_copyfile.c
    sci_gateway/c/sci_tempname.c
    sci_gateway/c/sci_movefile.c
    sci_gateway/cpp/fileio_gw.cpp
    sci_gateway/cpp/sci_mopen.cpp
    sci_gateway/cpp/sci_mclose.cpp
    sci_gateway/cpp/sci_mget.cpp
    sci_gateway/cpp/sci_mgeti.cpp
    sci_gateway/cpp/sci_mput.cpp
    sci_gateway/cpp/sci_mputl.cpp
    sci_gateway/cpp/sci_mgetl.cpp
    sci_gateway/cpp/sci_pathconvert.cpp
    sci_gateway/cpp/sci_isdir.cpp
    sci_gateway/cpp/sci_filesep.cpp
    sci_gateway/cpp/sci_fileparts.cpp
    sci_gateway/cpp/sci_findfiles.cpp
    sci_gateway/cpp/sci_basename.cpp
    sci_gateway/cpp/sci_fileinfo.cpp
    sci_gateway/cpp/sci_isfile.cpp
    sci_gateway/cpp/sci_deletefile.cpp
    sci_gateway/cpp/sci_getshortpathname.cpp
    sci_gateway/cpp/sci_getlongpathname.cpp
    sci_gateway/cpp/sci_fileext.cpp
    sci_gateway/cpp/sci_createdir.cpp
    sci_gateway/cpp/sci_fullpath.cpp
    sci_gateway/cpp/sci_removedir.cpp
    sci_gateway/cpp/sci_meof.cpp
    sci_gateway/cpp/sci_mputstr.cpp
    sci_gateway/cpp/sci_mgetstr.cpp
    sci_gateway/cpp/sci_mseek.cpp
    sci_gateway/cpp/sci_mtell.cpp
    sci_gateway/cpp/sci_newest.cpp
    sci_gateway/cpp/sci_merror.cpp
    sci_gateway/cpp/sci_mclearerr.cpp
    sci_gateway/cpp/sci_get_absolute_file_path.cpp
    sci_gateway/cpp/sci_getrelativefilename.cpp
    sci_gateway/cpp/sci_pathsep.cpp
    sci_gateway/cpp/sci_mfprintf.cpp
    sci_gateway/cpp/sci_mfscanf.cpp
    sci_gateway/cpp/sci_mscanf.cpp
    sci_gateway/cpp/sci_msscanf.cpp
    sci_gateway/cpp/sci_getdrives.cpp
    sci_gateway/cpp/sci_compress.cpp
    sci_gateway/cpp/sci_decompress.cpp
    sci_gateway/cpp/scanf_utils.cpp
    sci_gateway/cpp/archive_utils.cpp
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/preferences/includes
    /opt/homebrew/opt/hdf5/include
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/opt/openssl/include
    /Users/josemoya/miniconda3/include)
