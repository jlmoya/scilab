# Camera Access + OpenCV DNN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make live camera capture and neural-network object identification work from Scilab on macOS — grant the Scilab process camera permission, expose OpenCV's `dnn` module through scicv, and reconcile scicv's stale OpenCV version pin.

**Architecture:** Three workstreams. (A) macOS TCC refuses camera access to a process that cannot show it a usage description; Scilab's real Mach-O lives outside `Contents/MacOS/`, so `[NSBundle mainBundle]` finds no `Info.plist` — the fix carries the plist *inside* the binary as a `__TEXT,__info_plist` section, added by `scilab_executable()` at link time, plus the key in the two app bundles for the LaunchServices path. (B) `libscicv.dylib` already links all three OpenCV dnn libraries, but the SWIG interface never declared them — one new `modules/opencv_dnn.i` alongside the eight existing module interfaces exposes `readNet` / `blobFromImage` / `Net` / `ClassificationModel`. That change requires regenerating the wrapper, which a dry-run proved is currently impossible on a clean checkout, so a prerequisite task repairs the regeneration path first. (C) `thirdparty/versions.sce` pins 4.8.1 while macOS links 5.0.0; the constant governs only the bundled Windows/Linux prebuilt, so the fix is to say so and add a build-time report of the version actually resolved.

**Confidence:** every technical premise below was measured on this machine on 2026-08-01, including a full SWIG dry-run of the dnn module in a scratch copy (exit 0, zero errors, 4570-entry gateway table). The Verified Facts section records what was run and what came back; three assumptions that felt safe turned out wrong and are corrected there.

**Tech Stack:** CMake 3.28+ (`cmake/ScilabAggregate.cmake`), Apple `ld` (`-sectcreate`), SWIG 4.4.1, OpenCV 5.0.0 (Homebrew, via `pkg-config opencv5`), Scilab 2027 `.sce` / `.tst` test harness, bash native probes.

## Global Constraints

- **No AI-attribution trailers.** No `Co-Authored-By`, no `Claude-Session`, no "Generated with" in any commit message, in either repository.
- **Commit directly on `main`** in both repos. Push both remotes for each: scilab → `gitlab` + `origin`; scicv → `gitlab` + `origin`.
- **Never run `sudo`.** If a step needs it, stop and ask the user to run it.
- **Every batch `.sce` ends with `exit(n)`.** `quit(n)` silently ignores its argument — a script ending in `quit(1)` exits 0 and the guard never fires.
- **Use `git commit -F <file>`** with a message file; do not pass multi-line `-m`.
- **At most one Scilab app instance open.** Kill any running instance before launching a replacement.
- **Scilab repo:** `/Users/josemoya/Projects/CLionProjects/scilab` (git root); the source tree is the `scilab/` subdirectory — the doubled path `.../scilab/scilab` is real.
- **scicv repo:** `/Users/josemoya/Projects/SciLabProjects/scicv`.
- **OpenCV is resolved through pkg-config, never hardcoded.** `buildflags.sci::opencv_pkgconfig_name()` probes `opencv6 opencv5 opencv4 opencv` in order. Do not write `/opt/homebrew/opt/opencv/...` into any source file.
- **Parity discipline:** predict the effect on `build-parity` *before* running it, then diff. Never re-baseline to make a diff go away without stating what changed and why. `tu_flag_facts` is frozen baseline-only data — preserve it, never compare it.

---

## Verified Facts (measured 2026-08-01, do not re-derive)

These were checked against the installed toolchain and the current tree. They are the premises the tasks rest on.

**Camera / TCC**
- `/Applications/Scilab-2027.0.0.app/Contents/Info.plist` has 17 keys and **zero** `NS*UsageDescription` keys. `PlistBuddy -c "Print :NSCameraUsageDescription"` → `Does Not Exist`.
- `Contents/MacOS/Scilab-2027.0.0` is a **bash script**, not a Mach-O. It `exec`s `./bin/scilab`, which execs `Contents/Resources/scilab/.libs/Scilab-2027.0.0` (arm64 Mach-O, 157192 bytes).
- `otool -l` on that Mach-O finds **no** `sectname __info_plist`.
- `/System/Applications/Calculator.app/Contents/MacOS/Calculator` has no `__info_plist` section either — a normal app doesn't need one *because its executable sits in `Contents/MacOS/`*. Scilab is the abnormal case that gets neither route.
- `otool -P <binary>` prints an embedded `__TEXT,__info_plist` as text (2 header lines, then the XML). `otool -X -s __TEXT __info_plist` byte-swaps within 4-byte words — do **not** use it.

**Build system**
- `scilab_executable()` is defined at `scilab/cmake/ScilabAggregate.cmake:268`; its `target_link_options()` call is at lines 309–314.
- `scilab/CMakeLists.txt:235` declares `scilab-cli-bin`; `:270` declares `scilab-bin` with `ALIAS Scilab-${SCILAB_LIBRARY_VERSION_MAJOR}.0.0`. The ALIAS is a `cmake -E copy` of the linked binary, so anything linked into `scilab-bin` is present in `Scilab-2027.0.0`.
- `build-parity/parity/capture.py::_fingerprint_exe` records exactly: `build_version`, `install_name` (first dep), `deps` (sorted), `tmp_leak`, `rpaths`. A `__TEXT` **section** is none of those — `-sectcreate` adds no load command, no dylib, no rpath.

**scicv / OpenCV 5**
- `sci_gateway/c/swig/scicv.i` `%include`s eight module interfaces: core, highgui, imgproc, contrib, objectdetect, photo, video, features2d. There is **no** `modules/opencv_dnn.i`.
- `libscicv.dylib` already links `libopencv_dnn.500.dylib`, `libopencv_dnn_objdetect.500.dylib`, `libopencv_dnn_superres.500.dylib` (57 OpenCV libs total) — the SWIG interface is the only thing missing.
- `modules/opencv_core.i:33` does `%include "opencv2/core/cvdef.h"`, so `CV_EXPORTS_W` / `CV_WRAP` / `CV_OUT` / `CV_WRAP_FILE_PATH` are already neutralized for SWIG's preprocessor. dnn's **own** macros are not: `opencv2/dnn/version.hpp` defines `CV__DNN_INLINE_NS_BEGIN` / `_END`, and SWIG does not follow `#include`, so they must be defined in the new `.i`.
- `cv::dnn` API confirmed present in `opencv2/dnn/dnn.hpp`: `readNet` (:1183), `readNetFromONNX` (:1244), `readNetFromTensorflow` (:1102), `readNetFromTFLite` (:1142), `blobFromImage` (:1291), `NMSBoxes` (:1448), `Model` (:1524), `ClassificationModel` (:1639) with `classify` (:1683), `DetectionModel` (:1755), `setInputScale(const Scalar&)` (:1570).
- **OpenCV 5 removed `readNetFromDarknet` and `readNetFromCaffe`.** Only TensorFlow, TFLite, ONNX and OpenVINO importers survive. Any recipe using a `.cfg`+`.weights` or `.prototxt`+`.caffemodel` model is dead on this build.
- `cv::VideoCapture` is **already wrapped** — `highgui.hpp:51` includes `videoio.hpp`, and `scicv_wrap.cxx` contains `new_VideoCapture`, `VideoCapture_read`, `VideoCapture_isOpened`, … So camera capture needs no new binding, only the TCC permission.
- `thirdparty/versions.sce:4` says `OPENCV_VERSION = "4.8.1"`; the linked library is 5.0.0. On macOS the constant is unread — `buildflags.sci` resolves everything through pkg-config. It governs the bundled Windows/Linux prebuilt payload only.
- `tests/unit_tests/symbols-5.0.0.tst` `exec`s `functions-5.0.0.txt` (1879 entries, `table = ["name","_wrap_name"; ..]`) and asserts every listed name `exists()`. It is an inclusion test — new symbols do not break it, so newly wrapped dnn names must be *added* to be guarded.

**SWIG regeneration — dry-run in a scratch copy, 2026-08-01**

This was executed, not predicted. The results below replace several plausible-but-wrong assumptions.

- The real command is in `sci_gateway/c/swig/Makefile.in`, and it is **not** a plain `swig -o`:
  ```
  swig -scilab -c++ -builder -I./include -I$(OPENCV_INC) \
       -builderflagscript buildflags.sci -builderverbositylevel 2 scicv.i
  sed 's/builder/builder_gateway_c/' <builder.sce >builder_gateway_c.sce
  mv builder_gateway_c.sce ..
  mv scicv_wrap.cxx ..
  ```
  `-builder` is what makes SWIG emit the gateway table; the two `mv`s put the outputs where the toolbox expects them.
