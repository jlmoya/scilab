// fmincont smoke: fmincon() is unconditionally ipopt-backed (macros/fmincon/fmincon.sci:
// "Currently, we use ipopt for the actual solver of fmincon"; only fmincon_ipopt.sci exists
// as a backend) so load its real runtime dependency sci-ipopt (a sibling toolbox, already
// verified together at port time) first, then solve the exact QP recorded as verified in
// FINANCE-TOOLBOX-PORTING.md: "fmincon solves min x1^2+x2^2 s.t. x1+x2>=1 -> [0.5,0.5]".
exec(fullfile(cfg.projects, "sci-ipopt", "loader.sce"), -1);
function f = smoke_fmincont_obj(x)
    f = x(1)^2 + x(2)^2;
endfunction
A = [-1 -1];
b = [-1];
x0 = [1; 1];
[xcomp, fval] = fmincon(smoke_fmincont_obj, x0, A, b);
xcomp = xcomp(:);
smoke_ok = (norm(xcomp - [0.5; 0.5]) < 1e-4) & (abs(fval - 0.5) < 1e-4);
