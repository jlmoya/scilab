#!/usr/bin/env bash
# Populate build-cmake/test-native-libs/ with symlinks to every built module dylib,
# so surefire can point DYLD_LIBRARY_PATH + java.library.path at ONE directory when
# running the native/integration Java tests. Regenerate after a native rebuild.
set -euo pipefail
cd "$(dirname "$0")"
FARM="build-cmake/test-native-libs"
rm -rf "$FARM"; mkdir -p "$FARM"
n=0
while IFS= read -r dylib; do
  ln -sf "$(cd "$(dirname "$dylib")" && pwd)/$(basename "$dylib")" "$FARM/$(basename "$dylib")"
  n=$((n+1))
done < <(find modules -path '*/.libs/*.dylib' 2>/dev/null)
echo "test-native-libs: linked $n dylibs into $FARM"
