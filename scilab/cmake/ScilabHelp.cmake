# scilab/cmake/ScilabHelp.cmake — the help build as a CMake post-step (Stage 1f-c).
#
# `make doc` runs the BUILT scilab-adv-cli HEADLESS per locale (xmltojar) — help needs the
# running app (the circular dep), so this is a post-link, opt-in (BUILD_HELP-gated) target,
# NOT on drop-in-all. Reproduces the top-level Makefile's `doc:` recipe env + command
# EXACTLY — _JAVA_OPTIONS below is `-Djava.awt.headless=true $(DOC_JAVA_XML_OPTS)` with
# DOC_JAVA_XML_OPTS transcribed, expanded, from the configured Makefile (the seven
# -Djdk.xml.*Limit/Depth=0 lifts that let the DocBook pipeline swallow Scilab's help XML).

# BUILD_HELP gate, from config.status (automake conditional: _TRUE="" = on, "#" = off).
# Reuses _scilab_parse_am_conditional (cmake/ScilabJava.cmake, included before this file
# from CMakeLists.txt) for its missing/format-drift FATAL guard — a silently-absent line
# must not read as conditional-ON.
_scilab_parse_am_conditional(BUILD_HELP_TRUE SCILAB_BUILD_HELP)

# The doc locales: ALL_LINGUAS_DOC is a plain substituted value ("en_US fr_FR …"), not a
# conditional — same file(STRINGS …)+guard pattern: a missing or format-drifted line fails
# loudly instead of silently yielding zero locales. (A parsable-but-EMPTY value is legal:
# it means no doc locales, and the foreach below just produces an empty target.)
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ll REGEX "^S\\[\"ALL_LINGUAS_DOC\"\\]=")
if(NOT _sci_ll MATCHES "^S\\[\"ALL_LINGUAS_DOC\"\\]=\"(.*)\"$")
  message(FATAL_ERROR "config.status has no parsable S[\"ALL_LINGUAS_DOC\"] line "
                      "(got '${_sci_ll}') — cannot enumerate the help locales; "
                      "re-run ./configure or update the parse in cmake/ScilabHelp.cmake")
endif()
set(_sci_doc_langs "${CMAKE_MATCH_1}")
separate_arguments(_sci_doc_langs)   # "en_US fr_FR …" -> a CMake list

function(scilab_help_target)
  if(NOT SCILAB_BUILD_HELP STREQUAL "")
    add_custom_target(doc COMMENT "Help disabled (BUILD_HELP off — ./configure --enable-build-help)")
    return()
  endif()
  set(_cmds "")
  foreach(l ${_sci_doc_langs})
    # Per-locale env + command, byte-for-byte from the configured Makefile's `doc:` recipe
    # (incl. the expanded DOC_JAVA_XML_OPTS in _JAVA_OPTIONS). The -e payload's semicolons
    # are \;-escaped so the string rides the _cmds list as ONE element; with VERBATIM below,
    # the generator hands it to the shell as ONE quoted word — exactly the Makefile's
    # -e "$COMMAND". (Unescaped, the payload silently shatters into separate argv entries.)
    list(APPEND _cmds COMMAND ${CMAKE_COMMAND} -E env
         LANG=${l}.UTF-8 LC_ALL=C.UTF-8 SCI_DISABLE_TK=1 SCI_JAVA_ENABLE_HEADLESS=1
         "_JAVA_OPTIONS=-Djava.awt.headless=true -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 -Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.entityReplacementLimit=0 -Djdk.xml.maxParameterEntitySizeLimit=0 -Djdk.xml.elementAttributeLimit=0 -Djdk.xml.maxElementDepth=0"
         HOME=/tmp
         ${SCILAB_SOURCE_DIR}/bin/scilab-adv-cli -noatomsautoload -nb -l ${l} -nouserstartup
         -e "try xmltojar([],[],'${l}')\;catch disp(lasterror())\; exit(-1)\;end\;exit(0)\;")
  endforeach()
  # VERBATIM is load-bearing: without it the Makefile generator leaves the payload's
  # ( ) ; bare in the recipe — a shell syntax error the moment the target actually runs.
  add_custom_target(doc ${_cmds}
    WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}
    USES_TERMINAL
    VERBATIM
    COMMENT "Building Scilab help (xmltojar) per locale via the built scilab-adv-cli")
endfunction()
