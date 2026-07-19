# Retire-configure RC-d — CMake drives the macros build — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CMake builds Scilab's ~3,516 macro `.bin` files by running the just-built interpreter, byte-identically to `make macros` — gated by a macro parity dimension strengthened from path-presence to path→content.

**Architecture:** Strengthen the gate first (the `.bin` output is measurably deterministic, so content hashing is viable), then add a `macros` target that reproduces `Makefile.am:246-249`'s invocation exactly — with one deliberate divergence: it fails loudly.

**Tech Stack:** CMake custom target, Python 3 + pytest (harness), the existing `buildmacros.sce` + `genlib` machinery (invoked, not reimplemented).

## Global Constraints

- **ADDITIVE and rollback-free.** No edits to `configure.ac`, any `Makefile.am`, `buildmacros.sce`, any module's `macros/buildmacros.sce`, or `sci_genlib.cpp`. `make macros` must keep working identically.
- **REPRODUCE the artifact, not the habit.** The `.bin` output must be byte-identical to `make macros`'. But `make`'s `-` recipe prefix — which makes it **ignore the exit status** — is deliberately **not** reproduced: CMake never had it, and that swallow-the-error behavior already shipped one real bug (the `rc=231` incident). Document the divergence where the target lives.
- **No AI-attribution trailers in any commit.**
- **The gate must be seen to FAIL before it is trusted** — both ways: a deleted `.bin` and a corrupted one.
- The full `build-parity` suite must stay green (176 passing at RC-d start, HEAD `b7b01da4b4c`).

## The trap that will silently invalidate your acceptance test

**`genlib` is incremental.** It skips any `.sci` whose md5 matches the previous `lib` manifest when the `.bin` still exists (`modules/io/sci_gateway/cpp/sci_genlib.cpp:263-279`). So a second build run over an already-built tree is a **no-op** that produces identical output trivially and proves nothing.

Every comparison in this plan therefore deletes **both** `.bin` **and** `lib` files first:

```bash
find modules -path '*/macros/*' -name '*.bin' -delete
find modules -path '*/macros/*' -name 'lib'   -delete
```

If a step's output looks suspiciously fast or suspiciously perfect, check that you did this.

## File Structure

| File | Responsibility |
|---|---|
| `scilab/build-parity/parity/capture.py` | **modify** — macro manifest: paths → paths+content |
| `scilab/build-parity/tests/test_capture.py` | **modify** — the presence-only semantics are asserted there; strengthen |
| `scilab/build-parity/baseline-autotools.json` | **modify** — re-arm the one manifest hash |
| `scilab/cmake/ScilabMacros.cmake` | **new** — the `macros` target |
| `scilab/CMakeLists.txt` | **modify** — include it |
| `scilab/build-parity/README.md`, `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml` | **modify** — docs + CI |

---

### Task 1: Strengthen the macro gate to content

**Files:**
- Modify: `scilab/build-parity/parity/capture.py`, `scilab/build-parity/tests/test_capture.py`, `scilab/build-parity/baseline-autotools.json`

**Interfaces:**
- Consumes: the existing `MACRO_BIN_MANIFEST_KEY` single-entry shape in the `generated` map.
- Produces: the same key, same shape, but its hash now moves on content as well as presence.

- [ ] **Step 1: Read the current implementation before changing it.**

`parity/capture.py` around lines 72-76 (the key + its comment) and 310-318 (the walk that appends to `macro_bins`) and 348-349 (the manifest hash). The comment currently states the presence-only property explicitly — you are changing that property, so the comment must change with it.

- [ ] **Step 2: Write the failing tests.**

