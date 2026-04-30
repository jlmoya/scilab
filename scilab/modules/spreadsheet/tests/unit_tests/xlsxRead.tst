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

// Create test data and file
double_data = [1, 2, 3; 4, 5, 6; 7, 8, 9];
string_data = ["A1","B1","C1";"A2","B2","C2";"A3","B3","C3"];
test_file = TMPDIR + "/test_read.xlsx";


// Simple read without options
xlsxWrite(double_data, test_file, "writemode", "overwrite", "sheet", "Sheet1");
result = xlsxRead(test_file);
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, double_data);


// Read with conversion to string
result = xlsxRead(test_file, "conversion", "string");
assert_checktrue(typeof(result) == "string");
assert_checktrue(type(result) == 10);
assert_checkequal(size(result), [3, 3]);


// Read with conversion to double (explicit)
result = xlsxRead(test_file, "conversion", "double");
assert_checktrue(typeof(result) == "constant");
assert_checktrue(type(result) == 1);
assert_checkequal(result, double_data);


// Read by sheet index
xlsxSheet(test_file, "create", "SecondSheet");
xlsxWrite([1, 2], test_file, "sheet", "SecondSheet", "range", "A1:B1");
result = xlsxRead(test_file, "sheet", 1);
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, double_data);
result = xlsxRead(test_file, "sheet", 2);
assert_checkequal(size(result), [1, 2]);
assert_checkequal(result, [1, 2]);


// Read by sheet name
result = xlsxRead(test_file, "sheet", "Sheet1");
assert_checkequal(result, double_data);
result = xlsxRead(test_file, "sheet", "SecondSheet");
assert_checkequal(result, [1, 2]);


// RANGE
xlsxWrite(double_data, test_file, "writemode", "overwrite");
result = xlsxRead(test_file, "range", "A1:B2");
assert_checkequal(size(result), [2, 2]);
assert_checkequal(result, [1, 2; 4, 5]);
// range A:C (colonnes)
result = xlsxRead(test_file, "range", "A:C");
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, double_data);
// range 1:2 (lignes)
result = xlsxRead(test_file, "range", "1:2");
assert_checkequal(size(result), [2, 3]);
assert_checkequal(result, [1, 2, 3; 4, 5, 6]);
// range A1 (une cellule)
result = xlsxRead(test_file, "range", "A1");
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, double_data);
// range A (une colonne)
result = xlsxRead(test_file, "range", "A");
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, double_data);
// range 2 (une ligne)
result = xlsxRead(test_file, "range", "2");
assert_checkequal(size(result), [2, 3]);
assert_checkequal(result, [4, 5, 6; 7, 8, 9]);
// range matrice [1 1; 2 2]
result = xlsxRead(test_file, "range", [1 1; 2 2]);
assert_checkequal(size(result), [2, 2]);
assert_checkequal(result, [1, 2; 4, 5]);
// range matrice [1 2; 3 3]
result = xlsxRead(test_file, "range", [1 2; 3 3]);
assert_checkequal(size(result), [3, 2]);
assert_checkequal(result, [2, 3; 5, 6; 8, 9]);


// Read with all options combined
xlsxWrite(string_data, test_file, "writemode", "overwrite", "sheet", 1);
result = xlsxRead(test_file, "sheet", 1, "range", "A1:B2", "conversion", "string");
assert_checktrue(typeof(result) == "string");
assert_checkequal(size(result), [2, 2]);
assert_checkequal(result, ["A1","B1";"A2","B2"]);


// Read an empty sheet
xlsxWrite([], test_file, "writemode", "overwrite", "sheet", 1);
result = xlsxRead(test_file, "sheet", 1);
assert_checkequal(size(result), [0, 0]);


// Read mixed data (text and numbers)
xlsxWrite(string_data, test_file, "writemode", "overwrite");
result = xlsxRead(test_file, "conversion", "string");
assert_checktrue(typeof(result) == "string");
assert_checkequal(size(result), [3, 3]);
assert_checkequal(result, string_data);

// conversion cell
data = {1, %t, "A", datetime(2026, 4, 1), hours(1); 
        2, %f, "B", datetime(2026, 4, 1, 9, 30, 0), minutes(30);
        3, %t, "C", datetime(2026, 4, 1, 19, 0, 0), duration(12, 45, 15)};

xlsxSheet(test_file, "create", "cellSheet");
xlsxWrite(data, test_file, "sheet", "cellSheet");
result = xlsxRead(test_file, "sheet", "cellSheet", "conversion", "cell");
assert_checktrue(typeof(result) == "ce");
assert_checkequal(size(result), [3, 5]);
assert_checkequal(result, data);

