// sci-ipopt smoke: gateway-only toolbox (zero macro-library delta), so load alone proves
// nothing -- its historical failure mode was at SOLVE time (MPI_Comm_f2c abort before
// MPI_INIT, see macos-fix-arpack-mpi.sh / FINANCE-TOOLBOX-PORTING.md), not load time. This
// actually calls ipopt() and solves, lifted from the toolbox's own regression test
// (tests/unit_tests/ipopt_rosenbrock.tst): minimize the Rosenbrock function subject to a
// nonlinear inequality constraint (x1^2+x2^2<=1.5), exact Hessian. Reference solution is
// the toolbox's own .tst assertion, matching FINANCE-TOOLBOX-PORTING.md's port-time
// verification note ("ipopt solves constrained Rosenbrock -> [0.90723,0.82276]").
function y = smoke_ipopt_f(x, x_new)
    y = 100.0*(x(2)-x(1)^2)^2 + (1-x(1))^2;
endfunction
function y = smoke_ipopt_df(x, x_new)
    y(1) = -400*(x(2)-x(1)^2)*x(1) - 2*(1-x(1));
    y(2) = 200*(x(2)-x(1)^2);
endfunction
function y = smoke_ipopt_g(x, x_new)
    y = x(1)^2 + x(2)^2 - 1.5;
endfunction
function y = smoke_ipopt_dg(x, x_new)
    y(1) = 2*x(1);
    y(2) = 2*x(2);
endfunction
function y = smoke_ipopt_Hf(x, x_new)
    y = zeros(2,2);
    y(1,1) = diag(-400*x(2) + 1200*x(1).^2 + 2);
    y(2,2) = 200;
    y = y - diag(400*x(1),1) - diag(400*x(1),-1);
endfunction
function y = smoke_ipopt_Hg(x, x_new)
    y = [2 0; 0 2];
endfunction
function y = smoke_ipopt_hessian(x, new_x, obj_weight, lambda, new_lambda)
    Hf = smoke_ipopt_Hf(x);
    Hg = smoke_ipopt_Hg(x);
    y  = obj_weight*Hf + lambda(1)*Hg;
    y = y([1 2 4]);
endfunction

sparse_dg = [1 1; 1 2];
sparse_dh = [1 1; 2 1; 2 2];
x0              = [-1.9 2.0]';
var_lin_type    = [1 1];
constr_lin_type = 1;
constr_rhs      = 0;
constr_lhs      = -%inf;
params = struct();
params.hessian_approximation = "exact";

ie = execstr(..
    "[x_sol, f_sol, extra] = ipopt(x0, smoke_ipopt_f, smoke_ipopt_df, " + ..
    "smoke_ipopt_g, smoke_ipopt_dg, sparse_dg, smoke_ipopt_hessian, sparse_dh, " + ..
    "var_lin_type, constr_lin_type, constr_rhs, constr_lhs, [], [], [], params);", ..
    "errcatch");
if ie <> 0 then
    // Surface the historical MPI-abort failure mode (or any other solve-time error)
    // distinctly from a load-time failure -- diagnostic value if this ever regresses.
    smoke_ok = %f;
    error("sci-ipopt smoke: solve failed: " + lasterror());
end
smoke_ok = (norm(x_sol - [0.90723379674169202; 0.82275515858492032]) < 1e-4);
