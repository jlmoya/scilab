# OpenCV Module Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose every practically-wrappable OpenCV module through scicv's SWIG interface — 29 modules, ~1295 declarations — taking scicv from partial coverage to functional completeness.

**Architecture:** Purely additive interface work. All 29 target modules are **already linked** into `libscicv.dylib`; only the SWIG declarations are missing, exactly as was true of `dnn`. Each module is one `modules/opencv_<name>.i`, one `%include` line in `scicv.i`, a regeneration via `./regen.sh`, and an inventory entry. The plan front-loads two things that make the other 27 cheap: shared exception-safety infrastructure, and two fully-worked reference modules — one function-shaped, one factory-shaped — that later batches copy rather than re-derive.

**Tech Stack:** SWIG 4.4.1, OpenCV 5.0.0 (Homebrew, via `pkg-config opencv5`), Scilab 2027 `.tst` harness, `sci_gateway/c/swig/regen.sh`.

## Global Constraints

- **NO AI-attribution trailers.** No `Co-Authored-By`, no `Claude-Session`, no "Generated with" in any commit message. Absolute user mandate.
- Commit directly on `main`. No branches, no worktrees. Push BOTH remotes: `git push gitlab main && git push origin main`.
- Use `git commit -F <file>` with a message file — never multi-line `-m`.
- **Never run `sudo`.**
- **OpenCV is resolved through pkg-config, never hardcoded.** No `/opt/homebrew/opt/opencv/...` literal in any source file. Probe order `opencv6 opencv5 opencv4 opencv`.
- **Regenerate only via `sci_gateway/c/swig/regen.sh`.** Never hand-roll a `swig` command — the flag set, shadow-header patch and `-DCV_VERSION_MAJOR/MINOR` guards live there.
- **Never build the gateway with `scilab2027`.** The packaged app autoloads scicv, so `libscicv` is already loaded, `ilib_build` refuses to relink, and Scilab spins at an interactive prompt (measured: 144 MB of `-->` in one run). Use `/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce`, output redirected, run in background.
- **Every batch `.sce` ends with `exit(0)`/`exit(1)`** — `quit` ignores its argument and cannot signal failure.
- Repo: `/Users/josemoya/Projects/SciLabProjects/scicv`.

---

## Verified Facts (measured 2026-08-01)

- **All 29 target modules are already linked** into `libscicv.dylib` (51 OpenCV libs total). Checked each against `otool -L`; none is missing. **No build-system or link changes are required by this plan.**
- Currently wrapped: 9 module interfaces reaching ~24 headers — `core` (+base/mat/types/utility), `imgproc`, `imgcodecs`, `highgui`, `videoio`, `video`, `objdetect`, `xobjdetect`, `photo`, `features`, `xfeatures2d`, `calib3d`, `bgsegm`, `optflow`, `rgbd/linemod`, `ptcloud/depth`, `dnn`.
- Unwrapped modules carrying API: **35**, totalling **1368** `CV_EXPORTS_W`/`CV_WRAP` declarations.
- **Excluded as not practically wrappable** (6 modules, ~73 decls): `gapi` (11,369 LOC, **327 template declarations** — a graph API built on variant/template metaprogramming SWIG cannot usefully consume), `viz` (needs VTK and its own 3D window, duplicating the in-tree Vulkan renderer), `flann` (templated index types), `sfm` (requires Ceres/Eigen; 0 `CV_EXPORTS_W`, i.e. a C++-only API), `core_detect`, `opencv_modules`. Target after exclusions: **29 modules, ~1295 declarations**.
- `%exception` exists in **exactly one** interface today (`opencv_dnn.i`). The other 8 have none, so an OpenCV C++ exception from `imread`, `VideoCapture` etc. aborts the whole Scilab process rather than raising a catchable error.
- In the Scilab backend, `SWIG_exception` expands to bare `SWIG_Scilab_Error(code,msg);` — **it does not return**. Only `SWIG_exception_fail` appends `SWIG_fail` → `return SWIG_ERROR;`. Using the wrong one converts an abort into a segfault plus published garbage results.
- `SWIG_fail` is a bare `return SWIG_ERROR;`; the generated wrapper has **no `fail:` label anywhere** (0 of 212K lines). Cleanup emitted after `$action` is therefore skipped on the exception path unless `$cleanup` is invoked explicitly in the catch body.
- Scilab gateway primitive names are capped at **24 characters**. SWIG emits a truncated alias in the dead `if ver(1) < 6` branch (`builder_gateway_c.sce:23`) and the **untruncated** name in the live `else` branch (`:2516`). Inventory files must carry the untruncated form.
- `Size` typemaps read `[height, width]`, **not** OpenCV's `(width, height)` — `typemaps/Size_typemaps.i:25-26` assigns `height = piValues[0]`.
- Baseline before this plan: gateway table **4562** rows; scicv suite **31/31**.

### Per-module measurements

Shape drives the work far more than size. `PTR` = `Ptr<>` occurrences, `FACT` = `static Ptr<...> create` factories, `OUTP` = `CV_OUT`/`OutputArray` occurrences.

**Group F — function-over-Mat (few or no factories; work is out-parameter typemaps)**

