// lowdisc smoke: the Fast Halton sequence object, whose macro layer reaches
// native code on first use (macros/lowdisc_next.sci's ldhaltonf_next calls
// the raw gateway _lowdisc_haltonfnext; ldhaltonf_startup, invoked lazily on
// the first lowdisc_next, calls _lowdisc_haltonfnew -- see
// sci_gateway/cpp/loader.sce's list_functions for both).
//
// README.md's own "## Dependencies" section documents lowdisc as requiring
// the apifun (argument-checking helpers) and number (prime tables, used by
// ldhaltonf_new's default primeslist) ATOMS toolboxes, "loaded automatically
// by Scilab before lowdisc" -- true in a real app session, not in this
// throwaway one-toolbox harness, so load both sibling toolboxes first (same
// pattern as pso-toolbox -> apifun).
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
exec(fullfile(cfg.projects, "number", "loader.sce"), -1);
//
// Ground truth: the first six 2-D Halton terms are the base-2 and base-3
// Van der Corput sequences (1/2,1/4,3/4,1/8,5/8,3/8 and
// 1/3,2/3,1/9,4/9,7/9,2/9) -- independently hand-derivable, and matching
// tests/unit_tests/halton/haltonf.tst verbatim.
lds = lowdisc_new("halton");
lds = lowdisc_configure(lds, "-dimension", 2);
[lds, c1] = lowdisc_next(lds);
[lds, c26] = lowdisc_next(lds, 5);
lds = lowdisc_destroy(lds);
expected1 = [1/2 1/3];
expected26 = [1/4 2/3; 3/4 1/9; 1/8 4/9; 5/8 7/9; 3/8 2/9];
smoke_ok = (max(abs(c1 - expected1)) < 10*%eps) & (max(abs(c26 - expected26)) < 10*%eps);
