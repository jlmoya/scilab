// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Jose Moya
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function macroswatch(name, dir)
    // Live hot-reload of a macro library: watch its source directory and, when a
    // .sci changes (e.g. edited in the embedded terminal or by a build), re-run
    // genlib so the updated function is live in the interpreter.
    //
    // Syntax: macroswatch(libraryName, directory)

    [lhs, rhs] = argn(0);
    if rhs <> 2 then
        error(msprintf(gettext("%s: Wrong number of input arguments: %d expected.\n"), "macroswatch", 2));
    end
    if type(name) <> 10 | size(name, "*") <> 1 then
        error(msprintf(gettext("%s: Wrong type for input argument #%d: A string expected.\n"), "macroswatch", 1));
    end
    if type(dir) <> 10 | size(dir, "*") <> 1 then
        error(msprintf(gettext("%s: Wrong type for input argument #%d: A string expected.\n"), "macroswatch", 2));
    end
    if ~isdir(dir) then
        error(msprintf(gettext("%s: The directory ""%s"" does not exist.\n"), "macroswatch", dir));
    end
    if getscilabmode() == "NWNI" | getscilabmode() == "API" then
        error(gettext("macroswatch: live macro reload requires the Scilab GUI."));
    end

    jimport org.scilab.modules.action_binding.LibraryReloader
    LibraryReloader.getInstance().watch(name, dir);
endfunction
