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
# No JVM, no jars: Makefile.am:246's `macros:` rule lists check-jvm-dep (asserts
# scilab-cli-bin has NO libjvm dependency) and check-libstdcpp-dep as PREREQUISITES,
# both dropped here rather than reproduced. Depending on the Java build here would
# invent a prerequisite autotools does not have -- the inverse mistake. Both checks
# are dormant no-ops on macOS today (check-jvm-dep's body is `if !IS_MACOSX`, entirely
# absent here; check-libstdcpp-dep's is `if USE_STATIC_SYSTEM_LIB`, which this configure
# leaves false, so it only echoes "libstdc++ presence test skipped") -- worth reproducing
# before this driver ever targets Linux, where USE_STATIC_SYSTEM_LIB can be true and the
# check real.
#
# DELIBERATE DIVERGENCE FROM autotools -- this target FAILS LOUDLY.
# Makefile.am:247 prefixes its recipe with `-`, so make IGNORES the exit status:
# a failed macros pass prints "Error 1 (ignored)" and the build continues, and
# nothing downstream re-validates completeness. That is not hypothetical -- it
# shipped as the rc=231 bug, FIXED in commit 7303c43690e ("toolbox_manager: add the
# standard per-module buildmacros.sce (fixes the rc=231 exit)"): one module lacked
# its macros/buildmacros.sce, the unguarded exec failed, scilab-cli exited non-zero
# after building every other library fine, and make swallowed it. The migration's
# mandate is to reproduce the ARTIFACT, not to inherit a swallow-the-error habit
# into a build system that never had it. CMake propagates the failure.
#
# OPT-IN, not on drop-in-all -- like the 1f-c `doc` target, this needs a fully
# built interpreter. A totally unbuilt tree fails at exec with libtool's own
# wrapper diagnostic ("... does not exist. This script is just a wrapper for
# scilab-cli-bin. See the libtool documentation ...") -- clear and on-topic,
# not a CMake-looking failure (an earlier version of this comment claimed
# otherwise). That total-miss case is self-explanatory; the case that
# actually deserves worry is a STALE-but-present interpreter -- see DEPENDS
# ON A FRESH INTERPRETER below.
#
# DEPENDS ON A FRESH INTERPRETER, wired in CMakeLists.txt, not here.
# Without a dependency, this target has no idea whether bin/scilab-cli wraps a
# stale binary: edit modules/ast or modules/startup, forget `--target
# drop-in-all`, run `--target macros` directly, and CMake execs whatever is
# already in .libs/ with zero rebuild steps -- .bin files compiled by a stale
# interpreter, rc=0, no diagnostic. That is the same silent-divergence family
# the FAILS LOUDLY design above guards against, just via interpreter
# freshness rather than exit-status swallowing. The fix is
# `add_dependencies(macros drop-in-scilab-cli-bin)`, placed in CMakeLists.txt
# right after the scilab_executable(scilab-cli-bin ...) declaration: that
# target -- declared by scilab_executable() in cmake/ScilabAggregate.cmake,
# which compiles, links, and copies the binary into .libs/ -- does not exist
# yet when this file is include()'d earlier in CMakeLists.txt, so the
# dependency cannot be wired here without a configure-time "target not found"
# error. Do not read this file's lack of DEPENDS/add_dependencies() as the
# dependency being missing -- it is enforced, just from the other file.
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
