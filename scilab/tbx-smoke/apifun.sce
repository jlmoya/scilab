// apifun smoke: apifun is a pure input-validation library (no numeric "answer" of its
// own), so exercise its flagship checker apifun_checkrange the same way its own unit test
// does: accept an in-range value, reject an out-of-range one.
// Source: tests/unit_tests/checkrange.tst (apifun's own regression test for this function).
ierr_ok  = execstr("apifun_checkrange(""smoke"", 0.5, ""x"", 1, 0, 1)", "errcatch");
ierr_bad = execstr("apifun_checkrange(""smoke"", 1.5, ""x"", 1, 0, 1)", "errcatch");
smoke_ok = (ierr_ok == 0) & (ierr_bad == 10000);