- **`-I./include` is load-bearing and points at a patched-header shadow.** Without it SWIG dies with `cvstd_wrapper.hpp:45: Error: Syntax error in input(3).` — the variadic-template SFINAE `has_parenthesis_operator_check` declaration, which SWIG cannot parse. `Makefile.in`'s `patch:` target copies two OpenCV headers into `include/opencv2/core/` with line edits.
- **Three defects in that mechanism, all measured:**
  1. `sci_gateway/c/swig/include/` is **untracked** — `git ls-files sci_gateway/c/swig/include` returns nothing. It exists only on this machine. A fresh clone cannot regenerate the wrapper at all.
  2. `Makefile.in:21`'s `sed '45{s/^/\/\//}'` is GNU syntax. macOS BSD sed rejects it (`bad flag in substitute command: '}'`) and, because the shell has already truncated the redirect target, leaves an **empty** header behind — a silent corruption that still lets SWIG "succeed". The BSD-safe form is `sed '45s|^|//|'`.
  3. `Makefile.in:20` patches `cvdef.h` line 479; in OpenCV 5.0.0 that line is `#  endif`, so the `CV_DEPTH_MAX*CV_CN_MAX` edit no longer lands anywhere. The checked-out shadow `cvdef.h` was made from an older OpenCV.
  The current shadow's `cvstd_wrapper.hpp:45` is hand-patched with a `// SWIG-parse workaround:` prefix, i.e. someone fixed it by hand during the OpenCV 5 port and never wrote it down.
- **With the shadow restored, `scicv.i` + the new `modules/opencv_dnn.i` regenerates cleanly: exit 0, zero errors.** The dnn module needs no new typemaps and no parse workarounds.
- **The ignore file is a surface trim, not a parse fix.** SWIG also succeeds with an *empty* `opencv_dnn_ignore.i`. Measured table sizes: committed (no dnn) **4132** entries; with dnn and the ignores **4570**; with dnn and no ignores **4698**. The ignores remove 128 entries.
- **Generated names, read out of the produced table** (not predicted): `readNet`, `readNetFromONNX`, `readNetFromTensorflow`, `readNetFromTFLite`, `readNetFromModelOptimizer`, `blobFromImage`, `blobFromImages`, `blobFromImageWithParams`, `imagesFromBlob`, `NMSBoxes`, `NMSBoxesBatched`, `softNMSBoxes`, `getAvailableTargets`, `new_Net`, `delete_Net`, `Net_empty`, `Net_forward`, `Net_setInput`, `new_Model`, `Model_setInputParams`, `Model_setInputScale`, `Model_setInputMean`, `Model_setInputSize`, `Model_setInputSwapRB`, `Model_setInputCrop`, `Model_predict`, `new_ClassificationModel`, `delete_ClassificationModel`, `new_DetectionModel`, `DetectionModel_detect`.
- **Scilab's gateway primitive names are capped at 24 characters, and SWIG truncates.** The table holds `"ClassificationModel_clas","_wrap_ClassificationModel_classify"` — the *callable* name is the truncated one. Any name longer than 24 chars must be spelled truncated in Scilab code and in `functions-5.0.0.txt`.

**scicv Mat bindings (read from the committed gateway table)**
- `cvMatExtract(mat)` is the real Mat → Scilab-matrix converter; `macros/%Mat_e.sci` is built on it, so `someMat(:,:)` works too.
- There is **no** `Mat___mul__` and no per-channel scalar multiply. `Mat_mul` is `cv::Mat::mul` (element-wise against another Mat), and `Mat_convertTo`'s alpha is a single scalar. Per-channel scaling of a blob is therefore not available.
- `typemaps/opencv_typemaps.i` has **no OUT typemap for `int&` or `float&`**, so `ClassificationModel_classify(frame, CV_OUT int&, CV_OUT float&)` cannot return its results to Scilab. Use `Net_forward` + `cvMatExtract` + `max` instead.

**Model URLs (HTTP-verified 2026-08-01)**
- `https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx` → 206
- `https://raw.githubusercontent.com/onnx/models/main/validated/vision/classification/synset.txt` → 206

---

## File Structure

**scilab repo** (`/Users/josemoya/Projects/CLionProjects/scilab/scilab`)

| File | Responsibility |
|---|---|
| `etc/macos-usage-descriptions.plist` | **new** — the single source of the process's TCC usage strings; embedded into every Scilab executable at link time |
| `cmake/ScilabAggregate.cmake` | modify `scilab_executable()` (after line 314) to add the `-sectcreate` link option + `LINK_DEPENDS` |
| `modules/core/tests/native/run_macos_usage_plist.sh` | **new** — asserts the built executables carry a parseable embedded plist with the camera key |
| `package-macos.sh` | add `NSCameraUsageDescription` to the bundle `Info.plist` heredoc (~line 199) |
| `Scilab-2027.0.0.app/Contents/Info.plist` | add the same key to the dev-tree bundle |

**scicv repo** (`/Users/josemoya/Projects/SciLabProjects/scicv`)

| File | Responsibility |
|---|---|
| `sci_gateway/c/swig/regen.sh` | **new** — the whole regeneration recipe (patch shadow headers, run SWIG, place outputs) as one runnable script; replaces the broken `Makefile.in` targets |
| `sci_gateway/c/swig/include/opencv2/core/cvstd_wrapper.hpp` | **now tracked** — the patched-header shadow SWIG cannot parse without |
| `sci_gateway/c/swig/Makefile.in` | fix the GNU-only `sed`, drop the stale `cvdef.h` line-479 edit, delegate to `regen.sh` |
| `sci_gateway/c/swig/modules/opencv_dnn.i` | **new** — the dnn module interface: inline-namespace flattening + `%include` of `dnn/dnn.hpp` |
| `sci_gateway/c/swig/modules/opencv_dnn_ignore.i` | **new** — a surface trim (128 entries); not needed for SWIG to parse |
| `sci_gateway/c/swig/scicv.i` | register the new module (after `opencv_features2d.i`, before the legacy constants) |
| `sci_gateway/c/scicv_wrap.cxx` | **regenerated** by SWIG — never hand-edited |
| `sci_gateway/c/builder_gateway_c.sce` | **regenerated** by SWIG (it emits the gateway table) |
| `tests/unit_tests/dnn.tst` + `.dia.ref` | **new** — dnn binding smoke: `blobFromImage` shape, `readNet` error path, constants |
| `tests/unit_tests/functions-5.0.0.txt` | append the new dnn entries so `symbols-5.0.0.tst` guards them |
| `tests/camera_probe.sce` | **new** — user-gated camera acceptance probe (opens device 0, grabs a frame) |
| `thirdparty/versions.sce` | document what `OPENCV_VERSION` actually governs; record the verified macOS version |
| `sci_gateway/c/buildflags.sci` | add `getOpenCVVersion()` (pkg-config `--modversion`) |
| `build_macos.sce` | print the resolved OpenCV version in the build banner |
| `macros/scicv_opencv_version.sci` | **new** — runtime accessor returning the version the loaded gateway was built against |
| `demos/camera_classify/fetch-model.sh` | **new** — sha256-pinned fetch of the MobileNetV2 ONNX model + labels |
| `demos/camera_classify/camera_classify.sce` | **new** — the end-to-end demo: camera → blob → dnn → label |

**Task ordering:** Task 4a (regeneration prerequisite) must precede Task 4b (the dnn module) — the dnn module cannot be generated at all until regeneration works. Tasks 1→2→3 form the camera chain. Task 5 is independent. Task 6 needs 4b and 3.

---

### Task 1: Embed the camera usage description in the Scilab executables

**Files:**
- Create: `scilab/etc/macos-usage-descriptions.plist`
- Create: `scilab/modules/core/tests/native/run_macos_usage_plist.sh`
- Modify: `scilab/cmake/ScilabAggregate.cmake` (inside `scilab_executable()`, after the `target_link_options` block ending at line 314)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: every executable built by `scilab_executable()` — `scilab-bin`, `scilab-cli-bin`, and the `Scilab-2027.0.0` ALIAS copy — carries a `__TEXT,__info_plist` section containing `NSCameraUsageDescription`. Task 3 depends on this.

- [ ] **Step 1: Write the failing test**

Create `scilab/modules/core/tests/native/run_macos_usage_plist.sh`:

```bash
#!/usr/bin/env bash
# Every Scilab executable must carry an embedded __TEXT,__info_plist declaring
# NSCameraUsageDescription.
#
# WHY THE SECTION AND NOT THE BUNDLE
# ----------------------------------
# macOS TCC will not hand a process the camera unless it can read a usage
# description for that process. The normal route is the app bundle's
# Info.plist, reached because CFBundleExecutable lives in Contents/MacOS/.
# Scilab's does not: Contents/MacOS/Scilab-<version> is a bash script that
# execs bin/scilab, which execs Contents/Resources/scilab/.libs/Scilab-<version>.
# [NSBundle mainBundle] therefore resolves to .libs/ -- a plain directory with
# no Info.plist -- and TCC kills the process with
# __TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__ (abort trap 6) the moment scicv's
# AVFoundation backend opens a camera. Apple's documented route for an
# executable that is not in a bundle is to carry the plist inside the Mach-O.
#
# WHY otool -P AND NOT otool -s
# -----------------------------
# `otool -X -s __TEXT __info_plist` prints the section as 4-byte words in host
# order, i.e. every group of 4 characters comes out reversed ("mx?<ev loisr"
# instead of "<?xml versio"). `otool -P` prints the section as text, after two
# header lines. Verified against /usr/bin/plutil, which ships such a section.
#
# Usage:  ./run_macos_usage_plist.sh
# Env:    SCI_LIBS  override the .libs directory under test
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
SCI_ROOT="$(cd "$HERE/../../../.." && pwd)"
LIBS="${SCI_LIBS:-$SCI_ROOT/.libs}"

if [ "$(uname -s)" != "Darwin" ]; then
    echo "SKIP: macOS-only (TCC does not exist elsewhere)"
    exit 0
fi

status=0
found=0
for name in scilab-bin scilab-cli-bin Scilab-2027.0.0; do
    exe="$LIBS/$name"
    if [ ! -f "$exe" ]; then
        echo "SKIP $name: not built ($exe)"
        continue
    fi
    found=$((found + 1))

    if ! otool -l "$exe" | grep -q 'sectname __info_plist'; then
        echo "FAIL $name: no __TEXT,__info_plist section"
        status=1
        continue
    fi

    # otool -P emits "<path>:" and "(__TEXT,__info_plist) section" first.
    desc="$(otool -P "$exe" | tail -n +3 \
            | plutil -extract NSCameraUsageDescription raw -o - -- - 2>/dev/null)"
    if [ -z "$desc" ]; then
        echo "FAIL $name: embedded plist has no NSCameraUsageDescription"
        status=1
        continue
    fi
    echo "PASS $name: $desc"
done

if [ "$found" -eq 0 ]; then
    echo "FAIL: no executables found in $LIBS -- build first (cmake --build <dir> --target drop-in-all)"
    exit 1
fi
exit "$status"
```

Then `chmod +x scilab/modules/core/tests/native/run_macos_usage_plist.sh`.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
./modules/core/tests/native/run_macos_usage_plist.sh
```
Expected: `FAIL scilab-bin: no __TEXT,__info_plist section` (and the same for the other two), exit status 1. If it prints `SKIP … not built`, build first with `cmake --build build-cmake --target drop-in-all` and re-run — a run where everything skipped proves nothing.

- [ ] **Step 3: Create the usage-description plist**

Create `scilab/etc/macos-usage-descriptions.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<!--
  TCC usage descriptions for the Scilab executables.

  This file is NOT an app-bundle Info.plist. It is linked INTO each Scilab
  executable as the __TEXT,__info_plist section (see scilab_executable() in
  cmake/ScilabAggregate.cmake), because the real Mach-O lives in
  Contents/Resources/scilab/.libs/ rather than Contents/MacOS/, so the bundle
  Info.plist never reaches the process. Keep it to privacy keys: anything that
  belongs to the bundle identity (CFBundleIdentifier, icons, LSArchitecture*)
  is set by package-macos.sh on the bundle itself.
-->
<plist version="1.0">
<dict>
    <key>NSCameraUsageDescription</key>
    <string>Scilab uses the camera when a script captures video, for example through the scicv (OpenCV) toolbox.</string>
</dict>
</plist>
```

- [ ] **Step 4: Add the link option**

In `scilab/cmake/ScilabAggregate.cmake`, inside `scilab_executable()`, immediately after the existing `target_link_options(...)` block that ends with `"LINKER:-bind_at_load")` (line 314), insert:

```cmake
  # macOS TCC: this process is the one that opens a camera (scicv/OpenCV's
  # AVFoundation backend), and TCC aborts it with
  # __TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__ unless it can read a usage
  # description for the REQUESTING BINARY. The app-bundle route never reaches
  # us: CFBundleExecutable is a shell script in Contents/MacOS/ that execs
  # Contents/Resources/scilab/.libs/Scilab-<version>, so [NSBundle mainBundle]
  # resolves to .libs/ -- no Info.plist there. Apple's route for an executable
  # outside a bundle is to carry the plist inside the Mach-O.
  #
  # PARITY: -sectcreate adds a __TEXT SECTION, not a load command. The
  # executables dimension fingerprints build_version, first dep (install_name
  # slot), the sorted dep set and the ordered rpaths (build-parity/parity/
  # capture.py::_fingerprint_exe) -- none of which a section touches. Expect a
  # clean diff; if it is not clean, something else changed, so investigate
  # rather than re-baseline.
  #
  # LINK_DEPENDS makes an edit to the plist relink the executable; without it
  # CMake sees no changed input and the stale section survives.
  if(APPLE)
    set(_usage_plist ${SCILAB_SOURCE_DIR}/etc/macos-usage-descriptions.plist)
    if(NOT EXISTS ${_usage_plist})
      message(FATAL_ERROR "scilab_executable(${NAME}): missing ${_usage_plist}")
    endif()
    target_link_options(${NAME} PRIVATE
      "LINKER:-sectcreate,__TEXT,__info_plist,${_usage_plist}")
    set_property(TARGET ${NAME} APPEND PROPERTY LINK_DEPENDS ${_usage_plist})
  endif()
