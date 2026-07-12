// STATUS: scimax is NOT verified today (docs/design/toolbox-verification.md,
// ### scimax). This smoke is expected to hang/FAIL until the Maxima subprocess
// IPC blocker documented there is fixed -- maxinit()'s handshake with the
// forked `maxima` process never returns. Committed deliberately: without it,
// the harness's delta>=1 criterion alone false-PASSes scimax (its loader
// registers 2 macro libraries cleanly even though the CAS handshake hangs).
//
// scimax smoke: round-trip a symbolic derivative through the Maxima CAS
// subprocess. Requires the `maxima` binary on PATH at runtime (gate loudly
// if missing -- "verified" means runnable here, not just built).
// Entry points are scimax's raw gateway (macros/*sce convenience wrappers
// are not built in this port -- see docs/design/toolbox-verification.md):
//   Syms(name)            -- declare a symbolic variable, bound by name
//   x^2                   -- sym^constant, via the newfun op-overload chain
//   maxevalf(maxnam, ...) -- call a Maxima function by name on Scilab/sym args
//   sym.rep               -- the mlist's raw Maxima-syntax text field
// No pre-check for the `maxima` binary: maxinit() itself calls Scierror()
// and returns nonzero when execlp("maxima",...) fails, which the harness's
// errcatch wrapper turns into a loud smoke FAIL -- exactly the desired
// "verified means runnable here" gate, with no extra plumbing.
smoke_ok = %f;
maxinit();
Syms('x');
y = maxevalf('diff', x^2, x);
r = y.rep;
smoke_ok = (grep(r, "2") <> []);
maxkill();
