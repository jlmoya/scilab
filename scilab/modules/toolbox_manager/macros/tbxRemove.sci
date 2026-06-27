function ok = tbxRemove(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then mprintf("tbxRemove: %s not installed\n", name); ok = %f; return; end
    tbxUnload(name);
    // delete only remote clones (never the user's SciLabProjects working tree)
    if M.source(k) == "remote" & isdir(M.path(k)) then
        tbx_sh("rm -rf " + M.path(k));
    end
    keep = (1:size(M.name, "*")) <> k;
    M.name = M.name(keep); M.path = M.path(keep);
    M.source = M.source(keep); M.autoload = M.autoload(keep);
    tbx_manifest_write(M);
    mprintf("  removed %s\n", name); ok = %t;
endfunction
