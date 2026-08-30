function [ok, out] = tbx_sh(cmd)
    // host() returns [stat, stdout, stderr]; unix_g() returned stdout FIRST, so a
    // one-output swap would capture the exit status rather than the text that the
    // grep below searches.
    //
    // unix_g() also printed stderr of its own accord whenever it was called with
    // fewer than two outputs. Toolbox builds run through here and their error
    // output must stay visible -- build output is never to be hidden -- so it is
    // printed explicitly now that host() no longer does it for us.
    [stat, out, err] = host(cmd + " && echo __TBX_OK__");
    if ~isempty(err) & or(err <> "") then
        disp(err);
    end
    ok  = ~isempty(grep(out, "__TBX_OK__"));
endfunction
