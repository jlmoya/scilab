function cfg = tbx_cfg()
    cfg = struct();
    cfg.home     = getenv("HOME");
    cfg.projects = fullfile(cfg.home, "Projects", "SciLabProjects");
    cfg.tbxdir   = fullfile(SCIHOME, "toolboxes");          // remote clones live here
    cfg.manifest = fullfile(SCIHOME, "installed_toolboxes.tbx");
    // Clone sources, tried IN THIS ORDER by tbxInstall:
    //   glbase    our fork — carries the macOS/arm64 fixes, so it must win
    //   forgebase gitlab.com/scilab/forge — the CANONICAL upstream. Without this
    //             any toolbox we have not forked was simply uninstallable:
    //             tbxInstall fell straight through to the GitHub fork namespace
    //             and reported "Repository not found", which is how helptbx
    //             (a declared dependency of distfun and stixbox) stayed missing
    //             even though it sits on the forge.
    //   ghbase    GitHub mirror of our fork, last resort
    cfg.glbase   = "git@gitlab.com:jlmoya/";                 // SSH (works on this Mac)
    cfg.forgebase = "https://gitlab.com/scilab/forge/";
    cfg.ghbase   = "https://github.com/jlmoya/";
    // native-build env (needed only when BUILDING native toolboxes)
    cfg.cpath    = "/opt/homebrew/opt/gettext/include";
    cfg.libpath  = "/opt/homebrew/opt/gettext/lib:/opt/homebrew/lib/gcc/current/gcc/aarch64-apple-darwin25/16:/opt/homebrew/lib/gcc/current";
    // Verified-on-macOS set: pre-ticked in tbxManager and shown there as
    // "(verified)" vs "(build-only)". This is a RECORD of tbxVerify results, not
    // a live check -- the GUI never runs tbxVerify itself (that needs a throwaway
    // session per toolbox; see tbx-verify-all.sh). So a toolbox added after the
    // last sweep reads as "(build-only)" purely because nobody re-ran the sweep,
    // which is what happened to the three appended below: guimaker and
    // gui2bitmap were installed after this list was last refreshed, and
    // sciFinance is newer still. All three were re-verified on 2026-08-09
    // (PASS, delta=1, smoke=OK) before being added here.
    // To extend: ./tbx-verify-all.sh <names> and paste the PASS names it prints.
    cfg.verified = ["sciDatabase" "parquet" "xlsx" "libsvm" "guibuilder" "scicv" ..
                    "cgal" "sndfile-toolbox" "sciSymPy" "sciTorch" "sciQuantLib" ..
                    "PIMS" "financial" "nan" "quapro" "json" "specfun" "distfun" ..
                    "scidoe" "stixbox" "lowdisc" ..
                    "apifun" "cma-es" "dataint" "fmincont" "FOSSEE-Optimization-toolbox" ..
                    "grocer" "intprbs" "lsf_toolbox" "montesci" "nisp" "regtools" ..
                    "krisp" "csv-readwrite" "arfit" ..
                    "anova" "casci" "condnb" "conint" "dbldbl" "hypt" "makematrix" ..
                    "neuralnetwork" "number" "ortpol" ..
                    "pso-toolbox" "sci_gsl" "sci-ipopt" "accsum" "scimax" ..
                    "gui2bitmap" "guimaker" "sciFinance" "helptbx"];
    if ~isdir(cfg.tbxdir) then mkdir(cfg.tbxdir); end
endfunction
