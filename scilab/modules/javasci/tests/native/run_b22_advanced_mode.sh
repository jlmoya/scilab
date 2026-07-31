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
export SCI="${root}"

if [ ! -f "${SCI}/etc/classpath.xml" ]; then
    echo "no configured tree at ${SCI} (etc/classpath.xml missing)" >&2
    exit 2
fi

read_xml() {   # read_xml <file> <tag> <attr>  -> one value per line, $SCILAB expanded
    python3 - "${SCI}" "$1" "$2" "$3" <<'PY'
import sys, os, xml.etree.ElementTree as ET
# Scilab's own config files from the source tree: trusted input, and ElementTree
# does not resolve the external DTD they declare.
sci, path, tag, attr = sys.argv[1:5]
for e in ET.parse(os.path.join(sci, 'etc', path)).getroot().iter(tag):
    v = (e.get(attr) or '').replace('$SCILAB', sci)
    if v:
        print(v)
PY
}

CP="$(read_xml classpath.xml path value | paste -sd: -)"

# librarypath.xml lists optional locations too; keep only what exists, in order.
# The loop must not be the pipeline's exit status: under `set -e -o pipefail` a
# final non-directory entry would abort the script with no output at all.
LIBPATH="$(read_xml librarypath.xml path value | { while read -r d; do
    if [ -d "$d" ]; then printf '%s\n' "$d"; fi
done; } | paste -sd: -)"

# jvm_options.xml: the generic options plus os="macosx". java.library.path and
# java.class.path are supplied above instead.
mapfile -t OPTS < <(python3 - "${SCI}" <<'PY'
import sys, os, xml.etree.ElementTree as ET
sci = sys.argv[1]
host = 'macosx' if sys.platform == 'darwin' else ('windows' if os.name == 'nt' else 'linux')
for o in ET.parse(os.path.join(sci, 'etc', 'jvm_options.xml')).getroot().iter('option'):
    if o.get('os') not in (None, host):
        continue
    v = (o.get('value') or '').replace('$SCILAB', sci)
    if v and not v.startswith(('-Djava.library.path', '-Djava.class.path')):
        print(v)
PY
)

out="$(mktemp -d)"
trap 'rm -rf "${out}"' EXIT

javac -cp "${CP}" -d "${out}" "${here}/B22AdvancedMode.java"

# NOTE the library path deliberately does NOT put build-cmake/test-native-libs-cli
# first: advanced mode needs the GUI-linked libjavasci2, the opposite of what
# -Pnative-tests wants. That opposition is exactly why this is its own runner.
"${JAVA_HOME:+${JAVA_HOME}/bin/}java" "${OPTS[@]}" \
    -Djava.library.path="${LIBPATH}" \
    -cp "${CP}:${out}" B22AdvancedMode
