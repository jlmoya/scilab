# CMake build for the `scinotes` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools scinotes is always recoverable with
# `make -C modules/scinotes`.
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
#   libsciscinotes.2027.dylib          exports 26;  deps libc++ + libSystem
#   libsciscinotes-disable.2027.dylib  exports 1;  deps libc++ + libSystem
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: algo = ScilabKeywords.c + its SWIG wrap + GIWS SciNotes.cpp +
# callscinotes.cpp; gateway = the 2 cpp gateways. Stub = noscinotes.c +
# noscinotes_gw.cpp (TWO files, C + C++ -> the stub links libc++ like the
# baseline).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(scinotes
  ALGO_SOURCES
    src/c/ScilabKeywords.c
    src/jni/ScilabKeywords_wrap.c
    src/jni/SciNotes.cpp
    src/cpp/callscinotes.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_scinotes.cpp
    sci_gateway/cpp/sci_closeSciNotesFromScilab.cpp
  LANG CXX C
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/functions/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 26)

scilab_module(scinotes-disable
  GATEWAY_SOURCES
    src/noscinotes/noscinotes.c
    src/noscinotes/noscinotes_gw.cpp
  LANG CXX C
  SYMBOLS 1)
