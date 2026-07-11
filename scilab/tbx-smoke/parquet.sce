// parquet smoke: this toolbox regressed after Homebrew bumped apache-arrow past the
// libarrow.2400.dylib ABI the cached gateway was linked against (dlopen failure at load).
// Load succeeding again isn't proof the rebuilt gateway actually talks to the new arrow
// correctly, so round-trip a small mixed-type table (double/int32/string/bool -- the same
// type mix FINANCE-TOOLBOX-PORTING.md recorded as verified at original port time) through
// a real .parquet file in TMPDIR via the toolbox's own parquetWrite/parquetRead.
p = fullfile(TMPDIR, "tbx_smoke_parquet.parquet");
t = table(rand(3,1), int32([1;2;3]), ["a";"b";"c"], [%t;%f;%t], ..
    "VariableNames", ["d","i","s","b"]);
parquetWrite(p, t);
t2 = parquetRead(p);
smoke_ok = isequal(t.props.variableNames, t2.props.variableNames) ...
    & isequal(t.vars.data, t2.vars.data);
