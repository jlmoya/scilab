#!/usr/bin/env bash
# ============================================================================
# fetch-thirdparty.sh — populate the untracked third-party payload of a fresh
# clone so that `./build-macos.sh` (a plain configure && make) can run:
#
#   thirdparty/          jars (official Scilab prerequirements + this fork's
#                        additions: JediTerm terminal, JCEF browser, LWJGL/
#                        Vulkan renderer, JavaFX) + fonts/docbook/checkstyle
#   lib/thirdparty/      native dylibs (JOGL, JavaFX, JCEF) + libxlnt
#   ../xlnt-prefix/      xlnt headers + dylib + pkg-config file (out-of-tree,
#                        sibling of this source root's parent — the repo root)
#
# Everything is version-pinned and sha256-verified. Downloads are cached in
# --cache (default ~/.cache/scilab-thirdparty), so re-runs are offline-fast
# and a wiped clone can be repopulated without re-downloading.
#
#   ./fetch-thirdparty.sh                  # fetch + install + verify
#   ./fetch-thirdparty.sh --verify-only    # no network: check the payload
#   ./fetch-thirdparty.sh --force          # re-extract/reinstall everything
#   ./fetch-thirdparty.sh --dest DIR       # install into DIR instead of this
#                                          # source root (testing)
#
# NOTE: the official prerequirements URL is a *mutable* "branch-main" artifact;
# it is pinned by sha256 here, so if upstream republishes it this script fails
# loudly instead of silently drifting — bump the pin deliberately.
# See docs/building/macos.md §1–§2 for what each piece is.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"   # the source root (scilab/scilab)
DEST="$SCRIPT_DIR"
CACHE="${SCILAB_THIRDPARTY_CACHE:-$HOME/.cache/scilab-thirdparty}"
VERIFY_ONLY=0
FORCE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --dest)        DEST="$(mkdir -p "$2" && cd "$2" && pwd)"; shift 2;;
    --cache)       CACHE="$2"; shift 2;;
    --verify-only) VERIFY_ONLY=1; shift;;
    --force)       FORCE=1; shift;;
    -h|--help)     sed -n '2,28p' "$0"; exit 0;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done

TP="$DEST/thirdparty"
LTP="$DEST/lib/thirdparty"
XLNT_PREFIX="$(dirname "$DEST")/xlnt-prefix"
mkdir -p "$TP" "$LTP" "$CACHE"

# ---------------------------------------------------------------------------
# pinned sources
# ---------------------------------------------------------------------------
PREREQ_URL="https://oos.eu-west-2.outscale.com/scilab-releases-dev/prerequirements/prerequirements-scilab-branch-main-macosx.tar.xz"
PREREQ_SHA="d0ce83472e9a1f1a449929a5f1913f8e0b4168c89da4cd102f269f09f8db4f64"

JAVAFX_URL="https://download2.gluonhq.com/openjfx/25.0.2/openjfx-25.0.2_osx-aarch64_bin-sdk.zip"
JAVAFX_SHA="2a44be17cf1b14001b386e9a4ff54ee69e354bcf20a68189b11186f65abf96c5"

MOLTENVK_URL="https://github.com/KhronosGroup/MoltenVK/releases/download/v1.4.1/MoltenVK-macos.tar"
MOLTENVK_SHA="5ea0c259df7ded9a275444820f09cced54d6e5a7c7a31d262de62a5cdb7e15cf"

JCEF_NATIVES_URL="https://github.com/jcefmaven/jcefbuild/releases/download/1.0.66/macosx-arm64.tar.gz"
JCEF_NATIVES_SHA="bdb7df4ed87d3fcd9471f672115fdbef907802c8e911183a40c532fb29459433"

XLNT_SRC_URL="https://oos.eu-west-2.outscale.com/scilab-releases-dev/prerequirements-sources/xlnt-1.6.1_with_submodules.tar.gz"
XLNT_SRC_SHA="93a7ca746acadc08ec1ea3b4368b2c0602007e2c1bac09480a840ae26acfbef8"

