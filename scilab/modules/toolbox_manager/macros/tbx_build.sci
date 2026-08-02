// tbx_build -- build one toolbox from its own builder script.
//
// WHY THIS SCANS THE BUILD LOG
// ----------------------------
// The builders report per-component results themselves and then exit cleanly
// regardless, e.g. cgal:
//
//     [1/4] src/cpp (libcgal_cpp)     ierr=10000     <- a real failure
//     [2/4] sci_gateway/c (gw_cglab)  ierr=0
//     [3/4] macros                    ierr=0
//     [4/4] loader                    ierr=0
//
// The old test -- execstr's own status plus "loader.sce exists" -- was true in
// that case, so the toolbox was reported "ok" while its native library had not
// been built at all. A 54-toolbox run could therefore end claiming complete
// success with several toolboxes silently broken, which is worse than failing.
//
// There is no return channel from the builders, so the log is the only place
// those component results exist. Capturing it with diary() and scanning for a
// non-zero ierr, or for autoconf's "cannot create executables", turns a silent
// partial failure into a reported one.
function ok = tbx_build(path)
    cfg = tbx_cfg();
    setenv("CPATH", cfg.cpath);
    setenv("LIBRARY_PATH", cfg.libpath);
    setenv("__USE_DEPRECATED_STACK_FUNCTIONS__", "YES");
    script = "";
    if isfile(fullfile(path, "build_macos.sce")) then
        script = fullfile(path, "build_macos.sce");
    elseif isfile(fullfile(path, "builder.sce")) then
        script = fullfile(path, "builder.sce");
    end
    if script == "" then ok = isfile(fullfile(path, "loader.sce")); return; end

    // Sentinel for builders that end in quit/exit. Several toolboxes finish their
    // build_macos.sce with a bare `quit` so that running them standalone does not
    // strand Scilab at an interactive prompt -- correct on its own, fatal here:
    // exec'd from this loop that quit terminates the WHOLE process, and a
    // 54-toolbox run died silently at scicv (number 16) with no error and no
    // summary, looking for all the world like a clean finish. Builders test for
    // this variable and skip their quit when it is set.
    TBX_NESTED_BUILD = %t;

    logf = fullfile(TMPDIR, "tbx_build_" + basename(path) + ".log");
    mdelete(logf);
    diary(logf);
    ie = execstr("exec(""" + script + """, -1)", "errcatch");
    diary(0);

    lerr = "";
    if ie <> 0 then lerr = lasterror(); end
    ok = (ie == 0) & isfile(fullfile(path, "loader.sce"));

    // Help is the LAST step in every toolbox builder, and it cannot run here:
    //
    //     tbx_build_help: documentation cannot be built in this scilab mode: NWNI.
    //
    // scilab-cli is NWNI and has no JVM, so tbx_build_help raises and aborts the
    // builder with ierr=10000 AFTER the sources, gateway, macros and loader have
    // all been built successfully. Failing the toolbox for that would be as
    // misleading as the blanket "ok" this function used to report -- the code is
    // built, only the documentation was not regenerated. Excused NARROWLY: this
    // exact message and nothing else, and only when loader.sce is present.
    if ~ok & lerr <> [] then
        if grep(lerr, "documentation cannot be built in this scilab mode") <> [] & ..
           isfile(fullfile(path, "loader.sce")) then
            mprintf("      note: help not regenerated (NWNI has no JVM); code and macros built\n");
            ok = %t;
        end
    end
    if ~ok then return; end

    // The builder exited cleanly -- now check it did not quietly fail inside.
    if ~isfile(logf) then return; end
    txt = mgetl(logf);
    if txt == [] then return; end
    bad = [];
    hits = grep(txt, "ierr=");
    for i = 1:size(hits, "*")
        line = txt(hits(i));
        k = strindex(line, "ierr=");
        if k <> [] then
            code = strtod(part(line, (k($) + 5):length(line)));
            if ~isnan(code) & code <> 0 then bad = [bad ; line]; end
        end
    end
    cce = grep(txt, "cannot create executables");
    if bad <> [] | cce <> [] then
        mprintf("      PARTIAL FAILURE -- the builder exited cleanly but a component did not:\n");
        for i = 1:min(size(bad, "*"), 6)
            mprintf("        %s\n", stripblanks(bad(i)));
        end
        if cce <> [] then
            mprintf("        %d configure run(s) reported ""cannot create executables""\n", size(cce, "*"));
        end
        ok = %f;
    end
endfunction
