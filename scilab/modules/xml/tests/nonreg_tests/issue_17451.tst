// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->
//
// <-- Non-regression test for issue 17451 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17451
//
// <-- Short Description -->
// xmlXPath(...) (and the XMLSet datatype) .content access can lead to 0-sized datatypes
//

xml = xmlReadStr("<fmiModelDescription><CoSimulation hasEventMode=""true"" /></fmiModelDescription>")

// existing attribute
assert_checkequal(xmlXPath(xml, "//CoSimulation/@hasEventMode").content, "true");
// non-existing attribute
assert_checkequal(xmlXPath(xml, "//CoSimulation/@foo").content, []);
assert_checkfalse(xmlXPath(xml, "//CoSimulation/@foo").content == "true");
assert_checkequal(xmlXPath(xml, "//CoSimulation/@foo").name, []);
assert_checkfalse(xmlXPath(xml, "//CoSimulation/@foo").name == "true");


