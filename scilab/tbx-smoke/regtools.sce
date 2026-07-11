// regtools smoke: full-factorial design is the batch (non-GUI) core — linregr/nlinregr
// are interactive-only (guimaker-gated, see etc/regtools.start) and are covered by the
// tbxVerify load-time check + their own call-time guard, not here.
// fullfact([2 3]) size ground-truthed from macros/fullfact.sci's own algorithm:
// r=prod(levels)=2*3=6 rows, c=length(levels)=2 columns -> 6x2, matching its docstring
// examples (fullfact([2 2 2]) -> 2^3 x 3).
// Dependency: fullfact.sci calls apifun_checktype (macros/fullfact.sci), so apifun (a
// sibling toolbox, already verified) must be loaded first — same pattern as
// tbx-smoke/fmincont.sce loading sci-ipopt.
exec(fullfile(cfg.projects, "apifun", "loader.sce"), -1);
d = fullfact([2 3]);
smoke_ok = (size(d, 1) == 6 & size(d, 2) == 2);
