# CMake build for the `preferences` module — Batch C (external deps, variant
# pair) on the shared scilab_module() helper (cmake/ScilabModule.cmake); built
# from the top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this
# file is invisible to automake; the autotools preferences is always
# recoverable with `make -C modules/preferences`.
#
# VARIANT PAIR: main (GUI, XML-backed) + `-cli` stub. automake folds the SAME
# libscipreferences-algo.la object (getScilabPreference.o, compiled with the
# MAIN's CPPFLAGS) into BOTH dylibs; the -cli call reproduces that by consuming
# the main call's OBJECT lib via $<TARGET_OBJECTS:scipreferences-algo> instead
# of recompiling — object-for-object what libtool linked. Neither dylib links
# the other (no libsci edge in otool -L).
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS; -cli = NO_GUI_LIBS
# (empty on macOS -> no CLASS keyword).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscipreferences.2027.dylib      exports 24; deps /usr/lib/libxml2.2.dylib
#                                     + libz.1 + libicucore.A + libc++ + libSystem
#   libscipreferences-cli.2027.dylib  exports 12; deps libc++ + libSystem only
#
# EXTERNAL DEPS (main): LIBADD = algo + $(X_LIBS) $(X_EXTRA_LIBS) $(XML_LIBS);
# configured X_* are EMPTY, XML_LIBS = -lxml2 -lz -lpthread -licucore -lm ->
# SYSTEM_LIBS xml2 z icucore (pthread/m fold into libSystem), the coverage
# pattern: plain names resolved in the SDK sysroot -> /usr/lib deps. The -cli
# LIBADD is the algo only -> no SYSTEM_LIBS.
#
# Sources verbatim from Makefile.am (main's GATEWAY_C_SOURCES ref is
# undefined/empty). Main EXTRA_INCLUDES: src/jni (module-local; the helper's
# default set has src/c + src/cpp only) + commons/src/jni + JDK JNI dirs +
# XML_FLAGS' SDK usr/include + string/fileio/jvm/threads.

scilab_module(preferences
  ALGO_SOURCES
    src/c/getScilabPreference.c
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_addModulePreferences.cpp
    sci_gateway/cpp/sci_removeModulePreferences.cpp
    sci_gateway/cpp/sci_preferences.cpp
    sci_gateway/cpp/preferences_gw.cpp
    src/jni/ScilabPreferences.cpp
  LANG C CXX
  # REPRODUCES the automake _CFLAGS footgun (Makefile.am:120 sets
  # libscipreferences_algo_la_CFLAGS = $(libscipreferences_la_CFLAGS), and
  # libscipreferences_la_CFLAGS is NEVER assigned anywhere in this file --
  # expands empty, which REPLACES $(AM_CFLAGS) wholesale) -- getScilabPreference.c
  # (the algo object's only TU) compiles with none of SCI_CFLAGS, only $(CC)'s
  # own -std=gnu23. The GATEWAY_SOURCES are all C++ (untouched; no _CXXFLAGS
  # override exists) and preferences-cli below reuses this same compiled
  # object via TARGET_OBJECTS rather than recompiling it. Reproduce, do not
  # improve: restoring -O2 -fwrapv here is a separate stage.
  # C_FLAGS_OVERRIDE removed 2026-07-23 (register B10): un-footgunned — this module's
  # C TUs now get the full SCI_CFLAGS default (-O2 -fwrapv -g). The comment(s) above
  # describe the automake _CFLAGS footgun this used to reproduce.
  SYSTEM_LIBS xml2 z icucore
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 24)

# --- the CLI stub (nopreferences + the shared gateway entry + the SHARED algo
# object — see the header comment) ---
scilab_module(preferences-cli
  GATEWAY_SOURCES
    src/nopreferences/nopreferences.cpp
    sci_gateway/cpp/preferences_gw.cpp
    $<TARGET_OBJECTS:scipreferences-algo>
  LANG C CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
  SYMBOLS 12)