```

Also extend the function's header comment. In the block that begins `# THE LINK SHAPE (ground truth: ...)` (line 224), append a bullet after the `-undefined dynamic_lookup` bullet at line 264–266:

```
#
#  * `-sectcreate __TEXT __info_plist etc/macos-usage-descriptions.plist`: the
#    TCC usage descriptions, carried in the binary because Scilab's Mach-O sits
#    outside Contents/MacOS/ and so has no bundle Info.plist. Not part of the
#    autotools baseline -- a deliberate, parity-neutral addition (a section, not
#    a load command).
```

- [ ] **Step 5: Rebuild and run the test to verify it passes**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
cmake --build build-cmake --target drop-in-scilab-bin drop-in-scilab-cli-bin
./modules/core/tests/native/run_macos_usage_plist.sh
```
Expected: three `PASS` lines, each echoing the usage string, exit status 0.

- [ ] **Step 6: Verify build parity is unaffected**

Predict first, in writing: the executables dimension should be **identical** — no dep, rpath or build_version change.

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab/build-parity
./capture.sh .. /tmp/parity-sectcreate.json sectcreate
./diff.sh baseline-autotools.json /tmp/parity-sectcreate.json
```
Expected: no `executable …` lines in the diff. Macro-manifest drift is expected only if macros were rebuilt; nothing in this task touches macros, so that section should be silent too. If any `executable` line appears, stop and investigate — do not re-baseline.

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
cat > /tmp/msg-camera-sectcreate.txt <<'MSG'
macos: embed NSCameraUsageDescription in the Scilab executables

TCC refuses camera access to a process it cannot read a usage description
for, and Scilab's real Mach-O lives in Contents/Resources/scilab/.libs/
rather than Contents/MacOS/ -- so [NSBundle mainBundle] finds no Info.plist
and any scicv VideoCapture(0) died with
__TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__ (abort trap 6).

scilab_executable() now links etc/macos-usage-descriptions.plist into every
executable as __TEXT,__info_plist. That is a section, not a load command, so
the build-parity executables dimension (build_version / install_name / deps /
rpaths) is untouched -- verified clean against baseline-autotools.json.

run_macos_usage_plist.sh gates it; it reads the section with `otool -P`
(`otool -X -s` byte-swaps within 4-byte words and is unusable here).
MSG
git add scilab/etc/macos-usage-descriptions.plist \
        scilab/cmake/ScilabAggregate.cmake \
        scilab/modules/core/tests/native/run_macos_usage_plist.sh
git commit -F /tmp/msg-camera-sectcreate.txt
git push gitlab main && git push origin main
```

---

### Task 2: Declare the camera key on both app bundles

**Files:**
- Modify: `scilab/package-macos.sh` (the `Info.plist` heredoc starting at line 199)
- Modify: `scilab/Scilab-2027.0.0.app/Contents/Info.plist`

**Interfaces:**
- Consumes: nothing (independent of Task 1 — belt and braces for the second launch path).
- Produces: a packaged `/Applications/Scilab-2027.0.0.app` whose bundle `Info.plist` contains `NSCameraUsageDescription`. Task 3 verifies both routes together.

**Why both this and Task 1:** there are two distinct launch paths and it is not worth guessing which one TCC attributes. Double-clicking the app makes LaunchServices the launcher and the bundle the plausible attribution target; `scilab2027` from a terminal, and the dev tree's `bin/scilab`, have no bundle at all and depend entirely on Task 1's embedded section. The two changes do not conflict — when both are present TCC reads a consistent string either way.

**Explicitly not doing:** `etc/Info.plist` is regenerated from `etc/Info.plist.in` by `cmake/ScilabGeneratedFiles.cmake:226` and belongs to the legacy upstream `.app` layout that neither launcher uses. Leaving it alone keeps this change to the two bundles that actually launch Scilab here.

- [ ] **Step 1: Write the failing test**

Create `scilab/modules/core/tests/native/run_macos_bundle_plist.sh`:

```bash
#!/usr/bin/env bash
# Both Scilab app bundles must declare NSCameraUsageDescription.
#
# This covers the LaunchServices launch path (double-clicking the .app), which
# is distinct from the embedded __TEXT,__info_plist section checked by
# run_macos_usage_plist.sh. That one covers the paths with no bundle at all:
# the `scilab2027` terminal wrapper and the dev tree's bin/scilab.
#
# Usage:  ./run_macos_bundle_plist.sh
# Env:    APP_BUNDLES  space-separated bundle paths to check
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
SCI_ROOT="$(cd "$HERE/../../../.." && pwd)"
BUNDLES="${APP_BUNDLES:-$SCI_ROOT/Scilab-2027.0.0.app /Applications/Scilab-2027.0.0.app}"

if [ "$(uname -s)" != "Darwin" ]; then
    echo "SKIP: macOS-only"
    exit 0
fi

status=0
found=0
for app in $BUNDLES; do
    plist="$app/Contents/Info.plist"
    if [ ! -f "$plist" ]; then
        echo "SKIP $app: not present"
        continue
    fi
    found=$((found + 1))
    desc="$(/usr/libexec/PlistBuddy -c 'Print :NSCameraUsageDescription' "$plist" 2>/dev/null)"
    if [ -z "$desc" ]; then
        echo "FAIL $app: no NSCameraUsageDescription"
        status=1
    else
        echo "PASS $app: $desc"
    fi
done

if [ "$found" -eq 0 ]; then
    echo "FAIL: no bundles found -- package first (./package-macos.sh)"
    exit 1
fi
exit "$status"
```

Then `chmod +x scilab/modules/core/tests/native/run_macos_bundle_plist.sh`.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
./modules/core/tests/native/run_macos_bundle_plist.sh
```
Expected: `FAIL …/Scilab-2027.0.0.app: no NSCameraUsageDescription` for both bundles, exit status 1.

- [ ] **Step 3: Add the key to the packager**

In `scilab/package-macos.sh`, inside the `<<'PLIST'` heredoc, immediately after the `<key>NSHighResolutionCapable</key><true/>` line, insert:

```
    <!-- TCC: the camera prompt macOS shows when a script opens a capture device
         (scicv/OpenCV). The engine binary also carries this string in its own
         __TEXT,__info_plist (cmake/ScilabAggregate.cmake) because the real
         Mach-O lives under Contents/Resources/scilab/.libs/, outside the bundle
         layout TCC would otherwise consult. Keep the two strings in sync with
         etc/macos-usage-descriptions.plist. -->
    <key>NSCameraUsageDescription</key><string>Scilab uses the camera when a script captures video, for example through the scicv (OpenCV) toolbox.</string>
```

- [ ] **Step 4: Add the key to the dev-tree bundle**

In `scilab/Scilab-2027.0.0.app/Contents/Info.plist`, add the same pair inside the top-level `<dict>` (hand-maintained, not generated). **It is NOT tracked** — root `.gitignore:15` has
`/scilab/Scilab-*.app/`, so a plain `git add` exits 1 and the change would vanish from the
commit silently. Stage it with `git add -f` on this one path only; do not un-ignore the
whole bundle, whose launcher script hardcodes an absolute developer path:

```xml
	<key>NSCameraUsageDescription</key>
	<string>Scilab uses the camera when a script captures video, for example through the scicv (OpenCV) toolbox.</string>
```

- [ ] **Step 5: Re-package and run the test to verify it passes**

The `/Applications` bundle only gets the key by re-running the packager. Kill any running instance first (one-instance rule).

Run:
```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
pkill -f 'Scilab-2027.0.0' || true
./package-macos.sh
./modules/core/tests/native/run_macos_bundle_plist.sh
```
Expected: two `PASS` lines, exit status 0.

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
cat > /tmp/msg-camera-bundle.txt <<'MSG'
macos: declare NSCameraUsageDescription on both app bundles

Covers the LaunchServices launch path, where the bundle Info.plist is the
plausible TCC attribution target. The embedded __TEXT,__info_plist section
added in the previous commit covers the paths with no bundle at all -- the
scilab2027 terminal wrapper and the dev tree's bin/scilab. The strings match
so the prompt reads the same however Scilab was started.

run_macos_bundle_plist.sh gates both bundles.
MSG
git add scilab/package-macos.sh \
        scilab/Scilab-2027.0.0.app/Contents/Info.plist \
        scilab/modules/core/tests/native/run_macos_bundle_plist.sh
git commit -F /tmp/msg-camera-bundle.txt
git push gitlab main && git push origin main
```

---

### Task 3: Camera acceptance probe (user-gated)

**Files:**
- Create: `scicv/tests/camera_probe.sce`

**Interfaces:**
- Consumes: the embedded plist from Task 1 and the bundle key from Task 2.
- Produces: proof that `VideoCapture(0)` opens a real camera and returns a non-empty frame from inside Scilab. No new API — `VideoCapture` is already wrapped (`new_VideoCapture`, `VideoCapture_read`, `VideoCapture_isOpened` are all in `scicv_wrap.cxx`).

**This task requires the user.** The first camera request raises a system prompt that only a human can accept, and the machine must have a camera attached. The implementer runs the probe, asks the user to click **Allow**, then re-runs.

- [ ] **Step 1: Write the failing test**

Create `scicv/tests/camera_probe.sce`:

```scilab
// Camera acceptance probe — proves a live capture device opens from Scilab.
//
// Not a .tst: this needs a physical camera and, on first run, a human clicking
// Allow on the macOS privacy prompt. Neither belongs in the automated suite.
//
//   scilab-cli -nb -f tests/camera_probe.sce   (or via the packaged app's CLI)
//
// Exit status: 0 = a frame was captured, 1 = failure (reason printed).
// Uses exit(), not quit() — quit() ignores its argument and would always
// report success.

exec(fullfile(get_absolute_file_path("camera_probe.sce"), "..", "loader.sce"), -1);
scicv_Init();

