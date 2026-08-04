// Re-capture the ilib_build oracle on the current machine.
// See README.md and docs/design/dynamic-link-cmake-migration.md §11.
//
// Writes oracle-commands-<platform>.NEW.txt next to this script: the resolved
// libtool compile/link lines for the C / C++ / mixed-Fortran matrix, with the
// repeated Scilab include flags collapsed so the differences between cases stay
// visible. The ".NEW" is deliberate -- a re-capture must never silently
// overwrite the checked-in reference it is meant to be compared against.

here = get_absolute_file_path("capture-oracle.sce");

// Build in a scratch copy, never in place. ilib_build drops the generated
// wrapper (lib<name>.cpp/.h/.hxx), the dylib, loader.sce and cleaner.sce into
// the CURRENT directory -- running this in the checked-in fixture directory
// leaves ~10 build artifacts behind for someone to accidentally commit.
work = fullfile(TMPDIR, "oracle-capture");
mkdir(work);
for f = ["gw_c.c", "gw_cxx.cpp", "gw_f.f"]
    copyfile(fullfile(here, f), work);
end
chdir(work);

// diary() is what makes this work at all: the command lines only exist in
// ilib_verbose(2) output, which goes to the console, not to any return value.
logf = fullfile(TMPDIR, "oracle-raw.log");
if isfile(logf) then mdelete(logf); end
diary(logf);
ilib_verbose(2);

// One entry per distinct code path through ilib_gen_Make_unix. The libname is
// deliberately "lib*"-prefixed to match how real toolboxes call this (and to
// keep the TMPDIR/<name-without-lib> stripping visible in the log).
cases = list( ..
    list("libOracleC",   ["sci_gw_c","sci_gw_c"],     ["gw_c.c"]),              ..
    list("libOracleX",   ["sci_gw_cxx","sci_gw_cxx"], ["gw_cxx.cpp"]),          ..
    list("libOracleF",   ["sci_gw_c","sci_gw_c"],     ["gw_c.c","gw_f.f"])      ..
);

failed = 0;
for k = 1:size(cases)
    c = cases(k);
    mprintf("\n===== CASE %s =====\n", c(1));
    // errcatch, not a bare call: one failing case must not abandon the diary
    // and lose the cases that would have succeeded.
    ie = execstr("ilib_build(c(1), c(2), c(3), []);", "errcatch");
    if ie <> 0 then
        failed = failed + 1;
        mprintf("### CASE %s FAILED ierr=%d: %s\n", c(1), ie, lasterror()(1));
    end
end

diary(0);

// Post-process: keep only the resolved command lines, collapse the noisy
// repeated include/libdir blocks.
//
// Done by tokenising rather than by regex on purpose. The things being
// collapsed are absolute paths, and Scilab's regex flavour requires the pattern
// to be "/"-delimited -- so every "/" in the path would need escaping, which is
// how this went wrong the first time. Splitting on spaces has no such trap.
txt = mgetl(logf);
txt = stripblanks(txt);
txt = strsubst(txt, "Output: ", "");
keep = grep(txt, "libtool: compile:") ;
keep = [keep, grep(txt, "libtool: link:")];
if isempty(keep) then
    cmds = [];
else
    cmds = txt(gsort(keep, "g", "i"));
end

// The include COUNT is kept in the placeholder, not just collapsed away: C and
// C++ get 16 Scilab includes while Fortran gets exactly 1 (core only). That
// asymmetry is a real property of automake's F77 rule that the CMake path has
// to reproduce, and a bare "<SCI-INCLUDES>" would hide it.
sciInc = "-I" + SCI + "/modules/";
for i = 1:size(cmds, "*")
    toks = tokens(cmds(i), " ");
    out = []; nInc = 0; nLib = 0;
    for t = toks'
        if part(t, 1:length(sciInc)) == sciInc then
            nInc = nInc + 1;
            if nInc == 1 then out = [out; "<SCI-INCLUDES>"]; end
        elseif part(t, 1:2) == "-L" & grep(t, "/gcc/") <> [] then
            nLib = nLib + 1;
            if nLib == 1 then out = [out; "<GCC-LIBDIRS>"]; end
        else
            out = [out; t];
        end
    end
    line = strcat(out, " ");
    line = strsubst(line, "<SCI-INCLUDES>", "<SCI-INCLUDES x" + string(nInc) + ">");
    line = strsubst(line, "<GCC-LIBDIRS>",  "<GCC-LIBDIRS x" + string(nLib) + ">");
    cmds(i) = line;
end

plat = "unknown";
if getos() == "Darwin" then
    plat = "macos-" + getenv("HOSTTYPE", "arm64");
else
    plat = convstr(getos(), "l");
end
outf = fullfile(here, "oracle-commands-" + plat + ".NEW.txt");
mputl(cmds, outf);

mprintf("\n### cases run: %d, failed: %d\n", size(cases), failed);
mprintf("### command lines captured: %d\n", size(cmds, "*"));
mprintf("### written: %s\n", outf);
mprintf("### raw log kept at: %s\n", logf);
if failed <> 0 | size(cmds, "*") == 0 then
    mprintf("### CAPTURE INCOMPLETE - do not compare against the reference\n");
end

// exit() needs a numeric scalar; a boolean silently becomes "Wrong type" and
// the caller sees a bogus status instead of the real one.
exit(bool2s(failed <> 0 | size(cmds, "*") == 0));
