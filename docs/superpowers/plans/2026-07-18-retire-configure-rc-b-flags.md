# Retire-configure RC-b — CMake computes the flags + a derived per-TU flag gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-transcribed compiler flags in `cmake/ScilabModule.cmake` with policy CMake computes itself, and replace the flag gate's hand-written expectations with expectations derived from the autotools generated Makefiles and frozen into the parity baseline.

**Architecture:** A new `parity/makeflags.py` expands autotools' generated `Makefile` recipes to derive per-TU flag facts; those are captured into `baseline-autotools.json` and become `flagfacts_check`'s source of truth, replacing `_BASE` + two hand-maintained override tables. Then a live 4-TU divergence is closed, and `cmake/ScilabFlags.cmake` computes the four `SCI_*FLAGS` equivalents.

**Tech Stack:** Python 3 + pytest (harness), CMake (build), autotools generated Makefiles (the derivation source).

## Global Constraints

- **CMake COMPUTES the flag policy itself — it must never read a flag value out of `config.status`.** Reading configure's *evaluation* of the policy is the dependency RC-b removes. (The harness's `makeflags.py` reading generated **Makefiles** is a different thing: it derives the parity *expectation*, not the build's flags.)
- **ADDITIVE and rollback-free.** No edits to `configure.ac`, any `Makefile.am`/`Makefile.in`, `machine.h.in`, or `version.h.in`. Autotools must keep building exactly as it does.
- **REPRODUCE, don't improve.** Where autotools does something wrong (the `_CFLAGS` footgun), CMake reproduces it. The footgun *fix* is a separate, later stage and is explicitly out of scope here.
- **No AI-attribution trailers in any commit** — no `Co-Authored-By`, no "Generated with", no `Claude-Session`.
- **The gate must be seen to FAIL before it is trusted.** A guard that has not failed is not a guard.
- Derived fact keys are exactly `opt`, `wrapv`, `ndebug`, `std`, `openmp`. `min_macos` is **not** derived — see Task 3, Step 1.
- The full `build-parity` suite must stay green (121 passing at RC-b start, HEAD `cf92115789b`).

## File Structure

| File | Responsibility |
|---|---|
| `scilab/build-parity/parity/makeflags.py` | **new** — parse + expand autotools generated Makefiles; derive per-TU flag facts |
| `scilab/build-parity/tests/test_makeflags.py` | **new** — unit tests for the parser/expander |
| `scilab/build-parity/parity/capture.py` | **modify** — add the `tu_flag_facts` capture |
| `scilab/build-parity/parity/flagfacts_check.py` | **modify** — expectations come from the baseline; retire the hand tables |
| `scilab/build-parity/tests/test_flagfacts_check.py` | **modify** — retarget the locked-table tests at the derived source |
| `scilab/build-parity/baseline-autotools.json` | **modify** — armed with `tu_flag_facts` |
| `scilab/modules/{history_browser,preferences,types}/CMakeLists.txt` | **modify** — reproduce the footgun |
| `scilab/cmake/ScilabFlags.cmake` | **new** — computed flag policy |
| `scilab/cmake/ScilabModule.cmake` | **modify** — `_scilab_module_flag_env()` consumes the computed policy |
| `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml`, `scilab/build-parity/README.md` | **modify** — docs + CI |

---

### Task 1: The Makefile expander (pure functions)

**Files:**
- Create: `scilab/build-parity/parity/makeflags.py`
- Test: `scilab/build-parity/tests/test_makeflags.py`

**Interfaces:**
- Consumes: `parse_flag_facts(flagstring) -> {opt,wrapv,min_macos,openmp,ndebug,std}` from `parity/fingerprint.py`.
- Produces: `parse_make_defs(text) -> {var: raw_value}`; `expand_make_value(value, defs) -> str`; `makefile_tu_facts(text) -> {"defaults": {lang: facts}, "explicit": {relsrc: facts}}`.

- [ ] **Step 1: Write the failing tests.**

