// helptbx smoke: helptbx_getpath() -- the module-root resolver every other
// helptbx entry point builds on (helptbx_helpupdate/_iscontentupdte/_updtifneeded
// all locate the module through it).
//
// Ground truth is structural rather than numeric: the function computes its
// answer as fullpath(fullfile(fileparts(get_function_path("helptbx_getpath")),
// "..")), so a correct result is necessarily a real directory that contains the
// very source file the lookup started from. Asserting both halves discriminates
// a working resolver from the ways this actually breaks: get_function_path
// returning "" for an unregistered library (path would be a bare ".."), or the
// fileparts/".." arithmetic landing one level too high or too low -- each of
// which yields a string that still looks like a path but fails one of these two
// checks.
p = helptbx_getpath();

ok1 = (type(p) == 10) & (size(p, "*") == 1) & (p <> "");
ok2 = isdir(p);
ok3 = isfile(fullfile(p, "macros", "helptbx_getpath.sci"));

smoke_ok = ok1 & ok2 & ok3;
