# CMake build for the `matio` module — Batch C (external deps) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools matio is always recoverable with `make -C modules/matio`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD (dlopen-only).
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscimatio.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscimatio.2027.dylib
#   exports:       exactly 38 symbols (incl. 11 C++-mangled from the algo lib)
#   deps:          /usr/lib/libz.1.dylib
#                  /opt/homebrew/opt/libmatio/lib/libmatio.14.dylib
#                  /opt/homebrew/opt/hdf5/lib/libhdf5.320.dylib
#                  /usr/lib/libSystem.B.dylib   — NO libc++ (see below).
#
# C-DRIVER LINK DESPITE C++ OBJECTS: libscimatio_la_SOURCES is the 5 pure-C
# gateways only (the Makefile.am's GATEWAY_CPP_SOURCES ref is undefined/empty),
# so automake links the pkglib with --tag=CC (the C driver — no -lc++); the
# folded algo objects' libc++ undefineds resolve at load time through
# -undefined dynamic_lookup, exactly like the Scilab API symbols. LANG C CXX
# would make the helper pin the C++ linker, so the post-call
# set_target_properties below overrides LINKER_LANGUAGE back to C — the
# faithful reproduction of --tag=CC. But the gcc driver alone is not enough:
# because the target still CONTAINS C++ objects (the algo lib), CMake appends
# the CXX-minus-C implicit-runtime delta (`-lc++`) to the link line on its own,
# which would record a libc++ dep the baseline does not have. Emptying
# CMAKE_CXX_IMPLICIT_LINK_LIBRARIES in THIS directory scope (matio is its own
# add_subdirectory — no leak to siblings) drops that delta, so the C driver +
# no explicit -lc++ reproduce --tag=CC's dep set exactly (libc++ symbols still
# resolve at load time via dynamic_lookup, like the baseline).
set(CMAKE_CXX_IMPLICIT_LINK_LIBRARIES "")
#
# EXTERNAL DEPS: MATIO_LIBS = -L/opt/homebrew/opt/libmatio/lib -lm -lz -lmatio
# -lhdf5 (configured; -lhdf5 resolved via the global LDFLAGS' -L/opt/homebrew/lib).
# -lz stays a plain name (SDK -> /usr/lib/libz.1.dylib); matio + hdf5 pinned as
# absolute keg paths (install_name = the baseline path); -lm folds into
# libSystem (omitted, like the exemplars).

scilab_module(matio
  ALGO_SOURCES
    src/c/matfile_manager.c
    src/c/CreateMatlabVariable.c
    src/c/CreateBooleanVariable.c
    src/c/CreateCharVariable.c
    src/c/CreateDoubleVariable.c
    src/c/CreateIntegerVariable.c
    src/c/CreateSparseVariable.c
    src/c/GetMatlabVariable.c
    src/cpp/CreateCellVariable.cpp
    src/cpp/CreateHyperMatrixVariable.cpp
    src/cpp/CreateMatlabTreeVariable.cpp
    src/cpp/GetCellVariable.cpp
    src/cpp/GetStructVariable.cpp
    src/cpp/CreateStructVariable.cpp
    src/cpp/ConvertSciVarToMatVar.cpp
    src/cpp/GetIntegerVariable.cpp
    src/cpp/GetBooleanVariable.cpp
    src/cpp/GetCharVariable.cpp
    src/cpp/GetDoubleVariable.cpp
    src/cpp/GetSparseVariable.cpp
    src/cpp/GetMlistVariable.cpp
  GATEWAY_SOURCES
    sci_gateway/c/sci_matfile_open.c
    sci_gateway/c/sci_matfile_close.c
    sci_gateway/c/sci_matfile_listvar.c
    sci_gateway/c/sci_matfile_varreadnext.c
    sci_gateway/c/sci_matfile_varwrite.c
  LANG C CXX
  SYSTEM_LIBS
    z
    /opt/homebrew/opt/libmatio/lib/libmatio.dylib
    /opt/homebrew/opt/hdf5/lib/libhdf5.dylib
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    /opt/homebrew/opt/libmatio/include
  CLASS DYNAMIC_LOAD
  SYMBOLS 38)

# automake's --tag=CC link, verbatim (rationale in the header comment).
set_target_properties(scimatio PROPERTIES LINKER_LANGUAGE C)
