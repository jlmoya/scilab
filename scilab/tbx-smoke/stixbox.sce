// stixbox smoke: quantile() sample-quantile estimator (default method=1), the full
// 10-component result vector lifted verbatim from the toolbox's own regression test
// tests/unit_tests/quantile.tst.
//
// Ground truth: quantile.tst's own expected column for p=linspace(0.1,0.9,10)' on this
// fixed 20-element sample (its 5x4 matrix flattened column-major via x(:)). Checking
// the full 10-vector (not just one point) discriminates a broken/no-op implementation
// (e.g. one returning mean(x) or median(x) for every p, or a mis-ordered/mis-scaled
// interpolation) from the real one; all 10 components were re-confirmed against the
// .tst before trusting them, at the same 1.e-5 tolerance the toolbox's own test uses.
x = [
   0.4827129   0.3431706  -0.4127328    0.3843994
  -0.7107495  -0.2547306   0.0290803    0.1386087
  -0.7698385   1.0743628   1.0945652    0.4365680
  -0.5913411  -0.7426987   1.609719     0.8079680
  -2.1700554  -0.7361261   0.0069708    1.4626386
];
x = x(:);
p = linspace(0.1, 0.9, 10)';
q = quantile(x, p);
expected = [-0.7562686; -0.7290770; -0.5814184; -0.2810643; 0.0204822; ..
            0.2181605; 0.3930942; 0.4801493; 1.0003642; 1.2786019];
smoke_ok = and(abs(q - expected) < 1e-5);
