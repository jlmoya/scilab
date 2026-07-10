function [ok, bad] = tbx_arch_check(path)
    // Native Apple-Silicon gate. Scilab-2027 runs as a single-arch arm64 process; an x86_64-only
    // toolbox .dylib/.so cannot be dlopen'd into it (it would fail with a cryptic link error, or
    // push the user back to Rosetta). Scan the toolbox tree and report every native lib that lacks
    // an arm64 slice. Universal (x86_64+arm64) libs pass. Returns ok=%t + bad=[] when all clear.
    bad = [];
    ok = %t;
    if getos() <> "Darwin" then return; end   // macOS-only gate
    if ~isdir(path) then return; end
    q = """";  // one double-quote, for the shell command below
    cmd = "find " + q + path + q + " \( -name " + q + "*.dylib" + q + " -o -name " + q + "*.so" + q + ..
          " \) 2>/dev/null | while read f; do lipo -archs " + q + "$f" + q + ..
          " 2>/dev/null | grep -q arm64 || echo " + q + "$f" + q + "; done";
    out = unix_g(cmd);
    // keep only non-empty lines -> the offending libs
    if ~isempty(out) then
        out(out == "") = [];
    end
    if ~isempty(out) then
        bad = out;
        ok = %f;
    end
endfunction
