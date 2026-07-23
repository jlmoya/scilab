# CMake build for the `xcos` module — the densest edge module (5 sibling deps
# + the JDK) on the shared scilab_module() helper (cmake/ScilabModule.cmake);
# built from the top-level scilab/CMakeLists.txt driver.
#
# VARIANT PAIR — TWO scilab_module() calls: the MAIN dylib and the `-disable`
# stub (noxcos.c, pure C -> C-driver link, deps = libSystem ONLY).
#
# LINKING CLASS (modules/Makefile.am): main = ENGINE_LIBS_DYNAMIC_LOAD
# (dlopen'ed at runtime). The -disable stub is NO_GUI_LIBS — empty on macOS —
# so its call omits CLASS; this comment is the record (Batch B precedent).
#
# MODULE EDGES: libscixcos_la_LIBADD names libsciscicos.la + libscicommons.la
# + libscijvm.la; libtool's dependency_libs expansion additionally records
# libscisundials + libsciscicos_blocks (through scicos), klu/amd/umfpack/omp
# (through sundials), gfortran/quadmath (FLIBS through the .la chain — xcos
# itself compiles NO Fortran, so CMake's implicit Fortran runtime never fires
# and both are pinned explicitly), xml2/z/icucore + `-ljli` (through jvm).
# All spelled explicitly below: MODULE_DEPS for the five sibling targets +
# absolute keg paths / raw flags for the externals. omp is a SYSTEM_LIBS
# absolute path (like klu/amd/umfpack), NOT FIND_PACKAGES OpenMP: xcos's own
# Makefile.am has ZERO omp/OPENMP references (verified) — it is CXX-only with
# no compile-flag need at all, and FIND_PACKAGES OpenMP would put -fopenmp on
# every one of its C++ TUs via OpenMP::OpenMP_CXX's INTERFACE_COMPILE_OPTIONS.
# SYSTEM_LIBS' absolute path gets the identical linked dep with no compile-
# side effect (byte-identical otool -L, verified).
#
# THE JDK DEP records as `@rpath/libjli.dylib` with NO JDK LC_RPATH (the
# -Wl,-rpath inside JAVA_JNI_LIBS is dropped by libtool's dependency_libs
# propagation — same as types-java/external_objects_java; verified on the
# baseline dylib).
#
# THE @rpath SIBLING DEPS — reproduced from the autotools all-local hook:
# xcos is dlopen'ed, so its scicos/scicos_blocks deps cannot resolve through
# the libtool wrapper's DYLD paths in an uninstalled tree; the Makefile.am
# rewrites those two recorded deps to @rpath (install_name_tool -change) and
# stamps 4 extra LC_RPATHs covering every layout, then re-signs (macOS arm64
# requires a valid signature after mutation). The POST_BUILD command below is
# that hook, verbatim; the rpaths are appended after the helper's standard
# pair, preserving the baseline LC_RPATH order:
#   /usr/lib, gcc/current, @loader_path/, @loader_path/../../scicos/.libs,
#   @loader_path/../../scicos_blocks/.libs, /usr/local/lib/scilab
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscixcos.2027.dylib          exports 231; deps
#       @rpath/libsciscicos.2027.dylib            (rewritten)
#       @rpath/libsciscicos_blocks.2027.dylib     (rewritten)
#       /usr/local/lib/scilab/libscisundials.2027.dylib
#       /usr/local/lib/scilab/lib{scicommons,scijvm}.2027.dylib
#       /opt/homebrew/opt/suite-sparse/lib/lib{klu.2,amd.3,umfpack.6}.dylib
#       /opt/homebrew/opt/libomp/lib/libomp.dylib
#       /opt/homebrew/opt/gcc/lib/gcc/current/lib{gfortran.5,quadmath.0}.dylib
#       /usr/lib/lib{xml2.2,z.1,icucore.A}.dylib + @rpath/libjli.dylib
#       /opt/homebrew/opt/libarchive/lib/libarchive.13.dylib
#       + libc++ + libSystem
#   libscixcos-disable.2027.dylib  exports 1; deps libSystem ONLY
#
# Sources verbatim from Makefile.am (XCOS_CXX_SOURCES lists .hxx headers too —
# only the compiled TUs are spelled here); EXTRA_INCLUDES = la_CPPFLAGS beyond
# the helper base (incl. XML_FLAGS' SDK usr/include, LIBARCHIVE_CFLAGS'
# openssl include, and the JNI pair). SCILAB_JAVA_HOME comes from the
# toolchain (cmake/ScilabToolchain.cmake — config.status S["JAVA_HOME"], the
# configured jdk-25).

