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
    // ---- load declared dependencies FIRST, transitively, dependency-first ----
    // Without this, verification exercises a configuration no user ever runs:
    // the app's autoload closes over dependencies and topologically sorts them
    // (tbxAutoloadList), while this ran the toolbox's loader alone. A toolbox
    // whose loader hard-requires a dependency therefore failed the sweep for a
    // reason that has nothing to do with the toolbox -- guimaker's "apifun is
    // required but not loaded" is exactly that, and it is a correct guard doing
    // its job, not a defect.
    //
    // Deps come from DESCRIPTION only, never from the manifest: this runs in a
    // throwaway SCIHOME whose manifest is empty by construction, so a
    // dependency recorded only in one user's manifest is invisible here. That
    // is the point -- it forces the dependency to be declared IN the toolbox,
    // where every consumer can see it.
    __tbxv_pend = tbx_deps(path);
    __tbxv_seen = [];
    while ~isempty(__tbxv_pend)
        __tbxv_nm = __tbxv_pend(1);
        __tbxv_pend(1) = [];
        if ~isempty(__tbxv_seen) && or(__tbxv_seen == __tbxv_nm) then continue; end
        __tbxv_seen = [__tbxv_seen ; __tbxv_nm];
        __tbxv_dp = fullfile(cfg.projects, __tbxv_nm);
        if ~isdir(__tbxv_dp) then __tbxv_dp = fullfile(cfg.tbxdir, __tbxv_nm); end
        if ~isdir(__tbxv_dp) then
            __tbxv_R.err = "declared dependency not installed: " + __tbxv_nm;
            return;
        end
        __tbxv_pend = [__tbxv_pend ; tbx_deps(__tbxv_dp)];
    end
    if ~isempty(__tbxv_seen) then
        __tbxv_dl = list();
        for __tbxv_k = 1:size(__tbxv_seen, "*")
            __tbxv_dp = fullfile(cfg.projects, __tbxv_seen(__tbxv_k));
            if ~isdir(__tbxv_dp) then __tbxv_dp = fullfile(cfg.tbxdir, __tbxv_seen(__tbxv_k)); end
            __tbxv_dl(__tbxv_k) = tbx_deps(__tbxv_dp);
        end
        [__tbxv_ord, __tbxv_miss, __tbxv_cyc] = tbx_toposort(__tbxv_seen, __tbxv_dl);
        // cycle members are still loaded, just unordered -- same policy as
        // tbxAutoloadList: a cycle is worth reporting, not worth refusing over.
        for __tbxv_k = 1:size(__tbxv_seen, "*")
            if isempty(find(__tbxv_ord == __tbxv_k)) then
                __tbxv_ord = [__tbxv_ord ; __tbxv_k];
            end
        end
        for __tbxv_k = 1:size(__tbxv_ord, "*")
            __tbxv_nm = __tbxv_seen(__tbxv_ord(__tbxv_k));
            __tbxv_dp = fullfile(cfg.projects, __tbxv_nm);
            if ~isdir(__tbxv_dp) then __tbxv_dp = fullfile(cfg.tbxdir, __tbxv_nm); end
            __tbxv_ldr = fullfile(__tbxv_dp, "loader.sce");
            if ~isfile(__tbxv_ldr) then
                __tbxv_R.err = "declared dependency " + __tbxv_nm + " has no loader.sce";
                return;
            end
            if execstr("exec(__tbxv_ldr, -1)", "errcatch") <> 0 then
                __tbxv_R.err = "declared dependency " + __tbxv_nm + " failed to load: " + lasterror();
                return;
            end
        end
    end

    nbefore = size(librarieslist(), "*");
    // execstr(..., "errcatch") here is fail-safe by construction (contrast
    // tbxLoad.sci:17-20's IMPORTANT warning about this same shape trapping
    // lib() registration in a temporary eval scope): this run is throwaway and
    // consumes delta/smoke/smoke_ok entirely within itself, so a lost
    // registration can only turn a would-be PASS into a loud FAIL, never a
    // false PASS. A loader that registers its library as a pure function side
    // effect (accsum's shape) may therefore read delta=0 here -- it verifies
    // via its smoke instead.
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
