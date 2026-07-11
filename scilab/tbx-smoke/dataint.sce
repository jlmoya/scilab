// dataint smoke: GUI-ONLY at every entry point — audited all 7 public macros (DI_read,
// DI_readcsv, DI_readxls, DI_writecsv, DI_writedat, DI_show all gate on uigetfile/uiputfile/
// getvalue/messagebox before doing any computation; DI_getpath is the sole exception but is
// a trivial path helper, not the toolbox's function) plus the 3 internal readers (even
// DI_int_readcsv/DI_int_readxls gate the actual csvRead()/parsing behind a modal getvalue()
// parameter dialog). No non-interactive computational path exists, per the task's sanctioned
// exception for GUI-oriented toolboxes: load-verified via exists()==1 on the full public API.
smoke_ok = (exists("DI_getpath") == 1) & (exists("DI_read") == 1) ...
    & (exists("DI_readcsv") == 1) & (exists("DI_readxls") == 1) ...
    & (exists("DI_show") == 1) & (exists("DI_writecsv") == 1) ...
    & (exists("DI_writedat") == 1);
