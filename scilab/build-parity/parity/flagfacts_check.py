"""Assert the semantic compiler-flag facts of a CMake module's compile lines
against facts DERIVED from the autotools generated Makefiles and frozen into
the parity baseline (RC-b; parity.capture.capture_tu_flag_facts). Closes the
hybrid blind spot: the tree-wide flag manifest reads config.status (autotools),
so it cannot see a CMake module's own flags; this can -- and unlike the
hand-written expectation tables this replaces, what it checks against is
DERIVED ground truth, not merely what someone remembered to record."""
import json, os, sys
from parity.fingerprint import parse_flag_facts
from parity.makeflags import LANG_BY_SUFFIX

# ---------------------------------------------------------------------------
# Historical knowledge behind the derived facts. The hand-written
# FILE_EXPECTED_OVERRIDES / DIR_EXPECTED_OVERRIDES tables that used to record
# this are gone -- capture_tu_flag_facts (parity.capture) now derives their
# CONTENT straight from the generated Makefiles -- but the reviewed WHY behind
# those facts is not rederivable from the numbers alone, so it stays here.
#
# Per-FILE -O0 workaround (opt only; wrapv/min_macos stay at the tree default):
# colnew.f is compiled -O0 on macOS by an autotools per-file rule
# (modules/differential_equations/Makefile.am, `if IS_MACOSX` "enforce -O0 for
# some files") -- upstream a45812e728f (2019, "macOS: fix some crash while
# using gfortran from homebrew") works around a gfortran -O2 miscompile of the
# bvode collocation solver on Apple silicon. The Stage-1f fold-in modules carry
# FIVE more files of exactly this class, each forced -O0 by its own LIVE
# `if IS_MACOSX` per-file rule (baseline DWARF producers all read
# `-g1 -O2 -O0 -fwrapv`, verified 2026-07-17):
#   sszer.f                     modules/cacsd ("enforce -O0 for some files")
#   dtensbs.f                   modules/elementary_functions ("macOS crash")
#   blkfct.f symfct.f ordmmd.f  modules/sparse ("macOS crash")
# (elementary_functions' libdummy -O0 rules for hqror2/comqr3/pade/unsfdcopy/
# icopy are a DEAD root-level rule that subdir-objects never requests -- the
# baseline compiled all five at plain -O2, so capture_tu_flag_facts's
# object-referenced live-rule filter correctly excludes them and no override
# is recorded.)
#
# Per-DIRECTORY footgun (opt=O0 + wrapv=False; automake `_la_CFLAGS`-REPLACES-
# `AM_CFLAGS`): a few Stage-1f fold-in core modules set `libsci<m>_la_CFLAGS`
# to a bare -I list; automake's per-target _CFLAGS REPLACES $(AM_CFLAGS) =
# $(SCI_CFLAGS) WHOLESALE, so those modules' C TUs compiled with NONE of the
# SCI_CFLAGS codegen flags -- no -O2 (so -O0), no -fwrapv, no -g, no
# -mmacosx-version-min (host-default min-version instead). VERIFIED 2026-07-17
# on the rebuilt baseline objects (DW_AT_APPLE_optimized=0 / no DWARF;
# LC_BUILD_VERSION minos 26.0 vs 11.0 for a sibling SCI_CFLAGS TU):
#   modules/parameters/     -- 1 C TU (parameters.c)
#   modules/windows_tools/  -- 1 C TU (nowindows_tools.c)
#   modules/string/src/c/   -- 25 C TUs; string sets _CFLAGS but NOT
#                              _CXXFLAGS, so ONLY its C tree is footgunned
#                              (its C++ gateways keep SCI_CXXFLAGS = O2 and
#                              the tree default).
# The CMake port REPRODUCES this shape on purpose (1f-a invariant: same app,
# not a better one) via scilab_object_module(... C_FLAGS_OVERRIDE -std=gnu23);
# the fold-in aggregate parity check (Task 4) is codegen-BLIND (symbol/dep/
# rpath), so THIS gate is the only thing standing between "reproduced -O0" and
# "silently shipped -O2". The deliberate O2 fix (restore SCI_CFLAGS in the
# Makefile.am + re-baseline) is a separate step, not done here.
# ---------------------------------------------------------------------------

# Facts DERIVED from autotools (RC-b). min_macos is deliberately NOT among them:
# a footgunned TU's autotools recipe drops -mmacosx-version-min entirely (deriving
# min_macos=None), while CMAKE_OSX_DEPLOYMENT_TARGET stamps 11.0 on every CMake TU.
# That difference was reviewed and ACCEPTED before RC-b -- the baseline's host-default
# stamp on those objects was a non-portable artifact of the dropped flag, and a folded
# object's min-version is set at aggregate link time anyway. So min_macos is asserted
# as a CMake-side INVARIANT rather than derived, and stays guarded everywhere.
DERIVED_KEYS = ("opt", "wrapv", "ndebug", "std", "openmp")
INVARIANT = {"min_macos": "11.0"}

def expected_for(rel_path, suffix_lang, derived):
    """The expected facts for one TU: its derived override if it has one, else the
    derived tree-wide default for its language, plus the CMake-side invariant."""
    if rel_path in derived["overrides"]:
        facts = derived["overrides"][rel_path]
    else:
        facts = derived["defaults"].get(suffix_lang)
    if facts is None:
        return None
    return {**{k: facts[k] for k in DERIVED_KEYS if k in facts}, **INVARIANT}

