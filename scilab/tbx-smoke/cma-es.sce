// cma-es smoke: the classic cma_optim() run-loop needs a terminal (verified working
// interactively at port time — FINANCE-TOOLBOX-PORTING.md: "Cannot access term attributes"
// headless, not a port defect), so exercise the non-interactive ask/tell API instead
// (macros/cma_new.sci, cma_ask.sci, cma_tell.sci, cma_best.sci). Stochastic by nature
// (cma_ask samples from a Gaussian), so this is a structural check, not an exact one.
// logmodulo=0 (which also zeroes plotmodulo, see cma_new.sci) keeps this run headless-safe:
// no file writes, no plot trigger.
p = cma_getdefaults();
p.x0 = zeros(3,1);
p.sigma0 = 0.5;
p.verb.logmodulo = 0;
p.verb.displaymodulo = 0;
es = cma_new(p);
lambda = es.sp.lambda;
X = cma_ask(es, lambda, 1);
fit = zeros(1, lambda);
for k = 1:lambda
    fit(k) = sum(X(k).^2);
end
es = cma_tell(es, X, fit);
[fbest, xbest] = cma_best(es);
smoke_ok = (length(X) == lambda) && (size(X(1), 1) == es.N) && (es.countiter == 1) ...
    && (fbest >= 0) && (size(xbest, 1) == es.N) && ~isnan(fbest);
