function nm = tbx_gui_itemname(s)
    t = part(s, 5:length(s));
    p = strindex(t, "  (");
    if ~isempty(p) then t = part(t, 1:p($)-1); end
    nm = stripblanks(t);
endfunction
