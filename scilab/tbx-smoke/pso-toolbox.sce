// pso-toolbox smoke: PSO_inertial depends on apifun (DESCRIPTION: Depends: apifun) for its
// input-checking helpers (apifun_checkrhs/checktype/...), so load that sibling toolbox first
// (same pattern as fmincont -> sci-ipopt). Then run the toolbox's own regression case
// (tests/unit_tests/PSO_inertial.tst + objective.sce): a 2D sphere function, D=2, N=20,
// itmax=100, default weights/c/verbose -- matches FINANCE-TOOLBOX-PORTING.md's port-time
// verification note ("PSO_inertial on 2D sphere -> f=1.15e-34, ||xopt||=1.07e-17, essentially
// exact global min"). costf must be vectorized: PSO passes the swarm as N x D and requires an
// N x 1 fitness column (sum(x.^2,"c")), not a scalar -- a real gotcha logged at port time.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
function f = smoke_pso_sphere(x)
    f = sum(x.^2, "c");
endfunction
D = 2;
bounds = [-100*ones(D,1), 100*ones(D,1)];
speed  = [-10*ones(D,1), 10*ones(D,1)];
itmax = 100;
N = 20;
grand("setsd", 0);
[fopt, xopt] = PSO_inertial(smoke_pso_sphere, bounds, speed, itmax, N);
// Structural check: fitness near the known global optimum (0), optimum lies within bounds,
// xopt has the expected 1xD shape.
smoke_ok = (fopt < 1e-1) & isequal(size(xopt), [1 D]) & (norm(xopt) < 1e-1) ...
    & and(xopt >= bounds(:,1)') & and(xopt <= bounds(:,2)');
