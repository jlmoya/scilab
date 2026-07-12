// anova smoke: anova_anova1, the toolbox's flagship one-way ANOVA (its own header docstring
// is entirely built around this one worked example; anova_manova is the only sibling).
// Needs apifun (anova_anova1.sci calls apifun_checkrhs/checklhs directly) and distfun
// (distfun_fcdf, the F-distribution CDF used for the p-value) -- neither is auto-loaded by
// anova's own loader.sce/etc/anova.start, both are already-verified toolboxes (cfg.projects).
// nanmean/nansum/thrownan are core Scilab (modules/statistics), not a toolbox dependency.
//
// Ground truth: anova_anova1's own docstring worked example (y = 4 groups x 4 obs, one group
// per column), independently re-derived by hand rather than trusted as printed: group means
// [20 23 22 27], grand mean 23, SSB=104, SSW=118, df_b=3, df_w=12 -> f=104/3/(118/12)
// =3.525423728813559 exactly (rational arithmetic). The p-value (1 minus the F(3,12) CDF at
// f) was cross-checked with an independent regularized-incomplete-beta computation in Python:
// pval=0.04871293691107015. Both match the docstring's own rounded f=3.5254, p-value=0.0487.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
exec(fullfile(cfg.projects, "distfun", "loader.sce"), -1);
y = [17, 25, 22, 26; 19, 27, 21, 24; 20, 18, 19, 30; 24, 22, 26, 28];
[pval, f, df_b, df_w] = anova_anova1(y, 0.01);
smoke_ok = (abs(f - 3.525423728813559) < 1e-9) & (abs(pval - 0.04871293691107015) < 1e-6) ...
    & (df_b == 3) & (df_w == 12);
