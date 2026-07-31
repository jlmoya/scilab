// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
//
// This file is hereby licensed under the terms of the GNU GPL v2.0.
// For more information, see the COPYING file which you should have received
// along with this program.

// Order toolboxes so every dependency loads before its dependent.
//
//   names    : column of toolbox names to order
//   depsList : list(), parallel to names; depsList(i) is a column of the names
//              toolbox i depends on (see tbx_deps)
//
//   order    : indices into names, dependency-first
//   missing  : Nx2 matrix of [dependent, missing_dependency] — a declared
//              dependency that is not in `names` (not installed, or not
//              autoload-enabled). Reported, never silently dropped: an
//              unsatisfied dependency is the exact failure this whole mechanism
//              exists to surface.
//   cycles   : column of names that could not be ordered because they sit in (or
//              behind) a dependency cycle.
//
// Kahn's algorithm, with the ready-set kept in the caller's original order so a
// dependency-free manifest orders EXACTLY as before — this change must not
// reshuffle 50 working toolboxes to fix one.
function [order, missing, cycles] = tbx_toposort(names, depsList)
    order = []; missing = []; cycles = [];
    n = size(names, "*");
    if n == 0 then return; end

    // adjacency: edge dep -> node, plus indegree per node
    indeg = zeros(n, 1);
    succ = list();
    for i = 1:n
        succ(i) = [];
    end

    for i = 1:n
        d = depsList(i);
        for k = 1:size(d, "*")
            j = find(names == d(k));
            if isempty(j) then
                missing = [missing ; names(i), d(k)];
            else
                j = j(1);
                if j <> i then                      // ignore self-dependency
                    succ(j) = [succ(j) ; i];
                    indeg(i) = indeg(i) + 1;
                end
            end
        end
    end

    done = zeros(n, 1);
    // repeatedly take every currently-ready node, lowest original index first
    while %t
        progressed = %f;
        for i = 1:n
            if done(i) == 0 & indeg(i) == 0 then
                order = [order ; i];
                done(i) = 1;
                progressed = %t;
                s = succ(i);
                for k = 1:size(s, "*")
                    indeg(s(k)) = indeg(s(k)) - 1;
                end
            end
        end
        if ~progressed then break; end
    end

    for i = 1:n
        if done(i) == 0 then cycles = [cycles ; names(i)]; end
    end
endfunction
