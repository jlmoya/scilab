// financial smoke: Black-Scholes call/put pricing via bsoption() -- a closed-form
// textbook formula, the cleanest hand-checkable computation in this toolbox (financial
// has 0 own tests and 2 demos; duration()/interest() either need fsolve root-finding
// or Monte-Carlo simulation, harder to pin to an exact independent value). Reading
// macros/bsoption.sci: d1=-((log(K/S)-(r+sigma^2/2)*T)/(sigma*sqrt(T))) algebraically
// simplifies to the standard Black-Scholes d1 (log(S/K) form); d2=d1-sigma*sqrt(T);
// C=S*N(d1)-K*exp(-r*T)*N(d2); P=C+K*exp(-r*T)-S (put-call parity).
//
// Ground truth: independently computed in Python (math.erf-based normal CDF) for the
// exact parameter set used by the toolbox's own bsgreeks demo (demos/bsgreeks.sce:
// S=25, K=25, r=0.01, T=3/12, sigma=0.2): C=1.0272175223020046, P=0.9647955822385086.
// Cross-checked before trusting it: N(d1)=0.5298926440528948 matches that same demo's
// own documented call Delta (D=0.5298926 in its comments) to 4.4e-08 -- consistent
// with the demo's 7-digit rounding -- confirming both the formula reading above and
// the parameter set are right.
[C, P] = bsoption(25, 25, 0.01, 3/12, 0.2);
smoke_ok = (abs(C - 1.0272175223020046) < 1e-6) & (abs(P - 0.9647955822385086) < 1e-6);
