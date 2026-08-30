// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Scilab GUI Designer
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

// Open the GUI designer, optionally on a file.
function guidesigner(fname)
    if ~exists("fname", "local") then
        fname = "";
    end
    if type(fname) <> 10 then
        error(gettext("guidesigner: the argument must be a file name."));
    end
    guidesigner_open(fname);
endfunction
