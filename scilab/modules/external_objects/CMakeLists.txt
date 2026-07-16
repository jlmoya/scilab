# CMake build for the `external_objects` module — Batch A (simple leaf) on the
# shared scilab_module() helper (cmake/ScilabModule.cmake); built from the
# top-level scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools external_objects is always recoverable
# with `make -C modules/external_objects`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS. (This is the language-
# agnostic external-objects core; the JDK-linking twin external_objects_java
# is a separate Batch-E module.)
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libsciexternal_objects.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libsciexternal_objects.2027.dylib
#   exports:       exactly 126 symbols (ScilabEnvironments/ScilabObjects/
#                  ScilabGateway<> instantiations + the %EObj overload gateways)
#   deps:          /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib — leaf
#                  (the Makefile.am's $(X_LIBS) $(X_EXTRA_LIBS) LIBADD resolves
#                  EMPTY in the configured Makefile — nothing to link).
#
# Sources: EXTERNAL_OBJECTS_{JNI,C}_SOURCES are EMPTY; the noinst algo lib is
# the 49 EXTERNAL_OBJECTS_CPP_SOURCES, the gateway the 32 GATEWAY_CPP_SOURCES
# (GATEWAY_C_SOURCES is undefined) — verbatim from Makefile.am. Pure C++.
# (libsciexternal_objects_la_CFLAGS exists for C TUs, but no C TU compiles —
# irrelevant to parity.)
# EXTRA_INCLUDES beyond SCILAB_DEFAULT_INCLUDES, from the configured
# libsciexternal_objects_la_CPPFLAGS: string, fileio, core/src/c, threads,
# console.

scilab_module(external_objects
  ALGO_SOURCES
    src/cpp/ScilabEnvironments.cpp
    src/cpp/ScilabObjects.cpp
    src/cpp/OptionsHelper.cpp
    src/cpp/addToClasspath.cpp
    src/cpp/getClasspath.cpp
    src/cpp/getsetOptions.cpp
    src/cpp/getEnvironmentInfos.cpp
    src/cpp/array.cpp
    src/cpp/operation.cpp
    src/cpp/cast.cpp
    src/cpp/compile.cpp
    src/cpp/convMatrixMethod.cpp
    src/cpp/deff.cpp
    src/cpp/display.cpp
    src/cpp/clear.cpp
    src/cpp/doubleExclam_invoke.cpp
    src/cpp/classExtract.cpp
    src/cpp/objectExtract.cpp
    src/cpp/objectInsert.cpp
    src/cpp/objectPrint.cpp
    src/cpp/exists.cpp
    src/cpp/getClassName.cpp
    src/cpp/getEnvironmentName.cpp
    src/cpp/getField.cpp
    src/cpp/getFields.cpp
    src/cpp/getMethods.cpp
    src/cpp/getRepresentation.cpp
    src/cpp/import.cpp
    src/cpp/invoke.cpp
    src/cpp/invoke_asref.cpp
    src/cpp/invoke_lu.cpp
    src/cpp/trace.cpp
    src/cpp/newInstance.cpp
    src/cpp/remove.cpp
    src/cpp/setField.cpp
    src/cpp/evalString.cpp
    src/cpp/addNamedVariable.cpp
    src/cpp/getNamedVariable.cpp
    src/cpp/unwrap.cpp
    src/cpp/wrap.cpp
    src/cpp/unwrapremove.cpp
    src/cpp/wrapAsRef.cpp
    src/cpp/invoker.cpp
    src/cpp/getEnvId.cpp
    src/cpp/ScilabOptionsSetter.cpp
    src/cpp/ScilabAutoCleaner.cpp
    src/cpp/ExternalObjectsFunction.cpp
    src/cpp/wrapInFloat.cpp
    src/cpp/EOType.cpp
  GATEWAY_SOURCES
    sci_gateway/cpp/sci_percent_EObj_e.cpp
    sci_gateway/cpp/sci_percent_EObj_p.cpp
    sci_gateway/cpp/sci_percent_foo_i_EObj.cpp
    sci_gateway/cpp/sci_percent_EClass_e.cpp
    sci_gateway/cpp/sci_percent_EObj_a_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_d_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_g_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_h_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_j_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_k_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_l_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_m_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_n_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_o_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_p_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_q_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_r_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_s_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_x_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_y_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_z_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_1_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_2_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_3_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_4_foo.cpp
    sci_gateway/cpp/sci_percent_EObj_0.cpp
    sci_gateway/cpp/sci_percent_EObj_5.cpp
    sci_gateway/cpp/sci_percent_EObj_t.cpp
    sci_gateway/cpp/sci_doubleExclam_invoke_.cpp
    sci_gateway/cpp/sci_invoke_lu.cpp
    sci_gateway/cpp/sci_percent_EObj_disp.cpp
    sci_gateway/cpp/sci_percent_EObj_clear.cpp
  LANG CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/core/src/c
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
  CLASS ENGINE_LIBS
  SYMBOLS 126)
