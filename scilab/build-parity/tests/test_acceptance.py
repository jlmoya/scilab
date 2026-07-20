"""Acceptance: the harness must be neither too loose nor too tight, on REAL captured data.

Stability  -> capturing the same tree twice is identical (proves the capture pipeline is
              deterministic on an unchanged tree -> no false positives from stray ordering,
              PIDs, timestamps). Address-VALUE independence -- that a differently-linked build's
              shifted symbol addresses don't trip parity -- is proven structurally by
              test_parse_nm_strips_addresses_and_sorts (Task 1), which discards the address column
              unconditionally; the two together substantiate "not too loose."
Sensitivity -> a mutated real fingerprint is caught (proves no false negatives).
Alignment   -> the ONE cross-toolchain check in this file (all the others compare a fingerprint
               against itself or a baseline): every Maven jar must match its Ant counterpart, on
               the SAME real capture. This is what arms the maven_jars dimension (Stage 2-c Task 2)
               -- diff.py's transition rule only ever catches maven_jars regressing against itself
               across runs, never disagreeing with jars within one run.
"""
import copy
import glob
import json
import os
import xml.etree.ElementTree as ET

import pytest

from parity.capture import fingerprint_build, _default_roots
from parity.diff import diff_fingerprints

HERE = os.path.dirname(__file__)
BUILD_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))   # the scilab/ built tree
BASELINE = os.path.join(HERE, "..", "baseline-autotools.json")
PARENT_POM = os.path.join(BUILD_DIR, "pom.xml")                # the Maven reactor root

# CRITICAL (final review): this file used to carry ONE module-level `pytestmark`,
# keyed on `.libs/scilab-bin`, applied to EVERY test below -- including all four
# maven_jars tests. Two of those (the parse test and the two synthetic regression
# tests) touch no build output at all, and a third (test_maven_jars_align_with_ant_jars)
# needs only Ant's jars, never the native executable. `.libs/` is untracked build
# output, so a fresh clone, a `make clean`ed tree, a Java-only CI job, or a git
# worktree (this project's own documented isolation mode) all skipped the ENTIRE
# maven_jars gate silently -- "191 passed, 11 skipped" names no dimension, so
# nothing said the gate never ran. Each guard below is now scoped to what the test
# underneath it ACTUALLY needs, not to what the busiest test in the file needs.
_HAS_BUILT_TREE = os.path.exists(os.path.join(BUILD_DIR, ".libs", "scilab-bin"))
_requires_built_tree = pytest.mark.skipif(
    not _HAS_BUILT_TREE, reason="requires the built autotools tree")

# test_maven_jars_align_with_ant_jars' OWN requirement: at least one Ant-built
# module jar to compare against -- NOT the native .libs/scilab-bin executable,
# which that test never touches. A cheap filesystem glob (not a call to
# _capture(), which would pay for a whole-tree walk just to learn there is
# nothing to align).
_ANY_ANT_JAR = glob.glob(os.path.join(BUILD_DIR, "modules", "*", "jar", "*.jar"))
_requires_ant_jars = pytest.mark.skipif(
    not _ANY_ANT_JAR, reason="requires at least one Ant-built module jar")


def _capture():
    return fingerprint_build(BUILD_DIR, _default_roots(BUILD_DIR), build_id="candidate")


@_requires_built_tree
def test_stability_recapture_is_green():
    # No false positives: the same tree captured twice must be identical.
    a = _capture()
    b = _capture()
    assert diff_fingerprints(a, b) == {"ok": True, "differences": []}


@_requires_built_tree
def test_committed_baseline_matches_current_tree():
    with open(BASELINE) as f:
        base = json.load(f)
    assert diff_fingerprints(base, _capture())["ok"] is True


@_requires_built_tree
def test_sensitivity_dropped_symbol_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    victim = sorted(mutated["dylibs"])[0]
    assert mutated["dylibs"][victim]["symbols"], "victim dylib has no symbols to drop"
    mutated["dylibs"][victim]["symbols"].pop()          # drop one exported symbol
    assert diff_fingerprints(base, mutated)["ok"] is False


@_requires_built_tree
def test_sensitivity_sdk_downgrade_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    mutated["executables"]["scilab-bin"]["build_version"]["sdk"] = "26.0"  # the anti-SIGTRAP regression
    r = diff_fingerprints(base, mutated)
    assert r["ok"] is False
    assert any("sdk" in d.lower() for d in r["differences"])


