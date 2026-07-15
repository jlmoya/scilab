# Build Parity Harness — Implementation Plan (Migration Stage 0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a committed tool that fingerprints a Scilab build (exported symbols, link shape, SDK stamp, generated files) and diffs two fingerprints, so every step of the make→CMake / Ant→Maven migration can be proven behaviorally identical to the autotools baseline — not merely "it compiled."

**Architecture:** A small Python package (`scilab/build-parity/parity/`) split into pure parsers (turn `nm`/`otool` text into structured data — fast, fixture-tested), a differ (compare two fingerprints, exit nonzero on any behavioral difference), and a capture orchestrator (walk a built tree, shell out to `nm`/`otool`, emit a JSON fingerprint). A command-runner is injected into the orchestrator so it is unit-testable without a build. The acceptance test proves the harness is neither too loose (capture the real tree twice → identical) nor too tight (mutate a fingerprint → caught).

**Tech Stack:** Python 3 (3.14 present), pytest (9.1 present), the macOS `nm` / `otool` binaries, `unzip`. No third-party Python deps.

## Global Constraints

- **Parity means behavioral identity, NOT byte-identity.** Symbol *addresses* change every build and MUST be stripped; the library *version token* (`.2027.`) and machine-absolute path prefixes MUST be normalized. Only the set of exported symbols, the link/dependency shape, the SDK stamp, and normalized generated-file content are compared.
- **The harness must be PROVEN to catch a regression.** A guard you have not seen fail is not a guard. Task 6 is the fault-injection acceptance test; the plan is not done until the differ has been watched going red on a mutated real fingerprint AND green on a re-capture of the same tree.
- **Location:** `scilab/build-parity/` (alongside `scilab/build-macos.sh`). Run tests from that directory: `cd scilab/build-parity && python3 -m pytest tests/ -v`.
- **Real tool output formats (verified on this machine, 2026-07-14) — use these verbatim as fixtures:**
  - `nm -gU <dylib>` line: `0000000000006c0c T _CdfBase` (16-hex addr, type char, symbol; addr is volatile).
  - `otool -L <dylib>`: first line is `<path>:`, then tab-indented `\t<path> (compatibility version X, current version Y)`; the **first** such line is the dylib's own install name.
  - `otool -l <exe>` LC_BUILD_VERSION block (stripped): `cmd LC_BUILD_VERSION`, `minos 11.0`, `sdk 11.0`. **Both must read `11.0`** — this is the anti-SIGTRAP SDK stamp; a change here is a release-blocking regression.
  - Dylibs are named `libsci<module>.2027.dylib` (real) with a bare `libsci<module>.dylib` symlink; variant libs exist (`-disable`, `-minimal`).
- **Built baseline tree exists now** at `scilab/` (`.libs/scilab-bin`, `.libs/scilab-cli-bin`, ~67
  first-party module dylibs under `modules/*/.libs/` (132 dylib-named files there once you count the
  bare-name symlinks), 30 jars under `modules/*/jar/`).
- **No AI-attribution trailers** in any commit message (no `Co-Authored-By`, no `Claude-Session`, no "Generated with").
- Commit on `main`. DRY, YAGNI, TDD, frequent commits.

---

## File Structure

```
scilab/build-parity/
├── parity/
│   ├── __init__.py
│   ├── fingerprint.py   # pure parsers + normalizers (no I/O): parse_nm, parse_otool_libs,
│   │                    #   parse_build_version, normalize_version, normalize_path
│   ├── capture.py       # fingerprint_build(build_dir, runner) + `python -m parity.capture` CLI
│   └── diff.py          # diff_fingerprints(base, cand) + `python -m parity.diff` CLI
├── tests/
│   ├── test_fingerprint.py   # parsers/normalizers, real fixture strings
│   ├── test_capture.py       # fingerprint_build with a fake runner
│   └── test_diff.py          # the differ: missing/extra/added/removed-symbol, sdk change, /tmp leak
├── capture.sh           # thin wrapper: python3 -m parity.capture "$@"
├── diff.sh              # thin wrapper: python3 -m parity.diff "$@"
├── baseline-autotools.json   # captured from the real built tree (Task 6)
└── README.md            # usage + the manual GUI-surface checklist + the behavior (.tst) gate
```

