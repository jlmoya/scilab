// lsf_toolbox smoke: GUI-ONLY at every entry point — audited the full macro set: leastsqr()
// (the sole registered entry point) builds a uicontrol-table GUI figure and nothing else;
// every fitting callback (LiniarBtn_Callback, PolyFitBtn_Callback, ExpBtn_callback,
// PowerFnBtn_callback) reads its data via Data(handles), which needs a live
// handles.DataTabel uicontrol, and every one of them calls plot()/xlabel()/legend() directly
// mid-computation (via LabelGraph) — there is no separable pure-math core (the least-squares
// solve is inlined in the callback, not exposed as its own callable macro), and graphics are
// not available in this headless verifier. Falls back to the task's sanctioned GUI-only
// exception: load-verified via exists()==1 on the entry point + its core fitting callbacks.
smoke_ok = (exists("leastsqr") == 1) & (exists("LiniarBtn_Callback") == 1) ...
    & (exists("PolyFitBtn_Callback") == 1) & (exists("ExpBtn_callback") == 1) ...
    & (exists("PowerFnBtn_callback") == 1);
