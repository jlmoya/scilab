// makematrix smoke: makematrix_hilbert, the toolbox's best-known generator (readme.txt's own
// intro names it by example: "the Hilbert matrix, which arises in the least squares
// approximation of arbitrary functions by polynomials"). Needs apifun (called directly).
//
// Ground truth is the Hilbert matrix's own textbook definition (A(i,j) = 1/(i+j-1); see also
// makematrix_hilbert.sci's own bibliography link), not derived by re-reading the macro body:
// the full 5x5 matrix is compared exactly (all entries are exactly representable up to IEEE
// division, and 1./(i+j-1) is exactly how the macro itself computes it), plus the symmetry
// property that makes Hilbert matrices a canonical linear-algebra test case.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
A = makematrix_hilbert(5);
E = [1     1/2   1/3   1/4   1/5; ..
     1/2   1/3   1/4   1/5   1/6; ..
     1/3   1/4   1/5   1/6   1/7; ..
     1/4   1/5   1/6   1/7   1/8; ..
     1/5   1/6   1/7   1/8   1/9];
smoke_ok = isequal(A, E) & isequal(A, A');
