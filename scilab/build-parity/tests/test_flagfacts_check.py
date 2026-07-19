import json, os, subprocess, sys
import pytest
from parity.flagfacts_check import check_flag_facts, unchecked_suffixes, expected_for, DERIVED_KEYS, INVARIANT

# build-parity root (parent of tests/): the CWD the CLI is run from so that
# `python -m parity.flagfacts_check` can import the parity package, and the
# directory the real baseline-autotools.json lives in.
BUILD_PARITY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REAL_BASELINE = os.path.join(BUILD_PARITY, "baseline-autotools.json")

def _cc(tmp_path, entries):
    p = tmp_path / "compile_commands.json"; p.write_text(json.dumps(entries)); return str(p)

def _derived(defaults=None, overrides=None):
    """A minimal tu_flag_facts-shaped {"defaults", "overrides"} dict -- the same
    shape capture_tu_flag_facts produces -- for unit-level check_flag_facts/
    expected_for tests that must not depend on the real (large, slowly-changing)
    armed baseline."""
    return {"defaults": defaults or {}, "overrides": overrides or {}}

def _baseline_file(tmp_path, derived, name="baseline.json"):
    p = tmp_path / name
    p.write_text(json.dumps({"tu_flag_facts": derived}))
    return str(p)

def _run_cli(cc_path, baseline_path, source_root):
    """Invoke the real CLI the way Tasks 5-9 + CI do; return the CompletedProcess."""
    return subprocess.run([sys.executable, "-m", "parity.flagfacts_check",
                           cc_path, baseline_path, source_root],
                          cwd=BUILD_PARITY, capture_output=True, text=True)

def _real_derived():
    return json.load(open(REAL_BASELINE))["tu_flag_facts"]

_CXX_DEFAULT = {"opt": "O2", "wrapv": True, "min_macos": "11.0",
               "ndebug": True, "std": "c++17", "openmp": False}
_C_DEFAULT = {"opt": "O2", "wrapv": True, "min_macos": "11.0",
             "ndebug": True, "std": "gnu23", "openmp": False}

# --- basic pass/fail, against a synthetic derived tree (unit-level, no CLI) --

def test_pass_when_all_facts_match(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])
    derived = _derived(defaults={"cxx": _CXX_DEFAULT})
    assert check_flag_facts(cc, derived, "/x") == []

def test_fail_names_the_regressed_fact(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O0 -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])  # O0 + no fwrapv
    derived = _derived(defaults={"cxx": _CXX_DEFAULT})
    out = check_flag_facts(cc, derived, "/x")
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out)

def test_min_macos_fact_is_guarded_even_though_it_is_not_derived(tmp_path):
    # min_macos comes from INVARIANT, not from "defaults"/"overrides" -- but it
    # must still be enforced: a TU missing -mmacosx-version-min entirely fails.
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -DNDEBUG -c foo.cpp"}])  # no -mmacosx-version-min
    derived = _derived(defaults={"cxx": _CXX_DEFAULT})
    out = check_flag_facts(cc, derived, "/x")
    assert any("min_macos" in m for m in out)

# --- expected_for: the override/default/invariant merge contract -----------

def test_expected_for_prefers_override_over_default():
    derived = _derived(defaults={"c": _C_DEFAULT},
                       overrides={"modules/m/drop.c": {**_C_DEFAULT, "opt": "O0", "wrapv": False}})
    exp = expected_for("modules/m/drop.c", "c", derived)
    assert exp["opt"] == "O0" and exp["wrapv"] is False

def test_expected_for_falls_back_to_the_language_default():
    derived = _derived(defaults={"c": _C_DEFAULT}, overrides={})
    exp = expected_for("modules/m/plain.c", "c", derived)
    assert exp["opt"] == "O2" and exp["wrapv"] is True

def test_expected_for_returns_none_with_no_default_and_no_override():
    derived = _derived(defaults={"c": _C_DEFAULT}, overrides={})
    assert expected_for("modules/m/plain.cxx", "cxx", derived) is None  # no "cxx" default here

def test_expected_for_min_macos_is_always_the_invariant_not_the_derived_fact():
    # The design's central point: a footgunned TU's autotools recipe DROPS
    # -mmacosx-version-min (derived min_macos=None), but CMAKE_OSX_DEPLOYMENT_
    # TARGET stamps 11.0 on every CMake TU regardless -- so expected_for must
    # report 11.0 here, NOT the derived None, for every TU (default or override).
    derived = _derived(defaults={"c": {**_C_DEFAULT, "min_macos": None}},
                       overrides={"modules/m/foot.c": {**_C_DEFAULT, "min_macos": None,
                                                       "opt": "O0", "wrapv": False}})
    assert expected_for("modules/m/plain.c", "c", derived)["min_macos"] == "11.0"
    assert expected_for("modules/m/foot.c", "c", derived)["min_macos"] == "11.0"