// Errors
assert_checkerror("xlsxRead()", msprintf(_("%s: Wrong number of input argument(s): at least %d expected.\n"), "xlsxRead", 1));
// Errors - Invalid argument types
assert_checkerror("xlsxRead(123)", msprintf(_("%s: Wrong type for input argument #%d: Must be in ""%s"".\n"), "xlsxRead", 1, "string"));
assert_checkerror("xlsxRead(test_file, 123, 456)", msprintf(_("%s: Option keys must be strings.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""sheet"")", msprintf(_("%s: Options must be specified as key-value pairs.\n"), "xlsxRead"));
// Errors - Nonexistent file
assert_checkerror("xlsxRead(""nonexistent.xlsx"")", msprintf(_("%s: Wrong value for input argument #%d: File ""%s"" does not exist.\n"), "xlsxRead", 1, "nonexistent.xlsx"));
// Errors - Invalid option
assert_checkerror("xlsxRead(test_file, ""invalid_option"", ""value"")", msprintf(_("%s: Unknown option ""%s"".\n"), "xlsxRead", "invalid_option"));
// Errors - Invalid sheet value
assert_checkerror("xlsxRead(test_file, ""sheet"", %t)", msprintf(_("%s: Wrong type for ""%s"" argument: A double or string expected.\n"), "xlsxRead", "sheet"));
assert_checkerror("xlsxRead(test_file, ""sheet"", 0)", msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), "xlsxRead", "sheet"));
assert_checkerror("xlsxRead(test_file, ""sheet"", -1)", msprintf(_("%s: Wrong value for ""%s"" argument: Must be a positive integer >=1.\n"), "xlsxRead", "sheet"));
assert_checkerror("xlsxRead(test_file, ""sheet"", ""NonExistentSheet"")", msprintf(_("%s: Sheet not found in file or file is empty.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""sheet"", 999)", msprintf(_("%s: Sheet not found in file or file is empty.\n"), "xlsxRead"));
// Errors - Invalid range value
assert_checkerror("xlsxRead(test_file, ""range"", ""A:"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", "":A"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""1:"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", "":1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A:1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""1:A"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A1:1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A:A1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""1A:A1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A1:A"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A:A1"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""A:1A"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", ""1A:1A"")", msprintf(_("%s: Invalid range format. Use formats like ''A1:B2'', ''A:C'', ''1:3'', ''B5'', ''B'', or ''3''.\n"), "xlsxRead"));
assert_checkerror("xlsxRead(test_file, ""range"", 123)", msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", %t)", msprintf(_("%s: Wrong type for ""%s"" argument: Must be a string or a 2x2 matrix.\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1; 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1 2 3; 4 5 6])", msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [])", msprintf(_("%s: Wrong value for ""%s"" argument: Range matrix must be 2x2 [row1 col1; row2 col2].\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1.5 1; 2 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Must be integers.\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1 1; 2.7 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Must be integers.\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [0 1; 2 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Must be positive (>=1).\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1 0; 2 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Must be positive (>=1).\n"), "xlsxRead", "range"));
assert_checkerror("xlsxRead(test_file, ""range"", [1 1; -1 2])", msprintf(_("%s: Wrong value for ""%s"" argument: Must be positive (>=1).\n"), "xlsxRead", "range"));
// Errors - Invalid conversion value
assert_checkerror("xlsxRead(test_file, ""conversion"", 123)", msprintf(_("%s: Wrong type for ""%s"" argument: A string expected.\n"), "xlsxRead", "conversion"));
assert_checkerror("xlsxRead(test_file, ""conversion"", ""invalid"")", msprintf(_("%s: Wrong value for ""conversion"" argument: Must be in [""double"",""string"",""cell""].\n"), "xlsxRead", "conversion", sci2exp(["double", "string", "cell"])));
assert_checkerror("xlsxRead(test_file, ""conversion"", ""int"")", msprintf(_("%s: Wrong value for ""conversion"" argument: Must be in [""double"",""string"",""cell""].\n"), "xlsxRead", "conversion", sci2exp(["double", "string", "cell"])));


// Read with range extended beyond data
xlsxWrite([1, 2, 3], test_file, "writemode", "overwrite", "range", "A1:D10");
result = xlsxRead(test_file, "range", "A1:B1");
assert_checktrue(size(result) == [1, 2]);

// Cleanup
if isfile(test_file) then
    deletefile(test_file);
end