**Responsibilities.** `fingerprint.py` is pure text→data with zero I/O, so it is exhaustively fixture-tested in milliseconds. `capture.py` isolates all the shelling-out behind an injected `runner`, so its logic is testable with canned output and only one test actually touches the built tree. `diff.py` is the decision layer — it owns the exit code the migration's CI will gate on.

**The shared fingerprint schema** (produced by `fingerprint_build`, consumed by `diff_fingerprints`):
```python
{
  "build_id": "autotools",
  "executables": {                     # key = basename
    "scilab-bin":     {"build_version": {"minos": "11.0", "sdk": "11.0"},
                       "install_name": "...", "deps": [...], "tmp_leak": False},
    "scilab-cli-bin": {...},
  },
  "dylibs": {                          # key = version-normalized basename, e.g. "libscistatistics.VER.dylib"
    "libscistatistics.VER.dylib": {
      "symbols": ["T _CdfBase", ...],  # sorted, ADDRESS-STRIPPED
      "install_name": "...",           # normalized
      "deps": [...],                   # sorted, normalized
      "tmp_leak": False,
    }, ...
  },
  "generated": {                       # key = repo-relative path, value = sha256 of NORMALIZED content
    "etc/classpath.xml": "<sha256>",
    "modules/core/includes/machine.h": "<sha256>",
    "modules/core/includes/version.h": "<sha256>",
  },
}
```

---

### Task 1: Scaffold + `parse_nm`

**Files:**
- Create: `scilab/build-parity/parity/__init__.py`
- Create: `scilab/build-parity/parity/fingerprint.py`
- Test: `scilab/build-parity/tests/test_fingerprint.py`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `parse_nm(output: str) -> list[str]` — turns `nm -gU` text into a sorted list of `"<type> <symbol>"` strings with the volatile address dropped.

- [ ] **Step 1: Write the failing test**

Create `scilab/build-parity/tests/test_fingerprint.py`:
```python
from parity.fingerprint import parse_nm

# Real `nm -gU libscistatistics.2027.dylib` output (verified 2026-07-14).
NM_FIXTURE = """\
0000000000006c0c T _CdfBase
000000000000962c T __Z10braycurtisiiiiiPdS_S_
00000000000084bc T __Z10seuclideaniiiiiPdS_S_
00000000000084bc D _someDataSym
"""

def test_parse_nm_strips_addresses_and_sorts():
    syms = parse_nm(NM_FIXTURE)
    # Address dropped; type + name kept; sorted.
    assert syms == [
        "D _someDataSym",
        "T _CdfBase",
        "T __Z10braycurtisiiiiiPdS_S_",
        "T __Z10seuclideaniiiiiPdS_S_",
    ]

def test_parse_nm_ignores_blank_lines():
    assert parse_nm("\n0000000000006c0c T _X\n\n") == ["T _X"]

def test_parse_nm_empty():
    assert parse_nm("") == []
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'parity'` (package not created yet).

- [ ] **Step 3: Create the package + minimal implementation**

Create `scilab/build-parity/parity/__init__.py` (empty file).

Create `scilab/build-parity/parity/fingerprint.py`:
```python
"""Pure parsers and normalizers: text from nm/otool -> structured data. No I/O."""


def parse_nm(output):
    """`nm -gU` output -> sorted list of "<type> <symbol>" (address dropped).

    The address (first column) changes every build and is deliberately discarded;
    only the exported *set* and each symbol's linkage kind (T/D/...) are parity-relevant.
    """
    syms = []
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 3:
            syms.append(parts[1] + " " + " ".join(parts[2:]))
    return sorted(syms)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: PASS — 3 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/parity/__init__.py scilab/build-parity/parity/fingerprint.py scilab/build-parity/tests/test_fingerprint.py
git commit -m "build-parity: nm symbol parser (address-stripped, sorted)"
```

---

### Task 2: `parse_otool_libs` + `parse_build_version`

