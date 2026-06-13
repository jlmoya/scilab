// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Jose Moya
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// terminal() - embedded terminal command. These checks run headless (non-STD),
// where terminal() must be defined and must refuse cleanly rather than try to
// open a GUI window.

// the macro library is loaded -> terminal is defined
assert_checktrue(isdef("terminal"));

// we are not in the GUI (STD) mode here
assert_checkequal(getscilabmode() == "STD", %f);

// wrong number of input arguments -> error
ierr = execstr("terminal(1)", "errcatch");
assert_checktrue(ierr <> 0);

// no GUI available -> terminal() refuses with a clear error
msg = _("terminal: the embedded terminal requires the Scilab GUI (STD mode).");
assert_checkerror("terminal()", msg);
