#!/usr/bin/env bash
# Full from-source build of Scilab (branch 2027) on macOS arm64 with JDK 25.
#
# CMake (native + orchestration) + Maven (Java). autotools and Ant were retired
# 2026-07-21 (tag `autotools-ant-retired`): there is no ./configure, no Makefile.am,
# no build.xml. CMake computes what configure used to (machine.h, version.h, the
# compiler flags, the generated files) and drives Maven for the 24 module jars.
#
# See docs/design/build-cmake-maven-migration.md and build-cmake-driver.md.
# Run ./build-macos.sh --help for the full option list.
set -e
cd "$(dirname "$0")"

usage() {
cat <<'HELPDOC'
build-macos.sh — full from-source build of Scilab 2027 on macOS arm64 (JDK 25).

USAGE
  ./build-macos.sh [--release | --debug | --both] [options] [-- <cmake args>]
  ./build-macos.sh --clean
  ./build-macos.sh --help

BUILD TYPE  (default: --release)
  --release   -DNDEBUG -g1 -O2 -fwrapv     the shipping build.
              -fwrapv is the UB-miscompile hardening: several legacy C/Fortran
              routines rely on wrapping signed overflow and are miscompiled at
              -O2 without it.
  --debug     -O0 -g3                      unoptimised, full debug info.
              Use for readable stack traces and stepping. -fwrapv is absent by
              design here: that class of UB is benign at -O0.
  --both      builds BOTH, debug first and release last.
              The order matters. Both types drop into the same
              modules/*/.libs/, so whichever runs last is what the tree ends up
              holding. Finishing on release leaves it packageable.

  Each type has its own build directory, so alternating between them does not
  force a full reconfigure and recompile:
      --release  ->  build-cmake/          (historic name; the parity harness,
                                            the drop-in targets and the design
                                            docs all refer to it)
      --debug    ->  build-cmake-debug/

OPTIONS
  --jobs N|auto|max
              parallelism. Default: auto.
                auto  a computed value that leaves the machine usable — see
                      PARALLELISM below.
                max   every logical core (what this script did before --jobs
                      existed).
                N     exactly N.
  --skip-doc  skip step 4, the DocBook -> JavaHelp build. It is by far the
              slowest step and is rarely needed while iterating on code. The
              help jars already in modules/helptools/target/ are LEFT ALONE and
              therefore go stale — the script says so loudly at the end.
  --jdk PATH  JDK to build against (default: the JDK 25 under
              /Library/Java/JavaVirtualMachines). Must contain bin/javac.
  --clean     delete both CMake build trees and the build-type stamp, then EXIT
              without building. Deliberately keeps modules/*/.libs/,
              modules/*/target/ (the 24 module jars AND the help jars) and the
              compiled macro .bin files: the doc step that produces the help
              jars is the slowest part of the build, and discarding it on a
              routine clean costs a lot for no benefit. Removing the build trees
              already forces a full native reconfigure and recompile.
  --          everything after this is passed verbatim to the cmake configure
              step, e.g.   ./build-macos.sh --debug -- -DSOME_OPTION=ON
  -h, --help  this text.

PARALLELISM  (--jobs auto)
  Reasoned, not benchmarked — if you measure something better on your machine,
  pass --jobs N. The computation:

    Apple Silicon:  P-cores + half the E-cores
        E-cores deliver real throughput at roughly a third of a P-core's speed.
        Loading them fully adds contention and heat for little gain, so half is
        the compromise. On a 12-core M-series (8P + 4E) this gives 10.
    Intel:          logical cores - 2
        Leaves the machine responsive.
    Then capped by memory: RAM_GB / 2, because the template-heavy C++ in
        modules/ast and modules/types can take upwards of a gigabyte per
        compiler process, and swapping is far more expensive than the core it
        would have saved.
    Never below 1.

  The headroom is not politeness. A saturated machine distorts anything timed
  that runs alongside the build.

WHAT THE BUILD DOES  (four steps, same for either type)
  1. cmake configure       locates the Homebrew deps, computes what ./configure
                           used to (machine.h, version.h, the compiler flags).
  2. drop-in-all           64 module dylibs + 21 fold-in OBJECT libs + the two
                           aggregates + both executables + the 24 Maven jars,
                           each copied into place so the tree is runnable.
  3. macros                .sci -> .bin. Separate from step 2 because it runs
                           the just-built scilab-cli-bin (a bootstrap loop).
  4. doc                   DocBook -> JavaHelp jars. Also circular, and by far
                           the slowest step. Skippable with --skip-doc.

  Each step reports its elapsed time, and the run reports a total.

BUILD TYPE AND PACKAGING
  Both types drop into the SAME modules/*/.libs/, which is where bin/scilab and
  package-macos.sh read their binaries. A debug build therefore replaces the
  release one silently. To stop an unoptimised Scilab being shipped by accident,
  this script writes .scilab-build-type after step 2, and package-macos.sh
  refuses a debug payload unless given --allow-debug. A missing stamp is
  reported as "unknown" rather than assumed to be release.

AFTERWARDS
  Run:      JAVA_HOME=<jdk25> ./bin/scilab
  Package:  ./package-macos.sh                 (release)
            ./package-macos.sh --allow-debug   (debug, deliberately)

EXAMPLES
  ./build-macos.sh                          release build
  ./build-macos.sh --debug                  debug build, for stack traces
  ./build-macos.sh --both                   both; tree left release-ready
  ./build-macos.sh --skip-doc               fast iteration; help jars go stale
  ./build-macos.sh --jobs 4                 limit parallelism
  ./build-macos.sh --jdk /path/to/jdk-25    build against another JDK
  ./build-macos.sh --clean                  wipe the build trees, build nothing
  ./build-macos.sh --clean && ./build-macos.sh --both

  Prerequisites are checked before anything is built: CMake >= 3.20, a JDK with
  bin/javac, and gfortran (a stray flang on PATH otherwise wins and the driver
  hard-fails). See docs/design/build-cmake-maven-migration.md.
HELPDOC
}

