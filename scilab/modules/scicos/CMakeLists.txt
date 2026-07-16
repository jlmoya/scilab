# CMake build for the `scicos` module — Batch E (inter-module edges) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools scicos is always recoverable with
# `make -C modules/scicos`.
#
# VARIANT PAIR — FULL TWINS sharing ONE algo lib (the scicos_blocks-cli
# precedent): main and `-cli` build the SAME gateway sources + the SAME
# libsciscicos-algo convenience objects (the -cli call consumes
# $<TARGET_OBJECTS:sciscicos-algo>, object-for-object what libtool folded —
# no double compile, and both la_CPPFLAGS are defined identical). They differ
# ONLY in which scicos_blocks twin they link: main -> sciscicos_blocks,
# -cli -> sciscicos_blocks-cli. Both also link scisundials (defined in
# modules/differential_equations/ — CMake resolves cross-directory target
# names at generate time and orders the build).
#
# LINKING CLASS (modules/Makefile.am): main = ENGINE_LIBS_DYNAMIC_LOAD
# (dlopen-only). `-cli` appears in NO aggregate link list (it is linked by
# libscilab-cli consumers via libsciscicos-cli.la wiring on other platforms;
# the manifest's `(none)` class) -> no CLASS keyword, this comment is the
# record.
#
# PARITY CONTRACT (arbitrated by build-parity/): both dylibs export 604
# symbols (identical export lists — same TUs); deps
#   /opt/homebrew/opt/libarchive/lib/libarchive.13.dylib     (LIBARCHIVE_LIBS)
#   /usr/local/lib/scilab/libscisundials.2027.dylib          (MODULE_DEPS)
#   /opt/homebrew/opt/suite-sparse/lib/lib{klu.2,amd.3,umfpack.6}.dylib
#   /opt/homebrew/opt/libomp/lib/libomp.dylib                (FIND_PACKAGES)
#   /usr/local/lib/scilab/libsciscicos_blocks.2027.dylib     (main)
#     — or libsciscicos_blocks-cli.2027.dylib                (-cli)
#   libgfortran.5 + libquadmath.0 (Fortran TUs -> implicit runtime)
#   + libc++ + libSystem
#
# EXTERNAL DEPS, from the autotools LIBADDs: the algo lib carries
# $(LIBXML_LIBS) — an UNDEFINED automake variable on this tree (config.status
# has no S["LIBXML_LIBS"]; the real xml variable is XML_LIBS) -> empty, which
# is exactly why the baseline records NO xml2/z/icucore for scicos. The
# klu/amd/umfpack/omp deps ride in transitively through libscisundials.la's
# dependency_libs; CMake target links do not propagate a sibling's PRIVATE
# deps, so they are spelled explicitly (absolute keg paths, the matio
# pattern). $(LIBARCHIVE_LIBS) = `-larchive -ldl` (-ldl folds into libSystem).
#
# INCLUDES: EXTRA_INCLUDES = libsciscicos_algo_la_CPPFLAGS (XCOS branch)
# beyond the helper base — the patched_sundials set, the module-dir set,
# XML_FLAGS' SDK usr/include and LIBARCHIVE_CFLAGS' openssl include.

