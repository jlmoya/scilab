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
