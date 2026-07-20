"""Compare two build fingerprints. `ok` iff behaviorally identical."""
import json
import sys

# The eleven of "generated"'s 13 GENERATED_FILES entries that get a real CMake-vs-configure
# byte comparison: the ten RC-c files (RC-c's ScilabGeneratedFiles.cmake writes them into
# build-cmake/generated/) plus version.h (a later final review found it shared the exact
# same gap -- its CMake copy lands in build-cmake/generated-includes/ instead, via
# parity.capture's _GENERATED_CMAKE_PATH_OVERRIDES -- and closed it the same way, since
# version.h, unlike machine.h, IS byte-identical between CMake and configure). I.e. all of
# GENERATED_FILES EXCEPT etc/classpath.xml (no CMake counterpart yet, deferred to Stage 2)
# and machine.h (also under generated-includes/, but compared by its own semantic dimension,
# header_defines, below, because CMake's machine.h is NOT byte-identical to configure's).
# Kept as a literal here rather than imported from parity.capture, matching this module's
# existing standalone-of-capture.py design (see the ("c", "cxx", "f") literal further down)
# -- diff.py compares frozen JSON, never the tool that produced it. Update alongside
# parity.capture.GENERATED_FILES / _GENERATED_CMAKE_PATH_OVERRIDES if this subset ever changes.
_GENERATED_CMAKE_KEYS = frozenset((
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
    "modules/core/includes/version.h",
))


def _diff_named(kind, base, cand, out):
    """Report added/removed keys in a name->obj mapping."""
    for name in sorted(set(base) - set(cand)):
        out.append(f"{kind} missing in candidate: {name}")
    for name in sorted(set(cand) - set(base)):
        out.append(f"{kind} extra in candidate: {name}")


def _diff_rpaths(kind, name, b, c, out):
    """LC_RPATH list, compared as an ORDERED list (dyld searches rpaths in
    order -- a reorder can flip which library @rpath resolves to, so sorting
    here would false-green a real behavioral change). A BASELINE entry with no
    "rpaths" key predates rpath capture (the committed baseline until Task 2
    re-captures it): skip -- not yet gated, not a failure. The reverse is NOT
    tolerated: against an rpath-aware baseline, a candidate lacking the key
    must not silently skip the gate (mirrors the flags-block rule below)."""
    if "rpaths" not in b:
        return
    if "rpaths" not in c:
        out.append(f"{kind} {name}: rpaths missing in candidate")
    elif b["rpaths"] != c["rpaths"]:
        out.append(f"{kind} {name}: rpaths {b['rpaths']} != {c['rpaths']}")


def _diff_tu_flag_group(kind, base, cand, out):
    """One group inside "tu_flag_facts" -- "defaults" (keyed by language) or
    "overrides" (keyed by TU relpath), each a {name: {fact: value}} mapping.
    Same idiom as header_defines: presence via _diff_named, then a per-shared-
    name fact diff (added/removed/changed keys, values shown for a change)."""
    _diff_named(kind, base, cand, out)
    for name in sorted(set(base) & set(cand)):
        b, c = base[name], cand[name]
        for k in sorted(set(b) - set(c)):
            out.append(f"{kind} {name}: fact removed: {k}")
        for k in sorted(set(c) - set(b)):
            out.append(f"{kind} {name}: fact added: {k}")
        for k in sorted(set(b) & set(c)):
            if b[k] != c[k]:
                out.append(f"{kind} {name}: fact changed: {k} ({b[k]!r} -> {c[k]!r})")