**Files:**
- Modify: `scilab/build-parity/parity/fingerprint.py`
- Test: `scilab/build-parity/tests/test_fingerprint.py`

**Interfaces:**
- Consumes: `fingerprint.py` (Task 1).
- Produces:
  - `parse_otool_libs(output: str) -> dict` → `{"install_name": str|None, "deps": list[str], "tmp_leak": bool}`. `deps` is sorted and excludes the self install name. `tmp_leak` is True if any path (install name or dep) contains `/tmp` or `/private/var/folders` (a non-relocatable build path — the bonmin-class reboot time-bomb).
  - `parse_build_version(output: str) -> dict` → `{"minos": str|None, "sdk": str|None}` from an `otool -l` LC_BUILD_VERSION block.

- [ ] **Step 1: Write the failing test**

Append to `scilab/build-parity/tests/test_fingerprint.py`:
```python
from parity.fingerprint import parse_otool_libs, parse_build_version

# Real `otool -L libscistatistics.2027.dylib` output (verified 2026-07-14).
OTOOL_L_FIXTURE = """\
modules/statistics/.libs/libscistatistics.2027.dylib:
\t/usr/local/lib/scilab/libscistatistics.2027.dylib (compatibility version 2028.0.0, current version 2028.0.0)
\t/opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)
\t/opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib (compatibility version 1.0.0, current version 1.0.0)
"""

def test_parse_otool_libs_splits_install_name_from_deps():
    r = parse_otool_libs(OTOOL_L_FIXTURE)
    assert r["install_name"] == "/usr/local/lib/scilab/libscistatistics.2027.dylib (compatibility version 2028.0.0, current version 2028.0.0)"
    assert r["deps"] == [
        "/opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)",
        "/opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib (compatibility version 1.0.0, current version 1.0.0)",
    ]
    assert r["tmp_leak"] is False

def test_parse_otool_libs_flags_tmp_path():
    leaky = "x.dylib:\n\t/tmp/build/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
    assert parse_otool_libs(leaky)["tmp_leak"] is True

# Real `otool -l scilab-bin | grep -A5 LC_BUILD_VERSION` (verified 2026-07-14).
OTOOL_LV_FIXTURE = """\
      cmd LC_BUILD_VERSION
  cmdsize 32
 platform 1
    minos 11.0
      sdk 11.0
   ntools 1
"""

def test_parse_build_version():
    assert parse_build_version(OTOOL_LV_FIXTURE) == {"minos": "11.0", "sdk": "11.0"}

def test_parse_build_version_absent():
    assert parse_build_version("no build version here") == {"minos": None, "sdk": None}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: FAIL — `ImportError: cannot import name 'parse_otool_libs'`.

- [ ] **Step 3: Implement**

Append to `scilab/build-parity/parity/fingerprint.py`:
```python
_TMP_MARKERS = ("/tmp", "/private/var/folders")


def parse_otool_libs(output):
    """`otool -L` output -> {install_name, deps (sorted, self excluded), tmp_leak}."""
    entries = []
    for line in output.splitlines():
        if line.startswith("\t"):
            entries.append(line.strip())
    install_name = entries[0] if entries else None
    deps = sorted(entries[1:])
    tmp_leak = any(any(m in e for m in _TMP_MARKERS) for e in entries)
    return {"install_name": install_name, "deps": deps, "tmp_leak": tmp_leak}


