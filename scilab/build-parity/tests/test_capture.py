import os

import pytest

from parity.capture import fingerprint_dylib, fingerprint_build

def fake_runner(responses):
    def run(cmd):
        # cmd like ["nm","-gU",path] or ["otool","-L"/"-l",path]; key on the flag.
        key = cmd[1]
        return responses[key]
    return run

def test_fingerprint_dylib_combines_nm_and_otool():
    responses = {
        "-gU": "0000000000006c0c T _CdfBase\n0000000000000001 T _b\n",
        "-L": "x:\n\t/usr/local/lib/scilab/libx.2027.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
              "\t/opt/homebrew/lib/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)\n",
    }
    roots = {"/opt/homebrew": "$BREW"}
    fp = fingerprint_dylib("/any/.libs/libx.2027.dylib", roots, runner=fake_runner(responses))
    assert fp["symbols"] == ["T _CdfBase", "T _b"]
    assert fp["deps"] == ["$BREW/lib/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)"]
    assert fp["install_name"] == "/usr/local/lib/scilab/libx.VER.dylib (compatibility version 1.0.0, current version 1.0.0)"
    assert fp["tmp_leak"] is False


def fake_runner_by_path(responses):
    """responses: {path: {flag: output}}. cmd like ["nm","-gU",path] or ["otool","-L"/"-l",path];
    keyed on (path, flag) so distinct files in the same synthetic tree can return
    distinct canned output (mirrors fake_runner's "key on cmd" idea, extended to
    disambiguate when a tree has more than one file)."""
    def run(cmd):
        flag, path = cmd[1], cmd[2]
        return responses[path][flag]
    return run


def _touch(path, content=""):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)


def test_fingerprint_build_walks_dylibs_executables_and_generated(tmp_path):
    build_dir = str(tmp_path)
    libz = os.path.join(build_dir, "modules/core/.libs/libz.2027.dylib")
    libz_bare = os.path.join(build_dir, "modules/core/.libs/libz.dylib")
    scilab_bin = os.path.join(build_dir, ".libs/scilab-bin")
    classpath = os.path.join(build_dir, "etc/classpath.xml")

    _touch(libz)
    _touch(libz_bare)  # bare-name symlink sibling in real trees; a plain file here
                       # is enough since the walk predicate only looks at the name.
    _touch(scilab_bin)
    _touch(classpath, "<classpath/>\n")
    # modules/core/includes/{machine,version}.h deliberately NOT created --
    # a missing generated file must be skipped, not crash the walk.

    responses = {
        libz: {
            "-gU": "0000000000006c0c T _CdfBase\n",
            "-L": "x:\n\t/usr/local/lib/scilab/libz.2027.dylib (compatibility version 1.0.0, current version 1.0.0)\n",
        },
        scilab_bin: {
            "-l": "      cmd LC_BUILD_VERSION\n    minos 11.0\n      sdk 11.0\n",
            "-L": "scilab-bin:\n\t/usr/local/lib/scilab/libz.2027.dylib (compatibility version 1.0.0, current version 1.0.0)\n",
        },
    }
    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path(responses), build_id="t")

    # The real versioned dylib is captured exactly once, keyed by its normalized
    # name; the bare-name symlink sibling is NOT double-counted.
    assert list(fp["dylibs"].keys()) == ["libz.VER.dylib"]
    assert fp["dylibs"]["libz.VER.dylib"]["symbols"] == ["T _CdfBase"]

    # The executable under .libs/scilab-bin is captured.
    assert list(fp["executables"].keys()) == ["scilab-bin"]
    assert fp["executables"]["scilab-bin"]["build_version"] == {"minos": "11.0", "sdk": "11.0"}

    # The present generated file is hashed; the two missing ones are skipped (not a crash).
    assert list(fp["generated"].keys()) == ["etc/classpath.xml"]


def test_fingerprint_build_raises_on_dylib_key_collision(tmp_path):
    # Two different .libs dirs each holding a libz.2027.dylib (different fake
    # symbols -- these are genuinely different files, e.g. a stale artifact left
    # behind next to a fresh one) collide on the same normalized key. Finding 1:
    # this must be loud (ValueError), never a silent dict overwrite.
    build_dir = str(tmp_path)
    libz_a = os.path.join(build_dir, "a/.libs/libz.2027.dylib")
    libz_b = os.path.join(build_dir, "b/.libs/libz.2027.dylib")
    _touch(libz_a)
    _touch(libz_b)

    responses = {
        libz_a: {"-gU": "0000000000006c0c T _A\n", "-L": "a:\n"},
        libz_b: {"-gU": "0000000000006c0c T _B\n", "-L": "b:\n"},
    }
    with pytest.raises(ValueError):
        fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path(responses), build_id="t")
