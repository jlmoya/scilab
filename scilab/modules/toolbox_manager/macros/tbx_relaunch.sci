function tbx_relaunch()
    appbundle = fullpath(fullfile(SCI, "..", "..", ".."));   // /Applications/Scilab-2027.0.0.app
    if isdir(appbundle) & part(appbundle, length(appbundle)-3:length(appbundle)) == ".app" then
        unix_g("open -n """ + appbundle + """ >/dev/null 2>&1 &");
        exit;
    else
        // In-tree dev build (no app bundle): relaunch the same binary with the
        // same SCIHOME so the new toolbox set autoloads on the next start.
        // nohup + background detaches the successor from this exiting process.
        scilab_bin = fullfile(SCI, "bin", "scilab");
        if isfile(scilab_bin) then
            dq = """";
            unix_g("nohup " + dq + scilab_bin + dq + " -scihome " + dq + SCIHOME + dq + ..
                   " >/dev/null 2>&1 &");
            exit;
        else
            messagebox(["Could not locate bin/scilab to relaunch automatically."; ..
                        "Restart Scilab to load the new toolbox set."], "tbxManager", "info");
        end
    end
endfunction
