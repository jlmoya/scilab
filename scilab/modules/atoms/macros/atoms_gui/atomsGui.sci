// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2009 - DIGITEO - Vincent COUVERT
// Copyright (C) 2010 - DIGITEO - Pierre MARECHAL
// Copyright (C) 2012 - DIGITEO - Allan CORNET
// Copyright (C) 2014 - Scilab Enterprises - Antoine ELIAS
// Copyright (C) 2026 - Dassault Systemes S.E. - Antoine ELIAS
//
// Copyright (C) 2012 - 2016 - Scilab Enterprises
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function atomsGui()

    if ~isempty(get("atomsFigure")) then
        set("atomsFigure", "visible", "on");
        return;
    end

    if ~exists("atomsinternalslib") then
        load("SCI/modules/atoms/macros/atoms_internals/lib");
    end

    // Test connection
    allModules = [];
    errStatus = execstr("allModules = atomsDESCRIPTIONget();", "errcatch");

    if errStatus <> 0 | size(allModules, "*") == 0 then
        if size(atomsRepositoryList(), "*") > 0 then
            messagebox(gettext("No ATOMS module is available. Please, check your Internet connection or make sure that your OS is compatible with ATOMS."), gettext("ATOMS error"), "error");
        else
            messagebox(gettext("No ATOMS module is available: your repository list is empty."), gettext("ATOMS error"), "error");
        end
        return
    end

    // Figure
    f = figure( ...
        "figure_name", _("ATOMS - Package Manager"), ...
        "dockable", "off", ...
        "infobar_visible", "off", ...
        "toolbar_visible", "off", ...
        "toolbar", "none", ...
        "menubar", "none", ...
        "menubar_visible", "off", ...
        "default_axes", "off", ...
        "tag", "atomsFigure", ...
        "visible", "off", ...
        "icon", "atoms-green", ...
        "position", [0 0 1100 800], ...
        "layout", "border");

    fr = uicontrol(f, ...
        "style", "frame", ...
        "backgroundcolor", [1 1 1], ...
        "layout", "border");

    uicontrol(fr, ...
        "style", "browser", ...
        "debug", "on", ...
        "string", SCI + "/modules/atoms/gui/atoms_browser.html", ...
        "callback", "cbAtomsGui", ...
        "tag", "atoms_browser");

    // Store module data for the callback
    set("atoms_browser", "userdata", allModules);

    f.closerequestfcn = "delete(get(""atomsFigure""));";
    f.visible = "on";
endfunction
