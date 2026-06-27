// ============================================================================
// tbxmgr.sce — Scilab-app toolbox manager (macOS).
// Defines the tbx* verbs. exec'd by the app's $SCIHOME/.scilab at startup.
//
// Model (docs/design/macos-app-packaging.md):
//   * source of truth = git repos: prefer a local ~/Projects/SciLabProjects/<name>
//     clone, else clone from jlmoya (GitLab, then GitHub).
//   * load/unload uses the toolbox's native loader.sce / unloader.sce.
//   * a manifest in $SCIHOME remembers installed toolboxes + their autoload flag;
//     tbxAutoloadAll() (called by .scilab) reloads them every session.
// ============================================================================

function cfg = tbx_cfg()
    cfg = struct();
    cfg.home     = getenv("HOME");
    cfg.projects = fullfile(cfg.home, "Projects", "SciLabProjects");
    cfg.tbxdir   = fullfile(SCIHOME, "toolboxes");          // remote clones live here
    cfg.manifest = fullfile(SCIHOME, "installed_toolboxes.tbx");
    cfg.glbase   = "git@gitlab.com:jlmoya/";                 // SSH (works on this Mac)
    cfg.ghbase   = "https://github.com/jlmoya/";
    // native-build env (needed only when BUILDING native toolboxes)
    cfg.cpath    = "/opt/homebrew/opt/gettext/include";
    cfg.libpath  = "/opt/homebrew/opt/gettext/lib:/opt/homebrew/lib/gcc/current/gcc/aarch64-apple-darwin25/16:/opt/homebrew/lib/gcc/current";
    // verified-on-macOS set (pre-ticked in tbxManager; refined empirically in phase 5)
    cfg.verified = ["sciDatabase" "parquet" "xlsx" "libsvm" "guibuilder" "scicv" ..
                    "cgal" "sndfile-toolbox" "sciSymPy" "sciTorch" "sciQuantLib" ..
                    "PIMS" "financial" "nan" "quapro" "json" "specfun" "distfun" ..
                    "scidoe" "stixbox" "lowdisc"];
    if ~isdir(cfg.tbxdir) then mkdir(cfg.tbxdir); end
endfunction

// ---- manifest I/O (TSV: name <tab> path <tab> source <tab> autoload) -------
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

function tbx_manifest_write(M)
    cfg = tbx_cfg();
    lines = "# Scilab-app installed toolboxes — name<TAB>path<TAB>source<TAB>autoload(0/1)";
    for i = 1:size(M.name, "*")
        lines = [lines ; M.name(i) + ascii(9) + M.path(i) + ascii(9) + ..
                 M.source(i) + ascii(9) + string(M.autoload(i))];
    end
    mputl(lines, cfg.manifest);
endfunction

function i = tbx_find(M, name)
    i = 0;
    for k = 1:size(M.name, "*")
        if M.name(k) == name then i = k; return; end
    end
endfunction

// ---- resolve where a toolbox lives / should come from ----------------------
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

// ---- run a shell command, return success boolean + output ------------------
function [ok, out] = tbx_sh(cmd)
    out = unix_g(cmd + " && echo __TBX_OK__");
    ok  = ~isempty(grep(out, "__TBX_OK__"));
endfunction

