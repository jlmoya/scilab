# ScilabClasspath.cmake — generate etc/classpath.xml from etc/classpath.xml.in
#
# etc/classpath.xml is Scilab's RUNTIME classloader manifest: native C reads it at
# JVM startup and honours its lazy `load="onUse"` triggers and `disableUnderMode`
# gating. The migration keeps it as the runtime mechanism (build-cmake-maven-
# migration.md §2a) but retires its machine-specificity: configure used to
# substitute the `@FLEXDOCK@`-style tokens with ABSOLUTE thirdparty jar paths, so
# the file only worked on the machine it was configured on (register B9). Nothing
# in the CMake build regenerated it — the on-disk copy was frozen configure output.
#
# This regenerates it DYNAMICALLY at configure time from whatever fetch-thirdparty.sh
# actually placed in thirdparty/, rooting every jar at the `$SCILAB` token instead of
# an absolute path. LoadClassPath.java expands `$SCILAB` -> the `$SCI` env var at load
# time, so one set of bytes now works in the dev tree AND in any install prefix — the
# file drops out of the ScilabInstall.cmake path-relocation set, and a fresh clone
# gets a correct classpath.xml the moment thirdparty/ is populated and CMake runs.
#
# The .in stays the STRUCTURAL template: load order, `load="onUse"`, per-mode
# `<load on=...>` triggers and `disableUnderMode` are all preserved verbatim. Only the
# thirdparty `<path value>` targets are resolved here; the module-jar entries the .in
# already roots at `$SCILAB/modules/<m>/target/...` pass through configure_file
# untouched (they are `$SCILAB`, not `@TOKEN@`).
#
# Each token maps to a version-AGNOSTIC glob (a fetch-thirdparty.sh version bump flows
# through with no edit here); `[0-9]` after a trailing `-` keeps a base jar from
# swallowing a longer-prefixed sibling (lwjgl vs lwjgl-vulkan/-jawt, freehep-graphicsio
# vs -emf, jlatexmath vs -fop/-font-*, jakarta.activation vs -api, jogl-all/gluegen-rt
# vs their -natives- packs). natives/sources/javadoc variants are filtered out, and
# EXACTLY one jar must survive or configuration fails loudly naming the token — so a
# renamed, removed or duplicated dependency is caught here, not at Scilab startup.
# A couple of tokens resolve to a jar whose name does not match the token by design and
# is reproduced faithfully: @LUCENE_ANALYZER@ -> lucene-core (the analyzers folded into
# core in Lucene 9), @JAXB_RUNTIME@ -> jaxb-api. @SCIRENDERER_CP@ is the sole
# non-thirdparty token — scirenderer is a Maven MODULE, so it points at its build output.

