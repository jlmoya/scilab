# CMake build for the `sparse` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — sparse ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scisparse-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscisparse-algo (50
# TUs: SPARSE_C + SPARSE_FORTRAN + COLPACK_CPP) + libscisparse (27 gateway
# TUs).
#
# EXTRA_INCLUDES (libscisparse_la_CPPFLAGS): the six vendored-ColPack
# module-local dirs, console, threads, string, elementary_functions,
# then EIGEN_CPPFLAGS verbatim.

# --- per-TU overrides, verbatim from Makefile.am (`if IS_MACOSX` / "enforce
# -O0 for some files due to macOS crash"): -O0 APPENDED after the global -O2
# (last-wins), the colnew.f class. flagfacts_check.py FILE_EXPECTED_OVERRIDES
# carries all three (blkfct.f/symfct.f/ordmmd.f -> opt=O0).
set_source_files_properties(src/fortran/blkfct.f PROPERTIES COMPILE_OPTIONS "-O0")
set_source_files_properties(src/fortran/symfct.f PROPERTIES COMPILE_OPTIONS "-O0")
set_source_files_properties(src/fortran/ordmmd.f PROPERTIES COMPILE_OPTIONS "-O0")

scilab_object_module(sparse
  SOURCES
    # libscisparse-algo.la (50 TUs)
    src/c/spUtils.c
    src/c/lu.c
    src/c/spFactor.c
    src/c/spBuild.c
    src/c/spOutput.c
    src/c/spAllocate.c
    src/c/spSolve.c
    src/fortran/isort1.f
    src/fortran/spt.f
    src/fortran/sz2ptr.f
    src/fortran/spreshape.f
    src/fortran/ordmmd.f
    src/fortran/blkfc1.f
    src/fortran/blkfct.f
    src/fortran/blkslv.f
    src/fortran/inpnv.f
    src/fortran/symfct.f
    src/ColPack/src/Utilities/CoutLock.cpp
    src/ColPack/src/Utilities/DisjointSets.cpp
    src/ColPack/src/Utilities/File.cpp
    src/ColPack/src/Utilities/MatrixDeallocation.cpp
    src/ColPack/src/Utilities/Pause.cpp
    src/ColPack/src/Utilities/StringTokenizer.cpp
    src/ColPack/src/Utilities/Timer.cpp
    src/ColPack/src/Utilities/command_line_parameter_processor.cpp
    src/ColPack/src/Utilities/current_time.cpp
    src/ColPack/src/Utilities/extra.cpp
    src/ColPack/src/Utilities/mmio.cpp
    src/ColPack/src/Utilities/stat.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphBicoloring.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphBicoloringInterface.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphCore.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphInputOutput.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphOrdering.cpp
    src/ColPack/src/BipartiteGraphBicoloring/BipartiteGraphVertexCover.cpp
    src/ColPack/src/BipartiteGraphPartialColoring/BipartiteGraphPartialColoring.cpp
    src/ColPack/src/BipartiteGraphPartialColoring/BipartiteGraphPartialColoringInterface.cpp
    src/ColPack/src/BipartiteGraphPartialColoring/BipartiteGraphPartialOrdering.cpp
    src/ColPack/src/GeneralGraphColoring/GraphColoring.cpp
    src/ColPack/src/GeneralGraphColoring/GraphColoringInterface.cpp
    src/ColPack/src/GeneralGraphColoring/GraphCore.cpp
    src/ColPack/src/GeneralGraphColoring/GraphInputOutput.cpp
    src/ColPack/src/GeneralGraphColoring/GraphOrdering.cpp
    src/ColPack/src/Recovery/HessianRecovery.cpp
    src/ColPack/src/Recovery/JacobianRecovery1D.cpp
    src/ColPack/src/Recovery/JacobianRecovery2D.cpp
    src/ColPack/src/Recovery/RecoveryCore.cpp
    src/cpp/spCompGeneric.cpp
    src/cpp/spCompJacobian.cpp
    src/cpp/spCompHessian.cpp
    # libscisparse.la (27 TUs)
    sci_gateway/cpp/sparse_gw.cpp
    sci_gateway/cpp/sci_adj2sp.cpp
    sci_gateway/cpp/sci_full.cpp
    sci_gateway/cpp/sci_nnz.cpp
    sci_gateway/cpp/sci_sp2adj.cpp
    sci_gateway/cpp/sci_sparse.cpp
    sci_gateway/cpp/sci_spcompack.cpp
    sci_gateway/cpp/sci_spget.cpp
    sci_gateway/cpp/sci_spset.cpp
    sci_gateway/cpp/sci_spzeros.cpp
    sci_gateway/cpp/sci_spones.cpp
    sci_gateway/cpp/sci_lufact.cpp
    sci_gateway/cpp/sci_lusolve.cpp
    sci_gateway/cpp/sci_luget.cpp
    sci_gateway/cpp/sci_ludel.cpp
    sci_gateway/cpp/sci_ordmmd.cpp
    sci_gateway/cpp/sci_spchol.cpp
    sci_gateway/cpp/sci_sfinit.cpp
    sci_gateway/cpp/sci_symfcti.cpp
    sci_gateway/cpp/sci_bfinit.cpp
    sci_gateway/cpp/sci_inpnv.cpp
    sci_gateway/cpp/sci_blkfc1i.cpp
    sci_gateway/cpp/sci_blkslvi.cpp
    sci_gateway/cpp/sci_percent_spCompGeneric_clear.cpp
    sci_gateway/cpp/sci_percent_spCompGeneric_e.cpp
    sci_gateway/cpp/sci_spCompHessian.cpp
    sci_gateway/cpp/sci_spCompJacobian.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/src/Utilities
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/src/Recovery
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/src/GeneralGraphColoring
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/src/BipartiteGraphPartialColoring
    ${CMAKE_CURRENT_SOURCE_DIR}/src/ColPack/src/BipartiteGraphBicoloring
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    /opt/homebrew/opt/eigen/include/eigen3)
