# CMake build for the `coverage` module — migrated onto the shared scilab_module()
# helper (cmake/ScilabModule.cmake); built from the top-level scilab/CMakeLists.txt
# driver, no longer a standalone project. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools coverage is always recoverable with
# `make -C modules/coverage`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS — linked into libscilab /
# libscilab-cli and the scilab-*-bin executables. The drop-in swap is STILL
# relink-free: the recorded dependency is an install_name
# (/usr/local/lib/scilab/libscicoverage.2027.dylib) and at runtime the
# launcher's DYLD paths resolve the leaf name to modules/coverage/.libs/ —
# replacing the file there replaces the library for everything that links it.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscicoverage.2027.dylib (+ libscicoverage.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libscicoverage.2027.dylib
#   exports:       exactly 158 symbols (C++-mangled; incl. algo-only classes
#                  like coverage::URLEncoder::pairs — the helper's OBJECT lib
#                  folds ALL algo objects in, like the noinst convenience .la)
#   deps:          /usr/lib/libxml2.2.dylib   /usr/lib/libz.1.dylib
#                  /usr/lib/libicucore.A.dylib /usr/lib/libc++.1.dylib
#                  /usr/lib/libSystem.B.dylib
#
# Sources: the 8 COVERAGE_CXX_SOURCES (the noinst algo lib) + the 8
# GATEWAY_CXX_SOURCES, verbatim from Makefile.am.
# SYSTEM_LIBS: XML_LIBS = -lxml2 -lz -licucore as plain -l names resolved in
# the SDK sysroot — NOT find_package'd (FindLibXml2 can resolve to the Homebrew
# keg, changing the recorded dep path vs the /usr/lib baseline). -lpthread/-lm
# fold into libSystem; libc++ comes from the C++ link driver (LANG CXX).
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES: console, string,
# fileio/{includes,src/c}, threads — plus XML_FLAGS' SDK usr/include (the SDK
# ships the libxml/ headers directly under usr/include; listed before the
# Homebrew dirs, preserving the autotools resolution). MACHINE-CONFIG-SPECIFIC:
# an Xcode.app SDK path — a CLT-only machine differs; the future de-autotools
# driver derives it from the active SDK (`xcrun --show-sdk-path`).

scilab_module(coverage
  ALGO_SOURCES
    src/cpp/CoverModule.cpp
    src/cpp/InstrumentVisitor.cpp
    src/cpp/CoverResult.cpp
    src/cpp/CodePrinterVisitor.cpp
    src/cpp/CovHTMLCodePrinter.cpp
    src/cpp/CoverMacroInfo.cpp
    src/cpp/URLEncoder.cpp
    src/cpp/CoverModule_interface.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_covStart.cpp
    sci_gateway/cpp/sci_covWrite.cpp
    sci_gateway/cpp/sci_covStop.cpp
    sci_gateway/cpp/sci_covMerge.cpp
    sci_gateway/cpp/sci_profileGetInfo.cpp
    sci_gateway/cpp/sci_profileEnable.cpp
    sci_gateway/cpp/sci_profileDisable.cpp
    sci_gateway/cpp/coverage_gw.cpp
  LANG CXX
  SYSTEM_LIBS xml2 z icucore
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/src/c
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS
  SYMBOLS 158)
