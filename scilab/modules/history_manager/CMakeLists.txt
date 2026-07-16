# CMake build for the `history_manager` module — Batch A (simple leaf) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools history_manager is always recoverable
# with `make -C modules/history_manager`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscihistory_manager.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscihistory_manager.2027.dylib
#   exports:       exactly 140 symbols (HistoryFile/HistoryManager/
#                  HistorySearch, the 13 gateways, the SWIG wrapper's
#                  Java_* entry points)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources (noinst algo lib): HISTORY_MANAGER_C_SOURCES
# (getCommentDateSession.c + src/jni/HistoryManagement_wrap.c — the latter is
# am__append_1, active because GUI=on in the baseline config) + the 3
# HISTORY_MANAGER_CPP_SOURCES; gateway: the 13 GATEWAY_CPP_SOURCES — verbatim
# from Makefile.am.
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscihistory_manager_la_CPPFLAGS: JAVA_JNI_INCLUDE (the configured JDK's
# include dirs; jni.h for the SWIG wrapper), the module-local src/jni/ (not in
# the helper's module-local set), fileio, console, history_browser, string.
# MACHINE-CONFIG-SPECIFIC: the JDK-25 path is configure-detected on this
# machine; the future de-autotools driver derives it (FindJNI).

scilab_module(history_manager
  ALGO_SOURCES
    src/c/getCommentDateSession.c
    src/jni/HistoryManagement_wrap.c
    src/cpp/HistoryFile.cpp
    src/cpp/HistoryManager.cpp
    src/cpp/HistorySearch.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/history_manager_gw.cpp
    sci_gateway/cpp/sci_addhistory.cpp
    sci_gateway/cpp/sci_displayhistory.cpp
    sci_gateway/cpp/sci_gethistory.cpp
    sci_gateway/cpp/sci_gethistoryfile.cpp
    sci_gateway/cpp/sci_historysize.cpp
    sci_gateway/cpp/sci_loadhistory.cpp
    sci_gateway/cpp/sci_removelinehistory.cpp
    sci_gateway/cpp/sci_resethistory.cpp
    sci_gateway/cpp/sci_saveconsecutivecommands.cpp
    sci_gateway/cpp/sci_savehistory.cpp
    sci_gateway/cpp/sci_sethistoryfile.cpp
    sci_gateway/cpp/sci_historymanager.cpp
  LANG CXX C
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/history_browser/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS ENGINE_LIBS
  SYMBOLS 140)