| module | hdrs | LOC | API | PTR | FACT | OUTP |
|---|---|---|---|---|---|---|
| xphoto | 7 | 768 | 44 | 4 | 0 | 8 |
| img_hash | 8 | 419 | 30 | 7 | 0 | 7 |
| intensity_transform | 1 | 112 | 5 | 0 | 0 | 2 |
| fuzzy | 5 | 495 | 15 | 0 | 0 | 16 |
| hfs | 1 | 153 | 18 | 1 | 0 | 0 |
| phase_unwrapping | 3 | 241 | 7 | 1 | 0 | 2 |
| xstereo | 3 | 469 | 14 | 3 | 0 | 2 |
| stereo | 1 | 489 | 51 | 2 | 0 | 14 |
| surface_matching | 6 | 1203 | 28 | 2 | 0 | 3 |
| shape | 6 | 648 | 66 | 14 | 0 | 4 |
| geometry | 5 | 3985 | 107 | 3 | 2 | 129 |
| calib | 1 | 1526 | 10 | 0 | 0 | 48 |
| ccalib | 4 | 868 | 8 | 22 | 0 | 42 |

**Group P — `Ptr<>` + `create()` factories (work is factory/abstract-base handling)**

| module | hdrs | LOC | API | PTR | FACT | OUTP |
|---|---|---|---|---|---|---|
| ml | 3 | 2064 | 225 | 39 | 13 | 27 |
| ximgproc | 27 | 3707 | 247 | 37 | 2 | 70 |
| tracking | 10 | 3269 | 38 | 56 | 18 | 8 |
| face | 10 | 1721 | 73 | 27 | 9 | 16 |
| text | 5 | 1164 | 41 | 24 | 6 | 8 |
| line_descriptor | 2 | 1528 | 43 | 10 | 7 | 9 |
| stitching | 2 | 635 | 35 | 52 | 1 | 7 |
| quality | 8 | 636 | 37 | 12 | 0 | 6 |
| saliency | 3 | 726 | 39 | 9 | 0 | 14 |
| bioinspired | 5 | 857 | 36 | 4 | 0 | 7 |
| structured_light | 4 | 458 | 18 | 4 | 0 | 9 |
| dnn_superres | 1 | 133 | 10 | 1 | 0 | 3 |
| freetype | 1 | 203 | 7 | 2 | 0 | 2 |
| wechat_qrcode | 1 | 73 | 5 | 1 | 0 | 1 |
| rapid | 1 | 164 | 17 | 3 | 0 | 13 |
| plot | 1 | 120 | 21 | 2 | 0 | 1 |

**Decision recorded, not open:** libTorch is explicitly **out of scope**. sciTorch has no SWIG layer by design (7 hand-written gateway verbs) and is not currently built. libTorch's C++ surface is ATen — 2000+ operators, `c10::intrusive_ptr`, expression templates, a runtime dispatcher; PyTorch's own Python bindings are ~50k lines of hand-written pybind11 plus generation from `native_functions.yaml`. SWIG is the wrong tool. If Torch breadth is wanted later, route through PIMS to Python `torch`; keep the hand-written gateway for latency-sensitive inference.

---

## File Structure

| File | Responsibility |
|---|---|
| `sci_gateway/c/swig/scicv_exception.i` | **new** — the single shared `%exception` block; included once per module interface so the pattern is defined in one place rather than copied 29 times |
| `sci_gateway/c/swig/modules/opencv_<name>.i` | **new ×29** — one per module: `%{ #include %}`, ignores, `%include` of the module headers |
| `sci_gateway/c/swig/modules/opencv_<name>_ignore.i` | **new**, only where a module needs one — surface trim, never a parse fix |
| `sci_gateway/c/swig/scicv.i` | one `%include` line added per module, in dependency order |
| `sci_gateway/c/scicv_wrap.cxx`, `builder_gateway_c.sce` | **regenerated** by `regen.sh` — never hand-edited |
| `tests/unit_tests/<module>.tst` + `.dia.ref` | **new ×29** — per-module smoke: symbols exist, one real call, one error path |
| `tests/unit_tests/functions-5.0.0.txt` | append each module's new gateway names (untruncated form) |
| `tests/unit_tests/variables-5.0.0.txt` | append each module's new constants |
| `CHANGELOG.md` | record the coverage jump |

**Task ordering.** Task 1 (shared exception safety) must precede everything — it fixes a live crash class and every later module depends on the shared file. Task 2 and Task 3 are the two reference implementations; all batch tasks copy them. Batches are independent of each other and may be reordered freely.

---

### Task 1: Shared exception safety + retrofit the 8 existing modules

**Files:**
- Create: `sci_gateway/c/swig/scicv_exception.i`
- Modify: `sci_gateway/c/swig/modules/opencv_dnn.i` (replace its inline block with the shared include)
- Modify: `modules/opencv_core.i`, `opencv_imgproc.i`, `opencv_highgui.i`, `opencv_video.i`, `opencv_features2d.i`, `opencv_objectdetect.i`, `opencv_photo.i`, `opencv_contrib.i`
- Create: `tests/unit_tests/exception_safety.tst`, `tests/unit_tests/exception_safety.dia.ref`

**Interfaces:**
- Consumes: nothing.
- Produces: `scicv_exception.i`, included by every module interface (existing and new). After this task, an OpenCV C++ exception anywhere in scicv raises a catchable Scilab error carrying the OpenCV message, instead of aborting the process.

**Why first.** Today only `opencv_dnn.i` guards exceptions. `imread` on a corrupt file, `VideoCapture` on a bad device, or a dimension-mismatch in `imgproc` can abort the entire Scilab session. That is a bigger user-facing win than any new module, and doing it once as a shared file means the other 28 modules inherit it for free.

- [ ] **Step 1: Write the failing test**

Create `tests/unit_tests/exception_safety.tst`:

