"""Jar dimension of the parity harness: content fingerprint, MANIFEST
normalization, capture wiring, diff gating. A jar is compared by its entries'
CONTENT (sha256), timestamps + zip order stripped, so the same classes built at
different times fingerprint identically; MANIFEST tool-version lines are normalized."""
import hashlib
import zipfile

from parity.fingerprint import normalize_manifest
from parity.capture import fingerprint_build, fingerprint_jar
from parity.diff import diff_fingerprints


def _make_jar(path, entries):
    with zipfile.ZipFile(path, "w") as zf:
        for name, data in entries.items():
            zf.writestr(name, data)


def test_normalize_manifest_strips_volatile_lines():
    m = ("Manifest-Version: 1.0\nAnt-Version: Apache Ant 1.10.14\n"
         "Created-By: 25 (Oracle Corporation)\nMain-Class: org.scilab.Foo\n")
    assert normalize_manifest(m) == "Manifest-Version: 1.0\nMain-Class: org.scilab.Foo"


def test_normalize_manifest_keeps_stable_lines():
    m = "Manifest-Version: 1.0\nMain-Class: org.scilab.Foo\nClass-Path: a.jar b.jar"
    assert normalize_manifest(m) == m


def test_normalize_manifest_strips_dstamp_implementation_version():
    # build.incl.xml stamps Implementation-Version with "${DSTAMP} ${TSTAMP}" --
    # the build date + minute (e.g. "20260717 1645"). It is a Built-Date in
    # disguise: any cross-minute rebuild changes it in every module jar.
    m = ("Manifest-Version: 1.0\nImplementation-Title: org.scilab.modules.gui.jar\n"
         "Implementation-Version: 20260717 1645\nClass-Path: a.jar\n")
    assert normalize_manifest(m) == ("Manifest-Version: 1.0\n"
                                     "Implementation-Title: org.scilab.modules.gui.jar\n"
                                     "Class-Path: a.jar")


def test_normalize_manifest_keeps_semantic_implementation_version():
    # Only the DSTAMP/TSTAMP FORM is volatile. A real version string in
    # Implementation-Version is a stable attribute -- if it ever changed
    # between baseline and candidate, that is a regression to report.
    m = "Manifest-Version: 1.0\nImplementation-Version: 2027.0.0\n"
    assert normalize_manifest(m) == "Manifest-Version: 1.0\nImplementation-Version: 2027.0.0"


def test_fingerprint_jar_hashes_entry_content(tmp_path):
    j = tmp_path / "a.jar"
    _make_jar(j, {"org/x/A.class": b"AAAA", "org/x/B.class": b"BBBB"})
    fp = fingerprint_jar(str(j))
    assert set(fp) == {"org/x/A.class", "org/x/B.class"}
    assert fp["org/x/A.class"] == hashlib.sha256(b"AAAA").hexdigest()


def test_fingerprint_jar_ignores_manifest_volatile_lines(tmp_path):
    j1, j2 = tmp_path / "1.jar", tmp_path / "2.jar"
    _make_jar(j1, {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\nAnt-Version: A\nMain-Class: F\n",
                   "C.class": b"X"})
    _make_jar(j2, {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\nAnt-Version: B\nMain-Class: F\n",
                   "C.class": b"X"})
    assert fingerprint_jar(str(j1)) == fingerprint_jar(str(j2))


def test_fingerprint_jar_detects_class_change(tmp_path):
    j1, j2 = tmp_path / "1.jar", tmp_path / "2.jar"
    _make_jar(j1, {"C.class": b"X"})
    _make_jar(j2, {"C.class": b"Y"})
    assert fingerprint_jar(str(j1)) != fingerprint_jar(str(j2))


def test_fingerprint_jar_skips_directory_entries(tmp_path):
    j = tmp_path / "a.jar"
    with zipfile.ZipFile(j, "w") as zf:
        zf.writestr("org/", b"")          # directory entry
        zf.writestr("org/A.class", b"Z")
    assert set(fingerprint_jar(str(j))) == {"org/A.class"}


def test_fingerprint_jar_hashes_raw_bytes_not_text(tmp_path):
    # Class files are binary; b"\xca\xfe\xba\xbe" (the class-file magic) is
    # invalid UTF-8. The hash must be over the RAW bytes -- an implementation
    # that routed entries through a lossy decode/re-encode would change it.
    j = tmp_path / "a.jar"
    _make_jar(j, {"org/x/A.class": b"\xca\xfe\xba\xbe"})
    fp = fingerprint_jar(str(j))
    assert fp["org/x/A.class"] == hashlib.sha256(b"\xca\xfe\xba\xbe").hexdigest()


