function paths = tbxAutoloadList()
    M = tbx_manifest_read();
    paths = [];
    for i = 1:size(M.name, "*")
        if M.autoload(i) == 1 then
            ldr = fullfile(M.path(i), "loader.sce");
            if isfile(ldr) then paths = [paths; ldr]; end
        end
    end
endfunction
