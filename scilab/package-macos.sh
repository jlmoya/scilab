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
# refresh after `./build-macos.sh` takes seconds and
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
ALLOW_DEBUG=0
APP_SCIHOME="$HOME/.Scilab/scilab-app-2027"

while [ $# -gt 0 ]; do
  case "$1" in
    --app)            APP="$2"; shift 2;;
    --jdk-version)    JDK_PIN="$2"; shift 2;;
    --rebuild-toolboxes) REBUILD_TBX=1; shift;;
    --allow-debug)    ALLOW_DEBUG=1; shift;;
    -h|--help) sed -n '2,22p' "$0"; exit 0;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done

# ---- build-type guard ------------------------------------------------------
# Release and debug builds drop into the SAME modules/*/.libs/ layout, because that is
# where bin/scilab and the rsync below both read their binaries. So a debug build silently
# replaces the release one, and without this check you could build debug, forget, package,
# and ship an unoptimised Scilab (-O0 -g3) with nothing anywhere saying so.
#
# build-macos.sh writes .scilab-build-type after its drop-in step. A MISSING stamp is
# reported as unknown rather than assumed to be release: `cmake --build <dir> --target
# drop-in-all` by hand bypasses the stamp entirely, so absence proves nothing.
_stamp_file="$DEV/.scilab-build-type"
_build_type="$( [ -f "$_stamp_file" ] && tr -d '[:space:]' < "$_stamp_file" || echo unknown )"
case "$_build_type" in
  release) ;;
  debug)
    if [ "$ALLOW_DEBUG" -eq 0 ]; then
      echo "REFUSING: modules/*/.libs/ holds a DEBUG build (-O0 -g3)." >&2
      echo "  Packaging it would ship an unoptimised Scilab." >&2
      echo "  Re-run ./build-macos.sh for a release build, or pass --allow-debug to" >&2
      echo "  package the debug one deliberately." >&2
      exit 3
    fi
    echo "WARNING: packaging a DEBUG build (-O0 -g3) because --allow-debug was given."
    ;;
  *)
    echo "NOTE: build type unknown (no $_stamp_file) — packaging whatever is in modules/*/.libs/."
    ;;
esac

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
# build-cmake/ is the CMake build tree (gitignored, ~600MB, ~27% of the dev tree). It is
# pure build scaffolding -- CMake's drop-in targets copy their real outputs into the
# .libs/ layout, and its help target writes into modules/*/target/, so nothing under
# build-cmake/ is used at runtime. Without this exclude it copied ~500MB across 5,762 files
# into the bundle AND made step 3 below grep+sed every text file in it. build-parity/ and
# .atoms/ are the same class, much smaller. All three postdate this exclude list's last
# revision, which is why they were missing.
#
# modules/*/target/ MUST be copied -- it holds the 24 module jars and the 6 help jars that
# classpath.xml, bin/scilab and jvm_options.xml all resolve at runtime (the Ant-era jar/
# directories were deleted 2026-07-21). But Maven's target/ carries build intermediates the
# old jar/ never did: classes/ is the exploded copy of the very same .class files already
# inside the jar, and maven-status/ is reactor bookkeeping. Measured 90M total vs 75M of
# jars, so excluding them keeps ~15M of dead weight out of every bundle. The excludes are
# scoped to target/ so a module shipping a real classes/ resource dir elsewhere is untouched.
#
# modules/*/build/ is the same story one era earlier: 25 dirs / 14M of Ant-era output left on
# disk when Ant was retired. Gitignored, zero tracked files, nothing newer than pom.xml, and
# no runtime reference (classpath.xml has zero /build/ entries) -- but rsync copied them into
# every bundle regardless. Excluded here; deleting them from the dev tree is safe too.
#
# /cmake-build-*/ is a GLOB, not another literal, and that is the point. The exclude above
# names build-cmake/ exactly, so CLion's default build directory -- cmake-build-debug/ --
# walked straight past it and shipped 29M of IDE build output inside the application. That is
# the third time this class of leak has been found (build-cmake/ ~500M in 554d38cf1c8, then
# modules/*/build/ 14M, now this), each time because the exclude enumerated the directories
# that existed rather than the shape of the ones that appear. The glob covers CLion's whole
# family at once: cmake-build-debug, -release, -relwithdebinfo, -minsizerel.
#
# Nothing under it was ever shipped-and-used: cmake-build-debug/ holds 0 dylibs and 41 stray
# objects, and the runtime reads its binaries from modules/*/.libs/ (the drop-in layout that
# build-cmake/ populates). The shipped engine is unaffected -- it is a release build, -O2
# -DNDEBUG -fwrapv on all 3606 TUs, set by cmake/ScilabFlags.cmake rather than by
# CMAKE_BUILD_TYPE, which is deliberately left empty.
#
# /build/ is the last of the same family: a 36K autotools leftover holding one stale
# config.log. Untracked, no runtime reference. Anchored with a leading slash so it hits only
# the top-level directory, never a module's own build/ (already handled above).
echo "[1/7] rsync dev build -> payload (incremental)…"
rsync -a --delete \
  --exclude='Scilab-2027.0.0.app/' \
  --exclude='*.o' --exclude='*.lo' \
  --exclude='.deps/' --exclude='.dirstamp' \
  --exclude='autom4te.cache/' \
  --exclude='config.log' --exclude='config.status' \
  --exclude='.git/' \
  --exclude='/build-cmake/' --exclude='/build-parity/' --exclude='/.atoms/' \
  --exclude='/cmake-build-*/' --exclude='/build/' \
  --exclude='modules/*/target/classes/' --exclude='modules/*/target/maven-status/' \
  --exclude='modules/*/target/generated-sources/' --exclude='modules/*/target/maven-archiver/' \
  --exclude='modules/*/build/' \
  "$DEV"/ "$PAYLOAD"/