def test_fingerprint_jar_nested_manifest_hashes_raw(tmp_path):
    # Normalization is gated on the jar's own top-level META-INF/MANIFEST.MF
    # ONLY. A nested one (e.g. under a repackaged subtree) is ordinary content
    # and must hash as raw bytes, volatile lines and all.
    raw = b"Manifest-Version: 1.0\nAnt-Version: Apache Ant 1.10.14\nMain-Class: F\n"
    j = tmp_path / "a.jar"
    _make_jar(j, {"foo/META-INF/MANIFEST.MF": raw})
    fp = fingerprint_jar(str(j))
    assert fp["foo/META-INF/MANIFEST.MF"] == hashlib.sha256(raw).hexdigest()


def _fp(**over):
    """Minimal valid fingerprint; override any section via kwargs."""
    base = {"build_id": "t", "executables": {}, "dylibs": {},
            "generated": {}, "flags": {"source": "autotools", "c": None, "cxx": None, "f": None},
            "jars": {}}
    base.update(over)
    return base


def test_fingerprint_build_captures_jars(tmp_path):
    jardir = tmp_path / "modules" / "gui" / "jar"
    jardir.mkdir(parents=True)
    _make_jar(jardir / "org.scilab.modules.gui.jar", {"G.class": b"G"})
    # a non-module jar dir must NOT be captured
    other = tmp_path / "thirdparty" / "jar"
    other.mkdir(parents=True)
    _make_jar(other / "ext.jar", {"E.class": b"E"})
    fp = fingerprint_build(str(tmp_path), roots={}, runner=lambda cmd: "", build_id="t")
    assert "modules/gui/jar/org.scilab.modules.gui.jar" in fp["jars"]
    assert "thirdparty/jar/ext.jar" not in fp["jars"]


def test_fingerprint_build_skips_doc_output_jars(tmp_path):
    # The opt-in help build (CMake `doc` target / `make doc`) writes its OUTPUT
    # jars into modules/helptools/jar/ NEXT TO the real module jar: one
    # scilab_<locale>_help.jar per built locale plus scilab_images.jar. Those
    # are separately-gated, locale-dependent documentation artifacts -- not part
    # of the module-jar contract -- so capturing them would fail parity the
    # moment anyone builds help. The exclusion is by FILENAME: the real module
    # jar org.scilab.modules.helptools.jar in the SAME directory must survive.
    jardir = tmp_path / "modules" / "helptools" / "jar"
    jardir.mkdir(parents=True)
    _make_jar(jardir / "org.scilab.modules.helptools.jar", {"H.class": b"H"})
    _make_jar(jardir / "scilab_en_US_help.jar", {"help.html": b"<html>"})
    _make_jar(jardir / "scilab_images.jar", {"img.png": b"\x89PNG"})
    fp = fingerprint_build(str(tmp_path), roots={}, runner=lambda cmd: "", build_id="t")
    assert "modules/helptools/jar/org.scilab.modules.helptools.jar" in fp["jars"]
    assert "modules/helptools/jar/scilab_en_US_help.jar" not in fp["jars"]
    assert "modules/helptools/jar/scilab_images.jar" not in fp["jars"]


def test_diff_detects_jar_entry_change():
    base = _fp(jars={"m.jar": {"A.class": "h1"}})
    cand = _fp(jars={"m.jar": {"A.class": "h2"}})
    r = diff_fingerprints(base, cand)
    assert not r["ok"]
    assert any("jar m.jar: entry changed: A.class" in d for d in r["differences"])


def test_diff_detects_added_and_removed_jar():
    base = _fp(jars={"a.jar": {"A": "1"}})
    cand = _fp(jars={"b.jar": {"B": "1"}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("jar missing in candidate: a.jar" in d for d in diffs)
    assert any("jar extra in candidate: b.jar" in d for d in diffs)


def test_diff_baseline_without_jars_skips():
    base = _fp()
    del base["jars"]                     # pre-jar baseline (transition)
    assert diff_fingerprints(base, _fp(jars={"m.jar": {"A": "1"}}))["ok"]


def test_diff_candidate_missing_jars_against_armed_baseline_fails():
    base = _fp(jars={"m.jar": {"A": "1"}})
    cand = _fp()
    del cand["jars"]
    assert not diff_fingerprints(base, cand)["ok"]


def test_diff_identical_jars_ok():
    fp = _fp(jars={"m.jar": {"A.class": "h1", "B.class": "h2"}})
    assert diff_fingerprints(fp, _fp(jars={"m.jar": {"A.class": "h1", "B.class": "h2"}}))["ok"]


def test_diff_detects_jar_entry_add_and_remove():
    # a shared jar whose entry SET changed (B dropped, C appeared) — the canonical
    # "a class vanished from a still-built jar" regression the jar dimension exists for.
    base = _fp(jars={"m.jar": {"A.class": "1", "B.class": "2"}})
    cand = _fp(jars={"m.jar": {"A.class": "1", "C.class": "3"}})
    diffs = diff_fingerprints(base, cand)["differences"]
    assert any("jar m.jar: entry removed: B.class" in d for d in diffs)
    assert any("jar m.jar: entry added: C.class" in d for d in diffs)