```scilab
// Scilab Computer Vision Module
// An OpenCV C++ exception must raise a catchable Scilab error, never abort the process.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

scicv_Init();

// imread on a directory: OpenCV throws, it must not kill the interpreter.
ierr = execstr("m = imread(TMPDIR);", "errcatch");
assert_checktrue(ierr <> 0);
le = lasterror();
assert_checktrue(grep(le, "OpenCV") <> [] | grep(le, "imread") <> []);

// A dimension-mismatch inside imgproc: same requirement.
a = Mat(4, 4, CV_8UC1);
b = Mat(8, 8, CV_8UC1);
ierr = execstr("c = add(a, b);", "errcatch");
assert_checktrue(ierr <> 0);
assert_checktrue(grep(lasterror(), "OpenCV") <> []);
delete_Mat(a); delete_Mat(b);

// Reaching here at all proves the process survived every case above.
disp("EXCEPTION_SAFETY_SURVIVED");
```

Create `tests/unit_tests/exception_safety.dia.ref` as an empty file.

- [ ] **Step 2: Run it and confirm it fails by CRASHING, not by asserting**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','exception_safety',['no_check_error_output']); exit(0)" ; echo "rc=$?"
```
Expected: the process dies — a `Signal: Abort trap` / `Segmentation fault`, or a nonzero `rc` with `EXCEPTION_SAFETY_SURVIVED` never printed. **Record the exact failure mode**; that is the evidence this task is needed. If it merely fails an assertion while the process survives, the crash class is narrower than believed — say so in the report and continue anyway.

- [ ] **Step 3: Create the shared exception interface**

Create `sci_gateway/c/swig/scicv_exception.i`:

```swig
// Scilab Computer Vision Module
//
// The ONE exception guard, shared by every module interface.
//
// WHY THIS EXISTS
// ---------------
// OpenCV throws cv::Exception for bad arguments, unreadable files, dimension
// mismatches -- routine, user-triggerable conditions. Unguarded, that exception
// unwinds out of the SWIG wrapper and terminates the whole Scilab process
// instead of raising an error a script can catch.
//
// WHY SWIG_exception_fail AND NOT SWIG_exception
// ----------------------------------------------
// In the Scilab backend SWIG_exception expands to a bare
// SWIG_Scilab_Error(code,msg); -- it does NOT return. Every guarded wrapper
// would then fall through into its success path: functions returning by
// reference dereference a null result (segfault), and the rest publish a
// default-constructed object as if the call had succeeded. SWIG_exception_fail
// appends SWIG_fail => return SWIG_ERROR. This was measured, not assumed: the
// wrong macro turned an abort into "Signal: Segmentation fault: 11, Failing at
// address: 0x17".
//
// WHY $cleanup
// ------------
// SWIG_fail here is a bare `return SWIG_ERROR;` and the generated wrapper has
// no `fail:` label anywhere. Cleanup that SWIG emits after $action is therefore
// skipped on the exception path, leaking temporaries (notably the _InputArray
// wrapper and any converted pixel buffer). $cleanup expands to that argument
// cleanup code, so invoking it in the catch body restores it.
%exception {
    try {
        $action
    } catch (const cv::Exception& e) {
        $cleanup;
        SWIG_exception_fail(SWIG_RuntimeError, e.what());
    } catch (const std::exception& e) {
        $cleanup;
        SWIG_exception_fail(SWIG_RuntimeError, e.what());
    }
}
```

- [ ] **Step 4: Include it from every module interface**

In each of the nine `modules/opencv_*.i` files, add this line immediately after the closing `%}` of the file's `%{ ... %}` block:

```swig
%include ../scicv_exception.i
```

For `opencv_dnn.i`, **replace** its existing inline `%exception { ... }` block with that line — the shared file supersedes it, and two definitions would have the later one silently win.

Do not add a `%exception;` reset anywhere. The guard is wanted for every module now, not just one, so it should remain in force through the whole interface.

- [ ] **Step 5: Regenerate, rebuild, and run the test to verify it passes**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig && ./regen.sh
cd /Users/josemoya/Projects/SciLabProjects/scicv
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce > /tmp/scicv-build.log 2>&1 &
wait; grep -E "ierr=" /tmp/scicv-build.log
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','exception_safety',['no_check_error_output']); exit(0)"
```
Expected: three `ierr=0` lines, then the test passes with `EXCEPTION_SAFETY_SURVIVED` printed and the process exiting normally.

- [ ] **Step 6: Check for the double-free the guard could introduce**

`$cleanup` runs after a failed `$action`. If the action released something before throwing, cleanup releasing it again is a use-after-free — strictly worse than the leak it replaces.

Inspect the regenerated wrapper for three shapes and report what you find:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c
grep -n -A25 "_wrap_imread" scicv_wrap.cxx | grep -E "catch|release|delete|arg[0-9]" | head -20
grep -n -A30 "_wrap_blobFromImage__SWIG_4" scicv_wrap.cxx | grep -E "catch|release|delete" | head -20
grep -n -A30 "_wrap_add__SWIG" scicv_wrap.cxx | grep -E "catch|release|delete" | head -20
```
Expected: the cleanup calls appear inside the catch body, and each releases an *argument* temporary rather than anything the failed call could already have freed. If any cleanup touches a result or a shared `Mat` buffer, stop and report rather than committing.

Then confirm the leak is actually gone, over the shape most likely to leak:
```scilab
// /tmp/leakprobe.sce — 2000 throwing iterations, RSS must stay flat
exec('loader.sce', -1); scicv_Init();
img = rand(64, 64);
for k = 1:2000
    ierr = execstr("b = blobFromImage(img, 1.0, Size(8,8), Scalar(0,0,0,0), %t, %f); c = Net_forward(Net());", "errcatch");
