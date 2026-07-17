"""LC_RPATH in the parity fingerprint: parser, capture wiring, diff gating.

Rpaths are load-bearing for @rpath dependency resolution (the jvm/JDK modules
resolve libjvm via LC_RPATH): a dropped or spurious rpath changes no exported
symbol, link edge, or SDK stamp, so before this gate the harness sat green while
dyld resolution broke at runtime. Order is SIGNIFICANT throughout -- dyld
searches rpaths in load-command order, so a reorder can flip which library
@rpath resolves to and must fail parity (lists compared as-is, never sorted).
"""
import os

from parity.capture import fingerprint_build, fingerprint_dylib
from parity.diff import diff_fingerprints
from parity.fingerprint import parse_rpaths

OTOOL_L = """\
Load command 12
      cmd LC_RPATH
  cmdsize 32
     path /usr/lib (offset 12)
Load command 13
      cmd LC_RPATH
  cmdsize 56
     path /opt/homebrew/opt/gcc/lib/gcc/current (offset 12)
"""

def test_parse_rpaths_ordered():
    assert parse_rpaths(OTOOL_L) == ["/usr/lib", "/opt/homebrew/opt/gcc/lib/gcc/current"]

def test_parse_rpaths_empty():
    assert parse_rpaths("Load command 0\n cmd LC_SEGMENT_64\n") == []

# Real `otool -l` shape (verified against libsciscicos.2027.dylib 2026-07-16):
# deeper indentation than the fixture above, an @loader_path rpath, and a
# non-RPATH load command interleaved (whose "name" line must not be mistaken
# for an rpath "path" line). Order preserved; "(offset N)" stripped.
OTOOL_L_REAL = """\
Load command 16
          cmd LC_LOAD_DYLIB
      cmdsize 56
         name /usr/lib/libSystem.B.dylib (offset 24)
Load command 17
          cmd LC_RPATH
      cmdsize 24
         path /usr/lib (offset 12)
Load command 18
          cmd LC_RPATH
      cmdsize 48
         path @loader_path/../../scicos/.libs (offset 12)
"""

def test_parse_rpaths_real_output_shape():
    assert parse_rpaths(OTOOL_L_REAL) == ["/usr/lib", "@loader_path/../../scicos/.libs"]


# ---- capture wiring: every dylib and executable fingerprint carries "rpaths" ----

def fake_runner(responses):
    """cmd like ["nm","-gU",path] or ["otool","-L"/"-l",path]; key on the flag
    (mirrors test_capture.py's fake_runner)."""
    def run(cmd):
        return responses[cmd[1]]
    return run

DYLIB_RESPONSES = {
    "-gU": "0000000000006c0c T _a\n",
    "-L": "x:\n\t/usr/local/lib/scilab/libx.2027.dylib (compatibility version 1.0.0, current version 1.0.0)\n",
    "-l": OTOOL_L,
}

def test_fingerprint_dylib_captures_rpaths_in_order():
    fp = fingerprint_dylib("/any/.libs/libx.2027.dylib", roots={}, runner=fake_runner(DYLIB_RESPONSES))
    assert fp["rpaths"] == ["/usr/lib", "/opt/homebrew/opt/gcc/lib/gcc/current"]

def test_fingerprint_dylib_normalizes_rpath_roots_preserving_order():
    # Same roots treatment as install_name/deps: an rpath into the build tree or
    # under $HOME (the real tree has /Users/.../xlnt-prefix/lib) must not leak an
    # absolute machine path into the fingerprint. Order preserved, NOT sorted:
    # the $SCI entry deliberately precedes /usr/lib, so a sorted() slip fails here.
    responses = dict(DYLIB_RESPONSES)
    responses["-l"] = (
        "Load command 1\n      cmd LC_RPATH\n  cmdsize 32\n     path /zzz/build/sub (offset 12)\n"
        "Load command 2\n      cmd LC_RPATH\n  cmdsize 32\n     path /usr/lib (offset 12)\n")
    fp = fingerprint_dylib("/any/.libs/libx.2027.dylib", roots={"/zzz/build": "$SCI"},
                           runner=fake_runner(responses))
    assert fp["rpaths"] == ["$SCI/sub", "/usr/lib"]


def fake_runner_by_path(responses):
    """responses: {path: {flag: output}} (mirrors test_capture.py's fake_runner_by_path)."""
    def run(cmd):
        flag, path = cmd[1], cmd[2]
        return responses[path][flag]
    return run