rc = 1;
cap = [];

ierr = execstr("cap = VideoCapture(0);", "errcatch");
if ierr <> 0 then
    mprintf("FAIL: VideoCapture(0) raised: %s\n", lasterror());
    exit(1);
end

if VideoCapture_isOpened(cap) <> %T then
    mprintf("FAIL: camera did not open. Either no camera is attached, or macOS\n");
    mprintf("      denied access. Check System Settings > Privacy & Security >\n");
    mprintf("      Camera for the Scilab entry.\n");
    delete_VideoCapture(cap);
    exit(1);
end

// Give the device a few attempts: the first frames after opening an
// AVFoundation device are routinely empty while exposure settles, and a
// single-read probe would report a spurious failure.
frame = [];
for k = 1:10
    ok = VideoCapture_read(cap, frame);
    if ok == %T & Mat_size(frame) <> [] then
        sz = Mat_size(frame);
        if sz(1) > 0 & sz(2) > 0 then
            mprintf("PASS: captured a %d x %d frame on attempt %d\n", sz(1), sz(2), k);
            rc = 0;
            break;
        end
    end
end

if rc <> 0 then
    mprintf("FAIL: camera opened but returned no non-empty frame in 10 attempts\n");
end

delete_VideoCapture(cap);
exit(rc);
```

- [ ] **Step 2: Run the probe against a build WITHOUT the fix, to prove it discriminates**

The `/Applications` bundle only has the fix after Task 2 re-packaged it, so use a copy of the previous app if one exists; otherwise use the pre-fix binary directly. A probe that passes everywhere proves nothing.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -f tests/camera_probe.sce ; echo "rc=$?"
```
Expected against a pre-fix binary: the process dies with **abort trap 6** and a crash report in `~/Library/Logs/DiagnosticReports/` naming `__TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__`. Record the crash-report filename in the task report — that is the evidence the fix was needed.

- [ ] **Step 3: Run the probe against the fixed build, and ask the user to allow access**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -f tests/camera_probe.sce ; echo "rc=$?"
```
Expected on first run: macOS shows the camera prompt carrying the usage string from Task 1/2. **Ask the user to click Allow**, then re-run. Expected after allowing: `PASS: captured a 1080 x 1920 frame on attempt 1` (dimensions vary by camera), `rc=0`.

If the process still aborts with a TCC violation after allowing, do not add more plist keys — reset the decision and retry once with `tccutil reset Camera org.scilab.app.scilab-2027-0-0`, and if it still aborts, report BLOCKED with the crash report attached.

- [ ] **Step 4: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-camera-probe.txt <<'MSG'
tests: camera acceptance probe

Opens VideoCapture(0) and requires a non-empty frame. Kept out of the .tst
suite because it needs a physical camera and, on first run, a human accepting
the macOS privacy prompt.

Against a Scilab build without the embedded usage description the probe dies
with abort trap 6 (__TCC_CRASHING_DUE_TO_PRIVACY_VIOLATION__); against the
fixed build it captures a frame. Retries ten times because the first frames
from an AVFoundation device are routinely empty while exposure settles.
MSG
git add tests/camera_probe.sce
git commit -F /tmp/msg-camera-probe.txt
git push gitlab main && git push origin main
```

---

### Task 4a: Make the SWIG wrapper regenerable (prerequisite)

**Files:**
- Create: `scicv/sci_gateway/c/swig/regen.sh`
- Track: `scicv/sci_gateway/c/swig/include/opencv2/core/cvstd_wrapper.hpp`
- Modify: `scicv/sci_gateway/c/swig/Makefile.in` (lines 17–21, the `patch:` target)
- Modify: `scicv/.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: `./regen.sh` in `sci_gateway/c/swig/`, which regenerates `../scicv_wrap.cxx` and `../builder_gateway_c.sce` from `scicv.i` on a clean checkout. Task 4b calls it.

**Why this comes first.** Task 4b's entire deliverable is a regenerated wrapper, and regeneration is currently broken in three measured ways (see Verified Facts): the patched-header shadow SWIG needs is untracked, `Makefile.in`'s `sed` is GNU-only and silently truncates its output on macOS, and its `cvdef.h` line-479 edit no longer matches OpenCV 5. Right now the wrapper can only be regenerated on this one machine, by luck of a leftover directory.

- [ ] **Step 1: Write the failing test**

Prove regeneration is broken from a clean state, by moving the untracked shadow aside.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig
git ls-files include | wc -l          # expect 0 -- the shadow is untracked
mv include /tmp/scicv-shadow-backup
INC=$(/opt/homebrew/bin/pkg-config --cflags opencv5 | tr ' ' '\n' | sed -n 's/^-I//p' | head -1)
swig -scilab -c++ -builder -I./include -I"$INC" \
     -builderflagscript buildflags.sci -builderverbositylevel 2 scicv.i
echo "exit=$?"
```
Expected: `cvstd_wrapper.hpp:45: Error: Syntax error in input(3).`, non-zero exit. Leave the shadow moved aside for Step 2.

- [ ] **Step 2: Write the regeneration script**

Create `scicv/sci_gateway/c/swig/regen.sh`:

```bash
#!/usr/bin/env bash
# Regenerate ../scicv_wrap.cxx and ../builder_gateway_c.sce from scicv.i.
#
# WHY A SCRIPT AND NOT `make`
# ---------------------------
# Makefile.in's patch: target was GNU-sed-only -- macOS BSD sed rejects
# `sed '45{s/^/\/\//}'` with "bad flag in substitute command: '}'", and because
# the shell truncates the redirect target first, it left an EMPTY header behind.
# SWIG then "succeeded" against nothing. It also patched cvdef.h line 479, which
# in OpenCV 5.0.0 is `#  endif` -- the edit stopped landing years ago.
#
# THE SHADOW INCLUDE DIRECTORY
# ----------------------------
# SWIG cannot parse cvstd_wrapper.hpp:45 (a variadic-template SFINAE
# declaration) and dies with "Syntax error in input(3)". include/ holds a copy
# with that one line commented out, and -I./include puts it AHEAD of the real
# OpenCV headers. That directory is tracked, so a fresh clone can regenerate;
# it used to exist only on whichever machine had last built here.
#
#   ./regen.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

PC=/opt/homebrew/bin/pkg-config; command -v "$PC" >/dev/null || PC=pkg-config
OPENCV_PC=""
for n in opencv6 opencv5 opencv4 opencv; do
    if "$PC" --exists "$n" 2>/dev/null; then OPENCV_PC="$n"; break; fi
done
[ -n "$OPENCV_PC" ] || { echo "ERROR: pkg-config found no OpenCV" >&2; exit 1; }
OPENCV_INC="$("$PC" --cflags "$OPENCV_PC" | tr ' ' '\n' | sed -n 's/^-I//p' | head -1)"
echo "OpenCV: $("$PC" --modversion "$OPENCV_PC") ($OPENCV_PC) at $OPENCV_INC"

# Refresh the shadow from the installed headers. The target line is found by
# CONTENT, not by number: Makefile.in hardcoded 45 and 479, and the 479 one had
# already rotted silently.
mkdir -p include/opencv2/core
src="$OPENCV_INC/opencv2/core/cvstd_wrapper.hpp"
line="$(grep -n 'has_parenthesis_operator_check(typename std::is_same' "$src" | cut -d: -f1 | head -1)"
if [ -z "$line" ]; then
    echo "ERROR: cvstd_wrapper.hpp no longer contains the SFINAE declaration SWIG chokes on." >&2
    echo "       Try regenerating without the shadow; if SWIG now parses it, delete include/." >&2
    exit 1
fi
sed "${line}s|^|// SWIG-parse workaround: |" "$src" > include/opencv2/core/cvstd_wrapper.hpp
echo "patched cvstd_wrapper.hpp line $line"

swig -scilab -c++ -builder -I./include -I"$OPENCV_INC" \
     -builderflagscript buildflags.sci -builderverbositylevel 2 scicv.i

# SWIG names its output builder.sce; the toolbox expects builder_gateway_c.sce.
sed 's/builder/builder_gateway_c/' < builder.sce > builder_gateway_c.sce
rm -f builder.sce
mv -f builder_gateway_c.sce ..
mv -f scicv_wrap.cxx ..

echo "regenerated ../scicv_wrap.cxx and ../builder_gateway_c.sce"
echo "gateway table entries: $(grep -c '\.\.$' ../builder_gateway_c.sce)"
```

Then `chmod +x scicv/sci_gateway/c/swig/regen.sh`.

- [ ] **Step 3: Run it to verify it passes from the clean state**

The shadow is still moved aside from Step 1, so this proves `regen.sh` rebuilds it.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig
./regen.sh
```
Expected: `OpenCV: 5.0.0 (opencv5) …`, `patched cvstd_wrapper.hpp line 45`, SWIG runs without errors, and `gateway table entries: 4132` — the same count as the committed file, because `scicv.i` has not changed yet. A different count means the regeneration is not faithful; investigate before continuing.

Confirm the regenerated wrapper matches what was committed:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
git diff --stat sci_gateway/c/scicv_wrap.cxx sci_gateway/c/builder_gateway_c.sce
```
Expected: no changes, or changes confined to the SWIG version banner. Anything else means the committed wrapper was not generated from the current `scicv.i` — report that rather than committing over it.

- [ ] **Step 4: Track the shadow and fix the Makefile**

Add to `scicv/.gitignore` an explicit un-ignore if any pattern currently excludes it, then track the directory:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
git check-ignore -v sci_gateway/c/swig/include/opencv2/core/cvstd_wrapper.hpp || echo "not ignored"
git add -f sci_gateway/c/swig/include/opencv2/core/cvstd_wrapper.hpp
```

Then replace `Makefile.in`'s `build:` and `patch:` targets so the two paths cannot drift apart:

```make
build:
	./regen.sh

patch:
	./regen.sh
```

Delete the stale `include/opencv2/core/cvdef.h` — its line-479 edit no longer matches any OpenCV 5 header, and `regen.sh` does not produce it:
```bash
rm -f sci_gateway/c/swig/include/opencv2/core/cvdef.h
```

- [ ] **Step 5: Verify once more from a pristine checkout — run this AFTER Step 6's commit**

