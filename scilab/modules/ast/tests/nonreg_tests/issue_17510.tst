// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2025 - Dassault Systèmes S.E. - Antoine ELIAS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->

// <-- Non-regression test for issue 17510 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17510
//
// <-- Short Description -->
// classdef properties not shown correctly when an extract method is defined
// -------------------------------------------------------------

classdef setn
    properties
        a = 0
        b = 0
    end
    methods
        function setn(x)
            this.a = x
            this.b = x * 2;
        end
        function z = extract(y)
            z = this.a + this.b * y
        end
    end
end

q = setn(2)

// string()/disp must show real property values, not the result of user extract method
assert_checkequal(string(q), ["a = 2"; "b = 4"]);

// q.a and q("a") are strictly equivalent: direct property access, never delegated to user extract
assert_checkequal(q.a, 2);
assert_checkequal(q.b, 4);
assert_checkequal(q("a"), 2);
assert_checkequal(q("b"), 4);

// user extract is still called for non-string indexing
assert_checkequal(q(4), 18); // 2 + (2 * 2) * 4
