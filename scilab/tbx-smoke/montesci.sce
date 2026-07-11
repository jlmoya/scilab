// montesci smoke: its sole registered macro montesci() builds the full GUI app (windowed),
// but macros/montesci.sce (which montesci() execs) sources macros/montesci_helper.sce as its
// very first step, BEFORE any window is built — a handful of pure, deterministic helper
// functions. Exercise that real bootstrap file directly (same file, same exec pattern the
// toolbox itself uses) and stop short of the GUI part. decimate(x,number) strided-samples a
// vector; expected output hand-derived from its own definition (macros/montesci_helper.sce).
exec(fullfile(path, "macros", "montesci_helper.sce"), -1);
x = (1:10)';
x_dec = decimate(x, 5);
smoke_ok = isequal(x_dec, [1; 3; 5; 7; 9]);
