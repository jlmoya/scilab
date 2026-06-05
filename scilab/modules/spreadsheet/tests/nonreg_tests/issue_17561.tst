// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
//
// <-- Non-regression test for issue 17561 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17561
//
// <-- Short Description -->
// In summary(), sum of values in table did not skip NaN.

t = table([%nan; 1; 2; 3], [datetime(2025, 12, [10; 11; 12]); NaT()], hours([1; %nan; 2; 3]));
summary(t, "allstats")
summary(t, "sum")
