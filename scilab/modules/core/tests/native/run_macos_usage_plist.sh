#!/usr/bin/env bash
# Every Scilab executable must carry an embedded __TEXT,__info_plist declaring
# NSCameraUsageDescription.
#
# WHY THE SECTION AND NOT THE BUNDLE
# ----------------------------------
# macOS TCC will not hand a process the camera unless it can read a usage
# description for that process. The normal route is the app bundle's
# Info.plist, reached because CFBundleExecutable lives in Contents/MacOS/.
# Scilab's does not: Contents/MacOS/Scilab-<version> is a bash script that
# execs bin/scilab, which execs Contents/Resources/scilab/.libs/Scilab-<version>.
# [NSBundle mainBundle] therefore resolves to .libs/ -- a plain directory with
# no Info.plist -- and TCC kills the process with
# __TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__ (abort trap 6) the moment scicv's
# AVFoundation backend opens a camera. Apple's documented route for an
# executable that is not in a bundle is to carry the plist inside the Mach-O.
#
# WHY otool -P AND NOT otool -s
# -----------------------------
# `otool -X -s __TEXT __info_plist` prints the section as 4-byte words in host
# order, i.e. every group of 4 characters comes out reversed ("mx?<ev loisr"
# instead of "<?xml versio"). `otool -P` prints the section as text, after two
# header lines. Verified against /usr/bin/plutil, which ships such a section.
#
# Usage:  ./run_macos_usage_plist.sh
# Env:    SCI_LIBS  override the .libs directory under test
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
SCI_ROOT="$(cd "$HERE/../../../.." && pwd)"
LIBS="${SCI_LIBS:-$SCI_ROOT/.libs}"

if [ "$(uname -s)" != "Darwin" ]; then
    echo "SKIP: macOS-only (TCC does not exist elsewhere)"
    exit 0
fi

status=0
found=0
for name in scilab-bin scilab-cli-bin Scilab-2027.0.0; do
    exe="$LIBS/$name"
    if [ ! -f "$exe" ]; then
        echo "SKIP $name: not built ($exe)"
        continue
    fi
    found=$((found + 1))

    if ! otool -l "$exe" | grep -q 'sectname __info_plist'; then
        echo "FAIL $name: no __TEXT,__info_plist section"
        status=1
        continue
    fi

    # otool -P emits "<path>:" and "(__TEXT,__info_plist) section" first.
    desc="$(otool -P "$exe" | tail -n +3 \
            | plutil -extract NSCameraUsageDescription raw -o - -- - 2>/dev/null)"
    if [ -z "$desc" ]; then
        echo "FAIL $name: embedded plist has no NSCameraUsageDescription"
        status=1
        continue
    fi
    echo "PASS $name: $desc"
done

if [ "$found" -eq 0 ]; then
    echo "FAIL: no executables found in $LIBS -- build first (cmake --build <dir> --target drop-in-all)"
    exit 1
fi
exit "$status"
