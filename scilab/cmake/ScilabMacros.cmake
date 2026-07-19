# scilab/cmake/ScilabMacros.cmake -- the macros build (retire-configure RC-d).
#
# Scilab's ~3,516 macro .bin files are produced by RUNNING the just-built
# interpreter over modules/functions/scripts/buildmacros/buildmacros.sce, which
# loops the modules getmodules() reports and calls the compiled genlib() builtin.
# This target INVOKES that existing machinery; it does not reimplement it.
#
# Scope comes from etc/modules.xml (getmodules() -> ConfigVariable::getModuleList()
# -> FuncManager::AppendModules(), modules/functions_manager/src/cpp/funcmanager.cpp
# :125-233) -- a file RC-c already generates byte-identically and covers in two
# parity dimensions. So this stage inherits proven scope rather than re-deriving
# module enablement.
#
# No JVM, no jars: Makefile.am's own check-jvm-dep asserts scilab-cli-bin has NO
# libjvm dependency. Depending on the Java build here would invent a prerequisite
# autotools does not have.
#
# DELIBERATE DIVERGENCE FROM autotools -- this target FAILS LOUDLY.
# Makefile.am:247 prefixes its recipe with `-`, so make IGNORES the exit status:
# a failed macros pass prints "Error 1 (ignored)" and the build continues, and
# nothing downstream re-validates completeness. That is not hypothetical -- it is
# how the rc=231 bug shipped (commit 7303c43690e: one module lacked its
# macros/buildmacros.sce, the unguarded exec failed, scilab-cli exited non-zero
# after building every other library fine, and make swallowed it). The migration's
# mandate is to reproduce the ARTIFACT, not to inherit a swallow-the-error habit
# into a build system that never had it. CMake propagates the failure.
#
# OPT-IN, not on drop-in-all -- like the 1f-c `doc` target, this needs a fully
# built interpreter. On an unbuilt tree it would fail at exec in a way that reads
# as a CMake bug rather than a missing prerequisite.
#
# NOTE for anyone comparing this against `make macros`: genlib is INCREMENTAL
# (sci_genlib.cpp:263-279 skips a .sci whose md5 matches the previous `lib`
# manifest when its .bin still exists), so a second run over a built tree is a
# no-op. Delete both *.bin AND lib under modules/*/macros/ before comparing.

add_custom_target(macros
  COMMAND ${CMAKE_COMMAND} -E env HOME=/tmp
          ${SCILAB_SOURCE_DIR}/bin/scilab-cli
          -ns -noatomsautoload -nouserstartup -quit
          -f modules/functions/scripts/buildmacros/buildmacros.sce
  WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}
  COMMENT "Building Scilab macros (.sci -> .bin) with the built interpreter"
  VERBATIM)