**Ordering matters and the numbering here is misleading.** `git clone` only sees committed history, and Step 6 is the commit that puts `regen.sh` and the tracked shadow header into it. Run as numbered, this clones a repo with no `regen.sh` and fails for the wrong reason. Do Step 6's commit first, then this, then push.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
git stash list  # note anything already stashed; do not disturb it
rm -rf /tmp/scicv-clean && git clone . /tmp/scicv-clean
cd /tmp/scicv-clean/sci_gateway/c/swig && ./regen.sh
```
Expected: succeeds, `gateway table entries: 4132`. This is the check that actually proves the untracked-shadow problem is gone — a run in the working tree cannot distinguish "tracked" from "left over". If it fails, amend rather than pushing something broken.

**Entry count alone is not sufficient acceptance.** It counts `..`-terminated lines in `builder_gateway_c.sce`, so it is structurally blind to constant-registration drift inside `scicv_wrap.cxx` — the exact place a version-guard bug lands. Also confirm the regenerated wrapper compiles:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c
PC=/opt/homebrew/bin/pkg-config; command -v "$PC" >/dev/null || PC=pkg-config
for n in opencv6 opencv5 opencv4 opencv; do "$PC" --exists "$n" && OPENCV_PC="$n" && break; done
printf '#include <opencv2/opencv.hpp>\nint main(){ return (int)cv::CAP_PROP_GIGA_FRAME_HEIGH_MAX; }\n' > /tmp/probe.cpp
clang++ -fsyntax-only -std=c++17 $("$PC" --cflags "$OPENCV_PC") /tmp/probe.cpp
```
Expected: that probe **fails** (`no member named 'CAP_PROP_GIGA_FRAME_HEIGH_MAX'`) — proving the symbol genuinely does not exist in OpenCV 5 — and `grep -c CAP_PROP_GIGA_FRAME_HEIGH_MAX scicv_wrap.cxx` returns **0**, proving `regen.sh` no longer emits it.

- [ ] **Step 6: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-regen.txt <<'MSG'
swig: make the wrapper regenerable on a clean checkout

Regenerating scicv_wrap.cxx only worked on a machine that happened to have a
leftover sci_gateway/c/swig/include/ -- the directory was never tracked. On a
fresh clone SWIG died at cvstd_wrapper.hpp:45 with "Syntax error in input(3)"
(a variadic-template SFINAE declaration it cannot parse); -I./include exists to
shadow that header with a patched copy.

Two more defects in the Makefile recipe that produced it:

  - `sed '45{s/^/\/\//}'` is GNU syntax. BSD sed rejects it, and since the
    shell truncates the redirect target first, it left an EMPTY header --
    which SWIG then parsed happily. A silent corruption, not a visible failure.
  - The companion cvdef.h edit targeted line 479, which in OpenCV 5.0.0 is
    `#  endif`. It had stopped landing anywhere and is dropped.

regen.sh now owns the whole recipe: it resolves OpenCV through pkg-config,
finds the offending declaration by CONTENT rather than by line number, runs
SWIG with the real flag set (-builder + buildflags.sci), and places both
outputs. Makefile.in delegates to it so the two cannot drift. The shadow header
is tracked.

Verified by regenerating from a fresh `git clone`: 4132 gateway entries,
byte-identical to the committed wrapper.
MSG
git add sci_gateway/c/swig/regen.sh sci_gateway/c/swig/Makefile.in \
        sci_gateway/c/swig/include/opencv2/core/cvstd_wrapper.hpp .gitignore
git rm --cached --ignore-unmatch sci_gateway/c/swig/include/opencv2/core/cvdef.h
git commit -F /tmp/msg-regen.txt
git push gitlab main && git push origin main
```

---

### Task 4b: Expose OpenCV's dnn module through SWIG

**Files:**
- Create: `scicv/sci_gateway/c/swig/modules/opencv_dnn.i`
- Create: `scicv/sci_gateway/c/swig/modules/opencv_dnn_ignore.i`
- Modify: `scicv/sci_gateway/c/swig/scicv.i` (after the `opencv_features2d.i` line, before `scicv_legacy_constants.i`)
- Regenerate: `scicv/sci_gateway/c/scicv_wrap.cxx`, `scicv/sci_gateway/c/builder_gateway_c.sce`
- Create: `scicv/tests/unit_tests/dnn.tst`, `scicv/tests/unit_tests/dnn.dia.ref`
- Modify: `scicv/tests/unit_tests/functions-5.0.0.txt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, callable from Scilab after `scicv_Init()`:
  - `readNet(model)` / `readNet(model, config)` → a `Net` pointer
  - `readNetFromONNX(path)` → `Net`
  - `blobFromImage(image, scalefactor, size, mean, swapRB, crop)` → `Mat` (4-D blob, `Mat_size` reports `[1 3 H W]`)
  - `Net_setInput(net, blob)`, `Net_forward(net)` → `Mat`, `Net_empty(net)` → boolean, `delete_Net(net)`
  - `new_ClassificationModel(path)`, `ClassificationModel_setInputParams(...)`, `ClassificationModel_setInputScale(...)`, `ClassificationModel_setInputMean(...)`, `delete_ClassificationModel(...)`
  - `NMSBoxes(...)`
  Task 6 uses `readNet`, `blobFromImage`, `Net_setInput`, `Net_forward`.

- [ ] **Step 1: Write the failing test**

Create `scicv/tests/unit_tests/dnn.tst`:

```scilab
// Scilab Computer Vision Module
// dnn module bindings — the wrapper surface, with no model file needed.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

scicv_Init();

// --- the entry points exist -------------------------------------------------
assert_checkequal(exists("readNet"), 1);
assert_checkequal(exists("readNetFromONNX"), 1);
assert_checkequal(exists("blobFromImage"), 1);
assert_checkequal(exists("Net_setInput"), 1);
assert_checkequal(exists("Net_forward"), 1);
assert_checkequal(exists("Net_empty"), 1);
assert_checkequal(exists("delete_Net"), 1);
assert_checkequal(exists("new_ClassificationModel"), 1);

// --- blobFromImage produces the NCHW blob shape -----------------------------
// A 40x60 BGR image -> a 1x3x10x20 blob when resized to 20x10 (Size is w,h).
img = Mat(40, 60, CV_8UC3);
blob = blobFromImage(img, 1.0 / 255.0, Size(20, 10), Scalar(0, 0, 0, 0), %t, %f);
assert_checkequal(Mat_size(blob), [1 3 10 20]);
delete_Mat(blob);
delete_Mat(img);

// --- readNet on a missing file fails loudly rather than returning a bad Net --
ierr = execstr("bad = readNet(TMPDIR + ""/scicv-no-such-model.onnx"");", "errcatch");
assert_checktrue(ierr <> 0);

// --- an empty Net reports itself empty --------------------------------------
n = Net();
assert_checkequal(Net_empty(n), %t);
delete_Net(n);
```

Create `scicv/tests/unit_tests/dnn.dia.ref` as an empty file — the `// <-- NO CHECK REF -->` tag means the harness compares nothing, matching `symbols-5.0.0.tst`'s convention.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','dnn',['no_check_error_output']); exit(0)"
```
Expected: failure at the first assertion — `assert_checkequal(exists("readNet"), 1)` gets `0`, because no dnn symbol is wrapped today.

- [ ] **Step 3: Write the ignore file**

Create `scicv/sci_gateway/c/swig/modules/opencv_dnn_ignore.i`:

```swig
// Scilab Computer Vision Module
// dnn: a surface trim, NOT a parse fix.
//
// MEASURED: SWIG regenerates cleanly with this file EMPTY (exit 0, 0 errors).
// These %ignores exist only to keep C++-only machinery out of the gateway
// table -- 4698 entries without them, 4570 with. Every name below is
// unreachable or unusable from a Scilab script, and a wrapped-but-broken
// entry is worse than an absent one: it looks callable and then misbehaves.
//
// So: if any line here ever causes trouble, DELETE IT. Nothing depends on it.

// Layer authoring/registration is a C++ extension point: LayerFactory takes a
// std::function constructor, LayerParams is a Dict of heterogeneous values.
// No Scilab script can implement a layer.
%ignore cv::dnn::LayerFactory;
%ignore cv::dnn::LayerParams;
%ignore cv::dnn::Dict;
%ignore cv::dnn::DictValue;
%ignore cv::dnn::Layer;

// Async inference hands back an AsyncArray whose get() blocks on an internal
// promise -- a threading model Scilab's single interpreter cannot drive.
%ignore cv::dnn::Net::forwardAsync;

// Buffer overloads take (const char*, size_t): a raw pointer plus a length a
// Scilab caller has no way to produce. The path-taking overloads cover the use.
%ignore cv::dnn::readNetFromONNX(const char *, size_t, int);
%ignore cv::dnn::readNetFromTensorflow(const char *, size_t, int);
%ignore cv::dnn::readNetFromTFLite(const char *, size_t, int);
```

Note on what these do *not* achieve: `%ignore cv::dnn::Net::getLayerTypes` and friends were tried in the dry-run and had **no effect** — the names still appeared in the table, because flattening the inline namespace changes how the qualified name matches. Do not add more `cv::dnn::Net::*` ignores expecting them to bite; verify against the generated table instead.

- [ ] **Step 4: Write the dnn module interface**

Create `scicv/sci_gateway/c/swig/modules/opencv_dnn.i`:

```swig
// Scilab Computer Vision Module
// dnn — neural-network inference (readNet, blobFromImage, Net, Model).
//
// libscicv already LINKS libopencv_dnn (and dnn_objdetect, dnn_superres); only
// this interface was missing, so nothing about the build changes here.
//
// NOTE for OpenCV 5: the Darknet and Caffe importers are GONE. Only
// readNetFromTensorflow, readNetFromTFLite, readNetFromONNX and the OpenVINO
// path survive, with readNet dispatching by extension. Any recipe built on
// .cfg/.weights or .prototxt/.caffemodel will not work on this build.

%{
#include "opencv2/dnn.hpp"
using namespace cv::dnn;
%}

// OpenCV wraps every dnn declaration in a VERSIONED inline namespace --
// cv::dnn::dnn5_v20260605 -- through the CV__DNN_INLINE_NS_BEGIN/END macros in
// opencv2/dnn/version.hpp. SWIG does not follow #include, so it never sees
// those macros and would choke on the bare identifiers. Defining them empty
// here (for SWIG's preprocessor only, not inside %{ %}) flattens the namespace
// to cv::dnn, which is also how user code spells it: version.hpp emits
// `using namespace CV__DNN_INLINE_NS;` so cv::dnn::readNet resolves in the
// generated C++ regardless. Defining them empty ALSO keeps the wrapped names
// stable across OpenCV point releases -- otherwise every bump to
// OPENCV_DNN_API_VERSION would rename every symbol.
#define CV__DNN_INLINE_NS_BEGIN
#define CV__DNN_INLINE_NS_END

%include modules/opencv_dnn_ignore.i

// dnn/dnn.hpp, not the opencv2/dnn.hpp umbrella: the umbrella is a one-line
// forwarder, and %include does not recurse.
%include "opencv2/dnn/dnn.hpp"
```

- [ ] **Step 5: Register the module**

In `scicv/sci_gateway/c/swig/scicv.i`, after the line `%include modules/opencv_features2d.i` and before the `scicv_legacy_constants.i` comment block, add:

```swig
%include modules/opencv_dnn.i
```

- [ ] **Step 6: Regenerate the wrapper**

Use Task 4a's script — it owns the flag set, the shadow-header patch and the output placement.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig
./regen.sh
```
Expected: exit 0 and `gateway table entries: 4570` — up from the 4132 the same script produced before `opencv_dnn.i` existed, i.e. **+438 dnn entries**. (Dropping the ignore file would give 4698; that difference is the trim, not an error.)

