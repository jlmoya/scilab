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

test_file = TMPDIR + "/test_sheet.xlsx";
test_data = [1, 2, 3; 4, 5, 6];

// create a new sheet
xlsxWrite(test_data, test_file, "writemode", "overwrite");
xlsxSheet(test_file, "create", "NewSheet");
result = xlsxInfo(test_file);
assert_checktrue(result.sheet_names == ["Sheet1" , "NewSheet"]);
assert_checkequal(result.sheet_names(2), "NewSheet");

// create a sheet with a specific index
xlsxSheet(test_file, "create", "Sheet2");
xlsxSheet(test_file, "create", "Sheet3");
xlsxSheet(test_file, "create", "InsertedSheet", 2);
result = xlsxInfo(test_file);
assert_checktrue(result.sheet_names == ["Sheet1","InsertedSheet","NewSheet","Sheet2","Sheet3"]);
assert_checkequal(result.sheet_names(2), "InsertedSheet");

// create the first sheet of a new file
new_file = TMPDIR + "/new_test_sheet.xlsx";
xlsxSheet(new_file, "create", "FirstSheet");
assert_checktrue(isfile(new_file));
result = xlsxInfo(new_file);
assert_checkequal(result.sheet_names(1), "FirstSheet");
deletefile(new_file);

// delete a sheet by name
xlsxSheet(test_file, "delete", "Sheet3");
result = xlsxInfo(test_file);
assert_checktrue(result.sheet_names == ["Sheet1", "InsertedSheet", "NewSheet", "Sheet2"]);

// delete a sheet by index
xlsxSheet(test_file, "delete", 2);
result = xlsxInfo(test_file);
assert_checkequal(result.sheet_names(1), "Sheet1");
assert_checkequal(result.sheet_names(2), "NewSheet");

// rename a sheet by name
xlsxSheet(test_file, "rename", "Sheet1", "NewName");
result = xlsxInfo(test_file);
assert_checkequal(result.sheet_names(1), "NewName");

// rename a sheet by index
xlsxSheet(test_file, "rename", 1, "RenamedSheet");
result = xlsxInfo(test_file);
assert_checkequal(result.sheet_names(1), "RenamedSheet");

// Info on all sheets
xlsxSheet(test_file, "create", "Sheet29");
result = xlsxSheet(test_file, "info");
assert_checktrue(isfield(result, "sheet_names"));
assert_checktrue(result.total_sheets == 4);

// Errors
assert_checkerror("xlsxSheet()", msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), "xlsxSheet", 2, 4));
assert_checkerror("xlsxSheet(test_file)", msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), "xlsxSheet", 2, 4));
// Invalid argument types
assert_checkerror("xlsxSheet(123, ""create"", ""Test"")", msprintf(_("%s: Wrong type for input argument #%d: A valid file expected.\n"), "xlsxSheet", 1));
assert_checkerror("xlsxSheet(test_file, 123, ""Test"")", msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxSheet", 2));
assert_checkerror("xlsxSheet(test_file, ""create"", 123)", msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxSheet", 3));
// Invalid operation
assert_checkerror("xlsxSheet(test_file, ""invalid_op"", ""Test"")", msprintf(_("%s: Unknown operation ''%s''. Valid operations are %s.\n"), "xlsxSheet", "invalid_op", sci2exp(["create", "deleter", "rename", "info"])));
// CREATE operation
assert_checkerror("xlsxSheet(test_file, ""create"")", msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), "xlsxSheet", 3, 4));
assert_checkerror("xlsxSheet(test_file, ""create"", ""Sheet2"")", msprintf(_("%s: Sheet name already exists.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""create"", ""NewSheet"", 0)", msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), "xlsxSheet", 4));
assert_checkerror("xlsxSheet(test_file, ""create"", ""NewSheet"", -5)", msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), "xlsxSheet", 4));
// DELETE operation
assert_checkerror("xlsxSheet(test_file, ""delete"")", msprintf(_("%s: Wrong number of input arguments: %d expected.\n"), "xlsxSheet", 3));
assert_checkerror("xlsxSheet(test_file, ""delete"", ""NonExistentSheet"")", msprintf(_("%s: Sheet not found.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""delete"", 999)", msprintf(_("%s: Invalid sheet index.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""delete"", 0)", msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), "xlsxSheet", 3));
// RENAME operation
assert_checkerror("xlsxSheet(test_file, ""rename"")", msprintf(_("%s: Wrong number of input arguments: %d expected.\n"), "xlsxSheet", 4));
assert_checkerror("xlsxSheet(test_file, ""rename"", ""OldName"")", msprintf(_("%s: Wrong number of input arguments: %d expected.\n"), "xlsxSheet", 4));
assert_checkerror("xlsxSheet(test_file, ""rename"", ""NonExistent"", ""NewName"")", msprintf(_("%s: Sheet not found.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""rename"", 999, ""NewName"")", msprintf(_("%s: Invalid sheet index.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""rename"", 0, ""NewName"")", msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), "xlsxSheet", 3));
assert_checkerror("xlsxSheet(test_file, ""rename"", ""Sheet1"", ""Sheet2"")", msprintf(_("%s: Sheet not found.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""rename"", 1, ""Sheet2"")", msprintf(_("%s: New sheet name already exists.\n"), "xlsxSheet"));
assert_checkerror("xlsxSheet(test_file, ""rename"", ""Sheet1"", 123)", msprintf(_("%s: Wrong type for input argument #%d: A string expected.\n"), "xlsxSheet", 4));
// INFO operation
assert_checkerror("xlsxSheet(test_file, ""info"", ""test"", ""extra"")", msprintf(_("%s: Wrong number of input arguments: %d to %d expected.\n"), "xlsxSheet", 2, 3));
assert_checkerror("xlsxSheet(test_file, ""info"", ""NonExistentSheet"")", msprintf(_("%s: Sheet ""%s"" not found.\n"), "xlsxInfo", "NonExistentSheet"));
assert_checkerror("xlsxSheet(test_file, ""info"", 999)", msprintf(_("%s: Sheet index %d out of range.\n"), "xlsxInfo", 999));
assert_checkerror("xlsxSheet(test_file, ""info"", 0)", msprintf(_("%s: Wrong value for input argument #%d: Must be an integer >= 1.\n"), "xlsxSheet", 3));
// Nonexistent file for operations other than CREATE
assert_checkerror("xlsxSheet(""nonexistent.xlsx"", ""delete"", ""Sheet1"")", msprintf(_("%s: Cannot open file ''%s''.\n"), "xlsxSheet", "nonexistent.xlsx"));
assert_checkerror("xlsxSheet(""nonexistent.xlsx"", ""rename"", ""Sheet1"", ""NewName"")", msprintf(_("%s: Cannot open file ''%s''.\n"), "xlsxSheet", "nonexistent.xlsx"));
assert_checkerror("xlsxSheet(""nonexistent.xlsx"", ""info"")", msprintf(_("%s: Wrong value for input argument #%d: The file ""%s"" does not exist.\n"), "xlsxInfo", 1, "nonexistent.xlsx"));

if isfile(test_file) then
    deletefile(test_file);
end
