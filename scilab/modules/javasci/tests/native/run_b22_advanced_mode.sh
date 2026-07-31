#!/usr/bin/env bash
#
# Build and run the B22 defect-(1) advanced-mode acceptance probe.
# See the header of B22AdvancedMode.java for what it proves.
#
# Usage:  modules/javasci/tests/native/run_b22_advanced_mode.sh
#
# WHY THIS IS NOT A SUREFIRE EXECUTION. The four legacy advanced-mode tests
# (testBug10801, testGraphics, testExportOffscreen, testBug9544) stay excluded in
# pom.xml for a harness-topology reason the product fix does not remove: a single
# surefire run can only have ONE javasci2 variant first on java.library.path, and
# -Pnative-tests needs the CLI variant there for the 105 NWNI tests. Wiring a
# second execution would also mean either restating etc/classpath.xml's 86 entries
# inside pom.xml -- two sources of truth, guaranteed to rot -- or generating a
# manifest-only classpath jar, which is new build-graph and parity surface. This
# script instead DERIVES the environment from the same three XML files Scilab
# itself reads, so it cannot drift.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "${here}/../../../.." && pwd)"

# shellcheck source=../../../core/tests/native/scilab_test_env.sh
. "${root}/modules/core/tests/native/scilab_test_env.sh"

out="$(mktemp -d)"
trap 'rm -rf "${out}"' EXIT

javac -cp "${SCILAB_TEST_CP}" -d "${out}" "${here}/B22AdvancedMode.java"

# NOTE the library path deliberately does NOT put build-cmake/test-native-libs-cli
# first: advanced mode needs the GUI-linked libjavasci2, the opposite of what
# -Pnative-tests wants. That opposition is exactly why this is its own runner.
"${JAVA_HOME:+${JAVA_HOME}/bin/}java" "${SCILAB_TEST_OPTS[@]}" \
    -Djava.library.path="${SCILAB_TEST_LIBPATH}" \
    -cp "${SCILAB_TEST_CP}:${out}" B22AdvancedMode
