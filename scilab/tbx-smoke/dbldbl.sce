// dbldbl smoke: dbldbl_new + arithmetic, the toolbox's double-double numeric type
// (readme.txt: "We can add, subtract, multiply, divide, ... double-doubles"). Needs apifun
// (dbldbl_new/dbldbl_pi both call apifun_checkrhs/checklhs directly); not auto-loaded by
// dbldbl's own etc/dbldbl.start.
//
// Ground truth lifted verbatim from dbldbl's own shipped regression test
// (tests/unit_tests/dbldbl.tst, assert_checkequal): a multiplication whose low-order part
// captures the exact rounding error a plain double would silently lose -- the actual point of
// a double-double type, not just a re-skinned double -- plus the pi constant. Both are exact
// equalities in the toolbox's own test, not tolerance-based.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
a = dbldbl_new(2, 4.d-20);
b = dbldbl_new(3, 5.d-20);
c = a * b;
ddpi = dbldbl_pi();
smoke_ok = (c.dd(1) == 6) & (c.dd(2) == 2.199999999999999849e-19) & (ddpi.dd(1) == %pi);
