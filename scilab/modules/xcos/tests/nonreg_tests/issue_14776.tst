// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Clément DAVID
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- XCOS TEST -->
// <-- ENGLISH IMPOSED -->
//
// <-- Non-regression test for issue 14776 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/14776
//
// <-- Short Description -->
// On AFFICH_m block, the port size set to automatic can cause error during simulation
//

loadXcosLibs();

// Load the test diagram
importXcosDiagram("SCI/modules/xcos/tests/nonreg_tests/issue_14776.zcos");

// Try to compile the diagram - this should not crash and produce a meaningful error message about the bad connection
try
    scicos_simulate(scs_m, list())
catch
    disp("Test failed with exception: " + lasterror());
end
