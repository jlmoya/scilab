function ok = tbxLoad(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then [path, src] = tbx_resolve(name); else path = M.path(k); end
    ldr = fullfile(path, "loader.sce");
    if ~isfile(ldr) then
        mprintf("tbxLoad: %s not built (no loader.sce at %s)\n", name, path); ok = %f; return;
    end
    // IMPORTANT: exec the loader DIRECTLY (not via execstr). A direct exec lets the
    // toolbox's macro library (loaded with lib()) propagate to the global scope; an
    // execstr() wrapper traps it in a temporary eval scope so macros vanish on return
    // (gateways survive either way because link() is global). try/catch preserves this.
    ok = %t;
    try, exec(ldr, -1); catch, ok = %f; end
    if ok then mprintf("  loaded  %s\n", name);
    else mprintf("  FAILED  %s: %s\n", name, part(lasterror(), 1:120)); end
endfunction
