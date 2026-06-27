// ============================================================================
// tbxmgr_gui.sce — tbxManager() checkbox GUI for the toolbox manager.
//
// A scrollable check-list of known toolboxes. Each row is "[x] name (tag)";
// clicking a row toggles its checkbox. Buttons:
//   Apply             — write the manifest (enable checked, disable unchecked),
//                        building anything not built yet.
//   Apply & Relaunch  — Apply, then relaunch the app so the checked toolboxes
//                        autoload immediately (Scilab loads toolbox macros only at
//                        startup/top level — see docs/design/macos-app-packaging.md).
// ============================================================================

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

// ---- toggle the clicked row(s) --------------------------------------------
function tbx_gui_toggle()
    lb = findobj("tag", "tbxmgr_lb");
    items = lb.string; v = lb.value;
    for j = 1:size(v, "*")
        i = v(j); s = items(i);
        if part(s, 1:4) == "[x] " then items(i) = "[ ] " + part(s, 5:length(s));
        else items(i) = "[x] " + part(s, 5:length(s)); end
    end
    lb.string = items;
endfunction

// ---- preset: check the verified set, or clear all -------------------------
function tbx_gui_preset(which)
    lb = findobj("tag", "tbxmgr_lb");
    items = lb.string; C = tbxCatalog();
    vnames = C.name(find(C.verified));
    for i = 1:size(items, "*")
        rest = part(items(i), 5:length(items(i)));
        on = %f;
        if which == "verified" then on = or(tbx_gui_itemname(items(i)) == vnames); end
        if on then items(i) = "[x] " + rest; else items(i) = "[ ] " + rest; end
    end
    lb.string = items;
endfunction

// ---- extract the toolbox name from a "[x] name  (tag)" row ----------------
function nm = tbx_gui_itemname(s)
    t = part(s, 5:length(s));
    p = strindex(t, "  (");
    if ~isempty(p) then t = part(t, 1:p($)-1); end
    nm = stripblanks(t);
endfunction

// ---- Apply: write the manifest (build missing); optionally relaunch -------
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

// ---- relaunch the app so the new toolbox set autoloads --------------------
function tbx_relaunch()
    appbundle = fullpath(fullfile(SCI, "..", "..", ".."));   // /Applications/Scilab-2027.0.0.app
    if isdir(appbundle) & part(appbundle, length(appbundle)-3:length(appbundle)) == ".app" then
        unix_g("open -n """ + appbundle + """ >/dev/null 2>&1 &");
        exit;
    else
        messagebox(["Not running from the packaged app — cannot relaunch automatically."; ..
                    "Restart Scilab to load the new toolbox set."], "tbxManager", "info");
    end
endfunction
