function tbx_manifest_write(M)
    cfg = tbx_cfg();
    lines = "# Scilab-app installed toolboxes — name<TAB>path<TAB>source<TAB>autoload(0/1)";
    for i = 1:size(M.name, "*")
        lines = [lines ; M.name(i) + ascii(9) + M.path(i) + ascii(9) + ..
                 M.source(i) + ascii(9) + string(M.autoload(i))];
    end
    mputl(lines, cfg.manifest);
endfunction
