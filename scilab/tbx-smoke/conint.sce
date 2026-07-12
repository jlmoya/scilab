// conint smoke: conint_normmu, a confidence interval for a normal mean (readme.txt's first
// listed feature: "C.I. of the mean of a normal variable"). Needs apifun (called directly);
// the actual math is core Scilab's cdfnor -- no other cross-toolbox dependency.
//
// Ground truth: conint_normmu's own docstring worked example, cited to Sheldon Ross,
// "Introduction to probability and statistics for engineers and scientists", 3rd ed.,
// Example 7.3a p.241: x=[5,8.5,12,15,7,9,7.5,6.5,10.5], n=9, known variance v=4, default
// two-sided 95% CI. Independently recomputed (not copied from the docstring's own rounded
// 7.69/10.31) via the standard-normal 97.5th percentile z=1.9599639845400545:
// low=mean(x)-z*sqrt(v)/sqrt(n)=7.693357343639963, up=mean(x)+z*sqrt(v)/sqrt(n)
// =10.306642656360037.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
x = [5, 8.5, 12, 15, 7, 9, 7.5, 6.5, 10.5];
n = size(x, "*");
me = mean(x);
v = 4;
[low, up] = conint_normmu(n, me, v);
smoke_ok = (abs(low - 7.693357343639963) < 1e-6) & (abs(up - 10.306642656360037) < 1e-6);