```python
# scilab/build-parity/tests/test_makeflags.py
from parity.makeflags import parse_make_defs, expand_make_value, makefile_tu_facts

def test_parse_defs_handles_continuations_and_append():
    defs = parse_make_defs("A = one \\\n    two\nB = x\nB += y\n")
    assert defs["A"] == "one     two"
    assert defs["B"] == "x y"

def test_parse_defs_skips_recipes_and_disabled_conditionals():
    # A recipe line is TAB-indented; automake's FALSE conditional becomes a '#' line.
    defs = parse_make_defs("REAL = yes\n\tFAKE = no\n#GONE = no\n")
    assert defs == {"REAL": "yes"}

def test_expand_resolves_nested_refs_and_unknowns():
    defs = {"AM_CFLAGS": "$(SCI_CFLAGS)", "SCI_CFLAGS": "-O2 -fwrapv"}
    assert expand_make_value("$(AM_CFLAGS)", defs) == "-O2 -fwrapv"
    assert expand_make_value("$(NOPE)", defs) == ""      # unknown expands empty, like make

def test_expand_survives_a_definition_cycle():
    assert "loop" not in expand_make_value("$(A)", {"A": "$(B)", "B": "$(A)"})

_MK = """\
CC = gcc -std=gnu23 -arch arm64
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
libfoo_la_CFLAGS =
libbar_la_CFLAGS = $(AM_CFLAGS) -Iextra

.c.lo:
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

src/libfoo_la-drop.lo: src/drop.c
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(libfoo_la_CFLAGS) $(CFLAGS) -c -o $@ src/drop.c

src/libbar_la-keep.lo: src/keep.c
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(libbar_la_CFLAGS) $(CFLAGS) -c -o $@ src/keep.c
"""

def test_tu_facts_default_comes_from_the_suffix_rule():
    facts = makefile_tu_facts(_MK)
    assert facts["defaults"]["c"] == {"opt": "O2", "wrapv": True, "min_macos": "11.0",
                                      "openmp": False, "ndebug": True, "std": "gnu23"}

def test_tu_facts_empty_per_target_cflags_is_the_footgun():
    # The whole recipe is expanded, so -std=gnu23 still arrives via $(CC) even though
    # every SCI_CFLAGS token is gone -- the real shape of a footgunned TU.
    drop = makefile_tu_facts(_MK)["explicit"]["src/drop.c"]
    assert drop["opt"] == "O0" and drop["wrapv"] is False and drop["ndebug"] is False
    assert drop["std"] == "gnu23"
    assert drop["min_macos"] is None

def test_tu_facts_per_target_cflags_that_reincludes_am_cflags_is_NOT_a_footgun():
    # The negative control: localization/spreadsheet shape. A recipe-text matcher
    # that looks for the literal "$(AM_CFLAGS)" gets this wrong -- it is reached
    # only through the per-target variable's own definition.
    keep = makefile_tu_facts(_MK)["explicit"]["src/keep.c"]
    assert keep["opt"] == "O2" and keep["wrapv"] is True
```

- [ ] **Step 2: Run them and watch them fail.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_makeflags.py -q
```
Expected: collection error — `No module named 'parity.makeflags'`.

- [ ] **Step 3: Implement `parity/makeflags.py`.**

```python
"""Derive per-TU compiler-flag facts from the AUTOTOOLS generated Makefiles.

The only place in the harness that reads autotools' *recipes* rather than its
outputs. It exists so the flag gate's expectation is DERIVED from autotools
instead of hand-written -- the hand-written form silently blessed a real
4-TU divergence (RC-b design doc S3.3/S3.4).

Whole-recipe expansion, deliberately: -std=gnu23 and -arch arm64 live in $(CC),
not in $(SCI_CFLAGS), so expanding only the flag variables would derive std=None
for every TU and mismatch CMake everywhere. Unknown variables expand empty, as
make does, so $(LIBTOOL)/$@/$< contribute nothing and parse_flag_facts simply
ignores the residue.
"""
import re
from parity.fingerprint import parse_flag_facts

_DEF = re.compile(r'^([A-Za-z_][A-Za-z0-9_]*)\s*(\+?=)\s*(.*)$')
_REF = re.compile(r'\$\(([A-Za-z_][A-Za-z0-9_]*)\)')
_EXPLICIT_RULE = re.compile(r'^(\S+\.(?:lo|o)):\s+(\S+\.(?:c|cpp|cxx|cc|f|F|f90))\s*$')
_SUFFIX_RULE = re.compile(r'^\.(c|cpp|cxx|cc|f|F|f90)\.(?:lo|o):\s*$')
_MAX_DEPTH = 25

LANG_BY_SUFFIX = {"c": "c", "cpp": "cxx", "cxx": "cxx", "cc": "cxx",
                  "f": "f", "F": "f", "f90": "f"}

def parse_make_defs(text):
    """{VAR: raw unexpanded value}, honoring `\\` continuations and `+=`.

    Skips TAB-indented recipe lines (a recipe is not a definition) and '#' lines
    (config.status renders a FALSE automake conditional by commenting the line out,
    so a '#' line is a definition that is genuinely not in effect)."""
    defs, lines, i = {}, text.splitlines(), 0
    while i < len(lines):
        line = lines[i]
        while line.endswith("\\") and i + 1 < len(lines):
            i += 1
            line = line[:-1] + " " + lines[i].strip()
        if line and not line.startswith(("\t", "#")):
            m = _DEF.match(line)
            if m:
                name, op, val = m.groups()
                defs[name] = (defs.get(name, "") + " " + val) if op == "+=" else val
        i += 1
    return defs