# rsync's --delete does NOT remove destination files that an --exclude matches: excluded
# paths are PROTECTED in the destination, not pruned from it. So adding an exclude only
# stops FUTURE copies -- a bundle built before the exclude existed keeps the directory
# forever. That is why cmake-build-debug/ (29M) was still inside the app after being
# excluded, and why re-running the packager did not shrink it.
#
# --delete-excluded would fix it in a single flag and is deliberately NOT used: it applies
# to EVERY exclude above, including the Scilab-2027.0.0.app/ recursion guard, so it would
# delete the app directory out of its own payload whenever the dev tree contains one.
#
# Pruning the known-stale build directories explicitly is the narrow version of that. Only
# these names, only at the payload root, only if present.
for _stale in build-cmake build-parity cmake-build-debug cmake-build-release \
              cmake-build-relwithdebinfo cmake-build-minsizerel build .atoms; do
    if [ -e "$PAYLOAD/$_stale" ]; then
        echo "       pruning stale build dir from payload: $_stale ($(du -sh "$PAYLOAD/$_stale" | cut -f1))"
        rm -rf "${PAYLOAD:?}/$_stale"
    fi
done

# ---- 3. relocate: rewrite the dev abs-path -> payload path in text files ----
# (All dev-path-bearing files are text: launcher wrapper scripts, classpath.xml,
#  *.properties, libtool *.la. The Mach-O binaries hold it only as harmless
#  debug cruft and resolve siblings relatively, so they are left untouched.)
echo "[2/7] relocate dev path -> $PAYLOAD …"
# strip a stale nested stub if it slipped in
rm -rf "$PAYLOAD/Scilab-2027.0.0.app"
# grep -I skips binaries; rewrite only files that actually contain the old path
grep -rlI "$DEV" "$PAYLOAD" 2>/dev/null | while IFS= read -r f; do
  LC_ALL=C sed -i '' "s|$DEV|$PAYLOAD|g" "$f"
done
echo "      remaining dev-path refs in text files: $(grep -rlI "$DEV" "$PAYLOAD" 2>/dev/null | wc -l | tr -d ' ')"

# ---- 4. launcher (configurable JDK + own SCIHOME) --------------------------
echo "[3/7] launcher (JDK pin=$JDK_PIN, SCIHOME=$APP_SCIHOME)…"
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
echo "[4/7] Info.plist + icon…"
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
    <!-- TCC: the camera prompt macOS shows when a script opens a capture device
         (scicv/OpenCV). The engine binary also carries this string in its own
         __TEXT,__info_plist (cmake/ScilabAggregate.cmake) because the real
         Mach-O lives under Contents/Resources/scilab/.libs/, outside the bundle
         layout TCC would otherwise consult. Keep the two strings in sync with
         etc/macos-usage-descriptions.plist. -->
    <key>NSCameraUsageDescription</key><string>Scilab uses the camera when a script captures video, for example through the scicv (OpenCV) toolbox.</string>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
    <!-- Force native Apple-Silicon execution: refuse Rosetta and prefer the arm64 slice.
         The whole stack (scilab-bin, all module dylibs, the JDK 25 JVM, JOGL/GlueGen/MoltenVK)
         is already arm64; without these keys LaunchServices exposes an "Open using Rosetta"
         toggle that would drag the entire single-arch JVM process through x86_64 translation. -->
    <key>LSRequiresNativeExecution</key><true/>
    <key>LSArchitecturePriority</key>
    <array>
        <string>arm64</string>
    </array>
