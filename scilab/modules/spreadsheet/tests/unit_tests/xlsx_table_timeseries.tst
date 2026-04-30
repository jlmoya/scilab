// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// unit tests for readtable, readtimeseries, writetable and writetimeseries functions
// =============================================================================

double_data = [1:9]';
string_data = "A" + string(double_data);
boolean_data = rand(1:9)' > 0.5;
datetime_data = datetime(2026, 3, double_data);
duration_data = hours(double_data);

t = table(double_data, string_data, boolean_data, datetime_data, duration_data, "VariableNames", ["double", "string", "bool", "date", "duration"], "RowNames", string([0:8]'));

path = fullfile(TMPDIR, "table.xlsx");
writetable(t, path, "WriteRowNames", %t);
result = readtable(path, "ReadRowNames", %t);
assert_checkequal(table2cell(result), table2cell(t));

tt = readtable(fullfile(SCI, "modules", "spreadsheet", "tests", "unit_tests", "results_without_time.csv"));
writetable(tt, path, "sheet", "sheet2");
result = readtable(path, "sheet", 2);
assert_checkequal(table2cell(result), table2cell(tt));

Names = ["toto", "titi", "tutu"]';
tt.Row = Names;

writetable(tt, path, "sheet", "sheet2", "WriteRowNames", %t);
result = readtable(path, "sheet", "sheet2", "ReadRowNames", %t);
assert_checktrue(table2cell(result) == table2cell(tt));
assert_checktrue(result.Row == tt.Row);

// timeseries
ts = table2timeseries(t);
path2 = fullfile(TMPDIR, "timeseries.xlsx");
writetimeseries(ts, path2);
result = readtimeseries(path2);
assert_checkequal(result.date, datetime_data);
assert_checkequal(result.double, double_data);
assert_checkequal(result.string, string_data);
assert_checkequal(result.bool, boolean_data);
assert_checkequal(result.duration, duration_data);

filename = fullfile(SCI, "modules", "spreadsheet", "tests", "unit_tests", "results_with_datetime.csv");
tt = readtimeseries(filename);
writetimeseries(tt, path2, "sheet", "sheet2");
result = readtimeseries(path2, "sheet", 2);
assert_checkequal(result, tt);

filename = fullfile(SCI, "modules", "spreadsheet", "tests", "unit_tests", "results_with_duration.csv");
tt = readtimeseries(filename);
writetimeseries(tt, path2, "sheet", "sheet3");
result = readtimeseries(path2, "sheet", 3);
assert_checkequal(result, tt);

// other test
filename = fullfile(TMPDIR, "test.xlsx");
expected = [1 2 3; 4 5 6];
xlsxWrite(expected, filename);
computed = xlsxRead(filename);
assert_checkequal(computed, expected);

expected = ["a" "b" "c"; "d" "e" "f"];
xlsxSheet(filename, "create", "stringSheet");
xlsxWrite(expected, filename, "sheet", "stringSheet");
computed = xlsxRead(filename, "sheet", "stringSheet", "conversion", "string");
assert_checkequal(computed, expected);

writetable(t, filename, "Sheet", "tableSheet");
computed = xlsxRead(filename, "sheet", "tableSheet", "conversion", "cell");
assert_checkequal(computed, [num2cell(t.Properties.variableNames); table2cell(t)]);
tt = t; tt.Row = [];
computed = readtable(filename, "sheet", "tableSheet");
computed.Properties.VariableDescriptions = emptystr(1,5);
assert_checkequal(computed, tt);
