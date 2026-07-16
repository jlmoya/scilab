# CMake build for the `tclsci` module — Batch B (variant pair) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools tclsci is always recoverable with
# `make -C modules/tclsci`.
#
# VARIANT PAIR — TWO scilab_module() calls: the MAIN dylib and the `-disable`
# stub (second pkglib dylib, loaded in nogui/disabled mode). Neither links the
# other (otool -L on both baseline dylibs shows NO libsci edge — they are
# mutually exclusive alternates), so no MODULE_DEPS on either call.
#
# LINKING CLASS (modules/Makefile.am): main = GUI_LIBS. The -disable stub is
# NO_GUI_LIBS, which is EMPTY on macOS (`if IS_MACOSX`): the stub is built +
# installed but linked into NO aggregate. The helper's CLASS keyword has no
# NO_GUI_LIBS value (and Batch B adds no helper features), so the stub call
# omits CLASS — this comment is the record.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   libscitclsci.2027.dylib          exports 11;  deps libSystem only (C-driver link)
#   libscitclsci-disable.2027.dylib  exports 11;  deps libc++ + libSystem
#   install_names: /usr/local/lib/scilab/<that filename>  (+ unversioned symlinks)
#
# TCLTK=off in the baseline config: the MAIN dylib is built from the SAME
# single stub source as the -disable variant (src/notclsci/notclsci.cpp; the
# real TCLSCI_C_SOURCES only build under `if TCLTK`). In automake the main's
# la_SOURCES is EMPTY (the stub rides in via the libscitclsci-algo convenience
# lib), so libtool linked the MAIN with the C tag: baseline deps are
# libSystem ONLY — no libc++ — even though the TU is C++ (notclsci.cpp is
# extern-C code with zero undefined C++-runtime refs, so the C-driver link
# succeeds). The -disable la lists the .cpp in its OWN SOURCES -> C++ tag ->
# it DOES record libc++. Transcription: main gets LANG C + an explicit
# LINKER_LANGUAGE C override below (CMake would otherwise infer CXX from the
# .cpp and add libc++); the variant gets LANG CXX. Exports of the two dylibs
# are IDENTICAL (11 symbols, nm-diff-verified).
# The helper needs GATEWAY_SOURCES, so the stub sits there (algo lib folded:
# OBJECT-lib vs direct compile is object-identical — same includes/flags).

scilab_module(tclsci
  GATEWAY_SOURCES
    src/notclsci/notclsci.cpp
  LANG C
  EXTRA_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}/sci_gateway/c
    ${SCILAB_SOURCE_DIR}/modules/action_binding/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
  CLASS GUI_LIBS
  SYMBOLS 11)

# libtool linked the main with the C tag (empty la_SOURCES) -> NO libc++
# in the baseline deps. CMake infers CXX from the .cpp source, so pin the
# C link rule explicitly (undefined dynamic_lookup covers any stray refs;
# the TU has none).
set_target_properties(scitclsci PROPERTIES LINKER_LANGUAGE C)
# ... and stop CMake's mixed-language propagation from appending -lc++ anyway
# (a C-linked target containing CXX objects gets CMAKE_CXX_IMPLICIT_LINK_LIBRARIES
# added to the link line — libtool's C tag added nothing). Directory-scoped:
# it only affects languages OTHER than a target's link language, so the
# CXX-LINKED -disable variant below still records libc++ (from the g++ driver
# itself), exactly like the baseline.
set(CMAKE_CXX_IMPLICIT_LINK_LIBRARIES "")

scilab_module(tclsci-disable
  GATEWAY_SOURCES
    src/notclsci/notclsci.cpp
  LANG CXX
  SYMBOLS 11)
