function tbx_gui_preset(which)
    lb = findobj("tag", "tbxmgr_lb");
    items = lb.string; C = tbxCatalog();
    vnames = C.name(find(C.verified));
    for i = 1:size(items, "*")
        rest = part(items(i), 5:length(items(i)));
        on = %f;
        if which == "verified" then on = or(tbx_gui_itemname(items(i)) == vnames); end
        if on then items(i) = "[x] " + rest; else items(i) = "[ ] " + rest; end
    end
    lb.string = items;
endfunction
