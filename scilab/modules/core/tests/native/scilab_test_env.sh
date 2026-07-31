#!/usr/bin/env bash
#
# Shared environment derivation for native integration probes that need a FULL
# Scilab runtime in a plain JVM — the ones that cannot live under surefire.
#
# SOURCE this (it defines an array, which cannot survive an exec):
#
#     . "$(git rev-parse --show-toplevel)"/scilab/modules/core/tests/native/scilab_test_env.sh
#     java "${SCILAB_TEST_OPTS[@]}" -Djava.library.path="$SCILAB_TEST_LIBPATH" \
#          -cp "$SCILAB_TEST_CP:$out" MyProbe
#
# Everything is DERIVED from the three files Scilab itself reads at startup —
# etc/classpath.xml, etc/librarypath.xml, etc/jvm_options.xml — so a probe can
# never drift from the real launcher. Restating any of that in a pom.xml or in
# a script would be a second source of truth, and it would rot.
#
# Exports: SCI, SCILAB_TEST_CP, SCILAB_TEST_LIBPATH, SCILAB_TEST_OPTS (array).

SCI="${SCI:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)}"
export SCI

if [ ! -f "${SCI}/etc/classpath.xml" ]; then
    echo "scilab_test_env: no configured tree at ${SCI} (etc/classpath.xml missing)" >&2
    return 2 2>/dev/null || exit 2
fi

_sci_read_xml() {   # <file> <tag> <attr> -> one value per line, $SCILAB expanded
    python3 - "${SCI}" "$1" "$2" "$3" <<'PY'
import sys, os, xml.etree.ElementTree as ET
# Scilab's own config from the source tree: trusted input, and ElementTree does
# not resolve the external DTD these files declare.
sci, path, tag, attr = sys.argv[1:5]
for e in ET.parse(os.path.join(sci, 'etc', path)).getroot().iter(tag):
    v = (e.get(attr) or '').replace('$SCILAB', sci)
    if v:
        print(v)
PY
}

SCILAB_TEST_CP="$(_sci_read_xml classpath.xml path value | paste -sd: -)"

# librarypath.xml lists optional locations too; keep only what exists, in order.
# The loop must not be the pipeline's exit status: under `set -e -o pipefail` a
# final non-directory entry would abort the caller with no output at all.
SCILAB_TEST_LIBPATH="$(_sci_read_xml librarypath.xml path value | { while read -r d; do
    if [ -d "$d" ]; then printf '%s\n' "$d"; fi
done; } | paste -sd: -)"

# jvm_options.xml: generic options plus this host's os="..." ones. The two path
# options are dropped because the caller supplies them from the values above.
mapfile -t SCILAB_TEST_OPTS < <(python3 - "${SCI}" <<'PY'
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

export SCILAB_TEST_CP SCILAB_TEST_LIBPATH
