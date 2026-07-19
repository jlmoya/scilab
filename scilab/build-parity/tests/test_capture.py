import hashlib
import locale
import os
import re

import pytest

from parity.capture import (fingerprint_dylib, fingerprint_build, capture_flag_manifest,
                            MACRO_BIN_MANIFEST_KEY)

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
        "-l": "",   # no LC_RPATH here; rpath capture itself is pinned in test_rpath.py
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
            "-l": "",
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

    # generated_cmake is ALWAYS present (mirrors header_defines) even though this
    # synthetic tree has no build-cmake/generated/ dir at all -- an empty dict, not a
    # missing key, which is what lets the diff tell "old capture.py" apart from
    # "this tool, found nothing" (see the diff.py transition-rule comment).
    assert fp["generated_cmake"] == {}


# --- RC-c final-review Finding (Critical): generated_cmake ------------------
#
# capture.py's `generated` dict resolves GENERATED_FILES against build_dir, which is
# always the SOURCE TREE -- configure's own copy -- regardless of which build produced
# the fingerprint. It never looks at build-cmake/generated/, so a corrupted or stale
# CMake-generated file was invisible to parity (proven end-to-end against the real
# tree). `generated_cmake` hashes CMake's OWN copies from build-cmake/generated/,
# closing that gap; parity/diff.py checks it against the baseline's existing
# `generated` hashes (see test_diff.py for the comparison-side tests).

def test_fingerprint_build_captures_generated_cmake_files(tmp_path):
    build_dir = str(tmp_path)
    cmake_scilab_pc = os.path.join(build_dir, "build-cmake/generated/scilab.pc")
    cmake_version_incl = os.path.join(build_dir, "build-cmake/generated/Version.incl")
    _touch(cmake_scilab_pc, "prefix=/usr/local\n")
    _touch(cmake_version_incl, "SCIVERSION=scilab-branch-2027.0\n")
    # modules/core/includes/machine.h deliberately NOT created under build-cmake/generated/
    # -- it resolves through build-cmake/generated-includes/ instead (header_defines'
    # job), so it must stay absent from generated_cmake even though it IS one of
    # GENERATED_FILES's 13 entries this loop walks looking for.

    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")

    assert set(fp["generated_cmake"].keys()) == {"scilab.pc", "Version.incl"}
    expected_pc_hash = hashlib.sha256("prefix=/usr/local\n".encode("utf-8")).hexdigest()
    assert fp["generated_cmake"]["scilab.pc"] == expected_pc_hash
    assert "modules/core/includes/machine.h" not in fp["generated_cmake"]


def test_generated_cmake_hash_is_roots_normalized(tmp_path):
    # Same normalize_path(..., roots) treatment as the "generated" block above --
    # matters for a file that (like scilab.pc's install paths, in the real tree) can
    # embed an absolute checkout path.
    build_dir = str(tmp_path)
    p = os.path.join(build_dir, "build-cmake/generated/scilab.pc")
    _touch(p, f"prefix={build_dir}/usr\n")
    roots = {build_dir: "$SCI"}

    fp = fingerprint_build(build_dir, roots=roots, runner=fake_runner_by_path({}), build_id="t")

    expected = hashlib.sha256("prefix=$SCI/usr\n".encode("utf-8")).hexdigest()
    assert fp["generated_cmake"]["scilab.pc"] == expected


def test_generated_cmake_differs_from_source_tree_copy_when_corrupted(tmp_path):
    # The exact shape of the reviewer's exploit, at the unit level: build-cmake/generated/
    # diverges from the source tree, and generated_cmake must reflect what CMake ACTUALLY
    # wrote, not silently re-hash the source tree's copy (which is what "generated" does,
    # and why it alone cannot catch this).
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "scilab.pc"), "prefix=/usr/local\n")
    _touch(os.path.join(build_dir, "build-cmake/generated/scilab.pc"), "CORRUPTED\n")

    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")

    assert fp["generated"]["scilab.pc"] != fp["generated_cmake"]["scilab.pc"]


