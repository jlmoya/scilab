# CMake build for the `gui` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools gui is always recoverable with
# `make -C modules/gui`.
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
#   libscigui.2027.dylib          exports 515;  deps libc++ + libSystem
#   libscigui-disable.2027.dylib  exports 13;  deps libSystem only (C-driver link)
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# Sources: algo = GUI_C/CPP_SOURCES (uicontrol/uimenu C core + the GIWS/SWIG
# bridge cpp battery); gateway = 16 C + 37 C++ gateways. Stub = nogui.c
# (pure C -> C-driver link -> libSystem-only deps).
# Variant EXTRA_INCLUDES: none — the stub's -I set is inside the helper base.

scilab_module(gui
  ALGO_SOURCES
    src/c/buildMainWindowTitle.c
    src/c/getMainWindowTitle.c
    src/c/setMainWindowTitle.c
    src/c/addmenu.c
    src/c/CreateUimenu.c
    src/c/checkColorRange.c
    src/c/messageboxoptions.c
    src/c/ScilabTermination.c
    src/jni/ScilabTermination_wrap.c
    src/c/getHandleType.c
    src/c/getUIStyle.c
    src/cpp/InitializeGUI.cpp
    src/cpp/InitUIMenu.cpp
    src/jni/CallScilabBridge.cpp
    src/cpp/CreateUIControl.cpp
    src/jni/Jxclick.cpp
    src/jni/SwingView.cpp
    src/cpp/CallJxclick.cpp
    src/jni/Jxgetmouse.cpp
    src/cpp/CallJxgetmouse.cpp
    src/cpp/CallColorChooser.cpp
    src/cpp/CallFontChooser.cpp
    src/cpp/CallMessageBox.cpp
    src/cpp/CallHelpBrowser.cpp
    src/cpp/ContextMenu.cpp
    src/cpp/Widget.cpp
    src/cpp/SetUimenuLabel.cpp
    src/cpp/GetUimenuLabel.cpp
    src/cpp/GetUicontrolStyle.cpp
    src/cpp/SetUicontrolString.cpp
    src/cpp/GetUicontrolString.cpp
    src/cpp/SetUicontrolTooltipString.cpp
    src/cpp/GetUicontrolTooltipString.cpp
    src/cpp/SetUicontrolBackgroundColor.cpp
    src/cpp/GetUicontrolBackgroundColor.cpp
    src/cpp/SetUiobjectForegroundColor.cpp
    src/cpp/GetUiobjectForegroundColor.cpp
    src/cpp/SetUicontrolPosition.cpp
    src/cpp/GetUicontrolPosition.cpp
    src/cpp/SetUicontrolFontName.cpp
    src/cpp/GetUicontrolFontName.cpp
    src/cpp/SetUicontrolFontWeight.cpp
    src/cpp/GetUicontrolFontWeight.cpp
    src/cpp/SetUicontrolFontUnits.cpp
    src/cpp/GetUicontrolFontUnits.cpp
    src/cpp/SetUicontrolFontSize.cpp
    src/cpp/GetUicontrolFontSize.cpp
    src/cpp/SetUicontrolFontAngle.cpp
    src/cpp/GetUicontrolFontAngle.cpp
    src/cpp/SetUiobjectEnable.cpp
    src/cpp/GetUiobjectEnable.cpp
    src/cpp/SetUimenuChecked.cpp
    src/cpp/GetUimenuChecked.cpp
    src/cpp/SetUicontrolMin.cpp
    src/cpp/GetUicontrolMin.cpp
    src/cpp/SetUicontrolMax.cpp
    src/cpp/GetUicontrolMax.cpp
    src/cpp/SetUicontrolValue.cpp
    src/cpp/GetUicontrolValue.cpp
    src/cpp/SetUicontrolListboxTop.cpp
    src/cpp/GetUicontrolListboxTop.cpp
    src/cpp/SetUicontrolUnits.cpp
    src/cpp/GetUicontrolUnits.cpp
    src/cpp/SetUicontrolRelief.cpp
    src/cpp/GetUicontrolRelief.cpp
    src/cpp/SetUicontrolHorizontalAlignment.cpp
    src/cpp/GetUicontrolHorizontalAlignment.cpp
    src/cpp/SetUicontrolVerticalAlignment.cpp
    src/cpp/GetUicontrolVerticalAlignment.cpp
    src/cpp/SetUicontrolSliderStep.cpp
    src/cpp/GetUicontrolSliderStep.cpp
    src/cpp/SetUicontrolSnapToTicks.cpp
    src/cpp/GetUicontrolSnapToTicks.cpp
    src/cpp/SetUicontrolIcon.cpp
    src/cpp/GetUicontrolIcon.cpp
    src/cpp/SetUicontrolDebug.cpp
    src/cpp/GetUicontrolDebug.cpp
    src/cpp/SetConsoleShowHiddenHandles.cpp
    src/cpp/GetConsoleShowHiddenHandles.cpp
    src/cpp/UnitsConversion.cpp
    src/cpp/GetScreenProperty.cpp
    src/cpp/CallClipboard.cpp
    src/cpp/exportUserInterface.cpp
    src/jni/LookAndFeelManager.cpp
    src/jni/Juigetfile.cpp
    src/cpp/CallJuigetfile.cpp
    src/cpp/displaytree.cpp
    src/jni/ScilabDisplayTree.cpp
    src/jni/EditorManager.cpp
    src/jni/StartGED.cpp
    src/jni/StartPlotBrowser.cpp
    src/jni/DatatipManager.cpp
    src/jni/DatatipCreate.cpp
    src/jni/DatatipDelete.cpp
    src/jni/DatatipDrag.cpp
    src/jni/DatatipSetStyle.cpp
    src/jni/DatatipOrientation.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_x_choose.c
    sci_gateway/c/sci_x_choose_modeless.c
    sci_gateway/c/sci_unsetmenu.c
    sci_gateway/c/sci_delmenu.c
    sci_gateway/c/sci_setmenu.c
    sci_gateway/c/sci_toolbar.c
    sci_gateway/c/sci_uicontextmenu.c
    sci_gateway/c/sci_uicontrol.c
    sci_gateway/c/sci_uimenu.c
    sci_gateway/c/sci_uiwait.c
    sci_gateway/c/sci_messagebox.c
    sci_gateway/c/sci_waitbar.c
    sci_gateway/c/sci_progressionbar.c
    sci_gateway/c/sci_getcallbackobject.c
    sci_gateway/c/sci_exportUI.c
    sci_gateway/c/sci_figure.c
    sci_gateway/cpp/sci_printsetupbox.cpp
    sci_gateway/cpp/sci_toprint.cpp
    sci_gateway/cpp/sci_printfigure.cpp
    sci_gateway/cpp/sci_setlookandfeel.cpp
    sci_gateway/cpp/sci_getinstalledlookandfeels.cpp
    sci_gateway/cpp/sci_getlookandfeel.cpp
    sci_gateway/cpp/sci_uigetdir.cpp
    sci_gateway/cpp/sci_uigetfile.cpp
    sci_gateway/cpp/sci_uigetfont.cpp
    sci_gateway/cpp/sci_displaytree.cpp
    sci_gateway/cpp/sci_uiputfile.cpp
    sci_gateway/cpp/sci_about.cpp
    sci_gateway/cpp/sci_raise_window.cpp
    sci_gateway/cpp/sci_usecanvas.cpp
    sci_gateway/cpp/sci_helpbrowser.cpp
    sci_gateway/cpp/sci_fire_closing_finished.cpp
    sci_gateway/cpp/sci_useeditor.cpp
    sci_gateway/cpp/sci_openged.cpp
    sci_gateway/cpp/sci_datatipcreate.cpp
    sci_gateway/cpp/sci_datatipremove.cpp
    sci_gateway/cpp/sci_plotbrowser.cpp
    sci_gateway/cpp/sci_datatip_manager_mode.cpp
    sci_gateway/cpp/sci_datatipmove.cpp
    sci_gateway/cpp/sci_datatip_set_style.cpp
    sci_gateway/cpp/sci_datatip_set_display.cpp
    sci_gateway/cpp/sci_datatip_set_interp.cpp
    sci_gateway/cpp/sci_datatip_set_orient.cpp
    sci_gateway/cpp/sci_saveGui.cpp
    sci_gateway/cpp/sci_loadGui.cpp
    sci_gateway/cpp/sci_xchoicesi.cpp
    sci_gateway/cpp/sci_uigetcolor.cpp
    sci_gateway/cpp/sci_ClipBoard.cpp
    sci_gateway/cpp/sci_x_mdialog.cpp
    sci_gateway/cpp/sci_x_dialog.cpp
    sci_gateway/cpp/sci_getBrowserCookies.cpp
  LANG CXX C
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/src/c/getHandleProperty
    ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/graphic_export/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/webtools/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 515)

scilab_module(gui-disable
  GATEWAY_SOURCES
    src/nogui/nogui.c
  LANG C
  SYMBOLS 13)
