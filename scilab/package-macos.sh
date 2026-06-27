#!/usr/bin/env bash
# ============================================================================
# package-macos.sh — turn the in-place dev build into an independent,
# relocated /Applications/Scilab-2027.0.0.app for daily use on THIS Mac.
#
# Design: docs/design/macos-app-packaging.md  (Option A — relocated copy that
# keeps using the machine's Homebrew dylibs + a system JDK; NOT a notarized,
# dependency-vendored redistributable).
#
# Idempotent. First run creates the app; later runs rsync only the deltas, so a
# refresh after `./build-macos.sh && ./reapply-macos-fixes.sh` takes seconds and
# never touches your toolboxes/config (those live outside the bundle).
#
#   ./package-macos.sh                       # build/refresh /Applications app
#   ./package-macos.sh --app /path/Foo.app   # alternate target (e.g. for testing)
#   ./package-macos.sh --jdk-version 26      # pin a different default JDK
#   ./package-macos.sh --rebuild-toolboxes   # also rebuild native toolboxes (phase 2+)
# ============================================================================
set -euo pipefail

DEV="$(cd "$(dirname "$0")" && pwd)"            # the dev build tree (this script lives in it)
APP="/Applications/Scilab-2027.0.0.app"
JDK_PIN=25
REBUILD_TBX=0
APP_SCIHOME="$HOME/.Scilab/scilab-app-2027"

while [ $# -gt 0 ]; do
  case "$1" in
    --app)            APP="$2"; shift 2;;
    --jdk-version)    JDK_PIN="$2"; shift 2;;
    --rebuild-toolboxes) REBUILD_TBX=1; shift;;
    -h|--help) sed -n '2,22p' "$0"; exit 0;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done

PAYLOAD="$APP/Contents/Resources/scilab"
MACOS_DIR="$APP/Contents/MacOS"
RES_DIR="$APP/Contents/Resources"
BIN_NAME="Scilab-2027.0.0"

echo "DEV tree : $DEV"
echo "APP      : $APP"
echo "SCIHOME  : $APP_SCIHOME"

# ---- 0. sanity: the dev tree must be built ---------------------------------
if [ ! -x "$DEV/.libs/scilab-bin" ] && [ ! -x "$DEV/.libs/scilab-cli-bin" ]; then
  echo "ERROR: $DEV/.libs/scilab-bin not found — run ./build-macos.sh first." >&2
  exit 1
fi

# ---- 1. bundle skeleton ----------------------------------------------------
mkdir -p "$PAYLOAD" "$MACOS_DIR" "$RES_DIR" "$APP_SCIHOME"

# ---- 2. rsync the engine (incremental; skip build intermediates + recursion)
echo "[1/6] rsync dev build -> payload (incremental)…"
rsync -a --delete \
  --exclude='Scilab-2027.0.0.app/' \
  --exclude='*.o' --exclude='*.lo' \
  --exclude='.deps/' --exclude='.dirstamp' \
  --exclude='autom4te.cache/' \
  --exclude='config.log' --exclude='config.status' \
  --exclude='.git/' \
  "$DEV"/ "$PAYLOAD"/

# ---- 3. relocate: rewrite the dev abs-path -> payload path in text files ----
# (All dev-path-bearing files are text: launcher wrapper scripts, classpath.xml,
#  *.properties, libtool *.la. The Mach-O binaries hold it only as harmless
#  debug cruft and resolve siblings relatively, so they are left untouched.)
echo "[2/6] relocate dev path -> $PAYLOAD …"
# strip a stale nested stub if it slipped in
rm -rf "$PAYLOAD/Scilab-2027.0.0.app"
# grep -I skips binaries; rewrite only files that actually contain the old path
grep -rlI "$DEV" "$PAYLOAD" 2>/dev/null | while IFS= read -r f; do
  LC_ALL=C sed -i '' "s|$DEV|$PAYLOAD|g" "$f"
done
echo "      remaining dev-path refs in text files: $(grep -rlI "$DEV" "$PAYLOAD" 2>/dev/null | wc -l | tr -d ' ')"

# ---- 4. launcher (configurable JDK + own SCIHOME) --------------------------
echo "[3/6] launcher (JDK pin=$JDK_PIN, SCIHOME=$APP_SCIHOME)…"
cat > "$MACOS_DIR/$BIN_NAME" <<LAUNCHER
#!/bin/bash
# Scilab-2027.0.0.app launcher — independent relocated install (this Mac).
# JAVA_HOME resolution order: config file > inherited env > macOS resolver.
APP_RES="\$(cd "\$(dirname "\$0")/../Resources" && pwd)"
CFG="\$HOME/.config/scilab-app/java_home"
if   [ -s "\$CFG" ];        then export JAVA_HOME="\$(sed -n '1p' "\$CFG")"
elif [ -n "\${JAVA_HOME:-}" ]; then :   # keep inherited JAVA_HOME
else export JAVA_HOME="\$(/usr/libexec/java_home -v $JDK_PIN 2>/dev/null)"; fi
if [ -z "\${JAVA_HOME:-}" ] || [ ! -x "\$JAVA_HOME/bin/java" ]; then
  osascript -e 'display alert "Scilab: no JDK found" message "Set a JDK in ~/.config/scilab-app/java_home or install JDK $JDK_PIN."' 2>/dev/null
  exit 1