def expand_make_value(value, defs, _depth=0):
    """Recursively expand $(VAR); unknowns expand empty. Depth-capped so a cyclic
    definition returns a partial string instead of recursing forever."""
    if _depth > _MAX_DEPTH:
        return value
    return _REF.sub(lambda m: expand_make_value(defs.get(m.group(1), ""), defs, _depth + 1),
                    value)

def _recipe_after(lines, i):
    body = []
    j = i + 1
    while j < len(lines) and lines[j].startswith("\t"):
        body.append(lines[j].strip())
        j += 1
    return " ".join(body)

def makefile_tu_facts(text):
    """One generated Makefile -> {"defaults": {lang: facts}, "explicit": {relsrc: facts}}.

    "defaults" comes from the file's own suffix rules (.c.lo: etc) -- derived, not
    assumed, so a directory that redefines AM_CFLAGS (modules/dynamic_link/src/scripts)
    reports its real default. "explicit" holds every TU with a per-object rule, which
    is where automake's per-target _CFLAGS override lands.
    """
    defs, lines = parse_make_defs(text), text.splitlines()
    out = {"defaults": {}, "explicit": {}}
    for i, line in enumerate(lines):
        m = _SUFFIX_RULE.match(line)
        if m:
            recipe = _recipe_after(lines, i)
            if "--mode=compile" in recipe:
                out["defaults"][LANG_BY_SUFFIX[m.group(1)]] = \
                    parse_flag_facts(expand_make_value(recipe, defs))
            continue
        m = _EXPLICIT_RULE.match(line)
        if m:
            recipe = _recipe_after(lines, i)
            if "--mode=compile" in recipe:
                out["explicit"][m.group(2)] = \
                    parse_flag_facts(expand_make_value(recipe, defs))
    return out
```

- [ ] **Step 4: Run the tests to green.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_makeflags.py -q
```
Expected: `7 passed`.

- [ ] **Step 5: Commit.**

```bash
git add scilab/build-parity/parity/makeflags.py scilab/build-parity/tests/test_makeflags.py
git commit -m "build-parity: derive per-TU flag facts from autotools generated Makefiles"
```

---

### Task 2: Capture into the baseline + known-answer validation

**Files:**
- Modify: `scilab/build-parity/parity/capture.py`
- Test: `scilab/build-parity/tests/test_makeflags.py` (extend)

**Interfaces:**
- Consumes: `makefile_tu_facts` (Task 1).
- Produces: `capture_tu_flag_facts(source_root) -> {"defaults": {lang: facts}, "overrides": {relpath: facts}}`, and a `tu_flag_facts` key in the fingerprint.

- [ ] **Step 1: Write the failing test.**

```python
# append to scilab/build-parity/tests/test_makeflags.py
import os
from parity.capture import capture_tu_flag_facts

def test_capture_shape_and_override_selection(tmp_path):
    mk = """\
CC = gcc -std=gnu23
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
libfoo_la_CFLAGS =

.c.lo:
\t$(LIBTOOL) --mode=compile $(CC) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

src/libfoo_la-drop.lo: src/drop.c
\t$(LIBTOOL) --mode=compile $(CC) $(libfoo_la_CFLAGS) $(CFLAGS) -c -o $@ src/drop.c

src/libfoo_la-plain.lo: src/plain.c
\t$(LIBTOOL) --mode=compile $(CC) $(AM_CFLAGS) $(CFLAGS) -c -o $@ src/plain.c
"""
    d = tmp_path / "modules" / "m"
    d.mkdir(parents=True)
    (d / "Makefile").write_text(mk)
    got = capture_tu_flag_facts(str(tmp_path))
    assert got["defaults"]["c"]["opt"] == "O2"
    # ONLY the deviating TU is recorded; a TU matching the default is not.
    assert "modules/m/src/drop.c" in got["overrides"]
    assert "modules/m/src/plain.c" not in got["overrides"]
    assert got["overrides"]["modules/m/src/drop.c"]["opt"] == "O0"
```

- [ ] **Step 2: Run it and watch it fail.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_makeflags.py -q
```
Expected: `ImportError: cannot import name 'capture_tu_flag_facts'`.

- [ ] **Step 3: Implement the capture in `parity/capture.py`.**

Add the import at the top (`from parity.makeflags import makefile_tu_facts, LANG_BY_SUFFIX`), then:

```python
# Derived per-TU flag expectation (RC-b). Stored as a tree-wide default plus ONLY
# the TUs that deviate -- ~40 entries instead of ~3600, and it maps directly onto
# how flagfacts_check asks the question ("what is expected of THIS file?").
#
# FROZEN ON PURPOSE: RC-e deletes the generated Makefiles this is derived from, so
# the committed baseline is what lets the autotools-derived truth outlive autotools.
_DEFAULT_DEVIATION_LIMIT = 8