scilab_module(xcos
  ALGO_SOURCES
    src/jni/Xcos.cpp
    src/jni/XcosCellFactory.cpp
    src/jni/Palette.cpp
    src/jni/Modelica.cpp
    src/jni/JavaController_wrap.cxx
    src/cpp/xcosUtilities.cpp
    src/cpp/loadStatus.cpp
    src/jni/JavaSSPResource_wrap.cxx
    src/jni/JavaXMIResource_wrap.cxx
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_Xcos.cpp
    sci_gateway/cpp/sci_warnBlockByUID.cpp
    sci_gateway/cpp/sci_closeXcosFromScilab.cpp
    sci_gateway/cpp/sci_xcosCellCreated.cpp
    sci_gateway/cpp/sci_xcosUpdateBlock.cpp
    sci_gateway/cpp/sci_xcosDiagramToScilab.cpp
    sci_gateway/cpp/sci_xcosDiagramExport.cpp
    sci_gateway/cpp/sci_xcosPalLoad.cpp
    sci_gateway/cpp/sci_xcosPalCategoryAdd.cpp
    sci_gateway/cpp/sci_xcosPalDelete.cpp
    sci_gateway/cpp/sci_xcosPalMove.cpp
    sci_gateway/cpp/sci_xcosPalEnable.cpp
    sci_gateway/cpp/sci_xcosPalDisable.cpp
    sci_gateway/cpp/sci_xcosPalGenerateIcon.cpp
    sci_gateway/cpp/sci_xcosPalGet.cpp
    sci_gateway/cpp/sci_xcosConfigureXmlFile.cpp
    sci_gateway/cpp/sci_xcosAddToolsMenu.cpp
    sci_gateway/cpp/sci_loadXcos.cpp
    sci_gateway/cpp/sci_xcosSimulationStarted.cpp
  LANG CXX
  MODULE_DEPS sciscicos scisundials sciscicos_blocks scicommons scijvm
  SYSTEM_LIBS
    /opt/homebrew/opt/suite-sparse/lib/libklu.dylib
    /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
    /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
    /opt/homebrew/opt/libomp/lib/libomp.dylib
    /opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.dylib
    /opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.dylib
    xml2 z icucore
    "-L${SCILAB_JAVA_HOME}/lib" jli
    /opt/homebrew/opt/libarchive/lib/libarchive.dylib
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/jni
    ${SCILAB_SOURCE_DIR}/modules/scicos/includes
    ${SCILAB_SOURCE_DIR}/modules/scicos_blocks/includes
    ${SCILAB_SOURCE_DIR}/modules/scicos_blocks/src/jni
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/opt/openssl/include
    ${SCILAB_JAVA_HOME}/include
    ${SCILAB_JAVA_HOME}/include/darwin
  CLASS DYNAMIC_LOAD
  SYMBOLS 231)

# The autotools libscixcos_la_LDFLAGS rpath quartet (appended after the
# helper's /usr/lib + gcc/current pair — baseline LC_RPATH order preserved).
target_link_options(scixcos PRIVATE
  "LINKER:-rpath,@loader_path/"
  "LINKER:-rpath,@loader_path/../../scicos/.libs"
  "LINKER:-rpath,@loader_path/../../scicos_blocks/.libs"
  "LINKER:-rpath,/usr/local/lib/scilab")

# The autotools all-local hook, verbatim: rewrite the two sibling deps the
# link just recorded at their $(pkglibdir) install_names to @rpath, then
# re-sign (install_name_tool invalidates the ad-hoc signature; unsigned arm64
# dylibs refuse to load). Runs before drop-in-xcos (POST_BUILD completes as
# part of the target).
add_custom_command(TARGET scixcos POST_BUILD
  COMMAND install_name_tool
    -change "/usr/local/lib/scilab/libsciscicos.${SCILAB_LIBRARY_VERSION_MAJOR}.dylib"
            "@rpath/libsciscicos.${SCILAB_LIBRARY_VERSION_MAJOR}.dylib"
    -change "/usr/local/lib/scilab/libsciscicos_blocks.${SCILAB_LIBRARY_VERSION_MAJOR}.dylib"
            "@rpath/libsciscicos_blocks.${SCILAB_LIBRARY_VERSION_MAJOR}.dylib"
    $<TARGET_FILE:scixcos>
  COMMAND codesign -f -s - $<TARGET_FILE:scixcos>
  VERBATIM
  COMMENT "xcos: rewriting scicos/scicos_blocks deps to @rpath + re-signing")

scilab_module(xcos-disable
  GATEWAY_SOURCES
    src/noxcos/noxcos.c
  LANG C
  EXTRA_INCLUDES
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/opt/openssl/include
  SYMBOLS 1)