end
mprintf("RSS_KB=%d\n", msscanf(unix_g("ps -o rss= -p " + string(getpid())), "%d"));
exit(0);
```
Run it before and after the change and report both RSS figures.

- [ ] **Step 7: Run the full suite**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv',[],['no_check_error_output']); exit(0)"
```
Expected: **32/32** (the 31 existing plus `exception_safety`). Any newly failing test is a regression from making previously-fatal errors catchable — a test that relied on the old abort would now proceed further. Investigate each rather than adjusting the expectation.

- [ ] **Step 8: Commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
cat > /tmp/msg-exc.txt <<'MSG'
swig: one shared exception guard, applied to every module

Only opencv_dnn.i guarded C++ exceptions. Everywhere else an OpenCV throw --
imread on a corrupt file, VideoCapture on a bad device, a dimension mismatch
in imgproc -- unwound out of the wrapper and terminated the whole Scilab
process instead of raising a catchable error.

scicv_exception.i now holds the guard once and every module interface includes
it. Two details are load-bearing and both were measured, not assumed:

  - SWIG_exception_fail, never SWIG_exception. In the Scilab backend the latter
    expands to a bare SWIG_Scilab_Error(code,msg) with no return, so guarded
    wrappers fall through into their success path: by-reference returns
    dereference a null result and the rest publish a default-constructed object
    as a valid answer. The wrong macro turns an abort into a segfault.
  - $cleanup in the catch body. SWIG_fail is a bare `return SWIG_ERROR;` and the
    generated wrapper has no fail: label at all, so cleanup emitted after
    $action is skipped on the exception path, leaking the _InputArray wrapper
    and any converted pixel buffer.

exception_safety.tst covers imread and an imgproc mismatch; before this change
it killed the interpreter rather than failing an assertion.
MSG
git add sci_gateway/c/swig/scicv_exception.i sci_gateway/c/swig/modules/ \
        sci_gateway/c/scicv_wrap.cxx sci_gateway/c/builder_gateway_c.sce \
        tests/unit_tests/exception_safety.tst tests/unit_tests/exception_safety.dia.ref
git commit -F /tmp/msg-exc.txt
git push gitlab main && git push origin main
```

---

### Task 2: Reference implementation — `xphoto` (Group F template)

**Files:**
- Create: `sci_gateway/c/swig/modules/opencv_xphoto.i`
- Modify: `sci_gateway/c/swig/scicv.i`
- Create: `tests/unit_tests/xphoto.tst`, `tests/unit_tests/xphoto.dia.ref`
- Modify: `tests/unit_tests/functions-5.0.0.txt`

**Interfaces:**
- Consumes: `scicv_exception.i` from Task 1.
- Produces: **the Group F template.** Every later function-shaped module copies this file's structure verbatim. It also produces the wrapped `xphoto` API: `applyChannelGains`, `bm3dDenoising`, `createSimpleWB`, `createGrayworldWB`, `createLearningBasedWB`, `dctDenoising`, `inpaint`, `oilPainting`.

**Why `xphoto` is the reference.** 44 declarations across 7 headers, 4 `Ptr<>`, zero factories, 8 out-parameters — large enough to exercise the real machinery, small enough to review closely. Group F is 13 of the 29 modules.

- [ ] **Step 1: Write the failing test**

Create `tests/unit_tests/xphoto.tst`:

```scilab
// Scilab Computer Vision Module
// xphoto bindings — symbols, one real call, one error path.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

scicv_Init();

assert_checkequal(exists("oilPainting"), 1);
assert_checkequal(exists("dctDenoising"), 1);
assert_checkequal(exists("inpaint"), 1);
assert_checkequal(exists("createSimpleWB"), 1);
assert_checkequal(exists("applyChannelGains"), 1);

// A real call that must change the image. Size is [height, width].
src = imread(fullfile(get_scicv_path(), "images", "lena.jpg"));
assert_checktrue(Mat_empty(src) == %f);
dst = Mat();
oilPainting(src, dst, 5, 1);
assert_checkequal(Mat_empty(dst), %f);
assert_checkequal(MatShape_str(Mat_shape(dst)), MatShape_str(Mat_shape(src)));

// The white-balance factory returns a usable object.
wb = createSimpleWB();
assert_checktrue(wb <> []);
wbdst = Mat();
SimpleWB_balanceWhite(wb, src, wbdst);
assert_checkequal(Mat_empty(wbdst), %f);

// Error path: exercises Task 1's shared guard from a Group F module.
ierr = execstr("oilPainting(Mat(), dst, 5, 1);", "errcatch");
assert_checktrue(ierr <> 0);
assert_checktrue(grep(lasterror(), "OpenCV") <> []);

delete_Mat(src); delete_Mat(dst); delete_Mat(wbdst);
```

Create an empty `tests/unit_tests/xphoto.dia.ref`.

- [ ] **Step 2: Run it to verify it fails**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','xphoto',['no_check_error_output']); exit(0)"
```
Expected: fails at the first assertion — `exists("oilPainting")` returns 0.

- [ ] **Step 3: Write the module interface — this is the Group F template**

Create `sci_gateway/c/swig/modules/opencv_xphoto.i`:

