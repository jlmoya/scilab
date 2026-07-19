"""tu_flag_facts dimension of the parity harness's DIFF: the frozen per-TU
{"defaults", "overrides"} flag-fact baseline (RC-b; parity.capture.
capture_tu_flag_facts) compared the same way header_defines/jars are -- a
{name: {fact: value}} mapping, presence via _diff_named, then a per-shared-
name fact diff -- done once for "defaults" (keyed by language) and once for
"overrides" (keyed by TU relpath).

Exists because parity/flagfacts_check.py trusts this section as ground truth
but (before this file) nothing ever cross-checked the section ITSELF: a
tampered baseline, a baseline that drifted from the generated Makefiles it was
derived from, or a candidate that lost the section outright all diffed clean.
Closes final-review-of-retire-configure-RC-b finding I1. The synthetic tests
below mirror test_jar.py/test_header_defines.py's idiom on a minimal
fingerprint; the fault-injection tests at the bottom replay the review's own
verified exploits against the REAL committed baseline.
"""
import copy
import json
import os

from parity.diff import diff_fingerprints

HERE = os.path.dirname(__file__)
REAL_BASELINE = os.path.join(HERE, "..", "baseline-autotools.json")


def _fp(**over):
    """Minimal valid fingerprint; override any section via kwargs."""
    base = {"build_id": "t", "executables": {}, "dylibs": {}, "generated": {},
            "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
            "jars": {}, "header_defines": {},
            "tu_flag_facts": {"defaults": {}, "overrides": {}}}
    base.update(over)
    return base


_FACTS = {"opt": "O2", "wrapv": True, "min_macos": "11.0", "ndebug": True,
         "std": "gnu23", "openmp": False}


def _tu(**over):
    f = dict(_FACTS)
    f.update(over)
    return f


# --- synthetic, minimal-fingerprint tests (same idiom as test_jar.py / test_header_defines.py) --