SWIG warnings about unknown base classes and shadowed overloads are normal for OpenCV headers and were present before this change. A hard **error** at `cvstd_wrapper.hpp:45` means the shadow was not applied — re-run Task 4a Step 3. An error mentioning `CV__DNN_INLINE_NS` means Step 4's `#define`s did not take effect; check they sit outside `%{ %}`.

This exact configuration was dry-run in a scratch copy on 2026-08-01 and produced a 7.7 MB `scicv_wrap.cxx` with zero errors, so a failure here is an environment difference, not an unknown.

- [ ] **Step 7: Rebuild the gateway**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
# The DEV-TREE interpreter, not scilab2027. The packaged app autoloads scicv, so
# libscicv is already loaded and ilib_build refuses to relink it -- the build
# silently does nothing and Scilab then spins at an interactive prompt forever
# (measured: 144 MB of "-->" in one run). build_macos.sce's own header documents
# scilab-cli as the intended driver.
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce \
    > /tmp/scicv-build.log 2>&1 &
```
Expected: `[1/3] … ierr=0`, `[2/3] … ierr=0`, `[3/3] … ierr=0`. The configure step inside `ilib_build` flakes intermittently with "C compiler cannot create executables" — if that appears, simply re-run, as `build_macos.sce`'s own header documents.

Then confirm the new symbols really landed:
```bash
grep -c '"readNet"\|"blobFromImage"\|"Net_forward"' sci_gateway/c/builder_gateway_c.sce
```
Expected: `3`.

- [ ] **Step 8: Run the test to verify it passes**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','dnn',['no_check_error_output']); exit(0)"
```
Expected: `dnn ......................... passed`.

- [ ] **Step 9: Guard the new symbols in the inventory test**

`tests/unit_tests/symbols-5.0.0.tst` asserts every name in `functions-5.0.0.txt` exists. Extend that list so a future regeneration that silently drops dnn is caught. Append to `scicv/tests/unit_tests/functions-5.0.0.txt`, immediately before the closing `];` line:

```
"readNet","_wrap_readNet"; ..
"readNetFromONNX","_wrap_readNetFromONNX"; ..
"readNetFromTensorflow","_wrap_readNetFromTensorflow"; ..
"readNetFromTFLite","_wrap_readNetFromTFLite"; ..
"blobFromImage","_wrap_blobFromImage"; ..
"blobFromImages","_wrap_blobFromImages"; ..
"imagesFromBlob","_wrap_imagesFromBlob"; ..
"NMSBoxes","_wrap_NMSBoxes"; ..
"NMSBoxesBatched","_wrap_NMSBoxesBatched"; ..
"softNMSBoxes","_wrap_softNMSBoxes"; ..
"getAvailableTargets","_wrap_getAvailableTargets"; ..
"new_Net","_wrap_new_Net"; ..
"delete_Net","_wrap_delete_Net"; ..
"Net_setInput","_wrap_Net_setInput"; ..
"Net_forward","_wrap_Net_forward"; ..
"Net_empty","_wrap_Net_empty"; ..
"new_Model","_wrap_new_Model"; ..
"Model_setInputParams","_wrap_Model_setInputParams"; ..
"Model_setInputScale","_wrap_Model_setInputScale"; ..
"Model_setInputMean","_wrap_Model_setInputMean"; ..
"Model_setInputSize","_wrap_Model_setInputSize"; ..
"Model_setInputSwapRB","_wrap_Model_setInputSwapRB"; ..
"Model_predict","_wrap_Model_predict"; ..
"new_ClassificationModel","_wrap_new_ClassificationModel"; ..
"delete_ClassificationModel","_wrap_delete_ClassificationModel"; ..
"ClassificationModel_clas","_wrap_ClassificationModel_classify"; ..
"new_DetectionModel","_wrap_new_DetectionModel"; ..
"DetectionModel_detect","_wrap_DetectionModel_detect"; ..
```

Every one of these was read out of a real generated table in the 2026-08-01 dry-run, not predicted.

**Note the truncation.** Scilab gateway primitive names are capped at 24 characters and SWIG truncates the *callable* name while keeping the full C symbol — hence `"ClassificationModel_clas","_wrap_ClassificationModel_classify"`. That truncated form is what a Scilab script must call. Check any name over 24 characters against the generated table rather than assuming the full spelling works.

Confirm the whole list against the freshly generated table:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
for n in readNet readNetFromONNX readNetFromTensorflow readNetFromTFLite \
         blobFromImage blobFromImages imagesFromBlob NMSBoxes NMSBoxesBatched \
         softNMSBoxes getAvailableTargets new_Net delete_Net Net_setInput \
         Net_forward Net_empty new_Model Model_setInputParams \
         Model_setInputScale Model_setInputMean Model_setInputSize \
         Model_setInputSwapRB Model_predict new_ClassificationModel \
         delete_ClassificationModel ClassificationModel_clas \
         new_DetectionModel DetectionModel_detect; do
  grep -q "\"$n\"," sci_gateway/c/builder_gateway_c.sce || echo "MISSING: $n"
done
```
Expected: no output. For any `MISSING:` name, find the real spelling with
`grep -o '"[A-Za-z_0-9]*"' sci_gateway/c/builder_gateway_c.sce | sort -u | grep -i <stem>`
and correct the list — never the wrapper.

- [ ] **Step 10: Run the whole scicv suite for regressions**

Adding a module to the wrapper regenerates every wrapper function, so the existing tests are the regression gate.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv',[],['no_check_error_output']); exit(0)"
```
Expected: the same pass count as before this task, plus `dnn`. The baseline is 30/30 (recorded when scicv was ported to OpenCV 5 in `a81cf14`), so expect 31/31. Any newly failing test is a regression from the regeneration — fix it before committing.

- [ ] **Step 11: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-dnn.txt <<'MSG'
swig: wrap the OpenCV dnn module

libscicv already linked libopencv_dnn, libopencv_dnn_objdetect and
libopencv_dnn_superres -- the SWIG interface simply never declared any of it,
so readNet/blobFromImage/Net were unreachable from Scilab. modules/opencv_dnn.i
joins the eight existing module interfaces; the build is unchanged.

dnn's declarations live in a VERSIONED inline namespace (cv::dnn::dnn5_v<date>)
behind CV__DNN_INLINE_NS_BEGIN/END, which SWIG never sees because it does not
follow #include. Defining those macros empty for SWIG's preprocessor flattens
the namespace to cv::dnn -- which is what user code spells anyway -- and keeps
the wrapped names stable across OpenCV point releases.

The ignore list drops the layer-authoring registry, async inference, and the
raw-buffer overloads: none are reachable from a Scilab script.

Note for callers: OpenCV 5 removed the Darknet and Caffe importers. Only
TensorFlow, TFLite, ONNX and OpenVINO models load.

tests/unit_tests/dnn.tst covers the surface with no model file; the new
symbols are added to functions-5.0.0.txt so symbols-5.0.0.tst guards them.
MSG
git add sci_gateway/c/swig/modules/opencv_dnn.i \
        sci_gateway/c/swig/modules/opencv_dnn_ignore.i \
        sci_gateway/c/swig/scicv.i \
        sci_gateway/c/scicv_wrap.cxx \
        sci_gateway/c/builder_gateway_c.sce \
        tests/unit_tests/dnn.tst tests/unit_tests/dnn.dia.ref \
        tests/unit_tests/functions-5.0.0.txt
git commit -F /tmp/msg-dnn.txt
git push gitlab main && git push origin main
```

---

### Task 5: Reconcile the OpenCV version pin

**Files:**
- Modify: `scicv/thirdparty/versions.sce`
- Modify: `scicv/sci_gateway/c/buildflags.sci`
- Modify: `scicv/build_macos.sce`
- Create: `scicv/macros/scicv_opencv_version.sci`
- Create: `scicv/tests/unit_tests/opencv_version.tst`, `scicv/tests/unit_tests/opencv_version.dia.ref`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `scicv_opencv_version()` — a Scilab function taking no arguments and returning a string like `"5.0.0"`, the version of the OpenCV the loaded gateway was built against, read from the wrapped `CV_VERSION_MAJOR` / `CV_VERSION_MINOR` / `CV_VERSION_REVISION` constants. Also `getOpenCVVersion()` in `buildflags.sci`, returning the same string from `pkg-config --modversion` at build time.

**The actual problem:** `OPENCV_VERSION = "4.8.1"` reads like the version scicv uses; it is not. On macOS nothing reads it — `buildflags.sci` resolves everything through pkg-config, which is why a Homebrew bump from 4.8.1 to 5.0.0 changed the linked library without touching this file. The constant governs only the bundled Windows/Linux prebuilt payload that `builder.sce` downloads. Blindly bumping it to `"5.0.0"` would change what that download asks for, against a URL nobody here has verified — so the reconciliation is to state what the constant means and make the *real* version visible at build time and at runtime instead.

- [ ] **Step 1: Write the failing test**

Create `scicv/tests/unit_tests/opencv_version.tst`:

```scilab
// Scilab Computer Vision Module
// The version scicv reports must be the version it was built against.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

scicv_Init();

v = scicv_opencv_version();

// A non-empty dotted triple.
assert_checktrue(type(v) == 10);
assert_checktrue(size(strsplit(v, "."), "*") == 3);

// It must agree with the wrapped constants, which come from the headers the
// gateway actually compiled against.
expected = msprintf("%d.%d.%d", CV_VERSION_MAJOR, CV_VERSION_MINOR, CV_VERSION_REVISION);
assert_checkequal(v, expected);

// And with the library the gateway is linked to. OPENCV_VERSION in
// thirdparty/versions.sce is deliberately NOT compared: it pins the bundled
// Windows/Linux prebuilt payload, not the macOS pkg-config resolution.
assert_checktrue(CV_VERSION_MAJOR >= 4);
```

Create `scicv/tests/unit_tests/opencv_version.dia.ref` as an empty file.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','opencv_version',['no_check_error_output']); exit(0)"
```
Expected: failure — `scicv_opencv_version` is undefined.

- [ ] **Step 3a: Expose the version constants to SWIG — the accessor cannot work without this**

**Measured 2026-08-01:** `CV_VERSION_MAJOR` / `_MINOR` / `_REVISION` are **not wrapped**. Zero occurrences in `sci_gateway/c/scicv_wrap.cxx`; absent from the 2434-entry `tests/unit_tests/variables-5.0.0.txt`. They live in `opencv2/core/version.hpp` (lines 8–10, plain integer `#define`s), and no `.i` file ever `%include`s it — `modules/opencv_core.i` pulls in `interface.h`, `cvdef.h`, `types.hpp`, `mat.hpp`, `core.hpp`, `base.hpp` and `utility.hpp`, but never `version.hpp`. The compiler gets the macros transitively through `core.hpp`; SWIG does not, because it does not recurse into `#include`.