def _touch(path, content=""):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)

# One `otool -l` stream carries BOTH the LC_BUILD_VERSION block and the LC_RPATH
# list -- the executable fingerprint must take build_version AND rpaths from it.
OTOOL_L_EXE = """\
Load command 10
      cmd LC_BUILD_VERSION
  cmdsize 32
 platform 1
    minos 11.0
      sdk 11.0
Load command 11
      cmd LC_RPATH
  cmdsize 32
     path /usr/lib (offset 12)
Load command 12
      cmd LC_RPATH
  cmdsize 80
     path /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/lib (offset 12)
"""

def test_fingerprint_build_executable_captures_rpaths(tmp_path):
    build_dir = str(tmp_path)
    scilab_bin = os.path.join(build_dir, ".libs", "scilab-bin")
    _touch(scilab_bin)
    responses = {scilab_bin: {"-l": OTOOL_L_EXE, "-L": "scilab-bin:\n"}}
    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path(responses), build_id="t")
    exe = fp["executables"]["scilab-bin"]
    assert exe["build_version"] == {"minos": "11.0", "sdk": "11.0"}   # same -l stream still feeds this
    assert exe["rpaths"] == ["/usr/lib",
                             "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/lib"]


# ---- diff gating: rpath drift fails parity; a pre-rpath BASELINE is not yet gated ----

def _entry(**over):
    e = {"symbols": ["T _a"], "install_name": "n", "deps": [], "tmp_leak": False,
         "rpaths": ["/usr/lib", "/opt/homebrew/opt/gcc/lib/gcc/current"]}
    e.update(over)
    return e

def _fp(**over):
    base = {
        "build_id": "base",
        "executables": {"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "11.0"},
                                       "install_name": "n", "deps": [], "tmp_leak": False,
                                       "rpaths": ["/usr/lib", "$SCI/.libs"]}},
        "dylibs": {"libx.VER.dylib": _entry()},
        "generated": {"etc/classpath.xml": "hash1"},
        "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
    }
    base.update(over)
    return base

def test_identical_rpaths_diff_ok():
    # No false positive from the new field: rpath-bearing fingerprints that
    # MATCH still diff clean.
    assert diff_fingerprints(_fp(), _fp()) == {"ok": True, "differences": []}

def test_dylib_dropped_rpath_is_caught():
    cand = _fp(dylibs={"libx.VER.dylib": _entry(rpaths=["/usr/lib"])})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("libx.VER.dylib" in d and "rpaths" in d for d in r["differences"])

def test_dylib_rpath_reorder_is_caught():
    # dyld searches rpaths in order: a reorder can flip which library @rpath
    # resolves to. The comparison must be order-sensitive -- a set/sorted
    # compare would pass this and be a false green.
    cand = _fp(dylibs={"libx.VER.dylib": _entry(
        rpaths=["/opt/homebrew/opt/gcc/lib/gcc/current", "/usr/lib"])})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("rpaths" in d for d in r["differences"])

def test_executable_spurious_extra_rpath_is_caught():
    cand = _fp()
    cand["executables"]["scilab-bin"]["rpaths"] = ["/usr/lib", "$SCI/.libs", "/stray"]
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("scilab-bin" in d and "rpaths" in d for d in r["differences"])

def test_baseline_without_rpaths_is_not_yet_gated():
    # THE transition case: the committed baseline predates rpath capture (Task 2
    # re-captures it with rpaths). A baseline ENTRY with no "rpaths" key skips
    # the rpath check -- forward-compatible, not a parity failure.
    base = _fp()
    del base["dylibs"]["libx.VER.dylib"]["rpaths"]
    del base["executables"]["scilab-bin"]["rpaths"]
    assert diff_fingerprints(base, _fp()) == {"ok": True, "differences": []}

def test_candidate_missing_rpaths_against_rpath_aware_baseline_is_caught():
    # The reverse is NOT tolerated: once the baseline carries rpaths, a candidate
    # captured with a pre-rpath tool must not silently skip the gate (mirrors
    # the flags-block rule in diff.py). Both the dylib and the executable side.
    cand = _fp()
    del cand["dylibs"]["libx.VER.dylib"]["rpaths"]
    del cand["executables"]["scilab-bin"]["rpaths"]
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("libx.VER.dylib" in d and "rpaths" in d for d in r["differences"])
    assert any("scilab-bin" in d and "rpaths" in d for d in r["differences"])
