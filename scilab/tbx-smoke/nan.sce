// nan smoke: two calls -- (1) nan_mean() on data with a NaN, a hand value by
// construction (mean of {1,3} skipping the NaN = 2, per the wave-2 brief's
// own example); (2) a DIRECT call to the native sumskipnan_mex gateway
// (sci_gateway/c/sumskipnan_mex.cpp) using the exact signature
// macros/sumskipnan.sci uses internally.
//
// nan_mean's own call chain (nan_mean -> sumskipnan) wraps sumskipnan_mex in
// a try/catch that silently falls back to a pure-macro sum on ANY native
// failure (macros/sumskipnan.sci, the "catch" block) -- so nan_mean alone
// would still pass on a broken/missing gateway and would not be a
// discriminating native-path check. Calling sumskipnan_mex directly, outside
// any try/catch, bypasses that fallback: a broken gateway either errors here
// (honest smoke FAIL) or returns a wrong value (ok2 false).
a = nan_mean([1 %nan 3]);
ok1 = (a == 2);
x = [1 1 %nan];
[o, cnt] = sumskipnan_mex(real(x), 2, %f, []);
ok2 = (o == 2) & (cnt == 2);
smoke_ok = ok1 & ok2;
