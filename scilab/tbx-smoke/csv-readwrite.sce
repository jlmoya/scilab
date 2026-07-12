// csv-readwrite smoke: this toolbox's gateway was never fully ported (top-level
// loader.sce execs sci_gateway/loader_gateway.sce, which was never generated --
// the build never reached that point). Loading cleanly isn't proof the gateway
// actually reads/writes correctly, so round-trip a real 2x3 matrix through a
// .csv file in TMPDIR via the toolbox's OWN writer/reader -- NOT core Scilab's
// csvRead/csvWrite/csvTextScan (which ship separately and do not collide with
// this toolbox's csv_read/csv_write/csv_* names; different case/underscore
// convention, confirmed from sci_gateway/c/builder_gateway_c.sce's own
// table_functions list).
//
// Argument order and calling convention are the toolbox's own, taken verbatim
// from its shipped regression test (tests/unit_tests/csv_readwrite.tst):
// csv_write(matrix, filename) called bare (uncaptured) -- its success path
// only ever does the toolbox's void-return idiom (LhsVar(1) = 0, no actual
// stack value), so it must be called with zero captured outputs.
p = fullfile(TMPDIR, "tbxsmoke_csv_readwrite.csv");
M = [1 2 3; 4 5 6];
csv_write(M, p);
N = csv_read(p);
smoke_ok = and(N == M);
