from parity.diff import diff_fingerprints

def _fp(**over):
    base = {
        "build_id": "base",
        "executables": {"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "11.0"},
                                       "install_name": "n", "deps": [], "tmp_leak": False}},
        "dylibs": {"libx.VER.dylib": {"symbols": ["T _a", "T _b"], "install_name": "n",
                                      "deps": ["libc (v)"], "tmp_leak": False}},
        "generated": {"etc/classpath.xml": "hash1"},
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
