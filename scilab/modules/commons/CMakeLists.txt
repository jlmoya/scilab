# CMake build for the `commons` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools commons is always recoverable with
# `make -C modules/commons`.
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
#   libscicommons.2027.dylib          exports 102;  deps libc++ + libSystem
#   libscicommons-disable.2027.dylib  exports 2;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: NO algo lib in this module — the 5 files (2 SWIG wraps + fileutils.c
# + 2 GIWS jni cpp) sit on the pkglib directly -> all in GATEWAY_SOURCES.
# Stub = src/c/fileutils.c ONLY (the stub SHARES a source file with the main
# dylib — each target compiles its own copy, exactly like automake's per-la
# objects). Pure-C stub -> C-driver link -> deps libSystem only (baseline).
# Variant EXTRA_INCLUDES differ from the main: fileio + string only (no JNI).

scilab_module(commons
  GATEWAY_SOURCES
    src/jni/ScilabCommons_wrap.c
    src/jni/CommonFileUtils_wrap.c
    src/c/fileutils.c
    src/jni/FindIconHelper.cpp
    src/jni/GiwsException.cpp
  LANG CXX C
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS GUI_LIBS
  SYMBOLS 102)

scilab_module(commons-disable
  GATEWAY_SOURCES
    src/c/fileutils.c
  LANG C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  SYMBOLS 2)
