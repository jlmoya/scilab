#!/usr/bin/env bash
# Step 5 gate for docs/design/dynamic-link-cmake-migration.md: build the same
# gateway through BOTH build paths and prove the artifacts are equivalent.
#
# The comparison is deliberately on the LINKED ARTIFACT -- exported symbols and
# recorded dependencies -- not on command lines and not on exit status.
#
# That choice is load-bearing. Section 12 records a CMake configure cache that
# was 2.5x faster, exited 0, produced a byte-identical-sized library with an
# IDENTICAL exported symbol set, and silently omitted libgfortran. It differed
# in exactly two `otool -L` lines. A gate that compared anything cheaper would
# have passed it.
#
# Command lines cannot be compared literally either: the autotools path leaks
# the flags Scilab's own configure was run with (an openssl -I on this machine),
# which the CMake path correctly does not reproduce. See invariant 7 in §11.
#
#   ./gate-both-paths.sh [--keep]
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP="${SCILAB_APP:-/Applications/Scilab-2027.0.0.app}"
SCILAB="$APP/Contents/MacOS/Scilab-2027.0.0"
KEEP=0
[ "${1:-}" = "--keep" ] && KEEP=1

[ -x "$SCILAB" ] || { echo "ERROR: no Scilab at $SCILAB (override with SCILAB_APP)" >&2; exit 2; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/gate-both.XXXXXX")"
cleanup() { [ "$KEEP" -eq 1 ] || rm -rf "$WORK"; }
trap cleanup EXIT

# The matrix from §5 gate 1: C, C++, and mixed C+Fortran. Fortran is the case
# most likely to break (§6) and the ONLY one that can expose a dropped Fortran
# runtime, so it is not optional.
#   name | sources
CASES=(
  "GateC|gw_c.c"
  "GateX|gw_cxx.cpp"
  "GateF|gw_c.c gw_f.f"
)

build_one() {  # $1=mode  $2=name  $3=sources  -> prints artifact path, or empty
  local mode="$1" name="$2" srcs="$3"
  local d="$WORK/$mode/$name"
  mkdir -p "$d"
  for s in $srcs; do cp "$HERE/$s" "$d/"; done

  local entry="sci_gw_c"
  case "$srcs" in *gw_cxx.cpp*) entry="sci_gw_cxx";; esac
  # DOUBLED quotes: this list is interpolated into a Scilab string literal that
  # is itself passed to execstr, so a single " would close the outer string.
  local sciList="" ; for s in $srcs; do sciList="$sciList\"\"$s\"\","; done
  sciList="${sciList%,}"

  cat > "$d/build.sce" <<EOF
ilib_verbose(0);
cd("$d");
ie = execstr("ilib_build(""lib$name"", [""$entry"",""$entry""], [$sciList], []);", "errcatch");
if ie <> 0 then mprintf("BUILD-ERROR %s\n", lasterror()(1)); end
exit(bool2s(ie <> 0));
EOF
  SCILAB_GATEWAY_BUILD="$mode" "$SCILAB" -nwni -nb -nouserstartup -quit -f "$d/build.sce" \
      > "$d/build.log" 2>&1
  local rc=$?
  if [ $rc -ne 0 ]; then
    echo "" ; return 1
  fi
  local art="$d/lib$name.dylib"
  [ -f "$art" ] && echo "$art" || { echo ""; return 1; }
}

pass=0; fail=0
printf '%-8s  %-9s %-9s %-9s %-9s\n' CASE MAKE CMAKE EXPORTS DEPS
printf '%-8s  %-9s %-9s %-9s %-9s\n' -------- --------- --------- --------- ---------

for c in "${CASES[@]}"; do
  name="${c%%|*}"; srcs="${c#*|}"

  a="$(build_one make  "$name" "$srcs")"; a_ok=$?
  b="$(build_one cmake "$name" "$srcs")"; b_ok=$?

  a_s=$([ $a_ok -eq 0 ] && echo built || echo FAILED)
  b_s=$([ $b_ok -eq 0 ] && echo built || echo FAILED)

  if [ $a_ok -ne 0 ] || [ $b_ok -ne 0 ]; then
    printf '%-8s  %-9s %-9s %-9s %-9s\n' "$name" "$a_s" "$b_s" - -
    fail=$((fail+1)); continue
  fi

  # Exported symbols must match exactly.
  if diff -q <(nm -gU "$a" | awk '{print $NF}' | sort) \
             <(nm -gU "$b" | awk '{print $NF}' | sort) >/dev/null; then
    e_s=match; else e_s=DIFFER; fi

  # Dependencies, ignoring the library's own LC_ID_DYLIB: that differs by
  # design, libtool emitting lib<name>.0.dylib plus an unversioned symlink where
  # the CMake path emits the single lib<name>.dylib (§11 invariant 4).
  norm() { otool -L "$1" | tail -n +2 | awk '{print $1}' | grep -v "/usr/local/lib/scilab/lib" | sort; }

  # The comparison is ASYMMETRIC, and deliberately so.
  #
  # A gateway with NO Fortran sources is ALLOWED to lose libgfortran/libquadmath
  # under CMake: the autotools path appends $(FLIBS) unconditionally, so its
  # pure-C and pure-C++ gateways link a Fortran runtime they never call (§11
  # invariant 5). Dropping an unused dependency is the intended improvement, and
  # flagging it on every C gateway would train everyone to ignore this gate --
  # which is how a gate stops being one.
  #
  # A gateway WITH Fortran sources must match EXACTLY. That is where the §12
  # cache bug lived: it produced identical exports, identical size, exit 0, and
  # no Fortran runtime. Allowing the same two libraries to vanish there would
  # have let it straight through.
  has_fortran=0; extra=""
  case " $srcs " in *.f\ *|*.f90\ *|*.f95\ *) has_fortran=1;; esac

  if diff -q <(norm "$a") <(norm "$b") >/dev/null; then
    d_s=match
  elif [ "$has_fortran" -eq 1 ]; then
    d_s=DIFFER
  else
    # No Fortran here: accept ONLY the disappearance of the Fortran runtime.
    # Any other line in the diff -- including one appearing on the CMake side --
    # still fails.
    extra="$(diff <(norm "$a") <(norm "$b") | grep '^[<>]' \
             | grep -vE '^< .*/lib(gfortran|quadmath)\.[0-9]+\.dylib$' || true)"
    if [ -z "$extra" ]; then d_s="ok(-FLIBS)"; else d_s=DIFFER; fi
  fi

  printf '%-8s  %-9s %-9s %-9s %-9s\n' "$name" "$a_s" "$b_s" "$e_s" "$d_s"
  if [ "$e_s" = match ] && { [ "$d_s" = match ] || [ "$d_s" = "ok(-FLIBS)" ]; }; then
    pass=$((pass+1))
  else
    fail=$((fail+1))
    echo "    --- dependency diff (autotools < | > cmake) ---"
    diff <(norm "$a") <(norm "$b") | sed 's/^/    /'
  fi
done

echo
echo "gate: $pass passed, $fail failed"
[ "$KEEP" -eq 1 ] && echo "artifacts kept in $WORK"
[ $fail -eq 0 ] || exit 1
