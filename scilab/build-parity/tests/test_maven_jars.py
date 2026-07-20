"""maven_jars dimension: capture wiring (parity/capture.py) + diff gating
(parity/diff.py) for Maven's module jars.

THE GATE this file exists to build (Stage 2-c Task 1, design doc S2.1): before
this, `jars` only ever walked modules/<m>/jar/ (Ant's output). Maven writes to
modules/<m>/target/, a directory `jars` never looked at, so no Maven-built jar
had ever entered a whole-tree fingerprint -- every "parity green" claim for the
Maven beachhead modules (localization, commons) came from a HAND-RUN snippet
that calls fingerprint_jar() with both paths supplied by hand, which cannot
detect a wrong filename (the path is an input the human chooses). maven_jars
closes that: it walks target/ for real and keys each jar under the ANT-
EQUIVALENT path (modules/<m>/jar/<basename>) so it lands on a directly
comparable key -- a rename shows up as an added key on one side and a removed
key on the other, which a hand-picked path can never surface.
"""
import zipfile

from parity.capture import fingerprint_build, fingerprint_jar
from parity.diff import diff_fingerprints


def _make_jar(path, entries):
    with zipfile.ZipFile(path, "w") as zf:
        for name, data in entries.items():
            zf.writestr(name, data)


# ---- capture wiring: modules/<m>/target/*.jar -> maven_jars[modules/<m>/jar/<basename>] ----

def test_fingerprint_build_captures_maven_jar_at_ant_equivalent_key(tmp_path):
    build_dir = str(tmp_path)
    target = tmp_path / "modules" / "commons" / "target"
    target.mkdir(parents=True)
    jar_path = target / "commons-2027.0.0-SNAPSHOT.jar"
    _make_jar(jar_path, {"org/scilab/modules/commons/Foo.class": b"AAAA"})

    fp = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="t")

    # SYNTHETIC ant-equivalent key -- basename kept, "target" rewritten to "jar"
    # -- NOT Maven's real on-disk path, which must NOT appear as a key.
    assert "modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar" in fp["maven_jars"]
    assert "modules/commons/target/commons-2027.0.0-SNAPSHOT.jar" not in fp["maven_jars"]
    expected = fingerprint_jar(str(jar_path))
    assert fp["maven_jars"]["modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"] == expected


def test_fingerprint_build_maven_jars_excludes_classes_and_maven_archiver_subdirs(tmp_path):
    # "top level of target/ only -- not classes/, not maven-archiver/" (brief
    # Step 2). Adversarial fixture: a .jar-named file placed ONE level deeper
    # than target/ in each excluded subdir, so the exclusion is proven by
    # DIRECTORY, not merely by these subdirs happening to hold no jars.
    build_dir = str(tmp_path)
    target = tmp_path / "modules" / "commons" / "target"
    (target / "classes").mkdir(parents=True)
    (target / "maven-archiver").mkdir(parents=True)
    _make_jar(target / "classes" / "decoy.jar", {"X.class": b"X"})
    _make_jar(target / "maven-archiver" / "decoy2.jar", {"Y.class": b"Y"})
    _make_jar(target / "commons-2027.0.0-SNAPSHOT.jar", {"Z.class": b"Z"})

    fp = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="t")

    assert set(fp["maven_jars"]) == {"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"}


def test_fingerprint_build_maven_jars_excludes_non_module_target_dirs(tmp_path):
    # A target/ dir NOT under modules/ (e.g. a vendored thirdparty Maven build)
    # must not be captured -- mirrors the Ant-jar branch's thirdparty/jar
    # exclusion (test_jar.py's test_fingerprint_build_captures_jars).
    build_dir = str(tmp_path)
    stray = tmp_path / "thirdparty" / "something" / "target"
    stray.mkdir(parents=True)
    _make_jar(stray / "stray.jar", {"S.class": b"S"})
    real = tmp_path / "modules" / "commons" / "target"
    real.mkdir(parents=True)
    _make_jar(real / "commons-2027.0.0-SNAPSHOT.jar", {"C.class": b"C"})

    fp = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="t")

    assert set(fp["maven_jars"]) == {"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"}
    assert "thirdparty/something/jar/stray.jar" not in fp["maven_jars"]


def test_fingerprint_build_maven_jars_always_present_when_empty(tmp_path):
    # Mirrors generated_cmake/header_defines: ALWAYS present, even on a tree
    # with no modules/*/target at all -- an empty dict, never a missing key.
    # That is what lets diff.py tell "old capture.py" apart from "this tool,
    # found nothing" (see diff.py's transition-rule comment).
    fp = fingerprint_build(str(tmp_path), roots={}, runner=lambda cmd: "", build_id="t")
    assert fp["maven_jars"] == {}


def test_fingerprint_build_maven_jars_and_jars_stay_separate(tmp_path):
    # The two sections must not cross-populate: a Maven-only tree (target/, no
    # jar/) leaves the Ant `jars` dict empty.
    build_dir = str(tmp_path)
    target = tmp_path / "modules" / "commons" / "target"
    target.mkdir(parents=True)
    _make_jar(target / "commons-2027.0.0-SNAPSHOT.jar", {"C.class": b"C"})

    fp = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="t")

    assert fp["jars"] == {}
    assert set(fp["maven_jars"]) == {"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"}


