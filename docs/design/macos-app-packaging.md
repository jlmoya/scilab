# macOS app packaging + toolbox manager for Scilab 2027

Status: **approved design**, 2026-06-27. Implementation pending.

## Goal

Turn the in-place dev build at `…/CLionProjects/scilab/scilab` into an **independent,
usable** `/Applications/Scilab-2027.0.0.app` for daily use on **this Mac**, with a
git-driven toolbox manager that installs our toolboxes, lets the user pick which to load
via a GUI form, and remembers them across sessions.

Explicitly **not** a notarized, dependency-vendored redistributable (that was the
alternative "Option B"). This app keeps using the machine's Homebrew dylibs + a system
JDK, which is what keeps it simple. Porting to other Macs is out of scope.

## Decisions (settled with the user)

1. **Model = relocated copy.** The app's payload is an rsync **copy** of the 1.6 GB build
   under `Contents/Resources/scilab/`. Independent of the dev tree — dev changes never
   touch it until the user re-packages.
2. **Upgrade = one command.** `package-macos.sh` re-rsyncs only changed files (seconds).
   Toolboxes + config live *outside* the engine payload, so they survive every refresh.
   `--rebuild-toolboxes` also rebuilds native toolboxes (for the core-ABI-changed case).
3. **JDK = configurable.** Launcher resolves `JAVA_HOME` in order: (a) config file
   `~/.config/scilab-app/java_home`, (b) inherited `$JAVA_HOME`, (c) `/usr/libexec/java_home
   -v <PIN>`. `<PIN>` defaults to **25** (build-tested). Changing JDK = edit one line.
4. **Toolbox manager** uses Scilab's **native** `loader.sce`/`unloader.sce` for load/unload,
   **git** for install/update (latest), and a **persistent manifest** for autoload.
5. **Toolbox source = both.** Prefer local `~/Projects/SciLabProjects/<name>` clone when it
   exists; else clone from `jlmoya` (GitLab, then GitHub). Per-call override `"local"`/`"remote"`.
6. **Isolation.** The app runs with its **own SCIHOME** (`~/.Scilab/scilab-app-2027`) so its
   prefs + toolbox set never mix with the dev build.
7. **GUI form** `tbxManager()` — checkbox list of all known toolboxes (status + tag), Apply
   installs/autoloads the checked and unloads/unregisters the unchecked. It *is* the seeding
   screen (shown on first launch) and the everyday load/unload UI. The checkboxes are the
   autoload manifest. Verified-on-macOS toolboxes are **pre-ticked**.
8. **Launch = GUI + CLI.** A Finder/Spotlight/Dock GUI app, plus a `scilab2027` symlink on
   `PATH` for the console/CLI from any terminal.

## Architecture

### App layout

```
/Applications/Scilab-2027.0.0.app/
  Contents/
    Info.plist, Resources/scilab.icns
    MacOS/Scilab-2027.0.0          # launcher (resolves JAVA_HOME + SCIHOME, execs engine)
    Resources/
      scilab/                      # rsync copy of the dev build (the engine, ~1.6 GB)
      toolbox-manager/             # our macro library (tbx* verbs + tbxManager GUI)
```

Toolboxes + manifest live **outside** the bundle so re-packaging never disturbs them:

```
~/.Scilab/scilab-app-2027/         # the app's private SCIHOME
  toolboxes/<name>/                # git clones, built in place
  installed_toolboxes.tbx          # manifest: name, path, source, autoload(0/1)
  .scilab                          # app startup: load manager, autoload manifest entries
~/.config/scilab-app/java_home     # optional one-line JDK override
```

### Launcher (`Contents/MacOS/Scilab-2027.0.0`)

```sh
#!/bin/bash
APP_RES="$(cd "$(dirname "$0")/../Resources" && pwd)"
# JAVA_HOME: config file > inherited env > macOS resolver (pinned version)
CFG="$HOME/.config/scilab-app/java_home"; JPIN=25
if   [ -s "$CFG" ];            then export JAVA_HOME="$(sed -n '1p' "$CFG")"
elif [ -n "$JAVA_HOME" ];      then :   # keep inherited
else export JAVA_HOME="$(/usr/libexec/java_home -v $JPIN 2>/dev/null)"; fi
export SCIHOME="$HOME/.Scilab/scilab-app-2027"
cd "$APP_RES/scilab" || exit 1
exec ./bin/scilab "$@"           # bin/scilab honors JAVA_HOME (line 437)
```