```python
# in scilab/build-parity/tests/test_capture.py, alongside the existing
# test_macro_bin_manifest_changes_when_a_bin_file_goes_missing

def test_macro_bin_manifest_changes_when_a_bin_file_CONTENT_changes(tmp_path):
    """RC-d: the manifest gates CONTENT, not just presence.

    Before RC-d this hashed a sorted path list, so a .bin present at the right
    path with wrong bytes was invisible -- precisely the failure a migration of
    the macro compiler risks. The .bin output is deterministic (measured: two
    independent full rebuilds, 0 of 3516 files differing), so content hashing is
    strict rather than flaky.
    """
    from parity.capture import fingerprint_build, MACRO_BIN_MANIFEST_KEY
    mac = tmp_path / "modules" / "core" / "macros"
    mac.mkdir(parents=True)
    (tmp_path / ".libs").mkdir()
    bin_file = mac / "who_user.bin"

    bin_file.write_bytes(b"AST-BYTES-ONE")
    before = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]

    bin_file.write_bytes(b"AST-BYTES-TWO")   # same path, different bytes
    after = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]

    assert before != after, "a .bin's content changed but the manifest hash did not"


def test_macro_bin_manifest_still_changes_when_a_bin_goes_missing(tmp_path):
    """The presence property the old gate had must NOT regress -- strengthening a
    gate should be strictly additive."""
    from parity.capture import fingerprint_build, MACRO_BIN_MANIFEST_KEY
    mac = tmp_path / "modules" / "core" / "macros"
    mac.mkdir(parents=True)
    (tmp_path / ".libs").mkdir()
    (mac / "a.bin").write_bytes(b"x")
    (mac / "b.bin").write_bytes(b"y")
    before = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]
    (mac / "b.bin").unlink()
    after = fingerprint_build(str(tmp_path), {}, build_id="b")["generated"][MACRO_BIN_MANIFEST_KEY]
    assert before != after, "a .bin vanished but the manifest hash did not change"
```

- [ ] **Step 3: Run them and watch the content test fail.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_capture.py -q -k macro_bin
```
Expected: the *content* test FAILS (the manifest hashes paths only); the *missing* test passes (that property already exists).

- [ ] **Step 4: Change the manifest to hash content.** In the `.bin` walk, replace the path-only append with path+content, and update the manifest comment:

```python
                if fn.endswith(".bin"):
                    p = os.path.join(root, fn)
                    rel = os.path.relpath(p, build_dir).replace(os.sep, "/")
                    # BINARY read -- .bin files are serialized ASTs, not text. (The
                    # text readers elsewhere in this file pin encoding="utf-8"; that
                    # is the wrong tool here and would corrupt the hash.)
                    with open(p, "rb") as f:
                        macro_bins.append(rel + "\0" + hashlib.sha256(f.read()).hexdigest())
```

and the key's comment:

```python
# Key for the macro .bin manifest entry in the "generated" map: one hash over
# every compiled macro's PATH **and CONTENT** (RC-d). It was path-only through
# RC-c -- enough to catch a module's macros vanishing (the rc=231 shape), but
# blind to a .bin present at the right path with wrong bytes, which is exactly
# what migrating the macro compiler risks.
#
# Content hashing is strict rather than flaky because .bin output is
# deterministic -- measured before RC-d: two independent full rebuilds (.bin AND
# lib deleted between, since genlib is incremental) produced 0 of 3516 files
# differing, and both reproduced the pre-existing on-disk state. If that ever
# drifts, investigate it; do not weaken this back to presence.
MACRO_BIN_MANIFEST_KEY = "macros/*.bin (manifest)"
```

- [ ] **Step 5: Run the tests to green, then the full suite.**

```bash
cd scilab/build-parity && python3 -m pytest tests/test_capture.py -q && python3 -m pytest -q | tail -1
```
Expected: both macro tests pass. Other tests that construct `.bin` fixtures may need real bytes rather than empty files — update them to match; do **not** weaken what they assert.

- [ ] **Step 6: Re-arm the baseline's one manifest hash.**

```bash
cd scilab/build-parity && python3 - <<'PY'
import json
from parity.capture import fingerprint_build, _default_roots, MACRO_BIN_MANIFEST_KEY
fresh = fingerprint_build("..", _default_roots(".."), build_id="arm")
b = json.load(open("baseline-autotools.json"))
old = b["generated"].get(MACRO_BIN_MANIFEST_KEY)
new = fresh["generated"][MACRO_BIN_MANIFEST_KEY]
b["generated"][MACRO_BIN_MANIFEST_KEY] = new
json.dump(b, open("baseline-autotools.json", "w"), indent=2, sort_keys=True)
print(f"manifest re-armed\n  old (path-only): {old}\n  new (path+content): {new}")
print("changed:", old != new)
PY
```
Expected: exactly one hash changes; `changed: True` (the semantics changed, so the value must).

- [ ] **Step 7: Fault-inject the armed gate — both properties.**

```bash
cd scilab/build-parity && python3 -m parity.capture .. /tmp/rcd.json cand >/dev/null
echo "--- clean (expect PARITY OK, rc=0) ---"
python3 -m parity.diff baseline-autotools.json /tmp/rcd.json; echo "rc=$?"