# --- Later final-review Finding: version.h shared the SAME gap ------------------
#
# `generated_cmake`'s loop above resolves every GENERATED_FILES entry against
# build-cmake/generated/<rel> -- true for the ten RC-c files, but version.h's CMake copy
# actually lands in build-cmake/generated-includes/version.h (no build-cmake/generated/
# counterpart at all), so it fell through the `os.path.exists` guard exactly like a
# missing file and stayed silently absent from generated_cmake -- proven end-to-end:
# corrupting build-cmake/generated-includes/version.h's SCI_VERSION_MAJOR still reported
# PARITY OK. _GENERATED_CMAKE_PATH_OVERRIDES (capture.py, next to GENERATED_FILES) is the
# fix: an explicit path for the one entry that is not at the default location. machine.h
# sits in that SAME generated-includes/ directory and must stay excluded from
# generated_cmake either way -- it is header_defines' job, not this dimension's, because
# (unlike version.h) CMake's machine.h is not byte-identical to configure's.

def test_fingerprint_build_captures_version_h_from_generated_includes(tmp_path):
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "build-cmake/generated-includes/version.h"),
           "#define SCI_VERSION_MAJOR 2027\n")
    # machine.h in the SAME directory -- must stay OUT of generated_cmake (see the
    # docstring above), proving this is a version.h-specific override, not "capture
    # everything under generated-includes/".
    _touch(os.path.join(build_dir, "build-cmake/generated-includes/machine.h"),
           "#define SOME_MACRO 1\n")

    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")

    expected_hash = hashlib.sha256("#define SCI_VERSION_MAJOR 2027\n".encode("utf-8")).hexdigest()
    assert fp["generated_cmake"]["modules/core/includes/version.h"] == expected_hash
    assert "modules/core/includes/machine.h" not in fp["generated_cmake"]


def test_generated_cmake_version_h_differs_from_source_tree_copy_when_corrupted(tmp_path):
    # version.h's analogue of test_generated_cmake_differs_from_source_tree_copy_when_
    # corrupted: this is the exact reviewer exploit (SCI_VERSION_MAJOR 2027 -> 6666 in
    # build-cmake/generated-includes/version.h) reproduced at the unit level.
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "modules/core/includes/version.h"),
           "#define SCI_VERSION_MAJOR 2027\n")
    _touch(os.path.join(build_dir, "build-cmake/generated-includes/version.h"),
           "#define SCI_VERSION_MAJOR 6666\n")

    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")

    assert (fp["generated"]["modules/core/includes/version.h"]
            != fp["generated_cmake"]["modules/core/includes/version.h"])


def test_generated_files_covers_the_rc_c_inventory():
    """The 9 configure-substituted files RC-c generates, plus Version.incl, plus the
    3 that predate it. Pinned by exact set: this list IS the gate's coverage, and a
    silent shrink is exactly the failure mode the campaign keeps rediscovering.

    NOT here on purpose -- etc/classpath.xml is, but scilab-lib.properties and
    scilab-lib-doc.properties are deferred to Stage 2 (Ant->Maven) along with the jar
    -path search that feeds them; see the RC-c design doc S4.
    """
    from parity.capture import GENERATED_FILES
    assert set(GENERATED_FILES) == {
        "etc/classpath.xml",
        "modules/core/includes/machine.h",
        "modules/core/includes/version.h",
        "build.incl.xml",
        "scilab.pc",
        "scilab.properties",
        "etc/logging.properties",
        "etc/modules.xml",
        "etc/Info.plist",
        "modules/helptools/etc/SciDocConf.xml",
        "modules/atoms/etc/repositories",
        "modules/atoms/tests/unit_tests/repositories.orig",
        "Version.incl",
    }


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
        libz_a: {"-gU": "0000000000006c0c T _A\n", "-L": "a:\n", "-l": ""},
        libz_b: {"-gU": "0000000000006c0c T _B\n", "-L": "b:\n", "-l": ""},
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
            "-l": "",
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


def test_macro_bin_manifest_changes_when_a_bin_file_CONTENT_changes(tmp_path):
    """RC-d: the manifest gates CONTENT, not just presence.

    Before RC-d this hashed a sorted path list, so a .bin present at the right
    path with wrong bytes was invisible -- precisely the failure a migration of
    the macro compiler risks. The .bin output is deterministic (measured: two
    independent full rebuilds, 0 of 3516 files differing), so content hashing is
    strict rather than flaky.
    """
    from parity.capture import fingerprint_build, MACRO_BIN_MANIFEST_KEY
    mac = tmp_path / "modules" / "core" / "macros"
    mac.mkdir(parents=True)
    (tmp_path / ".libs").mkdir()
    bin_file = mac / "who_user.bin"

    bin_file.write_bytes(b"AST-BYTES-ONE")
    before = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]

    bin_file.write_bytes(b"AST-BYTES-TWO")   # same path, different bytes
    after = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]

    assert before != after, "a .bin's content changed but the manifest hash did not"


