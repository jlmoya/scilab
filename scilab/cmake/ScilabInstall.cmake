# scilab/cmake/ScilabInstall.cmake — `cmake --install` support (register §5b).
#
# Autotools' `make install` was retired 2026-07-21 and nothing replaced it, so the
# CMake build had ZERO install() rules and could not be prefix-installed (which is
# what a distro package needs). This restores it:
#
#     cmake --build   build-cmake --target drop-in-all   # (+ macros + doc)
#     cmake --install build-cmake --prefix /opt/scilab-2027
#
# installs a RUNNABLE Scilab at the prefix and rewrites the source-tree paths baked
# into the built text config (etc/classpath.xml carries 59 absolute paths, plus
# jvm_options.xml, the launcher wrappers and *.properties) to the install location.
# That copy+relocate is exactly what package-macos.sh does to build the .app payload;
# this exposes it as the standard CMake install so it works on any platform and for
# any prefix, macOS .app packaging included (package-macos.sh stays as the macOS
# convenience wrapper).
#
# SCOPE: a runnable SCI-rooted tree at <prefix> (the layout this fork ships and the
# launcher's default). The leaner autotools-style FHS split — bin/ + lib/scilab/ +
# share/scilab/ + include/scilab/, which bin/scilab still supports — is future work;
# see the "split prefix layout" note in bin/scilab.

# The runtime tree is the source tree minus build/dev/scratch artifacts — the same
# exclusion set package-macos.sh's rsync uses, plus the Maven intermediates. Anchored
# REGEXes exclude a directory only at the tree root or at a well-known nested path,
# so a legitimately-named file deeper in the tree is not swept out.
install(DIRECTORY ${SCILAB_SOURCE_DIR}/
        DESTINATION "."
        USE_SOURCE_PERMISSIONS
        # VCS / build trees / packaging scratch (root-level)
        REGEX "/\\.git($|/)" EXCLUDE
        REGEX "/build-cmake($|/)" EXCLUDE
        REGEX "/build-parity($|/)" EXCLUDE
        REGEX "/\\.atoms($|/)" EXCLUDE
        REGEX "/autom4te\\.cache($|/)" EXCLUDE
        REGEX "/Scilab-2027\\.0\\.0\\.app($|/)" EXCLUDE
        REGEX "/tbx-smoke($|/)" EXCLUDE
        # Ant-era per-module build output dirs (modules/<m>/build)
        REGEX "/modules/[^/]+/build($|/)" EXCLUDE
        # Maven per-module intermediates — keep the *.jar in target/, drop the rest
        REGEX "/target/(classes|maven-status|generated-sources|maven-archiver)($|/)" EXCLUDE
        # object files / test scratch
        PATTERN "*.o" EXCLUDE
        PATTERN "*.lo" EXCLUDE
        PATTERN "*.HIDDEN" EXCLUDE
        PATTERN "config.log" EXCLUDE
        PATTERN "config.status" EXCLUDE)

# Relocate the build-time source path -> the install prefix in the installed tree's
# text files. Runs at `cmake --install` time (CMAKE_INSTALL_PREFIX is live then).
# ${SCILAB_SOURCE_DIR} is interpolated now (configure time); \${CMAKE_INSTALL_PREFIX}
# is left for install time. grep -lI finds only text files that actually carry the
# path (fast); sed rewrites in place. No-op when source == prefix.
install(CODE "
  if(NOT \"${SCILAB_SOURCE_DIR}\" STREQUAL \"\${CMAKE_INSTALL_PREFIX}\")
    message(STATUS \"Scilab install: relocating '${SCILAB_SOURCE_DIR}' -> '\${CMAKE_INSTALL_PREFIX}'\")
    execute_process(COMMAND bash -c
      \"set -e; grep -rlI '${SCILAB_SOURCE_DIR}' '\${CMAKE_INSTALL_PREFIX}' 2>/dev/null | while IFS= read -r f; do LC_ALL=C sed -i.relbak 's|${SCILAB_SOURCE_DIR}|\${CMAKE_INSTALL_PREFIX}|g' \\\"\$f\\\"; rm -f \\\"\$f.relbak\\\"; done; true\")
    execute_process(COMMAND bash -c
      \"n=\$(grep -rlI '${SCILAB_SOURCE_DIR}' '\${CMAKE_INSTALL_PREFIX}' 2>/dev/null | wc -l | tr -d ' '); echo \\\"  remaining source-path refs after relocation: \$n\\\"\")
  endif()
")
