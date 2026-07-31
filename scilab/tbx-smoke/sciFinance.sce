// sciFinance smoke: the native QuantLib business-day gateway fin_isbusday,
// called DIRECTLY rather than through the isbusday() macro -- isbusday does the
// datetime/mlist unwrapping and its own validation, so exercising it would let a
// dead gateway hide behind a live wrapper.
//
// fin_isbusday(ymd, cal) takes an Nx3 [year month day] matrix and a calendar
// name ("TARGET" | "US" | "UK" | "None"), per macros/isbusday.sci:71.
//
// Ground truth, derived here rather than copied: 2026-07-13 is a Monday (the
// anchor stated in isbusday's own help). Counting forward from it,
// 2026-07-13 -> 2026-11-26 is 18+31+30+31+26 = 136 days, and 136 mod 7 = 3, so
// 2026-11-26 is a Thursday; November 2026's Thursdays are the 5th, 12th, 19th
// and 26th, making the 26th the FOURTH Thursday -- US Thanksgiving. 2026-07-11
// is two days before the Monday anchor, so a Saturday.
//
// The discriminating case is the same Thanksgiving date under two calendars:
// a real QuantLib calendar lookup answers %f for "US" and %t for "TARGET"
// (an ordinary European Thursday), whereas a gateway that ignores its calendar
// argument -- or one that only implements the weekend rule and no holidays --
// returns the SAME answer for both. Weekday/weekend cases alone cannot catch
// that, which is why they are not the whole test.
thanks = [2026 11 26];      // Thursday, US Thanksgiving
monday = [2026  7 13];      // ordinary Monday
satday = [2026  7 11];      // Saturday

us_thanks     = fin_isbusday(thanks, "US");
target_thanks = fin_isbusday(thanks, "TARGET");

ok1 = isequal(us_thanks, %f);          // US holiday   -> not a business day
ok2 = isequal(target_thanks, %t);      // plain Thursday in Europe -> business day
ok3 = isequal(fin_isbusday(monday, "TARGET"), %t);
ok4 = isequal(fin_isbusday(satday, "TARGET"), %f);   // weekend

// vectorised: one call, three dates, must agree elementwise with the above
v  = fin_isbusday([thanks ; monday ; satday], "US");
ok5 = isequal(size(v, "*"), 3) & isequal(v(2), %t) & isequal(v(3), %f) & isequal(v(1), %f);

smoke_ok = ok1 & ok2 & ok3 & ok4 & ok5;
