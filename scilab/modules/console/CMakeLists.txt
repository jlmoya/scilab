# CMake build for the `console` module — Batch C (external deps, variant pair)
# on the shared scilab_module() helper (cmake/ScilabModule.cmake); built from
# the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools console is always recoverable with
# `make -C modules/console`.
#
# VARIANT PAIR with an INTRA-module link: unlike the Batch-B `-disable` stubs,
# the console-minimal variant is a REAL library (the whole CLI console) and the
# main dylib LINKS it (Makefile.am: libsciconsole_la_LIBADD =
# libsciconsole-minimal.la) — so minimal is defined FIRST and main names it in
# MODULE_DEPS. Not a Batch-E inter-module edge: both targets live here.
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS; minimal = NO_GUI_LIBS
# (empty on macOS -> no CLASS keyword; built + installed, linked into no
# aggregate).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libsciconsole-minimal.2027.dylib  exports 53; deps /usr/lib/libncurses.5.4.dylib
#                                     + libc++ + libSystem
#   libsciconsole.2027.dylib          exports 36; deps
#                                     /usr/local/lib/scilab/libsciconsole-minimal.2027.dylib
#                                     + /usr/lib/libncurses.5.4.dylib + libc++ + libSystem
#
# EXTERNAL DEP: NCURSES_LIBS = -lcurses (configured Makefile). The SDK's
# libcurses.tbd records install_name /usr/lib/libncurses.5.4.dylib — the
# baseline dep. The MAIN dylib's own LIBADD has no -lcurses; it inherited it
# from the minimal .la's libtool dependency_libs. CMake target links don't
# propagate PRIVATE deps, so main lists `curses` explicitly — same recorded dep.
#
# Sources verbatim from Makefile.am (no algo lib in either target; the main
# la's CONSOLE_GATEWAY_*_SOURCES refs are undefined/empty — confirmed against
# the configured Makefile's am_libsciconsole_la_OBJECTS). initMacOSXEnv.c is
# the IS_MACOSX conditional source (on, per the baseline).

# --- the CLI console (variant; loaded in nogui mode; main links it) ---
scilab_module(console-minimal
  GATEWAY_SOURCES
    src/noconsole/noconsole.c
    sci_gateway/cpp/sci_clc.cpp
    sci_gateway/cpp/sci_tohome.cpp
    sci_gateway/cpp/sci_lines.cpp
    sci_gateway/cpp/sci_prompt.cpp
    sci_gateway/cpp/sci_iswaitingforinput.cpp
    src/c/clc.c
    src/c/tohome.c
    src/c/more.c
    src/c/scilines.c
    src/c/others/gotoxy_nw.c
    src/c/others/clrscr_nw.c
    src/cpp/prompt.cpp
    src/c/getpipeline.c
    src/c/cmdLine/autoCompletionCli.c
    src/c/cmdLine/charactersManagement.c
    src/c/cmdLine/cliDisplayManagement.c
    src/c/cmdLine/cliHistory.c
    src/c/cmdLine/cliPrompt.c
    src/c/cmdLine/getKey.c
    src/c/cmdLine/getNbrLine.c
    src/c/cmdLine/gotoFunctions.c
    src/c/cmdLine/initConsoleMode.c
    src/c/cmdLine/termcapManagement.c
    src/cpp/scilabRead.cpp
    sci_gateway/cpp/console_gw.cpp
  LANG C CXX
  SYSTEM_LIBS curses
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/history_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/completion/includes
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/src/c
    ${SCILAB_SOURCE_DIR}/modules/ui_data/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  SYMBOLS 53)

# --- the GUI console (main) ---
scilab_module(console
  GATEWAY_SOURCES
    src/jni/GuiManagement_wrap.c
    src/jni/DropFiles_wrap.c
    src/jni/AdvCLIManagement_wrap.c
    src/c/GuiManagement.c
    src/c/dropFiles.c
    src/c/others/initMacOSXEnv.c
    src/jni/CallScilabBridge.cpp
    src/cpp/ClearConsole.cpp
    src/cpp/ClearConsolePart.cpp
    src/cpp/ConsolePrintf.cpp
    src/cpp/ConsoleRead.cpp
    src/cpp/ConsoleIsWaitingForInput.cpp
    src/cpp/GetCharWithoutOutput.cpp
    src/cpp/PromptToHome.cpp
    src/cpp/ScilabLinesUpdate.cpp
    src/cpp/SetConsolePrompt.cpp
  LANG C CXX
  SYSTEM_LIBS curses
  MODULE_DEPS sciconsole-minimal
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/history_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/ui_data/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/completion/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 36)