# token|thirdparty-glob (validated 1:1 against thirdparty/ — see the B9 work log)
set(_SCILAB_CLASSPATH_TP_MAP
  "FLEXDOCK|flexdock-*.jar"
  "SKINLF|skinlf-*.jar"
  "LOOKS|jgoodies-looks-*.jar"
  "COMMONS_LOGGING|commons-logging-*.jar"
  "JHALL|jhall-*.jar"
  "LUCENE|lucene-core-*.jar"
  "LUCENE_ANALYZER|lucene-core-*.jar"
  "LUCENE_PARSER|lucene-queryparser-*.jar"
  "JAVAFX_BASE|javafx.base.jar"
  "JAVAFX_SWING|javafx.swing.jar"
  "JAVAFX_GRAPHICS|javafx.graphics.jar"
  "JCEF|jcef-api.jar"
  "GSON|gson-*.jar"
  # A plain glob is safe here because fetch-thirdparty.sh deletes every flatlaf jar
  # except its pinned one -- the prerequirements tarball ships a superseded 3.4.1
  # that would otherwise make this match two and trip the 1-jar assertion below.
  "FLATLAF|flatlaf-*.jar"
  "JEDITERM_CORE|jediterm-core-*.jar"
  "JEDITERM_UI|jediterm-ui-*.jar"
  "LWJGL|lwjgl-[0-9]*.jar"
  "LWJGL_VULKAN|lwjgl-vulkan-*.jar"
  "LWJGL_JAWT|lwjgl-jawt-*.jar"
  "LWJGL3_AWT|lwjgl3-awt-*.jar"
  "SWING_GPU_SURFACE|swing-gpu-surface-*.jar"
  "KOTLIN_STDLIB|kotlin-stdlib-*.jar"
  "JNA|jna-*.jar"
  "SLF4J_API|slf4j-api-*.jar"
  "ANNOTATIONS|annotations-*.jar"
  "DIRECTORY_WATCHER|directory-watcher-*.jar"
  "FREEHEP_IO|freehep-io-*.jar"
  "FREEHEP_GRAPHICSBASE|freehep-graphicsbase-*.jar"
  "FREEHEP_GRAPHICSIO|freehep-graphicsio-[0-9]*.jar"
  "FREEHEP_GRAPHICSIO_EMF|freehep-graphicsio-emf-*.jar"
  "FREEHEP_GRAPHICS2D|freehep-graphics2d-*.jar"
  "JROSETTA_API|jrosetta-API-*.jar"
  "JROSETTA_ENGINE|jrosetta-engine-*.jar"
  "JGRAPHX|jgraphx-*.jar"
  "JAXB_RUNTIME|jaxb-api-*.jar"
  "JAXB_IMPL|jaxb-impl-*.jar"
  "ACTIVATION|javax.activation-api-*.jar"
  "ISTACK_COMMONS_RUNTIME|istack-commons-runtime-*.jar"
  "JAKARTA_ACTIVATION|jakarta.activation-[0-9]*.jar"
  "JAKARTA_ACTIVATION_API|jakarta.activation-api-*.jar"
  "JOGL2|jogl-all-[0-9]*.jar"
  "GLUEGEN2_RT|gluegen-rt-[0-9]*.jar"
  "JEUCLID_CORE|jeuclid-core-*.jar"
  "JLATEXMATH_FOP|jlatexmath-fop-*.jar"
  "FOP_CORE|fop-core-*.jar"
  "FOP_EVENT|fop-events-*.jar"
  "FOP_UTIL|fop-util-*.jar"
  "BATIK|batik-all-*.jar"
  "XML_APIS_EXT|xml-apis-ext-*.jar"
  "COMMONS_IO|commons-io-*.jar"
  "SAXON|Saxon-HE-*.jar"
  "HTTPCORE|httpcore5-*.jar"
  "HTTPCLIENT|httpclient5-*.jar"
  "XMLRESOLVER|xmlresolver-*.jar"
  "XMLGRAPHICS_COMMONS|xmlgraphics-commons-*.jar"
  "AVALON_FRAMEWORK|avalon-framework-*.jar"
  "JLATEXMATH|jlatexmath-[0-9]*.jar"
  "JLATEXMATH_FONT_CYRILLIC|jlatexmath-font-cyrillic-*.jar"
  "JLATEXMATH_FONT_GREEK|jlatexmath-font-greek-*.jar"
  "ECJ|ecj-*.jar"
)

# scilab_generate_classpath(<source-tree-root>)
# Resolves the thirdparty tokens against <root>/thirdparty and writes
# <root>/etc/classpath.xml from <root>/etc/classpath.xml.in.
function(scilab_generate_classpath _root)
  set(_tp "${_root}/thirdparty")
  if(NOT IS_DIRECTORY "${_tp}")
    message(FATAL_ERROR "classpath.xml: thirdparty dir '${_tp}' is missing — run fetch-thirdparty.sh before configuring")
  endif()
  foreach(_entry IN LISTS _SCILAB_CLASSPATH_TP_MAP)
    string(REPLACE "|" ";" _pair "${_entry}")
    list(GET _pair 0 _tok)
    list(GET _pair 1 _pat)
    file(GLOB _hits RELATIVE "${_tp}" "${_tp}/${_pat}")
    list(FILTER _hits EXCLUDE REGEX "-natives-|-sources|-javadoc")
    list(LENGTH _hits _n)
    if(NOT _n EQUAL 1)
      message(FATAL_ERROR
        "classpath.xml: token @${_tok}@ glob '${_pat}' matched ${_n} jars in ${_tp} "
        "(need exactly 1): '${_hits}'. A dependency was renamed, removed, or duplicated — "
        "fix fetch-thirdparty.sh or this token's pattern in ScilabClasspath.cmake.")
    endif()
    # literal $SCILAB (NOT ${SCILAB}) — LoadClassPath.java expands it at runtime
    set(${_tok} "$SCILAB/thirdparty/${_hits}")
  endforeach()
  # scirenderer is a Maven module, not a thirdparty jar
  set(SCIRENDERER_CP "$SCILAB/modules/scirenderer/target/scirenderer.jar")
  configure_file("${_root}/etc/classpath.xml.in" "${_root}/etc/classpath.xml" @ONLY)
  message(STATUS "Generated etc/classpath.xml (relocatable, \$SCILAB-rooted) from ${_tp}")
endfunction()
