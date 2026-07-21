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
    PARSED rather than hardcoded so the completeness check below (review Fix 1,
    and Fix 2) stays correct with no further edits now that Wave F has brought
    the reactor to all 24 modules -- 0 remaining. Stage 2-f's own Waves A-E
    (Task 5) had declared it complete at 23/23; Wave F is the final-review
    CRITICAL correction that added the 24th, `terminal` -- a genuine shipping
    module (etc/classpath.xml:286) the 23-module count had silently dropped.

    NAMESPACE GOTCHA: the POM declares the default Maven namespace
    (xmlns="http://maven.apache.org/POM/4.0.0"), so ElementTree needs the
    "{http://maven.apache.org/POM/4.0.0}" prefix on EVERY tag in the path --
    root.findall("modules/module") against a namespaced document silently
    returns [] (no exception, just nothing), which would make the completeness
    check below assert nothing against everything. See
    test_reactor_modules_parses_real_pom_non_vacuously: verified to return
    exactly 24 entries today -- ALL 24 modules of the reactor, Wave F
    complete (modules/localization, modules/commons,
    modules/history_manager, modules/jvm, modules/action_binding,
    modules/scirenderer, modules/graphic_objects, modules/completion,
    modules/console, modules/helptools, modules/types,
    modules/external_objects_java, modules/renderer, modules/javasci,
    modules/graphic_export, modules/gui, modules/core,
    modules/history_browser, modules/graph, modules/ui_data,
    modules/scinotes, modules/preferences, modules/xcos,
    modules/terminal), not a silently-empty list.

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


def _ant_modules_without_reactor_entry(j, modules):
    """The completeness gate's MISSING direction (final review CRITICAL, Wave F):
    module names with >=1 Ant-built jar under "modules/<name>/jar/" in the `jars`
    dimension, but no "modules/<name>" entry in the reactor's <modules> list at
    all. `_missing_reactor_jars` above is the OTHER direction -- every declared
    reactor module must have produced a jar -- and it is blind to this one BY
    CONSTRUCTION: it only ever iterates the reactor's OWN `modules` argument, so a
    module that was simply never added to <modules> is not reported missing, it
    is never asked about in the first place. `terminal` was exactly this: a
    real, Ant-built, SHIPPING jar (`etc/classpath.xml:286` loads it into the
    running app) with no reactor module at all -- 23 green reactor modules, one
    invisible orphan the old one-directional check could not name. A pure
    function of its arguments (no pytest.skip, no filesystem/tree access),
    unit-testable against synthetic input -- see the regression tests below.
    """
    jar_modules = {k.split("/")[1] for k in j if k.startswith("modules/")}
    reactor_modules = {m.split("/")[1] for m in modules}
    return sorted(jar_modules - reactor_modules)


