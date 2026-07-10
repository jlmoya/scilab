# JCEF embedded browser — macOS notes (CEF 146)

Scilab's `uicontrol(..., "style", "browser", ...)` embeds a Chromium browser via
JCEF. On this fork the JCEF payload is pinned to **jcefbuild 1.0.70 / CEF 146**
(`fetch-thirdparty.sh`): CEF 135 crashed on macOS 26 in the SkyLight HID decode
path. `SwingScilabBrowser` is the only consumer of the JCEF stack; the Help
browser on this fork does **not** use JCEF.

This note records the two AppKit-thread exceptions that were investigated under
task #100 and how each was resolved.

## 1. Shutdown `IllegalStateException: CefApp was terminated` — FIXED

### Symptom
On quit (Cmd+Q) with a browser uicontrol open, stderr printed a full stack:

```
java.lang.IllegalStateException: CefApp was terminated
    at org.cef.CefApp.getInstance(CefApp.java:226)
    at org.cef.CefClient.cleanupBrowser(CefClient.java:620)
    at org.cef.CefClient.dispose(CefClient.java:127)
    at org.scilab.modules.gui.utils.ScilabBrowser.release(...)
    at org.scilab.modules.gui.bridge.browser.SwingScilabBrowser.destroy(...)
    at org.scilab.modules.gui.SwingView.deleteObject(...)
    at ...GraphicController.recursiveDeleteChildren(...)
```

### Root cause
Two independent teardown paths race at shutdown:

1. The JVM shutdown hook (`ScilabBrowser.shutdown()`, registered via
   `Scilab.registerFinalHook`) calls `cefApp_.dispose()`, which **terminates**
   CefApp.
2. Scilab's graphic-object tree teardown deletes the browser uicontrol, calling
   `SwingScilabBrowser.destroy()` → `ScilabBrowser.release()` → `client.dispose()`.

If (1) wins, `client.dispose()` → `CefClient.cleanupBrowser()` →
`CefApp.getInstance()` throws because CefApp is already `TERMINATED`. The native
browser is already gone at that point, so there is nothing left to release.

### Fix
Guard both teardown entry points against an already-terminated CefApp
(`modules/gui`):

- `SwingScilabBrowser.destroy()` returns early when
  `CefApp.getState() == TERMINATED` (nothing to close/release).
- `ScilabBrowser.release()` skips `client.dispose()` when `cefApp_` is null or
  `TERMINATED`, and wraps the call in a `try/catch (IllegalStateException)` to
  cover the check→dispose window. This mirrors the existing defensive
  `try/catch` in `ScilabBrowser.shutdown()`.

Verified: a real interactive session (open browser, interact, Cmd+Q) now exits
`rc=0` with **zero** `CefApp was terminated` occurrences (previously one per
quit-with-browser-open).

## 2. `StackOverflowError` on the "AppKit Thread" — DIAGNOSED, benign

### Symptom
During browser interaction, stderr shows bare, bodyless headers:

```
Exception in thread "AppKit Thread" Exception in thread "AppKit Thread" ...
```

No class, no stack. They correlate with input: **0** occur in a scripted
open-render-quit run with no user input; 2–9 occur in interactive sessions.

### Root cause
`-Xlog:exceptions` (which writes per-line to a file and so survives the shutdown
watchdog's `Runtime.halt(0)`, unlike block-buffered stderr) shows the actual
throw on the AppKit thread (tid confirmed via `jstack`):

```
[...][259][exceptions] Exception <a 'java/lang/StackOverflowError'>
    thrown [share/runtime/javaCalls.cpp, line 372]
```

`javaCalls.cpp:372` is HotSpot's stack-overflow check refusing a native→Java
call because the AppKit (Cocoa main) thread's stack is near exhaustion. The SOEs
fire immediately after `org.cef.handler.CefFocusHandler$FocusSource` loads —
i.e. clicking into the browser triggers a deep native↔Java **focus/input**
callback chain that overruns the AppKit thread's stack. An overflowed stack
cannot print its own trace, which is why the header body is empty.

### Why it is benign
Non-fatal: CEF recovers and the browser stays fully functional (pages load,
input works, clean shutdown). The only artifact is the cosmetic bodyless header.

### Why it is not fixed here
It is a JCEF/macOS platform limitation, not a Scilab code defect. The AppKit
thread is a native Cocoa thread, so a JVM `-Xss` bump is not guaranteed to reach
it, and any mitigation would need repeated interactive tuning rounds to verify.
For a non-fatal cosmetic artifact that is not our code, this is out of proportion.
Left documented rather than papered over with an unverified JVM flag. If revisited,
the lever to try is the AppKit thread's native stack size; verification requires a
live click-into-browser session (it cannot be reproduced from a script — synthetic
input does not reach the JCEF NSView).
