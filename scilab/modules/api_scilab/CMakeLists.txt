# CMake build for the `api_scilab` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — api_scilab ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:sciapi_scilab-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). ONE .la: libsciapi_scilab (19 C++
# TUs; API_SCILAB_C_SOURCES resolves empty on this config).
#
# EXTRA_INCLUDES beyond the helper base (libsciapi_scilab_la_CPPFLAGS):
# call_scilab, elementary_functions, string, threads.

scilab_object_module(api_scilab
  SOURCES
    # libsciapi_scilab.la (19 TUs)
    src/cpp/api_stack_boolean.cpp
    src/cpp/api_stack_boolean_sparse.cpp
    src/cpp/api_stack_common.cpp
    src/cpp/api_stack_double.cpp
    src/cpp/api_stack_int.cpp
    src/cpp/api_stack_list.cpp
    src/cpp/api_stack_poly.cpp
    src/cpp/api_stack_sparse.cpp
    src/cpp/api_stack_string.cpp
    src/cpp/api_stack_pointer.cpp
    src/cpp/api_stack_handle.cpp
    src/cpp/api_stack_error.cpp
    src/cpp/api_stack_hypermat.cpp
    src/cpp/api_stack_optional.cpp
    src/cpp/returnProperty.cpp
    src/cpp/returnPropertyList.cpp
    src/cpp/api_template_safe.cpp
    src/cpp/api_template_unsafe.cpp
    src/cpp/api_error.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/call_scilab/includes
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes)
