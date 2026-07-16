# CMake build for the `webtools` module — Batch C (external deps) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools webtools is always recoverable with
# `make -C modules/webtools`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciwebtools.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libsciwebtools.2027.dylib
#   exports:       exactly 72 symbols
#   deps:          @rpath/libcurl.4.dylib      (miniconda's curl)
#                  @rpath/libc++.1.dylib       (miniconda's libc++ — NON-system!)
#                  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: configure found curl in miniconda — CURL_LIBS =
# -L/Users/josemoya/miniconda3/lib -lcurl (via the algo la's LDFLAGS, inherited
# onto the final link). That -L has a SECOND recorded effect the pin-the-
# absolute-path pattern would miss: the C++ driver's implicit -lc++ ALSO
# resolves in the miniconda dir (user -L dirs precede the SDK defaults), and
# both miniconda dylibs carry @rpath install names — hence the baseline's
# @rpath libcurl AND @rpath libc++. So this module reproduces the search-path
# side effect verbatim: SYSTEM_LIBS passes the raw -L flag, then plain curl.
# CURL_CFLAGS (-I miniconda include) + XML_FLAGS (SDK usr/include) in
# EXTRA_INCLUDES.
#
# Sources verbatim from Makefile.am: algo = 6 CPP + DllmainWebtools.c;
# gateway = the 14 GATEWAY_CPP_SOURCES.

scilab_module(webtools
  ALGO_SOURCES
    src/cpp/webtools.cpp
    src/cpp/json.cpp
    src/cpp/fromJSON.cpp
    src/cpp/toJSON.cpp
    src/cpp/url_tools.cpp
    src/cpp/sciCurl.cpp
    src/c/DllmainWebtools.c
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_http_get.cpp
    sci_gateway/cpp/sci_http_put.cpp
    sci_gateway/cpp/sci_http_post.cpp
    sci_gateway/cpp/sci_http_patch.cpp
    sci_gateway/cpp/sci_http_put_post.cpp
    sci_gateway/cpp/sci_http_delete.cpp
    sci_gateway/cpp/sci_http_upload.cpp
    sci_gateway/cpp/sci_url_encode.cpp
    sci_gateway/cpp/sci_url_decode.cpp
    sci_gateway/cpp/sci_url_split.cpp
    sci_gateway/cpp/sci_toJSON.cpp
    sci_gateway/cpp/sci_fromJSON.cpp
    sci_gateway/cpp/gateway_tools.cpp
    sci_gateway/cpp/webtools_gw.cpp
  LANG C CXX
  SYSTEM_LIBS -L/Users/josemoya/miniconda3/lib curl
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/sci_gateway/cpp
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/preferences/includes
    /Users/josemoya/miniconda3/include
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS
  SYMBOLS 72)

# automake compiled the algo la with an extra -Wno-unused-parameter
# (libsciwebtools_algo_la_CPPFLAGS) — warning-visibility only, no codegen fact,
# transcribed for a warning-clean build.
target_compile_options(sciwebtools-algo PRIVATE -Wno-unused-parameter)
