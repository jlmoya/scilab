function ok = tbxInstall(name, source)
    cfg = tbx_cfg();
    if argn(2) < 2 then source = "auto"; end
    [path, src] = tbx_resolve(name, source);
    mprintf("tbxInstall %s (source=%s)\n", name, src);
    if src == "remote" then
        if isdir(fullfile(path, ".git")) then
            mprintf("  git pull...\n"); tbx_sh("cd " + path + " && git pull --ff-only");
        else
            mprintf("  git clone...\n");
            [ok, o] = tbx_sh("git clone " + cfg.glbase + name + ".git " + path);
            if ~ok then  // our fork has it not — try the CANONICAL upstream forge
                mprintf("  not in our fork; trying the Scilab forge...\n");
                [ok, o] = tbx_sh("git clone " + cfg.forgebase + name + ".git " + path);
            end
            if ~ok then  // finally the GitHub mirror
                [ok, o] = tbx_sh("git clone " + cfg.ghbase + name + ".git " + path);
            end
            if ~ok then mprintf("  clone FAILED for %s\n", name); ok = %f; return; end
        end
    end
    if ~isfile(fullfile(path, "loader.sce")) then
        mprintf("  building...\n");
        if ~tbx_build(path) then mprintf("  build FAILED for %s\n", name); ok = %f; return; end
    end
    // native-arm64 gate: refuse a toolbox that ships non-arm64 native libs (would need Rosetta)
    [archok, bad] = tbx_arch_check(path);
    if ~archok then
        mprintf("  ARCH GATE: %s ships non-arm64 native libs; not installing (needs an arm64 rebuild):\n", name);
        for i = 1:size(bad, "*")
            mprintf("    %s\n", bad(i));
        end
        ok = %f; return;
    end
    // register (autoload = 1)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then
        M.name = [M.name; name]; M.path = [M.path; path];
        M.source = [M.source; src]; M.autoload = [M.autoload; 1];
    else
        M.path(k) = path; M.source(k) = src; M.autoload(k) = 1;
    end
    tbx_manifest_write(M);
    // load now — exec the loader INLINE (not via tbxLoad). A toolbox macro library
    // only propagates ONE call-level up on return, so it must be exec'd at this depth
    // (tbxInstall is called directly by the user/GUI), not one level deeper in tbxLoad.
    ldr = fullfile(path, "loader.sce");
    ok = %t; try, exec(ldr, -1); catch, ok = %f; end
    if ok then mprintf("  loaded  %s\n", name); else mprintf("  load FAILED %s\n", name); end
endfunction
