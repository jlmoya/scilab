from parity.diff import diff_fingerprints, _GENERATED_CMAKE_KEYS
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


# --- RC-c final-review Finding (Critical): generated_cmake ------------------
#
# `generated` alone only ever hashes the SOURCE TREE copy of an RC-c file (configure's
# own output) -- on BOTH the baseline and the candidate side -- so it can never detect a
# corrupted or stale build-cmake/generated/ file: proven end-to-end (corrupting
# build-cmake/generated/{Version.incl,scilab.pc,etc/logging.properties} still reported
# PARITY OK before this fix). `generated_cmake` is the missing half: CMake's OWN copies,
# checked against the baseline's EXISTING `generated` hashes (no separate
# `generated_cmake` baseline section -- arming one is out of scope and unneeded, since
# `base["generated"]` already carries real hashes for all ten RC-c files).
#
# An "armed baseline" here means `base["generated"]` carries the RC-c file's hash --
# true of every baseline captured since RC-c (including the real committed one) --
# which is why these tests build `base` with `generated=` populated directly, the same
# shape `_fp()`'s default already has, rather than needing any special setup.
#
# A later final review found version.h shared this exact gap (its own section further
# down, near test_generated_cmake_version_h_mismatch_against_baseline_fails) and folded
# it into the same `generated_cmake` mechanism -- `_GENERATED_CMAKE_KEYS` now has eleven
# entries, not ten, but the "armed baseline" argument above is unchanged: version.h's
# hash has been in `base["generated"]` since before RC-c even existed.

def test_generated_cmake_absent_in_candidate_skips_cleanly():
    # Transition rule, half 1: a candidate captured by a pre-fix capture.py has no
    # "generated_cmake" key AT ALL (not even an empty dict) -- not yet armed -- so this
    # dimension must diff clean, exactly like an old candidate missing "header_defines"
    # against a header_defines-less baseline (test_header_defines.py). The DEFAULT _fp()
    # already omits "generated_cmake", so this is really just test_identical_is_ok's
    # scenario made explicit for this dimension.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"})
    assert "generated_cmake" not in cand
    assert diff_fingerprints(base, cand) == {"ok": True, "differences": []}


def test_generated_cmake_matching_baseline_is_ok():
    # CMake's copy hashes the same as configure's -- byte-identity holds -- must diff
    # clean once the candidate DOES carry the section.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"},
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc"})
    assert diff_fingerprints(base, cand)["ok"] is True


def test_generated_cmake_mismatch_against_baseline_fails():
    # THE fault-injection acceptance for this finding: the exact exploit the reviewer
    # proved end-to-end (a corrupted build-cmake/generated/ file) must now fail parity,
    # naming the file -- mirroring test_fault_injection_regressed_cflags_fail_parity's
    # role for the flags dimension.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "Version.incl": "hash-configure-version-incl"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "Version.incl": "hash-configure-version-incl"},
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc",
                                "Version.incl": "CORRUPTED-hash"})
    r = diff_fingerprints(base, cand)
    assert r["ok"] is False
    assert any("generated (cmake) file changed: Version.incl" in d for d in r["differences"])
    # The untouched file must NOT be named -- only the corrupted one.
    assert not any("scilab.pc" in d for d in r["differences"])


def test_generated_cmake_missing_entry_against_armed_baseline_fails():
    # Transition rule, half 2: once the candidate's section IS present, it is held to
    # EVERY RC-c file the (armed) baseline can attest to -- a file CMake silently failed
    # to write is a real regression, not a silent skip, exactly as jars/header_defines
    # do not tolerate a candidate quietly dropping an entry the baseline expects.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "Version.incl": "hash-configure-version-incl"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "Version.incl": "hash-configure-version-incl"},
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc"})   # Version.incl missing
    r = diff_fingerprints(base, cand)
    assert r["ok"] is False
    assert any("generated (cmake) file missing in candidate: Version.incl" in d
               for d in r["differences"])


