# Documentation

Documentation specific to this Scilab fork (building it, and the features being added).

> Upstream Scilab source and its docs live under [`../scilab/`](../scilab/); CI docs live
> under [`../.gitlab-ci/`](../.gitlab-ci/). This `docs/` folder holds only the docs we add.

## Building

- **[Building on macOS](building/macos.md)** — build & run Scilab from source on macOS
  (Apple Silicon / arm64): Homebrew dependencies, the deployment-target / JOGL startup fix,
  runtime dylib fixes, the `Scilab-2027.0.0.app` bundle + menu-bar naming, and (§11) a
  **standalone, relocatable app** with a git **toolbox manager** + `scilab2027` CLI
  (`package-macos.sh`).

## Design

- **[macOS app packaging + toolbox manager](design/macos-app-packaging.md)** — the independent
  `/Applications/Scilab-2027.0.0.app` (relocated copy, configurable JDK, isolated SCIHOME) and
  the git-driven toolbox manager (`tbx*` verbs + manifest + `.scilab` autoload + `tbxManager()`
  GUI). User guide: [building/macos.md §11](building/macos.md).
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
└── design/                feature designs / specs
    ├── macos-app-packaging.md
    ├── terminal-live-reload.md
    └── gpu-acceleration.md
```

New documents go under the matching category (`building/`, `design/`, …); add a category
folder if none fits, and link it from this index.
