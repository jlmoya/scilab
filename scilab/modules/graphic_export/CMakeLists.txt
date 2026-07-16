# CMake build for the `graphic_export` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools graphic_export is always recoverable with
# `make -C modules/graphic_export`.
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
#   libscigraphic_export.2027.dylib          exports 40;  deps libc++ + libSystem
#   libscigraphic_export-disable.2027.dylib  exports 1;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: algo = the 4 export cpp (exportToFile/xs2file + GIWS FileExporter/
# Driver); gateway = 10 sci_xs2* C gateways + 3 cpp gateways. Stub =
# nographic_export.c (pure C -> C-driver link -> libSystem-only deps).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(graphic_export
  ALGO_SOURCES
    src/cpp/exportToFile.cpp
    src/cpp/xs2file.cpp
    src/jni/FileExporter.cpp
    src/jni/Driver.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_xs2bmp.c
    sci_gateway/c/sci_xs2gif.c
    sci_gateway/c/sci_xs2ppm.c
    sci_gateway/c/sci_xs2ps.c
    sci_gateway/c/sci_xs2svg.c
    sci_gateway/c/sci_xs2jpg.c
    sci_gateway/c/sci_xs2eps.c
    sci_gateway/c/sci_xs2pdf.c
    sci_gateway/c/sci_xs2png.c
    sci_gateway/c/sci_xs2emf.c
    sci_gateway/cpp/sci_driver.cpp
    sci_gateway/cpp/sci_xinit.cpp
    sci_gateway/cpp/sci_xend.cpp
  LANG CXX C
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/renderer/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/gui/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 40)

scilab_module(graphic_export-disable
  GATEWAY_SOURCES
    src/nographic_export/nographic_export.c
  LANG C
  SYMBOLS 1)