echo "--- CONTENT: corrupt one .bin in place ---"
B=../modules/core/macros/who_user.bin; cp $B /tmp/who_user.bin.bak
printf 'CORRUPT' >> $B
python3 -m parity.capture .. /tmp/rcd-corrupt.json cand >/dev/null
python3 -m parity.diff baseline-autotools.json /tmp/rcd-corrupt.json | tail -2; echo "rc=${PIPESTATUS[0]} (expect 1)"
cp /tmp/who_user.bin.bak $B

echo "--- PRESENCE: remove one .bin ---"
mv $B /tmp/who_user.bin.moved
python3 -m parity.capture .. /tmp/rcd-missing.json cand >/dev/null
python3 -m parity.diff baseline-autotools.json /tmp/rcd-missing.json | tail -2; echo "rc=${PIPESTATUS[0]} (expect 1)"
mv /tmp/who_user.bin.moved $B

echo "--- restored (expect PARITY OK, rc=0) ---"
python3 -m parity.capture .. /tmp/rcd-restored.json cand >/dev/null
python3 -m parity.diff baseline-autotools.json /tmp/rcd-restored.json; echo "rc=$?"
```
Expected: clean → `PARITY OK` rc=0; corrupted → `PARITY FAILED` naming `macros/*.bin (manifest)`, rc=1; missing → same, rc=1; restored → `PARITY OK` rc=0. **All four matter** — a gate that fails on everything is as useless as one that fails on nothing.

- [ ] **Step 8: Commit.**

```bash
git add scilab/build-parity/parity/capture.py scilab/build-parity/tests/test_capture.py \
        scilab/build-parity/baseline-autotools.json
git commit -m "build-parity: macro gate hashes .bin content, not just presence"
```

---

### Task 2: The CMake `macros` target

**Files:**
- Create: `scilab/cmake/ScilabMacros.cmake`
- Modify: `scilab/CMakeLists.txt`

**Interfaces:**
- Consumes: `SCILAB_SOURCE_DIR`; the built `bin/scilab-cli` wrapper and `.libs/scilab-cli-bin`.
- Produces: a `macros` target.

- [ ] **Step 1: Create `scilab/cmake/ScilabMacros.cmake`.**

```cmake
# scilab/cmake/ScilabMacros.cmake -- the macros build (retire-configure RC-d).
#
# Scilab's ~3,516 macro .bin files are produced by RUNNING the just-built
# interpreter over modules/functions/scripts/buildmacros/buildmacros.sce, which
# loops the modules getmodules() reports and calls the compiled genlib() builtin.
# This target INVOKES that existing machinery; it does not reimplement it.
#
# Scope comes from etc/modules.xml (getmodules() -> ConfigVariable::getModuleList()
# -> FuncManager::AppendModules(), modules/functions_manager/src/cpp/funcmanager.cpp
# :125-233) -- a file RC-c already generates byte-identically and covers in two
# parity dimensions. So this stage inherits proven scope rather than re-deriving
# module enablement.
#
# No JVM, no jars: Makefile.am's own check-jvm-dep asserts scilab-cli-bin has NO
# libjvm dependency. Depending on the Java build here would invent a prerequisite
# autotools does not have.
#
# DELIBERATE DIVERGENCE FROM autotools -- this target FAILS LOUDLY.
# Makefile.am:247 prefixes its recipe with `-`, so make IGNORES the exit status:
# a failed macros pass prints "Error 1 (ignored)" and the build continues, and
# nothing downstream re-validates completeness. That is not hypothetical -- it is
# how the rc=231 bug shipped (commit 7303c43690e: one module lacked its
# macros/buildmacros.sce, the unguarded exec failed, scilab-cli exited non-zero
# after building every other library fine, and make swallowed it). The migration's
# mandate is to reproduce the ARTIFACT, not to inherit a swallow-the-error habit
# into a build system that never had it. CMake propagates the failure.
#
# OPT-IN, not on drop-in-all -- like the 1f-c `doc` target, this needs a fully
# built interpreter. On an unbuilt tree it would fail at exec in a way that reads
# as a CMake bug rather than a missing prerequisite.
#
# NOTE for anyone comparing this against `make macros`: genlib is INCREMENTAL
# (sci_genlib.cpp:263-279 skips a .sci whose md5 matches the previous `lib`
# manifest when its .bin still exists), so a second run over a built tree is a
# no-op. Delete both *.bin AND lib under modules/*/macros/ before comparing.

