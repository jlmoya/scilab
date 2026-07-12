// specfun smoke: specfun_nchoosek (binomial coefficient) -- one exact case + one
// large-magnitude tolerance case, both lifted verbatim from the toolbox's own
// regression test tests/unit_tests/nchoosek.tst.
//
// Ground truth: specfun_nchoosek(5,2)=10 is the textbook C(5,2) value, exact by
// construction. specfun_nchoosek(10000,134) is documented in nchoosek.tst as
// 2.050083865033972676e307 -- independently re-derived here via Python's arbitrary-
// precision math.comb(10000,134) cast to float64, which lands on the identical
// float64 value 2.0500838650339728e307 (both decimal strings round to the same
// IEEE-754 double), confirming the toolbox's own reference before trusting it. This
// discriminates a real log-gamma-based implementation from a naive factorial-ratio one:
// 10000! alone overflows double range (doubles overflow past ~171!), so a naive
// implementation would return %inf/%nan here instead of the correct finite value.
ok1 = (specfun_nchoosek(5, 2) == 10);
big = specfun_nchoosek(10000, 134);
ok2 = (abs(big - 2.050083865033972676e307) < 1e-10 * 2.050083865033972676e307);
smoke_ok = ok1 & ok2;
