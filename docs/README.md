# Documentation

Documentation specific to this Scilab fork (building it, and the features being added).

> Upstream Scilab source and its docs live under [`../scilab/`](../scilab/); CI docs live
> under [`../.gitlab-ci/`](../.gitlab-ci/). This `docs/` folder holds only the docs we add.

## Building

- **[Building on macOS](building/macos.md)** — build & run Scilab from source on macOS
  (Apple Silicon / arm64): Homebrew dependencies, the deployment-target / JOGL startup fix,
  runtime dylib fixes, and the `Scilab-2027.0.0.app` bundle + menu-bar naming.

## Design

- **[Terminal + live-reload](design/terminal-live-reload.md)** — approved design for the
  embedded terminal (run any command, notably Claude, inside Scilab) and the coupled live
  external-change-awareness system (`genlib` hot-reload + native FSEvents watcher).

---

### Layout

```
docs/
├── README.md              this index
├── building/              how to build & package
│   └── macos.md
└── design/                feature designs / specs
    └── terminal-live-reload.md
```

New documents go under the matching category (`building/`, `design/`, …); add a category
folder if none fits, and link it from this index.
