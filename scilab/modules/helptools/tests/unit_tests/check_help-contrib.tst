// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->

// Check toolbox skeletons (all languages)
assert_checktrue(check_help(fullfile(SCI, "contrib", "toolbox_skeleton", "help")));
assert_checktrue(check_help(fullfile(SCI, "contrib", "xcos_toolbox_skeleton", "help")));
