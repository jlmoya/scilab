// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- ENGLISH IMPOSED -->
// English is imposed for tests using current Scilab language

// Single file
assert_checktrue(check_help(fullfile(SCI, "modules", "elementary_functions", "help", "en_US", "matrixoperations", "abs.xml")));

// Directory
assert_checktrue(check_help(fullfile(SCI, "modules", "elementary_functions", "help", "en_US", "matrixoperations")));

// Module with current language
assert_checktrue(check_help("elementary_functions"));

// Module with language forced
assert_checktrue(check_help("elementary_functions", "en_US"));

// Test with invalid file
absHelp = fullfile(SCI, "modules", "elementary_functions", "help", "en_US", "matrixoperations", "abs.xml");
txt = mgetl(absHelp);
txt(31) = strsubst(txt(31), "<para>", ""); 
txt(31) = strsubst(txt(31), "</para>", ""); 
mputl(txt, fullfile(TMPDIR, "abs_invalid.xml"));
assert_checkfalse(check_help(fullfile(TMPDIR, "abs_invalid.xml")));

// Error messages
assert_checkerror("check_help(""module_does_not_exist"")", msprintf(_("%s: Module ''%s'' does not exist.\n"), "check_help", "module_does_not_exist"));
