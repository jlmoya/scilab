#!/bin/bash
# Launch the in-tree DEV Scilab with the toolbox set autoloaded.
# This does NOT change the clean default launch: plain ./bin/scilab still resolves
# to the dev SCIHOME (~/.Scilab/scilab-branch-2027.0), which has no .scilab and so
# loads no toolboxes. This wrapper only redirects -scihome to a dedicated settings
# dir that DOES have a .scilab autoload script + the shared toolbox manifest.
# Same binary you just built — only the settings directory differs.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
CFG="$HOME/.config/scilab-app/java_home"
if   [ -s "$CFG" ];           then export JAVA_HOME="$(sed -n '1p' "$CFG")"
elif [ -n "${JAVA_HOME:-}" ]; then :
else export JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)"; fi
exec "$HERE/bin/scilab" -scihome "$HOME/.Scilab/scilab-dev-tbx-2027" "$@"
