# CMake build for the `renderer` module — Batch A (simple leaf) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools renderer is always recoverable with
# `make -C modules/renderer`.
#
# LINKING CLASS (modules/Makefile.am): GUI_LIBS — the first GUI_LIBS module on
# the helper (CLASS extended for it). GUI_LIBS behaves like ENGINE_LIBS: this
# dylib is a DIRECT dyld dependency of the libscilab aggregate AND scilab-bin
# (verified: `otool -L modules/.libs/libscilab.2027.dylib` and `otool -L
# .libs/scilab-bin` both list libscirenderer), so it LOADS AT PROCESS LAUNCH,
# not on demand — renderer has no sci_gateway and is never dlopen'd. GUI_LIBS
# differs from ENGINE_LIBS only in living in the GUI aggregate link list. (This
# is the OPPOSITE of the DYNAMIC_LOAD class, whose dylibs are linked into
# nothing and dlopen'd on demand.)
#   "LEAF" below is about OUTGOING deps only: despite the JNI includes on the
# compile line, the baseline dylib records NO libsci edge and NO external dep
# (Makefile.am has no LIBADD at all; the JVM symbols resolve via -undefined
# dynamic_lookup at static-link time; the Java renderer lives in the jar).
# Being an incoming-load dependency (above) and a leaf on outgoing deps (here)
# are independent facts. The pkglib only builds under `if GUI` — GUI=on in the
# baseline.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscirenderer.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscirenderer.2027.dylib
#   exports:       exactly 58 symbols (GIWS XlFontManager/CallRenderer
#                  wrappers, Java property get/set, font manager)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources: no noinst algo lib — the 6 RENDERER_CXX_SOURCES sit on the pkglib
# directly, verbatim from Makefile.am (GIWS regeneration is off:
# BUILT_SOURCES' giws append is commented out in the configured Makefile; the
# generated src/jni wrappers are committed).
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libscirenderer_la_CPPFLAGS: JAVA_JNI_INCLUDE (the configured JDK's include
# dirs; jni.h for the GIWS wrappers), the module-local src/jni/ (not in the
# helper's module-local set), jvm, commons/src/jni, graphics, graphic_export,
# elementary_functions, gui, string. MACHINE-CONFIG-SPECIFIC: the JDK-25 path
# is configure-detected on this machine; the future de-autotools driver
# derives it (FindJNI).

scilab_module(renderer
  GATEWAY_SOURCES
    src/cpp/GetJavaProperty.cpp
    src/cpp/SetJavaProperty.cpp
    src/cpp/JavaInteraction.cpp
    src/cpp/RendererFontManager.cpp
    src/jni/XlFontManager.cpp
    src/jni/CallRenderer.cpp
  LANG CXX
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/graphic_export/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/gui/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS GUI_LIBS
  SYMBOLS 58)
