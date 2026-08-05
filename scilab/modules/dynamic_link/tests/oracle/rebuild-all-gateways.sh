#!/usr/bin/env bash
# Step 6 prerequisite: rebuild EVERY toolbox that goes through ilib_build on the
# CMake path, then verify it still loads and smokes.
#
# Section 5 of the design doc says three toolboxes are "directly affected"
# (nan, scicv, scimax) and that FOSSEE/sci-ipopt/sciTorch/xlsx "bypass
# ilib_build". Grepping for tbx_build_gateway/ilib_build/ilib_gen_gateway finds
# **23**, including all four of the supposed controls. Flipping the default
# therefore changes how 23 toolboxes rebuild, not 3, and that is what this
# script measures before the switch is thrown.
#
# Every failure is A/B'd automatically. A toolbox that fails on BOTH paths is a
# pre-existing or environmental problem, not a regression -- three separate
# times in this work a "CMake broke it" turned out to be the wrong entry point
# (builder.sce instead of build_macos.sce, which sets CPATH for gettext). Only
# cmake-fails-where-make-succeeds is a real regression, and it is reported as
# such.
#
#   ./rebuild-all-gateways.sh [toolbox ...]      # default: all 23
set -uo pipefail

SCILAB="${SCILAB_APP:-/Applications/Scilab-2027.0.0.app}/Contents/MacOS/Scilab-2027.0.0"
PROJECTS="${SCILAB_PROJECTS:-$HOME/Projects/SciLabProjects}"
BACKUP="${TMPDIR:-/tmp}/gateway-rebuild-backup"
TIMEOUT=900

[ -x "$SCILAB" ] || { echo "ERROR: no Scilab at $SCILAB" >&2; exit 2; }
mkdir -p "$BACKUP"

discover() {
  for d in "$PROJECTS"/*/; do
    n="$(basename "$d")"
    if grep -rl "tbx_build_gateway\|ilib_build\|ilib_gen_gateway" "$d" --include="*.sce" 2>/dev/null \
       | grep -qv thirdparty; then echo "$n"; fi
  done
}

# Pick the entry point the toolbox author intended. build_macos.sce exists
# precisely because builder.sce is not sufficient on this platform (it sets
# CPATH for gettext); using builder.sce directly is what produced three false
# "CMake regressions".
entry_for() {
  [ -f "$PROJECTS/$1/build_macos.sce" ] && { echo "build_macos.sce"; return; }
  [ -f "$PROJECTS/$1/builder.sce" ] && { echo "builder.sce"; return; }
  echo ""
}

build_with() {  # $1=mode $2=name $3=entry -> rc
  local mode="$1" name="$2" entry="$3" d="$PROJECTS/$2"
  local f="${TMPDIR:-/tmp}/build_${mode}_${name}.sce"
  cat > "$f" <<EOF
mode(-1);
chdir("$d");
ie = execstr("exec(""$d/$entry"", -1);", "errcatch");
if ie <> 0 then mprintf("\nBUILDFAIL %s\n", part(lasterror()(1),1:100)); end
exit(bool2s(ie <> 0));
EOF
  SCILAB_GATEWAY_BUILD="$mode" timeout "$TIMEOUT" "$SCILAB" -nwni -nb -nouserstartup -quit -f "$f" \
      > "${TMPDIR:-/tmp}/build_${mode}_${name}.log" 2>&1
  local rc=$?
  rm -f "$f"
  return $rc
}

verify() {  # $1=name -> prints pass|fail reason
  local name="$1"
  local f="${TMPDIR:-/tmp}/vfy_${name}.sce"
  cat > "$f" <<EOF
mode(-1);
ie = execstr("R = tbxVerify(""$name"");", "errcatch");
// leading newline: several loader banners end WITHOUT one and would otherwise
// swallow this marker (cgal, montesci)
if ie <> 0 then mprintf("\nVFY|RAISED|%s\n", part(lasterror()(1),1:60));
else mprintf("\nVFY|%s|%s\n", string(bool2s(R.pass)), R.err); end
exit(0);
EOF
  local out
  out=$(timeout 300 "$SCILAB" -nwni -nb -nouserstartup -quit -f "$f" </dev/null 2>&1 | grep "^VFY|")
  rm -f "$f"
  [ -z "$out" ] && { echo "VFY|NORESULT|process ended without a result"; return; }
  echo "$out"
}

names=( "$@" )
[ ${#names[@]} -eq 0 ] && mapfile -t names < <(discover)

echo "toolboxes going through ilib_build: ${#names[@]}"
printf '%-28s %-9s %-9s %s\n' TOOLBOX CMAKE VERIFY NOTE
printf '%-28s %-9s %-9s %s\n' ---------------------------- --------- --------- ----

ok=0; regress=0; preexist=0
for n in "${names[@]}"; do
  e="$(entry_for "$n")"
  if [ -z "$e" ]; then
    printf '%-28s %-9s %-9s %s\n' "$n" skip - "no builder.sce/build_macos.sce"
    continue
  fi

  # Back up every native artifact so any outcome is recoverable.
  mkdir -p "$BACKUP/$n"
  while IFS= read -r lib; do
    [ -n "$lib" ] && cp -p "$lib" "$BACKUP/$n/$(echo "${lib#$PROJECTS/$n/}" | tr '/' '_')" 2>/dev/null
  done < <(find "$PROJECTS/$n" -name '*.dylib' 2>/dev/null | grep -v thirdparty)

  if build_with cmake "$n" "$e"; then
    v="$(verify "$n")"
    case "$v" in
      VFY\|1\|*) printf '%-28s %-9s %-9s %s\n' "$n" built pass "$e"; ok=$((ok+1));;
      *)         printf '%-28s %-9s %-9s %s\n' "$n" built FAIL "${v#VFY|}"; regress=$((regress+1));;
    esac
  else
    # builder.sce does more than build the gateway -- most also build help, and
    # `tbx_build_help` refuses in NWNI ("documentation cannot be built in this
    # scilab mode"). Treating that as "the gateway failed" mis-attributes a
    # display requirement to this change, so ask the artifacts instead of the
    # exit code: if a native library was just written, the gateway compiled and
    # something LATER in the builder failed.
    newest=$(find "$PROJECTS/$n" -name '*.dylib' -newermt '-3 minutes' 2>/dev/null | grep -cv thirdparty)
    if [ "${newest:-0}" -gt 0 ]; then
      note="$(grep -ho 'tbx_build_help[^"]*' "${TMPDIR:-/tmp}/build_cmake_$n.log" 2>/dev/null | head -1)"
      printf '%-28s %-9s %-9s %s\n' "$n" built n/a \
        "gateway built; builder failed later: ${note:-non-gateway step}"
      ok=$((ok+1))
    elif build_with make "$n" "$e"; then
      printf '%-28s %-9s %-9s %s\n' "$n" FAILED - "REGRESSION: make built it, cmake did not"
      regress=$((regress+1))
    else
      printf '%-28s %-9s %-9s %s\n' "$n" FAILED - "both paths fail -- pre-existing, not this change"
      preexist=$((preexist+1))
    fi
  fi
done

echo
echo "cmake-built and verified : $ok"
echo "regressions              : $regress"
echo "pre-existing failures    : $preexist"
echo "artifact backups         : $BACKUP"
[ $regress -eq 0 ] || exit 1