def test_derived_keys_excludes_min_macos():
    # Composition lock: min_macos must never be sourced from "defaults"/
    # "overrides" -- INVARIANT is its only source (see the module comment).
    assert "min_macos" not in DERIVED_KEYS
    assert INVARIANT == {"min_macos": "11.0"}

def test_derived_keys_exact_membership():
    # Positive lock, complementing the negative assertion above: pins the FULL
    # membership, not just the min_macos exclusion. This is not tuple-shaped
    # bookkeeping -- "openmp" is the key whose ABSENCE reinstates a real
    # functional bug. Narrowing DERIVED_KEYS to drop "openmp" makes expected_for
    # stop asking for it at all (it builds the expectation dict by iterating
    # DERIVED_KEYS: `{k: facts[k] for k in DERIVED_KEYS if k in facts}`), so
    # check_flag_facts silently stops comparing openmp on every TU -- reopening
    # exactly the differential_equations/scicos serial-vs-parallel `#ifdef
    # _OPENMP` divergence that was this stage's headline finding (RC-b Task 3/4:
    # 47 files, closed by fixing the CMake OpenMP link scope). That regression
    # would then reproduce with the whole suite green and the gate at rc=0 --
    # the exact silent-pass this test exists to prevent.
    assert DERIVED_KEYS == ("opt", "wrapv", "ndebug", "std", "openmp")

# --- unchecked_suffixes: the coverage-gap contract --------------------------

def test_unchecked_suffixes_flags_an_unknown_extension(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.rs", "directory": "/x", "command": "rustc -c foo.rs"}])
    derived = _derived(defaults={"c": _C_DEFAULT})
    out = unchecked_suffixes(cc, derived, "/x")
    assert out == [("/x/foo.rs", ".rs")]

def test_unchecked_suffixes_flags_a_known_language_with_no_expectation(tmp_path):
    # .cpp maps to a real language (cxx) via LANG_BY_SUFFIX, but if the derived
    # tree carries no "cxx" default and this TU has no override, there is still
    # no expectation to check it against -- unchecked, not silently skipped.
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x", "command": "g++ -c foo.cpp"}])
    derived = _derived(defaults={"c": _C_DEFAULT})   # no "cxx" entry
    out = unchecked_suffixes(cc, derived, "/x")
    assert out == [("/x/foo.cpp", ".cpp")]

def test_unchecked_suffixes_is_empty_when_covered(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.c", "directory": "/x", "command": "cc -c foo.c"}])
    derived = _derived(defaults={"c": _C_DEFAULT})
    assert unchecked_suffixes(cc, derived, "/x") == []

# --- every LANG_BY_SUFFIX suffix is actually reached END TO END ------------
# Replaces the old parametrized test_each_required_suffix_is_guarded (7 cases
# over DEFAULT_EXPECTED_BY_SUFFIX, ".c"/".cpp"/".cxx"/".cc"/".f"/".F"/".f90"):
# that hand-written map is gone, and the suffix-to-language mapping now lives
# in, and is tested by, makeflags.py's LANG_BY_SUFFIX (test_makeflags.py) --
# re-locking ITS composition here would duplicate that file's job. What THIS
# file still owns, and must still prove, is that check_flag_facts -- walking
# the REAL derived baseline, not a synthetic one -- actually reaches a live
# "defaults" entry and catches a bad TU for every one of these 7 suffixes.
# That is end-to-end plumbing this file is responsible for, distinct from
# "does the mapping exist" (makeflags' job).

REQUIRED_SUFFIXES = (".c", ".cpp", ".cxx", ".cc", ".f", ".F", ".f90")

@pytest.mark.parametrize("suffix", REQUIRED_SUFFIXES)
def test_each_known_suffix_is_guarded_end_to_end_by_the_real_baseline(tmp_path, suffix):
    derived = _real_derived()
    # A path guaranteed absent from "overrides" (not under modules/ at all), so
    # this exercises the per-LANGUAGE DEFAULT path specifically.
    cc = _cc(tmp_path, [{"file": f"/x/never_overridden{suffix}", "directory": "/x",
        "command": f"cc -mmacosx-version-min=11.0 -c never_overridden{suffix}"}])  # no -O2, no -fwrapv
    out = check_flag_facts(cc, derived, "/x")
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out), \
        f"suffix {suffix!r} is not actually guarded end-to-end by the real derived baseline"

# --- the CLI contract (rc=0 pass / rc=1 mismatch / rc!=0 unchecked) ---------

def test_cli_exits_0_on_clean(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])
    baseline = _baseline_file(tmp_path, _derived(defaults={"cxx": _CXX_DEFAULT}))
    r = _run_cli(cc, baseline, "/x")
    assert r.returncode == 0, r.stdout + r.stderr

