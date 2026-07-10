# Documentation

Documentation specific to this Scilab fork (building it, and the features being added).

> Upstream Scilab source and its docs live under [`../scilab/`](../scilab/); CI docs live
> under [`../.gitlab-ci/`](../.gitlab-ci/). This `docs/` folder holds only the docs we add.

## Building

- **[Building on macOS](building/macos.md)** — the handbook: build & run Scilab from source on
  macOS (Apple Silicon / arm64). The whole flow is `./fetch-thirdparty.sh` (pinned third-party
  payload) then `./build-macos.sh` (a plain `configure && make` — every macOS fix lives in the
  build system, no patches or post-build fixups). Also covers the JDK-25 stack, the
  deployment-target / JOGL crash deep-dive, troubleshooting, and (§8) the **standalone,
  relocatable `Scilab-2027.0.0.app`** with a git **toolbox manager** + `scilab2027` CLI
  (`package-macos.sh`).

## Design

- **[Build-system modernization](design/build-modernization.md)** — how the autotools build was
  made modern-native (latest tools, regenerated baseline committed, `reapply-macos-fixes.sh`
  eliminated, `fetch-thirdparty.sh`), with the full audit trail and the Stage-2/3 roadmap (CMake).
- **[Modernization assessment](design/modernization-assessment.md)** — the native-code track:
  the UB-miscompile class (`rand()` returning `Inf` at `-O2`) and the `-fwrapv` policy.
- **[UBSan/ASan findings](design/ubsan-findings.md)** — the sanitizer sweep inventory: what was
  found, fixed, and deferred.
- **[Vulkan renderer](design/vulkan-renderer.md)** — the first-party Vulkan/MoltenVK 3-D
  renderer (Swing↔GPU surface, Layer-2/3, clipping, lighting; M1–M8 complete).
- **[macOS app packaging + toolbox manager](design/macos-app-packaging.md)** — the independent
  `/Applications/Scilab-2027.0.0.app` (relocated copy, configurable JDK, isolated SCIHOME) and
  the git-driven toolbox manager (`tbx*` verbs + manifest + `.scilab` autoload + `tbxManager()`
  GUI). User guide: [building/macos.md §8](building/macos.md).
- **[Terminal + live-reload](design/terminal-live-reload.md)** — approved design for the
  embedded terminal (run any command, notably Claude, inside Scilab) and the coupled live
  external-change-awareness system (`genlib` hot-reload + native FSEvents watcher).
- **[GPU acceleration](design/gpu-acceleration.md)** — parked plan for Metal-first transparent
  fp32 offload of GEMM/FFT behind a Preferences toggle.

---

### Layout

```
docs/
├── README.md              this index
├── building/              how to build & package
│   └── macos.md
└── design/                feature designs / specs / audit trails
    ├── build-modernization.md
    ├── modernization-assessment.md
    ├── ubsan-findings.md
    ├── vulkan-renderer.md
    ├── macos-app-packaging.md
    ├── terminal-live-reload.md
    └── gpu-acceleration.md
```

New documents go under the matching category (`building/`, `design/`, …); add a category
folder if none fits, and link it from this index.