def _check_maven_jars_alignment_and_completeness(cand):
    """The maven_jars gate body (Stage 2-c Task 2), factored out of the pytest
    test itself so the completeness half (Fix 1, and Fix 2 below) is
    unit-testable against synthetic `cand` dicts, without needing a real built
    tree.

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

    Fix 2 (final review CRITICAL, Wave F): completeness ALSO requires every
    module with a real Ant-built jar in `jars` to have a reactor <module> entry
    at all -- not only that every declared reactor module produced a jar (Fix
    1). Fix 1 alone still ranges only over the reactor's OWN declared modules,
    so a SHIPPING module the reactor never picked up -- `terminal`, a full Java
    module `etc/classpath.xml:286` loads into the running app -- was invisible
    to both the original check and Fix 1: 23/23 reactor modules green, and the
    24th jar never even asked about. Fix 2 closes that: `jars`' module set must
    be a SUBSET of the reactor's module set.
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

    # Completeness, THE OTHER DIRECTION (Fix 2, final review CRITICAL, Wave F):
    # every module with a real Ant-built jar in `jars` must have a reactor
    # <module> entry -- not only must every DECLARED reactor module have
    # produced a jar (the assert immediately above, Fix 1). Additive
    # strengthening of the SAME gate, not a new one: fingerprint_jar,
    # normalize_manifest, diff.py and capture.py are untouched. Skips exactly
    # when the asserts above would -- this sits downstream of the SAME
    # `if not mj` guard at the top of this function, so a tree where nobody
    # has run Maven at all still skips cleanly rather than failing (this
    # stage's binding constraint; see also
    # test_regression_all_empty_maven_jars_still_skips).
    orphans = _ant_modules_without_reactor_entry(j, modules)
    assert not orphans, (
        f"modules with an Ant-built jar in 'jars' but no reactor <module> entry: "
        f"{orphans} -- a shipping module jar with nobody building it under Maven; "
        "add modules/<name> to the parent POM's <modules> (scilab/pom.xml)."
    )


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
    # parse must return the twenty-four modules wired up today, not an empty
    # list -- an empty list would silently make the completeness check above
    # assert nothing against everything (vacuously "passing").
    #
    # MAINTENANCE NOTE (final review, Minor): this exact-list assertion is
    # meant to be UPDATED every time a module is added to the parent POM --
    # never weakened to a truthiness check (`assert _reactor_modules()`) to
    # dodge that churn. Truthiness alone is already covered by the `assert
    # modules` inside _check_maven_jars_alignment_and_completeness; this
    # test's whole point is pinning the EXACT set, so a module that silently
    # fails to parse (or parses to the wrong set) still gets caught here.
    # Updated for Stage 2-f Task 5, Wave E (modules/scinotes,
    # modules/preferences, modules/xcos added to the parent's <modules>,
    # bringing the reactor to 23/23 -- believed final at the time). scinotes
    # depended on core/gui/helptools (Waves D/C/A) plus the usual
    # action_binding/commons/completion/console/history_manager/jvm/
    # localization boilerplate (all pre-existing or Wave A); preferences
    # depended on gui (Wave C) AND on scinotes itself, which is why scinotes
    # is listed first of the three -- Maven's reactor build order is derived
    # from the dependency graph regardless of <modules> list order (see the
    # Reactor Build Order preamble the Stage 2-f Task 5 report quotes in
    # full), but the listing order still follows the established convention
    # of every earlier wave; xcos depended on core/graph/gui/helptools/
    # javasci/types (Waves D/D/C/A/B/A) plus the same boilerplate. None of
    # the three depends on the other two in a way that would cycle: xcos
    # does not import scinotes or preferences at all, and preferences's only
    # scinotes dependency is direct instantiation/method calls, never a
    # reactor cycle back onto preferences or xcos.
    #
    # UPDATED AGAIN for Wave F (final review CRITICAL): `modules/terminal`
    # appended as the 24th entry -- 23/23 was wrong, not final; `terminal`
    # ships (etc/classpath.xml:286) but sits outside prebuildjava's topo-sort
    # entirely (GUI-gated, its own USEANT=1 path), so none of Waves A-E ever
    # had a reason to consider it. Listed last, matching the established
    # convention of appending each wave in the order it landed -- its four
    # reactor dependencies (action_binding, commons, gui, localization) are
    # all earlier in this same list, so the dependency graph (not list order)
    # places it correctly regardless. The counter bottoms out here: 0
    # remaining, reactor complete at 24/24.
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
        "modules/scinotes",
        "modules/preferences",
        "modules/xcos",
        "modules/terminal",
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


def test_regression_ant_module_missing_reactor_entry_fails():
    # Fix 2 (final review CRITICAL, Wave F): `terminal` had a real Ant-built jar
    # in `jars` -- a genuine shipping module, etc/classpath.xml:286 loads it into
    # the running app -- but (before Wave F) no reactor <module> entry at all.
    # Both the ORIGINAL one-directional check (mj-vs-j) and Fix 1's
    # _missing_reactor_jars are blind to this shape: neither one ever iterates a
    # module that was never added to <modules> in the first place, so it is not
    # reported missing -- it is never asked about. The pure function, against a
    # wholly synthetic two-module reactor, must name the offender:
    modules = ["modules/localization"]
    j = {
        "modules/localization/jar/org.scilab.modules.localization.jar": {"A.class": "h1"},
        "modules/terminal/jar/org.scilab.modules.terminal.jar": {"T.class": "h9"},
    }
    assert _ant_modules_without_reactor_entry(j, modules) == ["terminal"]

    # And the SAME shape must fail through the FULL check too --
    # _check_maven_jars_alignment_and_completeness itself parses the real parent
    # POM internally (_reactor_modules() takes no injectable argument), so a
    # purely local `modules` list like the one above never reaches it. Built
    # against the REAL reactor module list instead (not hardcoded), so this
    # stays a clean Fix-2-only probe no matter how many modules the reactor
    # holds by the time this runs: one synthetic jar per REAL reactor module
    # satisfies Fix 1 trivially, plus one extra `jars`-only entry naming a
    # module that can never be a real one.
    real_names = [m.split("/")[1] for m in _reactor_modules()]
    mj = {f"modules/{n}/jar/{n}.jar": {"A.class": "h1"} for n in real_names}
    full_j = dict(mj)
    full_j["modules/_wave_f_probe/jar/_wave_f_probe.jar"] = {"X.class": "h9"}
    cand = {"jars": full_j, "maven_jars": mj}
    with pytest.raises(AssertionError, match="_wave_f_probe"):
        _check_maven_jars_alignment_and_completeness(cand)


def test_regression_ant_modules_without_reactor_entry_empty_jars_is_vacuous():
    # The new direction's own empty case: zero Ant-built module jars makes the
    # jars-module set empty, which is vacuously a subset of any reactor set --
    # no offenders, by construction, regardless of what `modules` holds. (Whether
    # the SURROUNDING check skips outright when nobody has run Maven at all is
    # the pre-existing `if not mj` guard, proven by
    # test_regression_all_empty_maven_jars_still_skips below -- this test
    # isolates the new pure function's own behavior on empty input, per this
    # stage's "keep the existing skip-when-empty behavior" constraint.)
    assert _ant_modules_without_reactor_entry({}, ["modules/localization"]) == []


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
