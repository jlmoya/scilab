// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Jose Moya
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function resetdesktop()
    // Re-dock all tool windows (Variable Browser, File Browser, Command History,
    // SciNotes, Terminal, ...) into the main Scilab window and show a terminal -
    // a quick way to recover from a scattered desktop. Graphics figures are left
    // as they are.
    //
    // Syntax: resetdesktop()

    if getscilabmode() == "NWNI" | getscilabmode() == "API" then
        error(gettext("resetdesktop: requires the Scilab GUI."));
    end

    jimport org.scilab.modules.terminal.ScilabTerminal
    ScilabTerminal.resetDesktop();
endfunction
