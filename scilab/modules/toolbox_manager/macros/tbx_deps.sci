// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
//
// This file is hereby licensed under the terms of the GNU GPL v2.0.
// For more information, see the COPYING file which you should have received
// along with this program.

// Return the declared dependencies of one toolbox, as a column of names.
//
// TWO SOURCES, unioned:
//
//   1. DESCRIPTION's `Depends:` field — the ATOMS convention, already shipped by
//      19 of the installed toolboxes, so most dependency data needs no new
//      metadata at all. Entries are comma-separated and may carry an ATOMS
//      version constraint: "~ apifun any", "PIMS", "helptbx >= 1.0". Only the
//      NAME is used; this resolver orders loading, it does not do version
//      solving, and inventing a version algebra nothing declares properly would
//      be worse than useless.
//
//   2. The manifest's `deps` column — for toolboxes that ship no DESCRIPTION at
//      all (guimaker and regtools are both in that position), and as an override
//      when a DESCRIPTION is wrong or stale.
//
// Unknown/oddly-formatted entries are skipped rather than guessed at.
function d = tbx_deps(path, declared)
    d = [];

    // ---- source 2: manifest column (authoritative, listed first) ----
    if argn(2) >= 2 then
        d = [d ; tbx_deps_split(declared)];
    end

    // ---- source 1: DESCRIPTION Depends: ----
    f = fullfile(path, "DESCRIPTION");
    if isfile(f) then
        L = mgetl(f);
        for i = 1:size(L, "*")
            s = stripblanks(L(i));
            if length(s) < 8 then continue; end
            if convstr(part(s, 1:8), "l") <> "depends:" then continue; end
            d = [d ; tbx_deps_split(part(s, 9:length(s)))];
        end
    end

    // de-duplicate, preserving first-seen order
    if ~isempty(d) then
        keep = [];
        for i = 1:size(d, "*")
            if isempty(find(keep == d(i))) then keep = [keep ; d(i)]; end
        end
        d = keep;
    end
endfunction