// ---- build a toolbox in place (native env set) -----------------------------
function ok = tbx_build(path)
    cfg = tbx_cfg();
    setenv("CPATH", cfg.cpath);
    setenv("LIBRARY_PATH", cfg.libpath);
    setenv("__USE_DEPRECATED_STACK_FUNCTIONS__", "YES");
    script = "";
    if isfile(fullfile(path, "build_macos.sce")) then
        script = fullfile(path, "build_macos.sce");
    elseif isfile(fullfile(path, "builder.sce")) then
        script = fullfile(path, "builder.sce");
    end
    if script == "" then ok = isfile(fullfile(path, "loader.sce")); return; end
    ie = execstr("exec(""" + script + """, -1)", "errcatch");
    ok = (ie == 0) & isfile(fullfile(path, "loader.sce"));
endfunction

// ---- load / unload via the toolbox's native loader -------------------------
function ok = tbxLoad(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then [path, src] = tbx_resolve(name); else path = M.path(k); end
    ldr = fullfile(path, "loader.sce");
    if ~isfile(ldr) then
        mprintf("tbxLoad: %s not built (no loader.sce at %s)\n", name, path); ok = %f; return;
    end
    // IMPORTANT: exec the loader DIRECTLY (not via execstr). A direct exec lets the
    // toolbox's macro library (loaded with lib()) propagate to the global scope; an
    // execstr() wrapper traps it in a temporary eval scope so macros vanish on return
    // (gateways survive either way because link() is global). try/catch preserves this.
    ok = %t;
    try, exec(ldr, -1); catch, ok = %f; end
    if ok then mprintf("  loaded  %s\n", name);
    else mprintf("  FAILED  %s: %s\n", name, part(lasterror(), 1:120)); end
endfunction

function ok = tbxUnload(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then [path, src] = tbx_resolve(name); else path = M.path(k); end
    unl = fullfile(path, "unloader.sce");
    ok = %t;
    if isfile(unl) then
        try, exec(unl, -1); catch, ok = %f; end
    end
    if ok then mprintf("  unloaded %s\n", name); end
endfunction

// ---- install: resolve source -> (clone/pull) -> build -> register -> load --
function ok = tbxInstall(name, source)
    cfg = tbx_cfg();
    if argn(2) < 2 then source = "auto"; end
    [path, src] = tbx_resolve(name, source);
    mprintf("tbxInstall %s (source=%s)\n", name, src);
    if src == "remote" then
        if isdir(fullfile(path, ".git")) then
            mprintf("  git pull...\n"); tbx_sh("cd " + path + " && git pull --ff-only");
        else
            mprintf("  git clone...\n");
            [ok, o] = tbx_sh("git clone " + cfg.glbase + name + ".git " + path);
            if ~ok then  // GitLab failed, try GitHub
                [ok, o] = tbx_sh("git clone " + cfg.ghbase + name + ".git " + path);
            end
            if ~ok then mprintf("  clone FAILED for %s\n", name); ok = %f; return; end
        end
    end
    if ~isfile(fullfile(path, "loader.sce")) then
        mprintf("  building...\n");
        if ~tbx_build(path) then mprintf("  build FAILED for %s\n", name); ok = %f; return; end
    end
    // register (autoload = 1)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then
        M.name = [M.name; name]; M.path = [M.path; path];
        M.source = [M.source; src]; M.autoload = [M.autoload; 1];
    else
        M.path(k) = path; M.source(k) = src; M.autoload(k) = 1;
    end
    tbx_manifest_write(M);
    // load now — exec the loader INLINE (not via tbxLoad). A toolbox macro library
    // only propagates ONE call-level up on return, so it must be exec'd at this depth
    // (tbxInstall is called directly by the user/GUI), not one level deeper in tbxLoad.
    ldr = fullfile(path, "loader.sce");
    ok = %t; try, exec(ldr, -1); catch, ok = %f; end
    if ok then mprintf("  loaded  %s\n", name); else mprintf("  load FAILED %s\n", name); end
endfunction

function ok = tbxUpdate(name)
    if argn(1) < 1 then  // update all (each tbxUpdate call is itself one level deep)
        M = tbx_manifest_read(); ok = %t;
        nm = M.name;
        for i = 1:size(nm, "*"), ok = tbxUpdate(nm(i)) & ok; end
        return;
    end
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then mprintf("tbxUpdate: %s not installed\n", name); ok = %f; return; end
    path = M.path(k);
    mprintf("tbxUpdate %s\n", name);
    if isdir(fullfile(path, ".git")) then tbx_sh("cd " + path + " && git pull --ff-only"); end
    ok = tbx_build(path);
    ldr = fullfile(path, "loader.sce");        // inline exec (see tbxInstall note)
    try, exec(ldr, -1); catch, ok = %f; end
endfunction

function ok = tbxRemove(name)
    M = tbx_manifest_read(); k = tbx_find(M, name);
    if k == 0 then mprintf("tbxRemove: %s not installed\n", name); ok = %f; return; end
    tbxUnload(name);
    // delete only remote clones (never the user's SciLabProjects working tree)
    if M.source(k) == "remote" & isdir(M.path(k)) then
        tbx_sh("rm -rf " + M.path(k));
    end
    keep = (1:size(M.name, "*")) <> k;
    M.name = M.name(keep); M.path = M.path(keep);
    M.source = M.source(keep); M.autoload = M.autoload(keep);
    tbx_manifest_write(M);
    mprintf("  removed %s\n", name); ok = %t;
endfunction

function tbxList()
    M = tbx_manifest_read();
    if isempty(M.name) then mprintf("No toolboxes installed. Run tbxManager() to add some.\n"); return; end
    mprintf("\n %-18s %-8s %-8s %s\n", "TOOLBOX", "SOURCE", "AUTOLOAD", "PATH");
    mprintf(" %s\n", part("-", 1:72));
    for i = 1:size(M.name, "*")
        mprintf(" %-18s %-8s %-8s %s\n", M.name(i), M.source(i), ..
                string(M.autoload(i)==1), M.path(i));
    end
    mprintf("\n");
endfunction

// Pure: return the loader.sce paths of all autoload=1 toolboxes (no loading).
// .scilab execs these at its OWN top level so macro libraries reach global scope —
// a macro library loaded inside any function does NOT propagate reliably to base.
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

function tbxAutoloadAll()
    // Called by .scilab (and usable interactively). Execs each loader INLINE here —
    // one call-level below base — so the macro libraries reach the global scope.
    M = tbx_manifest_read();
    n = 0;
    for i = 1:size(M.name, "*")
        if M.autoload(i) == 1 then
            ldr = fullfile(M.path(i), "loader.sce");
            if isfile(ldr) then
                ok = %t; try, exec(ldr, -1); catch, ok = %f; end
                if ok then n = n + 1; mprintf("  loaded  %s\n", M.name(i));
                else mprintf("  FAILED  %s\n", M.name(i)); end
            else
                mprintf("  skip    %s (not built)\n", M.name(i));
            end
        end
    end
    if n > 0 then mprintf("[toolbox-manager] autoloaded %d toolbox(es).\n", n); end
endfunction

// ---- catalog of installable toolboxes (auto-discovered + verified flag) ----
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

// tbxManager() GUI is defined in tbxmgr_gui.sce (phase 3); provide a stub fallback
if ~isdef("tbxManager") then
    function tbxManager()
        mprintf("tbxManager GUI not loaded. Use tbxList / tbxInstall(""name"") for now.\n");
    endfunction
end