In `sci_gateway/c/swig/modules/opencv_core.i`, add immediately after the `%include "opencv2/core/cvdef.h"` line:

```swig
// CV_VERSION_MAJOR / _MINOR / _REVISION. Not pulled in transitively: SWIG does not
// recurse into #include, so without this the constants are invisible to Scilab AND
// version-gated blocks in other headers evaluate against an undefined (=0) major.
// regen.sh also passes -DCV_VERSION_MAJOR/-DCV_VERSION_MINOR for the guard case; this
// %include is what makes the values reachable from Scilab code.
%include "opencv2/core/version.hpp" // CV_VERSION_MAJOR, CV_VERSION_MINOR, CV_VERSION_REVISION
```

Do **not** rely on the `CV_VERSION` *string* being wrapped — `version.hpp:19` builds it by macro concatenation (`CVAUX_STR(...) "." ...`), which SWIG may not fold. The three integers are all the accessor needs.

Then add the three names to `tests/unit_tests/variables-5.0.0.txt` so `symbols-5.0.0.tst` guards them, inserting before the closing `];`:

```
"CV_VERSION_MAJOR";
"CV_VERSION_MINOR";
"CV_VERSION_REVISION";
```

**Rejected alternative, for the record:** having `scicv_opencv_version()` shell out to `pkg-config` at runtime. That reports the *installed* OpenCV, not the one the gateway was compiled against — and those diverge after a `brew upgrade`, which is precisely the drift this task exists to expose.

- [ ] **Step 3: Add the runtime accessor**

Create `scicv/macros/scicv_opencv_version.sci`:

```scilab
// Scilab Computer Vision Module
//
// scicv_opencv_version() — the OpenCV version this gateway was BUILT against.
//
// Read from the wrapped CV_VERSION_* constants, so it can never drift: they
// come from the headers libscicv compiled against. Deliberately not read from
// thirdparty/versions.sce, which pins the bundled Windows/Linux prebuilt
// payload and says nothing about a macOS build (where buildflags.sci resolves
// OpenCV through pkg-config).

function v = scicv_opencv_version()
    v = msprintf("%d.%d.%d", CV_VERSION_MAJOR, CV_VERSION_MINOR, CV_VERSION_REVISION);
endfunction
```

- [ ] **Step 4: Document what the version pin governs**

Replace the body of `scicv/thirdparty/versions.sce` with:

```scilab
// Scilab Computer Vision Module
// Copyright (C) 2025 - Dassault Systèmes S.E. - Vincent COUVERT

// SCOPE: these constants pin the BUNDLED THIRD-PARTY PAYLOAD that builder.sce
// downloads for Windows and Linux. They are NOT the version scicv is built
// against on macOS: sci_gateway/c/buildflags.sci resolves the installed OpenCV
// through pkg-config (probing opencv6, opencv5, opencv4, opencv in that order)
// so a `brew upgrade opencv` major bump keeps building. That is why this file
// still said 4.8.1 while the macOS gateway was linking 5.0.0.
//
// To see the version actually in use: build_macos.sce prints it, and
// scicv_opencv_version() returns it at runtime from the wrapped CV_VERSION_*
// constants.
//
// Do not "fix" the drift by bumping OPENCV_VERSION to match a macOS build --
// that would change which prebuilt the Windows/Linux path downloads, from a
// URL nobody has verified for the new value.

OPENCV_VERSION = "4.8.1"

FFMPEG_VERSION = "7.1" // Latest version

OPENH264_VERSION = "1.8.0"  // Version imposed by FFmpeg 
```

- [ ] **Step 5: Report the resolved version at build time**

In `scicv/sci_gateway/c/buildflags.sci`, add after `opencv_pkgconfig()`:

```scilab
// The version of the OpenCV that opencv_pkgconfig_name() resolved. This is the
// truth for a macOS build; OPENCV_VERSION in thirdparty/versions.sce pins the
// bundled Windows/Linux prebuilt instead.
function v = getOpenCVVersion()
    v = opencv_pkgconfig("--modversion");
endfunction
```

In `scicv/build_macos.sce`, after the `root = get_absolute_file_path(...)` line, add:

```scilab
// Report the OpenCV actually resolved, so a Homebrew major bump is visible in
// the build log instead of being discovered later from a linker error.
exec(fullfile(root, "sci_gateway", "c", "buildflags.sci"), -1);
mprintf("[0/3] OpenCV resolved via pkg-config: %s\n", getOpenCVVersion());
```

- [ ] **Step 6: Rebuild macros and run the test to verify it passes**

Adding `version.hpp` to the SWIG interface changes the wrapper, so this step regenerates and rebuilds before testing.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig && ./regen.sh
cd /Users/josemoya/Projects/SciLabProjects/scicv
# BUILD with the dev-tree interpreter, never scilab2027: the packaged app autoloads
# scicv, so libscicv is already loaded, ilib_build refuses to relink, and Scilab
# spins at an interactive prompt forever (measured: 144 MB of "-->" in one run).
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce \
    > /tmp/scicv-build.log 2>&1 &
# TEST with the packaged CLI once the build is done -- autoload is fine (helpful, even) here.
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','opencv_version',['no_check_error_output']); exit(0)"
```
Expected: the build banner prints `[0/3] OpenCV resolved via pkg-config: 5.0.0`, `regen.sh` reports a gateway table larger than before (the three `CV_VERSION_*` constants are now registered), and the test passes.

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-version.txt <<'MSG'
thirdparty: say what OPENCV_VERSION actually pins, and report the real one

versions.sce said 4.8.1 while the macOS gateway linked OpenCV 5.0.0. The
constant was never wrong so much as mislabelled: it pins the bundled
Windows/Linux prebuilt that builder.sce downloads, and buildflags.sci resolves
macOS through pkg-config, so a Homebrew major bump moved the linked library
without touching this file.

Bumping it to 5.0.0 would have changed which prebuilt the Windows/Linux path
fetches, from a URL nobody has verified. Instead the file now documents its
scope, build_macos.sce prints the version pkg-config resolved, and
scicv_opencv_version() returns it at runtime from the wrapped CV_VERSION_*
constants -- which come from the headers the gateway compiled against and so
cannot drift.
MSG
git add thirdparty/versions.sce sci_gateway/c/buildflags.sci build_macos.sce \
        macros/scicv_opencv_version.sci \
        tests/unit_tests/opencv_version.tst tests/unit_tests/opencv_version.dia.ref
git commit -F /tmp/msg-version.txt
git push gitlab main && git push origin main
```

---

### Task 6: Realtime object-identification demo

**Files:**
- Create: `scicv/demos/camera_classify/fetch-model.sh`
- Create: `scicv/demos/camera_classify/models.sha256`
- Create: `scicv/demos/camera_classify/camera_classify.sce`

**Interfaces:**
- Consumes: `readNet`, `blobFromImage`, `Net_setInput`, `Net_forward`, `Net_empty`, `delete_Net` from Task 4b; camera permission from Tasks 1–2; `VideoCapture` and `cvMatExtract` (both already wrapped).
- Produces: the end-to-end answer to "camera + realtime object identification" — a loop that captures frames and prints the top ImageNet label per frame.

**Model choice, and why not detection:** OpenCV 5 dropped the Darknet and Caffe importers, so the classic YOLO-`.weights` and MobileNet-SSD-`.caffemodel` recipes are unavailable. Among what remains, **classification** has trivial, verifiable post-processing — forward, then argmax over 1000 scores — whereas every surviving ONNX *detector* needs bespoke decoding (NanoDet's generalized-focal-loss distribution, YOLOv8's 84×8400 transposed output) that cannot be validated without the model in hand. This task delivers classification. Bounding-box detection is the natural follow-on and is noted at the end of the demo file.

- [ ] **Step 1: Write the failing test**

The demo *is* the test — it self-checks and exits nonzero on failure, matching the toolbox-smoke convention. Create `scicv/demos/camera_classify/camera_classify.sce`:

```scilab
// Scilab Computer Vision Module
//
// camera_classify.sce — live object identification from the camera.
//
//   camera → VideoCapture_read → blobFromImage → Net_forward → top-1 label
//
// Prerequisites:
//   1. ./fetch-model.sh          (downloads MobileNetV2 + ImageNet labels)
//   2. camera permission — the first run raises the macOS privacy prompt
//
//   scilab2027 -nb -f demos/camera_classify/camera_classify.sce [nframes]
//
// Exit status: 0 = at least one frame classified, 1 = failure (reason printed).
// exit(), never quit(): quit() ignores its argument and would report success.

here = get_absolute_file_path("camera_classify.sce");
exec(fullfile(here, "..", "..", "loader.sce"), -1);
scicv_Init();

NFRAMES = 60;
if exists("nframes") then NFRAMES = nframes; end

model  = fullfile(here, "mobilenetv2-12.onnx");
labels = fullfile(here, "synset.txt");

if ~isfile(model) | ~isfile(labels) then
    mprintf("FAIL: model missing. Run %s first.\n", fullfile(here, "fetch-model.sh"));
    exit(1);
end

// MobileNetV2 expects 224x224 RGB with per-channel ImageNet normalization:
//     out = (pixel/255 - mean_c) / std_c
// with mean = (0.485, 0.456, 0.406) and std = (0.229, 0.224, 0.225), in RGB
// order because swapRB is on. Rearranged for blobFromImage's
// (pixel - MEAN) * scalefactor:
//     MEAN  = 255 * mean_c            -> exact, blobFromImage takes a Scalar
//     SCALE = 1 / (255 * std_c)       -> per channel, and blobFromImage takes
//                                        only ONE scalefactor for all three
//
// There is no way to apply the per-channel scale afterwards: scicv exposes no
// Mat * Scalar (no Mat___mul__; Mat_mul is cv::Mat::mul against another Mat)
// and Mat_convertTo's alpha is a single scalar too. ClassificationModel's
// setInputScale(Scalar) would do it exactly, but its classify() returns through
// CV_OUT int& / float& out-params and typemaps/opencv_typemaps.i has no OUT
// typemap for those, so the results never reach Scilab.
//
// So use one scale built from the mean of the three stds, 0.226:
//     1 / (255 * 0.226) = 1/57.63
// The three stds span 0.224..0.229, so the per-channel gain error is at most
// 1.3% -- far below what changes a top-1 argmax, and the mean subtraction
// (which dominates) stays exact. OpenCV's own MobileNet samples do the same.
MEAN  = Scalar(123.675, 116.28, 103.53, 0);
SCALE = 1.0 / 57.63;
SIDE  = Size(224, 224);

classes = mgetl(labels);
if size(classes, "*") < 1000 then
    mprintf("FAIL: %s holds %d labels, expected 1000\n", labels, size(classes, "*"));
    exit(1);
end

net = readNet(model);
if Net_empty(net) then
    mprintf("FAIL: readNet returned an empty Net for %s\n", model);
    exit(1);
end

cap = VideoCapture(0);
if VideoCapture_isOpened(cap) <> %T then
    mprintf("FAIL: camera did not open. Check System Settings > Privacy &\n");
    mprintf("      Security > Camera, and that a camera is attached.\n");
    delete_VideoCapture(cap);
    delete_Net(net);
    exit(1);
end

classified = 0;
t0 = getdate("s");
frame = [];

for k = 1:NFRAMES
    if VideoCapture_read(cap, frame) <> %T then
        continue;
    end
    sz = Mat_size(frame);
    if sz == [] | sz(1) == 0 then
        continue;
    end

    // blobFromImage does resize + swapRB + (x - MEAN) * SCALE in one pass.
    blob = blobFromImage(frame, SCALE, SIDE, MEAN, %t, %f);

    Net_setInput(net, blob);
    out = Net_forward(net);           // 1 x 1000 scores, CV_32F
    // cvMatExtract is scicv's Mat -> Scilab-matrix converter; macros/%Mat_e.sci
    // is built on it, so out(:) would work too.
    scores = cvMatExtract(out);

    [conf, idx] = max(scores);
    classified = classified + 1;
    mprintf("frame %3d: %-40s (score %.3f)\n", k, classes(idx), conf);

    delete_Mat(out);
    delete_Mat(blob);
end

dt = getdate("s") - t0;
delete_VideoCapture(cap);
delete_Net(net);

if classified == 0 then
    mprintf("FAIL: no frame was classified in %d attempts\n", NFRAMES);
    exit(1);
end

mprintf("PASS: classified %d frames in %d s (%.1f fps)\n", classified, dt, classified / max(dt, 1));

// NEXT: bounding-box detection. OpenCV 5 dropped the Darknet and Caffe
// importers, so it needs an ONNX detector (NanoDet-Plus-m from opencv_zoo, or
// YOLOv8n) plus its decoding — generalized-focal-loss distributions for the
// former, an 84x8400 transposed head for the latter — then NMSBoxes, which is
// already wrapped.
exit(0);
```