def capture_tu_flag_facts(source_root):
    modules = os.path.join(source_root, "modules")
    per_module, defaults_seen = {}, {}
    for name in sorted(os.listdir(modules)) if os.path.isdir(modules) else []:
        mk = os.path.join(modules, name, "Makefile")
        if not os.path.isfile(mk):
            continue
        with open(mk, errors="replace") as f:
            facts = makefile_tu_facts(f.read())
        per_module[name] = facts
        for lang, d in facts["defaults"].items():
            defaults_seen.setdefault(lang, []).append(json.dumps(d, sort_keys=True))

    # The tree-wide default is the MODAL per-module suffix-rule result, not a
    # hand-picked representative -- picking one module is the same "representative
    # TU" weakness that makes the global `flags` row a non-gate.
    defaults = {}
    for lang, seen in defaults_seen.items():
        modal = max(set(seen), key=seen.count)
        deviants = len(seen) - seen.count(modal)
        if deviants > _DEFAULT_DEVIATION_LIMIT:
            raise RuntimeError(
                f"{lang}: {deviants} modules deviate from the modal default -- "
                "'the tree-wide default' is not a real notion here; investigate "
                "before trusting this capture")
        defaults[lang] = json.loads(modal)

    overrides = {}
    for name, facts in per_module.items():
        for relsrc, tu in facts["explicit"].items():
            lang = LANG_BY_SUFFIX.get(relsrc.rsplit(".", 1)[-1])
            if lang and tu != defaults.get(lang):
                overrides[f"modules/{name}/{relsrc}"] = tu
    return {"defaults": defaults, "overrides": overrides}
```

Wire it into `fingerprint_build` alongside the other dimensions, keyed `tu_flag_facts`, and extend the capture CLI's summary line with `, {len(...['overrides'])} flag-override TUs`.

- [ ] **Step 4: Run the test to green.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_makeflags.py -q
```
Expected: `8 passed`.

- [ ] **Step 5: Validate against the known answers — the gate on the gate.**

```bash
cd scilab/build-parity && python3 - <<'PY'
from parity.capture import capture_tu_flag_facts
got = capture_tu_flag_facts("..")
ov, dflt = got["overrides"], got["defaults"]
print("defaults:", {k: (v["opt"], v["wrapv"], v["std"]) for k, v in dflt.items()})
print("override TUs:", len(ov))

FOOTGUN = ("parameters", "windows_tools", "string", "history_browser", "types", "preferences")
FORTRAN = ("colnew.f", "sszer.f", "dtensbs.f", "blkfct.f", "symfct.f", "ordmmd.f")
NEGATIVE = ("localization", "spreadsheet")

for m in FOOTGUN:
    hits = [k for k in ov if k.startswith(f"modules/{m}/") and k.endswith(".c")]
    bad = [k for k in hits if ov[k]["opt"] != "O0" or ov[k]["wrapv"]]
    print(f"  footgun {m:16s}: {len(hits):3d} TUs recorded, {len(bad)} not O0/no-wrapv")
for f in FORTRAN:
    hits = [k for k in ov if k.endswith("/" + f)]
    ok = hits and all(ov[k]["opt"] == "O0" and ov[k]["wrapv"] for k in hits)
    print(f"  fortran {f:12s}: {'OK' if ok else 'MISSING/WRONG'} {hits}")
for m in NEGATIVE:
    hits = [k for k in ov if k.startswith(f"modules/{m}/")]
    print(f"  NEGATIVE {m:12s}: {len(hits)} entries (MUST be 0) {hits[:3]}")
PY
```
Expected: every footgun module reports TUs recorded with `0 not O0/no-wrapv`; all six Fortran files `OK`; **both negative controls report 0 entries**. Do not proceed until all three groups pass — a parser that flags `localization` is reproducing the exact false positive this design was built to avoid.

- [ ] **Step 6: Commit.**

```bash
git add scilab/build-parity/parity/capture.py scilab/build-parity/tests/test_makeflags.py
git commit -m "build-parity: capture derived per-TU flag facts into the fingerprint"
```

---

### Task 3: Re-point `flagfacts_check` at the derived facts

**Files:**
- Modify: `scilab/build-parity/parity/flagfacts_check.py`, `scilab/build-parity/tests/test_flagfacts_check.py`
- Modify: `scilab/build-parity/baseline-autotools.json` (arm it)

