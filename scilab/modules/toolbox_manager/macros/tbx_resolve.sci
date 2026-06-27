function [path, src] = tbx_resolve(name, source)
    cfg = tbx_cfg();
    localp = fullfile(cfg.projects, name);
    if argn(2) < 2 then source = "auto"; end
    select source
    case "local" then
        path = localp; src = "local";
    case "remote" then
        path = fullfile(cfg.tbxdir, name); src = "remote";
    else  // auto: prefer a built local clone, else remote
        if isdir(localp) then path = localp; src = "local";
        else path = fullfile(cfg.tbxdir, name); src = "remote"; end
    end
endfunction