# ---- defaults --------------------------------------------------------------------
BUILD_TYPES=(release)
DO_CLEAN=0
SKIP_DOC=0
JOBS_SPEC=auto
JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
GFORTRAN=/opt/homebrew/bin/gfortran
EXTRA_CMAKE_ARGS=()

while [ $# -gt 0 ]; do
    case "$1" in
        --release)  BUILD_TYPES=(release) ;;
        --debug)    BUILD_TYPES=(debug) ;;
        # Debug FIRST, release LAST, and the order is the whole point: both types drop
        # into the same modules/*/.libs/, so whichever runs last is what the tree is left
        # holding. Ending on release leaves it packageable; ending on debug would leave a
        # tree that package-macos.sh refuses.
        --both)     BUILD_TYPES=(debug release) ;;
        --clean)    DO_CLEAN=1 ;;
        --skip-doc) SKIP_DOC=1 ;;
        --jobs)     JOBS_SPEC="${2:?--jobs needs a value (N | auto | max)}"; shift ;;
        --jdk)      JDK="${2:?--jdk needs a path}"; shift ;;
        --)         shift; EXTRA_CMAKE_ARGS=("$@"); break ;;
        -h|--help)  usage; exit 0 ;;
        *)
            echo "build-macos.sh: unknown argument '$1'" >&2
            echo >&2
            usage >&2
            exit 2 ;;
    esac
    shift
done

# The release tree keeps the historic name: the parity harness, the drop-in targets and
# every doc reference say build-cmake.
build_dir_for() { [ "$1" = debug ] && echo build-cmake-debug || echo build-cmake; }

fmt_dur() {  # seconds -> "2m 07s" / "43s"
    local s=$1
    if [ "$s" -ge 60 ]; then printf '%dm %02ds' $((s / 60)) $((s % 60)); else printf '%ds' "$s"; fi
}