scilab_module(scicos
  ALGO_SOURCES
    src/cpp/il_state.cpp
    src/cpp/il_sim.cpp
    src/cpp/var2vec.cpp
    src/cpp/vec2var.cpp
    src/cpp/XMIResource.cpp
    src/cpp/XMIResource_load.cpp
    src/cpp/XMIResource_save.cpp
    src/cpp/SSPResource.cpp
    src/cpp/SSPResource_load.cpp
    src/cpp/SSPResource_save.cpp
    src/cpp/Controller.cpp
    src/cpp/controller_helpers.cpp
    src/cpp/LoggerView.cpp
    src/cpp/Model.cpp
    src/cpp/Model_getObjectProperties.cpp
    src/cpp/Model_setObjectProperties.cpp
    src/cpp/view_scilab/Adapters.cpp
    src/cpp/view_scilab/AdapterView.cpp
    src/cpp/view_scilab/BlockAdapter.cpp
    src/cpp/view_scilab/CprAdapter.cpp
    src/cpp/view_scilab/DiagramAdapter.cpp
    src/cpp/view_scilab/GraphicsAdapter.cpp
    src/cpp/view_scilab/LinkAdapter.cpp
    src/cpp/view_scilab/ModelAdapter.cpp
    src/cpp/view_scilab/ParamsAdapter.cpp
    src/cpp/view_scilab/ScsAdapter.cpp
    src/cpp/view_scilab/StateAdapter.cpp
    src/cpp/view_scilab/TextAdapter.cpp
    src/cpp/sciblk2.cpp
    src/cpp/sciblk4.cpp
    src/cpp/createblklist.cpp
    src/cpp/extractblklist.cpp
    src/c/tree.c
    src/c/scicos_malloc.c
    src/c/scicos_print.c
    src/c/import.c
    src/c/scicos.c
    src/c/scicos_free.c
    src/c/ezxml.c
    src/c/var2sci.c
    src/c/MlistGetFieldNumber.c
    src/c/lsodar.c
    src/c/ddaskr.c
    src/fortran/sctree.f
    src/fortran/ftree2.f
    src/fortran/ftree3.f
    src/fortran/ftree4.f
    src/fortran/scifunc.f
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_scicos_debug.cpp
    sci_gateway/cpp/sci_scicos_debug_count.cpp
    sci_gateway/cpp/sci_scicos_log.cpp
    sci_gateway/cpp/sci_buildouttb.cpp
    sci_gateway/cpp/sci_ctree2.cpp
    sci_gateway/cpp/sci_ctree4.cpp
    sci_gateway/cpp/sci_coserror.cpp
    sci_gateway/cpp/sci_curblock.cpp
    sci_gateway/cpp/sci_data2sig.cpp
    sci_gateway/cpp/sci_diffobjs.cpp
    sci_gateway/cpp/sci_duplicate.cpp
    sci_gateway/cpp/sci_end_scicosim.cpp
    sci_gateway/cpp/sci_getblocklabel.cpp
    sci_gateway/cpp/sci_getscicosvars.cpp
    sci_gateway/cpp/sci_phase_simulation.cpp
    sci_gateway/cpp/sci_pointer_xproperty.cpp
    sci_gateway/cpp/sci_scicos_new.cpp
    sci_gateway/cpp/sci_scicos_setfield.cpp
    sci_gateway/cpp/sci_scicos_time.cpp
    sci_gateway/cpp/sci_scicosim.cpp
    sci_gateway/cpp/sci_set_blockerror.cpp
    sci_gateway/cpp/sci_set_xproperty.cpp
    sci_gateway/cpp/sci_sig2data.cpp
    sci_gateway/cpp/sci_validvar.cpp
    sci_gateway/cpp/sci_var2vec.cpp
    sci_gateway/cpp/sci_vec2var.cpp
    sci_gateway/cpp/sci_callblk.cpp
    sci_gateway/cpp/sci_model2blk.cpp
    sci_gateway/cpp/sci_scicosDiagramToScilab.cpp
    sci_gateway/c/sci_ftree2.c
    sci_gateway/c/sci_ftree3.c
    sci_gateway/c/sci_ftree4.c
    sci_gateway/c/sci_loadScicos.c
    sci_gateway/c/sci_sctree.c
    sci_gateway/fortran/sci_f_tree2.f
    sci_gateway/fortran/sci_f_tree3.f
    sci_gateway/fortran/sci_f_tree4.f
  LANG C CXX Fortran
  MODULE_DEPS scisundials sciscicos_blocks
  SYSTEM_LIBS
    /opt/homebrew/opt/libarchive/lib/libarchive.dylib
    /opt/homebrew/opt/suite-sparse/lib/libklu.dylib
    /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
    /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
  FIND_PACKAGES OpenMP
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/include
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/include/sundials
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/sundials
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/idas
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/cvodes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/scicos_blocks/includes
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/time/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/opt/openssl/include
  CLASS DYNAMIC_LOAD
  SYMBOLS 604)

# --- the CLI twin (same gateways + the SHARED algo objects; links the
# scicos_blocks -cli twin instead — see the header comment) ---
scilab_module(scicos-cli
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_scicos_debug.cpp
    sci_gateway/cpp/sci_scicos_debug_count.cpp
    sci_gateway/cpp/sci_scicos_log.cpp
    sci_gateway/cpp/sci_buildouttb.cpp
    sci_gateway/cpp/sci_ctree2.cpp
    sci_gateway/cpp/sci_ctree4.cpp
    sci_gateway/cpp/sci_coserror.cpp
    sci_gateway/cpp/sci_curblock.cpp
    sci_gateway/cpp/sci_data2sig.cpp
    sci_gateway/cpp/sci_diffobjs.cpp
    sci_gateway/cpp/sci_duplicate.cpp
    sci_gateway/cpp/sci_end_scicosim.cpp
    sci_gateway/cpp/sci_getblocklabel.cpp
    sci_gateway/cpp/sci_getscicosvars.cpp
    sci_gateway/cpp/sci_phase_simulation.cpp
    sci_gateway/cpp/sci_pointer_xproperty.cpp
    sci_gateway/cpp/sci_scicos_new.cpp
    sci_gateway/cpp/sci_scicos_setfield.cpp
    sci_gateway/cpp/sci_scicos_time.cpp
    sci_gateway/cpp/sci_scicosim.cpp
    sci_gateway/cpp/sci_set_blockerror.cpp
    sci_gateway/cpp/sci_set_xproperty.cpp
    sci_gateway/cpp/sci_sig2data.cpp
    sci_gateway/cpp/sci_validvar.cpp
    sci_gateway/cpp/sci_var2vec.cpp
    sci_gateway/cpp/sci_vec2var.cpp
    sci_gateway/cpp/sci_callblk.cpp
    sci_gateway/cpp/sci_model2blk.cpp
    sci_gateway/cpp/sci_scicosDiagramToScilab.cpp
    sci_gateway/c/sci_ftree2.c
    sci_gateway/c/sci_ftree3.c
    sci_gateway/c/sci_ftree4.c
    sci_gateway/c/sci_loadScicos.c
    sci_gateway/c/sci_sctree.c
    sci_gateway/fortran/sci_f_tree2.f
    sci_gateway/fortran/sci_f_tree3.f
    sci_gateway/fortran/sci_f_tree4.f
    $<TARGET_OBJECTS:sciscicos-algo>
  LANG C CXX Fortran
  MODULE_DEPS scisundials sciscicos_blocks-cli
  SYSTEM_LIBS
    /opt/homebrew/opt/libarchive/lib/libarchive.dylib
    /opt/homebrew/opt/suite-sparse/lib/libklu.dylib
    /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
    /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
  FIND_PACKAGES OpenMP
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/include
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/include/sundials
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/sundials
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/idas
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/src/patched_sundials/src/cvodes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/scicos_blocks/includes
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/time/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/differential_equations/includes
    /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include
    /opt/homebrew/opt/openssl/include
  SYMBOLS 604)