fi
SCIHOME_DIR="\$HOME/.Scilab/scilab-app-2027"
mkdir -p "\$SCIHOME_DIR"
cd "\$APP_RES/scilab" || exit 1
# -scihome (not the SCIHOME env var, which Scilab ignores) isolates this app's
# prefs + installed-toolbox set from the dev build.
exec ./bin/scilab -scihome "\$SCIHOME_DIR" "\$@"
LAUNCHER
chmod +x "$MACOS_DIR/$BIN_NAME"

# ---- 5. Info.plist + icon --------------------------------------------------
echo "[4/6] Info.plist + icon…"
cp -f "$DEV/Scilab-2027.0.0.app/Contents/Resources/scilab.icns" "$RES_DIR/scilab.icns" 2>/dev/null || true
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>Scilab-2027.0.0</string>
    <key>CFBundleDisplayName</key><string>Scilab-2027.0.0</string>
    <key>CFBundleExecutable</key><string>Scilab-2027.0.0</string>
    <key>CFBundleIconFile</key><string>scilab</string>
    <key>CFBundleIdentifier</key><string>org.scilab.app.scilab-2027-0-0</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleShortVersionString</key><string>2027.0.0</string>
    <key>CFBundleVersion</key><string>2027.0.0</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
</dict>
</plist>
PLIST

# ---- 6. seed the app SCIHOME .scilab (autoload startup) --------------------
echo "[5/7] .scilab autoload startup…"
# The toolbox manager is a core Scilab module (modules/toolbox_manager) — it is rsync'd
# with the engine above, so the tbx* verbs load for free. .scilab only autoloads the
# user's enabled toolboxes. Write it only if absent (preserve the user's own edits).
if [ ! -f "$APP_SCIHOME/.scilab" ]; then
if [ ! -f "$APP_SCIHOME/.scilab" ]; then
  cp "$DEV/macos-app/dot-scilab.template" "$APP_SCIHOME/.scilab"
  echo "      wrote $APP_SCIHOME/.scilab"
else
  echo "      $APP_SCIHOME/.scilab exists — left as-is"
fi

# ---- 6b. scilab2027 CLI wrapper on PATH (console from any terminal) ---------
CLI_DIR=/usr/local/bin; [ -w "$CLI_DIR" ] || CLI_DIR="$HOME/bin"
mkdir -p "$CLI_DIR"
cat > "$CLI_DIR/scilab2027" <<CLI
#!/bin/bash
# scilab2027 — console for $APP (managed by package-macos.sh)
APP_RES="$APP/Contents/Resources"
CFG="\$HOME/.config/scilab-app/java_home"
if   [ -s "\$CFG" ];           then export JAVA_HOME="\$(sed -n '1p' "\$CFG")"
elif [ -n "\${JAVA_HOME:-}" ]; then :
else export JAVA_HOME="\$(/usr/libexec/java_home -v $JDK_PIN 2>/dev/null)"; fi
exec "\$APP_RES/scilab/bin/scilab" -scihome "$APP_SCIHOME" -nw "\$@"
CLI
chmod +x "$CLI_DIR/scilab2027"
echo "      CLI: $CLI_DIR/scilab2027"

# ---- 7. ad-hoc sign the launcher (inner Mach-O keep their build-time sigs) --
echo "[6/7] ad-hoc sign launcher…"
codesign -f -s - "$MACOS_DIR/$BIN_NAME" 2>/dev/null || true

# ---- optional: rebuild native toolboxes (core-ABI-changed case) ------------
if [ "$REBUILD_TBX" = "1" ]; then
  echo "[7/7] --rebuild-toolboxes: tbxUpdate() all registered toolboxes…"
  JAVA_HOME="$(/usr/libexec/java_home -v "$JDK_PIN" 2>/dev/null)" \
    SCIHOME="$APP_SCIHOME" "$PAYLOAD/bin/scilab-cli" -nb -e \
    "exec(fullfile(SCI,'..','toolbox-manager','tbxmgr.sce'),-1); tbxUpdate(); quit" \
    2>/dev/null | grep -iE "tbxUpdate|loaded|FAILED" || true
else
  echo "[7/7] (skip toolbox rebuild — pass --rebuild-toolboxes to force)"
fi

echo
echo "Done. App: $APP"
echo "Launch:   open \"$APP\"     (or Spotlight 'Scilab-2027.0.0')"
