// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Adeline CARNIS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
//
// <-- Non-regression test for issue 17558 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17558
//
// <-- Short Description -->
// table summary optionnal selection feature returned incoherent result.

rand("seed", 0);
tb = table(rand(10,5))
summary(tb(:,4:5))
summary(tb,"default",["Var4","Var5"])
summary(tb)
