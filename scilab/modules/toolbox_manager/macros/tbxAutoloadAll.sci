function tbxAutoloadAll()
    // Called by .scilab (and usable interactively). Execs each loader INLINE here —
    // one call-level below base — so the macro libraries reach the global scope.
    M = tbx_manifest_read();
    n = 0;
    for i = 1:size(M.name, "*")
        if M.autoload(i) == 1 then
            ldr = fullfile(M.path(i), "loader.sce");
            if isfile(ldr) then
                ok = %t; try, exec(ldr, -1); catch, ok = %f; end
                if ok then n = n + 1; mprintf("  loaded  %s\n", M.name(i));
                else mprintf("  FAILED  %s\n", M.name(i)); end
            else
                mprintf("  skip    %s (not built)\n", M.name(i));
            end
        end
    end
    if n > 0 then mprintf("[toolbox-manager] autoloaded %d toolbox(es).\n", n); end
endfunction