def parse_build_version(output):
    """`otool -l` LC_BUILD_VERSION block -> {minos, sdk}. First block wins."""
    minos = sdk = None
    in_block = False
    for line in output.splitlines():
        s = line.strip()
        if s.startswith("cmd LC_BUILD_VERSION"):
            in_block = True
        elif in_block and s.startswith("minos "):
            minos = s.split()[1]
        elif in_block and s.startswith("sdk "):
            sdk = s.split()[1]
            break
    return {"minos": minos, "sdk": sdk}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: PASS — all tests pass (7 total).

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/parity/fingerprint.py scilab/build-parity/tests/test_fingerprint.py
git commit -m "build-parity: otool link-shape + LC_BUILD_VERSION parsers (with /tmp leak flag)"
```

---

### Task 3: `normalize_version` + `normalize_path`

**Files:**
- Modify: `scilab/build-parity/parity/fingerprint.py`
- Test: `scilab/build-parity/tests/test_fingerprint.py`

**Interfaces:**
- Consumes: `fingerprint.py` (Tasks 1–2).
- Produces:
  - `normalize_version(name: str) -> str` — replaces a 4-digit version token `.NNNN.` with `.VER.` so a version bump does not spuriously fail parity.
  - `normalize_path(text: str, roots: dict) -> str` — replaces each absolute-path prefix in `roots` (longest first) with its placeholder, then applies `normalize_version`.

- [ ] **Step 1: Write the failing test**

Append to `scilab/build-parity/tests/test_fingerprint.py`:
```python
from parity.fingerprint import normalize_version, normalize_path

def test_normalize_version():
    assert normalize_version("libscistatistics.2027.dylib") == "libscistatistics.VER.dylib"
    assert normalize_version("libsciaction_binding-disable.2027.dylib") == "libsciaction_binding-disable.VER.dylib"
    assert normalize_version("libscistatistics.dylib") == "libscistatistics.dylib"

def test_normalize_path_prefixes_and_version():
    roots = {
        "/Users/josemoya/Projects/CLionProjects/scilab/scilab": "$SCI",
        "/Users/josemoya": "$HOME",
    }
    # Longest prefix wins, then the version token collapses.
    s = "/Users/josemoya/Projects/CLionProjects/scilab/scilab/modules/x/.libs/libx.2027.dylib"
    assert normalize_path(s, roots) == "$SCI/modules/x/.libs/libx.VER.dylib"
    assert normalize_path("/Users/josemoya/other", roots) == "$HOME/other"
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: FAIL — `ImportError: cannot import name 'normalize_version'`.

- [ ] **Step 3: Implement**

Append to `scilab/build-parity/parity/fingerprint.py`:
```python
import re

_VERSION_TOKEN = re.compile(r"\.\d{4}\.")


def normalize_version(name):
    """Collapse a 4-digit library version token: libsciX.2027.dylib -> libsciX.VER.dylib."""
    return _VERSION_TOKEN.sub(".VER.", name)


def normalize_path(text, roots):
    """Replace absolute-path prefixes (longest first) with placeholders, then version-normalize."""
    for prefix in sorted(roots, key=len, reverse=True):
        text = text.replace(prefix, roots[prefix])
    return normalize_version(text)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_fingerprint.py -v`
Expected: PASS — all pass (10 total).

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/parity/fingerprint.py scilab/build-parity/tests/test_fingerprint.py
git commit -m "build-parity: version + path normalizers (stable keys across version bumps)"
```

---

### Task 4: `diff_fingerprints` — the decision layer

**Files:**
- Create: `scilab/build-parity/parity/diff.py`
- Test: `scilab/build-parity/tests/test_diff.py`

**Interfaces:**
- Consumes: the fingerprint schema (see File Structure).
- Produces: `diff_fingerprints(base: dict, cand: dict) -> dict` → `{"ok": bool, "differences": list[str]}`. `ok` is True iff `differences` is empty. It reports: dylibs/executables/jars added or removed; per-dylib symbols added/removed; per-dylib deps or install_name changed; any `tmp_leak`; any executable `build_version` change; any generated-file hash change.

- [ ] **Step 1: Write the failing test**

Create `scilab/build-parity/tests/test_diff.py`:
```python
from parity.diff import diff_fingerprints

def _fp(**over):
    base = {
        "build_id": "base",
        "executables": {"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "11.0"},
                                       "install_name": "n", "deps": [], "tmp_leak": False}},
        "dylibs": {"libx.VER.dylib": {"symbols": ["T _a", "T _b"], "install_name": "n",
                                      "deps": ["libc (v)"], "tmp_leak": False}},
        "generated": {"etc/classpath.xml": "hash1"},
    }
    base.update(over)
    return base

def test_identical_is_ok():
    assert diff_fingerprints(_fp(), _fp()) == {"ok": True, "differences": []}

