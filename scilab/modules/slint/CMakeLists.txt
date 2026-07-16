# CMake build for the `slint` module — Batch C (external deps) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools slint is always recoverable with `make -C modules/slint`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscislint.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscislint.2027.dylib
#   exports:       exactly 676 symbols
#   deps:          /usr/lib/libxml2.2.dylib  /usr/lib/libz.1.dylib
#                  /usr/lib/libicucore.A.dylib  /usr/lib/libc++.1.dylib
#                  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: XML_LIBS = -lxml2 -lz -lpthread -licucore -lm -> the coverage
# pattern (plain SDK names; pthread/m fold into libSystem). PCRE2 is headers-
# only here (PCRE2_CFLAGS; no PCRE2 lib in LIBADD — the matcher resolves at
# load time via dynamic_lookup, like the Scilab API symbols). SLINT_FLAGS is
# empty in the configured Makefile.
#
# Sources verbatim from Makefile.am: algo = the 74 SLINT_CXX_SOURCES;
# gateway = sci_slint.cpp.

scilab_module(slint
  ALGO_SOURCES
    src/cpp/SLintContext.cpp
    src/cpp/SLintChecker.cpp
    src/cpp/SLintVisitor.cpp
    src/cpp/SLint.cpp
    src/cpp/SLintOptions.cpp
    src/cpp/SciFile.cpp
    src/cpp/FileException.cpp
    src/cpp/PCREMatcher.cpp
    src/cpp/PCREException.cpp
    src/cpp/XMLConfig.cpp
    src/cpp/SLintXMLException.cpp
    src/cpp/SLintScilabResult.cpp
    src/cpp/FunctionNameChecker.cpp
    src/cpp/FunctionArgsChecker.cpp
    src/cpp/VariableNameChecker.cpp
    src/cpp/VariablesChecker.cpp
    src/cpp/UselessArgChecker.cpp
    src/cpp/UselessRetChecker.cpp
    src/cpp/RedefinitionChecker.cpp
    src/cpp/SingleInstrChecker.cpp
    src/cpp/EmptyBlockChecker.cpp
    src/cpp/SemicolonAtEOLChecker.cpp
    src/cpp/MopenMcloseChecker.cpp
    src/cpp/PrintfChecker.cpp
    src/cpp/TodoChecker.cpp
    src/cpp/McCabeChecker.cpp
    src/cpp/McCabeVisitor.cpp
    src/cpp/DecimalChecker.cpp
    src/cpp/LineLengthChecker.cpp
    src/cpp/LinesCountChecker.cpp
    src/cpp/IllegalCallsChecker.cpp
    src/cpp/NaNChecker.cpp
    src/cpp/EqEqChecker.cpp
    src/cpp/UselessOpChecker.cpp
    src/cpp/UnreachableCodeChecker.cpp
    src/cpp/DeprecatedChecker.cpp
    src/cpp/SelectChecker.cpp
    src/cpp/ImplicitListChecker.cpp
    src/cpp/StructChecker.cpp
    src/cpp/LoadSaveChecker.cpp
    src/cpp/OldNotChecker.cpp
    src/cpp/SpacesAroundOpChecker.cpp
    src/cpp/BreaksInLoopChecker.cpp
    src/cpp/NestedBlocksChecker.cpp
    src/cpp/BracketedExpChecker.cpp
    src/cpp/NotNotChecker.cpp
    src/cpp/SpacesInArgsChecker.cpp
    src/cpp/GlobalKeywordChecker.cpp
    src/cpp/ExpInCondChecker.cpp
    src/cpp/CommentRatioChecker.cpp
    src/cpp/FunctionArgsOrderChecker.cpp
    src/cpp/FunctionTestReturnChecker.cpp
    src/cpp/ReturnsCountChecker.cpp
    src/cpp/NotEqualChecker.cpp
    src/cpp/XMLtools.cpp
    src/cpp/SLintXmlResult.cpp
    src/cpp/AnalysisConfiguration.cpp
    src/cpp/AnalysisConfigurationType.cpp
    src/cpp/AnalysisRuleParameterType.cpp
    src/cpp/AnalysisRuleType.cpp
    src/cpp/ExcludedProjectFileType.cpp
    src/cpp/RuleLinkType.cpp
    src/cpp/Standard.cpp
    src/cpp/StandardRuleParameterType.cpp
    src/cpp/StandardRuleParameterValueType.cpp
    src/cpp/StandardRuleType.cpp
    src/cpp/StandardType.cpp
    src/cpp/ToolConfiguration.cpp
    src/cpp/ToolConfigurationType.cpp
    src/cpp/CNESException.cpp
    src/cpp/CNESConfig.cpp
    src/cpp/CNESXmlResult.cpp
    src/cpp/CNESCsvResult.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_slint.cpp
  LANG CXX
  SYSTEM_LIBS xml2 z icucore
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/Cellar/pcre2/10.47_1/include
  CLASS ENGINE_LIBS
  SYMBOLS 676)