# ---- --clean ---------------------------------------------------------------------
# Removes the CMake build trees and the build-type stamp, then exits WITHOUT building:
# "clean" and "build" are separate intentions, and folding them together makes a bare
# --clean ambiguous about what it would go on to build.
#
# What it deliberately does NOT remove: the artefacts already dropped into
# modules/*/.libs/, modules/*/target/ (the 24 module jars AND the help jars) and the
# compiled macro .bin files. Those are gitignored build output too, but the doc target
# that produces the help jars is by far the slowest step in the whole build, and blowing
# it away on a routine "clean" would turn a 30-minute rebuild into a much longer one for
# no benefit -- deleting the build trees already forces a full native reconfigure and
# recompile, which is what a clean is normally wanted for.
#
# CAVEAT worth knowing: because .libs/ survives, a tree cleaned and then only partially
# rebuilt still LOOKS runnable -- bin/scilab will happily load the previous build's
# binaries. The stamp is removed for exactly that reason, so package-macos.sh reports
# the build type as unknown rather than vouching for stale artefacts.
if [ "$DO_CLEAN" -eq 1 ]; then
    echo "Cleaning build trees…"
    removed=0
    for d in build-cmake build-cmake-debug; do
        if [ -e "$d" ]; then
            echo "  removing $d/ ($(du -sh "$d" 2>/dev/null | cut -f1))"
            rm -rf "${d:?}"
            removed=1
        fi
    done
    if [ -e .scilab-build-type ]; then
        echo "  removing .scilab-build-type"
        rm -f .scilab-build-type
        removed=1
    fi
    if [ "$removed" -eq 0 ]; then echo "  nothing to remove — already clean"; fi
    echo
    echo "Kept (regenerating these is far slower than recompiling):"
    echo "  modules/*/.libs/     dropped-in binaries from the previous build"
    echo "  modules/*/target/    the 24 module jars + the help jars (the doc step)"
    echo "  modules/*/macros/    compiled .bin macro libraries"
    echo
    echo "Now run ./build-macos.sh [--release|--debug|--both] for a full rebuild."
    exit 0
fi

# ---- parallelism -----------------------------------------------------------------
# See PARALLELISM in --help. Reasoned rather than benchmarked; --jobs N overrides.
compute_auto_jobs() {
    local logical p e j mem_gb mem_cap
    logical="$(sysctl -n hw.logicalcpu 2>/dev/null || echo 4)"
    p="$(sysctl -n hw.perflevel0.logicalcpu 2>/dev/null || true)"
    e="$(sysctl -n hw.perflevel1.logicalcpu 2>/dev/null || true)"

    if [ -n "$p" ] && [ -n "$e" ]; then
        # Apple Silicon: all P-cores plus half the E-cores. E-cores are worth using but
        # are far slower, so loading them fully mostly buys contention.
        j=$(( p + e / 2 ))
    else
        # Intel: keep two cores for the rest of the system.
        j=$(( logical - 2 ))
    fi

    # Memory ceiling: the operator templates in modules/types and the visitors in
    # modules/ast are the expensive TUs, and swapping costs far more than the core saved.
    mem_gb=$(( $(sysctl -n hw.memsize 2>/dev/null || echo 8589934592) / 1073741824 ))
    mem_cap=$(( mem_gb / 2 ))
    [ "$mem_cap" -lt 1 ] && mem_cap=1
    [ "$j" -gt "$mem_cap" ] && j="$mem_cap"

    [ "$j" -lt 1 ] && j=1
    echo "$j"
}

case "$JOBS_SPEC" in
    auto) JOBS="$(compute_auto_jobs)"
          JOBS_WHY="auto: $(sysctl -n hw.perflevel0.logicalcpu 2>/dev/null || echo '?')P+$(sysctl -n hw.perflevel1.logicalcpu 2>/dev/null || echo '?')E cores, $(( $(sysctl -n hw.memsize) / 1073741824 ))GB RAM" ;;
    max)  JOBS="$(sysctl -n hw.ncpu)"; JOBS_WHY="max: every logical core" ;;
    ''|*[!0-9]*)
          echo "build-macos.sh: --jobs wants a positive number, 'auto' or 'max' (got '$JOBS_SPEC')" >&2
          exit 2 ;;
    *)    JOBS="$JOBS_SPEC"; JOBS_WHY="explicit"
          if [ "$JOBS" -lt 1 ]; then echo "build-macos.sh: --jobs must be >= 1" >&2; exit 2; fi ;;