def test_macro_bin_manifest_still_changes_when_a_bin_goes_missing(tmp_path):
    """The presence property the old gate had must NOT regress -- strengthening a
    gate should be strictly additive."""
    from parity.capture import fingerprint_build, MACRO_BIN_MANIFEST_KEY
    mac = tmp_path / "modules" / "core" / "macros"
    mac.mkdir(parents=True)
    (tmp_path / ".libs").mkdir()
    (mac / "a.bin").write_bytes(b"x")
    (mac / "b.bin").write_bytes(b"y")
    before = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]
    (mac / "b.bin").unlink()
    after = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]
    assert before != after, "a .bin vanished but the manifest hash did not change"


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


def fake_reader(files):
    """reader(path) -> str | None, keyed on the basename (config.status /
    compile_commands.json) -- mirrors fake_runner's injection idea for file I/O."""
    def read(path):
        return files.get(os.path.basename(path))
    return read


# The real S["..."]= lines from the autotools config.status (verified 2026-07-15).
CONFIG_STATUS_SNIPPET = '''\
S["SCI_FFLAGS"]="-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0"
S["SCI_CXXFLAGS"]="-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector -Wall -Wpedantic"
S["SCI_CFLAGS"]="-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector -Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types"
'''

def test_capture_flag_manifest_autotools():
    m = capture_flag_manifest("/b", reader=fake_reader({"config.status": CONFIG_STATUS_SNIPPET}))
    assert m["source"] == "autotools"
    for lang in ("c", "cxx", "f"):
        assert m[lang]["opt"] == "O2"
        assert m[lang]["wrapv"] is True
        assert m[lang]["min_macos"] == "11.0"

# Autoconf splits any S["..."]="..." value longer than 148 chars across
# backslash-continuation lines -- `"…first…"\` newline `"…rest…"` -- and the cut
# lands MID-TOKEN (the real FLIBS/LDFLAGS/PKG_CONFIG_PATH in config.status all
# have this shape; first segment exactly 148 chars, verified 2026-07-15).
# SCI_CFLAGS is 140 chars today, 8 under the cliff: the very next flag appended
# would split it. Here the cut straddles -fwrapv itself ("-fwr" | "apv"), so a
# first-segment-only read sees no -fwrapv at all; SCI_CXXFLAGS carries TWO
# continuation lines (the FLIBS length) with -std=c++17 split at the second
# boundary; SCI_FFLAGS stays unsplit -- both spellings coexist in one file.
SPLIT_CONFIG_STATUS_SNIPPET = '''\
S["SCI_FFLAGS"]="-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0"
S["SCI_CXXFLAGS"]="-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector -Wall -Wpedantic -Wextra -Wno-deprecated-declarations -Wno-unused-parameter "\\
"-Wno-sign-compare -Werror=return-type -fvisibility=hidden -fvisibility-inlines-hidden -fno-common -pipe -fPIC -Wformat=2 -Wshadow -Wpointer-arith -s"\\
"td=c++17"
S["SCI_CFLAGS"]="-DNDEBUG -g1 -O2 -mmacosx-version-min=11.0 -fno-stack-protector -Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types -std=gnu17 -fwr"\\
"apv"
'''

def test_capture_flag_manifest_autotools_continuation_split():
    # Fixture honesty: the first SCI_CFLAGS segment is exactly 148 chars (the
    # real autoconf cliff) and ends mid-token in "-fwr" -- what a naive
    # first-segment-only regex would hand to the parser.
    seg1 = re.search(r'S\["SCI_CFLAGS"\]="([^"]*)"', SPLIT_CONFIG_STATUS_SNIPPET).group(1)
    assert len(seg1) == 148 and seg1.endswith("-fwr")

    m = capture_flag_manifest("/b", reader=fake_reader({"config.status": SPLIT_CONFIG_STATUS_SNIPPET}))
    assert m["source"] == "autotools"
    # -fwrapv straddles the boundary as "-fwr"|"apv": only a DIRECT (no-space)
    # join of the continuation segments reassembles it.
    assert m["c"]["wrapv"] is True
    assert m["c"]["std"] == "gnu17"       # the tail before the cut still parses
    assert m["c"]["opt"] == "O2"
    # Two continuation lines, -std=c++17 split as "-s"|"td=c++17" at the SECOND
    # boundary: every segment must be consumed, not just the first continuation.
    assert m["cxx"]["std"] == "c++17"
    assert m["cxx"]["wrapv"] is True
    # The unsplit spelling in the same file still parses.
    assert m["f"]["opt"] == "O2"

