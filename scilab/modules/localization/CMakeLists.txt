# CMake build for the `localization` module — Batch C (external deps) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools localization is always recoverable with
# `make -C modules/localization`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscilocalization.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscilocalization.2027.dylib
#   exports:       exactly 33 symbols
#   deps:          /System/Library/Frameworks/Cocoa.framework/Versions/A/Cocoa
#                  /System/Library/Frameworks/CoreFoundation.framework/Versions/A/CoreFoundation
#                  /opt/homebrew/opt/gettext/lib/libintl.8.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS (Makefile.am, `if IS_MACOSX`): LDFLAGS += -framework Cocoa;
# LIBADD += -lintl. FRAMEWORKS Cocoa reproduces the framework link; the
# CoreFoundation load command is NOT spelled anywhere — ld records the
# sub-framework where getLocaleInfo_Apple.c's CF* symbols actually live when it
# resolves them through Cocoa's reexport chain (verified empirically: a minimal
# CFLocaleCopyCurrent dylib linked with only `-framework Cocoa` records both
# Cocoa AND CoreFoundation) — identical mechanism, identical dep set. -lintl
# resolved via the global LDFLAGS' -L/opt/homebrew/lib in autotools; pinned
# here as the gettext keg's absolute path (the libomp pattern) — the recorded
# dep is its install_name, the exact baseline path.
#
# Sources verbatim from Makefile.am with both conditionals ON in the baseline
# (NEED_JAVA -> the 3 SWIG _wrap.c; IS_MACOSX -> getLocaleInfo_Apple.c).
# EXTRA_INCLUDES: functions_manager, string, fileio + the JDK JNI dirs
# (JAVA_JNI_INCLUDE). The `-framework Cocoa` the autotools build also put in
# CFLAGS is a link-only flag clang ignores at -c (unused-argument) — not
# transcribed.

scilab_module(localization
  ALGO_SOURCES
    src/c/InitializeLocalization.c
    src/c/setgetlanguage.c
    src/c/getLcidString.c
    src/c/charEncoding.c
    src/c/setdefaultlanguage.c
    src/jni/Messages_wrap.c
    src/jni/LocaleToLCID_wrap.c
    src/jni/setdefaultlanguage_wrap.c
    src/c/getLocaleInfo_Apple.c
    src/cpp/localization.cpp
    src/cpp/UTF8.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_addlocalizationdomain.c
    sci_gateway/c/sci_gettext.c
    sci_gateway/cpp/localization_gw.cpp
    sci_gateway/cpp/sci_setdefaultlanguage.cpp
    sci_gateway/cpp/sci_getdefaultlanguage.cpp
    sci_gateway/cpp/sci_setlanguage.cpp
    sci_gateway/cpp/sci_getlanguage.cpp
  LANG C CXX
  FRAMEWORKS Cocoa
  SYSTEM_LIBS /opt/homebrew/opt/gettext/lib/libintl.dylib
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS ENGINE_LIBS
  SYMBOLS 33)
