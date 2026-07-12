// guibuilder smoke: guibuilder is 0 tests, 0 demos, and (per the wave-2 brief)
// inherently a GUI-construction toolbox -- almost every macro under macros/ either
// calls figure()/uicontrol() directly (guialignements.sci and its callback siblings:
// bottom_callback.sci, left_callback.sci, etc.) or mutates a live uicontrol/axes handle
// passed in via a `handles` struct (e.g. same_width_callback.sci calls
// get(handles.mylist,'value') and writes handles.all_handles(cnt).position;
// guipropsedit.sci calls get(a,'BackgroundColor') on a real graphic handle;
// guiaxistight.sci reads h.children.children.data off a live axes handle) -- none of
// those are runnable without a live GUI object already on screen, so none qualify as a
// GUI-free smoke target.
//
// All 50 macros/*.sci files were scanned for GUI-call keywords (figure(/uicontrol(/
// x_dialog/messagebox/toolbar(/delmenu(/waitbar(/uimenu(/uigetfile/uiputfile/xclick/
// gcf(/drawlater/drawnow); the ~21 files with no direct hit were then read individually
// to rule out indirect GUI-handle dependence (the same_width_callback/guipropsedit
// pattern above). Exactly one is genuinely standalone and string/number-only:
// guicheckprops.sci (its dispatcher plus 18 private guicheck* validators) -- a
// property-value parser/sanitizer used by the GUI property editor, but itself calling
// only evstr/size/strcmp/strstr/round/isempty/error, no GUI functions at all.
//
// This smoke exercises the dispatcher on two datatype cases straight from its own case
// table (macros/guicheckprops.sci): datatype=1 (color, must evstr to a 1x3 vector) and
// datatype=13 (position, must evstr to a 1x4 vector), each with one correctly-shaped
// input (returns the parsed numeric vector) and one wrongly-shaped input (returns the
// literal string 'err' via its internal error()/catch) -- a broken/no-op implementation
// would not reproduce this shape-dependent branching. Traced by hand against the source
// and confirmed by running it before trusting it.
c_ok  = guicheckprops("[0.2, 0.3, 0.4]", 1);
c_bad = guicheckprops("[0.2, 0.3]", 1);
p_ok  = guicheckprops("[10, 20, 100, 50]", 13);
p_bad = guicheckprops("[10, 20, 100]", 13);
smoke_ok = and(abs(c_ok - [0.2 0.3 0.4]) < 1e-10) & (c_bad == 'err') & ..
    and(abs(p_ok - [10 20 100 50]) < 1e-10) & (p_bad == 'err');
