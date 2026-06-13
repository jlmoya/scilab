// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Jose Moya
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// macroswatch() - live macro hot-reload. These checks run headless (NWNI),
// where macroswatch must be defined and must refuse cleanly (it requires the GUI).

// the macro is defined
assert_checktrue(isdef("macroswatch"));

// no GUI here -> macroswatch refuses with a clear error (both forms)
msg = _("macroswatch: live macro reload requires the Scilab GUI.");
assert_checkerror("macroswatch()", msg);
assert_checkerror("macroswatch(""mylib"", TMPDIR)", msg);
