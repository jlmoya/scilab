// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Clément DAVID
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- NO CHECK REF -->
//
// This test is used to check that the Scilab can be properly restarted. It is
// important to note that the JVM is not stopped when Scilab is terminated, so
// it can be restarted without any issue.

exec("SCI/modules/call_scilab/tests/unit_tests/compileHelpers.sce");

[status, stdout, stderr] = run_executable(compile_executable("SCI/modules/call_scilab/examples/basicExamples/restart_with_jvm.c"))
assert_checkequal(stderr, "");
assert_checkequal(status, 0);
