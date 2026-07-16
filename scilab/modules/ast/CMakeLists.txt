# CMake build for the `ast` module — Batch A (simple leaf) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools ast is always recoverable with `make -C modules/ast`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciast.2027.dylib (+ libsciast.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libsciast.2027.dylib
#   exports:       exactly 7290 symbols (the interpreter core: types::*,
#                  ast::*, symbol::*, the operations kernels, dtoa, parser)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf.
#
# Sources: ast has NO noinst algo/gateway split — libsciast_la_SOURCES carries
# everything on the pkglib directly, so the whole list is GATEWAY_SOURCES
# (100 sources verbatim from Makefile.am, in order) + src/cpp/parse/fmemopen.c
# (am__append_2: IS_MACOSX && !IS_SYS_FMEMOPEN in the configured Makefile).
# NOT compiled (per the configured Makefile): src/cpp/types/inspector.cpp
# (am__append_1, ENABLE_DEBUG=off) and -DHAVE_SYS_FMEMOPEN (am__append_3, off).
# All 7290 exports rely on the helper's no-hidden-visibility default.
#
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libsciast_la_CPPFLAGS: functions_manager, console, elementary_functions,
# string, threads, io, fileio/{includes,src/c}, coverage, graphic_objects —
# plus EIGEN_CPPFLAGS (/opt/homebrew/opt/eigen/include/eigen3; sparse.cpp &
# types_power.cpp use Eigen). The module-local -I set (includes/* subdirs,
# src/cpp) is already covered by SCILAB_DEFAULT_INCLUDES + the helper's
# module-local dirs. MACHINE-CONFIG-SPECIFIC: the eigen keg path is
# configure-detected on this machine.

scilab_module(ast
  GATEWAY_SOURCES
    src/c/operations/doublecomplex.c
    src/c/operations/invert_matrix.c
    src/c/operations/matrix_addition.c
    src/c/operations/matrix_division.c
    src/c/operations/matrix_finite.c
    src/c/operations/matrix_kronecker.c
    src/c/operations/matrix_multiplication.c
    src/c/operations/matrix_power.c
    src/c/operations/matrix_transpose.c
    src/c/operations/operations_tools.c
    src/c/types/dtoa.c
    src/cpp/ast/expHistory.cpp
    src/cpp/ast/macrovarvisitor.cpp
    src/cpp/ast/printvisitor.cpp
    src/cpp/ast/runvisitor.cpp
    src/cpp/ast/shortcutvisitor.cpp
    src/cpp/ast/treevisitor.cpp
    src/cpp/ast/visitor_common.cpp
    src/cpp/ast/consoledebugger.cpp
    src/cpp/ast/debuggervisitor.cpp
    src/cpp/ast/debugmanager.cpp
    src/cpp/ast/prettyprintvisitor.cpp
    src/cpp/operations/interface_addition.cpp
    src/cpp/operations/operations.cpp
    src/cpp/operations/types_addition.cpp
    src/cpp/operations/types_and.cpp
    src/cpp/operations/types_comparison_eq.cpp
    src/cpp/operations/types_comparison_lt_le_gt_ge.cpp
    src/cpp/operations/types_comparison_ne.cpp
    src/cpp/operations/types_divide.cpp
    src/cpp/operations/types_dotdivide.cpp
    src/cpp/operations/types_dotmultiplication.cpp
    src/cpp/operations/types_finite.cpp
    src/cpp/operations/types_kronecker.cpp
    src/cpp/operations/types_ldivide.cpp
    src/cpp/operations/types_multiplication.cpp
    src/cpp/operations/types_opposite.cpp
    src/cpp/operations/types_or.cpp
    src/cpp/operations/types_power.cpp
    src/cpp/operations/types_subtraction.cpp
    src/cpp/parse/parser.cpp
    src/cpp/parse/parsescilab.cpp
    src/cpp/parse/printerror.cpp
    src/cpp/parse/scanscilab.cpp
    src/cpp/symbol/context.cpp
    src/cpp/symbol/libraries.cpp
    src/cpp/symbol/symbol.cpp
    src/cpp/symbol/variables.cpp
    src/cpp/system_env/configvariable.cpp
    src/cpp/system_env/configvariable_interface.cpp
    src/cpp/system_env/context_get.cpp
    src/cpp/system_env/dynamic_module.cpp
    src/cpp/system_env/home.cpp
    src/cpp/system_env/lasterror.cpp
    src/cpp/system_env/sci_home.cpp
    src/cpp/system_env/sci_path.cpp
    src/cpp/system_env/sci_tmpdir.cpp
    src/cpp/system_env/setenvvar.cpp
    src/cpp/system_env/threadmanagement.cpp
    src/cpp/system_env/numericconstants.cpp
    src/cpp/system_env/numericconstants_interface.cpp
    src/cpp/system_env/coverage_instance.cpp
    src/cpp/types/addfunction.cpp
    src/cpp/types/internal.cpp
    src/cpp/types/arrayof.cpp
    src/cpp/types/arguments.cpp
    src/cpp/types/bool.cpp
    src/cpp/types/callable.cpp
    src/cpp/types/cell.cpp
    src/cpp/types/double.cpp
    src/cpp/types/file.cpp
    src/cpp/types/function.cpp
    src/cpp/types/classdef.cpp
    src/cpp/types/object.cpp
    src/cpp/types/objectmethod.cpp
    src/cpp/types/graphichandle.cpp
    src/cpp/types/implicitlist.cpp
    src/cpp/types/int.cpp
    src/cpp/types/library.cpp
    src/cpp/types/list.cpp
    src/cpp/types/listdelete.cpp
    src/cpp/types/listinsert.cpp
    src/cpp/types/listoperation.cpp
    src/cpp/types/macro.cpp
    src/cpp/types/macrofile.cpp
    src/cpp/types/mlist.cpp
    src/cpp/types/overload.cpp
    src/cpp/types/polynom.cpp
    src/cpp/types/singlepoly.cpp
    src/cpp/types/singlestruct.cpp
    src/cpp/types/sparse.cpp
    src/cpp/types/string.cpp
    src/cpp/types/struct.cpp
    src/cpp/types/test-types.cpp
    src/cpp/types/threadId.cpp
    src/cpp/types/tlist.cpp
    src/cpp/types/tostring_common.cpp
    src/cpp/types/types.cpp
    src/cpp/types/types_tools.cpp
    src/cpp/types/void.cpp
    src/cpp/parse/fmemopen.c
  LANG CXX C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/functions_manager/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/src/c
    ${SCILAB_SOURCE_DIR}/modules/coverage/includes
    ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
    /opt/homebrew/opt/eigen/include/eigen3
  CLASS ENGINE_LIBS
  SYMBOLS 7290)
