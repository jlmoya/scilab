// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2008 - INRIA - Sylvestre LEDRU
// Copyright (C) 2010 - DIGITEO - Sylvestre LEDRU
//
// Copyright (C) 2012 - 2016 - Scilab Enterprises
//
// This file is hereby licensed under the terms of the GNU GPL v2.0,
// pursuant to article 5.3.4 of the CeCILL v.2.1.
// This file was originally licensed under the terms of the CeCILL v2.1,
// and continues to be available under such terms.
// For more information, see the COPYING file which you should have received
// along with this program.

//=============================================================================
function cmd = gencompilationflags_unix(ldflags, cflags, fflags, cc, flagsType)

    // This function is restricted to Linux/Unix user only
    if getos() == "Windows" then
        error(msprintf(gettext("%s: Feature not available under Microsoft Windows.\n"),"gencompilationflags_unix"));
        return;
    end

    [lhs,rhs] = argn(0);
    if rhs <> 5 then
        error(msprintf(gettext("%s: Wrong number of input arguments: %d expected.\n"),"gencompilationflags_unix",5));
        return
    end
    if isempty(ldflags)
        ldflags = ""
    end
    if isempty(cflags)
        cflags = ""
    end
    if isempty(fflags)
        fflags = ""
    end
    if isempty(cc)
        cc = ""
    end
    if isempty(flagsType)
        flagsType = ""
    end

    cmd = "";

    ScilabTreeFound=%f;
    tbxFlag = " -D__SCILAB_TOOLBOX__ ";
    envFlag = "";

    if getenv("__USE_DEPRECATED_STACK_FUNCTIONS__", "NO") == "YES" then
        envFlag = " -D__USE_DEPRECATED_STACK_FUNCTIONS__ ";
    end

    val = getenv("DEBUG_SCILAB_DYNAMIC_LINK", "");
    if (val == "" & isDebug()) | val == "YES" then
        envFlag = envFlag + " -g ";
    end

    // CFLAGS
    //
    // ilib_build gives callers ONE flags channel for two languages, and the line
    // below used to hand it verbatim to the C compiler as well. A C++ toolbox has
    // no way to pass -std=c++17 except through this channel, so it landed in
    // CFLAGS, and the autotools skeleton's AC_PROG_CC then compiled its conftest.c
    // as C with a C++ standard selected:
    //
    //     error: invalid argument '-std=c++17' not allowed with 'C'
    //
    // which autoconf reports as the thoroughly misleading "C compiler cannot
    // create executables". It is deterministic, not the intermittent flake it has
    // long been recorded as, and it broke every C++ toolbox in the tree (cgal,
    // parquet, scicv, sciTorch). A C++ standard selector is never valid in CFLAGS,
    // so strip exactly those and leave everything else untouched; CXXFLAGS below
    // still receives the full set.
    ccflags = cflags;
    ccflags = strsubst(ccflags, "/(^|\s)-std=(c|gnu)\+\+[0-9a-z]+/", " ", "r");
    if cflags <> "" then
        cmd = cmd + " CFLAGS="""+tbxFlag+envFlag+ccflags+""""
    end

    // CXXFLAGS ... use the same as C
    if cflags <> "" then
        cmd = cmd +" CXXFLAGS="""+tbxFlag+envFlag+cflags+""""
    end

    // LDFLAGS
    // Do not set the LDFLAGS See bug #4787
    if ldflags <> "" & flagsType <> "configure" then
        cmd = cmd +" LDFLAGS="""+ldflags+""""
    end

    // FFLAGS
    if fflags <> "" then
        cmd = cmd +" FFLAGS="""+fflags+""""
    end

    // CC
    if cc <> "" then
        cmd = cmd +" CC="""+cc+""""
    end

endfunction
//=============================================================================
