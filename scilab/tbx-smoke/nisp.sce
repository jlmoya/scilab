// nisp smoke: randvar_new/setrandvar_*, the native-gateway random-variable + sampling core
// (sci_gateway/cpp/sci_randvar_new.cpp, sci_setrandvar_new.cpp — the "hardest port yet", per
// FINANCE-TOOLBOX-PORTING.md), matching its own demos (demos/randvar/demo_randvar1.sce,
// demos/setrandvar/demo_setrandvar_mc.sce) and the port-time verification note
// ("randvar_new(Normale)/setrandvar/MonteCarlo(5000) computes (size=5000,dim=1)").
// NOTE: setrandvar_getsample (used by the upstream demo to pull the raw sample matrix) is
// declared in gw_nisp.h but has no compiled/registered gateway entry in this port's
// sci_gateway/cpp/builder_gateway_cpp.sce (confirmed empirically: smoke_ok run failed with
// "Undefined variable: setrandvar_getsample" even though setrandvar_buildsample succeeded) --
// use the registered setrandvar_getsize/setrandvar_getdimension for the exact (size,dim)
// structural check the tracker itself cites, and randvar_getvalue directly (as
// demo_randvar1.sce does) for a statistical numeric check. Sampling is stochastic, so the
// mean check is wide-tolerance (std error over 1000 draws is ~0.016; 0.15 is >9 sigma).
vu1 = randvar_new("Normale", 1.0, 0.5);
srv = setrandvar_new();
setrandvar_addrandvar(srv, vu1);
setrandvar_buildsample(srv, "MonteCarlo", 5000);
nsamp = setrandvar_getsize(srv);
ndim  = setrandvar_getdimension(srv);
nb = 1000;
vals = zeros(1, nb);
for i = 1:nb
    vals(i) = randvar_getvalue(vu1);
end
m = mean(vals);
randvar_destroy(vu1);
setrandvar_destroy(srv);
smoke_ok = (nsamp == 5000) & (ndim == 1) & (abs(m - 1.0) < 0.15);
