// number smoke: number_isprime + number_getprimefactors, the toolbox's flagship primality/
// factorization functions (readme.txt's "Prime numbers" / "Factorization" sections). Needs
// apifun (called directly by both).
//
// Ground truth lifted verbatim from number's own shipped regression tests:
// tests/unit_tests/isprime.tst (integerlist=[1 2 ... 9 10 73 32003],
// expectedlist=[%f %t %t %f %t %f %t %f %f %f %t %t] -- 9 -> %f, 73 -> %t);
// tests/unit_tests/getprimefactors.tst (number_getprimefactors(120) -> p=[2 3 5]',
// e=[3 1 1]', i.e. 120=2^3*3*5).
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
isp73 = number_isprime(73);
isp9  = number_isprime(9);
[p, e] = number_getprimefactors(120);
smoke_ok = (isp73 == %t) & (isp9 == %f) & isequal(p, [2 3 5]') & isequal(e, [3 1 1]');
