// STATUS: RESOLVED — the root cause below was fixed in the sciTorch repo
// (commit b8d63183d27: stray IPCV link() removed at the builder source of truth,
// OpenCV closure vendored self-contained, the swallowing catch now warns) and this
// smoke passes: sciTorch PASS delta=1; smoke=OK. The history is kept because it
// documents why the smoke exists: without it, the harness's delta>=1 criterion alone
// false-PASSed sciTorch (its loader registers 1 macro library cleanly -- torch_load/
// torch_list/torch_unload etc. all exist as macros -- even while every one of its 8
// native int_torch_* entry points was silently missing).
//
// Original root cause, isolated by direct diagnostic (bypassing the swallowing catch):
// sci_gateway/cpp/loader.sce's very first executable line is
//   link('/Applications/scilab-2026.1.0.app/Contents/share/scilab/contrib/IPCV/4.5.0.2/sci_gateway/cpp/libgw_ipcv' + getdynlibext());
// -- a hardcoded, version-pinned, EXTERNAL absolute path into a *different* Scilab
// app bundle (2026.1.0, not this dev build). The file exists on this machine, but
// link() fails against it: "link: The shared archive was not loaded: (null)" (an
// ABI/symbol mismatch against that old bundle's own core, not a missing-file error).
// Since the addinter() call for sciTorch's own libgw_sciTorch never runs after that
// failing link(), none of the 8 native functions register.
// This failure is entirely swallowed: etc/sciTorch.start wraps the whole gateway-load
// step in
//   try
//       exec(pathconvert(root_tlbx + "/sci_gateway/loader_gateway.sce", %f));
//   catch
//       err = lasterror();
//       return;
//   end
// with `err` never used, never printed, never surfaced -- toolbox startup reports
// success (delta=1) regardless. This is the same failure-masking pattern the wave-2
// brief flagged for individual wrapper macros (see the nan-toolbox precedent), just
// one level up, at whole-toolbox startup.
//
// sciTorch smoke: no tensor-creation/matmul/sum primitive exists anywhere in this
// toolbox (macros/ exposes exactly 8 verbs, all built around loading a whole
// pretrained TorchScript model and forward-passing images through it -- confirmed by
// reading every macros/*.sci and the sci_gateway/cpp/loader.sce addinter list), so the
// brief's suggested "one op" direction was substituted for the toolbox's own actual
// native round trip: torch_load (-> int_torch_load -> torch::jit::load, genuinely
// loading the bundled sciMnist.pt via libtorch) / torch_list (-> int_torch_list,
// reflects the loaded-model table) / torch_unload (-> int_torch_unload, frees the
// slot). This is the toolbox's OWN documented canonical example, repeated verbatim in
// torch_load.sci's, torch_list.sci's and torch_unload.sci's own "Examples" doc-comment
// blocks. No own tests exist (tests/unit_tests/ is empty).
//
// int_torch_load/int_torch_list wrap native exceptions in a try/catch that reports via
// sciprint (not Scierror) and return a -1 sentinel instead of raising a catchable
// Scilab error on failure (sci_int_torch_load.cpp, sci_int_torch_list.cpp) -- so this
// smoke checks the sentinel explicitly (ptr>=1, and list membership before/after
// unload) rather than trusting the mere absence of a thrown error, the same class of
// trap as the nan-toolbox precedent of not trusting a maybe-swallowed native failure.
//
// macOS loads the bundled thirdparty/libtorch dylibs implicitly via a build-time
// @loader_path rpath baked into libgw_sciTorch.dylib (etc/sciTorch_MacOS.sci is a
// documented no-op by design -- verified against the dylib's own LC_RPATH/LC_LOAD_DYLIB
// load commands with otool -l/-L, not just trusted from the comment).
mpath = fullpath(torch_path() + "/demos/models/" + "sciMnist.pt");
model = torch_load(mpath);
ok1 = (model.ptr >= 1);
y1 = torch_list();
ok2 = or(y1 == model.ptr);
torch_unload(model);
y2 = torch_list();
ok3 = ~or(y2 == model.ptr);
smoke_ok = ok1 & ok2 & ok3;
