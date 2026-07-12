// sciSymPy smoke: a real symbolic round-trip through the toolbox (a thin
// pass-through over PIMS' Python bridge), mirroring the toolbox's own
// regression test (tests/unit_tests/sympy.tst: same sp/symbol("x") shape,
// diff instead of integrate). sciSymPy's loader.sce (already exec'd by
// tbxVerify before this runs) only registers its own macro library -- it does
// not load PIMS, so the pyImport/pylib gateway that sympy() needs is not yet
// available here; load PIMS first, the same sibling-toolbox pattern already
// used by fmincont -> sci-ipopt and pso-toolbox -> apifun.
exec(fullfile(cfg.projects, "PIMS", "loader.sce"), -1);
sp = sympy();
x = symbol("x");
d = sp.diff(sp.sympify("x**2"), x);
smoke_ok = (grep(pystr(d), "2*x") <> []);
