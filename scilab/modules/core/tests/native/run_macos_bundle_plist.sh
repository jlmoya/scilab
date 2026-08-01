#!/usr/bin/env bash
# Both Scilab app bundles must declare NSCameraUsageDescription.
#
# This covers the LaunchServices launch path (double-clicking the .app), which
# is distinct from the embedded __TEXT,__info_plist section checked by
# run_macos_usage_plist.sh. That one covers the paths with no bundle at all:
# the `scilab2027` terminal wrapper and the dev tree's bin/scilab.
#
# Usage:  ./run_macos_bundle_plist.sh
# Env:    APP_BUNDLES  space-separated bundle paths to check
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
SCI_ROOT="$(cd "$HERE/../../../.." && pwd)"
BUNDLES="${APP_BUNDLES:-$SCI_ROOT/Scilab-2027.0.0.app /Applications/Scilab-2027.0.0.app}"

if [ "$(uname -s)" != "Darwin" ]; then
    echo "SKIP: macOS-only"
    exit 0
fi

status=0
found=0
for app in $BUNDLES; do
    plist="$app/Contents/Info.plist"
    if [ ! -f "$plist" ]; then
        echo "SKIP $app: not present"
        continue
    fi

    # A tracked Info.plist does not make a launchable bundle. The dev-tree
    # bundle's launcher executable and icon are untracked, so a fresh clone or
    # `git clean -fdx` can leave Info.plist behind with everything else gone --
    # a skeleton. Read the executable name from the plist itself rather than
    # hardcoding it (so this keeps working across version bumps) and treat a
    # missing executable the same as a missing bundle: SKIP, not counted below.
    exe_name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$plist" 2>/dev/null)"
    if [ -z "$exe_name" ] || [ ! -f "$app/Contents/MacOS/$exe_name" ]; then
        echo "SKIP $app: not present (Info.plist found but launcher executable missing -- skeleton bundle)"
        continue
    fi

    found=$((found + 1))
    desc="$(/usr/libexec/PlistBuddy -c 'Print :NSCameraUsageDescription' "$plist" 2>/dev/null)"
    if [ -z "$desc" ]; then
        echo "FAIL $app: no NSCameraUsageDescription"
        status=1
    else
        echo "PASS $app: $desc"
    fi
done

if [ "$found" -eq 0 ]; then
    echo "FAIL: no bundles found -- package first (./package-macos.sh)"
    exit 1
fi
exit "$status"
