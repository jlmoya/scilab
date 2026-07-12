// condnb smoke: condnb_condnum, the toolbox's flagship generic condition-number estimator
// (readme.txt lists it first: "Computes the empirical condition number of the function f at
// point x"). Needs apifun (called directly by condnb_condnum.sci); condnb_matrixnorm/
// condnb_condnum_evalf are condnb-internal, numderivative/ieee are core Scilab -- no other
// cross-toolbox dependency.
//
// Ground truth: condnb_condnum's own docstring Examples section recommends exactly this
// cross-check ("cc = condnb_condnum(sqrt, 1, 1); // Compare with exact formula: cc =
// condnb_sqrtcond(1)"). condnb_sqrtcond's own source is the closed form c(x)=1/2 for sqrt,
// independent of x (not derived from condnb_condnum's own finite-difference machinery), so
// the finite-difference (order=1) approximation must land close to that known-exact target.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
cc = condnb_condnum(sqrt, 1, 1);
smoke_ok = (abs(cc - 0.5) < 1e-3);
