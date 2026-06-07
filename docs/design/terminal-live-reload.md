# Embedded Terminal + Live External-Change Awareness — Design

**Status:** design approved (via grilling session), not yet implemented.
**Platform focus:** macOS arm64 first; macOS/Linux share the POSIX path; Windows deferred.
**Scilab:** dev branch 2027, built from source.

## 1. Context — why this exists (the thesis)

This is **one project with two coupled deliverables**:

1. An **embedded terminal** inside the Scilab GUI — to run any shell command, and in
   particular to run **Claude Code** (`claude --dangerously-skip-permissions -c`) from
   inside Scilab, exactly like the terminal embedded in JetBrains IDEs.
2. A **live external-change awareness** system — so that when a process running in that
   terminal (Claude, a build script, anything) creates/edits/deletes files, Scilab's GUI
   **and interpreter reflect those changes as they happen**.

The second is not a follow-on; it is a **first-class requirement**. A terminal running Claude
is only valuable if Scilab is a live window onto what Claude is doing — you open files while
it works to judge whether it's on the right track. Without live reflection you'd be quitting
and reopening Scilab after every change, which defeats the purpose. **Neither feature ships
"done" without the other.**

A happy structural fact ties them together: **JNA** is the native bridge for *both* — the
terminal's `forkpty` PTY *and* the file watcher's native FSEvents. One dependency, two features.

---

## 2. Feature 1 — Embedded terminal

| Decision | Choice | Rationale |
|---|---|---|
| Fidelity | **Full PTY emulator** | Claude is a full-screen TUI (alt-screen, truecolor, raw input); only a real PTY can host it. |
| Window | **Dockable FlexDock tab, multiple sessions** | Matches Variable Browser/SciNotes; float/redock for free; run several shells. |
| Widget | **JediTerm** (`jediterm-core` + `jediterm-ui`) | The same engine JetBrains IDEs use — the user's reference model. LGPLv3/Apache-2.0. |
| PTY backend | **Own `TtyConnector` over `forkpty(3)` via JNA** — **no pty4j** | pty4j is EPL-1.0 (GPL-incompatible → blocks upstream) and ships native binaries needing macOS codesigning. forkpty lives in libSystem → no new native binary; license-clean. |
| Shell | **`$SHELL -l`** (user uses **bash**), login shell | A `.app`/Finder launch inherits launchd's minimal PATH; a **login shell** sources `~/.bash_profile`/`~/.bashrc` and rebuilds the real PATH so `claude`/`node` resolve — exactly like Terminal.app. |
| Environment | inherit Scilab env + `TERM=xterm-256color` + UTF-8 `LANG`/`LC_ALL`; resize via `TIOCSWINSZ`/SIGWINCH | So colors/box-drawing/spinners render and the TUI repaints on resize/dock/float. |
| Start dir | Scilab's `pwd()` (the effective project root); overridable via prefs | Mirrors CLion opening at project root. |
| Command | **bare `terminal()`** | General terminal; type any command incl. `claude --dangerously-skip-permissions -c`. No Claude coupling (the user passes args, so a fixed "run claude" button is useless). |
| Menu | Applications → **Terminal** (`with_module` guard, like Xcos) | `modules/gui/etc/main_menubar.xml:103`. Needs a `utilities-terminal` icon (only `utilities-system-monitor` exists today). |
| Prefs | **Full Terminal pane** (shell override, starting dir, scrollback, bell, + **disabled** `cd`-follow & send-to-terminal toggles "coming soon") + a `terminal-font` item in the global Fonts pane | The user wants config in the Preferences page; toggles scaffolded now. |
| Code home | **New upstream-quality `terminal` module** | Distinct feature; proper long-term home. |

### Terminal architecture
- `SwingScilabTerminalPanel extends SwingScilabDockablePanel` (FlexDock) + a `TabFactory`,
  shown via `SwingScilabWindow.createWindow(true)` — mirrors `ScilabVariableBrowser`.
- Holds a JediTerm `JediTermWidget`/`TerminalPanel` whose `TtyConnector` is **our own**
  `ForkptyTtyConnector` (JNA → `forkpty`, `ioctl(TIOCSWINSZ)`, read/write master fd, reap child).
- `terminal()` exposed via the standard GIWS + minimal gateway (like `browsevar()`); a thin
  `.sci` launcher is the fallback if we want to avoid a module native lib on macOS.

---

## 3. Feature 2 — Live external-change awareness

"Holistic" is bounded: only **file-backed** surfaces can go stale. The Variable Browser and
workspace are in-memory → correctly out of scope. So the target is three subscribers behind
one watcher.

