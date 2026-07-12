// arfit smoke: VAR(1) simulate-then-fit-then-residual-check round trip.
//
// The brief's originally-proposed smoke -- a bare [w,A,C]=arfit(v,1,2) on iid random
// data (signature confirmed from macros/arfit.sci's header: nargin==3 takes the
// default branch mcor=1, selector='sbc') -- PASSES even on the broken toolbox: arfit()
// itself never calls the undefined mtlb_repmat() compat shim (confirmed by grep across
// macros/*.sci). The actual port-time "core fns HANG" defect lives in arsim() and
// arres() (5 call sites: macros/arsim.sci:82,104,134 and macros/arres.sci:81,91), both
// part of the toolbox's documented fit-and-diagnose workflow (demos/ardem.sce) and
// both exercised below, so this smoke actually pins the bug that
// macros/mtlb_repmat.sci now fixes (see that file's header for the full root-cause
// writeup).
//
// Known-coefficient VAR(1) recovery: simulate from a fixed w/A/C via arsim(), recover
// them via arfit() forced to order 1 (pmin=pmax=1), then check the residuals are
// acceptably white via arres() -- siglev > 0.05 means the modified Li-McLeod
// portmanteau test does NOT reject uncorrelatedness (see arres.sci's own header).
rand("seed", 0);
w_true = [0.25; -0.1];
A_true = [0.5 0.1; -0.2 0.3];
C_true = [0.05 0; 0 0.05];
v = arsim(w_true, A_true, C_true, 500);
[w, A, C] = arfit(v, 1, 1);
[siglev, res] = arres(w, A, v);
smoke_ok = (norm(w - w_true) < 0.1) & (norm(A - A_true) < 0.2) & (siglev > 0.05);