def test_diff_detects_default_fact_changed():
    base = _fp(tu_flag_facts={"defaults": {"c": _tu()}, "overrides": {}})
    cand = _fp(tu_flag_facts={"defaults": {"c": _tu(opt="O0", wrapv=False)}, "overrides": {}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("flags default c: fact changed: opt" in d for d in r["differences"])
    assert any("flags default c: fact changed: wrapv" in d for d in r["differences"])


def test_diff_detects_override_fact_changed():
    base = _fp(tu_flag_facts={"defaults": {},
                              "overrides": {"modules/m/f.c": _tu(opt="O0", wrapv=False)}})
    cand = _fp(tu_flag_facts={"defaults": {}, "overrides": {"modules/m/f.c": _tu()}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("flags override modules/m/f.c: fact changed: opt" in d for d in r["differences"])
    assert any("flags override modules/m/f.c: fact changed: wrapv" in d for d in r["differences"])


def test_diff_detects_added_and_removed_default():
    base = _fp(tu_flag_facts={"defaults": {"c": _tu()}, "overrides": {}})
    cand = _fp(tu_flag_facts={"defaults": {"cxx": _tu()}, "overrides": {}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("flags default missing in candidate: c" in d for d in diffs)
    assert any("flags default extra in candidate: cxx" in d for d in diffs)


def test_diff_detects_added_and_removed_override():
    base = _fp(tu_flag_facts={"defaults": {}, "overrides": {"modules/m/a.c": _tu()}})
    cand = _fp(tu_flag_facts={"defaults": {}, "overrides": {"modules/m/b.c": _tu()}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("flags override missing in candidate: modules/m/a.c" in d for d in diffs)
    assert any("flags override extra in candidate: modules/m/b.c" in d for d in diffs)


def test_diff_identical_tu_flag_facts_ok():
    tu = {"defaults": {"c": _tu()}, "overrides": {"modules/m/f.c": _tu(opt="O0")}}
    assert diff_fingerprints(_fp(tu_flag_facts=tu), _fp(tu_flag_facts=copy.deepcopy(tu)))["ok"]


def test_diff_baseline_without_tu_flag_facts_skips():
    # Transition rule (mirrors rpaths/jars/header_defines): a baseline with no
    # "tu_flag_facts" section predates RC-b -- skip, not a failure.
    base = _fp()
    del base["tu_flag_facts"]
    cand = _fp(tu_flag_facts={"defaults": {"c": _tu()}, "overrides": {}})
    assert diff_fingerprints(base, cand)["ok"]


def test_diff_both_missing_tu_flag_facts_is_ok():
    # Two pre-RC-b fingerprints (no "tu_flag_facts" key at all) still diff
    # cleanly -- the comparison must not KeyError on the old schema.
    base, cand = _fp(), _fp()
    del base["tu_flag_facts"], cand["tu_flag_facts"]
    assert diff_fingerprints(base, cand) == {"ok": True, "differences": []}


def test_diff_candidate_missing_tu_flag_facts_against_armed_baseline_fails():
    # The reverse is NOT tolerated: a candidate that LOST the section against an
    # armed baseline must fail, not silently skip (I1's second verified exploit).
    base = _fp(tu_flag_facts={"defaults": {"c": _tu()}, "overrides": {}})
    cand = _fp()
    del cand["tu_flag_facts"]
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert "tu_flag_facts section missing in candidate" in r["differences"]


def test_diff_empty_tu_flag_facts_against_armed_baseline_fails():
    # The non-adversarial variant: the SECTION is present but empty (e.g. a
    # capture taken where ./configure never ran, so capture_tu_flag_facts finds
    # no modules/*/Makefile and returns {"defaults": {}, "overrides": {}}) --
    # distinct from the section being absent, and must fail the same way.
    base = _fp(tu_flag_facts={"defaults": {"c": _tu()},
                              "overrides": {"modules/m/f.c": _tu(opt="O0")}})
    cand = _fp(tu_flag_facts={"defaults": {}, "overrides": {}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("flags default missing in candidate: c" in d for d in r["differences"])
    assert any("flags override missing in candidate: modules/m/f.c" in d for d in r["differences"])


# --- fault-injection on the REAL committed baseline: I1's exact verified exploits --

def _real_baseline():
    with open(REAL_BASELINE) as f:
        return json.load(f)


def test_fault_injection_tu_flag_facts_only_diff_is_caught():
    # I1 exploit 1, reproduced on the real baseline: a candidate differing from
    # the committed baseline ONLY in tu_flag_facts must be reported, not "ok:
    # True, no differences" -- the exact REPRODUCE-not-improve blind spot the
    # final review found (a hand-edited footgun override sailing through
    # unnoticed, e.g. the sci_browsehistory.c/getScilabPreference.c/
    # getScilabVariable_wrap.c scenario in the review).
    base = _real_baseline()
    cand = copy.deepcopy(base)
    victim = "modules/parameters/src/c/parameters.c"
    assert victim in cand["tu_flag_facts"]["overrides"], "known footgun override must exist"
    cand["tu_flag_facts"]["overrides"][victim]["opt"] = "O2"
    cand["tu_flag_facts"]["overrides"][victim]["wrapv"] = True
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any(victim in d and "opt" in d for d in r["differences"])
    assert any(victim in d and "wrapv" in d for d in r["differences"])


def test_fault_injection_tu_flag_facts_lost_entirely_is_caught():
    # I1 exploit 2: a candidate that lost the whole section against the real,
    # armed baseline must fail parity.
    base = _real_baseline()
    cand = copy.deepcopy(base)
    del cand["tu_flag_facts"]
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert "tu_flag_facts section missing in candidate" in r["differences"]


def test_fault_injection_never_configured_tree_against_real_baseline_is_caught():
    # I1's second, non-adversarial variant: on a tree where ./configure was
    # never run, capture_tu_flag_facts records an EMPTY section (present, not
    # missing) -- must still fail against the real, armed baseline, and loudly
    # (every one of the ~214 defaults+overrides entries reported missing), not
    # as a single easy-to-miss line.
    base = _real_baseline()
    cand = copy.deepcopy(base)
    cand["tu_flag_facts"] = {"defaults": {}, "overrides": {}}
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert len(r["differences"]) > 200