@_requires_built_tree
def test_sensitivity_tmp_leak_is_caught():
    base = _capture()
    mutated = copy.deepcopy(base)
    victim = sorted(mutated["dylibs"])[0]
    mutated["dylibs"][victim]["tmp_leak"] = True         # a reboot time-bomb sneaks in
    assert diff_fingerprints(base, mutated)["ok"] is False


@_requires_built_tree
def test_sensitivity_dropped_rpath_is_caught():
    # Stage 1f: LC_RPATH is load-bearing for @rpath resolution (the jvm/JDK
    # modules resolve libjvm through it). Dropping one moves no symbol, link
    # edge, or SDK stamp -- only the rpath gate can catch it. Fault-injected on
    # a REAL captured fingerprint, like the other sensitivity tests.
    base = _capture()
    mutated = copy.deepcopy(base)
    victims = [n for n in sorted(mutated["dylibs"]) if mutated["dylibs"][n]["rpaths"]]
    assert victims, "real tree must have at least one rpath-bearing dylib"
    mutated["dylibs"][victims[0]]["rpaths"].pop()        # drop one LC_RPATH
    r = diff_fingerprints(base, mutated)
    assert r["ok"] is False
    assert any("rpaths" in d for d in r["differences"])


@_requires_built_tree
def test_sensitivity_wrapv_drop_is_caught():
    # THE codegen blind spot the flag manifest closes: -fwrapv drops out of the C
    # flags and NOTHING about symbols/link/stamp moves -- the exact class that sat
    # green for days (fixed in 516c57573cc). Must now fail parity, naming wrapv.
    base = _capture()
    assert base["flags"]["source"] == "autotools"
    assert base["flags"]["c"], "real tree must yield C flag facts"
    mutated = copy.deepcopy(base)
    mutated["flags"]["c"]["wrapv"] = False
    r = diff_fingerprints(base, mutated)
    assert r["ok"] is False
    assert any("flags c" in d and "wrapv" in d for d in r["differences"])


_MVN_NS = "{http://maven.apache.org/POM/4.0.0}"


def _reactor_modules(pom_path=PARENT_POM):
    """<module> entries from the parent reactor POM (e.g. "modules/localization"),
    PARSED rather than hardcoded so the completeness check below (review Fix 1)
    stays correct through all 3 remaining migrations with no further edits.

    NAMESPACE GOTCHA: the POM declares the default Maven namespace
    (xmlns="http://maven.apache.org/POM/4.0.0"), so ElementTree needs the
    "{http://maven.apache.org/POM/4.0.0}" prefix on EVERY tag in the path --
    root.findall("modules/module") against a namespaced document silently
    returns [] (no exception, just nothing), which would make the completeness
    check below assert nothing against everything. See
    test_reactor_modules_parses_real_pom_non_vacuously: verified to return
    exactly 20 entries today (modules/localization, modules/commons,
    modules/history_manager, modules/jvm, modules/action_binding,
    modules/scirenderer, modules/graphic_objects, modules/completion,
    modules/console, modules/helptools, modules/types,
    modules/external_objects_java, modules/renderer, modules/javasci,
    modules/graphic_export, modules/gui, modules/core,
    modules/history_browser, modules/graph, modules/ui_data), not a
    silently-empty list.

    PROFILE-SCOPED MODULES GOTCHA (final review, Minor): this only matches a
    <modules> block that is a DIRECT CHILD of <project> --
    root.findall(f"{_MVN_NS}modules/{_MVN_NS}module"). A module declared
    instead under <profiles><profile><modules>...</modules></profile>
    </profiles> -- a plausible next move for an opt-in reactor -- is
    invisible to this parse, and therefore to the completeness check below:
    it is never counted as expected, so it can never be reported missing
    either. Not a live gap today (the real POM has no <profiles> block), but
    widen this deliberately if one is ever added -- don't assume profile-
    gated modules are automatically covered.
    """
    root = ET.parse(pom_path).getroot()
    return [el.text.strip() for el in root.findall(f"{_MVN_NS}modules/{_MVN_NS}module")]


def _missing_reactor_jars(mj, modules):
    """Reactor `modules` (from _reactor_modules) with no key in `mj` under
    their Ant-equivalent "<module>/jar/" prefix -- i.e. Maven ran at all (mj is
    non-empty) but THIS module produced nothing: `mvn clean`, a <skip> added to
    maven-jar-plugin, a module dropped from <modules>, or a mid-reactor
    packaging failure would all show up here. A pure function of its arguments
    (no pytest.skip, no filesystem/tree access) so it is directly unit-testable
    against synthetic input -- see the regression tests below.

    GRANULARITY LIMIT, kept on purpose (final review, Minor): this asks only
    whether a module produced >=1 jar under its prefix, not how many -- a
    module whose Ant build emits two jars but whose Maven build emits only
    one would still pass. No live instance today: all 24 Ant-built modules
    emit exactly one jar apiece."""
    return sorted(m for m in modules if not any(k.startswith(m + "/jar/") for k in mj))