**Interfaces:**
- Consumes: the `tu_flag_facts` fingerprint section (Task 2).
- Produces: `check_flag_facts(compile_commands_path, derived, source_root) -> [mismatch strings]`.

- [ ] **Step 1: Rewrite the expectation source.** Replace `_BASE`, `DEFAULT_EXPECTED_BY_SUFFIX`, `FILE_EXPECTED_OVERRIDES`, `DIR_EXPECTED_OVERRIDES`, and `_override_for` with:

```python
# Facts DERIVED from autotools (RC-b). min_macos is deliberately NOT among them:
# a footgunned TU's autotools recipe drops -mmacosx-version-min entirely (deriving
# min_macos=None), while CMAKE_OSX_DEPLOYMENT_TARGET stamps 11.0 on every CMake TU.
# That difference was reviewed and ACCEPTED before RC-b -- the baseline's host-default
# stamp on those objects was a non-portable artifact of the dropped flag, and a folded
# object's min-version is set at aggregate link time anyway. So min_macos is asserted
# as a CMake-side INVARIANT rather than derived, and stays guarded everywhere.
DERIVED_KEYS = ("opt", "wrapv", "ndebug", "std", "openmp")
INVARIANT = {"min_macos": "11.0"}

def expected_for(rel_path, suffix_lang, derived):
    """The expected facts for one TU: its derived override if it has one, else the
    derived tree-wide default for its language, plus the CMake-side invariant."""
    facts = derived["overrides"].get(rel_path) or derived["defaults"].get(suffix_lang)
    if facts is None:
        return None
    return {**{k: facts[k] for k in DERIVED_KEYS if k in facts}, **INVARIANT}
```

and make `check_flag_facts` take `derived` + `source_root`, computing each entry's `rel_path` as `os.path.relpath(e["file"], source_root)` and its language via `LANG_BY_SUFFIX`. A TU whose language yields no expectation is reported as unchecked (the existing `unchecked_suffixes` contract, preserved).

The `__main__` block gains the baseline path: `python -m parity.flagfacts_check <compile_commands.json> <baseline.json> <source_root>`.

- [ ] **Step 2: Arm the baseline.**

```bash
cd scilab/build-parity && python3 - <<'PY'
import json
from parity.capture import capture_tu_flag_facts
b = json.load(open("baseline-autotools.json"))
assert "tu_flag_facts" not in b, "baseline already armed"
b["tu_flag_facts"] = capture_tu_flag_facts("..")
json.dump(b, open("baseline-autotools.json", "w"), indent=2, sort_keys=True)
print("armed:", len(b["tu_flag_facts"]["overrides"]), "override TUs")
PY
```

- [ ] **Step 3: Run the gate on today's tree — IT MUST FAIL.**

```bash
cd scilab/build-parity && python3 -m parity.flagfacts_check \
  ../build-cmake/compile_commands.json baseline-autotools.json ..; echo "rc=$?"
```
Expected: **rc=1**, naming exactly these four files with `opt`/`wrapv` mismatches:
```
modules/history_browser/sci_gateway/c/sci_browsehistory.c
modules/history_browser/src/nogui/CommandHistory_Wrap_Fake.c
modules/preferences/src/cpp/getScilabPreference.c
modules/types/src/jni/getScilabVariable_wrap.c
```
This is the task's whole point: the hand-written gate passed this tree (rc=0) while these four TUs were genuinely divergent. If the derived gate does not fail here, it is not deriving anything — stop and investigate rather than proceeding.

- [ ] **Step 4: Retarget the table-locking tests.** `tests/test_flagfacts_check.py` currently locks the three `DIR_EXPECTED_OVERRIDES` entries (`:116-126`). Replace that test with one asserting the same three directories are present **in the derived overrides**, so the behavior stays locked while its source changes:

```python
def test_derived_overrides_cover_the_known_footgun_dirs():
    import json
    from parity.flagfacts_check import expected_for, DERIVED_KEYS
    derived = json.load(open("baseline-autotools.json"))["tu_flag_facts"]
    for probe in ("modules/parameters/src/c/parameters.c",
                  "modules/windows_tools/src/nowindows_tools.c"):
        exp = expected_for(probe, "c", derived)
        assert exp["opt"] == "O0" and exp["wrapv"] is False, probe

def test_a_tu_with_no_override_gets_the_derived_default():
    import json
    from parity.flagfacts_check import expected_for
    derived = json.load(open("baseline-autotools.json"))["tu_flag_facts"]
    exp = expected_for("modules/core/src/c/nowhere.c", "c", derived)
    assert exp["opt"] == "O2" and exp["wrapv"] is True and exp["min_macos"] == "11.0"
```

