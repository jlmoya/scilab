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

function(scilab_help_target)
  if(NOT SCILAB_BUILD_HELP STREQUAL "")
    add_custom_target(doc COMMENT "Help disabled (BUILD_HELP off — ./configure --enable-build-help)")
    return()
  endif()
  # The doc locales: RC-e.2 severs this config.status read too. configure.ac:1875
  # assigns ALL_LINGUAS_DOC="en_US fr_FR pt_BR ja_JP ru_RU" as a plain literal —
  # not a probed/derived value — then AC_SUBSTs it (only inside the
  # enable_build_localization branch, configure.ac:1877-1899) into config.status
  # as S["ALL_LINGUAS_DOC"]. This driver has no --disable-build-localization
  # equivalent (no CMake option gates it — same as ENABLE_GUI/ENABLE_JAVA in
  # cmake/ScilabJava.cmake, which likewise hardcode the one configured tree's
  # answer rather than modeling the autotools --without-* switch), so unlike the
  # config.status read this replaces, there is no "legally absent" case left to
  # degrade for: the list below is unconditional, the same one-time
  # transcription RC-e.1 used for the version triple (cmake/ScilabVersion.cmake).
  # Verified equal to this tree's config.status S["ALL_LINGUAS_DOC"] before the
  # cutover. Bump here (and in configure.ac:1875, until configure.ac is deleted
  # at RC-e.4) if the doc locale set ever changes.
  set(_sci_doc_langs "en_US fr_FR pt_BR ja_JP ru_RU")
  separate_arguments(_sci_doc_langs)   # "en_US fr_FR …" -> a CMake list
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
