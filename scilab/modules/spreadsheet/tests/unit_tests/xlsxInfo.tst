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
test_data = [1, 2, 3; 4, 5, 6];
test_file = TMPDIR + "/test_info.xlsx";


// Test 1: file info with one sheet and metadata
xlsxWrite(test_data, test_file, "writemode", "overwrite", "sheet", "Sheet1", "title", "Test Title", "subject", "Test Subject", "description", "Test Description");
info = xlsxInfo(test_file);
assert_checktrue(typeof(info) == "st");
assert_checktrue(isfield(info, "filename"));
assert_checktrue(isfield(info, "total_sheets"));
assert_checktrue(isfield(info, "sheet_names"));
assert_checktrue(isfield(info, "title"));
assert_checktrue(isfield(info, "subject"));
assert_checktrue(isfield(info, "description"));
assert_checkequal(info.total_sheets, 1);
assert_checkequal(info.sheet_names(1), "Sheet1");
assert_checkequal(info.title, "Test Title");
assert_checkequal(info.subject, "Test Subject");
assert_checkequal(info.description, "Test Description");


// Test 2: Info of a file with multiple sheets
xlsxSheet(test_file, "create", "Sheet2");
xlsxSheet(test_file, "create", "Sheet3");
info = xlsxInfo(test_file);
assert_checkequal(info.total_sheets, 3);
assert_checktrue(info.sheet_names == ["Sheet1", "Sheet2", "Sheet3"]);


// Test 3: Info of a specific sheet by index
info = xlsxInfo(test_file, 1);
assert_checktrue(typeof(info) == "st");
assert_checktrue(isfield(info, "sheet_name"));
assert_checktrue(isfield(info, "sheet_index"));
assert_checktrue(isfield(info, "max_row"));
assert_checktrue(isfield(info, "max_col"));
assert_checktrue(isfield(info, "cell_count"));
assert_checkequal(info.sheet_name, "Sheet1");
assert_checkequal(info.sheet_index, 1);
assert_checkequal(info.max_row, 2);
assert_checkequal(info.max_col, 3);
assert_checkequal(info.cell_count, 6);


// Test 4: Info of a specific sheet by name
info = xlsxInfo(test_file, "Sheet2");
assert_checktrue(typeof(info) == "st");
assert_checkequal(info.sheet_name, "Sheet2");
assert_checkequal(info.sheet_index, 2);
assert_checkequal(info.sheet_empty, %t);


// Test 5: Info of a sheet with data at different positions
xlsxWrite([10, 20], test_file, "sheet", "Sheet3", "range", "D5:E5");
info = xlsxInfo(test_file, "Sheet3");
assert_checkequal(info.max_row, 5);
assert_checkequal(info.max_col, 5);
assert_checkequal(info.cell_count, 2);


// Test 6: Info of the last sheet by index
info = xlsxInfo(test_file, 3);
assert_checkequal(info.sheet_name, "Sheet3");
assert_checkequal(info.sheet_index, 3);


// Test 7: Errors
assert_checkerror("xlsxInfo()", msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), "xlsxInfo", 1, 2));
// Errors - Invalid argument types
assert_checkerror("xlsxInfo(123)", msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxInfo", 1));
assert_checkerror("xlsxInfo(test_file, %t)", msprintf(_("%s: Wrong type for input argument #%d: A string or double expected.\n"), "xlsxInfo", 2));
// Errors - Nonexistent file
assert_checkerror("xlsxInfo(""nonexistent.xlsx"")", msprintf(_("%s: Wrong value for input argument #%d: The file ""%s"" does not exist.\n"), "xlsxInfo", 1, "nonexistent.xlsx"));
assert_checkerror("xlsxInfo(""nonexistent.xlsx"", 1)", msprintf(_("%s: Wrong value for input argument #%d: The file ""%s"" does not exist.\n"), "xlsxInfo", 1, "nonexistent.xlsx"));
assert_checkerror("xlsxInfo(""nonexistent.xlsx"", ""Sheet1"")", msprintf(_("%s: Wrong value for input argument #%d: The file ""%s"" does not exist.\n"), "xlsxInfo", 1, "nonexistent.xlsx"));
// Errors - Invalid sheet index
assert_checkerror("xlsxInfo(test_file, 0)", msprintf(_("%s: Wrong value for input argument #%d: Sheet index must be >= 1."), "xlsxInfo", 2));
assert_checkerror("xlsxInfo(test_file, -1)", msprintf(_("%s: Wrong value for input argument #%d: Sheet index must be >= 1."), "xlsxInfo", 2));
assert_checkerror("xlsxInfo(test_file, 100)", msprintf(_("%s: Sheet index %d out of range."), "xlsxInfo", 100));
assert_checkerror("xlsxInfo(test_file, 999378437845454546)", msprintf(_("%s: Wrong value for input argument #%d: Sheet index must be >= 1."), "xlsxInfo", 2));
// Errors - Invalid sheet name
assert_checkerror("xlsxInfo(test_file, ""NonExistentSheet"")", msprintf(_("%s: Sheet ""%s"" not found.\n"), "xlsxInfo", "NonExistentSheet"));
assert_checkerror("xlsxInfo(test_file, ""InvalidName123"")", msprintf(_("%s: Sheet ""%s"" not found.\n"), "xlsxInfo", "InvalidName123"));


// Test 8: Info of an empty file (no data)
empty_file = TMPDIR + "/test_empty.xlsx";
xlsxWrite([], empty_file, "writemode", "overwrite");
info = xlsxInfo(empty_file);
info = xlsxInfo(empty_file);
assert_checktrue(typeof(info) == "st");
assert_checkequal(info.title, "");
assert_checkequal(info.subject, "");
assert_checkequal(info.description, "");
assert_checkequal(info.total_sheets, 1);
info_sheet = xlsxInfo(empty_file, 1);
assert_checktrue(typeof(info) == "st");
assert_checkequal(info_sheet.sheet_empty, %t);
deletefile(empty_file);


// Test 9: Info after sheet modification
xlsxSheet(test_file, "rename", "Sheet1", "Renamed");
info = xlsxInfo(test_file);
assert_checkequal(info.total_sheets, 3);
assert_checktrue(info.sheet_names == ["Renamed", "Sheet2", "Sheet3"]);
info_renamed = xlsxInfo(test_file, "Renamed");
assert_checkequal(info_renamed.sheet_name, "Renamed");
assert_checkequal(info_renamed.sheet_index, 1);
assert_checkequal(info_renamed.max_row, 2);
assert_checkequal(info_renamed.max_col, 3);
assert_checkequal(info_renamed.cell_count, 6);



// Cleanup
if isfile(test_file) then
    deletefile(test_file);
end

// <-- TEST END -->