def test_cli_exits_1_on_mismatch(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -O0 -mmacosx-version-min=11.0 -c foo.cpp"}])  # O0 + no fwrapv
    baseline = _baseline_file(tmp_path, _derived(defaults={"cxx": _CXX_DEFAULT}))
    r = _run_cli(cc, baseline, "/x")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "opt" in r.stdout and "wrapv" in r.stdout

def test_cli_fails_on_unknown_suffix(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.rs", "directory": "/x", "command": "rustc -c foo.rs"}])
    baseline = _baseline_file(tmp_path, _derived(defaults={"cxx": _CXX_DEFAULT}))
    r = _run_cli(cc, baseline, "/x")
    assert r.returncode != 0, r.stdout + r.stderr
    assert "unchecked compiled suffix" in r.stdout and ".rs" in r.stdout

# --- I4/I5 equivalent: the real per-file/per-directory footgun overrides,
# exercised END TO END through check_flag_facts (not just expected_for), with
# realistic synthetic compile commands -- same rigor as the retired
# test_footgun_*/test_colnew_* tests, re-sourced from the derived baseline
# instead of the hand-written tables that used to carry this. Every fact
# (opt/wrapv/ndebug/std/openmp/min_macos) is checked now, not just
# opt/wrapv/min_macos, so these are a strict superset of the old assertions.

def test_derived_overrides_cover_the_known_footgun_dirs():
    derived = _real_derived()
    # NOTE: the brief's Step 4 snippet spells the windows_tools probe as
    # "modules/windows_tools/src/nowindows_tools.c". The real generated Makefile
    # (and capture_tu_flag_facts's derivation from it) keys this TU one directory
    # deeper -- "modules/windows_tools/src/nowindows_tools/nowindows_tools.c",
    # verified against both modules/windows_tools/Makefile and the file on disk.
    # The brief's literal path is absent from "overrides", which would silently
    # fall through to the C default (O2/True) and fail this assertion -- using
    # the verified real path is what actually locks the footgun behavior.
    for probe in ("modules/parameters/src/c/parameters.c",
                  "modules/windows_tools/src/nowindows_tools/nowindows_tools.c"):
        exp = expected_for(probe, "c", derived)
        assert exp is not None, probe
        assert exp["opt"] == "O0" and exp["wrapv"] is False, probe

def test_a_tu_with_no_override_gets_the_derived_default():
    derived = _real_derived()
    exp = expected_for("modules/core/src/c/nowhere.c", "c", derived)
    assert exp["opt"] == "O2" and exp["wrapv"] is True and exp["min_macos"] == "11.0"

def test_footgun_c_shape_passes_end_to_end(tmp_path):
    # The real footgun shape (per-target _CFLAGS replaced AM_CFLAGS wholesale --
    # only $(CC)'s own -std=gnu23 survives): no -O2, no -fwrapv, no -DNDEBUG.
    # Covers all three DIR_EXPECTED_OVERRIDES-era directories in one test.
    derived = _real_derived()
    for path in ("/m/modules/parameters/src/c/parameters.c",
                 "/m/modules/windows_tools/src/nowindows_tools/nowindows_tools.c",
                 "/m/modules/string/src/c/StringConvert.c"):
        cc = _cc(tmp_path, [{"file": path, "directory": "/m",
            "command": f"gcc -std=gnu23 -arch arm64 -mmacosx-version-min=11.0 "
            f"-DHAVE_CONFIG_H -c {os.path.basename(path)}"}])
        assert check_flag_facts(cc, derived, "/m") == [], path

def test_footgun_c_reverting_to_o2_still_fails(tmp_path):
    # If parameters.c is ever silently un-footgunned (SCI_CFLAGS restored), the
    # derived override must still catch it -- opt/wrapv/ndebug all regress together.
    derived = _real_derived()
    cc = _cc(tmp_path, [{"file": "/m/modules/parameters/src/c/parameters.c", "directory": "/m",
        "command": "gcc -std=gnu23 -arch arm64 -DNDEBUG -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c parameters.c"}])
    out = check_flag_facts(cc, derived, "/m")
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out) \
        and any("ndebug" in m for m in out), out

def test_footgun_dir_override_is_c_only_end_to_end(tmp_path):
    # string's C++ gateways keep SCI_CXXFLAGS (O2): a clean O2 .cpp there passes
    # on the DEFAULT (not a relaxed override), and a bad O0 .cpp there still
    # fails -- proving the C-only footgun does not leak into the C++ tree.
    derived = _real_derived()
    ok = _cc(tmp_path, [{"file": "/m/modules/string/sci_gateway/cpp/sci_strindex.cpp",
        "directory": "/m", "command": "g++ -std=c++17 -DNDEBUG -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c sci_strindex.cpp"}])
    assert check_flag_facts(ok, derived, "/m") == []
    bad = _cc(tmp_path, [{"file": "/m/modules/string/sci_gateway/cpp/sci_strindex.cpp",
        "directory": "/m", "command": "g++ -std=c++17 -DNDEBUG -O0 "
        "-mmacosx-version-min=11.0 -c sci_strindex.cpp"}])
    out = check_flag_facts(bad, derived, "/m")
    assert any("opt" in m for m in out), out

def test_colnew_o0_passes_end_to_end(tmp_path):
    # The real baseline shape: `-O2 -O0` (last-wins -> O0) + fwrapv + NDEBUG.
    derived = _real_derived()
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O2 -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    assert check_flag_facts(cc, derived, "/m") == []

def test_colnew_o2_regression_fails_naming_only_opt(tmp_path):
    # The guard that matters: if colnew.f is ever compiled plain -O2 (silently
    # reverting the gfortran miscompile workaround), the override's opt=O0
    # expectation catches it -- and ONLY opt, since wrapv/ndebug/min_macos are
    # otherwise correct here (proves the override merges, not blanket-exempts).
    derived = _real_derived()
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    out = check_flag_facts(cc, derived, "/m")
    assert len(out) == 1 and "opt" in out[0], out

def test_colnew_missing_fwrapv_still_fails(tmp_path):
    derived = _real_derived()
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O2 -O0 "
        "-mmacosx-version-min=11.0 -c colnew.f"}])  # no -fwrapv
    out = check_flag_facts(cc, derived, "/m")
    assert any("wrapv" in m for m in out) and not any("opt" in m for m in out), out