def _check_maven_jars_alignment_and_completeness(cand):
    """The maven_jars gate body (Stage 2-c Task 2), factored out of the pytest
    test itself so the completeness half (Fix 1) is unit-testable against
    synthetic `cand` dicts, without needing a real built tree.

    Every Maven-built jar must have an Ant counterpart at the same key, with
    identical content (the ORIGINAL check) -- AND, once Maven has run at all,
    it must have produced a jar for EVERY module the parent reactor declares
    (Fix 1): the original check alone is one-directional (set(mj) - set(j)),
    so a build that produced FEWER jars than it should -- only `localization`,
    say, with `commons` silently missing -- used to pass it, because a module
    that produced NOTHING leaves no orphan key to report. `mvn clean`, a
    <skip> added to maven-jar-plugin, a module dropped from <modules>, or a
    packaging failure in an otherwise-building tree all used to read as
    success.
    """
    mj = cand.get("maven_jars", {})
    if not mj:
        pytest.skip("no Maven-built jars in this tree -- nothing to align")
    j = cand["jars"]

    orphans = sorted(set(mj) - set(j))
    assert not orphans, (
        f"Maven jars with no Ant counterpart: {orphans} -- could be a naming "
        "divergence (Maven's finalName vs. Ant's jar name), a stale target/ "
        "jar left over from a pre-rename build (try `mvn clean`), or a "
        "genuinely missing Ant jar for this module."
    )
    differing = sorted(k for k in mj if mj[k] != j[k])
    assert not differing, f"Maven and Ant jars differ in content at: {differing}"

    # Completeness (review Fix 1). LIMITATION KEPT ON PURPOSE: the all-empty
    # case above still SKIPS rather than failing -- "Maven never ran at all"
    # stays invisible here. That is correct for as long as Maven is run by
    # hand and additively (a developer who has never typed `mvn` must not get
    # a red suite) -- it must become a hard FAILURE once Maven is wired into
    # CI (a later Stage 2 task), at which point both "never ran" and "ran but
    # incomplete" need to fail.
    modules = _reactor_modules()
    assert modules, (
        f"parsed 0 <module> entries from the parent POM ({PARENT_POM}) -- the "
        "namespace-aware parse is broken, or the POM lost its <modules> "
        "block. A vacuous module list would make the completeness check "
        "below assert nothing against everything -- fix the parse, don't let "
        "this pass silently."
    )
    missing = _missing_reactor_jars(mj, modules)
    assert not missing, f"reactor modules with no Maven jar at all: {missing}"


@_requires_ant_jars
def test_maven_jars_align_with_ant_jars():
    """Every Maven-built jar must have an Ant counterpart at the same key, with
    identical content, AND Maven must have produced a jar for every module the
    parent reactor declares (Fix 1) -- not merely for whichever ones it
    happened to build.

    This is what makes the maven_jars dimension a GATE rather than a recorded
    observation: diff.py's transition rule only detects regression across
    runs, never disagreement between the two toolchains, and never a module
    Maven dropped entirely. Skips when no Maven jars are present (the
    pytest.skip inside _check_maven_jars_alignment_and_completeness) so a
    pure-autotools tree is unaffected; fires the moment anyone runs
    `mvn package`.

    GUARD, DELIBERATELY NARROWER THAN THIS FILE'S OTHER TESTS (final review,
    Critical): decorated with @_requires_ant_jars, not @_requires_built_tree.
    This test needs Ant's jars to compare against -- never the native
    .libs/scilab-bin executable every other test in this file requires -- so
    it must run (and can fail) in a tree with no built native binaries at
    all: a Java-only CI job, a git worktree, or a fresh clone that has only
    run the Ant/Maven jar builds.
    """
    _check_maven_jars_alignment_and_completeness(_capture())


