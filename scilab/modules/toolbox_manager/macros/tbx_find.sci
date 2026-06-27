function i = tbx_find(M, name)
    i = 0;
    for k = 1:size(M.name, "*")
        if M.name(k) == name then i = k; return; end
    end
endfunction
