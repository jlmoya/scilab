// gui2bitmap smoke: the "setScale"/"setBorders" preference commands.
//
// NOT the capture path, deliberately. gui2bitmap's actual capture is
// java.awt.Robot.createScreenCapture over the figure's screen rectangle
// (macros/gui2bitmap.sci:263-271) -- it photographs the physical display, so it
// needs a real on-screen window AND, on macOS, Screen Recording permission for
// the JVM. In this throwaway headless CLI it can only ever produce a blank or
// permission-denied image, so asserting on it would be theatre. The interactive
// capture stays a manual/GUI check; what IS mechanically verifiable is the rest
// of the toolbox's logic, which is what this covers.
//
// The setX commands return before any capture (gui2bitmap.sci:160-190) after
// validating their argument and persisting it through gui2bitmap_setpref into
// SCIHOME/gui2bitmap_preferences.xml. That file is the same one the capture path
// reads its scale/borders back from (gui2bitmap.sci:135-146), so this exercises
// the real round-trip contract, not a side alley. SCIHOME here is the sweep's
// throwaway scratch dir, so the starting state is clean by construction.
prefs = SCIHOME + filesep() + "gui2bitmap_preferences.xml";
if isfile(prefs) then mdelete(prefs); end

// --- positive: file is created from nothing, carrying the value we set ---
gui2bitmap("setScale", 150);
ok1 = isfile(prefs);
txt = strcat(mgetl(prefs), " ");
ok2 = (strindex(txt, "scale=""150""") <> []);

// --- positive: second command UPDATES the existing file, keeping the first ---
// value. This is the interesting half: setpref takes a different branch when the
// file already exists (xmlSetValues on a doc vs. writing a fresh document), and
// a broken update path either errors, clobbers scale=150, or drops borders.
//
// Asserted as a ROUND-TRIP -- read back exactly the way the capture path reads
// its settings (xmlGetValues + evstr, gui2bitmap.sci:135-146) -- rather than by
// matching the stored text. The stored form is whatever sci2exp() currently
// emits, and that is NOT stable across Scilab versions: the toolbox's own
// comment documents "[%T %F %T]" while Scilab 2027 writes "[%t,%f,%t]". Pinning
// the string would test the serialiser's spelling; the round-trip tests the
// contract that actually matters, and stays correct when the spelling changes.
gui2bitmap("setBorders", [%t %f %t]);
back = xmlGetValues("/gui2bitmap", ["scale" "borders"], prefs);
ok3 = isequal(evstr(back(2)), [%t %f %t]);   // new value survives the write
ok4 = (evstr(back(1)) == 150);               // and the earlier one is not clobbered

// --- negative: validation must actually reject bad input ---
// Discriminates a real function body from one that accepts anything: scale must
// be > 0, and borders must be a 3-element boolean vector.
ok5 = (execstr("gui2bitmap(""setScale"", 0)", "errcatch") <> 0);
ok6 = (execstr("gui2bitmap(""setBorders"", [%t %t])", "errcatch") <> 0);
ok7 = (execstr("gui2bitmap(""setScale"", ""not a number"")", "errcatch") <> 0);

smoke_ok = ok1 & ok2 & ok3 & ok4 & ok5 & ok6 & ok7;
