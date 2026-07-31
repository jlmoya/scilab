#!/usr/bin/env bash
#
# Build and run the B21 XcosCellFactory probe. See the header of
# XcosCellFactoryProbe.java for what it proves.
#
# Usage:  modules/xcos/tests/native/run_xcos_cell_factory.sh
#
# WHY THIS IS NOT A SUREFIRE EXECUTION — three independent reasons, each
# sufficient on its own:
#
#   1. java.library.path is per-JVM and -Pnative-tests must put the NWNI
#      libjavasci2 first for the 105 javasci tests (register B16). xcos's
#      libscixcos links the REAL libscijvm and that variant links
#      libscijvm-disable; together they trip checkForLinkerErrors()'s exit(1).
#   2. Advanced mode needs the full etc/classpath.xml jar set. Maven's reactor
#      graph is ~6 modules short of it (ui_data first: BrowseVar), and listing
#      those 86 entries in a pom would be a second source of truth that rots.
#   3. It needs -Djava.system.class.loader=ScilabClassLoader and the --add-opens
#      set from etc/jvm_options.xml; without them ClassPath.addURL throws
#      ClassCastException and LibraryPath cannot reach jdk.internal.loader.
#
# All three are satisfied by DERIVING the environment from the same files the
# real launcher reads. Same pattern as
# modules/javasci/tests/native/run_b22_advanced_mode.sh.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "${here}/../../../.." && pwd)"

# shellcheck source=../../../core/tests/native/scilab_test_env.sh
. "${root}/modules/core/tests/native/scilab_test_env.sh"

out="$(mktemp -d)"
trap 'rm -rf "${out}"' EXIT

javac -cp "${SCILAB_TEST_CP}" -d "${out}" "${here}/XcosCellFactoryProbe.java"

"${JAVA_HOME:+${JAVA_HOME}/bin/}java" "${SCILAB_TEST_OPTS[@]}" \
    -Djava.library.path="${SCILAB_TEST_LIBPATH}" \
    -cp "${SCILAB_TEST_CP}:${out}" XcosCellFactoryProbe