- [ ] **Step 2: Run it to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -f demos/camera_classify/camera_classify.sce ; echo "rc=$?"
```
Expected: `FAIL: model missing. Run …/fetch-model.sh first.`, `rc=1`.

- [ ] **Step 3: Write the model fetcher**

Create `scicv/demos/camera_classify/fetch-model.sh`:

```bash
#!/usr/bin/env bash
# Fetch the MobileNetV2 ONNX classifier + ImageNet labels for camera_classify.sce.
#
# Same shape as the engine's fetch-thirdparty.sh: pinned URLs, sha256-verified,
# idempotent. Downloads are NOT committed -- they are ~14 MB of binary that does
# not belong in git.
#
# OpenCV 5 removed the Darknet and Caffe importers, so an ONNX model is not a
# preference here, it is the only option that loads.
#
#   ./fetch-model.sh          # fetch + verify
#   ./fetch-model.sh --print  # print the sha256 of what is on disk and exit
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SUMS="$HERE/models.sha256"

MODEL_URL="https://github.com/onnx/models/raw/main/validated/vision/classification/mobilenet/model/mobilenetv2-12.onnx"
LABEL_URL="https://raw.githubusercontent.com/onnx/models/main/validated/vision/classification/synset.txt"

fetch() {  # <url> <dest>
    if [ -f "$2" ]; then
        echo "have $(basename "$2")"
        return
    fi
    echo "fetching $(basename "$2")…"
    curl -fsSL --retry 3 -o "$2.part" "$1"
    mv "$2.part" "$2"
}

fetch "$MODEL_URL" "$HERE/mobilenetv2-12.onnx"
fetch "$LABEL_URL" "$HERE/synset.txt"

if [ "${1:-}" = "--print" ]; then
    ( cd "$HERE" && shasum -a 256 mobilenetv2-12.onnx synset.txt )
    exit 0
fi

if [ ! -f "$SUMS" ]; then
    echo "ERROR: $SUMS missing. Record the pins with:  ./fetch-model.sh --print > models.sha256" >&2
    exit 1
fi

( cd "$HERE" && shasum -a 256 -c models.sha256 )
echo "OK — model and labels verified."
```

Then `chmod +x scicv/demos/camera_classify/fetch-model.sh`.

- [ ] **Step 4: Fetch and record the pins**

The checksums must be the real bytes, so derive them rather than inventing them.

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/demos/camera_classify
./fetch-model.sh --print > models.sha256
cat models.sha256
./fetch-model.sh
```
Expected: `models.sha256` holds two lines (each a 64-hex digest plus a filename), and the second invocation ends `OK — model and labels verified.` Add `mobilenetv2-12.onnx` and `synset.txt` to `scicv/.gitignore` so the binaries stay out of the repo.

- [ ] **Step 5: Confirm the Mat conversion path — no new macros needed**

An earlier draft of this plan invented `Mat_mul_scalar` and `Mat_to_double`. Neither is needed and neither could have worked: the gateway table has no `Mat___mul__` and no `Mat_at_float`. The demo uses `cvMatExtract`, which is a real gateway function — `macros/%Mat_e.sci` is built on it.

Verify before running the demo:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
grep -c '"cvMatExtract",' sci_gateway/c/builder_gateway_c.sce
```
Expected: `1` (or more, since the builder emits a legacy table too). If it is `0`, find the converter with
`grep -o '"cv[A-Za-z_0-9]*"' sci_gateway/c/builder_gateway_c.sce | sort -u`
and use that name in the demo instead.

No macro-library rebuild is required for this task — the demo calls gateway functions directly.

- [ ] **Step 6: Run the demo to verify it passes**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -f demos/camera_classify/camera_classify.sce ; echo "rc=$?"
```
Expected: 60 `frame NNN: <label> (score X.XXX)` lines followed by `PASS: classified 60 frames in N s (F fps)` and `rc=0`. Point the camera at something recognizable — a keyboard, a mug, a monitor — and confirm the labels are plausible rather than random; a model wired up with wrong normalization still emits confident nonsense, so a plausible label is the real evidence the preprocessing is right. Record two or three observed labels in the task report.

- [ ] **Step 7: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-demo.txt <<'MSG'
demos: live camera object identification

camera → VideoCapture_read → blobFromImage → Net_forward → top-1 ImageNet
label, using the dnn bindings added earlier in this series.

Classification rather than detection: OpenCV 5 dropped the Darknet and Caffe
importers, so the classic YOLO-.weights and MobileNet-SSD-.caffemodel recipes
do not load at all, and every surviving ONNX detector needs bespoke decoding
(NanoDet's focal-loss distributions, YOLOv8's transposed head). Argmax over
1000 scores is verifiable by eye; that decoding is not. The follow-on is noted
in the demo.

fetch-model.sh pins the MobileNetV2 ONNX model and the ImageNet labels by
sha256, following fetch-thirdparty.sh; the binaries are gitignored.

ImageNet's per-channel std is approximated by a single scale (1/57.63, the mean
of 0.229/0.224/0.225 -- at most 1.3% per-channel gain error) because
blobFromImage takes only one scalefactor and scicv exposes no way to scale a
blob per channel afterwards. ClassificationModel.setInputScale(Scalar) would be
exact, but classify() returns through CV_OUT int&/float& and there is no OUT
typemap for those, so its results never reach Scilab.
MSG
git add demos/camera_classify/fetch-model.sh \
        demos/camera_classify/models.sha256 \
        demos/camera_classify/camera_classify.sce .gitignore
git commit -F /tmp/msg-demo.txt
git push gitlab main && git push origin main
```

---

## Risks and How Each Is Handled

| Risk | Handling |
|---|---|
| `-sectcreate` trips build parity | `_fingerprint_exe` records only build_version / install_name / deps / rpaths; a section is none of those. Task 1 Step 6 verifies rather than assumes, and forbids re-baselining. |
| TCC attributes the request to the bundle, not the binary | Tasks 1 and 2 cover both routes with the same string; Task 3 proves at least one works end to end. |
| Rebuilt gateway is AMFI-killed at load (SIGKILL, empty stderr) even though `codesign --verify` passes | Known macOS trap for rebuilt toolbox gateways. If `scicv_Init()` dies silently after Task 4b, `codesign --force --sign - sci_gateway/c/libscicv.dylib` and check `~/Library/Logs/DiagnosticReports/*.ips` for `CODESIGNING`. |
| The wrapper cannot be regenerated at all | **This was real, and measured.** Task 4a fixes it — untracked shadow headers, GNU-only `sed` that silently truncated its output on macOS, and a stale line-number patch — and proves the fix from a fresh `git clone`. |
| SWIG chokes on dnn's inline namespace | Dry-run 2026-08-01: it does not. `CV__DNN_INLINE_NS_BEGIN/_END` defined empty for SWIG's preprocessor gives exit 0, zero errors. Task 4b Step 6 names the exact error text that would mean the `#define`s did not take. |
| Wrapper regeneration silently changes existing symbols | Task 4a Step 3 requires the regenerated wrapper to match the committed one before any interface change; Task 4b Step 10 then runs the full suite against the recorded 30/30 baseline. |
| SWIG symbol names differ from expectation | The list in Task 4b Step 9 was read out of a real generated table, not predicted. Names over 24 characters are truncated by SWIG (`ClassificationModel_clas`), and Step 9 re-verifies every entry against the freshly generated table. |
| Demo model URL rots | `fetch-model.sh` fails loudly on a 404 (`curl -fsSL`), and the sha256 pins catch a silently changed file. Both URLs were HTTP-verified 2026-08-01. |
| Demo preprocessing is approximate | Deliberate and bounded: one scale instead of three (≤1.3% per-channel gain error) because scicv exposes no per-channel blob scaling and `ClassificationModel.classify`'s `CV_OUT int&`/`float&` have no OUT typemap. Task 6 Step 6 requires plausible labels, not just a nonzero exit — confidently wrong labels are what bad preprocessing looks like. |
| No camera on the machine | Task 3 and Task 6 print an explicit diagnostic and exit 1 rather than hanging. |

## Not In Scope

- `etc/Info.plist` / `etc/Info.plist.in` — generated, legacy upstream `.app` layout, unused by either launcher here.
- `NSMicrophoneUsageDescription` — `cv::VideoCapture` opens video only; add it if and when audio capture appears.
- Bounding-box detection — needs an ONNX detector plus its decoder; noted at the end of `camera_classify.sce`.
- `libopencv_dnn_superres` / `libopencv_dnn_objdetect` APIs — linked but not wrapped; add module interfaces the same way if wanted.
- Bumping `OPENCV_VERSION` to 5.0.0 — would change an unverified Windows/Linux download; Task 5 documents instead.
