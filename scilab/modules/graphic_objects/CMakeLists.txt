# CMake build for the `graphic_objects` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools graphic_objects is always recoverable with
# `make -C modules/graphic_objects`.
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
#   libscigraphic_objects.2027.dylib          exports 738;  deps libc++ + libSystem
#   libscigraphic_objects-disable.2027.dylib  exports 21;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: NO algo lib — all 48 files (5 SWIG wraps + GIWS jni + the C++
# graphic-object/decomposer core) sit on the pkglib directly -> all in
# GATEWAY_SOURCES. Stub = nographic_objects.c (pure C -> libSystem-only).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(graphic_objects
  GATEWAY_SOURCES
    src/jni/DataLoader_wrap.c
    src/jni/ScilabNativeView_wrap.c
    src/jni/PolylineData_wrap.c
    src/jni/SurfaceData_wrap.c
    src/jni/ObjectData_wrap.c
    src/jni/CallGraphicController.cpp
    src/jni/Builder.cpp
    src/jni/CallXmlLoader.cpp
    src/cpp/createGraphicObject.cpp
    src/cpp/getGraphicObjectProperty.cpp
    src/cpp/getConsoleIdentifier.cpp
    src/cpp/setGraphicObjectProperty.cpp
    src/cpp/deleteGraphicObject.cpp
    src/cpp/dataModel.cpp
    src/cpp/Data3D.cpp
    src/cpp/DataLoader.cpp
    src/cpp/NgonData.cpp
    src/cpp/NgonGeneralData.cpp
    src/cpp/NgonGridData.cpp
    src/cpp/NgonGridMatplotData.cpp
    src/cpp/NgonPolylineData.cpp
    src/cpp/MeshData.cpp
    src/cpp/MeshFecData.cpp
    src/cpp/ColorComputer.cpp
    src/cpp/DecompositionUtils.cpp
    src/cpp/Fac3DColorComputer.cpp
    src/cpp/Fac3DDecomposer.cpp
    src/cpp/NgonGridDataDecomposer.cpp
    src/cpp/NgonGridGrayplotDataDecomposer.cpp
    src/cpp/MatPlotDecomposer.cpp
    src/cpp/NgonGridMatplotDataDecomposer.cpp
    src/cpp/Plot3DDecomposer.cpp
    src/cpp/PolylineDecomposer.cpp
    src/cpp/MeshFecDataDecomposer.cpp
    src/cpp/Triangulator.cpp
    src/cpp/ScilabView.cpp
    src/cpp/FigureList.cpp
    src/cpp/HandleManagement.cpp
    src/cpp/AxesModel.cpp
    src/cpp/FigureModel.cpp
    src/cpp/CurrentFigure.cpp
    src/cpp/CurrentObject.cpp
    src/cpp/CurrentSubwin.cpp
    src/cpp/createObjectData.cpp
    src/cpp/pickSurface.cpp
    src/cpp/Texture.cpp
    src/cpp/NormalGenerator.cpp
    src/cpp/DllmainGraphic_objects.cpp
  LANG CXX C
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 738)

scilab_module(graphic_objects-disable
  GATEWAY_SOURCES
    src/nographic_objects/nographic_objects.c
  LANG C
  SYMBOLS 21)
