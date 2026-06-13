// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Jose Moya
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

function terminal()
    // Open an embedded terminal (a JediTerm VT emulator on a real PTY) in a
    // dockable Scilab tab. Run any shell command, in particular:
    //     claude --dangerously-skip-permissions -c
    //
    // Syntax: terminal()

    [lhs, rhs] = argn(0);
    if rhs > 0 then
        error(msprintf(gettext("%s: Wrong number of input arguments: %d expected.\n"), "terminal", 0));
    end

    if ~with_module("gui") | getscilabmode() <> "STD" then
        error(gettext("terminal: the embedded terminal requires the Scilab GUI (STD mode)."));
    end

    jimport org.scilab.modules.terminal.ScilabTerminal
    ScilabTerminal.openTerminal();
endfunction
