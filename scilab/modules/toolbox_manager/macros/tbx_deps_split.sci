// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
//
// This file is hereby licensed under the terms of the GNU GPL v2.0.
// For more information, see the COPYING file which you should have received
// along with this program.

// Its own file, not a helper inside tbx_deps.sci: genlib maps one macro per
// file, so a second function in the same file is not a library member and is
// unreachable from anywhere else (including tests).
// Split a comma-separated dependency list into bare names, dropping ATOMS
// version syntax ("~ name any", "name >= 1.2") and empty entries.
function names = tbx_deps_split(s)
    names = [];
    if type(s) <> 10 then return; end
    s = stripblanks(s);
    if s == "" then return; end
    entries = strsplit(s, ",")';
    for k = 1:size(entries, "*")
        e = stripblanks(entries(k));
        if e == "" then continue; end
        // first whitespace-delimited token that is not the "~" constraint marker
        toks = strsplit(strsubst(e, ascii(9), " "), " ")';
        for t = 1:size(toks, "*")
            tk = stripblanks(toks(t));
            if tk == "" | tk == "~" then continue; end
            names = [names ; tk];
            break;
        end
    end
endfunction