Preserve the retired tables' explanatory comments as a block comment above `DERIVED_KEYS`, recording *why* the recorded facts look as they do (the `if IS_MACOSX` gfortran workaround; the `_CFLAGS` footgun) — the code goes, the knowledge does not.

- [ ] **Step 5: Fault-inject the armed gate.**

```bash
cd scilab/build-parity && python3 - <<'PY'
import json
cc = json.load(open("../build-cmake/compile_commands.json"))
for e in cc:
    if e["file"].endswith("/modules/core/src/c/csignal.c"):
        e.pop("arguments", None)
        e["command"] = (e.get("command") or "") .replace("-O2", "-O0")
json.dump(cc, open("/tmp/rcb-mut.json", "w"))
PY
python3 -m parity.flagfacts_check /tmp/rcb-mut.json baseline-autotools.json ..; echo "rc=$? (expect 1)"
```
Expected: rc=1 naming `csignal.c: flag fact opt='O0' (want 'O2')`.

- [ ] **Step 6: Commit.**

```bash
git add scilab/build-parity/parity/flagfacts_check.py \
        scilab/build-parity/tests/test_flagfacts_check.py \
        scilab/build-parity/baseline-autotools.json
git commit -m "build-parity: flag gate expectations derived from autotools, not hand-written"
```

---

### Task 4: Close the 4-TU divergence

**Files:**
- Modify: `scilab/modules/history_browser/CMakeLists.txt`, `scilab/modules/preferences/CMakeLists.txt`, `scilab/modules/types/CMakeLists.txt`

**Interfaces:**
- Consumes: the derived gate (Task 3) as the arbiter.
- Produces: a green gate.

- [ ] **Step 1: Read the existing mechanism.** `modules/parameters/CMakeLists.txt:41` shows the established form — `scilab_object_module(... C_FLAGS_OVERRIDE -std=gnu23)`. That is exactly the shape the footgun produces: `$(CC)` still supplies `-std=gnu23`, and every `SCI_CFLAGS` token is gone.

- [ ] **Step 2: Apply the same override to the three modules,** each with a comment naming the `Makefile.am` line it reproduces:

```cmake
# REPRODUCES the automake _CFLAGS footgun (Makefile.am:50 sets
# libscihistory_browser_la_CFLAGS to empty, which REPLACES $(AM_CFLAGS) wholesale),
# so these TUs compile with none of SCI_CFLAGS -- only $(CC)'s own -std=gnu23.
# Reproduce, do not improve: restoring -O2 -fwrapv here is a separate stage.
C_FLAGS_OVERRIDE -std=gnu23
```

`history_browser` covers both `sci_browsehistory.c` and `CommandHistory_Wrap_Fake.c`; `preferences` covers `getScilabPreference.c` (the `_algo_` target, whose `_CFLAGS` references the never-assigned `libscipreferences_la_CFLAGS`); `types` covers `getScilabVariable_wrap.c` (the `_java_` target).

- [ ] **Step 3: Reconfigure and re-run the gate.**

```bash
cd scilab && cmake -S . -B build-cmake >/dev/null 2>&1 && cd build-parity && \
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
echo "rc=$? (expect 0)"
```
Expected: **rc=0** — the divergence that opened this stage is closed, proven by the gate that found it.

- [ ] **Step 4: Commit.**

```bash
git add scilab/modules/history_browser/CMakeLists.txt \
        scilab/modules/preferences/CMakeLists.txt scilab/modules/types/CMakeLists.txt
git commit -m "cmake: reproduce the _CFLAGS footgun in history_browser, preferences, types"
```

---

### Task 5: `ScilabFlags.cmake` — computed policy

**Files:**
- Create: `scilab/cmake/ScilabFlags.cmake`
- Modify: `scilab/cmake/ScilabModule.cmake` (`_scilab_module_flag_env()`), `scilab/CMakeLists.txt` (include it)

**Interfaces:**
- Consumes: `CMAKE_OSX_DEPLOYMENT_TARGET`, the compiler id.
- Produces: `SCILAB_C_FLAGS`, `SCILAB_CXX_FLAGS`, `SCILAB_Fortran_FLAGS`, `SCILAB_LINK_FLAGS` as CMake lists.

- [ ] **Step 1: Create `scilab/cmake/ScilabFlags.cmake`.**