def test_capture_flag_manifest_autotools_missing_language_is_none():
    m = capture_flag_manifest("/b", reader=fake_reader({"config.status": 'S["SCI_CFLAGS"]="-O2"\n'}))
    assert m["source"] == "autotools"
    assert m["c"]["opt"] == "O2"
    assert m["cxx"] is None
    assert m["f"] is None

# A small CMake compile_commands.json: one "command" entry, one "arguments"
# entry, extension-cased Fortran, and a non-compiled-language entry to skip.
# (No CMake tree exists yet -- this is the fixture the brief prescribes.)
COMPILE_COMMANDS_SNIPPET = '''\
[
  {"directory": "/b", "file": "/src/foo.c",
   "command": "cc -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0 -c /src/foo.c -o foo.o"},
  {"directory": "/b", "file": "/src/bar.cpp",
   "arguments": ["c++", "-O2", "-fwrapv", "-std=c++17", "-c", "/src/bar.cpp", "-o", "bar.o"]},
  {"directory": "/b", "file": "/src/baz.F",
   "command": "gfortran -O2 -fwrapv -c /src/baz.F -o baz.o"},
  {"directory": "/b", "file": "/src/skip.s", "command": "as /src/skip.s"}
]
'''

def test_capture_flag_manifest_cmake():
    m = capture_flag_manifest("/b", reader=fake_reader({"compile_commands.json": COMPILE_COMMANDS_SNIPPET}))
    assert m["source"] == "cmake"
    assert m["c"]["opt"] == "O2"
    assert m["c"]["wrapv"] is True
    assert m["c"]["min_macos"] == "11.0"
    assert m["c"]["ndebug"] is True
    assert m["cxx"]["std"] == "c++17"     # the "arguments" (list) spelling works too
    assert m["f"]["opt"] == "O2"          # .F (uppercase) grouped as fortran

def test_capture_flag_manifest_config_status_wins_over_compile_commands():
    # Precedence: an autotools tree that ALSO carries a stray compile_commands.json
    # (a Stage-1 CMake experiment run inside it) still reports the autotools flags.
    m = capture_flag_manifest("/b", reader=fake_reader({
        "config.status": CONFIG_STATUS_SNIPPET,
        "compile_commands.json": '[{"directory": "/b", "file": "/src/x.c", "command": "cc -O0 -c /src/x.c"}]',
    }))
    assert m["source"] == "autotools"
    assert m["c"]["opt"] == "O2"

def test_capture_flag_manifest_unknown_when_neither_exists():
    assert capture_flag_manifest("/b", reader=fake_reader({})) == {
        "source": "unknown", "c": None, "cxx": None, "f": None}

def test_fingerprint_build_includes_flag_manifest(tmp_path):
    # Wiring + the DEFAULT (real file) reader: a config.status in the tree surfaces
    # as the top-level "flags" block of the fingerprint.
    build_dir = str(tmp_path)
    _touch(os.path.join(build_dir, "config.status"), CONFIG_STATUS_SNIPPET)
    fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")
    assert fp["flags"]["source"] == "autotools"
    assert fp["flags"]["c"]["opt"] == "O2"
    assert fp["flags"]["c"]["wrapv"] is True

def test_fingerprint_build_flags_unknown_on_bare_tree(tmp_path):
    # No config.status / compile_commands.json (every pre-existing tmp_path test's
    # shape): the flags block degrades to source=unknown, never a crash.
    fp = fingerprint_build(str(tmp_path), roots={}, runner=fake_runner_by_path({}), build_id="t")
    assert fp["flags"] == {"source": "unknown", "c": None, "cxx": None, "f": None}


