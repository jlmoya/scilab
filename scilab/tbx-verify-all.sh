#!/usr/bin/env bash
# Sweep-verify toolboxes against this dev build: one FRESH scilab-adv-cli per
# toolbox (isolated -scihome + hard timeout) so a hang/crash can't kill the sweep.
# Usage:  ./tbx-verify-all.sh [name...]     (no args = full SciLabProjects catalog)
# Env:    TBX_TIMEOUT (s, default 300) | TBX_REPORT (default ./tbx-verify-report.tsv)
# Failing runs keep their scratch dir (path printed) for debugging; passing runs clean up.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
CFG="$HOME/.config/scilab-app/java_home"
if   [ -s "$CFG" ];           then export JAVA_HOME="$(sed -n '1p' "$CFG")"
elif [ -n "${JAVA_HOME:-}" ]; then :
else export JAVA_HOME="$(/usr/libexec/java_home -v 25)"; fi
PROJECTS="$HOME/Projects/SciLabProjects"
# Toolboxes installed from a remote (tbxInstall clones them) live in the REAL
# SCIHOME's toolboxes/, not under SciLabProjects. The throwaway -scihome below
# exists to isolate mutable STATE (prefs, manifest), not to hide installed
# toolboxes, so that directory is linked into each scratch home -- otherwise
# every SCIHOME-resident toolbox fails with "toolbox dir not found", which is
# what happened to helptbx (a declared dependency of distfun and stixbox).
INSTALLED="${TBX_INSTALLED_DIR:-$HOME/.Scilab/scilab-app-2027/toolboxes}"
OUT="${TBX_REPORT:-$HERE/tbx-verify-report.tsv}"
TIMEOUT="${TBX_TIMEOUT:-300}"
: > "$OUT"
names=("$@")
if [ ${#names[@]} -eq 0 ]; then
    for d in "$PROJECTS"/*/; do
        d="${d%/}"; n="${d##*/}"
        [ -f "$d/loader.sce" ] || [ -f "$d/builder.sce" ] && names+=("$n")
    done
    # ...plus anything installed only under the real SCIHOME
    if [ -d "$INSTALLED" ]; then
        for d in "$INSTALLED"/*/; do
            d="${d%/}"; n="${d##*/}"
            [ -e "$PROJECTS/$n" ] && continue
            { [ -f "$d/loader.sce" ] || [ -f "$d/builder.sce" ]; } && names+=("$n")
        done
    fi
fi
pass=0; fail=0
for n in "${names[@]}"; do
    sch="$(mktemp -d "${TMPDIR:-/tmp}/tbxverify-$n-XXXXXX")"
    [ -d "$INSTALLED" ] && ln -s "$INSTALLED" "$sch/toolboxes"
    TBX_NAME="$n" TBX_OUT="$sch/result.tsv" gtimeout "$TIMEOUT" \
        "$HERE/bin/scilab-adv-cli" -nb -scihome "$sch" -f "$HERE/tbx-verify-one.sce" \
        > "$sch/log.txt" 2>&1
    rc=$?
    if   [ -s "$sch/result.tsv" ]; then cat "$sch/result.tsv" >> "$OUT"
    elif [ $rc -eq 124 ];          then printf '%s\tTIMEOUT\t%ss; scratch=%s\n' "$n" "$TIMEOUT" "$sch" >> "$OUT"
    else                                printf '%s\tCRASH\trc=%s; scratch=%s\n' "$n" "$rc" "$sch" >> "$OUT"; fi
    tail -1 "$OUT"
    if [ "$(awk -F'\t' 'END{print $2}' "$OUT")" = "PASS" ]; then
        pass=$((pass+1)); rm -rf "$sch"
    else
        fail=$((fail+1))
        echo "  scratch kept: $sch"
    fi
done
echo
echo "== $pass PASS / $fail non-PASS of ${#names[@]} =="
echo "== PASS names (paste into cfg.verified) =="
awk -F'\t' '$2=="PASS"{printf "\"%s\" ", $1}' "$OUT"; echo