# Maven artifacts (this fork's additions on top of the prerequirements):
#   repo  group:artifact:version[:classifier]  sha256
# repo: central = repo1.maven.org, jb = JetBrains intellij-dependencies (JediTerm lives there)
MAVEN_MANIFEST="
central com.google.code.gson:gson:2.10.1                 4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593
central net.java.dev.jna:jna:5.14.0                      34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6
central org.jetbrains.kotlin:kotlin-stdlib:2.1.21        263bdc679e1f62012db7b091796279b6d71cf36f4797a98ff1ace05835f201c8
central org.jetbrains:annotations:24.0.1                 61666dbce7e42e6c85b43c04fcfb8293a21dcb55b3c80e869270ce42c01a6b35
central io.methvin:directory-watcher:0.18.0              18f67869b0d31d39512623226220abeedd6bde486d5599e6256eab7975110754
jb      org.jetbrains.jediterm:jediterm-core:3.70        d94732a512d4c328b1fd6ec9163428b2d4351a2828989e8240f1e575949d93d1
jb      org.jetbrains.jediterm:jediterm-ui:3.70          d18e290da36d9dbc46e37683cc1dc9a3758b2af4cc9b458ffb9271b73d18c6d2
central org.slf4j:slf4j-api:2.0.9                        0818930dc8d7debb403204611691da58e49d42c50b6ffcfdce02dadb7c3c2b6c
central org.lwjgl:lwjgl:3.3.4                            6844ff591a4fa4175136416eb1d93ede336224fe3e2026ff29993a93a000b169
central org.lwjgl:lwjgl:3.3.4:natives-macos-arm64        9c524d760a82410306aa6f11234d9b3f520444ae625a7a9843439b9dd32a0801
central org.lwjgl:lwjgl-vulkan:3.3.4                     3dedd608a3597e4f895cbbc389fa9cc98cd5aba6c9cd9cfda1d30e842a629c34
central org.lwjgl:lwjgl-jawt:3.3.4                       b69f4550e53fa424441b93fda5e605196a971e3ae55a26bd69e83afee7e3b4fc
central org.lwjglx:lwjgl3-awt:0.2.4                      217cb3201a7c1acf844b20926393d081193297abde38b945f87f2597912f1de6
central me.friwi:jcef-api:jcef-ca49ada+cef-135.0.20+ge7de5c3+chromium-135.0.7049.85 4c103b99cb5b95c0e7c4a3cea7e0bc75ab8ed955f620c101f343e30cd85a342a jcef-api.jar
"

# The first-party Layer-1 GPU surface jar is *tracked* in this repo (vendored).
SWING_GPU_SURFACE_SRC="$SCRIPT_DIR/modules/prebuildjava/firstparty/swing-gpu-surface-0.1.0.jar"

PENDING_SEEN=0

# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
sha256() { shasum -a 256 "$1" | awk '{print $1}'; }

# fetch <url> <sha256|PENDING> <cache-name>  -> echoes the cached path
fetch() {
  local url="$1" pin="$2" name="$3" f="$CACHE/$3" got
  if [ ! -f "$f" ]; then
    echo "      downloading $name …" >&2
    curl -fsSL --retry 3 --connect-timeout 20 -o "$f.part" "$url" \
      || { echo "ERROR: download failed ($url)" >&2; rm -f "$f.part"; exit 1; }
    mv "$f.part" "$f"
  fi
  got="$(sha256 "$f")"
  if [ "$pin" = "PENDING" ]; then
    echo "!!    PENDING PIN: $got  $name  <- bake this into the script" >&2
    PENDING_SEEN=1
  elif [ "$got" != "$pin" ]; then
    echo "ERROR: sha256 mismatch for $name" >&2
    echo "  expected: $pin" >&2
    echo "  got:      $got   (cached at $f — delete it to re-download)" >&2
    exit 1
  fi
  echo "$f"
}

# install_pinned <src> <dest> <sha256>  — copy iff missing/differing, then verify
install_pinned() {
  local src="$1" dst="$2" pin="$3"
  if [ "$FORCE" = 1 ] || [ ! -f "$dst" ] || [ "$(sha256 "$dst")" != "$pin" ]; then
    cp -f "$src" "$dst"
  fi
  [ "$(sha256 "$dst")" = "$pin" ] || { echo "ERROR: $dst does not match its pin" >&2; exit 1; }
}

