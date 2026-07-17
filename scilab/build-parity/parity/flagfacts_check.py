"""Assert the semantic compiler-flag facts of a CMake module's compile lines.
Closes the hybrid blind spot: the tree-wide flag manifest reads config.status
(autotools), so it cannot see a CMake module's own flags; this can."""
import json, os, sys
from parity.fingerprint import parse_flag_facts

# Default expectation: every compiled TU -- C, C++, and Fortran alike -- is
# O2 + fwrapv + min_macos 11.0. Named + module-level (not buried in __main__) so
# it is importable and testable: the CLI gate and Tasks 5-9 share ONE source of
# truth for which suffixes get checked + what is expected of them.
_BASE = {"opt": "O2", "wrapv": True, "min_macos": "11.0"}
# Every compiled-source suffix that actually appears in the Scilab tree (census
# 2026-07-16: .c 1818, .cpp 1435, .cxx 4, .cc 3, .f 848, .F 3, .f90 59). ALL of
# these are MUTUALLY unreachable via endswith (it is exact + case-sensitive:
# "x.cpp".endswith(".c"), "x.cc".endswith(".c"), "x.f90".endswith(".f"),
# "x.F".endswith(".f") are ALL False), so each must be listed explicitly or its
# TUs' flags go unchecked -- a guard that does not guard. The parametrized test
# test_each_required_suffix_is_guarded proves every entry here is live; the CLI
# additionally FAILS on any entry whose suffix is absent here (unchecked_suffixes).
DEFAULT_EXPECTED_BY_SUFFIX = {suffix: _BASE for suffix in
                              (".c", ".cpp", ".cxx", ".cc", ".f", ".F", ".f90")}

# Per-FILE overrides of the per-suffix default, keyed by BASENAME (the compile-DB
# path is absolute, and the rule is file-identity, not location). An override is
# MERGED onto the suffix expectation (see check_flag_facts) so ONLY the named
# facts are relaxed -- every other fact stays guarded, and every other file of
# that suffix stays held to the default.
#
# colnew.f: compiled -O0 on macOS by an autotools per-file rule
# (modules/differential_equations/Makefile.am, `if IS_MACOSX` "enforce -O0 for
# some files") -- upstream a45812e728f (2019, "macOS: fix some crash while using
# gfortran from homebrew") works around a gfortran -O2 miscompile of the bvode
# collocation solver on Apple silicon. The CMake port reproduces the -O0
# faithfully (dylib parity is green; the baseline object's DWARF producer reads
# `-g1 -O2 -O0 -fwrapv`), so the check must EXPECT opt=O0 for this ONE file
# instead of false-flagging it. Only `opt` is overridden: the workaround does
# NOT drop -fwrapv or the -mmacosx-version-min stamp (both present on the
# baseline -O0 line), so wrapv=True + min_macos=11.0 remain enforced here -- and
# a future colnew.f compiled at -O2 (silently reverting the workaround) still
# FAILS, now naming opt=O2 (want O0). colnew.f is the sole file of this name in
# the tree (verified), so the basename key is unambiguous.
#
# The Stage-1f fold-in modules carry FIVE more files of exactly this class --
# each forced -O0 by a LIVE `if IS_MACOSX` per-file rule in its Makefile.am
# (rule target == the real subdir-objects .lo path; baseline DWARF producers
# all read `-g1 -O2 -O0 -fwrapv`, verified 2026-07-17):
#   sszer.f                     modules/cacsd ("enforce -O0 for some files")
#   dtensbs.f                   modules/elementary_functions ("macOS crash")
#   blkfct.f symfct.f ordmmd.f  modules/sparse ("macOS crash")
# (elementary_functions' libdummy -O0 rules for hqror2/comqr3/pade/unsfdcopy/
# icopy are NOT here on purpose: those handwritten rules target root-level
# prefixed .lo names that subdir-objects never requests -- DEAD; the baseline
# compiled all five at plain -O2, so the default expectation already matches.)
# Every basename is unique tree-wide (verified), so the keys are unambiguous.
FILE_EXPECTED_OVERRIDES = {basename: {"opt": "O0"} for basename in
                           ("colnew.f", "sszer.f", "dtensbs.f",
                            "blkfct.f", "symfct.f", "ordmmd.f")}

