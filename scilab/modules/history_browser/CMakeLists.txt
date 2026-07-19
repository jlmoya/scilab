# CMake build for the `history_browser` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools history_browser is always recoverable with
# `make -C modules/history_browser`.
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
#   libscihistory_browser.2027.dylib          exports 25;  deps libc++ + libSystem
#   libscihistory_browser-disable.2027.dylib  exports 7;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: NO algo lib — the 3 files (sci_browsehistory.c gateway + GIWS
# CommandHistory.cpp + CommandHistory_Wrap.cpp) sit on the pkglib directly ->
# all in GATEWAY_SOURCES. Stub = CommandHistory_Wrap_Fake.c (the only Batch-B
# stub NOT named no<module>.*; pure C -> libSystem-only deps).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(history_browser
  GATEWAY_SOURCES
    sci_gateway/c/sci_browsehistory.c
    src/jni/CommandHistory.cpp
    src/cpp/CommandHistory_Wrap.cpp
  LANG CXX C
  # REPRODUCES the automake _CFLAGS footgun (Makefile.am:50 sets
  # libscihistory_browser_la_CFLAGS to empty, which REPLACES $(AM_CFLAGS)
  # wholesale) -- sci_browsehistory.c (this call's only C TU) compiles with
  # none of SCI_CFLAGS, only $(CC)'s own -std=gnu23. The CommandHistory.cpp/
  # CommandHistory_Wrap.cpp C++ sources are unaffected (no _CXXFLAGS override
  # exists) and the history_browser-disable stub below is a SEPARATE library
  # with no _CFLAGS of its own -- verified against the baseline: only
  # sci_browsehistory.c carries an opt/wrapv/std override, not
  # CommandHistory_Wrap_Fake.c. Reproduce, do not improve: restoring
  # -O2 -fwrapv here is a separate stage.
  C_FLAGS_OVERRIDE -std=gnu23
  EXTRA_INCLUDES
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
  CLASS GUI_LIBS
  SYMBOLS 25)

scilab_module(history_browser-disable
  GATEWAY_SOURCES
    src/nohistory_browser/CommandHistory_Wrap_Fake.c
  LANG C
  SYMBOLS 7)
