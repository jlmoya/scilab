function ok = tbxUpdate(name)
    if argn(1) < 1 then  // update all (each tbxUpdate call is itself one level deep)
        M = tbx_manifest_read(); ok = %t;
        nm = M.name;
        for i = 1:size(nm, "*"), ok = tbxUpdate(nm(i)) & ok; end
        return;
    end
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then mprintf("tbxUpdate: %s not installed\n", name); ok = %f; return; end
    path = M.path(k);
    mprintf("tbxUpdate %s\n", name);
    if isdir(fullfile(path, ".git")) then tbx_sh("cd " + path + " && git pull --ff-only"); end
    ok = tbx_build(path);
    // native-arm64 gate: a rebuild may have produced non-arm64 native libs; don't load them
    [archok, bad] = tbx_arch_check(path);
    if ~archok then
        mprintf("  ARCH GATE: %s ships non-arm64 native libs; not loading (needs an arm64 rebuild):\n", name);
        for i = 1:size(bad, "*")
            mprintf("    %s\n", bad(i));
        end
        ok = %f; return;
    end
    ldr = fullfile(path, "loader.sce");        // inline exec (see tbxInstall note)
    try, exec(ldr, -1); catch, ok = %f; end
endfunction
