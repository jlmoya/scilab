// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- TEST WITH GRAPHIC -->
// <-- NO CHECK REF -->

// <-- Non-regression test for bug 17506 -->
//
// <-- Bugzilla URL -->
// https://gitlab.com/scilab/scilab/-/issues/17506
//
// <-- Short Description -->
// Scatterplot is always black for marker=0

x = rand(50, 1)';
y = rand(50, 1)';
colr = int(rand(50, 1) * 8);
sz = int(50 * rand(50,1)' + 10);

scf();
h1 = scatter(x, y, sz, colr, marker=3); // already worked
assert_checkequal(size(h1.mark_foreground, "*"), 50); // Colored foreground
assert_checkequal(size(h1.mark_background, "*"), 1); // Transparent background

for m=[0 4]
    scf();
    h2 = scatter(x, y, sz, colr, marker=m); // fixed
    assert_checkequal(size(h2.mark_foreground, "*"), 50); // Colored foreground
    assert_checkequal(size(h2.mark_background, "*"), 50); // Colored background
    assert_checkequal(h2.mark_background, h2.mark_foreground); // Same background & foreground colors
end
