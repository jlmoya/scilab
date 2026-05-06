// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// unit tests for parquetWrite function
// =============================================================================

for extension = [".parquet", ".arrow"]
    // double
    path = fullfile(TMPDIR, "double" + extension);
    t = table(rand(3,1), [1; 2; 3], [4.5; 9.33; 5.6784], "VariableNames", ["a", "b", "c"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // int8 - int16 - int32 - int64
    path = fullfile(TMPDIR, "int" + extension);
    t = table(int8([0:10]'), int16([11:21]'), int32([22:32]'), int64([33:43]'), "VariableNames", ["int8", "int16", "int32", "int64"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // uint8 - uint16 -uint32 - uint64
    path = fullfile(TMPDIR, "uint" + extension);
    t = table(uint8([0:10]'), uint16([11:21]'), uint32([22:32]'), uint64([33:43]'), "VariableNames", ["uint8", "uint16", "uint32", "uint64"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // bool
    path = fullfile(TMPDIR, "bool" + extension);
    t = table([1;0;1;0],[%t;%f;%t;%f], [%T;%F;%T;%F], "VariableNames", ["d", "bool1", "bool2"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // string
    path = fullfile(TMPDIR, "string" + extension);
    t = table(string([1;2;3]), ["a";"b"; "c"], "VariableNames", ["string1", "string2"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // datetime
    path = fullfile(TMPDIR, "datetime" + extension);
    t = table([datetime(); datetime("today"); datetime("tomorrow")], [-1; 0; 1], "VariableNames", ["date", "double"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);

    // duration
    path = fullfile(TMPDIR, "duration" + extension);
    t = table([datetime(); datetime("today"); datetime("tomorrow"); datetime("yesterday")], [hours(1); minutes(1); seconds(1); milliseconds(1)], "VariableNames", ["date", "duration"]);
    parquetWrite(path, t)

    expected = parquetRead(path);
    assert_checkequal(expected, t);
end

// errors
assert_checkerror("parquetWrite()", "parquetWrite: Wrong number of input argument(s): 2 expected.");
assert_checkerror("parquetWrite(1,1)", "parquetWrite: Wrong type for input argument #1: Must be in ""string"".");
assert_checkerror("parquetWrite(""1"",1)", "parquetWrite: Wrong type for input argument #2: Must be in ""table"".");
assert_checkerror("parquetWrite(fullfile(TMPDIR, ""test.csv""), t)", "parquetWrite: Wrong extension for input argument #1: Supported extensions are [""parquet"",""arrow""].");