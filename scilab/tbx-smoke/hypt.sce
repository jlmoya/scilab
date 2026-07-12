// hypt smoke: hypt_ttest, one-/paired-sample t-test (hypt's readme.txt lists it in the
// MATLAB-compatible "High Priority" set). Needs apifun (arg checking) and distfun
// (distfun_tcdf/distfun_tinv, the Student-t CDF/quantile) -- neither auto-loaded by hypt's
// own loader.sce. nanmean/nansum/nanstdev are core Scilab, not a toolbox dependency.
//
// Ground truth: hypt_ttest's own docstring ships a worked example (before/after paired
// samples) but its claimed outcome ("h=%t indicates that the null hypothesis could be
// rejected") does NOT hold up -- independently re-derived below, because trusting it blind
// would have baked in a wrong assertion.
//
// before/after are fixed (non-random) data, so tstat/df/pval are all exact, deterministic
// numbers, hand-computed from the paired-t formula (d=before-after,
// t=mean(d)/(sd(d)/sqrt(n))): d has mean 6.9, sample sd 10.535126429658492, df=9,
// t=2.0711394401243206. The two-tailed p-value, computed independently in Python two ways
// (direct numerical integration of the Student-t density, and the regularized incomplete
// beta function -- both agreeing to 1e-9, and the same code reproducing the textbook df=9
// alpha=0.05 critical value of 2.262 as a sanity check) is 0.06823795853. That is ABOVE 0.05,
// so the correct decision is h=%f (fail to reject at the default alpha) -- the opposite of
// the docstring's own comment, which is simply wrong for this data.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
exec(fullfile(cfg.projects, "distfun", "loader.sce"), -1);
before = [223, 259, 248, 220, 287, 191, 229, 270, 245, 201];
after  = [220, 244, 243, 211, 299, 170, 210, 276, 242, 189];
[h, pval, ci, stats] = hypt_ttest(before, after);
smoke_ok = (abs(stats.tstat - 2.0711394401243206) < 1e-6) & (stats.df == 9) ...
    & (abs(pval - 0.06823795853) < 1e-6) & (h == %f);