def diff_fingerprints(base, cand):
    out = []

    # Executables: presence + the SDK stamp (release-blocking) + deps + tmp leak.
    _diff_named("executable", base["executables"], cand["executables"], out)
    for name in sorted(set(base["executables"]) & set(cand["executables"])):
        b, c = base["executables"][name], cand["executables"][name]
        if b["build_version"] != c["build_version"]:
            out.append(f"executable {name}: build_version (minos/sdk) changed "
                       f"{b['build_version']} -> {c['build_version']}")
        if c["tmp_leak"]:
            out.append(f"executable {name}: non-relocatable /tmp path in link")
        # An executable has no LC_ID_DYLIB, so parse_otool_libs records its FIRST
        # linked library under install_name. Compare it (the dylib block does) or a
        # Stage-1 CMake link-order change that reshuffles scilab-bin's first
        # dependency is invisible -- it lands in the one field nobody checked.
        if b["install_name"] != c["install_name"]:
            out.append(f"executable {name}: install_name (first linked library) changed")
        if sorted(b["deps"]) != sorted(c["deps"]):
            out.append(f"executable {name}: link dependencies changed")
        _diff_rpaths("executable", name, b, c, out)

    # Dylibs: presence + symbol set + deps + install name + tmp leak.
    _diff_named("dylib", base["dylibs"], cand["dylibs"], out)
    for name in sorted(set(base["dylibs"]) & set(cand["dylibs"])):
        b, c = base["dylibs"][name], cand["dylibs"][name]
        removed = sorted(set(b["symbols"]) - set(c["symbols"]))
        added = sorted(set(c["symbols"]) - set(b["symbols"]))
        if removed:
            out.append(f"dylib {name}: symbols removed: {', '.join(removed)}")
        if added:
            out.append(f"dylib {name}: symbols added: {', '.join(added)}")
        if b["install_name"] != c["install_name"]:
            out.append(f"dylib {name}: install_name changed")
        if sorted(b["deps"]) != sorted(c["deps"]):
            out.append(f"dylib {name}: link dependencies changed")
        _diff_rpaths("dylib", name, b, c, out)
        if c["tmp_leak"]:
            out.append(f"dylib {name}: non-relocatable /tmp path in link")

    # Generated files: presence + content hash. NOTE what this compares: `generated` is
    # always resolved against the SOURCE TREE (configure's own copy) on both the baseline and
    # candidate side -- see parity.capture.fingerprint_build's GENERATED_FILES loop. It never
    # looks at anything CMake wrote. The block below (`generated_cmake`) is what does that.
    _diff_named("generated file", base["generated"], cand["generated"], out)
    for name in sorted(set(base["generated"]) & set(cand["generated"])):
        if base["generated"][name] != cand["generated"][name]:
            out.append(f"generated file changed: {name}")

    # RC-c final-review Finding (Critical): CMake's OWN copies of the generated files
    # (build-cmake/generated/ for ten of them, build-cmake/generated-includes/version.h for
    # the eleventh -- see parity.capture._GENERATED_CMAKE_PATH_OVERRIDES), checked directly
    # against the BASELINE's EXISTING "generated" hashes above (configure's copies) --
    # deliberately NOT a same-named "generated_cmake" baseline section. Arming one is unneeded
    # and out of scope: base["generated"] has carried real hashes for every one of
    # _GENERATED_CMAKE_KEYS since before RC-c (version.h was one of the original three
    # GENERATED_FILES entries; the other nine plus Version.incl arrived at RC-c), so the
    # reference side needs no new baseline capture, and baseline-autotools.json stays
    # untouched. This is what actually proves "CMake wrote what configure wrote" -- comparing
    # GENERATED_FILES against build_dir on both sides, as the block above does, only ever reads
    # configure's own copy no matter which build produced the fingerprint, so a corrupted
    # build-cmake/generated/ (or generated-includes/version.h) file was invisible to parity
    # before this (configure-vs-configure, unconditionally).
    #
    # Transition rule mirrors header_defines/jars, adapted to that base/generated_cmake naming
    # asymmetry: a candidate with no "generated_cmake" key AT ALL predates this capture (an old
    # capture.py) -> skip, not yet armed -- the key is always present, even if empty, in any
    # fingerprint taken by a capture.py new enough to have this section (mirrors how
    # header_defines tells "old tool" apart from "tool found nothing"). Once a candidate DOES
    # carry the section, it is held to the baseline's existing hashes for exactly the
    # _GENERATED_CMAKE_KEYS subset: a file it is missing, adds unexpectedly, or reports with
    # the wrong hash all fail, naming the file -- never silently drops a comparison it is able
    # to make.
    if "generated_cmake" in cand:
        expected = {k: v for k, v in base.get("generated", {}).items() if k in _GENERATED_CMAKE_KEYS}
        _diff_named("generated (cmake) file", expected, cand["generated_cmake"], out)
        for name in sorted(set(expected) & set(cand["generated_cmake"])):
            if expected[name] != cand["generated_cmake"][name]:
                out.append(f"generated (cmake) file changed: {name}")

    # Jars: presence + per-jar entry-content map. Transition rule mirrors rpaths/
    # flags: a baseline with no "jars" section predates jar capture -> skip (not yet
    # armed, not a failure). The reverse is NOT tolerated: against a jar-aware
    # baseline, a candidate lacking the section must fail (not silently skip).
    if "jars" in base:
        if "jars" not in cand:
            out.append("jars section missing in candidate")
        else:
            _diff_named("jar", base["jars"], cand["jars"], out)
            for name in sorted(set(base["jars"]) & set(cand["jars"])):
                b, c = base["jars"][name], cand["jars"][name]
                for e in sorted(set(b) - set(c)):
                    out.append(f"jar {name}: entry removed: {e}")
                for e in sorted(set(c) - set(b)):
                    out.append(f"jar {name}: entry added: {e}")
                for e in sorted(set(b) & set(c)):
                    if b[e] != c[e]:
                        out.append(f"jar {name}: entry changed: {e}")

    # Maven's module jars (parity.capture's maven_jars section), keyed under the
    # SAME ant-equivalent-path scheme as `jars` above -- see that section's
    # comment in capture.py for why the key is deliberately synthetic. Compared
    # exactly the way `jars` is: presence via _diff_named, then a per-shared-key
    # entry diff (removed/added/changed). Transition rule mirrors jars/rpaths/
    # header_defines/tu_flag_facts exactly: a baseline with no "maven_jars"
    # section predates Maven capture -> skip (not yet armed, not a failure). The
    # reverse is NOT tolerated: against a maven_jars-aware baseline, a candidate
    # lacking the section must fail (not silently skip).
    #
    # SCOPE NOTE: this checks a baseline's OWN maven_jars against a candidate's
    # OWN maven_jars -- regression across RUNS, exactly like `jars` does. It does
    # NOT cross-check maven_jars against `jars` -- that cross-check is a SEPARATE
    # gate, test_acceptance.py::test_maven_jars_align_with_ant_jars, run once per
    # capture rather than base-vs-candidate. CURRENT STATE (Stage 2-c Decision A,
    # shipped): the parent POM's
    # <finalName>org.scilab.modules.${project.artifactId}</finalName> makes
    # every Maven jar's basename match its Ant counterpart, so that cross-check
    # now lands each module on one shared key and compares clean. Before
    # <finalName> landed, that SAME cross-check showed an added key and a
    # removed key per module -- that was the gate WORKING, catching the exact
    # wrong-filename defect the hand-run snippet it replaces could never catch
    # (see the Stage 2-c design doc S2.1). This transition rule does not perform
    # that cross-check itself; it is what makes `maven_jars` a real,
    # regression-gated dimension once armed.
    if "maven_jars" in base:
        if "maven_jars" not in cand:
            out.append("maven_jars section missing in candidate")
        else:
            _diff_named("maven jar", base["maven_jars"], cand["maven_jars"], out)
            for name in sorted(set(base["maven_jars"]) & set(cand["maven_jars"])):
                b, c = base["maven_jars"][name], cand["maven_jars"][name]
                for e in sorted(set(b) - set(c)):
                    out.append(f"maven jar {name}: entry removed: {e}")
                for e in sorted(set(c) - set(b)):
                    out.append(f"maven jar {name}: entry added: {e}")
                for e in sorted(set(b) & set(c)):
                    if b[e] != c[e]:
                        out.append(f"maven jar {name}: entry changed: {e}")

    # Semantic header parity (RC-a): machine.h compared by its #define SET, not bytes
    # (a CMake-generated header is never byte-identical to autoconf's). Transition rule
    # mirrors rpaths/jars: a baseline with no section predates RC-a -> skip; a candidate
    # that LOST the section against an armed baseline must FAIL.
    if "header_defines" in base:
        if "header_defines" not in cand:
            out.append("header_defines section missing in candidate")
        else:
            _diff_named("semantic header", base["header_defines"], cand["header_defines"], out)
            for name in sorted(set(base["header_defines"]) & set(cand["header_defines"])):
                b, c = base["header_defines"][name], cand["header_defines"][name]
                for m in sorted(set(b) - set(c)):
                    out.append(f"{name}: macro removed: {m}")
                for m in sorted(set(c) - set(b)):
                    out.append(f"{name}: macro added: {m}")
                for m in sorted(set(b) & set(c)):
                    if b[m] != c[m]:
                        out.append(f"{name}: macro changed: {m} ({b[m]!r} -> {c[m]!r})")

    # Compiler-flag facts, per language. The `source` label (autotools vs cmake)
    # is deliberately NOT compared -- flipping it is the migration itself; only
    # the semantic codegen facts matter. .get(): fingerprints captured before
    # the flag manifest existed lack the block entirely -- two old fingerprints
    # diff clean, but a language present on one side only is a difference (a
    # pre-manifest candidate must not silently skip the flag check).
    bflags = base.get("flags") or {}
    cflags = cand.get("flags") or {}
    for lang in ("c", "cxx", "f"):
        b, c = bflags.get(lang), cflags.get(lang)
        if b is None and c is None:
            continue
        if c is None:
            out.append(f"flags {lang}: facts missing in candidate")
        elif b is None:
            out.append(f"flags {lang}: facts extra in candidate")
        elif b != c:
            changed = sorted(k for k in set(b) | set(c) if b.get(k) != c.get(k))
            out.append(f"flags {lang}: " + ", ".join(
                f"{k} {b.get(k)!r} -> {c.get(k)!r}" for k in changed))

    # Per-TU derived flag-fact baseline (RC-b): the frozen {"defaults", "overrides"}
    # that parity.flagfacts_check checks CMake TUs against (parity.capture.
    # capture_tu_flag_facts derives it from the autotools generated Makefiles).
    # Unlike the "flags" block above (one representative TU per language), this is
    # the ~211-entry per-TU ground truth, so it gets its own comparison rather than
    # reusing that one. Compared the same way header_defines/jars are: presence via
    # _diff_named, then a per-shared-name fact diff -- done once for "defaults"
    # (keyed by language) and once for "overrides" (keyed by TU relpath). Transition
    # rule mirrors rpaths/jars/header_defines: a baseline with no "tu_flag_facts"
    # section predates RC-b -> skip (not yet armed, not a failure); a candidate that
    # LOST the section against an armed baseline must FAIL (not silently skip). This
    # is also what catches the frozen baseline drifting from the generated Makefiles
    # it was derived from -- a hand edit, or a capture taken before ./configure ever
    # ran (an empty "defaults"/"overrides") -- which flagfacts_check alone cannot
    # see: it only ever reads the baseline, never cross-checks it against anything
    # else. Closes Makefile drift too, for as long as the generated Makefiles exist.
    if "tu_flag_facts" in base:
        if "tu_flag_facts" not in cand:
            out.append("tu_flag_facts section missing in candidate")
        else:
            btu, ctu = base["tu_flag_facts"], cand["tu_flag_facts"]
            _diff_tu_flag_group("flags default", btu.get("defaults", {}), ctu.get("defaults", {}), out)
            _diff_tu_flag_group("flags override", btu.get("overrides", {}), ctu.get("overrides", {}), out)

    return {"ok": not out, "differences": out}


def _main(argv):
    if len(argv) != 3:
        print("usage: python -m parity.diff <baseline.json> <candidate.json>", file=sys.stderr)
        return 2
    # Exit 2 (not 1) if a file is missing/unreadable/malformed: a broken pipeline
    # must be distinguishable from a genuine parity regression (exit 1) in CI.
    try:
        with open(argv[1]) as f:
            base = json.load(f)
        with open(argv[2]) as f:
            cand = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"error: could not read fingerprint file: {e}", file=sys.stderr)
        return 2
    result = diff_fingerprints(base, cand)
    if result["ok"]:
        print("PARITY OK")
        return 0
    print(f"PARITY FAILED — {len(result['differences'])} difference(s):")
    for d in result["differences"]:
        print(f"  - {d}")
    return 1


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
