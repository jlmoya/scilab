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