def test_other_fortran_file_is_not_exempted_by_colnews_override(tmp_path):
    # A DIFFERENT .f (lsoda.f) at -O0 must still fail on opt -- colnew.f's
    # override is scoped to colnew.f, it does not soften the default for the
    # other ~847 Fortran TUs in the tree.
    derived = _real_derived()
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/lsoda.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c lsoda.f"}])
    out = check_flag_facts(cc, derived, "/m")
    assert any("opt" in m for m in out), out

def test_a_second_stage1f_file_override_is_present_and_scoped_to_opt():
    # Spot-check a SECOND file from the Stage-1f "five more files of exactly
    # this class" (see the module comment): confirms the -O0-workaround family
    # is covered beyond just colnew.f, without re-asserting all 211 derived
    # entries by name -- doing that would recreate the hand-maintained-table
    # pattern this task retires.
    derived = _real_derived()
    exp = expected_for("modules/cacsd/src/fortran/sszer.f", "f", derived)
    assert exp["opt"] == "O0" and exp["wrapv"] is True

def test_string_src_c_footgun_is_scoped_to_c_not_cxx():
    # string sets _CFLAGS but not _CXXFLAGS: its C tree is footgunned, its C++
    # gateways are not -- so a "cxx"-language probe at the same relpath must NOT
    # pick up the C override (expected_for is keyed by language too).
    derived = _real_derived()
    c_exp = expected_for("modules/string/src/c/StringConvert.c", "c", derived)
    assert c_exp["opt"] == "O0" and c_exp["wrapv"] is False
    # The real C++ gateways live at a different relpath entirely (sci_gateway/
    # cpp/), absent from "overrides", so they fall through to the cxx default.
    gw_exp = expected_for("modules/string/sci_gateway/cpp/sci_strindex.cpp", "cxx", derived)
    assert gw_exp["opt"] == "O2" and gw_exp["wrapv"] is True

# --- targeted CLI-level rc contract on the real baseline, isolated from the
# whole tree's current (Task-4-owned) divergences via a minimal probe --------

def test_cli_exits_0_on_a_clean_colnew_only_probe(tmp_path):
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O2 -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    r = _run_cli(cc, REAL_BASELINE, "/m")
    assert r.returncode == 0, r.stdout + r.stderr

