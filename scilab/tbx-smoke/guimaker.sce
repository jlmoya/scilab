// guimaker smoke: gui2builder(), the toolbox's figure -> guibuilder-source
// generator (macros/gui2builder.sci).
//
// Why this entry point and not guimaker() itself: guimaker(), menumaker() and
// inputui() all build a live uicontrol window and then block waiting for the
// user -- guimaker() with no arguments starts a demo GUI (guimaker.sci:157) and
// raises "the window was killed by the user" when it is torn down. None of that
// is assertable in a headless throwaway CLI. gui2builder is the toolbox's one
// substantial NON-interactive routine: it walks an existing figure's children
// and emits source text, so it exercises real logic (properties2code) against a
// verifiable result.
//
// Called with ONE argument on purpose. With two, gui2builder writes a file and,
// if that file exists, pops a modal messagebox to ask about overwriting
// (gui2builder.sci:127) -- which would hang the sweep. The single-argument form
// returns the generated code instead (gui2builder.sci:141) and touches no disk.
//
// The discriminator is that the generated source must mention BOTH the emitted
// uicontrol constructor and the specific properties this figure was given: the
// tag becomes the handle name via sprintf('handles.%s=uicontrol(f,', Tag)
// (gui2builder.sci:105), and the button's label has to survive into the text.
// Generic or empty output -- the shape a stubbed/broken walker produces -- fails
// on the string checks even though it would pass a mere "returned something".
f = figure("visible", "off");
uicontrol(f, "style", "pushbutton", "string", "SmokeBtn", ..
             "tag", "smokebtn", "position", [10 10 90 30]);

code = gui2builder(f);
delete(f);

ok1 = (type(code) == 10) & (size(code, "*") > 1);
ok2 = (grep(code, "uicontrol") <> []);
ok3 = (grep(code, "smokebtn") <> []);
ok4 = (grep(code, "SmokeBtn") <> []);

smoke_ok = ok1 & ok2 & ok3 & ok4;