```swig
// Scilab Computer Vision Module
// xphoto — white balance, denoising, inpainting, oil-painting stylisation.
//
// THE GROUP F TEMPLATE. Function-shaped modules (few or no Ptr<> factories;
// the work is out-parameter typemaps) copy this file's structure exactly:
//   1. %{ ... %} with the module's public headers and `using namespace cv;`
//   2. %include ../scicv_exception.i   -- the shared guard, never an inline copy
//   3. %include of the ignore file IF one is needed (a surface trim, never a
//      parse fix -- if SWIG cannot parse without it, the fix belongs elsewhere)
//   4. %include of each public header, most-general first
//
// libopencv_xphoto is ALREADY linked into libscicv; nothing about the build
// changes here.

%{
#include "opencv2/xphoto.hpp"
#include "opencv2/xphoto/white_balance.hpp"
#include "opencv2/xphoto/bm3d_image_denoising.hpp"
#include "opencv2/xphoto/dct_image_denoising.hpp"
#include "opencv2/xphoto/inpainting.hpp"
#include "opencv2/xphoto/oilpainting.hpp"
#include "opencv2/xphoto/tonemap.hpp"
using namespace cv;
using namespace cv::xphoto;
%}

%include ../scicv_exception.i

// InputArray/OutputArray pairs are already mapped by the shared typemaps; the
// module needs no per-function %apply.
%include "opencv2/xphoto/white_balance.hpp"   // WhiteBalancer, SimpleWB, GrayworldWB, LearningBasedWB
%include "opencv2/xphoto/bm3d_image_denoising.hpp"
%include "opencv2/xphoto/dct_image_denoising.hpp"
%include "opencv2/xphoto/inpainting.hpp"
%include "opencv2/xphoto/oilpainting.hpp"
%include "opencv2/xphoto/tonemap.hpp"
```

- [ ] **Step 4: Register the module**

In `sci_gateway/c/swig/scicv.i`, add after the `%include modules/opencv_dnn.i` line:

```swig
%include modules/opencv_xphoto.i
```

Registration order matters only where one module's headers reference another's types. `xphoto` depends on `core` and `photo`, both already included earlier.

- [ ] **Step 5: Regenerate and rebuild**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig && ./regen.sh
cd /Users/josemoya/Projects/SciLabProjects/scicv
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce > /tmp/scicv-build.log 2>&1 &
wait; grep -E "ierr=" /tmp/scicv-build.log
```
Expected: `regen.sh` reports a gateway table larger than 4562 (roughly +90 rows, since the metric counts both the live and dead branches), and three `ierr=0` lines.

**If SWIG errors**, do not reach for `%ignore` first. Read the error: an unparseable declaration usually needs a `%ignore` for that one name, but a *compile* error after successful generation usually means a version-gated block took the wrong branch — check whether the header gates on something `regen.sh` does not `-D`.

- [ ] **Step 6: Run the test to verify it passes**

Run:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv','xphoto',['no_check_error_output']); exit(0)"
```
Expected: `xphoto ......... passed`.

- [ ] **Step 7: Add the new names to the inventory**

Derive them from the generated table rather than predicting:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
git diff sci_gateway/c/builder_gateway_c.sce | grep '^+' | grep -oE '"[A-Za-z_0-9]+","_wrap_[A-Za-z_0-9]+"' | sort -u
```
Append every line that emits to `tests/unit_tests/functions-5.0.0.txt` before the closing `];`, in the file's existing `"name","_wrap_name"; ..` format. Use the **untruncated** name — the truncated alias lives only in the dead `ver(1) < 6` branch and is not callable.

- [ ] **Step 8: Run the full suite and commit**

```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
scilab2027 -nb -e "exec('loader.sce',-1); test_run('scicv',[],['no_check_error_output']); exit(0)"
```
Expected: **33/33**. Then:
```bash
cat > /tmp/msg-xphoto.txt <<'MSG'
swig: wrap the xphoto module

White balance, BM3D and DCT denoising, inpainting, oil-painting stylisation --
44 declarations across 7 headers. libopencv_xphoto was already linked; only the
interface was missing.

This file is the reference for every function-shaped module still to come: the
%{ %} include block, the shared exception guard, then the public headers
most-general first. No per-function %apply is needed -- the InputArray and
OutputArray typemaps already cover the shapes xphoto uses.
MSG
git add sci_gateway/c/swig/modules/opencv_xphoto.i sci_gateway/c/swig/scicv.i \
        sci_gateway/c/scicv_wrap.cxx sci_gateway/c/builder_gateway_c.sce \
        tests/unit_tests/xphoto.tst tests/unit_tests/xphoto.dia.ref \
        tests/unit_tests/functions-5.0.0.txt
git commit -F /tmp/msg-xphoto.txt
git push gitlab main && git push origin main
```

---

### Task 3: Reference implementation — `ml` (Group P template, highest user value)

**Files:**
- Create: `sci_gateway/c/swig/modules/opencv_ml.i`, `modules/opencv_ml_ignore.i`
- Modify: `sci_gateway/c/swig/scicv.i`
- Create: `tests/unit_tests/ml.tst`, `tests/unit_tests/ml.dia.ref`
- Modify: `tests/unit_tests/functions-5.0.0.txt`, `tests/unit_tests/variables-5.0.0.txt`

**Interfaces:**
- Consumes: `scicv_exception.i` from Task 1; the structure established by Task 2.
- Produces: **the Group P template** — how `Ptr<>`-returning `create()` factories and abstract base classes are wrapped. Group P is 16 of the 29 modules. Also produces the `ml` API: `SVM`, `RTrees`, `KNearest`, `ANN_MLP`, `Boost`, `DTrees`, `NormalBayesClassifier`, `EM`, `LogisticRegression`, plus `TrainData`.

**Why `ml` is the second reference.** 225 declarations, 39 `Ptr<>`, **13 `create()` factories** — the densest factory surface outside `tracking`, and by far the highest user value in the unwrapped set (classical machine learning: SVM, random forests, kNN, neural nets). Getting the factory pattern right here makes the remaining 15 Group P modules mechanical.

- [ ] **Step 1: Write the failing test**

Create `tests/unit_tests/ml.tst`:

```scilab
// Scilab Computer Vision Module
// ml bindings — factory creation, train, predict, and the abstract-base path.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

