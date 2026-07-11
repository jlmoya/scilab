// krisp smoke: RLHS (Latin hypercube, verified working at port time) + native corr_*
// gateway registered AND numerically correct -- the deeper, previously-broken half (see
// FINANCE-TOOLBOX-PORTING.md's krisp entry: macros worked at port time but the corr_*
// natives built without registering at load; the tbxVerify pass criterion alone
// (delta>=1 library) can't catch that regression because krisp's macro libraries
// (krisplib1/2/3) register fine on their own, masking a dead gateway).
//
// RLHS(maxiter, n, lob, upb) has no default bounds (macros/RLHS.sci) -- lob/upb are
// required, matching its own docstring example RLHS(10,2,[1 2],[1.5,3]); a bare
// RLHS(8,2) throws "Undefined variable: lob". Use unit-box bounds [0 0]/[1 1] and check
// both shape and the sampling stays inside them (rescale() ground truth in RLHS.sci).
//
// Ground truth from the gateway's own source (sci_gateway/c/sci_corr_D.c) and
// src/c/corr_functions.h:
//   c_corr_D(D, theta, funct, dm) returns the dm x dm gaussian/matern correlation matrix
//   for dm points; funct=1 selects gaussian(x,y) = exp(-0.5*(x/y)^2). The diagonal is
//   always forced to 1 (a point's correlation with itself), matching the canonical
//   kriging-correlation property corr(0) = 1. With dm=2 coincident points (distance 0,
//   D=[0]), the whole 2x2 matrix collapses to ones(2,2). A nonzero distance (3) with
//   theta=1 gives the closed-form off-diagonal value exp(-0.5*3^2) = exp(-4.5).
X = RLHS(8, 2, [0 0], [1 1]);
ok_lhs = (size(X,1) == 8 & size(X,2) == 2) & and(X >= 0) & and(X <= 1);

ok_reg = (exists("c_corr_D") == 1) & (exists("c_corr_X") == 1) & (exists("c_corr_vector") == 1);

ok_corr0 = %f; ok_gauss = %f;
if ok_reg then
    R0 = c_corr_D([0], [1], 1, 2);     // coincident points -> corr(0) = 1 everywhere
    ok_corr0 = isequal(R0, ones(2, 2));
    R1 = c_corr_D([3], [1], 1, 2);     // distance 3, theta 1, gaussian kernel
    ok_gauss = (abs(R1(1, 2) - exp(-0.5 * 3^2)) < 1e-12);
end

smoke_ok = ok_lhs & ok_reg & ok_corr0 & ok_gauss;
