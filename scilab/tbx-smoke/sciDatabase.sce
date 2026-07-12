// sciDatabase smoke: SQLite is a genuine, fully-wired engine (macros/dbEngines.sci
// lists postgresql/mysql/sqlite/mongodb/redis; dbConnect -> scidb_adapter("sqlite3")
// -> macros/scidb_sqlite.sci -> native db_sqlite_connect/exec/query/close, registered
// via addinter in sci_gateway/c/loader.sce and linked -- confirmed by otool -L on the
// built libscidatabase_native.dylib -- against the real Homebrew libsqlite3.dylib), and
// the only one of the 5 that needs no server. The other 4's local test servers
// (Postgres :5433, MySQL :3307, Mongo :27018, Redis :6380) are all down, so this smoke
// exercises SQLite only, mirroring the toolbox's own tests/test_engines.sce SQLite leg
// verbatim (that script's SQLite block runs unconditionally; its Postgres/MySQL legs
// are wrapped in try/catch "skipped"). dbQuery(db,sql,%t) is the same documented
// back-compat 3-arg form used there (macros/dbQuery.sci: type(params)==4 -> legacy
// asMatrix positional arg), confirmed by reading the macro source directly.
//
// Ground truth: tests/test_engines.sce's own SQLite leg, verbatim call shape and
// values -- insert 3 rows (10.0,20.0,30.0), mean must be 20.0 (exact by construction;
// that script prints the same "(expect 20.0)" comment).
dbfile = TMPDIR + "/sciDatabase_smoke.sqlite";
if isfile(dbfile) then mdelete(dbfile); end
db = dbConnect("sqlite", struct("database", dbfile));
dbExec(db, "create table t(id integer, v real)");
n = dbExec(db, "insert into t values(1,10.0),(2,20.0),(3,30.0)");
r = dbQuery(db, "select v from t", %t);
dbClose(db);
mdelete(dbfile);
smoke_ok = (n == 3) & (abs(mean(r) - 20.0) < 1e-9);