scicv_Init();

assert_checkequal(exists("SVM_create"), 1);
assert_checkequal(exists("RTrees_create"), 1);
assert_checkequal(exists("KNearest_create"), 1);
assert_checkequal(exists("TrainData_create"), 1);

// Two separable clusters: rows are samples, one feature column pair.
samples = [1 1; 2 2; 1 2; 8 8; 9 9; 8 9];
labels  = [0; 0; 0; 1; 1; 1];
mSamples = cvMatFromScilab(samples, CV_32F);
mLabels  = cvMatFromScilab(labels,  CV_32S);

svm = SVM_create();
assert_checktrue(svm <> []);
SVM_setType(svm, CV_SVM_C_SVC);
SVM_setKernel(svm, CV_SVM_LINEAR);

// train() is inherited from StatModel -- proves the abstract base is reachable.
ok = StatModel_train(svm, mSamples, ROW_SAMPLE, mLabels);
assert_checkequal(ok, %t);
assert_checkequal(StatModel_isTrained(svm), %t);

// Predict a point inside each cluster.
q = cvMatFromScilab([1.5 1.5], CV_32F);
r = Mat();
StatModel_predict(svm, q, r, 0);
assert_checkequal(cvMatExtract(r)(1), 0);

q2 = cvMatFromScilab([8.5 8.5], CV_32F);
StatModel_predict(svm, q2, r, 0);
assert_checkequal(cvMatExtract(r)(1), 1);

// Error path: predicting with the wrong feature count must raise, not abort.
bad = cvMatFromScilab([1 2 3 4 5], CV_32F);
ierr = execstr("StatModel_predict(svm, bad, r, 0);", "errcatch");
assert_checktrue(ierr <> 0);
assert_checktrue(grep(lasterror(), "OpenCV") <> []);

delete_Mat(mSamples); delete_Mat(mLabels); delete_Mat(q); delete_Mat(q2);
delete_Mat(r); delete_Mat(bad);
```

Create an empty `tests/unit_tests/ml.dia.ref`.

**Note on `cvMatFromScilab`:** verify this constructor exists before relying on it —
`grep -o '"cvMat[A-Za-z]*"' sci_gateway/c/builder_gateway_c.sce | sort -u`.
`cvMatExtract` is confirmed present. If no Scilab→Mat constructor exists under that name, use the real spelling from that list and say so in the report.

- [ ] **Step 2: Run it to verify it fails**

Expected: fails at `exists("SVM_create")` returning 0.

- [ ] **Step 3: Write the ignore file**

Create `sci_gateway/c/swig/modules/opencv_ml_ignore.i`:

```swig
// Scilab Computer Vision Module
// ml: a surface trim, NOT a parse fix.
//
// If SWIG cannot PARSE without an entry here, that is a different problem and
// the fix belongs elsewhere -- add ignores only to keep C++-only machinery out
// of the gateway table.

// SimulatedAnnealingSolverSystem is a template policy class the caller must
// model in C++; no Scilab script can supply one.
%ignore cv::ml::SimulatedAnnealingSolverSystem;
%ignore cv::ml::ANN_MLP_ANNEAL;

// The custom-kernel hook takes a user-implemented Ptr<SVM::Kernel> subclass --
// again only implementable in C++. setKernel(int) with the built-in kernel
// enums is kept and is the usable path.
%ignore cv::ml::SVM::Kernel;
%ignore cv::ml::SVM::setCustomKernel;
```

- [ ] **Step 4: Write the module interface — this is the Group P template**

Create `sci_gateway/c/swig/modules/opencv_ml.i`:

```swig
// Scilab Computer Vision Module
// ml — classical machine learning: SVM, RTrees, KNearest, ANN_MLP, Boost,
// DTrees, NormalBayesClassifier, EM, LogisticRegression.
//
// THE GROUP P TEMPLATE. Factory-shaped modules copy this structure:
//
//   Every algorithm here is an ABSTRACT class reached through a static
//   `create()` returning Ptr<T>. Two consequences drive the whole file:
//
//   1. The Ptr<T> return needs no special handling -- scicv's shared typemaps
//      already unwrap Ptr<> to the pointer Scilab holds. Do NOT add %template
//      or %shared_ptr; both fight the existing machinery.
//   2. The inherited surface must be %include'd, not just the leaf class.
//      SVM::train / predict / isTrained live on cv::ml::StatModel, so
//      ml.hpp must be included whole -- wrapping only the leaf types would
//      generate SVM_create() objects with nothing callable on them. This is
//      the single most common way a factory-shaped module ends up "wrapped"
//      but useless.
//
// libopencv_ml is ALREADY linked into libscicv; the build does not change.

%{
#include "opencv2/ml.hpp"
using namespace cv;
using namespace cv::ml;
%}

%include ../scicv_exception.i
%include modules/opencv_ml_ignore.i