</dict>
</plist>
PLIST

# ---- 6. seed the app SCIHOME .scilab (autoload startup) --------------------
echo "[5/7] .scilab autoload startup…"
# The toolbox manager is a core Scilab module (modules/toolbox_manager) — it is rsync'd
# with the engine above, so the tbx* verbs load for free. .scilab only autoloads the
# user's enabled toolboxes. Write it only if absent (preserve the user's own edits).
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
#
# You usually do NOT need this. Toolboxes registered as `local` are loaded from
# their source directory (see installed_toolboxes.tbx), so a toolbox you rebuilt
# yourself is already live in the app -- rebuild only when Scilab's own native ABI
# changed under them.
#
# THREE BUGS LIVED HERE, all of which made a slow step look like a hung one:
#
#  * `SCIHOME=<dir>` as an ENVIRONMENT VARIABLE IS IGNORED by Scilab -- measured:
#    the child reported SCIHOME=~/.Scilab/scilab-branch-2027.0 (the dev home) while
#    this line passed the app's. So the rebuild silently targeted the WRONG toolbox
#    manifest. -scihome is the only thing that works, which is why the launcher
#    written above uses the flag, not the variable.
#  * `quit` at the end of -e never runs if tbxUpdate() raises, and Scilab then falls
#    through to the interactive prompt. Run from a terminal that is a real tty, it
#    waits on stdin forever. `exit(n)` inside errcatch always runs, and </dev/null
#    guarantees EOF terminates it even if something still reaches a prompt.
#  * `2>/dev/null | grep ...` threw away every error message AND buffered stdout, so
#    a step that legitimately takes tens of minutes (ilib_build runs a full
#    ./configure && make PER TOOLBOX) printed nothing at all while it worked.
#
# DO NOT PUT A FILTER BACK ON THIS PIPELINE. A `grep -iE "...|error|..."` lived here
# and was worse than no output at all: ilib_compile prints
#   "ilib_compile: Warning: No error code returned by the compilation but the error
#    output is not empty:"
# and then the compiler's actual text. The header matched the filter (it contains the
# word "error"); the diagnostics under it did not, so every run showed ten alarming
# headers with the explanation stripped out -- unreadable, and indistinguishable from
# a real failure. The output belongs to whoever is reading it; they decide what
# matters. tee keeps it on screen and on disk. Summarising or suppressing warnings
# here is the same mistake in a nicer costume.
if [ "$REBUILD_TBX" = "1" ]; then
  N_TBX=$(grep -cvE '^\s*(#|$)' "$APP_SCIHOME/installed_toolboxes.tbx" 2>/dev/null || echo 0)
  echo "[7/7] --rebuild-toolboxes: tbxUpdate() over $N_TBX toolbox(es) in $APP_SCIHOME"
  echo "      Each native toolbox runs ./configure && make -- tens of minutes is normal."
  echo "      NO NETWORK: tbxUpdate's git pull is opt-in and stays off here. A 24h+ wedge"
  echo "      was traced to it -- 52 of the registered toolboxes have SSH remotes and"
  echo "      tbx_sh() had no timeout, so one unreachable host stopped everything."
  echo "      Per-toolbox progress with [i/N] and elapsed seconds streams below, so a"
  echo "      stall is distinguishable from work."
  TBX_LOG="$APP_SCIHOME/rebuild-toolboxes-$(date +%Y%m%d-%H%M%S).log"
  echo "      Output is UNFILTERED: every compiler warning is printed verbatim."
  echo "      Full copy saved to: $TBX_LOG"
  # Driven from a temp script, not -e: the snippet needs nested Scilab strings, and
  # in a bash double-quoted string `""` is empty concatenation rather than an escaped
  # quote -- mprintf("x") silently became mprintf(x), a Scilab syntax error, which
  # would have produced exactly the silent hang this block exists to prevent.
  # DO NOT exec Resources/toolbox-manager/tbxmgr.sce here. That file carries its
  # OWN standalone copy of tbxUpdate (line ~174, the old one-argument signature),
  # and exec'ing it REDEFINES the core module's tbxUpdate with that stale copy --
  # visibly, as "Warning : redefining function: tbxUpdate". A run that did so
  # failed with "Undefined variable: name" and never executed the fixed version
  # at all. The engine's own modules/toolbox_manager macros are rsync'd into the
  # payload and load at startup, so tbxUpdate and tbx_manifest_read are already
  # available -- verified in the packaged app: the 2-argument signature resolves
  # without tbxmgr.sce being exec'd.
  #
  # The duplicate itself is a standing hazard: two implementations of the same
  # verbs, and only one of them gets fixed when someone fixes a bug.
  TBX_SCE="$(mktemp -t scilab-tbxupdate).sce"
  cat > "$TBX_SCE" <<'TBXEOF'
