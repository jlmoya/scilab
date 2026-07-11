// sciQuantLib smoke: gateway-only toolbox (zero macro-library delta). This regressed to a
// SIGTRAP crash during load with zero output; root cause (see toolbox-verification.md) was
// the repo-root loader.sce being a stale SWIG "baseline" spike pointing at a nonexistent
// libbaseline.dylib -- fixed by replacing it with a delegator to the real toolbox
// (quantlib-swig/Scilab/toolbox/loader.sce, a git submodule). Load succeeding again isn't
// proof pricing actually works, so exercise a real QuantLib call: price a canonical European
// call under Black-Scholes (flat term structures), lifted from the toolbox's own regression
// test quantlib-swig/Scilab/test/t_european.sce. Golden values are that test's documented
// analytic Black-Scholes closed form, reproduced exactly by this build:
//   npv=10.450584 delta=0.636831 gamma=0.018762 vega=37.524035 theta=-6.414028 rho=53.232482
today    = new_Date(4, 6, 2026);
exercise = new_Date(4, 6, 2027);
Settings_instance().setEvaluationDate(today);

dc  = new_Actual365Fixed();
cal = new_TARGET();

spot = new_SimpleQuote(100.0);
rTS  = new_FlatForward(today, 0.05, dc);
qTS  = new_FlatForward(today, 0.00, dc);
vol  = new_BlackConstantVol(today, cal, 0.20, dc);

hSpot = new_QuoteHandle(spot);
hRTS  = new_YieldTermStructureHandle(rTS);
hQTS  = new_YieldTermStructureHandle(qTS);
hVol  = new_BlackVolTermStructureHandle(vol);

proc = new_BlackScholesMertonProcess(hSpot, hQTS, hRTS, hVol);

payoff = new_PlainVanillaPayoff(Option_Call, 100.0);
exer   = new_EuropeanExercise(exercise);
opt    = new_EuropeanOption(payoff, exer);

eng = new_AnalyticEuropeanEngine(proc);
opt.setPricingEngine(eng);

golden = [10.450584, 0.636831, 0.018762, 37.524035, -6.414028, 53.232482];
got = [opt.NPV(), opt.delta(), opt.gamma(), opt.vega(), opt.theta(), opt.rho()];
smoke_ok = (norm(got - golden) < 1e-4);
