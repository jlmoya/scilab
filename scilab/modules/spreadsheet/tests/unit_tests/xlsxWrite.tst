// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Valentin MULLER
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// Create test data
double_data = [1, 2, 3; 4, 5, 6];
string_data = ["A1","B1","C1";"A2","B2","C2"];
output_file = TMPDIR + "/test_write.xlsx";


//Test 1: Write basic data
xlsxWrite(double_data, output_file, "sheet", "TestSheet");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", "TestSheet");
assert_checkequal(result, double_data);
assert_checktrue(typeof(result) == "constant");
assert_checkequal(size(result), [2, 3]);
assert_checktrue(type(result) == 1);


xlsxWrite(string_data, output_file, "sheet", "TestSheet");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", "TestSheet", 'conversion', 'string');
assert_checkequal(result, string_data);
assert_checktrue(typeof(result) == "string");
assert_checkequal(size(result), [2, 3]);
assert_checktrue(type(result) == 10);



// test 2: clear
xlsxWrite([], output_file, "writemode", "overwrite");
result = xlsxRead(output_file);
assert_checktrue(typeof(result) == "constant");
assert_checkequal(size(result), [0, 0]);
assert_checkfalse(result(1, 1) == 1);


// test 3: range
// range A6:C9
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(double_data, output_file, "range", "A6:C9");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", "TestSheet");
assert_checkequal(size(result), [7, 3]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range A:C
xlsxWrite(double_data, output_file,"writemode", "overwrite", "range", "A:C");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", "TestSheet");
assert_checkequal(size(result), [2, 3]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range 3:7
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(double_data, output_file, "range", "3:7");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [4, 3]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range C6
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(double_data, output_file, "range", "C6");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [7, 5]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range 6
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(double_data, output_file, "range", "6");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [7, 3]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range [1 1; 1 2]
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", [1 1; 1 2]);
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [1, 2]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// range [1 3; 4 5]
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(double_data, output_file, "range", [1 3; 4 5]);
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [2, 5]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
// appending data
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "C1:D2");
xlsxWrite(double_data, output_file, "writemode", "append");
xlsxWrite(double_data, output_file, "writemode", "append");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1);
assert_checkequal(size(result), [6, 4]);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);

// cell data
cell_data = {"A", 2, 3;4, "B", 6; 7, 8 , "C"};
output_file = TMPDIR + "/cell_write.xlsx";
xlsxWrite(cell_data, output_file);
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "conversion", "cell");
assert_checkequal(result, cell_data);


