# CMake build for the `action_binding` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools action_binding is always recoverable with
# `make -C modules/action_binding`.
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
#   libsciaction_binding.2027.dylib          exports 19;  deps libc++ + libSystem
#   libsciaction_binding-disable.2027.dylib  exports 0;  deps libc++ + libSystem
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: algo = ACTION_BINDING_C/CPP_SOURCES + the SWIG wrapper (Signal.hxx,
# a header listed in the automake _SOURCES for dist only, is omitted — not a
# compiled TU); gateway = the 2 GATEWAY_CPP_SOURCES. Stub =
# libsciaction_binding_disable_la_SOURCES (noaction_binding.cpp, C++ -> the
# stub links libc++ like the baseline). The stub exports NOTHING (SYMBOLS 0:
# nm -gU on the baseline prints 0 lines — it only carries local no-op code).
# Variant EXTRA_INCLUDES differ from the main: the stub's CPPFLAGS add
# libs/dynamiclibrary/includes (and none of the JNI set).

scilab_module(action_binding
  ALGO_SOURCES
    src/c/InterpreterManagement.c
    src/jni/Signal.cpp
    src/cpp/action_binding_GiwsExports.cpp
    src/jni/InterpreterManagement_wrap.c
  GATEWAY_SOURCES
    sci_gateway/cpp/action_binding_gw.cpp
    sci_gateway/cpp/sci_notify.cpp
  LANG CXX C
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS GUI_LIBS
  SYMBOLS 19)

scilab_module(action_binding-disable
  GATEWAY_SOURCES
    src/noaction_binding/noaction_binding.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/libs/dynamiclibrary/includes
  SYMBOLS 0)
