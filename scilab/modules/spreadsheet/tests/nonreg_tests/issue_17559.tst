// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
//
// <-- Non-regression test for issue 17559 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17559
//
// <-- Short Description -->
// table/timeseries now accepts a column vector for VariableNames property. 
// This vector is automatically converted to a row vector.

v = ["varA", "varB", "varC"];
tt = table(ones(3, 3), "VariableNames", v);
assert_checktrue(tt.Properties.VariableNames == v);

ttt = table(ones(3, 3), "VariableNames", v');
assert_checktrue(ttt.Properties.VariableNames == v)

tt
ttt

tt(:, 2)
ttt(:, 2)
tt("varB")
tt.varB

ttt("varB")
ttt.varB

tt.Properties.VariableNames = ["A"; "B"; "C"]
assert_checktrue(tt.Properties.VariableNames == ["A" "B" "C"]);
ttt.Properties.VariableNames = ["1"; "2"; "3"]
assert_checktrue(ttt.Properties.VariableNames == ["1" "2" "3"]);