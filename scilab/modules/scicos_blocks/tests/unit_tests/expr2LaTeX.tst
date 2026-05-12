// ============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Clément DAVID
//
//  This file is distributed under the same license as the Scilab package.
// ============================================================================
//
// <-- ENGLISH IMPOSED -->
// <-- XCOS TEST -->
// <-- CLI SHELL MODE -->
//
// <-- Short Description -->
// Testing the expr2LaTeX function used to format the expression to be displayed on some blocks icons
// The LaTeX formatting is rendered as an image



exprs = ["1+s"
"1+Ks*s"
"1-2.5*s"
"1+Ts*s-A*s^2"
"s^ab +s^ 10+s^-12.4-s^20"
"(u1<0)*sin(u2)^2"
"y1=2*(u1<3)"
"u1^0.5"
"u1^1.5 + u2^%eps + (u2-u1)^(1.5-%eps)"];

// echo to dia.ref for manual checking between versions
for expr = exprs'
    expr
    expr2LaTeX(expr)
end

if getscilabmode() == "STD" then
    // render for checking the generated LaTeX code
    for i=1:size(exprs,'*')
        xstring(0, 1-(i-1)*0.1, "$"+expr2LaTeX(exprs(i))+"$")
    end

    xs2svg(gcf(), "TMPDIR/expr2LaTeX_test.svg");
end