def test_cli_exits_1_when_that_probe_regresses_to_o2(tmp_path):
    cc = _cc(tmp_path, [{"file": "/m/modules/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -DNDEBUG -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    r = _run_cli(cc, REAL_BASELINE, "/m")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "opt" in r.stdout

# --- whole-tree: the real armed baseline against the real CMake tree -------

_REAL_CC = os.path.join(BUILD_PARITY, "..", "build-cmake", "compile_commands.json")
_REAL_SOURCE_ROOT = os.path.join(BUILD_PARITY, "..")

@pytest.mark.skipif(not os.path.exists(_REAL_CC),
                    reason="requires a built build-cmake/compile_commands.json")
def test_no_compiled_tu_goes_unchecked_on_the_real_tree():
    assert unchecked_suffixes(_REAL_CC, _real_derived(), _REAL_SOURCE_ROOT) == []

# The KNOWN, TRACKED shape of red on the tree (RC-b Task 3, 2026-07-18): 3
# files from the automake _CFLAGS-replaces-AM_CFLAGS footgun (opt/wrapv/ndebug
# mismatches -- history_browser, preferences, types) + 47 files from a CMake
# OpenMP-linking-scope bug in cmake/ScilabModule.cmake's FIND_PACKAGES OpenMP
# handling (openmp-only mismatches): differential_equations under-applies it
# (its ALGO_SOURCES compile into a separate OBJECT library never linked to
# OpenMP, and its C++ GATEWAY_SOURCES never get it either since only
# OpenMP::OpenMP_C, not OpenMP::OpenMP_CXX, is linked); scicos/scicos-cli
# over-apply it (their C gateway sources inherit -fopenmp from the whole
# target being linked to OpenMP::OpenMP_C, even though autotools never puts
# -fopenmp on scicos's own compile lines) -- confirmed against the real
# generated Makefiles on both sides, bidirectional and codegen-neutral (only
# patched_sundials' nvector_openmp.c carries an actual #pragma omp among the
# whole differential_equations/scicos family, and CMake already flags THAT one
# correctly). Both classes were REPRODUCE-not-improve bugs (CMake not matching
# autotools) -- Task 3's job was making this gate DERIVE its expectations and
# correctly FAIL, which it did; Task 4 (2026-07-18) closed all 50: the 3
# footgunned modules now carry C_FLAGS_OVERRIDE (the scilab_object_module()
# mechanism, extended to scilab_module()); differential_equations now links
# OpenMP::OpenMP_CXX too (whenever CXX is in LANG) and propagates it onto its
# ALGO_SOURCES OBJECT lib directly (target_link_libraries reaches an OBJECT
# lib's OWN TUs, but $<TARGET_OBJECTS:...> consumption does not); scicos/
# scicos-cli/xcos dropped FIND_PACKAGES OpenMP entirely and instead add
# libomp's resolved absolute path straight to SYSTEM_LIBS (the pre-existing
# klu/amd/umfpack "matio pattern" in those same files) -- link-only, verified
# byte-identical otool -L dep sets before/after (libomp merely moved link-line
# position, and the harness sorts deps before comparing) with zero compile-
# side effect.
#
# This set exists so a reader hitting rc=1 later can tell "this is the known,
# tracked state" from "something new broke": if a future change narrows it
# further (there is nothing left to narrow today -- it is empty), THIS test
# goes red and must be updated to the smaller set (a welcome failure to fix);
# if the set grows or changes shape instead, that is a real new regression --
# investigate before touching this list, the same discipline Task 3 itself
# was asked to apply to Step 3's expected file list.
_KNOWN_DIVERGENT_FILES = frozenset()

@pytest.mark.skipif(not os.path.exists(_REAL_CC),
                    reason="requires a built build-cmake/compile_commands.json")
def test_real_tree_divergence_is_exactly_the_known_tracked_set():
    mismatches = check_flag_facts(_REAL_CC, _real_derived(), _REAL_SOURCE_ROOT)
    got = {os.path.relpath(m.split(":")[0], _REAL_SOURCE_ROOT) for m in mismatches}
    assert got == _KNOWN_DIVERGENT_FILES, (
        f"new: {sorted(got - _KNOWN_DIVERGENT_FILES)}\n"
        f"resolved: {sorted(_KNOWN_DIVERGENT_FILES - got)}\n"
        "The tracked set is empty (Task 4 closed all 50). If files appear here, "
        "investigate before editing this list -- that is a real new regression, "
        "not table drift.")