// Whole-module include: StatModel's train/predict/isTrained are inherited by
// every algorithm and must be present for any of them to be usable.
%include "opencv2/ml.hpp"
```

- [ ] **Step 5: Register, regenerate, rebuild**

Add to `scicv.i` after the `xphoto` line:
```swig
%include modules/opencv_ml.i
```
Then:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv/sci_gateway/c/swig && ./regen.sh
cd /Users/josemoya/Projects/SciLabProjects/scicv
/Users/josemoya/Projects/CLionProjects/scilab/scilab/bin/scilab-cli -nb -f build_macos.sce > /tmp/scicv-build.log 2>&1 &
wait; grep -E "ierr=" /tmp/scicv-build.log
```
Expected: three `ierr=0`; gateway table up roughly 450 rows.

- [ ] **Step 6: Verify the inherited surface is actually callable**

This is the check that distinguishes a usable factory module from a useless one:
```bash
cd /Users/josemoya/Projects/SciLabProjects/scicv
for n in SVM_create RTrees_create KNearest_create ANN_MLP_create Boost_create \
         StatModel_train StatModel_predict StatModel_isTrained TrainData_create; do
  grep -q "\"$n\"," sci_gateway/c/builder_gateway_c.sce || echo "MISSING: $n"
done
```
Expected: no output. A missing `StatModel_*` means the inherited surface did not come through — revisit the `%include`, do not paper over it by wrapping leaf methods individually.

- [ ] **Step 7: Run the test, then the full suite**

Expected: `ml` passes; suite **34/34**.

- [ ] **Step 8: Add inventory entries and commit**

Derive names as in Task 2 Step 7, appending function names to `functions-5.0.0.txt` and the new `CV_SVM_*` / `ROW_SAMPLE` / `COL_SAMPLE` style constants to `variables-5.0.0.txt`. Then:

```bash
cat > /tmp/msg-ml.txt <<'MSG'
swig: wrap the ml module (SVM, RTrees, KNearest, ANN_MLP, Boost, ...)

225 declarations, 13 create() factories -- the densest factory surface in the
unwrapped set and the highest user value: classical machine learning was
entirely unreachable from Scilab despite libopencv_ml being linked all along.

This file is the reference for every factory-shaped module still to come. The
load-bearing detail is including ml.hpp WHOLE rather than the leaf algorithm
classes: train/predict/isTrained live on the StatModel base, so wrapping only
SVM and friends would have produced objects with nothing callable on them --
the standard way a factory module ends up nominally wrapped and actually
useless. The test asserts against StatModel_* specifically to pin that down.

Ptr<> needs no %template or %shared_ptr; the existing typemaps already unwrap
it, and adding either fights that machinery.
MSG
git add sci_gateway/c/swig/modules/opencv_ml.i sci_gateway/c/swig/modules/opencv_ml_ignore.i \
        sci_gateway/c/swig/scicv.i sci_gateway/c/scicv_wrap.cxx \
        sci_gateway/c/builder_gateway_c.sce \
        tests/unit_tests/ml.tst tests/unit_tests/ml.dia.ref \
        tests/unit_tests/functions-5.0.0.txt tests/unit_tests/variables-5.0.0.txt
git commit -F /tmp/msg-ml.txt
git push gitlab main && git push origin main
```

---

### Tasks 4–7: Group F batches

Each batch task wraps four function-shaped modules by copying Task 2's `opencv_xphoto.i` structure exactly. Per module: write the `.i`, register it in `scicv.i`, write a `.tst` asserting symbols + one real call + one error path, regenerate once for the whole batch, rebuild, run the suite, add inventory entries, commit **once per batch**.

- [ ] **Task 4 — small function modules.** `intensity_transform` (5 decls, 1 hdr), `hfs` (18, 1), `phase_unwrapping` (7, 3), `img_hash` (30, 8). Headers: `opencv2/intensity_transform.hpp`, `opencv2/hfs.hpp`, `opencv2/phase_unwrapping.hpp` + `phase_unwrapping/histogramphaseunwrapping.hpp`, `opencv2/img_hash.hpp` + the seven `img_hash/*.hpp`. Expect ~120 new table rows, suite 38/38.

- [ ] **Task 5 — denoise/stereo family.** `fuzzy` (15, 5 — 16 out-params, the highest out-param density in this group), `xstereo` (14, 3), `stereo` (51, 1), `surface_matching` (28, 6). `surface_matching` uses `Pose3D`/`PoseCluster3D` value types; check they marshal before assuming. Expect ~200 rows, suite 42/42.

- [ ] **Task 6 — geometry and calibration.** `calib` (10 decls but **48 out-params**), `ccalib` (8, 22 `Ptr<>`, 42 out-params), `shape` (66, 14 `Ptr<>`). These are out-parameter heavy: the risk is a function whose `OutputArray` is `arginit`-allocated and never reaches Scilab. For each module pick one function returning two or more outputs and assert **both** are populated. Expect ~170 rows, suite 45/45.

- [ ] **Task 7 — `geometry` alone.** 107 declarations, 3985 LOC, **129 out-parameters** — the largest single Group F module, given its own task because its out-parameter density is twice anything else. Headers under `opencv2/geometry/`. Expect ~220 rows, suite 46/46.

---

### Tasks 8–11: Group P batches

Each copies Task 3's `opencv_ml.i` structure. **The recurring hazard is Task 3 Step 6's:** include the module header whole so inherited base-class methods (`Algorithm::read/write/clear`, module-specific bases) come through, then verify with a `MISSING:` loop naming the base methods explicitly — not just the `create()` factories.

