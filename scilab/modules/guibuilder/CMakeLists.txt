# CMake build for the `guibuilder` module — Batch B on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. This module was never part of the autotools
# tree (it is new, Phase 1 of the GUI designer), so unlike its Batch B
# neighbours there is no automake Makefile.am to stay invisible to and no
# baseline-autotools.json entry for the parity harness to arbitrate against.
#
# SINGLE VARIANT — deliberately ONE scilab_module() call, not the usual Batch B
# variant pair (main + `-disable` stub). Measured, not assumed: under -nwni,
# scilab-cli-bin links the `-disable` stub of every Batch B GUI module
# (libscicommons-disable.dylib, libsciscinotes-disable.dylib, libscijvm-
# disable.dylib, ...), and scinotes' OWN gateway primitive never actually
# runs there — it is in FuncManager::CreateNonNwniModuleList's exclusion
# set, so under -nwni Scilab substitutes a generic dummy before
# libsciscinotes.dylib is ever dlopen'd. guidesigner_open has no such
# exclusion (Task 7's placeholder has no window to fail to open, and the
# task requires it to work under -nwni), so THIS module's real gateway runs
# in both worlds. Two consequences, both reproduced and fixed while wiring
# this module in rather than assumed away:
#   1. src/jni/GuiDesigner.{hxx,cpp} does not use modules/commons'
#      GiwsException: commons-disable compiles only src/c/fileutils.c, not
#      GiwsException.cpp, so a dylib referencing it fails to dlopen under
#      -nwni ("symbol not found in flat namespace").
#   2. getScilabJavaVM() cannot be left to flat-namespace luck (the
#      scinotes/gui pattern, which works there ONLY because scinotes never
#      actually reaches that call under -nwni): scilab-cli-bin links
#      libscijvm-disable.dylib, not the real libscijvm.dylib that exports
#      it, so MODULE_DEPS scijvm below adds a REAL dyld dependency edge —
#      dlopen'ing this dylib now force-loads the real libscijvm.dylib too,
#      in either mode, exactly like modules/types and modules/
#      external_objects_java already do for the same symbol (their
#      MODULE_DEPS scicommons scijvm; guibuilder needs only scijvm, having
#      no commons dependency left after point 1).
# With both fixed, the one real dylib below needs nothing the -disable
# ecosystem lacks, so a second, do-nothing `-disable` variant would add a
# file with no behavioural difference to reproduce.
#
# LINKING CLASS (mirrors scinotes): GUI_LIBS — this dylib is a direct dyld
# dependency of the aggregate/executables, loaded at process launch, exactly
# like the other Batch B GUI modules.
#
# Sources: algo = the hand-written JNI bridge (src/jni/GuiDesigner.{hxx,cpp}),
# playing the role a GIWS-generated pair would play elsewhere in this tree
# (see src/jni/GuiDesigner.giws.xml for the interface it implements by hand —
# modules/graph, which @ScilabExported lives in, is not a dependency of this
# module and pulling it in for one annotation was rejected); gateway = the one
# hand-written cpp gateway (sci_guidesigner.cpp) behind the guidesigner_open
# primitive that modules/guibuilder/macros/guidesigner.sci calls.

scilab_module(guibuilder
  ALGO_SOURCES
    src/jni/GuiDesigner.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_guidesigner.cpp
  LANG CXX
  MODULE_DEPS scijvm
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
  CLASS GUI_LIBS
  SYMBOLS 2)
