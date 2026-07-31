// =============================================================================
// Scilab ( https://www.scilab.org/ ) - This file is part of Scilab
// Copyright (C) 2023 - Dassault Systèmes - Clément DAVID
//
//  This file is distributed under the same license as the Scilab package.
// =============================================================================

// <-- CLI SHELL MODE -->
// <-- NO CHECK REF -->
// <-- NO CHECK ERROR OUTPUT -->

// <-- Short Description -->
// quit is an command line option
//

// Try to find the path of Scilab executable
//scilab path
if getos() == "Windows" then
    scilabBin = """" + WSCI + "\bin\scilex""";
else
    scilabBin = strsplit(SCI, "share/scilab")(1) + "/bin/scilab-cli";
end

exitcode = host(scilabBin + " -quit -e ""1+1"" --timeout 2m");
assert_checkequal(exitcode, 0);

filepath = fullfile(TMPDIR, "quit_option.sce");
code = "1 + 1";
mputl(code, filepath);

exitcode = host("< " + filepath + " " + scilabBin + " -quit --timeout 2m");
assert_checkequal(exitcode, 0);
// without -quit option the behavior is the same
exitcode = host("< " + filepath + " " + scilabBin + "  --timeout 2m");
assert_checkequal(exitcode, 0);

exitcode = host(scilabBin + " -quit -e ""error(''exit code will be set'')"" --timeout 2m");
assert_checktrue(exitcode <> 0 && exitcode <> 22 && exitcode <> 258);

code = "error(''exit code will be set'')";
mputl(code, filepath);

exitcode = host("< " + filepath + " " + scilabBin + " -quit --timeout 2m");
assert_checktrue(exitcode <> 0 && exitcode <> 22 && exitcode <> 258);
// without -quit option the behavior is the same
exitcode = host("< " + filepath + " " + scilabBin + "  --timeout 2m");
assert_checktrue(exitcode <> 0 && exitcode <> 22 && exitcode <> 258);

// A CAUGHT error is a handled error: the run succeeded, so the status is 0.
//
// bug_14225 already asserts this, but only for a handler that calls
// disp(lasterror()) -- and READING lasterror() is what clears it. A handler
// that does not happen to read it used to leak the error number into the exit
// status, which is why a 53-toolbox startup whose loader caught and reported
// every failure still exited 231. The handler below deliberately reads nothing.
code = ["try"; "    error(''handled, on purpose'');"; "catch"; "end"; "disp(''done'')"];
mputl(code, filepath);

exitcode = host(scilabBin + " -quit -f " + filepath + " --timeout 2m");
assert_checkequal(exitcode, 0);
exitcode = host("< " + filepath + " " + scilabBin + " -quit --timeout 2m");
assert_checkequal(exitcode, 0);
// without -quit option the behavior is the same
exitcode = host("< " + filepath + " " + scilabBin + "  --timeout 2m");
assert_checkequal(exitcode, 0);

// NOT covered here: -quit when stdin is a pipe that stays OPEN and never
// delivers EOF (java.lang.Runtime.exec hands a child exactly that). Scilab used
// to discard -quit in that situation and block forever; the fix lives in
// scilab.cpp's "we are in a pipe" branch.
//
// It is deliberately not a .tst case. Reproducing it needs a writer that never
// goes away, and holding one open from inside host() -- whether by a background
// process or a shell descriptor on a fifo -- races with the child's open() of
// that fifo. Measured over three runs of this file, that shape passed twice and
// failed once with 134 (the watchdog aborting a child whose open never
// completed). An intermittent test is worse than no test, so the coverage lives
// in a deterministic runner instead:
//
//     modules/core/tests/native/run_quit_open_stdin.sh
//
// which is the same convention already used for the B21/B22 scenarios the .tst
// harness cannot host reliably.