# Per-DIRECTORY overrides for the automake `_la_CFLAGS`-REPLACES-AM_CFLAGS
# footgun (distinct from the per-file -O0 class above -- this is a per-target
# flag mistake, not a per-file workaround, so it is keyed by the C source tree
# it applies to). A few Stage-1f fold-in core modules set `libsci<m>_la_CFLAGS`
# to a bare -I list; automake's per-target _CFLAGS REPLACES $(AM_CFLAGS) =
# $(SCI_CFLAGS) WHOLESALE, so those modules' C TUs compiled with NONE of the
# SCI_CFLAGS codegen flags -- no -O2 (so -O0), no -fwrapv, no -g, no
# -mmacosx-version-min (host-default min-version). VERIFIED 2026-07-17 on the
# rebuilt baseline objects (DW_AT_APPLE_optimized=0 / no DWARF; LC_BUILD_VERSION
# minos 26.0 vs 11.0 for a sibling SCI_CFLAGS TU):
#   /modules/parameters/         -- 1 C TU (parameters.c)
#   /modules/windows_tools/      -- 1 C TU (nowindows_tools.c)
#   /modules/string/src/c/       -- 25 C TUs; string sets _CFLAGS but NOT
#                                   _CXXFLAGS, so ONLY its C tree is footgunned
#                                   (its C++ gateways keep SCI_CXXFLAGS = O2 and
#                                   stay on the default expectation). Scoped to
#                                   src/c/ so those C++ TUs are NOT relaxed.
# The CMake port REPRODUCES this (1f-a invariant: same app, not a better one)
# via scilab_object_module(... C_FLAGS_OVERRIDE -std=gnu23); the fold-in
# aggregate parity (Task 4) is codegen-BLIND (symbol/dep/rpath), so THIS gate is
# the only thing standing between "reproduced -O0" and "silently shipped -O2",
# and it must EXPECT the real shape: opt=O0 + wrapv=False. min_macos is NOT
# overridden -- CMAKE_OSX_DEPLOYMENT_TARGET stamps -mmacosx-version-min=11.0 on
# these TUs (the baseline's host-default 26.0 was a non-portable artifact of the
# dropped flag, and the folded object's min-version is set at aggregate link
# time anyway), so 11.0 stays guarded. C-only (`.c`): a future C++/Fortran TU
# added under these dirs must NOT inherit the C footgun. The deliberate O2 fix
# (restore SCI_CFLAGS in the Makefile.am + re-baseline) is a separate step.
DIR_EXPECTED_OVERRIDES = (
    ("/modules/parameters/",    {"opt": "O0", "wrapv": False}),
    ("/modules/windows_tools/", {"opt": "O0", "wrapv": False}),
    ("/modules/string/src/c/",  {"opt": "O0", "wrapv": False}),
)

def _override_for(path):
    """The expected-fact override for one compile-DB file, or None.

    Per-FILE basename override (the -O0 workaround class) wins; else, for C
    sources only, the first matching per-DIRECTORY override (the _CFLAGS
    footgun). C-only because the footgun is a C-compile mistake -- C++/Fortran
    TUs under the same dir keep the default."""
    override = FILE_EXPECTED_OVERRIDES.get(os.path.basename(path))
    if override is None and path.endswith(".c"):
        for substr, facts in DIR_EXPECTED_OVERRIDES:
            if substr in path:
                return facts
    return override

def check_flag_facts(compile_commands_path, expected_by_suffix):
    with open(compile_commands_path) as f:
        entries = json.load(f)
    mismatches = []
    for e in entries:
        cmd = e.get("command") or " ".join(e.get("arguments", []))
        override = _override_for(e["file"])
        for suffix, expected in expected_by_suffix.items():
            if not e["file"].endswith(suffix):
                continue
            # Override wins over the per-suffix default, MERGED (not replaced):
            # {**default, **override} keeps the non-overridden facts under guard
            # while relaxing only the named ones. A fresh dict every time --
            # never mutate the shared per-suffix dict.
            if override:
                expected = {**expected, **override}
            facts = parse_flag_facts(cmd)
            for k, want in expected.items():
                if facts.get(k) != want:
                    mismatches.append(f"{e['file']}: flag fact {k}={facts.get(k)!r} (want {want!r})")
    return mismatches

def unchecked_suffixes(compile_commands_path, expected_by_suffix):
    """Compile-DB entries whose file matches NO suffix in the map.

    compile_commands.json holds exactly one entry per COMPILED translation unit,
    so an entry matching no suffix is a compiled source going unchecked -- a
    silent coverage gap. Returns (file, ext) pairs for the CLI to report and fail
    on. Mirrors check_flag_facts' endswith predicate exactly, so "covered here"
    means "actually examined there".
    """
    with open(compile_commands_path) as f:
        entries = json.load(f)
    out = []
    for e in entries:
        if not any(e["file"].endswith(s) for s in expected_by_suffix):
            out.append((e["file"], os.path.splitext(e["file"])[1] or "(none)"))
    return out

if __name__ == "__main__":
    path = sys.argv[1]
    unchecked = unchecked_suffixes(path, DEFAULT_EXPECTED_BY_SUFFIX)
    mismatches = check_flag_facts(path, DEFAULT_EXPECTED_BY_SUFFIX)
    for f, ext in unchecked:
        print(f"unchecked compiled suffix {ext!r} in {f} -- add it to DEFAULT_EXPECTED_BY_SUFFIX")
    for m in mismatches:
        print(m)
    sys.exit(1 if (unchecked or mismatches) else 0)