def test_maven_jars_end_to_end_content_change_is_caught(tmp_path):
    # Full pipeline, REAL bytes: capture, mutate one class entry's content in a
    # REBUILT jar (same path, same entry name), recapture, diff. Closes the
    # same gap test_jar.py's test_fingerprint_jar_detects_class_change closes
    # for `jars` -- proves the byte-flip case end to end, not just against a
    # hand-typed hash string.
    build_dir = str(tmp_path)
    target = tmp_path / "modules" / "commons" / "target"
    target.mkdir(parents=True)
    jar_path = target / "commons-2027.0.0-SNAPSHOT.jar"

    _make_jar(jar_path, {"org/scilab/modules/commons/Foo.class": b"AAAA"})
    base = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="base")

    _make_jar(jar_path, {"org/scilab/modules/commons/Foo.class": b"BBBB"})  # rebuilt, one entry changed
    cand = fingerprint_build(build_dir, roots={}, runner=lambda cmd: "", build_id="cand")

    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any(
        "maven jar modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar: entry changed: "
        "org/scilab/modules/commons/Foo.class" in d
        for d in r["differences"])


# ---- diff gating: the four fault injections (brief Step 4) ----------------

def _fp(**over):
    """Minimal valid fingerprint; override any section via kwargs. Mirrors
    test_jar.py's _fp, plus the maven_jars section this file adds."""
    base = {"build_id": "t", "executables": {}, "dylibs": {},
            "generated": {}, "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
            "jars": {}, "maven_jars": {}}
    base.update(over)
    return base


def test_diff_detects_maven_jar_rename():
    # Injection 1: rename the Maven jar. Same content, different KEY (a
    # basename change -- e.g. Ant's finalName vs Maven's default, or an
    # artifactId/version slip). Must surface as an add + a remove, never a
    # silent match.
    base = _fp(maven_jars={"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar": {"A.class": "h1"}})
    cand = _fp(maven_jars={"modules/commons/jar/commons-9999.0.0-SNAPSHOT.jar": {"A.class": "h1"}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("maven jar missing in candidate: modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar" in d
               for d in diffs)
    assert any("maven jar extra in candidate: modules/commons/jar/commons-9999.0.0-SNAPSHOT.jar" in d
               for d in diffs)


def test_diff_detects_maven_jar_entry_byte_flip():
    # Injection 2: flip one byte in one class entry -- same key, same entry
    # NAME, different hash. Must report a content difference.
    key = "modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"
    base = _fp(maven_jars={key: {"org/scilab/modules/commons/Foo.class": "h1"}})
    cand = _fp(maven_jars={key: {"org/scilab/modules/commons/Foo.class": "h2"}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any(f"maven jar {key}: entry changed: org/scilab/modules/commons/Foo.class" in d
               for d in r["differences"])


def test_diff_detects_maven_jar_deleted():
    # Injection 3: delete the Maven jar entirely -- the key vanishes.
    key = "modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"
    base = _fp(maven_jars={key: {"A.class": "h1"}})
    cand = _fp(maven_jars={})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any(f"maven jar missing in candidate: {key}" in d for d in r["differences"])


def test_diff_detects_maven_jar_stray_entry_added():
    # Injection 4: add a stray entry to the jar (e.g. a META-INF/maven/ descriptor
    # maven-jar-plugin would embed by default) -- same key, one extra entry name.
    key = "modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar"
    base = _fp(maven_jars={key: {"A.class": "h1"}})
    cand = _fp(maven_jars={key: {"A.class": "h1",
                                 "META-INF/maven/org.scilab/commons/pom.xml": "hstray"}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any(f"maven jar {key}: entry added: META-INF/maven/org.scilab/commons/pom.xml" in d
               for d in r["differences"])


def test_diff_identical_maven_jars_ok():
    # No false positive: matching maven_jars sections diff clean.
    fp = _fp(maven_jars={"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar":
                         {"A.class": "h1", "B.class": "h2"}})
    assert diff_fingerprints(fp, _fp(maven_jars=fp["maven_jars"]))["ok"]


# ---- transition rule, one test per direction (brief Step 4) ---------------

def test_diff_baseline_without_maven_jars_skips():
    # The committed baseline predates maven_jars capture: a baseline with no
    # "maven_jars" key at all skips the check entirely -- forward-compatible,
    # not a parity failure, regardless of what the candidate carries.
    base = _fp()
    del base["maven_jars"]
    cand = _fp(maven_jars={"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar": {"A": "1"}})
    assert diff_fingerprints(base, cand) == {"ok": True, "differences": []}


def test_diff_candidate_missing_maven_jars_against_armed_baseline_fails():
    # The reverse is NOT tolerated: once the baseline carries maven_jars, a
    # candidate captured with a pre-maven_jars tool must not silently skip --
    # it must fail, naming the missing section (mirrors the jars/rpaths rule).
    base = _fp(maven_jars={"modules/commons/jar/commons-2027.0.0-SNAPSHOT.jar": {"A": "1"}})
    cand = _fp()
    del cand["maven_jars"]
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("maven_jars section missing in candidate" in d for d in r["differences"])
