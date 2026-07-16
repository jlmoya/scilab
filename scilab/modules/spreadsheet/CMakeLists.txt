# CMake build for the `spreadsheet` module — Batch C (external deps) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools spreadsheet is always recoverable with
# `make -C modules/spreadsheet`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscispreadsheet.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscispreadsheet.2027.dylib
#   exports:       exactly 181 symbols
#   deps:          @rpath/libxlnt.1.6.1.dylib   (bundled, from-source)
#                  /opt/homebrew/opt/apache-arrow/lib/libparquet.2500.dylib
#                  /opt/homebrew/opt/apache-arrow/lib/libarrow.2500.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: LIBADD += $(XLNT_LDFLAGS) $(XLNT_LIBS) $(ARROW_LDFLAGS)
# $(ARROW_LIBS) $(PARQUET_LDFLAGS) $(PARQUET_LIBS). All pinned as absolute
# paths: libxlnt from the out-of-tree from-source prefix (its install_name IS
# @rpath/libxlnt.1.6.1.dylib — the recorded dep, matching the baseline
# verbatim); parquet/arrow via the /opt/homebrew/opt/apache-arrow alias of the
# configured Cellar path (same keg, brew-stable spelling) — install_names
# record at the /opt/homebrew/opt/... paths above.
#
# Sources verbatim from Makefile.am: algo = 8 C + 7 CPP + 7 ripole C;
# gateway = 9 CXX + 6 C. EXTRA_INCLUDES: src/c/ripole + sci_gateway/cpp
# (module-local) + XLNT/ARROW/PARQUET CFLAGS dirs + the usual module set.

scilab_module(spreadsheet
  ALGO_SOURCES
    src/c/xls.c
    src/c/checkCsvWriteFormat.c
    src/c/csvDefault.c
    src/c/csvRead.c
    src/c/csvWrite.c
    src/c/getRange.c
    src/c/splitLine.c
    src/c/utftolatin.c
    src/cpp/xlsx_read.cpp
    src/cpp/xlsx_write.cpp
    src/cpp/xlsx_metadata.cpp
    src/cpp/arrow_read.cpp
    src/cpp/arrow_write.cpp
    src/cpp/arrow_write_utils.cpp
    src/cpp/parquet_write.cpp
    src/c/ripole/olestream-unwrap.c
    src/c/ripole/pldstr.c
    src/c/ripole/ole.c
    src/c/ripole/bytedecoders.c
    src/c/ripole/logger.c
    src/c/ripole/ripole.c
    src/c/ripole/bt-int.c
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_xls_read.cpp
    sci_gateway/cpp/sci_xls_open.cpp
    sci_gateway/cpp/sci_isXlsx.cpp
    sci_gateway/cpp/sci_xlsxInfo.cpp
    sci_gateway/cpp/sci_xlsxRead.cpp
    sci_gateway/cpp/sci_xlsxSheet.cpp
    sci_gateway/cpp/sci_xlsxWrite.cpp
    sci_gateway/cpp/sci_parquetRead.cpp
    sci_gateway/cpp/sci_parquetWrite.cpp
    sci_gateway/c/gw_csv_helpers.c
    sci_gateway/c/sci_csvDefault.c
    sci_gateway/c/sci_csvRead.c
    sci_gateway/c/sci_csvStringToDouble.c
    sci_gateway/c/sci_csvTextScan.c
    sci_gateway/c/sci_csvWrite.c
  LANG C CXX
  SYSTEM_LIBS
    /Users/josemoya/Projects/CLionProjects/scilab/xlnt-prefix/lib/libxlnt.dylib
    /opt/homebrew/opt/apache-arrow/lib/libparquet.dylib
    /opt/homebrew/opt/apache-arrow/lib/libarrow.dylib
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/c/ripole
    ${CMAKE_CURRENT_SOURCE_DIR}/sci_gateway/cpp
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/string/src/c
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    /Users/josemoya/Projects/CLionProjects/scilab/xlnt-prefix/include
    /opt/homebrew/opt/apache-arrow/include
  CLASS DYNAMIC_LOAD
  SYMBOLS 181)

# Apache Arrow >= 24 headers require C++20 — the same per-module override
# automake applied (libscispreadsheet_*_la_CXXFLAGS = $(AM_CXXFLAGS)
# -std=c++20): appended AFTER the helper's baseline -std=c++17, so the last
# -std wins for this module's C++ TUs only. flag-facts (O2/fwrapv/min_macos)
# are unaffected.
target_compile_options(scispreadsheet-algo PRIVATE "$<$<COMPILE_LANGUAGE:CXX>:-std=c++20>")
target_compile_options(scispreadsheet      PRIVATE "$<$<COMPILE_LANGUAGE:CXX>:-std=c++20>")

# libxlnt lives out of tree with an @rpath install name: the autotools link
# gave the module an rpath to its own directory (a libxlnt copy is kept next
# to the dylib in .libs/) plus the build-time prefix — reproduced verbatim so
# the dropped-in dylib resolves @rpath/libxlnt.1.6.1.dylib at runtime. (Not
# parity-compared — LC_RPATHs are outside the fingerprint — but load-bearing
# for the drop-in to actually run.)
target_link_options(scispreadsheet PRIVATE
  "LINKER:-rpath,@loader_path/"
  "LINKER:-rpath,/Users/josemoya/Projects/CLionProjects/scilab/xlnt-prefix/lib")
