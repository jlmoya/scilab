// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Antoine ELIAS
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================
//
// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->
//
// <-- Non-regression test for bug 17550 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/work_items/17550
//
// <-- Short Description -->
// tbx_build_src was calling unique(files) which sorted the source list
// alphabetically. For Fortran 90 modules with dependencies, the order
// of compilation matters: a module that USEs another must be compiled
// AFTER it. With names chosen so that the dependency order is the
// opposite of the alphabetical order, the build fails without the fix.

if haveacompiler() then
    if getos() == "Windows" & findmsifortcompiler() == "unknown" then
        // No Fortran 90 compiler available on Windows (f2c cannot build f90)
    else
        ilib_verbose(0);

        src_path = TMPDIR + "/bug_17550";
        mkdir(src_path);

        // Provider module: alphabetically LAST (z_mod.f90)
        provider = ["module mod_z"
                    "  implicit none"
                    "contains"
                    "  subroutine z_inc(x, y)"
                    "    double precision, intent(in)  :: x"
                    "    double precision, intent(out) :: y"
                    "    y = x + 1.0d0"
                    "  end subroutine z_inc"
                    "end module mod_z"];
        mputl(provider, src_path + "/z_mod.f90");

        // Consumer: alphabetically FIRST (a_use.f90), depends on mod_z
        consumer = ["subroutine bug17550(x, y)"
                    "  use mod_z"
                    "  implicit none"
                    "  double precision, intent(in)  :: x"
                    "  double precision, intent(out) :: y"
                    "  call z_inc(x, y)"
                    "end subroutine bug17550"];
        mputl(consumer, src_path + "/a_use.f90");

        // Files passed in DEPENDENCY order (provider first).
        // Before the fix, unique() would re-sort them to
        // ["a_use.f90", "z_mod.f90"] and the consumer would be
        // compiled before its module -> build failure.
        tbx_build_src("bug17550", ["z_mod.f90", "a_use.f90"], "f", src_path);

        exec(src_path + "/loader.sce");
        y = call("bug17550", 1.0, 1, "d", "out", [1,1], 2, "d");
        assert_checkequal(y, 2.0);
    end
end
