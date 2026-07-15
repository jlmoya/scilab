import os

import pytest

from parity.capture import fingerprint_dylib, fingerprint_build, MACRO_BIN_MANIFEST_KEY

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
    # Path-only: the otool "(compatibility version X, current version Y)" suffix is
    # stripped by parse_otool_libs (I3) so a `brew upgrade`'s `current version` bump
    # doesn't show up here at all.
    assert fp["deps"] == ["$BREW/lib/libgfortran.5.dylib"]
    assert fp["install_name"] == "/usr/local/lib/scilab/libx.VER.dylib"
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
    os.symlink(libz, libz_bare)  # the real bare-name symlink sibling (as libtool
                                 # creates it) -- must be excluded via os.path.islink,
                                 # not by "does the name carry a 4-digit version token".
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

    # The present generated file is hashed; the two missing ones are skipped (not a
    # crash); the macro .bin manifest entry is always present (empty set here --
    # this synthetic tree has no macros/ dir).
    assert list(fp["generated"].keys()) == ["etc/classpath.xml", MACRO_BIN_MANIFEST_KEY]


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


def test_fingerprint_build_captures_real_non_4digit_versioned_dylib_not_its_symlink(tmp_path):
    # I1 regression: libxlnt.1.6.1.dylib is a REAL built file (linked by
    # libscispreadsheet, 1720 exported symbols in the real tree) whose version is
    # NOT a 4-digit token. The old "does the name carry a 4-digit version token"
    # proxy for "real file, not symlink" silently excluded it. The predicate must
    # key off os.path.islink, so this non-4-digit-versioned real file IS captured
    # while its bare-name symlink sibling is NOT.
    build_dir = str(tmp_path)
    libxlnt = os.path.join(build_dir, "modules/spreadsheet/.libs/libxlnt.1.6.1.dylib")
    libxlnt_bare = os.path.join(build_dir, "modules/spreadsheet/.libs/libxlnt.dylib")

    _touch(libxlnt)
    os.symlink(libxlnt, libxlnt_bare)

    responses = {
        libxlnt: {
            "-gU": "0000000000006c0c T __ZN4xlnt3zip4x\n",
            "-L": "x:\n\t/usr/local/lib/scilab/libxlnt.1.6.1.dylib (compatibility version 1.0.0, current version 1.0.0)\n",
        },
    }
    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path(responses), build_id="t")

    # normalize_version leaves a non-4-digit version token untouched, so the real
    # file is keyed by its own basename -- and it IS captured (not silently dropped).
    assert list(fp["dylibs"].keys()) == ["libxlnt.1.6.1.dylib"]
    # The symlink sibling is not double-counted (would KeyError on the fake runner
    # if it were, since only `libxlnt` has canned responses above).
    assert len(fp["dylibs"]) == 1


def _bin_manifest(build_dir):
    return fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")["generated"][
        MACRO_BIN_MANIFEST_KEY]


def test_macro_bin_manifest_changes_when_a_bin_file_goes_missing(tmp_path):
    # I2: the harness captures the SET of macro .bin paths (presence, not content)
    # so a Stage-1 CMake bootstrap that misses a module's macros is caught even
    # though no dylib/executable/named-generated-file changed.
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "modules/signal_processing/macros/conv.bin"))
    _touch(os.path.join(build_dir, "modules/signal_processing/macros/fftshift.bin"))
    # A nested macros subdir, matching the real tree's shape (e.g.
    # modules/assert/macros/assert/assert_checkerror.bin).
    _touch(os.path.join(build_dir, "modules/assert/macros/assert/assert_checkerror.bin"))

    full_hash = _bin_manifest(build_dir)

    os.remove(os.path.join(build_dir, "modules/signal_processing/macros/fftshift.bin"))
    reduced_hash = _bin_manifest(build_dir)

    assert full_hash != reduced_hash


def test_macro_bin_manifest_ignores_bin_files_outside_a_macros_dir(tmp_path):
    # Real trees have a handful of unrelated .bin files (e.g. .atoms/toremove.bin,
    # JCEF's v8_context_snapshot.arm64.bin) that are not compiled macros and must
    # not be swept into the manifest.
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "modules/signal_processing/macros/conv.bin"))
    with_only_macro = _bin_manifest(build_dir)

    _touch(os.path.join(build_dir, ".atoms/toremove.bin"))
    _touch(os.path.join(build_dir, "lib/thirdparty/jcef/Resources/v8_context_snapshot.arm64.bin"))
    with_unrelated_bins_too = _bin_manifest(build_dir)

    assert with_only_macro == with_unrelated_bins_too
