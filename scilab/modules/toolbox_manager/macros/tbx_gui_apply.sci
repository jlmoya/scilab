function tbx_gui_apply(relaunch)
    lb = findobj("tag", "tbxmgr_lb");
    items = lb.string;
    checked = [];
    for i = 1:size(items, "*")
        if part(items(i), 1:4) == "[x] " then
            checked = [checked; tbx_gui_itemname(items(i))];
        end
    end
    mprintf("[tbxManager] applying: %d toolbox(es) enabled\n", size(checked, "*"));
    // enable + ensure built
    for i = 1:size(checked, "*")
        nm = checked(i);
        M = tbx_manifest_read(); k = tbx_find(M, nm);
        if k == 0 then
            tbxInstall(nm);                 // clone/build/register (autoload=1)
        else
            M.autoload(k) = 1; tbx_manifest_write(M);
        end
    end
    // disable everything unchecked
    M = tbx_manifest_read();
    for k = 1:size(M.name, "*")
        if ~or(M.name(k) == checked) then M.autoload(k) = 0; end
    end
    tbx_manifest_write(M);

    if relaunch then
        tbx_relaunch();
    else
        messagebox(["Saved." ; ..
                    "Checked toolboxes will autoload on the next launch."; ..
                    "(Use Apply & Relaunch to activate them now.)"], ..
                   "tbxManager", "info");
    end
endfunction