The CLI symlink `scilab2027` → a tiny wrapper that sets the same JAVA_HOME/SCIHOME and execs
`bin/scilab-cli` (or `-nw`), installed to `/usr/local/bin` (or `~/bin`).

### Toolbox manager (macro library, autoloaded by the app)

Verbs (final non-colliding names; Scilab core already owns `tbx_*`, so we use `tbx` camelCase):

| Verb | Behaviour |
|---|---|
| `tbxInstall(name[, source])` | resolve source → git clone/pull → run `build_macos.sce` (or `builder.sce`) → register (autoload=1) → load now |
| `tbxLoad(name)` / `tbxUnload(name)` | exec the toolbox's native `loader.sce` / `unloader.sce` |
| `tbxUpdate([name])` | git pull + rebuild (all, or one) |
| `tbxRemove(name)` | unload + unregister + delete clone |
| `tbxList()` | table: name / installed / loaded / autoload / source |
| `tbxManager()` | GUI checkbox form (below) |

Manifest `installed_toolboxes.tbx` is the single source of truth; every verb reads/writes it.
The app's `.scilab` startup loads the manager, then loops the manifest and `tbxLoad`s each
entry with autoload=1 — that is the "remember across sessions" mechanism.

### GUI form (`tbxManager()`)

Scilab `uicontrol` window: scrollable list, one row per known toolbox =
`[checkbox] name  (status: installed/loaded/available)  [tag: verified | build-only]`.
Buttons: **Apply** (diff the checks against the manifest → install+autoload newly-checked,
unload+unregister newly-unchecked), **Update checked**, **Close**. Known toolboxes =
union of local `SciLabProjects/*` + a curated list. Verified-on-macOS rows pre-ticked.

### Packager (`scilab/package-macos.sh`, next to build-macos.sh)

1. Ensure the engine is built + runtime-fixed (warn if `reapply-macos-fixes.sh` is stale).
2. `rsync -a --delete` the dev tree → `…app/Contents/Resources/scilab/` (skip `.git`, build
   intermediates). Incremental on refresh.
3. Write `Contents/MacOS/Scilab-2027.0.0` launcher + `Info.plist` + icon.
4. Ad-hoc codesign the copied `bin/.libs` binaries (deep) so Gatekeeper lets it run.
5. Install the `toolbox-manager/` macro lib + seed the app SCIHOME `.scilab`.
6. Symlink `scilab2027` CLI wrapper onto PATH.
7. `--rebuild-toolboxes`: after the rsync, `tbxUpdate()` all manifest entries.

First run (no manifest) auto-opens `tbxManager()` to seed.

## Upgrade workflow

```
# in the dev repo, as today
./build-macos.sh && ./reapply-macos-fixes.sh
# refresh the app (seconds; toolboxes/config untouched)
./package-macos.sh                       # + --rebuild-toolboxes if core ABI changed
```

## Implementation phases

1. **Packager + launcher** → a runnable independent app with configurable JDK + own SCIHOME
   (no toolboxes yet). Verify it boots the GUI from `/Applications`.
2. **Toolbox manager macro lib** (verbs + manifest + autoload wiring). Verify install/load/
   unload/update/remember across a restart, headless.
3. **`tbxManager()` GUI form.** Verify checkbox apply/seed.
4. **CLI symlink** + first-run seeding.
5. **Seed the verified set** (determined by test-loading each on this build) + end-to-end
   verification from the real `/Applications` app.

## Open/empirical

- Exact pre-checked "verified" set is determined in phase 5 by actually test-loading each
  toolbox against this Scilab 2027 build; build-only / heavier-dep ones ship unchecked.
- JDK pin 25 vs 26: default 25; try 26 and bump if it runs clean.