```cmake
# scilab/cmake/ScilabFlags.cmake -- the compiler-flag POLICY, computed (retire-configure RC-b).
#
# Replaces the hand-transcribed literal lists that used to live in
# _scilab_module_flag_env(). CMake states the policy itself; it never reads
# config.status. The parity gate (parity/flagfacts_check.py, expectations DERIVED
# from the autotools Makefiles) is what proves the computed values still match.
#
# TRAP, learned the hard way: -std= is NOT part of SCI_CFLAGS/SCI_CXXFLAGS. autotools
# carries it in the COMPILER variable -- `CC = gcc -std=gnu23 -arch arm64`,
# `CXX = g++ -arch arm64 -std=c++17`. A port that mirrors only SCI_*FLAGS silently
# drops the language standard. It is set explicitly below.

option(SCILAB_ENABLE_DEBUG "Build unoptimized with full debug info (configure's --enable-debug)" OFF)

# configure.ac:467,562,588,671,757 -- five sites, one policy:
#   enable_debug ? "-O0 -g3" : "-DNDEBUG -g1 -O2 -fwrapv"
# -fwrapv and NOT -fno-strict-overflow: clang expands the latter to -fwrapv-pointer
# too and blows up compile time on template-heavy TUs (configure.ac:671,757).
if(SCILAB_ENABLE_DEBUG)
  set(_codegen -O0 -g3)
else()
  set(_codegen -DNDEBUG -g1 -O2 -fwrapv)
endif()

# Derived, not baked: the deployment target is already CMake's own.
set(_min_macos -mmacosx-version-min=${CMAKE_OSX_DEPLOYMENT_TARGET})

# configure.ac:682,768,573,599 -- bug 3131.
set(_compiler_c -fno-stack-protector)
set(_compiler_cxx -fno-stack-protector)

# configure.ac:674,760 (C) and :565,591 (C++). Fortran gets NO warning flags:
# WARNING_FFLAGS has zero assignment sites anywhere in configure.ac/m4.
set(_warn_c -Wall -Wpedantic)
set(_warn_cxx -Wall -Wpedantic)
# configure.ac:2358-2360 -- unconditional and C-ONLY; C++ never gets these.
list(APPEND _warn_c -Werror=implicit -Werror=incompatible-pointer-types)

set(SCILAB_C_FLAGS       -std=gnu23  ${_codegen} ${_min_macos} ${_compiler_c}   ${_warn_c})
set(SCILAB_CXX_FLAGS     -std=c++17  ${_codegen} ${_min_macos} ${_compiler_cxx} ${_warn_cxx})
set(SCILAB_Fortran_FLAGS             ${_codegen} ${_min_macos})
set(SCILAB_LINK_FLAGS    ${_min_macos})

# NOT implemented, on purpose -- each verified rather than assumed:
#  * SCI_CPPFLAGS is a PHANTOM: Makefile.incl.am:25 and Makefile.am:27 both do
#    AM_CPPFLAGS = $(SCI_CPPFLAGS), but nothing assigns or AC_SUBSTs it anywhere
#    (verified absent from config.status). It always expands empty.
#  * WARNING_FFLAGS / DEBUG_LDFLAGS / WARNING_LDFLAGS / SSE_LDFLAGS /
#    BACKTRACE_LDFLAGS: zero assignment sites anywhere. Dead everywhere.
#  * COMPILER_FFLAGS: dead HERE but not dead everywhere -- assigned only on the
#    Intel-compiler path (m4/intel_compiler.m4:28,30), which this build never takes.
#  * SSE_*FLAGS: i*86-linux-gnu only (configure.ac:869-875). BACKTRACE_*FLAGS: gated
#    on a glibc-backtrace probe (m4/backtrace.m4) that fails on macOS, contributing
#    no -rdynamic here.
```

- [ ] **Step 2: Include it from the driver.** In `scilab/CMakeLists.txt`, immediately after `include(cmake/ScilabMachineHeader.cmake)`:

```cmake
# RC-b -- the compiler-flag policy, computed in CMake rather than transcribed.
include(cmake/ScilabFlags.cmake)
```

- [ ] **Step 3: Consume it in `_scilab_module_flag_env()`.** Replace the three hardcoded `set(_cflags …)` / `set(_cxxflags …)` / `set(_fflags …)` literal lists (`ScilabModule.cmake:146-160`) with:

```cmake
  # RC-b: the policy is computed in cmake/ScilabFlags.cmake, not transcribed here.
  set(_cflags   ${SCILAB_C_FLAGS})
  set(_cxxflags ${SCILAB_CXX_FLAGS})
  set(_fflags   ${SCILAB_Fortran_FLAGS})
```

Update the file header's "transcribed from the CONFIGURED autotools build" note to say the flags are now computed and gated, leaving the rest of its provenance statement (includes, defines, link options, naming) intact — those are still transcribed and RC-b does not change them.

- [ ] **Step 4: Reconfigure and prove nothing moved.**

