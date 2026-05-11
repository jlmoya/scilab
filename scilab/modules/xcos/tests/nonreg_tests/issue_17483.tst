// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Clement DAVID
//
// This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- XCOS TEST -->
// <-- NO CHECK REF -->
//
// <-- Non-regression test for bug 17483 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17483
//
// <-- Short Description -->
// Some labels were not preserved when reloading a block
//

loadXcosLibs();
scicos_log("TRACE");

function assert_checklabels(scs_m)
    // SUPER_f interface labels
    assert_checkequal(scs_m.objs(1).graphics.in_label, "in1_unique_name");
    assert_checkequal(scs_m.objs(1).graphics.out_label, "out1_unique_name");

    // SUPER_f inner labels
    assert_checkequal(scs_m.objs(1).model.rpar.objs(1).model.label, "in1_unique_name");
    assert_checkequal(grep(scs_m.objs(1).model.rpar.objs(1).graphics.id, "in1 long text"), 1);

    assert_checkequal(scs_m.objs(1).model.rpar.objs(2).model.label, "out1_unique_name");
    assert_checkequal(grep(scs_m.objs(1).model.rpar.objs(2).graphics.id, "out1 long text"), 1);
endfunction


scs_m_ssp1 = scicosDiagramToScilab(SCI + "/modules/xcos/tests/nonreg_tests/issue_17483.ssp");
scs_m = scs_m_ssp1;
assert_checklabels(scs_m);

// save a copy and recheck
scicosDiagramToScilab(TMPDIR + "/issue_17483.ssp", scs_m);
scs_m_ssp2 = scicosDiagramToScilab(TMPDIR + "/issue_17483.ssp");
scs_m = scs_m_ssp2;
assert_checklabels(scs_m);


// in zcos, the labels are preserved as well
scs_m_zcos = xcosDiagramToScilab(SCI + "/modules/xcos/tests/nonreg_tests/issue_17483.zcos");
scs_m = scs_m_zcos;
assert_checklabels(scs_m);

// save a copy and recheck
xcosDiagramToScilab(TMPDIR + "/issue_17483.zcos", scs_m_zcos);
scs_m_zcos2 = xcosDiagramToScilab(TMPDIR + "/issue_17483.zcos");
scs_m = scs_m_zcos2;
assert_checklabels(scs_m);
