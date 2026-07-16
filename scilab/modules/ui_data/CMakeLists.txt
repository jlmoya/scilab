# CMake build for the `ui_data` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools ui_data is always recoverable with
# `make -C modules/ui_data`.
#
# VARIANT PAIR — TWO scilab_module() calls: the MAIN dylib and the `-disable`
# stub (second pkglib dylib, loaded in nogui/disabled mode). Neither links the
# other (otool -L on both baseline dylibs shows NO libsci edge — they are
# mutually exclusive alternates), so no MODULE_DEPS on either call.
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS. The -disable stub is
# NO_GUI_LIBS, which is EMPTY on macOS (`if IS_MACOSX`): the stub is built +
# installed but linked into NO aggregate. The helper's CLASS keyword has no
# NO_GUI_LIBS value (and Batch B adds no helper features), so the stub call
# omits CLASS — this comment is the record.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libsciui_data.2027.dylib          exports 78;  deps libc++ + libSystem
#   libsciui_data-disable.2027.dylib  exports 3;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: algo = ui_data_wrap.c (SWIG) + PutScilabVariable.c + GIWS
# BrowseVar/EditVar/FileBrowser.cpp + BrowseVarManager/FileBrowserChDir.cpp;
# gateway = the 4 cpp gateways. Stub = noui_data.c (pure C -> libSystem-only).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(ui_data
  ALGO_SOURCES
    src/jni/ui_data_wrap.c
    src/c/PutScilabVariable.c
    src/jni/BrowseVar.cpp
    src/jni/EditVar.cpp
    src/jni/FileBrowser.cpp
    src/cpp/BrowseVarManager.cpp
    src/cpp/FileBrowserChDir.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_editvar.cpp
    sci_gateway/cpp/sci_browsevar.cpp
    sci_gateway/cpp/sci_filebrowser.cpp
    sci_gateway/cpp/sci_updatebrowsevar.cpp
  LANG CXX C
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
  CLASS GUI_LIBS
  SYMBOLS 78)

scilab_module(ui_data-disable
  GATEWAY_SOURCES
    src/noui_data/noui_data.c
  LANG C
  SYMBOLS 3)
