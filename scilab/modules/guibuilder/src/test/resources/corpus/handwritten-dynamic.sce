// Small hand-written GUI with two dynamic wrinkles.
//
// First, a status label whose vertical position is computed from a variable
// rather than written as a literal -- that locks the "position" property on
// an otherwise perfectly ordinary widget, and nothing else about it.
//
// Second, a row of shortcut buttons built in a loop: one uicontrol call in
// the source, but as many buttons at runtime as there are entries in
// "shortcuts", so no single call there is the widget. It must be carried
// through unchanged rather than modelled.

shortcuts = ["Open", "Save", "Print"];
rowY = 10;
buttonWidth = 70;

f = figure("figure_name", "Dynamic Toolbar Demo", "background", [8]);

okButton = uicontrol(f, "style", "pushbutton", "tag", "okButton", ...
    "string", "OK", "position", [10 200 80 25]);

gap = 20;
statusY = 160 + gap;
status = uicontrol(f, "style", "text", "tag", "status", ...
    "string", "Ready", "position", [10 statusY 200 20]);

for i = 1:size(shortcuts, "*")
    x = 10 + (i - 1) * (buttonWidth + 5);
    btn = uicontrol(f, "style", "pushbutton", ...
        "string", shortcuts(i), "position", [x rowY buttonWidth 22], ...
        "tag", msprintf("shortcut%d", i));
end

cancelButton = uicontrol(f, "style", "pushbutton", "tag", "cancelButton", ...
    "string", "Cancel", "position", [300 200 80 25]);
