// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// <-- Non-regression test for issue 16088 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/16088
//
// <-- Short Description -->
// Error executing the function spec
// Actually, the issue occurs when calling spec with complex matrices having imaginary part equal to 0.
// This case can lead to a memory overflow because of an issue in spec gateway.

// Code similar to bug_3652.tst but run 100 times

for i=1:100
    Areal=[1 0 0;...
    0 1 0;...
    0 0 1];
    A = complex(Areal,0);
    Ereal=[1 1e-14 0;...
    1e-14 1 0;...
    0 0 1];
    E = complex(Ereal,0);
    Scomputed = spec(A,E);
    Sexpected = [1-1e-14;1+1e-14;1];
    assert_checkfalse(norm(Scomputed - Sexpected) > 1000 * %eps);
end

