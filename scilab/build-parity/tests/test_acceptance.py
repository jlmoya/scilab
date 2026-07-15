"""Acceptance: the harness must be neither too loose nor too tight, on REAL captured data.

Stability  -> capturing the same tree twice is identical (proves the capture pipeline is
              deterministic on an unchanged tree -> no false positives from stray ordering,
              PIDs, timestamps). Address-VALUE independence -- that a differently-linked build's
              shifted symbol addresses don't trip parity -- is proven structurally by
              test_parse_nm_strips_addresses_and_sorts (Task 1), which discards the address column
              unconditionally; the two together substantiate "not too loose."
Sensitivity -> a mutated real fingerprint is caught (proves no false negatives).
"""
import copy
import json
import os

import pytest

from parity.capture import fingerprint_build, _default_roots
from parity.diff import diff_fingerprints

HERE = os.path.dirname(__file__)
BUILD_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))   # the scilab/ built tree
BASELINE = os.path.join(HERE, "..", "baseline-autotools.json")

pytestmark = pytest.mark.skipif(
    not os.path.exists(os.path.join(BUILD_DIR, ".libs", "scilab-bin")),
    reason="requires the built autotools tree",
)


def _capture():
    return fingerprint_build(BUILD_DIR, _default_roots(BUILD_DIR), build_id="candidate")


def test_stability_recapture_is_green():
    # No false positives: the same tree captured twice must be identical.
    a = _capture()
    b = _capture()
    assert diff_fingerprints(a, b) == {"ok": True, "differences": []}


def test_committed_baseline_matches_current_tree():
    with open(BASELINE) as f:
        base = json.load(f)
    assert diff_fingerprints(base, _capture())["ok"] is True


def test_sensitivity_dropped_symbol_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    victim = sorted(mutated["dylibs"])[0]
    assert mutated["dylibs"][victim]["symbols"], "victim dylib has no symbols to drop"
    mutated["dylibs"][victim]["symbols"].pop()          # drop one exported symbol
    assert diff_fingerprints(base, mutated)["ok"] is False


def test_sensitivity_sdk_downgrade_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    mutated["executables"]["scilab-bin"]["build_version"]["sdk"] = "26.0"  # the anti-SIGTRAP regression
    r = diff_fingerprints(base, mutated)
    assert r["ok"] is False
    assert any("sdk" in d.lower() for d in r["differences"])


def test_sensitivity_tmp_leak_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    victim = sorted(mutated["dylibs"])[0]
    mutated["dylibs"][victim]["tmp_leak"] = True         # a reboot time-bomb sneaks in
    assert diff_fingerprints(base, mutated)["ok"] is False