- [ ] **Task 8 — small factory modules.** `plot` (21), `wechat_qrcode` (5), `freetype` (7), `dnn_superres` (10), `rapid` (17). Each is a single header with a `create()` factory. `freetype` needs a font file at runtime; its test should assert the factory returns non-empty and skip rendering if no font is found rather than failing. Expect ~110 rows, suite 51/51.

- [ ] **Task 9 — quality and saliency.** `quality` (37, 12 `Ptr<>`, 3 templates), `saliency` (39, 9 `Ptr<>`, 14 out-params), `bioinspired` (36), `structured_light` (18). `quality` has three template declarations — check whether they are `QualityBase` helpers that need `%ignore` or genuinely instantiable. Expect ~180 rows, suite 55/55.

- [ ] **Task 10 — text and features.** `text` (41, 6 factories), `line_descriptor` (43, 7 factories), `stitching` (35, **52 `Ptr<>`** — the highest Ptr density relative to API, so nearly every call traffics in objects). `text` needs Tesseract at runtime for OCR paths; its test must assert the non-OCR surface and skip OCR if unavailable. Expect ~200 rows, suite 58/58.

- [ ] **Task 11 — tracking and face.** `tracking` (38 decls but **56 `Ptr<>` and 18 factories** — the densest factory surface anywhere), `face` (73, 9 factories, 10 headers). Both have abstract bases with the inherited-surface trap; verify `Tracker_init`/`Tracker_update` and `FaceRecognizer_train`/`FaceRecognizer_predict` are present, not merely the factories. Expect ~230 rows, suite 60/60.

---

### Task 12: `ximgproc` — the largest module

**Files:** `modules/opencv_ximgproc.i`, `modules/opencv_ximgproc_ignore.i`, `scicv.i`, `tests/unit_tests/ximgproc.tst` + `.dia.ref`, both inventory files.

247 declarations across **27 headers**, 37 `Ptr<>`, 70 out-parameters — larger than any other single module and mixing both shapes, so it gets its own task and draws on both templates. Include `opencv2/ximgproc.hpp` whole first (it is an umbrella that pulls the sub-headers), then add individual `%include`s only for sub-headers the umbrella omits — verify which by diffing the generated symbol list against the header's declared names. Expect ~500 rows, suite 61/61.

---

### Task 13: Final sweep — coverage report, docs, demo

**Files:** `CHANGELOG.md`, `README.md` (or `readme.txt`), `tests/unit_tests/coverage.tst` + `.dia.ref`, `demos/` entry.

- [ ] **Step 1: Write a coverage test that cannot silently rot**

Create `tests/unit_tests/coverage.tst` asserting that one signature symbol from each of the 29 newly wrapped modules exists — e.g. `oilPainting` (xphoto), `SVM_create` (ml), `Tracker_init` (tracking), `FaceRecognizer_train` (face), one per module. A future regeneration that silently drops a module then fails here rather than being noticed months later.

- [ ] **Step 2: Record the coverage jump**

Update `CHANGELOG.md` with the before/after: 9 module interfaces → 38; ~24 headers → ~90; 4562 gateway rows → the final measured figure. State the exclusions and why (`gapi`, `viz`, `flann`, `sfm`) so the gap is documented rather than looking like an oversight.

- [ ] **Step 3: One demo that uses a newly wrapped module end to end**

An `ml` demo is the strongest showcase: load or synthesise a two-class dataset, train an `SVM`, predict a grid, and plot the decision boundary with Scilab's own graphics. Ends with `exit(0)` and self-checks, following the existing demo convention.

- [ ] **Step 4: Full suite, then commit and push both remotes**

Expected: **62/62**.

---

## Risks and How Each Is Handled

| Risk | Handling |
|---|---|
| A factory module wraps `create()` but nothing callable on the result | Task 3 establishes whole-header inclusion as the pattern and Step 6's `MISSING:` loop as the gate; every Group P batch repeats it against that module's base-class methods |
| `$cleanup` double-frees on a partially-completed action | Task 1 Step 6 inspects three wrapper shapes before committing and requires a stop-and-report if cleanup touches a result rather than an argument temporary |
| Over-ignoring silently removes wanted API | Every ignore file is headed "a surface trim, NOT a parse fix"; a SWIG parse failure must be fixed at its cause, never by ignoring the symbol |
| A version-gated header block takes the wrong branch | `regen.sh` passes `-DCV_VERSION_MAJOR/MINOR` from pkg-config; Task 2 Step 5 names this explicitly as the thing to check when generation succeeds but compilation fails |
| Runtime-dependent modules fail in CI or on other machines | `freetype` (font file) and `text` (Tesseract) tests assert the always-available surface and skip the dependent paths rather than failing |
| Suite count drifts unnoticed | Every batch task states its expected suite total; a mismatch is investigated, not adjusted |
| Regenerating produces unrelated diff churn | The known one-line `CV_MAT_TYPE_MASK` residual is value-identical (both forms 4095) and expected; anything else is investigated |

## Not In Scope

- `gapi`, `viz`, `flann`, `sfm`, `core_detect` — excluded with reasons in Verified Facts.
- **libTorch / sciTorch** — recorded decision, see Verified Facts. SWIG is the wrong tool; route through PIMS if breadth is wanted later.
- Scilab-idiomatic wrapper macros over the raw bindings — worth doing eventually, but the raw surface must exist first.
- Help pages for the new functions — the toolbox's existing help covers only the original modules; a separate documentation pass.