esac

# ---- preflight -------------------------------------------------------------------
# Every one of these otherwise fails minutes into the run with a message that does not
# name the real problem. A build this long should refuse in a second instead.
preflight() {
    local fail=0

    if ! command -v cmake >/dev/null; then
        echo "  MISSING: cmake — install it (brew install cmake)" >&2; fail=1
    else
        local v major minor
        v="$(cmake --version | head -1 | awk '{print $3}')"
        major="${v%%.*}"; minor="$(echo "$v" | cut -d. -f2)"
        if [ "$major" -lt 3 ] || { [ "$major" -eq 3 ] && [ "$minor" -lt 20 ]; }; then
            echo "  TOO OLD: cmake $v — the top-level CMakeLists requires >= 3.20" >&2; fail=1
        else
            echo "  cmake      $v"
        fi
    fi

    if [ ! -x "$JDK/bin/javac" ]; then
        echo "  MISSING: no bin/javac under JDK '$JDK'" >&2
        echo "           pass --jdk /path/to/jdk, or install JDK 25" >&2
        fail=1
    else
        echo "  jdk        $("$JDK/bin/javac" -version 2>&1 | awk '{print $2}')  ($JDK)"
    fi

    # Fortran is the one dependency worth pinning: a stray flang on PATH otherwise wins
    # and the driver hard-fails well into the configure step.
    if [ ! -x "$GFORTRAN" ]; then
        echo "  MISSING: gfortran at $GFORTRAN (brew install gcc)" >&2; fail=1
    else
        echo "  gfortran   $("$GFORTRAN" -dumpversion 2>/dev/null)"
    fi

    # Homebrew deps: a WARNING, never a hard failure. CMake's own find_package errors are
    # clear enough, and this list would rot faster than the build it guards.
    if command -v brew >/dev/null; then
        local missing=()
        for f in openblas arpack fftw hdf5 matio suite-sparse eigen pcre curl libxml2; do
            brew --prefix "$f" >/dev/null 2>&1 || missing+=("$f")
        done
            if [ ${#missing[@]} -gt 0 ]; then
            echo "  note: brew formulae not found: ${missing[*]} (configure may still locate them)"
        fi
    fi

    # An `if`, NOT `[ ... ] && { ... }`: as the last statement of a function the && list
    # returns non-zero whenever the test is false, so a SUCCESSFUL preflight made
    # preflight itself return 1 and `set -e` killed the build right after printing the
    # versions. Same reason the --jobs branch below is an if.
    if [ "$fail" -eq 1 ]; then
        echo >&2
        echo "Preflight failed — nothing was built." >&2
        exit 1
    fi
    return 0
}

echo "Preflight…"
preflight
echo "  jobs       $JOBS  ($JOBS_WHY)"

# ---- build -----------------------------------------------------------------------
run_step() {  # run_step "<label>" <cmake args...>
    local label="$1"; shift
    local t0=$SECONDS
    echo "$label"
    "$@"
    echo "      ↳ $(fmt_dur $((SECONDS - t0)))"
}

build_one() {
    local build_type="$1"
    local build enable_debug t0=$SECONDS
    build="$(build_dir_for "$build_type")"
    [ "$build_type" = debug ] && enable_debug=ON || enable_debug=OFF

    echo
    echo "=================================================================="
    echo " Building Scilab — $build_type  ($build/)"
    echo "=================================================================="

    run_step "[1/4] cmake configure ($build_type, gfortran) -> $build/…" \
        cmake -S . -B "$build" -DCMAKE_Fortran_COMPILER="$GFORTRAN" \
              -DSCILAB_ENABLE_DEBUG="$enable_debug" "${EXTRA_CMAKE_ARGS[@]}"

    # drop-in-all = the 64 module dylibs + the 21 fold-in OBJECT libs + the two aggregates
    # (libscilab / libscilab-cli) + both executables + sci-java-all (the 24 Maven module
    # jars, built into modules/<m>/target/). Each artifact is copied into place, so the
    # tree is runnable straight afterwards.
    run_step "[2/4] build the app — native + Maven jars (-j$JOBS)…" \
        cmake --build "$build" --target drop-in-all -j"$JOBS"

    # Stamp what now sits in modules/*/.libs/.
    #
    # Both build types drop into the SAME .libs/ layout, because that is where bin/scilab
    # and package-macos.sh read their binaries from. So a debug build silently replaces
    # the release one there, and nothing downstream could tell -- you could build debug,
    # forget, package, and ship an unoptimised Scilab with no warning anywhere. The stamp
    # is what lets package-macos.sh notice (it refuses a debug payload unless told
    # otherwise).
    #
    # Written AFTER drop-in, so it describes what is actually in .libs/ rather than what
    # was merely configured. Running `cmake --build <dir> --target drop-in-all` by hand
    # bypasses this and leaves the stamp stale -- package-macos.sh treats a missing stamp
    # as unknown and says so rather than assuming release.
    printf '%s\n' "$build_type" > .scilab-build-type

    # The macro library (.sci -> .bin) is a SEPARATE target, deliberately not part of
    # drop-in-all: it runs the just-built scilab-cli-bin over the .sci sources, so it
    # depends on the executable already existing (the bootstrap loop).
    run_step "[3/4] macros (.sci -> .bin)…" \
        cmake --build "$build" --target macros -j"$JOBS"

    # Help content (DocBook XML -> JavaHelp jars in modules/helptools/target/). Also
    # circular — it drives the built scilab-adv-cli headless via xmltojar — and the
    # slowest step. ScilabHelp.cmake supplies the JDK-25 -Djdk.xml.*Limit=0 lifts the
    # DocBook pipeline needs; without them the parse dies on jdk.xml.totalEntitySizeLimit.
    if [ "$SKIP_DOC" -eq 1 ]; then
        echo "[4/4] doc — SKIPPED (--skip-doc)"
    else
        run_step "[4/4] doc (help browser content, all locales)…" \
            cmake --build "$build" --target doc
    fi

    echo "  $build_type total: $(fmt_dur $((SECONDS - t0)))"
}

export JAVA_HOME="$JDK"          # the Maven reactor + the JNI probes read it
START=$SECONDS
for t in "${BUILD_TYPES[@]}"; do
    build_one "$t"
done

# What the tree is LEFT holding is the last type built (see --both above).
FINAL_TYPE="${BUILD_TYPES[${#BUILD_TYPES[@]}-1]}"

echo
if [ "${#BUILD_TYPES[@]}" -gt 1 ]; then
    echo "Build complete — ${BUILD_TYPES[*]} — in $(fmt_dur $((SECONDS - START)))."
    echo "  Trees:     build-cmake-debug/ and build-cmake/ both kept, so either can be"
    echo "             rebuilt incrementally."
    echo "  In place:  modules/*/.libs/ holds the $FINAL_TYPE build (the last one built)."
else
    echo "Build complete ($FINAL_TYPE, from $(build_dir_for "$FINAL_TYPE")/) in $(fmt_dur $((SECONDS - START)))."
fi
echo "  Terminal:  JAVA_HOME=$JDK ./bin/scilab     # run the GUI"
if [ "$FINAL_TYPE" = debug ]; then
    echo "  Package:   ./package-macos.sh --allow-debug   # refuses a debug payload otherwise"
    echo
    echo "  NOTE: modules/*/.libs/ now holds DEBUG binaries (-O0 -g3). Re-run"
    echo "        ./build-macos.sh   to put the release build back."
else
    echo "  Package:   ./package-macos.sh              # build the /Applications app"
fi
if [ "$SKIP_DOC" -eq 1 ]; then
    echo
    echo "  ⚠  --skip-doc: the help jars in modules/*/target/ are from an EARLIER build."
    echo "     The help browser will show stale content. Re-run without --skip-doc"
    echo "     before packaging anything you intend to ship."
fi
