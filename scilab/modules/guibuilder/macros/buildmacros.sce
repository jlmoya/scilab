// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Scilab GUI Designer
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

// The library is called guidesignerlib, NOT guibuilderlib, and the name is
// load-bearing. genlib() defines its first argument as a variable in the base
// workspace, and the ATOMS guibuilder toolbox this module will eventually
// replace opens its own etc/guibuilder.start with
//
//     if isdef("guibuilderlib") then
//         warning("Toolbox skeleton library is already loaded");
//         return;
//     end
//
// Module .start files run before ~/.Scilab/<version>/.scilab, so a library
// named guibuilderlib here would already be defined by the time the toolbox
// loads: the toolbox would refuse to load and the `guibuilder` command would
// silently disappear. Design spec section 12 promises the toolbox keeps
// working until phase 2, so this module's library carries the new name. Only
// the LIBRARY variable changes -- the module directory, the Maven artifactId
// and the load path below all stay `guibuilder`.
genlib("guidesignerlib","SCI/modules/guibuilder/macros",%f,%t);
