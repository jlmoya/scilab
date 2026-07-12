// quapro smoke: the native qpqpqp gateway (-> sci_quapro -> Fortran plcbas,
// sci_gateway/fortran/loader.sce's addinter list) reached through macros/quapro.sci,
// which calls qpqpqp unconditionally on every rhs branch (no try/catch anywhere in the
// macro, and no pure-Scilab QP reimplementation exists to fall back to -- a broken
// native call surfaces as an undefined-function error, not a silently different
// answer). quapro has no own tests; its 3 demos (optloc, multiflow) only exercise the
// Q=0 special case via the linpro wrapper, so this smoke uses a genuine Q<>0 problem
// instead, matching the shape of help/en_US/quapro.xml's own worked example.
//
// Ground truth: min x'*x s.t. x1+x2=1, hand-derived by Lagrange multipliers ->
// x=[0.5;0.5], f=0.5*x'*Q*x+p'*x=0.5 (Q=2*eye(2,2) makes 0.5*x'*Q*x = x1^2+x2^2 = x'*x).
Q = 2*eye(2, 2);
p = [0; 0];
C = [1 1];
b = 1;
mi = 1;
[x, lagr, f] = quapro(Q, p, C, b, [], [], mi);
smoke_ok = (max(abs(x - [0.5; 0.5])) < 1e-8) & (abs(f - 0.5) < 1e-8);
