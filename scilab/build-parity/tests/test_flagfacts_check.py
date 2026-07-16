import json, os, subprocess, sys
import pytest
from parity.flagfacts_check import (check_flag_facts, DEFAULT_EXPECTED_BY_SUFFIX,
                                    FILE_EXPECTED_OVERRIDES)

# build-parity root (parent of tests/): the CWD the CLI is run from so that
# `python -m parity.flagfacts_check` can import the parity package.
BUILD_PARITY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def _cc(tmp_path, entries):
    p = tmp_path / "compile_commands.json"; p.write_text(json.dumps(entries)); return str(p)

def _run_cli(cc_path):
    """Invoke the real CLI the way Tasks 5-9 + CI do; return the CompletedProcess."""
    return subprocess.run([sys.executable, "-m", "parity.flagfacts_check", cc_path],
                          cwd=BUILD_PARITY, capture_output=True, text=True)

# --- the two brief-verbatim tests -------------------------------------------

def test_pass_when_all_facts_match(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])
    assert check_flag_facts(cc, {".cpp": {"opt": "O2", "wrapv": True, "min_macos": "11.0"}}) == []

def test_fail_names_the_regressed_fact(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O0 -mmacosx-version-min=11.0 -DNDEBUG -c foo.cpp"}])  # O0 + no fwrapv
    out = check_flag_facts(cc, {".cpp": {"opt": "O2", "wrapv": True, "min_macos": "11.0"}})
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out)

# --- I1: EVERY covered suffix is guarded, and min_macos is guarded ----------
# The parametrization iterates a FIXED spec list, NOT DEFAULT_EXPECTED_BY_SUFFIX
# itself. Iterating the map would make "drop a suffix" a no-op: the case would
# simply vanish and the suite stay green. With a fixed list the case stays put,
# so dropping the suffix from the map leaves a bad TU of it UNCAUGHT -> RED.
REQUIRED_SUFFIXES = (".c", ".cpp", ".cxx", ".cc", ".f", ".F", ".f90")

def test_map_covers_exactly_the_required_suffixes():
    # Locks the map's composition to the census-derived spec: any drift -- a drop
    # OR an unguarded add -- is a deliberate change that must update this list too.
    assert set(DEFAULT_EXPECTED_BY_SUFFIX) == set(REQUIRED_SUFFIXES)

@pytest.mark.parametrize("suffix", REQUIRED_SUFFIXES)
def test_each_required_suffix_is_guarded(tmp_path, suffix):
    # A bad TU (-O0, no -fwrapv) of THIS suffix must be caught by the DEFAULT map
    # (all suffixes are mutually unreachable via endswith). Drop the suffix from
    # DEFAULT_EXPECTED_BY_SUFFIX and this case goes red.
    cc = _cc(tmp_path, [{"file": f"/x/foo{suffix}", "directory": "/x",
        "command": f"cc -O0 -mmacosx-version-min=11.0 -c foo{suffix}"}])  # O0 + no fwrapv
    out = check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX)
    assert any("opt" in m for m in out) and any("wrapv" in m for m in out), \
        f"suffix {suffix!r} is not actually guarded by DEFAULT_EXPECTED_BY_SUFFIX"

def test_min_macos_fact_is_guarded(tmp_path):
    # Drives the DEFAULT map. A TU with correct O2+fwrapv but NO
    # -mmacosx-version-min must still be caught -- proving _BASE guards min_macos,
    # not just opt/wrapv. Drop min_macos from _BASE and this goes red.
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -std=c++17 -O2 -fwrapv -c foo.cpp"}])  # no -mmacosx-version-min
    out = check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX)
    assert any("min_macos" in m for m in out)

# --- I2: the CLI exit-code contract (rc=0 pass / rc=1 mismatch) -------------
# This contract is what Tasks 5-9 + CI consume; an always-exit-0 regression
# would neuter the gate invisibly, so it gets its own guard.