def test_generated_cmake_unexpected_entry_is_caught():
    # A generated_cmake entry outside the eleven _GENERATED_CMAKE_KEYS (e.g. capture.py's
    # GENERATED_FILES loop finding something under
    # build-cmake/generated/modules/core/includes/ that should not be there -- machine.h
    # resolves through generated-includes/ instead, and stays excluded even there) is
    # drift worth surfacing, not silently accepted.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc"},
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc",
                                "modules/core/includes/machine.h": "unexpected"})
    r = diff_fingerprints(base, cand)
    assert r["ok"] is False
    assert any("generated (cmake) file extra in candidate: modules/core/includes/machine.h" in d
               for d in r["differences"])


def test_generated_cmake_ignores_non_rcc_generated_keys():
    # base["generated"] legitimately carries MORE than the eleven _GENERATED_CMAKE_KEYS --
    # etc/classpath.xml, machine.h, and the macro .bin manifest key -- none of which CMake
    # ever writes anywhere this dimension looks (classpath.xml has no CMake counterpart yet,
    # deferred to Stage 2; machine.h is header_defines' job, not byte-hashed here). A naive
    # presence-diff against the FULL "generated" section would spuriously report all of
    # those "missing in candidate" on every single comparison; the _GENERATED_CMAKE_KEYS
    # filter must keep this dimension silent about them. version.h is deliberately NOT one
    # of the keys tested here any more -- it IS a member of _GENERATED_CMAKE_KEYS now (see
    # test_generated_cmake_keys_match_capture_module_minus_the_two_without_a_cmake_copy) and
    # gets its own fault-injection coverage in
    # test_generated_cmake_version_h_mismatch_against_baseline_fails below.
    base = _fp(generated={
        "scilab.pc": "hash-configure-scilab-pc",
        "etc/classpath.xml": "hash-classpath",
        "modules/core/includes/machine.h": "hash-machine-h",
        "macros/*.bin (manifest)": "hash-macro-manifest",
    })
    cand = _fp(generated=dict(base["generated"]),
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc"})
    assert diff_fingerprints(base, cand) == {"ok": True, "differences": []}


def test_generated_cmake_version_h_mismatch_against_baseline_fails():
    # version.h's analogue of test_generated_cmake_mismatch_against_baseline_fails: the
    # exact exploit a later final review found and reproduced end-to-end against the real
    # tree (corrupting build-cmake/generated-includes/version.h's SCI_VERSION_MAJOR from
    # 2027 to 6666 still reported PARITY OK, because version.h was not in
    # _GENERATED_CMAKE_KEYS at all before this fix). Must now fail parity, naming version.h.
    base = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "modules/core/includes/version.h": "hash-configure-version-h"})
    cand = _fp(generated={"scilab.pc": "hash-configure-scilab-pc",
                          "modules/core/includes/version.h": "hash-configure-version-h"},
               generated_cmake={"scilab.pc": "hash-configure-scilab-pc",
                                "modules/core/includes/version.h": "CORRUPTED-hash"})
    r = diff_fingerprints(base, cand)
    assert r["ok"] is False
    assert any("generated (cmake) file changed: modules/core/includes/version.h" in d
               for d in r["differences"])
    # The untouched file must NOT be named -- only the corrupted one.
    assert not any("scilab.pc" in d for d in r["differences"])


def test_generated_cmake_keys_match_capture_module_minus_the_two_without_a_cmake_copy():
    # Pinned against parity.capture.GENERATED_FILES itself (not just a literal set), so
    # the two lists cannot silently drift apart -- diff.py deliberately duplicates this
    # list rather than importing it (this module stays decoupled from parity.capture,
    # comparing frozen JSON only), so nothing else enforces they stay in sync. version.h
    # used to be a third "no cmake copy" entry (generated-includes/, out of scope) until a
    # later final review closed that gap the same way as the RC-c ten -- it is now in
    # _GENERATED_CMAKE_KEYS, not in this set.
    from parity.capture import GENERATED_FILES
    no_cmake_copy = {
        "etc/classpath.xml",                    # deferred to Stage 2
        "modules/core/includes/machine.h",      # generated-includes/, header_defines' job
    }
    assert _GENERATED_CMAKE_KEYS == set(GENERATED_FILES) - no_cmake_copy
    assert len(_GENERATED_CMAKE_KEYS) == 11


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
