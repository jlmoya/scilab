function tbxManager()
    if getscilabmode() <> "STD" then
        mprintf("tbxManager() needs the Scilab desktop (STD mode). Current set:\n");
        tbxList();
        mprintf("Use tbxInstall(""name"") / tbxRemove(""name"") from here.\n");
        return;
    end
    C = tbxCatalog();
    M = tbx_manifest_read();
    firstrun = isempty(M.name);
    n = size(C.name, "*");
    items = [];
    for i = 1:n
        k = tbx_find(M, C.name(i));
        enabled = %f;
        if k > 0 then
            enabled = (M.autoload(k) == 1);
        elseif firstrun then
            enabled = C.verified(i);            // first run: pre-check the verified set
        end
        if C.verified(i) then tag = "  (verified)"; else tag = "  (build-only)"; end
        box = "[ ] "; if enabled then box = "[x] "; end
        items = [items; box + C.name(i) + tag];
    end

    W = 470; H = 580;
    f = figure("figure_name", "Scilab Toolbox Manager", "dockable", "off", ..
               "infobar_visible", "off", "toolbar", "none", "menubar", "none", ..
               "default_axes", "off", "position", [120 120 W H], "tag", "tbxmgr_fig");
    uicontrol(f, "style", "text", ..
              "string", "Check the toolboxes to enable. Changes take effect on relaunch.", ..
              "position", [15 H-42 W-30 26], "horizontalalignment", "left", "fontsize", 12);
    uicontrol(f, "style", "listbox", "string", items, "tag", "tbxmgr_lb", ..
              "fontname", "Menlo", "fontsize", 13, "position", [15 78 W-30 H-130], ..
              "callback", "tbx_gui_toggle()");
    uicontrol(f, "style", "pushbutton", "string", "Select verified", ..
              "position", [15 44 130 26], "callback", "tbx_gui_preset(""verified"")");
    uicontrol(f, "style", "pushbutton", "string", "Clear all", ..
              "position", [152 44 90 26], "callback", "tbx_gui_preset(""none"")");
    uicontrol(f, "style", "pushbutton", "string", "Apply", ..
              "position", [15 8 90 28], "callback", "tbx_gui_apply(%f)");
    uicontrol(f, "style", "pushbutton", "string", "Apply & Relaunch", ..
              "position", [112 8 160 28], "backgroundcolor", [0.2 0.5 0.9], ..
              "foregroundcolor", [1 1 1], "callback", "tbx_gui_apply(%t)");
    uicontrol(f, "style", "pushbutton", "string", "Close", ..
              "position", [W-90 8 75 28], "callback", "close(findobj(""tag"",""tbxmgr_fig""))");
endfunction