def test_cli_exits_0_on_clean(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -O2 -fwrapv -mmacosx-version-min=11.0 -c foo.cpp"}])
    r = _run_cli(cc)
    assert r.returncode == 0, r.stdout + r.stderr

def test_cli_exits_1_on_mismatch(tmp_path):
    cc = _cc(tmp_path, [{"file": "/x/foo.cpp", "directory": "/x",
        "command": "g++ -O0 -mmacosx-version-min=11.0 -c foo.cpp"}])  # O0 + no fwrapv
    r = _run_cli(cc)
    assert r.returncode == 1, r.stdout + r.stderr
    assert "opt" in r.stdout and "wrapv" in r.stdout

# --- I3: an unknown compiled suffix must FAIL the CLI, not skip silently -----

def test_cli_fails_on_unknown_suffix(tmp_path):
    # compile_commands.json holds only compiled TUs, so a suffix not in the map
    # is an UNCHECKED compiled source -- the CLI must fail loudly, not skip it.
    cc = _cc(tmp_path, [{"file": "/x/foo.rs", "directory": "/x",
        "command": "rustc -O0 -c foo.rs"}])  # .rs is not in the default map
    r = _run_cli(cc)
    assert r.returncode != 0, r.stdout + r.stderr
    assert "unchecked compiled suffix" in r.stdout and ".rs" in r.stdout

# --- I4: the per-FILE override (colnew.f -O0, the IS_MACOSX gfortran workaround)
# must (a) STOP the false positive on the correct baseline, (b) still GUARD the
# file (revert to -O2 -> FAIL), (c) still guard the file's OTHER facts (drop
# -fwrapv -> FAIL), and (d) NOT weaken the general O2 rule for other .f files.
# Same red->green rigor as the suffix guards: each case fails if the override is
# removed OR if it is made too broad.

def test_file_overrides_table_composition():
    # Locks the exception table to its reviewed contents: any addition/removal is
    # a deliberate change that must update this assertion (and get re-reviewed) --
    # a blanket exemption cannot slip in silently. Mirrors
    # test_map_covers_exactly_the_required_suffixes for the suffix map.
    assert FILE_EXPECTED_OVERRIDES == {"colnew.f": {"opt": "O0"}}

def test_colnew_o0_passes_via_file_exception(tmp_path):
    # The real baseline shape: `-O2 -O0` (last-wins -> O0) + fwrapv + min_macos.
    # With the per-file override this is CLEAN -- no false positive. Remove the
    # override (or its colnew.f entry) and this goes red.
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -O2 -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    assert check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX) == []

def test_colnew_o2_still_fails_naming_opt(tmp_path):
    # The guard that matters: if colnew.f is ever compiled -O2 (silently reverting
    # the miscompile workaround), the override's opt=O0 expectation catches it.
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    out = check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX)
    assert any("opt" in m for m in out), out
    assert not any("wrapv" in m or "min_macos" in m for m in out), out

def test_colnew_exception_still_guards_wrapv(tmp_path):
    # The override relaxes ONLY opt: colnew.f at -O0 but WITHOUT -fwrapv must
    # still FAIL on wrapv (proves the override is a merge, not a blanket exempt).
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -O2 -O0 "
        "-mmacosx-version-min=11.0 -c colnew.f"}])  # no -fwrapv
    out = check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX)
    assert any("wrapv" in m for m in out), out
    assert not any("opt" in m for m in out), out  # opt=O0 satisfies the override

def test_non_exception_f_at_o0_still_fails_naming_opt(tmp_path):
    # A DIFFERENT .f at -O0 (fwrapv + min_macos otherwise correct) must still FAIL
    # on opt -- the override is scoped to colnew.f, it does not soften the global
    # O2 rule for the other 847 Fortran TUs.
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/lsoda.f",
        "directory": "/m", "command": "gfortran -g1 -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c lsoda.f"}])
    out = check_flag_facts(cc, DEFAULT_EXPECTED_BY_SUFFIX)
    assert any("opt" in m for m in out), out

def test_cli_exits_0_when_colnew_is_o0(tmp_path):
    # End-to-end rc contract: the exception makes the real CLI exit 0 on a clean
    # baseline colnew.f line (this is what closes the Task-9 flagfacts rc=1).
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -O2 -O0 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    r = _run_cli(cc)
    assert r.returncode == 0, r.stdout + r.stderr

def test_cli_exits_1_when_colnew_reverts_to_o2(tmp_path):
    cc = _cc(tmp_path, [{"file": "/m/differential_equations/src/fortran/colnew.f",
        "directory": "/m", "command": "gfortran -g1 -O2 -fwrapv "
        "-mmacosx-version-min=11.0 -c colnew.f"}])
    r = _run_cli(cc)
    assert r.returncode == 1, r.stdout + r.stderr
    assert "opt" in r.stdout
