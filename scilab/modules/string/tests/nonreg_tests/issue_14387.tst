// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- Non-regression test for issue 14387 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/14387

// string(cell_array) returned wrong size
// =============================================================================

c = {%pi %i %t ; %z "abcd" list(2,%f)}
t = string(c)
assert_checktrue(typeof(t) == "string");
assert_checktrue(size(t) == [2 3]);
