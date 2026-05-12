// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
// <-- ENGLISH IMPOSED -->
//

// Single file
assert_checktrue(check_help("SCI/modules/elementary_functions/help/en_US/matrixoperations/abs.xml"));

// Directory
assert_checktrue(check_help("SCI/modules/elementary_functions/help/en_US/matrixoperations"));

// Module with current language
assert_checktrue(check_help("elementary_functions"));

// Module with language forced
assert_checktrue(check_help("elementary_functions", "en_US"));

// Test with invalid file
absHelp = "SCI/modules/elementary_functions/help/en_US/matrixoperations/abs.xml";
txt = mgetl(absHelp);
txt(31) = strsubst(txt(31), "<para>", ""); 
txt(31) = strsubst(txt(31), "</para>", ""); 
mputl(txt, "TMPDIR/abs_invalid.xml");
assert_checkfalse(check_help("TMPDIR/abs_invalid.xml"));

// Error messages
assert_checkerror("check_help(""module_does_not_exist"")", msprintf(_("%s: Module ''%s'' does not exist.\n"), "check_help", "module_does_not_exist"));

// Generate an xUnit test file
check_help("SCI/modules/elementary_functions/help/en_US/matrixoperations/abs.xml", "en_US", "TMPDIR/check_help_xunit1.xml");
check_help("elementary_functions", "en_US", "TMPDIR/check_help_xunit2.xml");
