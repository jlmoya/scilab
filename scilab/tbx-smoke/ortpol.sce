// ortpol smoke: legendre_eval, an orthogonal-polynomial evaluator (readme.txt's Legendre
// section: "legendre_eval - Evaluate polynomial"). Needs apifun (called directly); does NOT
// need stixbox despite the toolbox-level README dependency note -- confirmed by grep, no
// call chain from legendre_eval touches a stixbox_* function.
//
// Ground truth: the closed-form Legendre polynomials (also exactly what the toolbox's own
// shipped tests/unit_tests/legendre/legendre_eval.tst asserts against, via
// assert_checkalmostequal on the same formulas): P0(x)=1, P1(x)=x, P2(x)=(3x^2-1)/2 ->
// P2(0.5)=-0.125. At x=0.5 every intermediate (3*0.25, -1, /2) is exactly representable in
// binary floating point, so this holds bit-for-bit, not just to tolerance.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
p0 = legendre_eval(0.5, 0);
p1 = legendre_eval(0.5, 1);
p2 = legendre_eval(0.5, 2);
smoke_ok = (p0 == 1) & (p1 == 0.5) & (p2 == -0.125);