// test 4 : Errors
// mandatory errors
assert_checkerror("xlsxWrite()", msprintf(_("%s: Wrong number of input argument(s): at least %d expected.\n"), "xlsxWrite", 2));
assert_checkerror("xlsxWrite(output_file)", msprintf(_("%s: Wrong number of input argument(s): at least %d expected.\n"), "xlsxWrite", 2));
assert_checkerror("xlsxWrite([], 123)", msprintf(_("%s: Wrong type for input argument #%d: Must be in ""%s"".\n"), "xlsxWrite", 2, "string"));
assert_checkerror("xlsxWrite(''[]'', 123)", msprintf(_("%s: Wrong type for input argument #%d: Must be in ""string"".\n"), "xlsxWrite", 2, "string"));
assert_checkerror("xlsxWrite(%t, output_file)", msprintf(_("%s: Wrong type for input argument #%d: Must be in %s.\n"), "xlsxWrite", 1, sci2exp(["double","string","cell"])));
assert_checkerror("xlsxWrite([], output_file, ''sht'', 1)", msprintf(_("%s: Unknown option ""%s"". Valid options are %s.\n"), "xlsxWrite", "sht", sci2exp(["range", "title", "subject", "description", "writemode"])));
assert_checkerror("xlsxWrite([], output_file, ''sheet'')", msprintf(_("%s: Options must be specified as key-value pairs"), "xlsxWrite"));
//range errors
assert_checkerror("xlsxWrite([], output_file, ''range'', ''aacc'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''89623789653287652'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''12:89623789653287652'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''a:'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''1:'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', '':a'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', '':1'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''B:5'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''3:B2'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''6:2'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''c:a'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1 3; 2 1])", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [3 3; 1 4])", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', ''[1 1; 1 2]'')", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', 9)", msprintf(_("%s: Wrong value for ""range"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1; 2])", msprintf(_("%s: Wrong value for ""range"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [])", msprintf(_("%s: Wrong value for ""range"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1.5 1; 2 2])", msprintf(_("%s: Wrong value for ""range"" argument: Must be integers.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1 1; 2.7 2])", msprintf(_("%s: Wrong value for ""range"" argument: Must be integers.\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [0 1; 2 2])", msprintf(_("%s: Wrong value for ""range"" argument: Must be positive (>=1).\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1 0; 2 2])", msprintf(_("%s: Wrong value for ""range"" argument: Must be positive (>=1).\n"), "xlsxWrite"));
assert_checkerror("xlsxWrite([], output_file, ''range'', [1 1; -1 2])", msprintf(_("%s: Wrong value for ""range"" argument: Must be positive (>=1).\n"), "xlsxWrite"));
// writemode errors
assert_checkerror("xlsxWrite([], output_file, ''writemode'', ''invalid_mode'')", msprintf(_("%s: Wrong value for ""%s"" argument: Must be in %s.\n"), "xlsxWrite", "writemode", sci2exp(["overwrite", "append"])));
// sheet errors
assert_checkerror("xlsxWrite([], output_file, ''sheet'', ''eiygrei'')", msprintf(_("%s: Sheet ""%s"" does not exist in the file.\n"), "xlsxWrite", "eiygrei"));
assert_checkerror("xlsxWrite([], output_file, ''sheet'', -2)", msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), "xlsxWrite", "sheet"));
assert_checkerror("xlsxWrite([], output_file, ''sheet'', 0)", msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), "xlsxWrite", "sheet"));
assert_checkerror("xlsxWrite([], output_file, ''sheet'', 9898)", msprintf(_("%s: Sheet index %d does not exist in the file.\n"), "xlsxWrite", 9898));
assert_checkerror("xlsxWrite([], output_file, ''sheet'', 9838974538978)", msprintf(_("%s: Sheet index must be a positive integer >= 1.\n"), "xlsxWrite"));
// title/subject/description errors
assert_checkerror("xlsxWrite([], output_file, ''title'', 123)", msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), "xlsxWrite", "title"));
assert_checkerror("xlsxWrite([], output_file, ''subject'', 456)", msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), "xlsxWrite", "subject"));
assert_checkerror("xlsxWrite([], output_file, ''description'', 789)", msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), "xlsxWrite", "description"));
assert_checkerror("xlsxWrite([], output_file, ''title'', 123, ''subject'', ''ssss'', ''description'', ''sss'')", msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), "xlsxWrite", "title"));



// test all options together:
xlsxWrite(double_data, output_file, "writemode", "overwrite", "range", "A1:A1");
xlsxWrite(string_data, output_file, "range", [1 3; 4 5], "sheet", 1, "title", "My Title", "subject", "My Subject", "description", "My Description", "writemode", "overwrite");
assert_checktrue(isfile(output_file));
result = xlsxRead(output_file, "sheet", 1, "conversion", "string");
assert_checkequal(size(result), [2, 3]);
assert_checktrue(typeof(result) == "string");
assert_checktrue(type(result) == 10);
result = xlsxInfo(output_file);
assert_checktrue(isfield(result, "title"));
assert_checktrue(isfield(result, "subject"));
assert_checktrue(isfield(result, "description"));
assert_checkequal(result.title, "My Title");
assert_checkequal(result.subject, "My Subject");
assert_checkequal(result.description, "My Description");
result = xlsxInfo(output_file, 1);
assert_checktrue(isfield(result, "sheet_name"));
assert_checktrue(isfield(result, "sheet_index"));
assert_checktrue(isfield(result, "max_row"));
assert_checktrue(isfield(result, "max_col"));
assert_checktrue(isfield(result, "cell_count"));
assert_checkequal(result.sheet_index, 1);
assert_checkequal(result.max_row, 2);
assert_checkequal(result.max_col, 5);
assert_checkequal(result.cell_count, 6);



// Cleanup
if isfile(output_file) then
    deletefile(output_file);
end

// <-- TEST END -->