add_custom_target(macros
  COMMAND ${CMAKE_COMMAND} -E env HOME=/tmp
          ${SCILAB_SOURCE_DIR}/bin/scilab-cli
          -ns -noatomsautoload -nouserstartup -quit
          -f modules/functions/scripts/buildmacros/buildmacros.sce
  WORKING_DIRECTORY ${SCILAB_SOURCE_DIR}
  COMMENT "Building Scilab macros (.sci -> .bin) with the built interpreter"
  VERBATIM)
```

**Reproduce the invocation exactly** — `HOME=/tmp`, the same five flags in the same order, the same script path, the same working directory. Each is load-bearing: `HOME=/tmp` keeps a developer's real `SCIHOME` out of the build, and `-ns -noatomsautoload -nouserstartup` suppress startup files that would otherwise perturb the AST node counter the `.bin` format embeds.

- [ ] **Step 2: Include it from the driver.** In `scilab/CMakeLists.txt`, after `include(cmake/ScilabGeneratedFiles.cmake)`:

```cmake
# RC-d -- the macros build (opt-in post-step; needs a built interpreter).
include(cmake/ScilabMacros.cmake)
```

- [ ] **Step 3: Prove byte-identity against `make macros` across ALL files.**

```bash
cd scilab
purge() { find modules -path '*/macros/*' -name '*.bin' -delete
          find modules -path '*/macros/*' -name 'lib'   -delete; }
snap()  { find modules -path '*/macros/*' -name '*.bin' -exec shasum -a256 {} + | sort -k2 > "$1"; }

purge && make macros >/tmp/rcd-make.log 2>&1; echo "make macros rc=$?"
snap /tmp/rcd-make.txt

purge && cmake --build build-cmake --target macros >/tmp/rcd-cmake.log 2>&1; echo "cmake macros rc=$?"
snap /tmp/rcd-cmake.txt

echo "make: $(wc -l < /tmp/rcd-make.txt | tr -d ' ')  cmake: $(wc -l < /tmp/rcd-cmake.txt | tr -d ' ')"
echo "differing files: $(diff /tmp/rcd-make.txt /tmp/rcd-cmake.txt | grep -c '^<')"
diff /tmp/rcd-make.txt /tmp/rcd-cmake.txt | head -5
```
Expected: both rc=0, both ~3516 files, **0 differing**. Report the counts verbatim. A count mismatch means one driver visited a different module set; a content mismatch means the invocation is not equivalent — investigate either rather than accepting it.

- [ ] **Step 4: Prove the target FAILS LOUDLY — this is the deliberate divergence, so verify it.**

```bash
cd scilab
BAD=modules/core/macros/who_user.sci; cp $BAD /tmp/who_user.sci.bak
printf '\nthis is not valid scilab syntax ((( \n' >> $BAD
find modules -path '*/macros/*' -name '*.bin' -delete
find modules -path '*/macros/*' -name 'lib'   -delete
cmake --build build-cmake --target macros >/tmp/rcd-fail.log 2>&1; echo "cmake macros rc=$? (expect NON-ZERO)"
cp /tmp/who_user.sci.bak $BAD
```
Expected: **non-zero**. Contrast with autotools, where the same injected error yields `make macros` rc=0 with "Error 1 (ignored)" — worth running to confirm the divergence is real and not assumed. Afterwards, restore the source and rebuild the macros cleanly so the tree is left valid.

- [ ] **Step 5: Confirm coexistence and the suite.**

```bash
cd scilab && git status --short -- configure.ac '*/Makefile.am' '*buildmacros.sce' | head
find modules -path '*/macros/*' -name '*.bin' -delete && find modules -path '*/macros/*' -name 'lib' -delete
make macros >/dev/null 2>&1; echo "make macros still works, rc=$?"
cd build-parity && python3 -m pytest -q | tail -1
python3 -m parity.capture .. /tmp/rcd-co.json cand >/dev/null && python3 -m parity.diff baseline-autotools.json /tmp/rcd-co.json; echo "parity rc=$?"
```
Expected: no autotools/script modifications; `make macros` rc=0; suite green; `PARITY OK`.

- [ ] **Step 6: Commit.**

```bash
git add scilab/cmake/ScilabMacros.cmake scilab/CMakeLists.txt
git commit -m "cmake: drive the macros build (RC-d)"
```

---

### Task 3: From-scratch gate, docs + CI (CONTROLLER-executed — long build)

**Files:**
- Modify: `scilab/build-parity/README.md`, `docs/design/build-cmake-driver.md`, `.gitlab-ci.yml`

- [ ] **Step 1: From-scratch whole-tree gate.**

```bash
cd scilab && rm -rf build-cmake && cmake -S . -B build-cmake >/dev/null && \
  find modules -path '*/jar/*.jar' -delete && cmake --build build-cmake --target drop-in-all -j
