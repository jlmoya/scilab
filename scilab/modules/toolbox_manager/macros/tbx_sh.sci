function [ok, out] = tbx_sh(cmd)
    out = unix_g(cmd + " && echo __TBX_OK__");
    ok  = ~isempty(grep(out, "__TBX_OK__"));
endfunction
