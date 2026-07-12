// scidoe smoke: the toolbox's ONLY native entry point (scidoe_pdist,
// sci_gateway/c/sci_scidoe_pdist.c, linked against src/c/libscidoelib and
// registered directly via addinter -- see sci_gateway/c/loader.sce's
// list_functions, which names nothing else). No macro wraps it, and no
// other macro in this toolbox reaches native code: scidoe_fullfact (the
// brief's suggested direction) is pure Scilab and never calls into a
// gateway, so it was rejected as a non-discriminating check -- see report.
//
// Ground truth: pairwise Euclidean distances (condensed form) lifted from
// the toolbox's own tests/unit_tests/pdist.tst; independently spot-checked
// by hand for the first pair: norm([0.7688531 0.3089766]-[0.3185689
// 0.684731]) = norm([0.4502842 -0.3757544]) = 0.5865 (matches D(1)).
X = [0.3185689 0.684731; 0.7688531 0.3089766; 0.5430379 0.5694503; 0.9205527 0.1715891; 0.1097627 0.9247127];
D = scidoe_pdist(X);
Dexpected = [0.5865 0.2523 0.7910 0.3181 0.3447 0.2047 0.9020 0.5485 0.5603 1.1066];
smoke_ok = (length(D) == 10) & (max(abs(D(:) - Dexpected(:))) < 1.0e-3);
