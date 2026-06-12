#!/usr/bin/env bash
# Re-apply the macOS dev-tree runtime fixes after a `make` rebuild of Scilab.
# A relink drops the deployment-target and @loader_path tweaks; a reconfigure
# also resets modules.xml. This re-applies all of them. Idempotent — safe to
# re-run. See BUILDING-macOS.md for the full story.
cd "$(dirname "$0")" || exit 1
JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home

echo "[1/6] deployment target -> macOS 11.0 (GUI/plotting main-thread assertion)"
for b in scilab-bin scilab-cli-bin; do
  if [ -f ".libs/$b" ]; then
    vtool -set-build-version macos 11.0 11.0 -replace -output ".libs/$b.tmp" ".libs/$b" \
      && mv ".libs/$b.tmp" ".libs/$b" && chmod +x ".libs/$b" && codesign -f -s - ".libs/$b" \
      && echo "    patched .libs/$b"
  fi
done

echo "[2/6] xlnt -> @loader_path (spreadsheet module)"
SP=modules/spreadsheet/.libs/libscispreadsheet.2027.dylib
if [ -f "$SP" ]; then
  cp -f lib/thirdparty/libxlnt.1.6.1.dylib modules/spreadsheet/.libs/ 2>/dev/null \
    || cp -f ../xlnt-prefix/lib/libxlnt.1.6.1.dylib modules/spreadsheet/.libs/ 2>/dev/null || true
  install_name_tool -change libxlnt.1.6.1.dylib @loader_path/libxlnt.1.6.1.dylib "$SP" 2>/dev/null
  codesign -f -s - "$SP" && echo "    patched libscispreadsheet"
fi

echo "[3/6] xcos -> scicos @loader_path"
XC=modules/xcos/.libs/libscixcos.2027.dylib
if [ -f "$XC" ]; then
  install_name_tool \
    -change /usr/local/lib/scilab/libsciscicos.2027.dylib @loader_path/../../scicos/.libs/libsciscicos.2027.dylib \
    -change /usr/local/lib/scilab/libsciscicos_blocks.2027.dylib @loader_path/../../scicos_blocks/.libs/libsciscicos_blocks.2027.dylib \
    "$XC" 2>/dev/null
  codesign -f -s - "$XC" && echo "    patched libscixcos"
fi

echo "[4/6] activate helptools module (help window)"
sed -i '' 's|<module name="helptools" activate="no"/>|<module name="helptools" activate="yes"/>|' etc/modules.xml 2>/dev/null \
  && echo "    helptools active"

echo "[5/6] build all macros (genlib) — MUST run after the binary/dylib fixes above:"
echo "      during 'make' the macro build runs too early and scilab-cli crashes, leaving a"
echo "      0-byte modules/core/macros/lib that breaks every later startup. Rebuild it here."
find modules -path '*/macros/lib' -size 0 -delete 2>/dev/null   # drop crash-truncated libs
if [ -x ./bin/scilab-cli ]; then
  JAVA_HOME=$JDK ./bin/scilab-cli -ns -noatomsautoload -nouserstartup -quit \
    -f modules/functions/scripts/buildmacros/buildmacros.sce > /tmp/scilab-buildmacros.log 2>&1
  echo "    macros built: $(ls modules/*/macros/lib 2>/dev/null | wc -l | tr -d ' ') libs  (log: /tmp/scilab-buildmacros.log)"
fi

echo "[6/6] menu-bar / Dock name -> Scilab-2027.0.0"
NAME="Scilab-2027.0.0"
if [ -f .libs/scilab-bin ]; then
  # .libs/$NAME is the same binary under a versioned name; the GUI process's
  # executable filename is what macOS shows in the menu bar and Dock.
  ln -f .libs/scilab-bin ".libs/$NAME" 2>/dev/null \
    || { cp -f .libs/scilab-bin ".libs/$NAME" && codesign -f -s - ".libs/$NAME"; }
  # the libtool wrapper is regenerated on every relink -> re-point it
  sed -i '' "s/program='scilab-bin'/program='$NAME'/" scilab-bin 2>/dev/null
  echo "    wrapper + .libs/$NAME  ->  process/menu name '$NAME'"
fi

echo
echo "Done."
echo "  Finder:    double-click Scilab-2027.0.0.app    (GUI only, no Terminal window)"
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab           (GUI + console output)"