```bash
cd scilab && cmake -S . -B build-cmake >/dev/null 2>&1 && cd build-parity && \
python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json .. \
  && echo "GATE rc=0" && python3 -m pytest -q | tail -1
```
Expected: `GATE rc=0`, suite green. The computed flags must produce byte-identical compile lines to the transcribed ones for all 3600 TUs; any drift is named by file and fact.

- [ ] **Step 5: Commit.**

```bash
git add scilab/cmake/ScilabFlags.cmake scilab/cmake/ScilabModule.cmake scilab/CMakeLists.txt
git commit -m "cmake: compute the compiler-flag policy instead of transcribing it"
```

---

### Task 6: From-scratch gate, docs + CI (CONTROLLER-executed — long build)

**Files:**
- Modify: `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml`, `scilab/build-parity/README.md`

- [ ] **Step 1: From-scratch whole-tree gate.**

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && cmake --build build-cmake --target drop-in-all -j
cd build-parity && python3 -m parity.capture .. /tmp/rcb-final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/rcb-final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
echo "rc=$?"
```
Expected: `PARITY OK` (68 dylibs + 2 executables + 24 jars + the semantic header) and the flag gate rc=0.

- [ ] **Step 2: Update `docs/design/build-cmake-driver.md`.** Add a "Compiler flags — computed in CMake (RC-b)" section: the four `SCI_*FLAGS` equivalents are computed in `ScilabFlags.cmake`, not transcribed; the gate's expectations are derived from the autotools Makefiles and frozen in the baseline (frozen because RC-e deletes those Makefiles); the `-std=`-lives-in-`$CC` trap; the phantom `SCI_CPPFLAGS` and the dead-everywhere vs dead-here ingredient distinction. Replace the existing "The `_CFLAGS`-replaces-`AM_CFLAGS` footgun" bullet's "a handful of dirs … and 6 Fortran files" with the measured figure — **6 modules / 33 C TUs**, of which 4 were silently divergent until RC-b — and keep the pointer to the deferred fix, including its `-fwrapv` hardening rationale.

- [ ] **Step 3: Update `scilab/build-parity/README.md`.** Add `tu_flag_facts` to the dimension table, and record what the fault injection measured: the global `flags` row judges each language by **one arbitrary TU**, and was shown to miss a 3570-TU regression, so it is a coarse cross-build record rather than a gate; `flagfacts_check` is the real per-TU gate and now derives its expectations rather than asserting hand-written ones.

- [ ] **Step 4: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`, extend the checks:

```bash
      # I. Retire-configure RC-b is wired: the flag policy is computed, not transcribed.
      grep -q 'include(cmake/ScilabFlags.cmake)' CMakeLists.txt
      grep -q 'SCILAB_C_FLAGS' cmake/ScilabModule.cmake
```
and update the native job's `flagfacts_check` invocation to the new three-argument form.

- [ ] **Step 5: Commit.**

```bash
git add docs/design/build-cmake-driver.md .gitlab-ci.yml scilab/build-parity/README.md
git commit -m "build-parity: RC-b complete — derived flag gate, computed policy (docs + CI)"
```

---

## Self-Review

**Spec coverage:** design §5.1 (derived per-TU expectation, whole-recipe expansion, frozen in the baseline) → Tasks 1–3; §5.2 (close the divergence) → Task 4; §5.3 (computed policy, `-std=` trap, dead-ingredient distinctions) → Task 5; §6.1 (the gate must fail on the known bug) → Task 3 Step 3; §6.2 (known-answer validation incl. both negative controls) → Task 2 Step 5; §6.3–6.4 (green after the fix, from-scratch parity) → Task 4 Step 3 + Task 6 Step 1; §6.5 (fault injection on the armed gate) → Task 3 Step 5; §8 (parser unit tests) → Task 1. No spec requirement lacks a task.

**Placeholder scan:** every step carries runnable code or a concrete command with expected output. The one figure the plan cannot pre-compute is the override-TU count produced by Task 2's capture; Step 5 prints it and validates its *composition* against the three known-answer groups, which is the property that matters.

**Type consistency:** `parse_flag_facts` returns the six-key dict used unchanged throughout; `makefile_tu_facts` returns `{"defaults","explicit"}` (Task 1) and is consumed under exactly those keys in Task 2; `capture_tu_flag_facts` returns `{"defaults","overrides"}` (Task 2) and is consumed under exactly those keys by `expected_for` in Task 3; `LANG_BY_SUFFIX` is defined once in `makeflags.py` and imported by both `capture.py` and `flagfacts_check.py`; the `SCILAB_{C,CXX,Fortran}_FLAGS` names set in Task 5 Step 1 are the names consumed in Step 3.