// Guard: fail loudly if a stale one-argument tbxUpdate is what we ended up with,
// rather than silently rebuilding through it.
if execstr("nargs = 0; nargs = macrovar(tbxUpdate); ", "errcatch") == 0 then
    if size(nargs(1), "*") < 2 then
        mprintf("tbxUpdate has the STALE 1-arg signature -- refusing to run.\n");
        mprintf("Something redefined it over the core module version.\n");
        exit(1);
    end
end
ierr = execstr("tbxUpdate();", "errcatch");
if ierr <> 0 then
    mprintf("tbxUpdate FAILED (ierr=%d): %s\n", ierr, lasterror());
end
// bool2s is REQUIRED. `ierr <> 0` is a boolean and exit() wants a numeric
// scalar, so exit(ierr <> 0) raises
//     exit: Wrong type for input argument #1: A scalar expected.
// and never exits. Measured: the process then ended with 231 for BOTH the
// success and the failure case, i.e. the status carried no information at
// all, and the script's "reported failures" check could not work.
exit(bool2s(ierr <> 0));
TBXEOF
  # -nouserstartup is LOAD-BEARING, not tidiness. Without it the app's
  # $SCIHOME/.scilab autoloads all 53 registered toolboxes before the rebuild
  # starts, and you cannot relink a library that is already loaded into the
  # running process: every native toolbox then fails instantly with
  # "<name> library is already loaded" or a configure error. Measured with the
  # flag: scicv_Init and pyEvalStr are absent (nothing autoloaded) while
  # tbxUpdate and tbx_manifest_read are still present, because the toolbox
  # manager is a core Scilab module rather than something .scilab pulls in.
  # The ordering then works out on its own -- tbxUpdate loads each toolbox only
  # AFTER building it, so nothing is ever loaded before its own rebuild.
  set +e
  JAVA_HOME="$(/usr/libexec/java_home -v "$JDK_PIN" 2>/dev/null)" \
    "$PAYLOAD/bin/scilab-cli" -nb -nouserstartup -scihome "$APP_SCIHOME" -f "$TBX_SCE" \
    < /dev/null 2>&1 | tee "$TBX_LOG"
  # `set +e` is required, and NOT interchangeable with the `|| true` that used to
  # be here. This script runs under `set -euo pipefail` (line 19): with pipefail
  # the pipeline carries scilab-cli's status, so a non-zero one aborts the script
  # instantly -- before the verdict below and before "Done." ever prints. That is
  # exactly what a run did after the filter was removed: the log stopped dead on
  # the last toolbox with no summary. `|| true` prevented that abort, but it also
  # reset PIPESTATUS to a single (0), so TBX_RC was ALWAYS 0 and the failure
  # branch below could never be reached -- the guard was decorative. Suspending
  # errexit around the pipeline keeps both the real status and the rest of the run.
  TBX_RC=${PIPESTATUS[0]}
  set -e
  rm -f "$TBX_SCE"
  if [ "${TBX_RC:-0}" -ne 0 ]; then
    echo "      toolbox rebuild reported failures (exit $TBX_RC) — see the lines above" >&2
  else
    echo "      (toolbox rebuild finished cleanly)"
  fi
else
  echo "[7/7] (skip toolbox rebuild — pass --rebuild-toolboxes to force)"
  echo "      Toolboxes registered 'local' load from their source dir, so a toolbox"
  echo "      you rebuilt yourself is already live without this flag."
fi

echo
echo "Done. App: $APP"
echo "Launch:   open \"$APP\"     (or Spotlight 'Scilab-2027.0.0')"
