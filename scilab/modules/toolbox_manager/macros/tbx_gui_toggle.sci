function tbx_gui_toggle()
    lb = findobj("tag", "tbxmgr_lb");
    items = lb.string; v = lb.value;
    for j = 1:size(v, "*")
        i = v(j); s = items(i);
        if part(s, 1:4) == "[x] " then items(i) = "[ ] " + part(s, 5:length(s));
        else items(i) = "[x] " + part(s, 5:length(s)); end
    end
    lb.string = items;
endfunction