| Decision | Choice |
|---|---|
| Watcher | **One shared, recursive `FileSystemMonitor`** (net-new; today's only watcher is File-Browser-local, non-recursive, create/delete-only, and on macOS polls ~10 s) |
| Watcher tech | **`io.methvin:directory-watcher`** (Apache-2.0) — native **FSEvents/inotify/ReadDirectoryChangesW**, recursive, modify events; runs on **the same JNA we add for the terminal** |
| Watch scope | Scilab working-dir/project tree + loaded library source dirs + open-file dirs (not the whole disk) |
| Hygiene | Debounce/coalesce bursts; **self-write filtering** (ignore Scilab's own saves → no loops) |
| File Browser | extend to **recursive + ENTRY_MODIFY**, subscribe to the shared monitor |
| SciNotes | flip from **focus-only** to **proactive, conflict-aware** reload: clean buffer → silent auto-reload; unsaved local edits → prompt |
| **Interpreter** | **Auto-reload loaded libraries when idle, with a visible notice** (configurable Auto/Notify/Off) |

### Validated seams (from due diligence)
- **Interpreter reload** = re-run **`genlib(libdir)`**, which builds a fresh `types::Library`
  and atomically swaps it in `symbol::Context` (`loadlib.cpp` → `Context::put`).
  - Safe: **workspace variables untouched** (separate container); a function mid-execution
    **finishes on old code**, new calls get new code; only blocked by `funcprot` protection.
  - `MacroFile` caches its compiled AST permanently and never re-reads disk on its own →
    a fresh `genlib` (new `MacroFile`s) is the correct trigger.
- **Safe trigger channel** = `org.scilab.modules.action_binding.InterpreterManagement`
  `.putCommandInScilabQueue("genlib(...)")` → `StoreCommand` → runs on the interpreter
  thread at the prompt. Idle is provable via `StaticRunner::isRunning()`. The existing
  post-command hook at **`modules/core/src/cpp/runner.cpp:203`** (where `UpdateBrowseVar()` /
  `FileBrowserChDir()` already fire) is the natural place to drain pending reloads/refreshes.
- **SciNotes** already has `SciNotes.reload(index)` and dirty tracking via
  `ScilabDocument.isContentModified()` — enough for conflict-aware reload. The current
  focus-only check (`ScilabEditorPane.updateInfosWhenFocused` / `checkExternalModif`) becomes
  a fallback.
- **macOS WatchService is the polling impl (~10 s lag)** — confirmed; hence native FSEvents.

### Change-awareness architecture
```
io.methvin directory-watcher (native FSEvents via JNA)
        │  (debounced, self-write filtered, off-EDT)
        ▼
FileSystemMonitor (new singleton service, recursive, pub/sub)
        ├──► File Browser      → ENTRY_*  → refresh tree (EDT)
        ├──► SciNotes          → ENTRY_MODIFY of open file → clean? auto-reload : prompt (EDT)
        └──► Interpreter reload → .sci in a loaded lib dir → putCommandInScilabQueue("genlib(dir)")
                                   → runs at runner.cpp idle hook → "↻ reloaded library pims (3 macros)"
```

---

## 4. Dependencies (all GPL-compatible → upstreamable)

`jediterm-core`, `jediterm-ui` (LGPLv3/Apache-2.0) · `kotlin-stdlib` (Apache-2.0; JediTerm is
~19% Kotlin) · `jna` + `jna-platform` (Apache-2.0/LGPL) · `io.methvin:directory-watcher`
(Apache-2.0, + small slf4j). **No pty4j, no EPL, no bundled PTY binary.**

Wiring (per the existing model): drop jars in `thirdparty/` → declare in
`scilab-lib.properties` + `configure.ac` (`AC_JAVA_CHECK_JAR`) → `etc/classpath.xml.in`
(`load on="Terminal"`/`"Console"`) → `build.incl.xml` compile classpath. JNA's `jnidispatch`
self-extracts at runtime exactly like JOGL's native libs already do here.

---

## 5. Plan (phased)

- **Phase 1 — Terminal spike (de-risk).** JNA `forkpty` connector + JediTerm widget in a
  `SwingScilabDockablePanel`, hardcoded `bash -l`, docked. Success = `claude
  --dangerously-skip-permissions -c` runs with full color, input, and repaint-on-resize.
  Proves classpath (kotlin/jna/jediterm), JNA native extraction, and the login-shell PATH fix.
- **Phase 2 — Terminal full module.** `modules/terminal` (Makefile.am, build.xml, `.start`,
  `modules.xml`+MODULES), `terminal()` gateway, Applications menu + icon, multiple sessions,
  `SIGHUP` on close, full prefs pane + `terminal-font`, help page, i18n, basic test.
- **Phase 3 — Display-tier live reload.** Shared `FileSystemMonitor` (directory-watcher,
  native FSEvents); File Browser → recursive + modify; SciNotes → proactive conflict-aware
  reload.
- **Phase 4 — Interpreter-tier live reload.** `genlib` hot-reload via `putCommandInScilabQueue`
  at the `runner.cpp` idle hook; auto-when-idle + notice; unified reload prefs (Auto/Notify/Off).

Phases 3–4 may proceed in parallel with/after Phase 2.

---

## 6. Risks / watch-items
- **forkpty correctness:** controlling tty, raw termios, `TIOCSWINSZ`/SIGWINCH, child reaping, EOF on exit.
- **macOS native libs:** JNA + JOGL precedent says runtime extraction + ad-hoc signing works; a C gateway's libtool lib still needs the `minos 11.0` + codesign step from `reapply-macos-fixes.sh`.
- **Interpreter reload:** only when idle (use the queue, never mutate Context from a watcher thread); respect `funcprot`; expect "finishes on old code" mid-call.
- **Watcher hygiene:** debounce bursts; filter Scilab's own writes to avoid reload loops.
- **Versions:** pick a JDK-17-compatible JediTerm and the matching `kotlin-stdlib`.

## 7. Deferred / explicitly out of scope for now
- Windows (a ConPTY `TtyConnector` + the Windows watcher backend).
- Terminal↔Scilab coupling toggles (`cd`-follow, send-selection-to-terminal) — scaffolded
  in prefs as disabled.
- Variable Browser / workspace reflection of disk (in-memory by design).