# --- Finding 1 (RC-c review): locale-independent hashing -------------------
#
# Both _file_reader and fingerprint_build's generated-file loop used to open
# files with no explicit `encoding=`, so Python decoded with the CAPTURING
# PROCESS's locale-preferred codec (locale.getpreferredencoding) rather than
# the file's actual encoding. Every generated file this harness reads is
# written in UTF-8; on a machine/container whose default locale is NOT UTF-8
# (LANG=C, no C.UTF-8 fallback -- a bare debian-slim-style container, unlike
# this repo's CI image which happens to escape via PEP 538 locale coercion),
# any non-ASCII byte decoded differently, changing the hash against an
# UNMODIFIED tree: a false "PARITY FAILED — generated file changed". Two real
# files carry non-ASCII today -- scilab.properties ("Dassault Systèmes") and
# etc/Info.plist ("© ... Dassault Systèmes") -- so this is not hypothetical.
#
# Both tests force the "C" locale (ASCII-range default codec) for the read and
# assert the real UTF-8 bytes still come back correctly. Without
# encoding="utf-8" pinned in capture.py, `errors="replace"` silently swaps
# each non-ASCII byte for U+FFFD instead of raising, so these fail by
# content/hash mismatch, not by exception.

_NON_ASCII_LINE = "\t<string>Scilab 2027.0.0, © 2022-2026 Dassault Systèmes</string>\n"


def _assert_c_locale_is_not_utf8():
    # Sanity gate: if some future platform's "C" locale resolved to UTF-8, both
    # tests below would pass vacuously (same codec on both sides of the
    # comparison) without ever exercising the bug this pins. Belt-and-braces
    # with the explicit forced read below, not a substitute for it.
    #
    # RC-c final-review Finding (Minor 4): PEP 686 makes UTF-8 mode the interpreter
    # default starting around Python 3.15, which can make locale.getpreferredencoding
    # report "utf-8" even under LC_ALL=C. When that happens, this is a property of the
    # PLATFORM/interpreter, not a fresh regression in the code under test -- so this
    # must SKIP the two tests below, not hard-fail the suite. (A real encoding
    # regression is still caught independently: capture.py pins encoding="utf-8"
    # explicitly at both call sites regardless of locale, which is what those tests
    # assert against a hash computed with an explicit codec of its own -- see
    # test_generated_file_hash_is_locale_independent's expected_hash.)
    if locale.getpreferredencoding(False).lower() in ("utf-8", "utf8"):
        pytest.skip(
            "this platform's 'C' locale resolves to UTF-8 (PEP 686 UTF-8-by-default?) "
            "-- the locale-independence regression this pins cannot be distinguished "
            "from the fix on this interpreter")


def test_file_reader_is_locale_independent(tmp_path):
    # _file_reader (capture_flag_manifest's default reader, capture.py:67) --
    # one of the two call sites Finding 1 named.
    from parity.capture import _file_reader

    path = os.path.join(str(tmp_path), "config.status")
    # Written with an explicit codec regardless of the ambient locale, so the
    # ON-DISK bytes are deterministic no matter what environment runs this test
    # -- only the READ below is exercised under a forced non-UTF-8 default.
    with open(path, "w", encoding="utf-8") as f:
        f.write(_NON_ASCII_LINE)

    saved = locale.setlocale(locale.LC_ALL)
    try:
        locale.setlocale(locale.LC_ALL, "C")
        _assert_c_locale_is_not_utf8()
        result = _file_reader(path)
    finally:
        locale.setlocale(locale.LC_ALL, saved)   # process-global state; never leak into other tests

    assert result == _NON_ASCII_LINE


def test_generated_file_hash_is_locale_independent(tmp_path):
    # fingerprint_build's GENERATED_FILES loop (capture.py:~307-312) -- the
    # OTHER call site Finding 1 named, and the one the review reproduced
    # end-to-end against the real scilab.properties / etc/Info.plist. Pinned
    # against a hash computed independently (an explicit UTF-8 encode), not
    # merely "two captures agree" -- a comparison a shared bug could still pass.
    build_dir = str(tmp_path)
    path = os.path.join(build_dir, "etc/classpath.xml")   # any GENERATED_FILES member
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(_NON_ASCII_LINE)

    expected_hash = hashlib.sha256(_NON_ASCII_LINE.encode("utf-8", "replace")).hexdigest()

    saved = locale.setlocale(locale.LC_ALL)
    try:
        locale.setlocale(locale.LC_ALL, "C")
        _assert_c_locale_is_not_utf8()
        fp = fingerprint_build(build_dir, roots={}, runner=fake_runner_by_path({}), build_id="t")
    finally:
        locale.setlocale(locale.LC_ALL, saved)

    assert fp["generated"]["etc/classpath.xml"] == expected_hash
