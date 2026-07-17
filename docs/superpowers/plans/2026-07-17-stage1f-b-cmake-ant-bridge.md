# Stage 1f-b — CMake→Ant bridge for the Java jars — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make CMake (not the autotools recursive `make`) invoke Ant to build the 24 Java module jars, keeping Ant unchanged, with the jars proven content-equivalent to their autotools originals by a new jar dimension in the parity harness.

**Architecture:** One CMake custom target (`sci-java-all`) wraps the existing `modules/prebuildjava` Ant super-build (its `build.xml` `all` target hand-topo-sorts all 24 module jars + Ivy). The topo-sort, Ivy, and inter-module Java deps stay inside Ant. The parity harness gains a `jars` section: per jar, the sorted map of `entry-name → sha256(content)` with volatile `META-INF/MANIFEST.MF` lines normalized out — the honest analog of native byte-shape parity, without jar timestamp nondeterminism.

**Tech Stack:** CMake (custom target + config.status parsing), Apache Ant + Ivy (unchanged), Python 3 stdlib (`zipfile`, `hashlib`) + pytest (the parity harness).

## Global Constraints

- **Reproduce, don't improve; keep Ant unchanged.** No edits to `configure.ac`, any `Makefile.am`, any `build.xml`, `build.incl.xml`, or `ivy.xml`. Only the parity harness, the new `scilab/cmake/ScilabJava.cmake`, `scilab/CMakeLists.txt`, the baseline JSON, docs, and CI change.
- **Approach A — one target wrapping `prebuildjava`.** No per-module CMake Java targets; do NOT replicate the topo-sort / Java dep graph in CMake (that is Stage 2's Maven reactor).
- **Jar parity = normalized, timestamp-free content manifest** (`{entry: sha256}`, MANIFEST volatile lines stripped). NOT byte-for-byte (jars embed timestamps + non-deterministic zip order).
- **Baseline from a pure-autotools rebuild.** Same JDK as the bridge (`SCILAB_JAVA_HOME` from 1f-a = the configured jdk-25) ⟹ identical bytecode.
- **Coexistence, rollback free.** Autotools `make` still builds the jars via `prebuildjava`. The CMake file is invisible to automake.
- **The reproducibility probe must be seen to work** — the MANIFEST normalize-list is empirical (two identical autotools builds must fingerprint identically); "a guard you have not seen FAIL is not a guard."
- **Headless smoke uses `-nw`, never `-nwni`** — `-nwni` disables the JVM (*"jimport function disabled in -nwni mode"*), so it loads no jars. `-nw` is "no window, JVM on".
- **No AI-attribution in commit messages** (no Co-Authored-By / "Generated with" / Claude trailers).
- **Native side unchanged** — `-std=c++17` held; no dylib/aggregate/executable change.

## File Structure

- `scilab/build-parity/parity/fingerprint.py` — MODIFY: add pure `normalize_manifest(text)`.
- `scilab/build-parity/parity/capture.py` — MODIFY: add `fingerprint_jar(path, opener=…)`; walk `modules/*/jar/*.jar` in `fingerprint_build` → a `jars` section; update the CLI summary line.
- `scilab/build-parity/parity/diff.py` — MODIFY: compare the `jars` section (set + per-jar entry map) with the rpath-style transition rule.
- `scilab/build-parity/tests/test_jar.py` — CREATE: unit tests for all of the above (fault-injection + normalization).
- `scilab/build-parity/baseline-autotools.json` — MODIFY: re-capture native + `jars` from a pure-autotools rebuild.
- `scilab/cmake/ScilabJava.cmake` — CREATE: `scilab_java_bridge()` → the `sci-java-all` target.
- `scilab/CMakeLists.txt` — MODIFY: `include(cmake/ScilabJava.cmake)` + call + wire onto `drop-in-all`.
- `docs/design/build-cmake-driver.md` — MODIFY: document the jar bridge + jar parity dimension.
- `.gitlab-ci.yml` — MODIFY: `sanity:cmake-driver` asserts `sci-java-all`; the native gate covers jars automatically.

---

### Task 1: Jar content fingerprint + MANIFEST normalization

**Files:**
- Modify: `scilab/build-parity/parity/fingerprint.py` (add `normalize_manifest`)
- Modify: `scilab/build-parity/parity/capture.py` (add `fingerprint_jar`)
- Test: `scilab/build-parity/tests/test_jar.py` (new)

**Interfaces:**
- Produces: `normalize_manifest(text: str) -> str` (fingerprint.py); `fingerprint_jar(path: str, opener=zipfile.ZipFile) -> dict[str, str]` (capture.py, `{entry_name: sha256hex}`).
- Consumes: nothing from later tasks.

- [ ] **Step 1: Write the failing tests.** Create `scilab/build-parity/tests/test_jar.py`:

```python
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
```

- [ ] **Step 2: Run to verify failure.**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_jar.py -q`
Expected: FAIL — `ImportError: cannot import name 'normalize_manifest'` (and `fingerprint_jar`).

- [ ] **Step 3: Implement `normalize_manifest` in `fingerprint.py`.** Append after `normalize_path`:

```python
# MANIFEST.MF lines Ant/jar stamp with build-environment specifics (tool + JDK
# versions/vendor). Identical across two runs on the same machine, but they would
# make a jar's content hash Ant/JDK-version-dependent, defeating the point of
# comparing bytecode. Stripped before hashing so the manifest compares by its
# STABLE attributes only (Manifest-Version, Main-Class, Class-Path, package attrs).
# The two-build reproducibility probe (plan Task 3) is what proves this list COMPLETE.
_MANIFEST_VOLATILE = re.compile(
    r"^(Ant-Version|Created-By|Built-By|Built-Date|Build-Jdk(-Spec)?|"
    r"Bnd-LastModified|Archiver-Version):", re.IGNORECASE)


def normalize_manifest(text):
    """Drop build-environment-volatile lines from a jar's META-INF/MANIFEST.MF so
    its content hash reflects only stable attributes. Line-oriented; preserves the
    order of surviving lines."""
    return "\n".join(l for l in text.splitlines() if not _MANIFEST_VOLATILE.match(l))
```

- [ ] **Step 4: Implement `fingerprint_jar` in `capture.py`.** Add `import zipfile` to the imports, add `normalize_manifest` to the `from parity.fingerprint import (…)` list, and add:

```python
def fingerprint_jar(path, opener=zipfile.ZipFile):
    """A jar (zip) -> {entry_name: sha256hex(content)}. Reads each entry's CONTENT,
    NOT the zip container's per-entry timestamp or ordering, so two jars with
    identical files but different build times / entry order fingerprint identically.
    META-INF/MANIFEST.MF is normalize_manifest()'d first (strip tool-version lines).
    Directory entries (no content) are skipped. `opener` is injected for tests."""
    out = {}
    with opener(path) as zf:
        for name in sorted(zf.namelist()):
            if name.endswith("/"):
                continue
            data = zf.read(name)
            if name == "META-INF/MANIFEST.MF":
                data = normalize_manifest(data.decode("utf-8", "replace")).encode("utf-8")
            out[name] = hashlib.sha256(data).hexdigest()
    return out
```

- [ ] **Step 5: Run to verify pass.**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_jar.py -q`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit.**

```bash
git add scilab/build-parity/parity/fingerprint.py scilab/build-parity/parity/capture.py scilab/build-parity/tests/test_jar.py
git commit -m "build-parity: jar content fingerprint + MANIFEST normalization"
```

---

### Task 2: Capture the `jars` section + diff it

**Files:**
- Modify: `scilab/build-parity/parity/capture.py` (`fingerprint_build` jars walk + CLI summary)
- Modify: `scilab/build-parity/parity/diff.py` (jars comparison block)
- Test: `scilab/build-parity/tests/test_jar.py` (extend)

**Interfaces:**
- Consumes: `fingerprint_jar` (Task 1).
- Produces: fingerprint dict gains a `"jars": {jar_relpath: {entry: hash}}` section; `diff_fingerprints` compares it.

- [ ] **Step 1: Write the failing tests.** Append to `tests/test_jar.py`:

```python
from parity.capture import fingerprint_build
from parity.diff import diff_fingerprints


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
```

- [ ] **Step 2: Run to verify failure.**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_jar.py -q`
Expected: FAIL — `fingerprint_build` result has no `jars` key (`KeyError`) and the diff ignores jars.

- [ ] **Step 3: Capture the `jars` section in `capture.py`.** In `fingerprint_build`, add a `jars = {}` initializer next to `dylibs = {}`, and add this branch inside the existing `os.walk` loop (after the `elif "/macros/"` branch):

```python
        elif "/modules/" in posix_root + "/" and posix_root.endswith("/jar"):
            # modules/<m>/jar/*.jar — the Ant-built module jars. Content manifest
            # (fingerprint_jar), NOT byte hash: jars embed timestamps + non-det zip
            # order. Only modules/*/jar (not thirdparty/ or a build-cache jar dir).
            for fn in files:
                if fn.endswith(".jar"):
                    rel = os.path.relpath(os.path.join(root, fn), build_dir).replace(os.sep, "/")
                    jars[rel] = fingerprint_jar(os.path.join(root, fn))
```

Then add `"jars": jars,` to the returned dict, and update the final `print(...)` summary to include `f"{len(fp['jars'])} jars, "`.

- [ ] **Step 4: Compare the `jars` section in `diff.py`.** In `diff_fingerprints`, after the generated-files block and before the flags block, add:

```python
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
```

- [ ] **Step 5: Run to verify pass.**

Run: `cd scilab/build-parity && python3 -m pytest tests/ -q`
Expected: PASS (the full suite, incl. the new jar tests; existing tests unaffected).

- [ ] **Step 6: Commit.**

```bash
git add scilab/build-parity/parity/capture.py scilab/build-parity/parity/diff.py scilab/build-parity/tests/test_jar.py
git commit -m "build-parity: capture + diff the jars section (transition-gated like rpaths)"
```

---

### Task 3: Re-baseline (native + jars) from a pure-autotools rebuild + reproducibility probe

**Files:**
- Modify: `scilab/build-parity/baseline-autotools.json` (re-captured)
- Possibly modify: `scilab/build-parity/parity/fingerprint.py` (extend `_MANIFEST_VOLATILE` only if the probe finds a new volatile entry)

**Interfaces:**
- Consumes: the jar-aware capture/diff (Tasks 1–2).
- Produces: a jar-aware `baseline-autotools.json` — the reference every later task gates against.

This is a one-time, controller-executed, slow task (a full autotools rebuild). It does NOT write app code; it establishes the reference and empirically validates the normalize-list.

- [ ] **Step 1: Rebuild the jars via pure autotools** (the independent reference; from `scilab/`):

```bash
cd scilab && make -C modules/prebuildjava clean-java 2>/dev/null; \
  JAVA_HOME="$(sed -n 's/^S\["JAVA_HOME"\]="\(.*\)"$/\1/p' config.status)" \
  make -C modules/prebuildjava
ls modules/*/jar/*.jar | wc -l    # expect 24
```
Expected: 24 jars under `modules/*/jar/`.

- [ ] **Step 2: Run the reproducibility probe** (two identical autotools jar builds must fingerprint identically — this is what proves the MANIFEST normalize-list is complete):

```bash
cd scilab/build-parity
python3 -m parity.capture .. /tmp/jars-a.json capA
( cd ../modules/prebuildjava && make clean-java && \
  JAVA_HOME="$(sed -n 's/^S\["JAVA_HOME"\]="\(.*\)"$/\1/p' ../../config.status)" make )
python3 -m parity.capture .. /tmp/jars-b.json capB
python3 - <<'PY'
import json
a = json.load(open("/tmp/jars-a.json"))["jars"]
b = json.load(open("/tmp/jars-b.json"))["jars"]
diffs = []
for jar in sorted(set(a) | set(b)):
    ea, eb = a.get(jar, {}), b.get(jar, {})
    for e in sorted(set(ea) | set(eb)):
        if ea.get(e) != eb.get(e):
            diffs.append(f"{jar} :: {e}")
print("VOLATILE ENTRIES (must be empty):", diffs or "none")
PY
```
Expected: `VOLATILE ENTRIES (must be empty): none`.

- [ ] **Step 3: If the probe lists any volatile entry, make the normalize-list catch it — then re-run Step 2.** For a volatile `META-INF/MANIFEST.MF`, add the offending header to `_MANIFEST_VOLATILE` in `fingerprint.py`. For a volatile *non-manifest* entry (an embedded build stamp, e.g. a `*.properties` with a date), that is a real reproducibility fact — record it: extend `fingerprint_jar` to normalize that specific entry with a documented, narrowly-scoped rule (never a blanket skip). Re-run Step 2 until it reports `none`. Do NOT proceed with a non-empty probe — a volatile entry left in would make the gate flap.

- [ ] **Step 4: Re-capture the jar-aware baseline** from the pure-autotools tree (native + jars in one capture):

```bash
cd scilab/build-parity
python3 -m parity.capture .. baseline-autotools.json baseline-autotools
python3 - <<'PY'
import json
fp = json.load(open("baseline-autotools.json"))
print("dylibs:", len(fp["dylibs"]), "executables:", len(fp["executables"]), "jars:", len(fp["jars"]))
assert len(fp["jars"]) == 24, fp["jars"].keys()
PY
```
Expected: `dylibs: 68 executables: 2 jars: 24`.

- [ ] **Step 5: Confirm the current tree is PARITY OK against the new baseline** (self-check — same autotools tree):

```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/self.json self && \
  python3 -m parity.diff baseline-autotools.json /tmp/self.json
```
Expected: `PARITY OK`.

- [ ] **Step 6: Commit** (report the probe result in the message — it is the evidence the normalize-list is complete):

```bash
git add scilab/build-parity/baseline-autotools.json scilab/build-parity/parity/fingerprint.py
git commit -m "build-parity: re-baseline native + jars from a pure-autotools rebuild (24 jars; reproducibility probe clean)"
```

---

### Task 4: `scilab_java_bridge()` → `sci-java-all`, wired onto `drop-in-all`

**Files:**
- Create: `scilab/cmake/ScilabJava.cmake`
- Modify: `scilab/CMakeLists.txt` (include + call + wire onto `drop-in-all`)

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR` + `SCILAB_JAVA_HOME` (`ScilabToolchain.cmake`); the `drop-in-all` target (`CMakeLists.txt:47`).
- Produces: targets `sci-java-all` (builds the 24 jars) and `drop-in-jars`; `sci-java-all` rides `drop-in-all`.

- [ ] **Step 1: Create `scilab/cmake/ScilabJava.cmake`:**

```cmake
# scilab/cmake/ScilabJava.cmake — the CMake->Ant bridge (Stage 1f-b).
#
# ONE target wraps the existing prebuildjava Ant super-build: modules/prebuildjava/
# build.xml (default "all") hand-topo-sorts all 24 module jars and drives Ivy. The
# topo-sort / inter-module Java deps stay INSIDE Ant, unchanged (Stage 2's Maven
# reactor replaces them wholesale). This reproduces exactly how Makefile.incl.am's
# `java:` target runs it: bare `ant` in modules/prebuildjava with JAVA_HOME exported.
#
# ANT + the NEED_JAVA gate come from config.status (the configured tree's facts):
#   S["ANT"]="…/ant"           the configured Ant binary
#   S["NEED_JAVA_TRUE"]=""      automake conditional: "" when Java IS in this build,
#                               "#" when it is not.
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_ant_line REGEX "^S\\[\"ANT\"\\]=")
string(REGEX REPLACE "^S\\[\"ANT\"\\]=\"(.*)\"$" "\\1" SCILAB_ANT "${_sci_ant_line}")
file(STRINGS ${SCILAB_SOURCE_DIR}/config.status _sci_needjava_line REGEX "^S\\[\"NEED_JAVA_TRUE\"\\]=")
string(REGEX REPLACE "^S\\[\"NEED_JAVA_TRUE\"\\]=\"(.*)\"$" "\\1" SCILAB_NEED_JAVA "${_sci_needjava_line}")

function(scilab_java_bridge)
  add_custom_target(drop-in-jars COMMENT "The 24 Scilab module jars (Ant)")
  if(NOT SCILAB_NEED_JAVA STREQUAL "")
    # NEED_JAVA off (NEED_JAVA_TRUE is "#") — this configuration builds no jars.
    message(STATUS "Java disabled in this configuration (NEED_JAVA off) — jar bridge is a no-op")
    add_custom_target(sci-java-all COMMENT "Java disabled (NEED_JAVA off) — no-op")
    add_dependencies(drop-in-jars sci-java-all)
    return()
  endif()
  if(NOT SCILAB_ANT OR NOT EXISTS "${SCILAB_ANT}")
    message(FATAL_ERROR "config.status ANT unusable ('${SCILAB_ANT}') — cannot build the Java jars")
  endif()
  # Bare `ant` in modules/prebuildjava, JAVA_HOME exported — byte-equivalent to
  # Makefile.incl.am's `java:` recipe. No -D args: target-jar defaults to "jar" and
  # build_xcos/build_javasci resolve from the configure-substituted build.incl.xml.
  # Jars land in modules/<m>/jar/ (the same place `make` writes them) so the drop-in
  # is automatic — no copy step.
  add_custom_target(sci-java-all
    COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${SCILAB_JAVA_HOME} ${SCILAB_ANT}
    WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}/modules/prebuildjava
    USES_TERMINAL
    COMMENT "Building the 24 Scilab module jars via Ant (prebuildjava super-build)")
  add_dependencies(drop-in-jars sci-java-all)
  message(STATUS "SCILAB_ANT = ${SCILAB_ANT} (jar bridge armed)")
endfunction()
```

- [ ] **Step 2: Wire it into `scilab/CMakeLists.txt`.** After `include(cmake/ScilabAggregate.cmake)` (line 42) add `include(cmake/ScilabJava.cmake)`. At the end of the file (after the `scilab_executable(scilab-bin …)` block) add:

```cmake
# THE JAVA JARS (Stage 1f-b) — one target wrapping the prebuildjava Ant super-build
# (cmake/ScilabJava.cmake). Rides drop-in-all so `--target drop-in-all` now builds
# the whole native app AND the 24 jars. Jars land in modules/<m>/jar/ (drop-in is
# automatic). The topo-sort/Ivy stay inside Ant (Stage 2's Maven reactor's job).
scilab_java_bridge()
add_dependencies(drop-in-all sci-java-all)
```

- [ ] **Step 3: Configure + build the jars via CMake, forcing a real CMake-driven rebuild** (delete the jars first so a stale autotools jar can't masquerade as the CMake output — the jar analog of `rm -rf build-cmake`):

```bash
cd scilab
cmake -S . -B build-cmake >/dev/null
find modules -path '*/jar/*.jar' -delete
cmake --build build-cmake --target sci-java-all
ls modules/*/jar/*.jar | wc -l      # expect 24 — rebuilt by CMake's ant
```
Expected: 24 jars, rebuilt by the CMake-invoked Ant.

- [ ] **Step 4: Whole-tree parity gate (native + jars).**

```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/cand.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/cand.json
```
Expected: `PARITY OK` (68 dylibs + 2 executables + 24 jars all match the autotools baseline — the CMake-invoked Ant produced content-identical jars).

- [ ] **Step 5: Headless `-nw` JVM/jar smoke** (proves the jars load into a working JVM; `-nwni` would disable the JVM):

```bash
cd scilab && pkill -f scilab-bin 2>/dev/null; \
  timeout 120 ./bin/scilab -nw -nb -e "disp(1+1); exit(0)" >/tmp/nw-smoke.log 2>&1; echo "rc=$?"; \
  grep -icE 'ClassNotFound|NoClassDefFound|Exception|Could not' /tmp/nw-smoke.log; \
  pkill -f scilab-bin 2>/dev/null; true
```
Expected: `rc=0` and `0` jar/class-error lines (the log shows the compute result `2.`).

- [ ] **Step 6: Commit.**

```bash
git add scilab/cmake/ScilabJava.cmake scilab/CMakeLists.txt
git commit -m "cmake: scilab_java_bridge() — sci-java-all builds the 24 jars via Ant, on drop-in-all"
```

---

### Task 5: Finalize — GUI-launch acceptance, docs, CI

**Files:**
- Modify: `docs/design/build-cmake-driver.md`
- Modify: `.gitlab-ci.yml`

**Interfaces:**
- Consumes: `sci-java-all` (Task 4); the jar-aware harness (Tasks 1–3).
- Produces: none (finalization).

- [ ] **Step 1: Full clean build of the whole app incl. jars + the whole-tree gate** (the from-scratch proof):

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && \
  cmake --build build-cmake --target drop-in-all -j
cd build-parity && python3 -m parity.capture .. /tmp/final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json; echo "rc=$?"
```
Expected: `PARITY OK` (68 dylibs + 2 executables + 24 jars) + flag-facts rc=0.

- [ ] **Step 2: GUI-launch acceptance** — launch the full GUI (loads the full jar set), scan the startup log for jar errors, leave the instance open for the user (kill any stale one first, per the one-app-instance rule):

```bash
cd scilab && pkill -f scilab-bin 2>/dev/null; sleep 1
nohup ./bin/scilab > /tmp/gui-launch.log 2>&1 &
sleep 45     # let the GUI + jars initialize
grep -icE 'ClassNotFoundException|NoClassDefFoundError|java.lang.[A-Za-z]*Error|Could not load' /tmp/gui-launch.log
echo "scilab-bin running: $(pgrep -f scilab-bin | wc -l | tr -d ' ')"
```
Expected: `0` jar/class-error lines and `1` running `scilab-bin` (the app came up on the CMake-built jars and is left open for testing).

- [ ] **Step 3: Update `docs/design/build-cmake-driver.md`.** Add a "Java jars (Stage 1f-b)" subsection: `sci-java-all` wraps the `prebuildjava` Ant super-build (24 jars, topo-sort stays in Ant); the harness has a jar dimension (normalized content manifest, re-baselined from a pure-autotools rebuild, reproducibility-probe-guarded); `drop-in-all` now builds the native app + the jars; acceptance = parity incl. jars + `-nw` smoke + GUI launch. Move the "Stage 1f-b — the CMake→Ant bridge" bullet out of the Deferred list into scope. Update the title/status line to note the jars are now CMake-driven.

- [ ] **Step 4: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`'s `set -e` block, add a check that the bridge is wired (a deleted call would silently drop the jars from `drop-in-all`):

```bash
      # F. the Java bridge is wired (sci-java-all defined + on drop-in-all)
      grep -q 'scilab_java_bridge()' CMakeLists.txt
      grep -q 'add_dependencies(drop-in-all sci-java-all)' CMakeLists.txt
```
Update the `parity:cmake-drop-in` header comment to note it now also gates the 24 jars (automatic — they ride `drop-in-all` and the capture walks `modules/*/jar/`). No script change needed there (the runner already has Ant + the JDK).

- [ ] **Step 5: Commit.**

```bash
git add docs/design/build-cmake-driver.md .gitlab-ci.yml
git commit -m "cmake: Stage 1f-b complete — CMake drives the Java jar build (docs + CI)"
```

---

## Self-Review

**Spec coverage:** §4 bridge → Task 4; §5 jar harness dimension (fingerprint/normalize/capture/diff/probe/baseline) → Tasks 1–3; §6 gate & acceptance (parity + `-nw` smoke + GUI launch) → Tasks 4–5; §7 order → Tasks 1→5; §8 testing (fault-injection + probe + CI) → Tasks 1–2 tests + Task 3 probe + Task 5 CI; §9 risks (volatile entries → Task 3 probe; JDK → `SCILAB_JAVA_HOME`; Ivy env → same bare ant) all covered. No spec requirement without a task.

**Placeholder scan:** every code step shows real code; every command has an expected result. The only intentionally-conditional step is Task 3 Step 3 (extend the normalize-list *iff* the probe finds a volatile entry) — that is a real branch with concrete instructions, not a placeholder.

**Type consistency:** `normalize_manifest(text)->str` and `fingerprint_jar(path, opener)->dict[str,str]` are defined in Task 1 and consumed unchanged in Task 2 (`fingerprint_build`) and the tests; the `jars` fingerprint section is `{jar_relpath: {entry: hash}}` everywhere (capture, diff, tests, baseline check); CMake `sci-java-all` / `drop-in-jars` / `scilab_java_bridge()` names match across Task 4 and the CI check in Task 5.