# ---------------------------------------------------------------------------
# fetch + install
# ---------------------------------------------------------------------------
if [ "$VERIFY_ONLY" = 0 ]; then

  echo "[1/8] official Scilab prerequirements (jars + JOGL dylibs + fonts/docbook)…"
  # Sentinel: a tarball-provided jar. The archive extracts thirdparty/ + lib/thirdparty/.
  if [ "$FORCE" = 1 ] || [ ! -f "$TP/jogl-all-2.5.0.jar" ]; then
    PREREQ_TAR="$(fetch "$PREREQ_URL" "$PREREQ_SHA" "prerequirements-macosx.tar.xz")"
    tar -xJf "$PREREQ_TAR" -C "$DEST"
    echo "      extracted"
  else
    echo "      present (sentinel jogl-all-2.5.0.jar) — skip (--force to re-extract)"
  fi

  echo "[2/8] JavaFX 25.0.2 (dylibs + base/graphics/swing jars — JDK-25 parity)…"
  if [ "$FORCE" = 1 ] || [ ! -f "$TP/javafx.graphics.jar" ]; then
    JFX_ZIP="$(fetch "$JAVAFX_URL" "$JAVAFX_SHA" "openjfx-25.0.2_osx-aarch64_bin-sdk.zip")"
    JFX_TMP="$(mktemp -d)"
    unzip -q "$JFX_ZIP" -d "$JFX_TMP"
    cp -f "$JFX_TMP"/javafx-sdk-25.0.2/lib/*.dylib "$LTP/"
    for j in base graphics swing; do
      cp -f "$JFX_TMP/javafx-sdk-25.0.2/lib/javafx.$j.jar" "$TP/javafx.$j.jar"
    done
    rm -rf "$JFX_TMP"
    echo "      installed"
  else
    echo "      present — skip"
  fi
  # The prerequirements tarball also ships versioned JavaFX 21 jars and a versioned jcef jar.
  # configure's jar checks glob (javafx.base*.jar, jcef*.jar), so duplicates make the resolved
  # jar nondeterministic — keep exactly one of each: the JDK-parity JavaFX set installed above
  # and the pinned jcef-api.jar (installed in step 3, matching the step-5 natives release).
  rm -f "$TP"/javafx.base-*.jar "$TP"/javafx.graphics-*.jar "$TP"/javafx.swing-*.jar "$TP"/jcef-1*.jar

  echo "[3/8] Maven artifacts (fork additions)…"
  while read -r repo gav pin dest; do
    [ -n "$repo" ] || continue
    case "$repo" in
      central) base="https://repo1.maven.org/maven2";;
      jb)      base="https://packages.jetbrains.team/maven/p/ij/intellij-dependencies";;
      *) echo "ERROR: unknown repo key '$repo' in manifest" >&2; exit 1;;
    esac
    IFS=: read -r g a v c <<EOF
$gav
EOF
    jar="$a-$v${c:+-$c}.jar"
    [ -n "${dest:-}" ] || dest="$jar"
    url="$base/$(echo "$g" | tr . /)/$a/$v/$jar"
    f="$(fetch "$url" "$pin" "$dest")"
    install_pinned "$f" "$TP/$dest" "$pin"
    echo "      $dest"
  done <<< "$MAVEN_MANIFEST"

  echo "[4/8] MoltenVK 1.4.1 (Vulkan renderer's Metal translation layer)…"
  if [ "$FORCE" = 1 ] || [ ! -f "$TP/libMoltenVK.dylib" ]; then
    MVK_TAR="$(fetch "$MOLTENVK_URL" "$MOLTENVK_SHA" "MoltenVK-1.4.1-macos.tar")"
    MVK_TMP="$(mktemp -d)"
    tar -xf "$MVK_TAR" -C "$MVK_TMP"
    MVK_DYLIB="$(find "$MVK_TMP" -type f -name libMoltenVK.dylib -path "*macOS*" | head -1)"
    [ -n "$MVK_DYLIB" ] || { echo "ERROR: libMoltenVK.dylib not found in the MoltenVK archive" >&2; exit 1; }
    cp -f "$MVK_DYLIB" "$TP/libMoltenVK.dylib"
    rm -rf "$MVK_TMP"
    echo "      installed ($(lipo -archs "$TP/libMoltenVK.dylib" 2>/dev/null))"
  else
    echo "      present — skip"
  fi

  echo "[5/8] JCEF natives (embedded browser, jcefbuild 1.0.66; the api jar came from Maven)…"
  if [ "$FORCE" = 1 ] || [ ! -d "$LTP/jcef/Chromium Embedded Framework.framework" ]; then
    JCEF_TGZ="$(fetch "$JCEF_NATIVES_URL" "$JCEF_NATIVES_SHA" "jcefbuild-1.0.66-macosx-arm64.tar.gz")"
    JCEF_TMP="$(mktemp -d)"
    tar -xzf "$JCEF_TGZ" -C "$JCEF_TMP"
    mkdir -p "$LTP/jcef"
    # the bundle nests the framework + helper apps + libjcef under bin/…/Contents/…
    FW="$(find "$JCEF_TMP" -maxdepth 6 -type d -name "Chromium Embedded Framework.framework" | head -1)"
    [ -n "$FW" ] || { echo "ERROR: CEF framework not found in the jcefbuild archive" >&2; exit 1; }
    rsync -a --delete "$FW/" "$LTP/jcef/Chromium Embedded Framework.framework/"
    find "$JCEF_TMP" -maxdepth 6 -type d -name "jcef Helper*.app" | while IFS= read -r app; do
      rsync -a --delete "$app/" "$LTP/jcef/$(basename "$app")/"
    done
    LIBJCEF="$(find "$JCEF_TMP" -type f -name libjcef.dylib | head -1)"
    [ -n "$LIBJCEF" ] && cp -f "$LIBJCEF" "$LTP/jcef/libjcef.dylib"
    for lic in LICENSE.txt README.txt gluegen.LICENSE.txt jogl.LICENSE.txt; do
      src="$(find "$JCEF_TMP" -maxdepth 4 -name "$lic" | head -1)"
      [ -n "$src" ] && cp -f "$src" "$LTP/jcef/$lic"
    done
    cat > "$LTP/jcef/build_meta.json" <<'META'
{
  "jcef_url": "https://bitbucket.org/chromiumembedded/java-cef/commits/ca49ada5c7e3fc98cd7058b6253b59f967530a65",
  "release_tag": "jcef-ca49ada+cef-135.0.20+ge7de5c3+chromium-135.0.7049.85",
  "release_url": "https://github.com/jcefmaven/jcefbuild/releases/tag/1.0.66",
  "platform": "macosx-arm64",
  "release_download_url": "https://github.com/jcefmaven/jcefbuild/releases/download/1.0.66/macosx-arm64.tar.gz"
}
META
    rm -rf "$JCEF_TMP"
    echo "      installed"
  else
    echo "      present — skip"
  fi

  echo "[6/8] swing-gpu-surface (first-party Layer-1, vendored in this repo)…"
  [ -f "$SWING_GPU_SURFACE_SRC" ] || { echo "ERROR: $SWING_GPU_SURFACE_SRC missing (broken checkout?)" >&2; exit 1; }
  cp -f "$SWING_GPU_SURFACE_SRC" "$TP/swing-gpu-surface-0.1.0.jar"
  echo "      installed"

  echo "[7/8] compatibility symlinks…"
  ln -sf lucene-core-9.10.0.jar "$TP/lucene-analyzers-common-9.10.0.jar"   # class moved in Lucene 9
  ln -sf jogl-all-2.5.0.jar     "$TP/jogl2.jar"
  ln -sf gluegen-rt-2.5.0.jar   "$TP/gluegen2-rt.jar"
  ln -sf libgluegen_rt.dylib    "$LTP/libgluegen2-rt.dylib"                 # legacy gluegen2 name
  echo "      lucene-analyzers-common / jogl2 / gluegen2-rt (+dylib alias)"

  echo "[8/8] ../xlnt-prefix — build xlnt 1.6.1 from source (cmake, ~1 min)…"
  # Built from Scilab's pinned source tarball (headers, export header, dylib all come out of the
  # real build) — no dependency on an installed Scilab release for the dylib. The install name is
  # @rpath/… so the spreadsheet module resolves it via its rpaths, and the deployment target
  # matches the build's (macOS 11.0).
  if [ "$FORCE" = 1 ] || [ ! -f "$XLNT_PREFIX/lib/pkgconfig/xlnt.pc" ]; then
    command -v cmake >/dev/null || { echo "ERROR: cmake is required to build xlnt (brew install cmake)" >&2; exit 1; }
    XLNT_TGZ="$(fetch "$XLNT_SRC_URL" "$XLNT_SRC_SHA" "xlnt-1.6.1_with_submodules.tar.gz")"
    XLNT_TMP="$(mktemp -d)"
    tar -xzf "$XLNT_TGZ" -C "$XLNT_TMP"
    ( cd "$XLNT_TMP/xlnt-1.6.1" \
      && cmake -B build -DCMAKE_BUILD_TYPE=Release -DSTATIC=OFF -DTESTS=OFF \
           -DCMAKE_INSTALL_PREFIX="$XLNT_PREFIX" -DCMAKE_INSTALL_NAME_DIR=@rpath \
           -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0 -DCMAKE_POLICY_VERSION_MINIMUM=3.5 > build-cfg.log 2>&1 \
      && cmake --build build -j"$(sysctl -n hw.ncpu)" > build.log 2>&1 \
      && cmake --install build > build-install.log 2>&1 ) \
      || { echo "ERROR: xlnt build failed (logs in $XLNT_TMP/xlnt-1.6.1)" >&2; exit 1; }
    rm -rf "$XLNT_TMP"
    # xlnt's CMake ignores INSTALL_NAME_DIR and its installed .pc has relative libdir/includedir
    # (both 1.6.1 packaging bugs) — enforce the @rpath id and write a correct .pc ourselves.
    install_name_tool -id @rpath/libxlnt.1.6.1.dylib "$XLNT_PREFIX/lib/libxlnt.1.6.1.dylib"
    codesign -f -s - "$XLNT_PREFIX/lib/libxlnt.1.6.1.dylib"
    ln -sf libxlnt.1.6.1.dylib "$XLNT_PREFIX/lib/libxlnt.dylib"
    mkdir -p "$XLNT_PREFIX/lib/pkgconfig"
    cat > "$XLNT_PREFIX/lib/pkgconfig/xlnt.pc" <<EOP
prefix=$XLNT_PREFIX
libdir=\${prefix}/lib
Name: xlnt
Description: cross-platform user-friendly xlsx library
Version: 1.6.1
Cflags: -I\${prefix}/include
Libs: -L\${prefix}/lib -lxlnt
EOP
    echo "      built + installed at $XLNT_PREFIX"
  else
    echo "      present — skip"
  fi
fi

# ---------------------------------------------------------------------------
# verify — every artifact configure/make/runtime needs
# ---------------------------------------------------------------------------
echo
echo "verify: required payload…"
MISSING=0
need()      { [ -e "$1" ] || { echo "  MISSING: $1"; MISSING=1; }; }
need_arm64(){ need "$1"; [ -e "$1" ] && { lipo -archs "$1" 2>/dev/null | grep -q arm64 || { echo "  NOT arm64: $1"; MISSING=1; }; }; }

# fork additions + the tarball-provided jars configure hard-requires
for j in gson-2.10.1 jna-5.14.0 kotlin-stdlib-2.1.21 annotations-24.0.1 \
         directory-watcher-0.18.0 jediterm-core-3.70 jediterm-ui-3.70 slf4j-api-2.0.9 \
         lwjgl-3.3.4 lwjgl-3.3.4-natives-macos-arm64 lwjgl-vulkan-3.3.4 lwjgl-jawt-3.3.4 \
         lwjgl3-awt-0.2.4 swing-gpu-surface-0.1.0 jcef-api \
         jogl-all-2.5.0 gluegen-rt-2.5.0 flexdock-1.2.5 jgraphx-2.1.0.7 skinlf-1.2.3 \
         jlatexmath-1.0.7 fop-core-2.9 batik-all-1.17 freehep-graphics2d-2.4 \
         lucene-core-9.10.0 lucene-queryparser-9.10.0 jrosetta-API-1.0.4 jrosetta-engine-1.0.4 \
         commons-io-2.11.0 commons-logging-1.1.1 jhall-2.0 jgoodies-looks-2.7.0; do
  need "$TP/$j.jar"
done
need "$TP/lucene-analyzers-common-9.10.0.jar"
for j in base graphics swing; do need "$TP/javafx.$j.jar"; done
need "$TP/fonts"; need "$TP/docbook"
need_arm64 "$TP/libMoltenVK.dylib"
for d in libgluegen_rt libjogl_desktop libnativewindow_awt libnativewindow_macosx libnewt_head \
         libglass libjavafx_font libjavafx_iio libprism_common libprism_es2 libprism_sw libprism_mtl \
         libdecora_sse libglib-lite; do
  need_arm64 "$LTP/$d.dylib"
done
need "$LTP/jcef/Chromium Embedded Framework.framework"
need "$LTP/jcef/build_meta.json"
need_arm64 "$XLNT_PREFIX/lib/libxlnt.1.6.1.dylib"
need "$XLNT_PREFIX/lib/pkgconfig/xlnt.pc"
need "$XLNT_PREFIX/include/xlnt/utils/xlnt_cmake_export.h"
if [ -f "$XLNT_PREFIX/lib/libxlnt.1.6.1.dylib" ]; then
  otool -D "$XLNT_PREFIX/lib/libxlnt.1.6.1.dylib" | grep -q "@rpath/libxlnt.1.6.1.dylib" \
    || { echo "  BAD install name (want @rpath/…): $XLNT_PREFIX/lib/libxlnt.1.6.1.dylib"; MISSING=1; }
fi

if [ "${PENDING_SEEN:-0}" = 1 ]; then
  echo
  echo "RESULT: downloads OK but PENDING sha256 pins remain — bake the printed values into this script."
  exit 3
elif [ "$MISSING" = 0 ]; then
  echo "RESULT: payload complete. Next: ./build-macos.sh"
else
  echo "RESULT: payload INCOMPLETE (see MISSING above)."
  exit 1
fi
