// FOSSEE-Optimization-toolbox smoke: fot_linprog on the "Linear program with all constraint
// types" example from its own docstring (macros/fot_linprog.sci Examples section), which is
// mirrored verbatim as the toolbox's own regression test with a reference optimum
// (tests/unit_tests/fot_linprog.dia.ref): xopt=[0.1875 1.25], fopt=-0.6041667, exitflag=0.
// linprog/quadprog were verified working at port time (FINANCE-TOOLBOX-PORTING.md).
c   = [-1, -1/3]';
A   = [1, 1; 1, 1/4; 1, -1; -1/4, -1; -1, -1; -1, 1];
b   = [2, 1, 2, 1, -1, 2];
Aeq = [1, 1/4];
beq = [1/2];
lb  = [-1, -0.5];
ub  = [1.5, 1.25];
[xopt, fopt, exitflag] = fot_linprog(c, A, b, Aeq, beq, lb, ub);
smoke_ok = (exitflag == 0) & (norm(xopt(:) - [0.1875; 1.25]) < 5e-4) ...
    & (abs(fopt - (-0.6041667)) < 5e-4);