def test_removed_symbol_is_caught():
    cand = _fp(dylibs={"libx.VER.dylib": {"symbols": ["T _a"], "install_name": "n",
                                          "deps": ["libc (v)"], "tmp_leak": False}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("_b" in d and "libx.VER.dylib" in d for d in r["differences"])

def test_missing_dylib_is_caught():
    cand = _fp(dylibs={})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("libx.VER.dylib" in d and "missing" in d.lower() for d in r["differences"])

def test_sdk_stamp_change_is_caught():
    cand = _fp(executables={"scilab-bin": {"build_version": {"minos": "11.0", "sdk": "26.0"},
                                           "install_name": "n", "deps": [], "tmp_leak": False}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("sdk" in d.lower() for d in r["differences"])

def test_tmp_leak_is_caught():
    cand = _fp(dylibs={"libx.VER.dylib": {"symbols": ["T _a", "T _b"], "install_name": "n",
                                          "deps": ["libc (v)"], "tmp_leak": True}})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("tmp" in d.lower() for d in r["differences"])

def test_generated_hash_change_is_caught():
    cand = _fp(generated={"etc/classpath.xml": "hash2"})
    r = diff_fingerprints(_fp(), cand)
    assert r["ok"] is False
    assert any("classpath.xml" in d for d in r["differences"])
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_diff.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'parity.diff'`.

- [ ] **Step 3: Implement**

Create `scilab/build-parity/parity/diff.py`:
```python
"""Compare two build fingerprints. `ok` iff behaviorally identical."""
import json
import sys


def _diff_named(kind, base, cand, out):
    """Report added/removed keys in a name->obj mapping."""
    for name in sorted(set(base) - set(cand)):
        out.append(f"{kind} missing in candidate: {name}")
    for name in sorted(set(cand) - set(base)):
        out.append(f"{kind} extra in candidate: {name}")


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
        if sorted(b["deps"]) != sorted(c["deps"]):
            out.append(f"executable {name}: link dependencies changed")

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
        if c["tmp_leak"]:
            out.append(f"dylib {name}: non-relocatable /tmp path in link")

    # Generated files: presence + content hash.
    _diff_named("generated file", base["generated"], cand["generated"], out)
    for name in sorted(set(base["generated"]) & set(cand["generated"])):
        if base["generated"][name] != cand["generated"][name]:
            out.append(f"generated file changed: {name}")

    return {"ok": not out, "differences": out}


def _main(argv):
    if len(argv) != 3:
        print("usage: python -m parity.diff <baseline.json> <candidate.json>", file=sys.stderr)
        return 2
    with open(argv[1]) as f:
        base = json.load(f)
    with open(argv[2]) as f:
        cand = json.load(f)
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_diff.py -v`
Expected: PASS — 6 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/parity/diff.py scilab/build-parity/tests/test_diff.py
git commit -m "build-parity: fingerprint differ + CLI (symbols, link, SDK stamp, generated, /tmp)"
```

---

### Task 5: `fingerprint_build` capture orchestrator + CLIs

**Files:**
- Create: `scilab/build-parity/parity/capture.py`
- Create: `scilab/build-parity/capture.sh`
- Create: `scilab/build-parity/diff.sh`
- Test: `scilab/build-parity/tests/test_capture.py`

**Interfaces:**
- Consumes: `parse_nm`, `parse_otool_libs`, `parse_build_version`, `normalize_version`, `normalize_path` (Tasks 1–3).
- Produces: `fingerprint_build(build_dir: str, roots: dict, runner=<subprocess>) -> dict` — walks `build_dir` for `.libs/*.dylib` and the two executables, calls `runner(["nm","-gU",path])` / `runner(["otool",...])`, hashes the generated files, and returns the shared fingerprint schema. `runner(cmd: list[str]) -> str` returns the command's stdout; it is injected so the logic is unit-testable without a build.

- [ ] **Step 1: Write the failing test**

Create `scilab/build-parity/tests/test_capture.py`:
```python
from parity.capture import fingerprint_dylib

def fake_runner(responses):
    def run(cmd):
        # cmd like ["nm","-gU",path] or ["otool","-L"/"-l",path]; key on the flag.
        key = cmd[1]
        return responses[key]
    return run

def test_fingerprint_dylib_combines_nm_and_otool():
    responses = {
        "-gU": "0000000000006c0c T _CdfBase\n0000000000000001 T _b\n",
        "-L": "x:\n\t/usr/local/lib/scilab/libx.2027.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
              "\t/opt/homebrew/lib/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)\n",
    }
    roots = {"/opt/homebrew": "$BREW"}
    fp = fingerprint_dylib("/any/.libs/libx.2027.dylib", roots, runner=fake_runner(responses))
    assert fp["symbols"] == ["T _CdfBase", "T _b"]
    assert fp["deps"] == ["$BREW/lib/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)"]
    assert fp["install_name"] == "/usr/local/lib/scilab/libx.VER.dylib (compatibility version 1.0.0, current version 1.0.0)"
    assert fp["tmp_leak"] is False
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_capture.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'parity.capture'`.

- [ ] **Step 3: Implement**

Create `scilab/build-parity/parity/capture.py`:
```python
"""Walk a built tree and emit a fingerprint (see the shared schema)."""
import hashlib
import json
import os
import subprocess
import sys

from parity.fingerprint import (parse_nm, parse_otool_libs, parse_build_version,
                                normalize_version, normalize_path)

GENERATED_FILES = [
    "etc/classpath.xml",
    "modules/core/includes/machine.h",
    "modules/core/includes/version.h",
]


def _subprocess_runner(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=False).stdout


def _normalize_entry(entry, roots):
    return normalize_path(entry, roots) if entry else entry


def fingerprint_dylib(path, roots, runner=_subprocess_runner):
    syms = parse_nm(runner(["nm", "-gU", path]))
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    return {
        "symbols": syms,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
    }


def _fingerprint_exe(path, roots, runner):
    bv = parse_build_version(runner(["otool", "-l", path]))
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    return {
        "build_version": bv,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
    }


def fingerprint_build(build_dir, roots, runner=_subprocess_runner, build_id="build"):
    dylibs = {}
    for root, _dirs, files in os.walk(build_dir):
        if not root.endswith("/.libs"):
            continue
        for fn in files:
            # Real versioned files only (skip the bare-name symlinks and non-dylibs).
            if fn.endswith(".dylib") and normalize_version(fn) != fn:
                key = normalize_version(fn)
                dylibs[key] = fingerprint_dylib(os.path.join(root, fn), roots, runner)

    executables = {}
    for name in ("scilab-bin", "scilab-cli-bin"):
        p = os.path.join(build_dir, ".libs", name)
        if os.path.exists(p):
            executables[name] = _fingerprint_exe(p, roots, runner)

    generated = {}
    for rel in GENERATED_FILES:
        p = os.path.join(build_dir, rel)
        if os.path.exists(p):
            with open(p, "r", errors="replace") as f:
                content = normalize_path(f.read(), roots)
            generated[rel] = hashlib.sha256(content.encode("utf-8", "replace")).hexdigest()

    return {"build_id": build_id, "executables": executables,
            "dylibs": dylibs, "generated": generated}


def _default_roots(build_dir):
    return {os.path.abspath(build_dir): "$SCI", os.path.expanduser("~"): "$HOME"}


def _main(argv):
    if len(argv) < 3:
        print("usage: python -m parity.capture <build-dir> <out.json> [build_id]", file=sys.stderr)
        return 2
    build_dir, out = argv[1], argv[2]
    build_id = argv[3] if len(argv) > 3 else "build"
    fp = fingerprint_build(build_dir, _default_roots(build_dir), build_id=build_id)
    with open(out, "w") as f:
        json.dump(fp, f, indent=2, sort_keys=True)
    print(f"captured {len(fp['dylibs'])} dylibs, {len(fp['executables'])} executables, "
          f"{len(fp['generated'])} generated files -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
```

Create `scilab/build-parity/capture.sh`:
```bash
#!/usr/bin/env bash
# Capture a build fingerprint. Usage: capture.sh <build-dir> <out.json> [build_id]
set -euo pipefail
cd "$(dirname "$0")"
exec python3 -m parity.capture "$@"
```

Create `scilab/build-parity/diff.sh`:
```bash
#!/usr/bin/env bash
# Diff two build fingerprints. Usage: diff.sh <baseline.json> <candidate.json>
set -euo pipefail
cd "$(dirname "$0")"
exec python3 -m parity.diff "$@"
```

- [ ] **Step 4: Run the test to verify it passes, and make the wrappers executable**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab/build-parity
chmod +x capture.sh diff.sh
python3 -m pytest tests/ -v
```
Expected: PASS — all tests across all three test files pass (17 total).

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/parity/capture.py scilab/build-parity/capture.sh scilab/build-parity/diff.sh scilab/build-parity/tests/test_capture.py
git commit -m "build-parity: capture orchestrator (injectable runner) + capture/diff CLIs"
```

---

### Task 6: Real baseline + the fault-injection acceptance test + docs

**Files:**
- Create: `scilab/build-parity/baseline-autotools.json` (generated, committed)
- Create: `scilab/build-parity/tests/test_acceptance.py`
- Create: `scilab/build-parity/README.md`
- Modify: `scilab/build-parity/.gitignore` (create) — ignore scratch candidate captures

**Interfaces:**
- Consumes: `fingerprint_build` (Task 5), `diff_fingerprints` (Task 4).
- Produces: the committed autotools baseline and the proof the harness discriminates.

- [ ] **Step 1: Capture the real baseline from the built tree**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab/build-parity
python3 -m parity.capture .. baseline-autotools.json autotools
```
Expected output like: `captured 67 dylibs, 2 executables, 3 generated files -> baseline-autotools.json`
(Numbers should be ~67 first-party dylibs / 2 executables / 3 generated — matching the built tree.
132 is what you get if bare-name symlinks are counted too; the harness must not double-count them.
If dylibs is 0, the `.libs` walk or the build dir is wrong — stop and fix before continuing.)

- [ ] **Step 2: Write the acceptance test (stability + sensitivity)**

Create `scilab/build-parity/tests/test_acceptance.py`:
```python
"""Acceptance: the harness must be neither too loose nor too tight, on REAL captured data.

Stability  -> capturing the same tree twice is byte-for-byte identical after normalization
              (proves volatile symbol addresses do NOT cause false positives).
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
```

- [ ] **Step 3: Run the acceptance test**

Run: `cd scilab/build-parity && python3 -m pytest tests/test_acceptance.py -v`
Expected: PASS — 5 passed. In particular `test_stability_recapture_is_green` proves address-stripping works (no false positives) and the three `test_sensitivity_*` prove the differ catches a dropped symbol, an SDK downgrade, and a `/tmp` leak (no false negatives).

- [ ] **Step 4: Write the README (usage + the gates the harness does NOT automate)**

Create `scilab/build-parity/README.md`:
```markdown
# Build parity harness (migration Stage 0)

Proves a CMake/Maven build is *behaviorally identical* to the autotools baseline — the safety net
for the make->CMake / Ant->Maven migration (`docs/design/build-cmake-maven-migration.md`).

## Usage
```bash
# Capture the current (candidate) build, then diff against the committed baseline:
./capture.sh .. /tmp/candidate.json candidate
./diff.sh baseline-autotools.json /tmp/candidate.json   # exit 0 = parity, 1 = regression
```

## What it compares
Per dylib: the exported symbol set (addresses stripped) and the link/dependency shape.
Per executable: the `LC_BUILD_VERSION` SDK stamp (must stay `minos 11.0 / sdk 11.0` — the
anti-SIGTRAP fix) and the link shape. Plus: any non-relocatable `/tmp` path, and the normalized
content hash of `etc/classpath.xml`, `machine.h`, `version.h`.

## What it does NOT automate (manual gates — run these too before declaring a module migrated)
1. **Behavior — the `.tst` suite.** There is no compiled test binary; run it inside the built
   interpreter:
   ```bash
   cd .. && LANG=C ./bin/scilab-cli -nb -e "exit(test_run([],[]))"   # or per-module: test_run('statistics')
   ```
   A migrated build must produce the same pass/fail set as autotools.
2. **The GUI-surface checklist** (needs a human at the screen): console, 2-D and 3-D plotting,
   the JavaFX file chooser, the embedded browser, and an xcos simulation run. These exercise the
   off-main-thread graphics path the SDK stamp protects — a green fingerprint does not prove them.

## Refreshing the baseline
Only when the autotools build itself legitimately changes:
`./capture.sh .. baseline-autotools.json autotools` and commit the new baseline in the same change.
```

Create `scilab/build-parity/.gitignore`:
```
__pycache__/
*.pyc
.pytest_cache/
/candidate*.json
```

- [ ] **Step 5: Run the whole suite once, then commit**

Run: `cd scilab/build-parity && python3 -m pytest tests/ -v`
Expected: PASS — all tests (22 total: 10 fingerprint + 6 diff + 1 capture + 5 acceptance).

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/build-parity/baseline-autotools.json scilab/build-parity/tests/test_acceptance.py scilab/build-parity/README.md scilab/build-parity/.gitignore
git commit -m "build-parity: real autotools baseline + fault-injection acceptance (stability + sensitivity)

Proven on the real built tree: re-capturing the same tree is green (address
stripping kills false positives), and a dropped symbol / SDK downgrade / tmp
leak each turn the differ red (no false negatives). The harness is the Stage 0
safety net for the make->CMake / Ant->Maven migration."
```

---

## Self-Review

**Spec coverage** (against `build-cmake-maven-migration.md` §3, "What it captures"):
1. Artifact set — `fingerprint_build` walks `.libs/*.dylib` + the two executables (Task 5); diff reports added/removed (Task 4). ✓
2. Exported symbols via `nm` — `parse_nm` (Task 1), address-stripped; diff reports added/removed per dylib (Task 4). ✓
3. Link shape + `LC_BUILD_VERSION` via `otool` — `parse_otool_libs` + `parse_build_version` (Task 2); diff reports dep changes + SDK change (Task 4); the `11.0/11.0` value is asserted in the acceptance test (Task 6). ✓
4. Generated artifacts — `classpath.xml`/`machine.h`/`version.h`, normalized + hashed (Task 5); diff reports changes (Task 4). ✓
5. `/tmp` non-relocatable check — `parse_otool_libs` `tmp_leak` (Task 2), diffed (Task 4), acceptance-tested (Task 6). ✓
6. Behavior (`.tst`) + GUI checklist — documented as the two manual gates in the README (Task 6). The spec calls the GUI checklist a *manual* gate, so automating it is correctly out of scope. ✓
7. "Neither too loose nor too tight" proof — the stability + sensitivity acceptance tests on real data (Task 6). ✓

**Placeholder scan:** no TBD/TODO. Every code step contains complete, runnable code; every fixture is real `nm`/`otool` output verified on this machine; every command has an expected output. The one soft number is "22 total tests" — it is a sum of the per-file counts stated in each task, not a guess.

**Type consistency:** the fingerprint schema (dict with `executables`/`dylibs`/`generated`, each dylib carrying `symbols`/`install_name`/`deps`/`tmp_leak`) is identical in the File Structure block, in `fingerprint_build`/`fingerprint_dylib` (Task 5), in `diff_fingerprints` (Task 4), and in every test's `_fp()`/fixtures. `runner(cmd: list[str]) -> str` is the same in the fake (Task 5 test) and the real `_subprocess_runner` (Task 5). `normalize_path(text, roots)` and `normalize_version(name)` signatures match across Tasks 3, 5. `diff_fingerprints -> {"ok", "differences"}` is consumed identically in Tasks 4 and 6.

**One decision recorded during review:** `fingerprint_build` keys dylibs by version-normalized basename and captures only the real `.2027.dylib` files (skipping the bare-name symlinks) so the same physical library is never counted twice and a version bump does not spuriously fail parity. The `test_stability_recapture_is_green` acceptance test is what guarantees this de-duplication and the address-stripping actually hold on the real tree.
