from parity.diff import diff_fingerprints
from parity.fingerprint import parse_flag_facts

def _facts(**over):
    f = {"opt": "O2", "wrapv": True, "min_macos": "11.0",
         "openmp": False, "ndebug": True, "std": None}
    f.update(over)
    return f

def _flags(**over):
    fl = {"source": "autotools", "c": _facts(), "cxx": _facts(), "f": _facts()}
    fl.update(over)
    return fl

def _fp(**over):
    base = {
        "build_id": "base",
        "executables": {"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "11.0"},
                                       "install_name": "n", "deps": [], "tmp_leak": False}},
        "dylibs": {"libx.VER.dylib": {"symbols": ["T _a", "T _b"], "install_name": "n",
                                      "deps": ["libc (v)"], "tmp_leak": False}},
        "generated": {"etc/classpath.xml": "hash1"},
        "flags": _flags(),
    }
    base.update(over)
    return base

def test_identical_is_ok():
    assert diff_fingerprints(_fp(), _fp()) == {"ok": True, "differences": []}

def test_removed_symbol_is_caught():
    cand = _fp(dylibs={"libx.VER.dylib": {"symbols": ["T _a"], "install_name": "n",
                                          "deps": ["libc (v)"], "tmp_leak": False}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("_b" in d and "libx.VER.dylib" in d for d in r["differences"])

def test_missing_dylib_is_caught():
    cand = _fp(dylibs={})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("libx.VER.dylib" in d and "missing" in d.lower() for d in r["differences"])

def test_executable_install_name_change_is_caught():
    # An executable's install_name holds its FIRST linked library; a Stage-1 link
    # reorder that changes it must be caught (it was silently uncompared before).
    cand = _fp(executables={"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "11.0"},
                                           "install_name": "DIFFERENT", "deps": [], "tmp_leak": False}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("scilab-bin" in d and "install_name" in d for d in r["differences"])

def test_sdk_stamp_change_is_caught():
    cand = _fp(executables={"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "26.0"},
                                           "install_name": "n", "deps": [], "tmp_leak": False}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("sdk" in d.lower() for d in r["differences"])

def test_tmp_leak_is_caught():
    cand = _fp(dylibs={"libx.VER.dylib": {"symbols": ["T _a", "T _b"], "install_name": "n",
                                          "deps": ["libc (v)"], "tmp_leak": True}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("tmp" in d.lower() for d in r["differences"])

def test_generated_hash_change_is_caught():
    cand = _fp(generated={"etc/classpath.xml": "hash2"})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("classpath.xml" in d for d in r["differences"])

def test_flag_wrapv_flip_is_caught():
    cand = _fp(flags=_flags(c=_facts(wrapv=False)))
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("flags c" in d and "wrapv" in d for d in r["differences"])

def test_flag_source_label_change_alone_is_ok():
    # autotools -> cmake IS the migration; identical facts must diff clean or the
    # harness fails parity on its own reason for existing.
    assert diff_fingerprints(_fp(), _fp(flags=_flags(source="cmake")))["ok"] is True

def test_flag_language_absent_in_candidate_is_caught():
    cand = _fp(flags=_flags(f=None))
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("flags f" in d for d in r["differences"])

def test_flags_block_missing_on_both_sides_is_ok():
    # Two pre-manifest fingerprints (no "flags" key at all) still diff cleanly --
    # the comparison must not KeyError on the old schema.
    base, cand = _fp(), _fp()
    del base["flags"], cand["flags"]
    assert diff_fingerprints(base, cand) == {"ok": True, "differences": []}

def test_flags_block_missing_in_candidate_only_is_caught():
    # A candidate captured with a pre-manifest tool must NOT silently skip the
    # flag check against a manifest-bearing baseline.
    cand = _fp()
    del cand["flags"]
    assert diff_fingerprints(_fp(), cand)["ok"] is False

def test_flags_block_missing_in_baseline_only_is_caught():
    # The reverse direction: a pre-manifest BASELINE vs a manifest-bearing
    # candidate exercises the "facts extra in candidate" branch -- flagged too.
    base = _fp()
    del base["flags"]
    assert diff_fingerprints(base, _fp())["ok"] is False

# THE fault-injection acceptance: the exact drift that sat green for days (all C
# at -O0 / no -fwrapv; fixed in 516c57573cc), expressed with the REAL pre-fix and
# post-fix SCI_CFLAGS values. A candidate with the regressed C facts MUST fail
# parity against a correct baseline, naming opt and wrapv.
REGRESSED_CFLAGS = ("-DNDEBUG -mmacosx-version-min=11.0 "
                    "-Werror=implicit -Werror=incompatible-pointer-types")
CORRECT_CFLAGS = ("-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector "
                  "-Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types")

def test_fault_injection_regressed_cflags_fail_parity():
    base = _fp(flags=_flags(c=parse_flag_facts(CORRECT_CFLAGS)))
    cand = _fp(flags=_flags(c=parse_flag_facts(REGRESSED_CFLAGS)))
    r = diff_fingerprints(base, cand)
    assert r["ok"] is False
    flagged = [d for d in r["differences"] if d.startswith("flags c")]
    assert flagged, r["differences"]
    assert any("opt" in d for d in flagged)
    assert any("wrapv" in d for d in flagged)
