function ok = tbxUnload(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then [path, src] = tbx_resolve(name); else path = M.path(k); end
    unl = fullfile(path, "unloader.sce");
    ok = %t;
    if isfile(unl) then
        try, exec(unl, -1); catch, ok = %f; end
    end
    if ok then mprintf("  unloaded %s\n", name); end
endfunction
