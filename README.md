Scilab
======

[![Latest release](https://gitlab.com/jlmoya/scilab/-/badges/release.svg)](https://gitlab.com/jlmoya/scilab/-/releases)
[![Pipeline status](https://gitlab.com/jlmoya/scilab/badges/main/pipeline.svg)](https://gitlab.com/jlmoya/scilab/-/pipelines)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](scilab/COPYING)
[![Platform: macOS Apple Silicon](https://img.shields.io/badge/macOS-Apple%20Silicon%20%C2%B7%20Scilab%202027-black?logo=apple&logoColor=white)](docs/building/macos.md)
[![Last commit](https://img.shields.io/github/last-commit/jlmoya/scilab/main?logo=github)](https://github.com/jlmoya/scilab/commits/main)

![Scilab-2027.0.0 on macOS — console, embedded terminal, file & variable browsers, news feed](docs/images/scilab-2027-desktop.png "Scilab-2027.0.0 desktop on macOS (Apple Silicon)")

# Documentation

All project documentation lives in **[`docs/`](docs/)** — start at
[docs/README.md](docs/README.md). Highlights:

- **[Building on macOS](docs/building/macos.md)** — build & run Scilab from source on
  macOS (Apple Silicon / arm64), including the macOS-specific dependencies and fixes, plus
  packaging a standalone **`Scilab-2027.0.0.app`** with a git **toolbox manager** ([§11](docs/building/macos.md)).
- **[macOS app packaging + toolbox manager](docs/design/macos-app-packaging.md)** — the
  independent `/Applications` app (configurable JDK, isolated config) and the `tbxManager()`
  toolbox picker.
- **[Terminal + live-reload design](docs/design/terminal-live-reload.md)** — embedded
  terminal and live external-change awareness.

For Linux and Windows builds, see the
[developer wiki](https://gitlab.com/scilab/scilab/-/wikis/home).

# What does Scilab do?

Scilab includes hundreds of mathematical functions. It has a high-level programming language allowing access to advanced data structures, 2-D and 3-D graphical functions. A large number of functionalities is included in Scilab:

<dl>
  <dt>Maths & Simulation</dt>
  <dd>For usual engineering and science applications including mathematical operations and data analysis.</dd>

  <dt>2-D & 3-D Visualization</dt>
  <dd>Graphics functions to visualize, annotate and export data and many ways to create and customize various types of plots and charts.</dd>

  <dt>Optimization</dt>
  <dd>Algorithms to solve constrained and unconstrained continuous and discrete optimization problems.</dd>

  <dt>Statistics</dt>
  <dd>Tools to perform data analysis and modeling.</dd>

  <dt>Control System Design & Analysis</dt>
  <dd>Standard algorithms and tools for control system study.</dd>

  <dt>Signal Processing</dt>
  <dd>Visualize, analyze and filter signals in time and frequency domains.</dd>

  <dt>Application Development</dt>
  <dd>Increase Scilab native functionalities and manage data exchanges with external tools.</dd>

  <dt>Xcos - Hybrid dynamic systems modeler and simulator</dt>
  <dd>Modeling mechanical systems, hydraulic circuits, control systems...</dd>
</dl>
