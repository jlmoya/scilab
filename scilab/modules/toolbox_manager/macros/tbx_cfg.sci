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
