function M = tbx_manifest_read()
    cfg = tbx_cfg();
    M = struct("name", [], "path", [], "source", [], "autoload", []);
    if ~isfile(cfg.manifest) then return; end
    lines = mgetl(cfg.manifest);
    for i = 1:size(lines, "*")
        L = stripblanks(lines(i));
        if L == "" | part(L, 1) == "#" then continue; end
        t = strsplit(L, ascii(9))';
        if size(t, "*") < 4 then continue; end
        M.name     = [M.name ; stripblanks(t(1))];
        M.path     = [M.path ; stripblanks(t(2))];
        M.source   = [M.source ; stripblanks(t(3))];
        M.autoload = [M.autoload ; evstr(stripblanks(t(4)))];
    end
endfunction
