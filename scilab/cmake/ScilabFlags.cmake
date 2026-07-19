# scilab/cmake/ScilabFlags.cmake -- the compiler-flag POLICY, computed (retire-configure RC-b).
#
# Replaces the hand-transcribed literal lists that used to live in
# _scilab_module_flag_env(). CMake states the policy itself; it never reads
# config.status. The parity gate (parity/flagfacts_check.py, expectations DERIVED
# from the autotools Makefiles) is what proves the computed values still match.
#
# TRAP, learned the hard way: -std= is NOT part of SCI_CFLAGS/SCI_CXXFLAGS. autotools
# carries it in the COMPILER variable -- `CC = gcc -std=gnu23 -arch arm64`,
# `CXX = g++ -arch arm64 -std=c++17`. A port that mirrors only SCI_*FLAGS silently
# drops the language standard. It is set explicitly below.

option(SCILAB_ENABLE_DEBUG "Build unoptimized with full debug info (configure's --enable-debug)" OFF)

# configure.ac:467,562,588,671,757 -- five sites, one policy:
#   enable_debug ? "-O0 -g3" : "-DNDEBUG -g1 -O2 -fwrapv"
# -fwrapv and NOT -fno-strict-overflow: clang expands the latter to -fwrapv-pointer
# too and blows up compile time on template-heavy TUs (configure.ac:671,757).
if(SCILAB_ENABLE_DEBUG)
  set(_codegen -O0 -g3)
else()
  set(_codegen -DNDEBUG -g1 -O2 -fwrapv)
endif()

# Derived, not baked: the deployment target is already CMake's own.
set(_min_macos -mmacosx-version-min=${CMAKE_OSX_DEPLOYMENT_TARGET})

# configure.ac:682,768,573,599 -- bug 3131.
set(_compiler_c -fno-stack-protector)
set(_compiler_cxx -fno-stack-protector)

# configure.ac:674,760 (C) and :565,591 (C++). Fortran gets NO warning flags:
# WARNING_FFLAGS has zero assignment sites anywhere in configure.ac/m4.
set(_warn_c -Wall -Wpedantic)
set(_warn_cxx -Wall -Wpedantic)
# configure.ac:2358-2360 -- unconditional and C-ONLY; C++ never gets these.
list(APPEND _warn_c -Werror=implicit -Werror=incompatible-pointer-types)

set(SCILAB_C_FLAGS       -std=gnu23  ${_codegen} ${_min_macos} ${_compiler_c}   ${_warn_c})
set(SCILAB_CXX_FLAGS     -std=c++17  ${_codegen} ${_min_macos} ${_compiler_cxx} ${_warn_cxx})
set(SCILAB_Fortran_FLAGS             ${_codegen} ${_min_macos})
set(SCILAB_LINK_FLAGS    ${_min_macos})

# NOT implemented, on purpose -- each verified rather than assumed:
#  * SCI_CPPFLAGS is a PHANTOM: Makefile.incl.am:25 and Makefile.am:27 both do
#    AM_CPPFLAGS = $(SCI_CPPFLAGS), but nothing assigns or AC_SUBSTs it anywhere
#    (verified absent from config.status). It always expands empty.
#  * WARNING_FFLAGS / DEBUG_LDFLAGS / WARNING_LDFLAGS / SSE_LDFLAGS /
#    BACKTRACE_LDFLAGS: zero assignment sites anywhere. Dead everywhere.
#  * COMPILER_FFLAGS: dead HERE but not dead everywhere -- assigned only on the
#    Intel-compiler path (m4/intel_compiler.m4:28,30), which this build never takes.
#  * SSE_*FLAGS: i*86-linux-gnu only (configure.ac:869-875). BACKTRACE_*FLAGS: gated
#    on a glibc-backtrace probe (m4/backtrace.m4) that fails on macOS, contributing
#    no -rdynamic here.
