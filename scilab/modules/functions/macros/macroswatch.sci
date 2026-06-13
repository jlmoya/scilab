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
    // Live hot-reload of macro libraries: watch a library's source directory and,
    // when a .sci changes (e.g. edited in the embedded terminal or by a build),
    // re-run genlib so the updated function is live in the interpreter.
    //
    // Syntax:
    //   macroswatch()                  // watch every loaded user library
    //   macroswatch(libraryName, dir)  // watch one library

    [lhs, rhs] = argn(0);

    if getscilabmode() == "NWNI" | getscilabmode() == "API" then
        error(gettext("macroswatch: live macro reload requires the Scilab GUI."));
    end

    jimport org.scilab.modules.action_binding.LibraryReloader

    if rhs == 0 then
        // Auto mode: watch every loaded user library (skip Scilab's own, under SCI).
        libs = librarieslist();
        sciroot = pathconvert(fullpath(SCI), %f, %f);
        n = 0;
        for i = 1:size(libs, "*")
            nm = libs(i);
            if execstr("%mw_p = string(" + nm + ")", "errcatch") == 0 then
                if size(%mw_p, "*") >= 1 then
                    d = pathconvert(fullpath(%mw_p(1)), %f, %f);
                    if part(d, 1:length(sciroot)) <> sciroot & isdir(%mw_p(1)) then
                        LibraryReloader.getInstance().watch(nm, %mw_p(1));
                        n = n + 1;
                    end
                end
            end
        end
        clear %mw_p;
        mprintf(gettext("macroswatch: watching %d user library(ies) for live reload.\n"), n);
        return;
    end

    if rhs <> 2 then
        error(msprintf(gettext("%s: Wrong number of input arguments: %d or %d expected.\n"), "macroswatch", 0, 2));
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

    LibraryReloader.getInstance().watch(name, dir);
endfunction
