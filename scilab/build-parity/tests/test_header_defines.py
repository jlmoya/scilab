"""Semantic header parity: a generated C header is compared by its {macro: value}
#define SET, never byte-for-byte — autoconf and CMake spell the same configuration
differently (comment style, `#define X 1` vs `/* #undef X */`, ordering), exactly
like they spell compiler flags differently. machine.h carries no volatile fields,
so no normalization is needed."""
from parity.fingerprint import parse_defines
from parity.diff import diff_fingerprints


def _fp(**over):
    base = {"build_id": "t", "executables": {}, "dylibs": {}, "generated": {},
            "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
            "jars": {}, "header_defines": {}}
    base.update(over)
    return base


def test_parse_defines_value_and_bare():
    h = "#define HAVE_ATEXIT 1\n#define STDC_HEADERS\n"
    assert parse_defines(h) == {"HAVE_ATEXIT": "1", "STDC_HEADERS": ""}


def test_parse_defines_ignores_undef_and_comments():
    h = "/* #undef HAVE_MPI */\n#undef HAVE_TK\n/* a comment */\n#define HAVE_DLFCN_H 1\n"
    assert parse_defines(h) == {"HAVE_DLFCN_H": "1"}


def test_parse_defines_function_like_macro_keeps_body():
    # C2F/F2C/CNAME are function-like; key is the bare identifier, value the rest.
    h = "#define C2F(name) name##_\n"
    assert parse_defines(h) == {"C2F": "(name) name##_"}


def test_parse_defines_tolerates_indentation_and_spacing():
    h = "  #  define  SIZEOF_INT   4  \n"
    assert parse_defines(h) == {"SIZEOF_INT": "4"}


def test_diff_detects_changed_macro():
    base = _fp(header_defines={"machine.h": {"HAVE_X": "1", "SIZEOF_INT": "4"}})
    cand = _fp(header_defines={"machine.h": {"HAVE_X": "0", "SIZEOF_INT": "4"}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("machine.h: macro changed: HAVE_X" in d for d in r["differences"])


def test_diff_detects_added_and_removed_macro():
    base = _fp(header_defines={"machine.h": {"HAVE_A": "1"}})
    cand = _fp(header_defines={"machine.h": {"HAVE_B": "1"}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("machine.h: macro removed: HAVE_A" in d for d in diffs)
    assert any("machine.h: macro added: HAVE_B" in d for d in diffs)


def test_diff_baseline_without_header_defines_skips():
    base = _fp()
    del base["header_defines"]                     # pre-RC-a baseline (transition)
    assert diff_fingerprints(base, _fp(header_defines={"machine.h": {"A": "1"}}))["ok"]


def test_diff_candidate_missing_header_defines_against_armed_baseline_fails():
    base = _fp(header_defines={"machine.h": {"A": "1"}})
    cand = _fp()
    del cand["header_defines"]
    assert not diff_fingerprints(base, cand)["ok"]


def test_diff_identical_header_defines_ok():
    h = {"machine.h": {"HAVE_X": "1", "STDC_HEADERS": ""}}
    assert diff_fingerprints(_fp(header_defines=h), _fp(header_defines=dict(h)))["ok"]
