// distfun smoke: two native cdf/pdf gateway entry points reached through
// their thin validating macro wrappers (distfun_normcdf -> native
// distfun_cdfnorm at macros/distfun_normcdf.sci:96; distfun_normpdf ->
// native distfun_pdfnorm at macros/distfun_normpdf.sci:92 -- both gateway
// functions are registered directly via addinter, see
// sci_gateway/cdf/loader.sce).
//
// Ground truth: p0 is exact by symmetry of the normal CDF about its mean
// (Phi(0)=0.5); d0 is the exact analytic peak density of the standard normal,
// 1/sqrt(2*pi); p1 is lifted from the toolbox's own
// tests/unit_tests/norm/normcdf.tst ("See upper tail" block).
p0 = distfun_normcdf(0, 0, 1);
p1 = distfun_normcdf(7, 4, 1);
d0 = distfun_normpdf(0, 0, 1);
ok1 = (abs(p0 - 0.5) < 1e-14);
ok2 = (abs(p1 - 0.99865010196837) < 1e-12);
ok3 = (abs(d0 - 0.3989422804014327) < 1e-12);
smoke_ok = ok1 & ok2 & ok3;
