// =============================================================================
// Scilab ( http://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Antoine ELIAS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- Non-regression test for issue 17542 -->
//
// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->
//
// <-- Gitlab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17542
//
// <-- Short Description -->
// Different behavior of classdef methods with heterogeneous types: binary
// operators must dispatch the classdef method whichever side carries the
// object. plus already did; horzcat / vertcat were asymmetric.

classdef dual
    properties
        x
        d
    end
    methods
        function dual(_x, _d)
            this.x = _x;
            this.d = _d;
        end
        function c = plus(a, b)
            if type(a) == 1
                a = dual(a, zeros(a));
            elseif type(b) == 1
                b = dual(b, zeros(b));
            end
            c = dual(a.x + b.x, a.d + b.d);
        end
        function c = horzcat(a, b)
            if type(a) == 1
                a = dual(a, zeros(a));
            elseif type(b) == 1
                b = dual(b, zeros(b));
            end
            c = dual([a.x, b.x], [a.d, b.d]);
        end
        function c = vertcat(a, b)
            if type(a) == 1
                a = dual(a, zeros(a));
            elseif type(b) == 1
                b = dual(b, zeros(b));
            end
            c = dual([a.x; b.x], [a.d; b.d]);
        end
    end
end

// plus is symmetric (already worked before the fix).
r = 1 + dual(1, 1);
assert_checkequal(r.x, 2);
assert_checkequal(r.d, 1);

r = dual(1, 1) + 1;
assert_checkequal(r.x, 2);
assert_checkequal(r.d, 1);

// horzcat: must dispatch in both directions.
r = [dual(1, 1), 1]; // used to fail with %dual_c_s
assert_checkequal(r.x, [1 1]);
assert_checkequal(r.d, [1 0]);

r = [1, dual(1, 1)];
assert_checkequal(r.x, [1 1]);
assert_checkequal(r.d, [0 1]);

// vertcat: same asymmetry, fixed alongside horzcat.
r = [dual(1, 1); 1]; // used to fail with %dual_f_s
assert_checkequal(r.x, [1; 1]);
assert_checkequal(r.d, [1; 0]);

r = [1; dual(1, 1)];
assert_checkequal(r.x, [1; 1]);
assert_checkequal(r.d, [0; 1]);
