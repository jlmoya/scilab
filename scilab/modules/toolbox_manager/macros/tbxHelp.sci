function tbxHelp(name)
    V = [ ..
"tbxManager()        ";"open the GUI check-list of toolboxes. Tick the ones to enable, then Apply (save) or";
"                    ";"Apply & Relaunch (save + restart so the checked toolboxes autoload immediately).";
"tbxList()           ";"show the installed toolboxes: source, autoload flag and path.";
"tbxInstall(name[,s])";"install a toolbox: s=""local"" uses ~/Projects/SciLabProjects/<name>, ""remote"" clones";
"                    ";"from jlmoya (GitLab then GitHub); default prefers a local clone. Builds + sets autoload=1.";
"tbxUpdate([name])   ";"git pull + rebuild one toolbox, or all of them when called with no argument.";
"tbxRemove(name)     ";"unload + unregister; deletes the clone only when it was installed from ""remote"".";
"tbxLoader(name)     ";"return the toolbox loader.sce path, e.g.  exec(tbxLoader(""cgal"")) to load now.";
"tbxHelp([name])     ";"this help; tbxHelp(""tbxInstall"") for one verb." ];
    names = V(1:2:$);  desc = V(2:2:$);
    if argn(2) >= 1 then
        req = stripblanks(name); k = 0;
        for i = 1:size(names, "*")
            nm = stripblanks(names(i)); p = strindex(nm, "(");
            if ~isempty(p) then nm = part(nm, 1:p(1)-1); end
            if nm == req then k = i; break; end
        end
        if k == 0 then mprintf("tbxHelp: no verb ""%s"". Try tbxHelp().\n", name); return; end
        mprintf("\n  %s\n      %s\n\n", stripblanks(names(k)), desc(k));
        return;
    end
    mprintf("\n Scilab-app toolbox manager — manage built-from-source toolboxes.\n");
    mprintf(" Pick toolboxes in tbxManager(); they are remembered and autoload every session.\n\n");
    for i = 1:size(names, "*")
        if stripblanks(names(i)) <> "" then mprintf("   %-20s %s\n", names(i), desc(i));
        else mprintf("   %-20s %s\n", "", desc(i)); end
    end
    mprintf("\n Note: macros activate at launch (Scilab loads toolbox macros only at startup),\n");
    mprintf("       which is why the GUI offers ""Apply & Relaunch"".\n\n");
endfunction
