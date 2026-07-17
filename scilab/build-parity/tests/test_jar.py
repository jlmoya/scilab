"""Jar dimension of the parity harness: content fingerprint, MANIFEST
normalization, capture wiring, diff gating. A jar is compared by its entries'
CONTENT (sha256), timestamps + zip order stripped, so the same classes built at
different times fingerprint identically; MANIFEST tool-version lines are normalized."""
import hashlib
import zipfile

from parity.fingerprint import normalize_manifest
from parity.capture import fingerprint_jar


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
