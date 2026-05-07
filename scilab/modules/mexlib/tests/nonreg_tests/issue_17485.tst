// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2026 - Dassault Systèmes S.E. - Vincent COUVERT
//
//  This file is distributed under the same license as the Scilab package.
//
// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->
//
// <-- Non-regression test for issue 17485 -->
//
// <-- GitLab URL -->
// https://gitlab.com/scilab/scilab/-/issues/17485
//
// <-- Short Description -->
// compiling against mex.h c23 error: ‘bool’ cannot be defined via ‘typedef’

cd(TMPDIR);
ilib_verbose(0);
mputl([ "#include <mex.h>"
""
"void mexFunction(int nlhs, mxArray *plhs[], int nrhs, const mxArray *prhs[])"
"{"
"    if(nrhs < 2)"
"        mexErrMsgTxt(""Two input arguments required."");"
"    if (nlhs > 1)"
"        mexErrMsgTxt(""Too many output arguments."");"
"}"],"mexfunction_11485.c");
ilib_mex_build("libmextest", ["mex_11485", "mexfunction_11485", "cmex"], "mexfunction_11485.c", "", "", "", ["-std=c11"]);
exec("loader.sce");

assert_checkerror("mex_11485()", [], 999);
ierr = execstr("mex_11485(2,2)","errcatch");
assert_checktrue(ierr == 0);