def test_reactor_modules_parses_real_pom_non_vacuously():
    # Guards the namespace gotcha itself: against the REAL parent POM, the
    # parse must return the twenty modules wired up today, not an empty list
    # -- an empty list would silently make the completeness check above assert
    # nothing against everything (vacuously "passing").
    #
    # MAINTENANCE NOTE (final review, Minor): this exact-list assertion is
    # meant to be UPDATED every time a module is added to the parent POM (3
    # more times before the migration is done) -- never weakened to a
    # truthiness check (`assert _reactor_modules()`) to dodge that churn.
    # Truthiness alone is already covered by the `assert modules` inside
    # _check_maven_jars_alignment_and_completeness; this test's whole point
    # is pinning the EXACT set, so a module that silently fails to parse (or
    # parses to the wrong set) still gets caught here. Updated for Stage 2-f
    # Task 4, Wave D (modules/core, modules/history_browser, modules/graph,
    # modules/ui_data added to the parent's <modules> -- all four depended on
    # gui, migrated the wave before; core and history_browser also needed
    # action_binding/commons/graphic_objects/jvm/localization/history_manager,
    # graph and ui_data also needed commons/jvm-or-types/localization -- every
    # one of those reactor edges was already migrated by Wave A, Wave B, Wave
    # C, or earlier; none of the four depends on any of the others despite
    # two textual near-misses ruled out by reading the source: graph's only
    # org.scilab.modules.xcos mention is a javadoc @see tag, and ui_data's
    # only org.scilab.modules.graph mention is also a javadoc @see tag on an
    # unrelated same-named DefaultAction class).
    assert _reactor_modules() == [
        "modules/localization",
        "modules/commons",
        "modules/history_manager",
        "modules/jvm",
        "modules/action_binding",
        "modules/scirenderer",
        "modules/graphic_objects",
        "modules/completion",
        "modules/console",
        "modules/helptools",
        "modules/types",
        "modules/external_objects_java",
        "modules/renderer",
        "modules/javasci",
        "modules/graphic_export",
        "modules/gui",
        "modules/core",
        "modules/history_browser",
        "modules/graph",
        "modules/ui_data",
    ]


def test_regression_reactor_module_missing_maven_jar_fails():
    # Fix 1 (a): `commons` is a declared reactor module but produced no Maven
    # jar at all -- a PARTIAL build. The original one-directional orphan check
    # (set(mj) - set(j)) cannot see this: with only `localization` present on
    # both sides it is trivially satisfied. Must now fail, naming the module.
    modules = ["modules/localization", "modules/commons"]
    mj = {"modules/localization/jar/org.scilab.modules.localization.jar": {"A.class": "h1"}}
    assert _missing_reactor_jars(mj, modules) == ["modules/commons"]

    cand = {
        "jars": {"modules/localization/jar/org.scilab.modules.localization.jar": {"A.class": "h1"}},
        "maven_jars": mj,
    }
    with pytest.raises(AssertionError, match="modules/commons"):
        _check_maven_jars_alignment_and_completeness(cand)


def test_regression_all_empty_maven_jars_still_skips():
    # Fix 1 (b): maven_jars completely empty must still SKIP, not fail the new
    # completeness check -- a developer who has never run `mvn` must not get a
    # red suite. Confirms the completeness check did not move ahead of the
    # pre-existing empty-skip.
    with pytest.raises(pytest.skip.Exception):
        _check_maven_jars_alignment_and_completeness({"jars": {}, "maven_jars": {}})


def test_committed_baseline_carries_no_maven_jars_section():
    """The committed baseline must NOT contain a `maven_jars` section.

    `baseline-autotools.json` is, by name and purpose, a snapshot of the AUTOTOOLS/Ant
    build. Maven's output is not part of that build, so it does not belong in there --
    and `diff.py`'s transition rule is written to SKIP `maven_jars` when the baseline
    lacks it, which is the designed behaviour, not an oversight.

    This test exists because the section was once added by accident: a re-baseline run
    (commit 65baec3b4c8, fixing an unrelated manifest-normalization change) used
    `capture.sh`, which captures every section including `maven_jars`, and froze a
    3-module snapshot into the file. The verification script used at the time compared
    only keys within sections present in BOTH files, so an entirely NEW section was
    invisible to it and the change was reported as "0 keys added".

    The consequence is worse than untidiness. With `maven_jars` frozen in the baseline,
    EVERY subsequent module migration makes the baseline stale and turns
    `test_committed_baseline_matches_current_tree` red -- so the fix would become a
    routine re-baseline on every migration, and a gate that is routinely re-baselined
    has stopped being a gate.

    The real arming for this dimension is `test_maven_jars_align_with_ant_jars`, which
    compares `maven_jars` against `jars` WITHIN a single capture and therefore needs no
    frozen reference at all.

    If you are here because `capture.sh` re-added the section: strip it, do not re-point
    this test.
    """
    with open(BASELINE) as f:
        baseline = json.load(f)
    assert "maven_jars" not in baseline, (
        "baseline-autotools.json has grown a maven_jars section -- most likely a "
        "capture.sh run wrote it. Strip the section; see this test's docstring for why "
        "a frozen Maven snapshot turns every future migration into a re-baseline."
    )
