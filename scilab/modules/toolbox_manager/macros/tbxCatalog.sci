function C = tbxCatalog()
    cfg = tbx_cfg();
    C = struct("name", [], "verified", [], "installed", []);
    M = tbx_manifest_read();
    // discover from local SciLabProjects (dirs with a loader.sce or builder.sce)
    d = dir(cfg.projects);
    names = [];
    for i = 1:size(d.name, "*")
        nm = d.name(i);
        if d.isdir(i) & nm <> "." & nm <> ".." then
            p = fullfile(cfg.projects, nm);
            if isfile(fullfile(p,"loader.sce")) | isfile(fullfile(p,"builder.sce")) then
                names = [names; nm];
            end
        end
    end
    names = unique(names);
    for i = 1:size(names, "*")
        C.name      = [C.name; names(i)];
        C.verified  = [C.verified; or(names(i) == cfg.verified)];
        C.installed = [C.installed; tbx_find(M, names(i)) > 0];
    end
endfunction
