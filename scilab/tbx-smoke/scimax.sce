// STATUS: RESOLVED -- the Maxima IPC handshake blocker was fixed in the scimax
// repo (commit 32d984290e5: load-time flushing main-prompt in loader.lisp +
// restored two-step handshake in maxinit.c, pipe-only, no pty) and this smoke
// passes: scimax PASS delta=2; smoke=OK. It stays committed as the tripwire
// for future Maxima/SBCL regressions: without it, the harness's delta>=1
// criterion alone would false-PASS scimax (its loader registers 2 macro
// libraries cleanly regardless of whether the CAS handshake works).
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
