// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// unit tests for parquetRead function
// =============================================================================

dt = datetime(2026, 1, 1, 9, 30, 45):calmonths(1):datetime(2026, 12, 31);
dt = dt';
d = [1:12]';
i = int32(d);
str = "A" + string(d);
b = [%f; %t; %t; %f; %f; %t; %f; %f; %f; %t; %t; %f];
t = table(dt, d, i, str, b, "VariableNames", ["date", "double", "int32", "string", "bool"]);
path = fullfile(TMPDIR, "table.parquet");
parquetWrite(path, t);

expected = parquetRead(path);
assert_checkequal(expected, t);

path = fullfile(TMPDIR, "table.arrow");
parquetWrite(path, t);

expected = parquetRead(path);
assert_checkequal(expected, t);

// errors
msg = msprintf(_("%s: Wrong number of input argument(s): %d expected.\n"), "parquetRead", 1);
assert_checkerror("parquetRead()", msg);
msg = msprintf(_("%s: Wrong type for input argument #%d: Must be in %s.\n"), "parquetRead", 1, sci2exp("string"));
assert_checkerror("parquetRead(123)", msg);
msg = msprintf(_("%s: Wrong value for input argument #%d: File ""%s"" does not exist.\n"), "parquetRead", 1, "nonexistent.parquet")
assert_checkerror("parquetRead(""nonexistent.parquet"")", msg);