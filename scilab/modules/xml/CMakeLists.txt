# CMake build for the `xml` module — Batch C (external deps) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools xml is always recoverable with `make -C modules/xml`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscixml.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscixml.2027.dylib
#   exports:       exactly 334 symbols
#   deps:          /usr/lib/libxml2.2.dylib  /usr/lib/libz.1.dylib
#                  /usr/lib/libicucore.A.dylib  /usr/lib/libc++.1.dylib
#                  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: XML_LIBS = -lxml2 -lz -lpthread -licucore -lm — the coverage
# pattern verbatim (plain SDK names -> /usr/lib deps; pthread/m fold into
# libSystem). XML_FLAGS' SDK usr/include in EXTRA_INCLUDES (the SDK ships the
# libxml/ headers there).
#
# Sources verbatim from Makefile.am: algo = the 16 XML_CXX_SOURCES
# (XML_JNI_SOURCES is empty); gateway = the 39 GATEWAY_CXX_SOURCES.

scilab_module(xml
  ALGO_SOURCES
    src/cpp/VariableScope.cpp
    src/cpp/XMLObject.cpp
    src/cpp/XMLDocument.cpp
    src/cpp/XMLElement.cpp
    src/cpp/XMLAttr.cpp
    src/cpp/XMLNs.cpp
    src/cpp/XMLNodeList.cpp
    src/cpp/XMLNodeSet.cpp
    src/cpp/XMLXPath.cpp
    src/cpp/XMLList.cpp
    src/cpp/XMLNotHandledElement.cpp
    src/cpp/XMLValidation.cpp
    src/cpp/XMLValidationDTD.cpp
    src/cpp/XMLValidationSchema.cpp
    src/cpp/XMLValidationRelaxNG.cpp
    src/cpp/XMLMlistsManagement.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_xmlRead.cpp
    sci_gateway/cpp/sci_xmlXPath.cpp
    sci_gateway/cpp/sci_extraction.cpp
    sci_gateway/cpp/sci_insertion.cpp
    sci_gateway/cpp/sci_print.cpp
    sci_gateway/cpp/sci_xmlGetOpenDocs.cpp
    sci_gateway/cpp/sci_xmlDelete.cpp
    sci_gateway/cpp/sci_xmlDump.cpp
    sci_gateway/cpp/sci_xmlReadStr.cpp
    sci_gateway/cpp/sci_xmlFormat.cpp
    sci_gateway/cpp/sci_percent_XMLList_e.cpp
    sci_gateway/cpp/sci_percent_XMLAttr_e.cpp
    sci_gateway/cpp/sci_percent_XMLList_size.cpp
    sci_gateway/cpp/sci_percent_XMLAttr_size.cpp
    sci_gateway/cpp/sci_percent_XMLList_length.cpp
    sci_gateway/cpp/sci_percent_XMLAttr_length.cpp
    sci_gateway/cpp/sci_percent_c_i_XMLAttr.cpp
    sci_gateway/cpp/sci_percent_s_i_XMLList.cpp
    sci_gateway/cpp/sci_xmlElement.cpp
    sci_gateway/cpp/sci_xmlDocument.cpp
    sci_gateway/cpp/sci_xmlNs.cpp
    sci_gateway/cpp/sci_xmlAddNs.cpp
    sci_gateway/cpp/sci_xmlGetNsByHref.cpp
    sci_gateway/cpp/sci_xmlGetNsByPrefix.cpp
    sci_gateway/cpp/sci_xmlWrite.cpp
    sci_gateway/cpp/sci_percent_foo_i_XMLList.cpp
    sci_gateway/cpp/sci_xmlValidationFile.cpp
    sci_gateway/cpp/sci_xmlValidate.cpp
    sci_gateway/cpp/sci_xmlIsValidObject.cpp
    sci_gateway/cpp/sci_xmlAsNumber.cpp
    sci_gateway/cpp/sci_xmlAsText.cpp
    sci_gateway/cpp/sci_xmlRemove.cpp
    sci_gateway/cpp/sci_xmlSetAttributes.cpp
    sci_gateway/cpp/sci_xmlName.cpp
    sci_gateway/cpp/sci_xmlAppend.cpp
    sci_gateway/cpp/sci_htmlRead.cpp
    sci_gateway/cpp/sci_htmlReadStr.cpp
    sci_gateway/cpp/sci_htmlWrite.cpp
    sci_gateway/cpp/sci_htmlDump.cpp
  LANG CXX
  SYSTEM_LIBS xml2 z icucore
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
  CLASS ENGINE_LIBS
  SYMBOLS 334)
