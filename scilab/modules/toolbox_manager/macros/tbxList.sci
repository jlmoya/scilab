function tbxList()
    M = tbx_manifest_read();
    if isempty(M.name) then mprintf("No toolboxes installed. Run tbxManager() to add some.\n"); return; end
    mprintf("\n %-18s %-8s %-8s %s\n", "TOOLBOX", "SOURCE", "AUTOLOAD", "PATH");
    mprintf(" %s\n", part("-", 1:72));
    for i = 1:size(M.name, "*")
        mprintf(" %-18s %-8s %-8s %s\n", M.name(i), M.source(i), ..
                string(M.autoload(i)==1), M.path(i));
    end
    mprintf("\n");
endfunction
