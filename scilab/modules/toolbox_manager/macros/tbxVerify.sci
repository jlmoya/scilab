function __tbxv_R = tbxVerify(name)
    // Verify one toolbox against THIS Scilab build:
    //   build (if no loader.sce) -> arm64 arch gate -> loader must exec clean
    //   -> pass needs >=1 newly registered library OR a passing smoke <SCI>/tbx-smoke/<name>.sce
    //      (smoke must run clean and set smoke_ok=%t; gateway-only toolboxes add no macro
    //       library, so smoke evidence is their pass path).
    // Meant to run in a throwaway session (see tbx-verify-all.sh): loading pollutes the session.
    cfg = tbx_cfg();
    // Obscure return-variable name on purpose: smoke scripts exec in THIS scope (so they can
    // see path and the just-loaded library) and must not be able to clobber the result;
    // scratch names like R/x/ok are fair game for them.
    __tbxv_R = struct("name", name, "built", %f, "archok", %f, "loaded", %f, ..
                      "delta", 0, "smoke", "none", "pass", %f, "err", "");
    path = fullfile(cfg.projects, name);
    if ~isdir(path) then path = fullfile(cfg.tbxdir, name); end
    if ~isdir(path) then __tbxv_R.err = "toolbox dir not found"; return; end
    if ~isfile(fullfile(path, "loader.sce")) then
        if ~tbx_build(path) then __tbxv_R.err = "build failed"; return; end
    end
    __tbxv_R.built = %t;
    [archok, bad] = tbx_arch_check(path);
    __tbxv_R.archok = archok;
    if ~archok then __tbxv_R.err = "non-arm64 native lib: " + strcat(bad', ", "); return; end
    nbefore = size(librarieslist(), "*");
    ie = execstr("exec(fullfile(path, ""loader.sce""), -1)", "errcatch");
    if ie <> 0 then __tbxv_R.err = "loader error " + string(ie) + ": " + lasterror(); return; end
    __tbxv_R.loaded = %t;
    __tbxv_R.delta = size(librarieslist(), "*") - nbefore;
    smk = fullfile(SCI, "tbx-smoke", name + ".sce");
    if isfile(smk) then
        smoke_ok = %f;
        ie = execstr("exec(smk, -1)", "errcatch");
        if ie <> 0 then __tbxv_R.smoke = "FAIL"; __tbxv_R.err = "smoke error: " + lasterror(); return; end
        // The smoke may have cleared or re-typed smoke_ok: anything not identically %t fails.
        if ~isdef("smoke_ok") || ~isequal(smoke_ok, %t) then
            __tbxv_R.smoke = "FAIL"; __tbxv_R.err = "smoke ran but smoke_ok<>%t"; return;
        end
        __tbxv_R.smoke = "OK";
    end
    // Pass criterion (v1.1): loader clean AND (>=1 new library OR passing smoke).
    if __tbxv_R.delta < 1 && __tbxv_R.smoke <> "OK" then
        __tbxv_R.err = "loader registered no library and no passing smoke (gateway-only toolboxes need a tbx-smoke file)";
        return;
    end
    __tbxv_R.pass = %t;
endfunction