find modules -path '*/macros/*' -name '*.bin' -delete
find modules -path '*/macros/*' -name 'lib'   -delete
cmake --build build-cmake --target macros
cd build-parity && python3 -m parity.capture .. /tmp/rcd-final.json cand && \
  python3 -m parity.diff baseline-autotools.json /tmp/rcd-final.json && \
  python3 -m parity.flagfacts_check ../build-cmake/compile_commands.json baseline-autotools.json ..
echo "rc=$?"
```
Expected: `PARITY OK` — now including a **content-checked** macro manifest built by CMake — and the flag gate rc=0.

- [ ] **Step 2: Update `scilab/build-parity/README.md`.** The `generated` row's macro-manifest description currently says "presence, not content." Correct it, and record why content hashing is strict rather than flaky: `.bin` output is deterministic despite embedding a process-wide AST node counter, measured at 0 of 3,516 files differing across two independent rebuilds.

- [ ] **Step 3: Update `docs/design/build-cmake-driver.md`.** Add a "Macros — built by CMake (RC-d)" section: the `macros` target invokes the existing `buildmacros.sce`/`genlib` machinery; scope comes from `etc/modules.xml`, which RC-c already generates and parity-proves; it is opt-in and JVM-independent; the `.bin` determinism measurement; `genlib`'s incrementality and why comparisons must purge `lib` too; and the **deliberate fail-loudly divergence** from `make`'s `-` prefix, with the `rc=231` history that justifies it. Also record the two incidental findings: `windows_tools`' unreachable macros (has `buildmacros.sce`, absent from `modules.xml`, so its `.bin` files are stale artifacts) and `dynamic_link`'s dead `loadgenlib.sce` fallback to a `genlib.sci` that does not exist.

- [ ] **Step 4: Update `.gitlab-ci.yml`.** In `sanity:cmake-driver`, extend the wiring checks:

```bash
      # K. Retire-configure RC-d is wired: CMake drives the macros build. Invisible
      #    during coexistence -- `make macros` still works -- so only the content
      #    manifest in the native parity job would notice, and only after a build.
      grep -q 'include(cmake/ScilabMacros.cmake)' CMakeLists.txt
```

- [ ] **Step 5: Commit.**

```bash
git add scilab/build-parity/README.md docs/design/build-cmake-driver.md .gitlab-ci.yml
git commit -m "build-parity: RC-d complete — macros under CMake (docs + CI)"
```

---

## Self-Review

**Spec coverage:** §5.1 (content gate, armed, fault-injected both ways) → Task 1; §5.2 (`ScilabMacros.cmake`, exact invocation, fail-loudly, opt-in) → Task 2 Steps 1-2; §5.3 (coexistence) → Task 2 Step 5; §6.1 (gate seen to fail) → Task 1 Step 7; §6.2 (byte-identity across all ~3,516, not a sample) → Task 2 Step 3; §6.3 (fails non-zero on a real error, verified by injection) → Task 2 Step 4; §6.4 (from-scratch parity + flag gate + suite) → Task 3 Step 1; §7's genlib-incrementality note → the plan-level trap section, repeated in every comparison step. No spec requirement lacks a task.

**Placeholder scan:** every step carries runnable code or a concrete command with expected output. The one figure the plan cannot pre-compute is the re-armed manifest hash, which Step 6 prints and Step 7 immediately exercises.

**Type consistency:** `MACRO_BIN_MANIFEST_KEY` is the same string constant before and after, so the baseline's `generated` map keeps its shape and only one value changes; `macro_bins` stays a list of strings joined by `"\n"`, with the per-entry format extended from `rel` to `rel + "\0" + sha256`; `SCILAB_SOURCE_DIR` is the name `ScilabConfigure.cmake` already sets and is consumed unchanged in Task 2.