def check_flag_facts(compile_commands_path, derived, source_root):
    """Compare each compile-DB entry's ACTUAL flag facts (parse_flag_facts on its
    compile command) against its EXPECTED facts (expected_for) and report every
    mismatch as a string.

    `derived` is the tu_flag_facts section of the parity baseline --
    {"defaults": {lang: facts}, "overrides": {relpath: facts}} -- exactly as
    capture_tu_flag_facts produces it. `source_root` is what compile_commands.json's
    absolute `file` paths are made relative to before the override lookup
    (capture_tu_flag_facts keys overrides by path relative to the autotools source
    root, e.g. "modules/string/src/c/foo.c").

    A TU whose suffix maps to no language, or whose language has neither a
    derived override nor a tree-wide default, yields no expectation and is
    silently skipped here -- unchecked_suffixes reports those separately (the
    CLI fails the run on them too, so nothing goes unchecked without the run
    failing loudly).
    """
    with open(compile_commands_path) as f:
        entries = json.load(f)
    mismatches = []
    for e in entries:
        rel_path = os.path.relpath(e["file"], source_root)
        lang = LANG_BY_SUFFIX.get(os.path.splitext(e["file"])[1][1:])
        expected = expected_for(rel_path, lang, derived) if lang else None
        if expected is None:
            continue
        cmd = e.get("command") or " ".join(e.get("arguments", []))
        facts = parse_flag_facts(cmd)
        for k, want in expected.items():
            if facts.get(k) != want:
                mismatches.append(f"{e['file']}: flag fact {k}={facts.get(k)!r} (want {want!r})")
    return mismatches

def unchecked_suffixes(compile_commands_path, derived, source_root):
    """Compile-DB entries that check_flag_facts would silently skip: an unknown
    suffix (absent from LANG_BY_SUFFIX), or a known language with no derived
    expectation at all (no override for that TU AND no tree-wide default for its
    language). compile_commands.json holds exactly one entry per COMPILED
    translation unit, so either case is a compiled source going unchecked -- a
    silent coverage gap. Returns (file, ext) pairs for the CLI to report and fail
    on. Mirrors check_flag_facts' predicate exactly, so "covered here" means
    "actually examined there".
    """
    with open(compile_commands_path) as f:
        entries = json.load(f)
    out = []
    for e in entries:
        rel_path = os.path.relpath(e["file"], source_root)
        ext = os.path.splitext(e["file"])[1]
        lang = LANG_BY_SUFFIX.get(ext[1:])
        if lang is None or expected_for(rel_path, lang, derived) is None:
            out.append((e["file"], ext or "(none)"))
    return out

# KNOWN STATE (RC-b Task 3 acceptance run, 2026-07-18): this gate currently
# reports rc=1 against the real tree -- 50 divergent files in two classes, both
# REPRODUCE-not-improve bugs (CMake not yet matching autotools) and both Task
# 4's to close, not Task 3's (Task 3's job was making this gate DERIVE its
# expectations and correctly FAIL, which it does):
#   - 3 files (history_browser/sci_gateway/c/sci_browsehistory.c,
#     preferences/src/c/getScilabPreference.c,
#     types/src/jni/getScilabVariable_wrap.c): opt/wrapv/ndebug mismatches --
#     the _CFLAGS-replaces-AM_CFLAGS footgun documented above, newly found in
#     these three modules (parameters/windows_tools/string already reproduce
#     it correctly).
#   - 47 files (42 in modules/differential_equations, 5 in modules/scicos --
#     the latter doubled across the scicos/scicos-cli targets, so 10 mismatch
#     lines): openmp-only mismatches, a bidirectional CMake OpenMP-linking-
#     scope bug in cmake/ScilabModule.cmake's FIND_PACKAGES OpenMP handling
#     (differential_equations under-applies -fopenmp: its ALGO_SOURCES compile
#     into an OBJECT library never linked to OpenMP, and its C++ gateways only
#     ever get OpenMP::OpenMP_C, never OpenMP::OpenMP_CXX; scicos/scicos-cli
#     over-apply it: their C gateway sources inherit -fopenmp from the whole
#     target being linked to OpenMP::OpenMP_C even though autotools never puts
#     -fopenmp on scicos's own compile lines). Verified codegen-neutral: of
#     these 47, only patched_sundials' nvector_openmp.c carries an actual
#     #pragma omp in the whole differential_equations/scicos family, and CMake
#     already flags that one correctly -- structurally invisible to every
#     prior gate because none of them ever asserted `openmp` at all.
# tests/test_flagfacts_check.py::test_real_tree_divergence_is_exactly_the_known_
# tracked_set freezes this exact 50-file set: it fails (a WELCOME failure) the
# moment Task 4 shrinks it, and fails (investigate first, don't just edit the
# set) if the shape changes any other way. A reader hitting rc=1 here should
# check that test before assuming something new broke.

if __name__ == "__main__":
    cc_path, baseline_path, source_root = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(baseline_path) as bf:
        derived = json.load(bf)["tu_flag_facts"]
    unchecked = unchecked_suffixes(cc_path, derived, source_root)
    mismatches = check_flag_facts(cc_path, derived, source_root)
    for file_, ext in unchecked:
        print(f"unchecked compiled suffix {ext!r} in {file_} -- add it to LANG_BY_SUFFIX or the derived facts")
    for m in mismatches:
        print(m)
    sys.exit(1 if (unchecked or mismatches) else 0)
