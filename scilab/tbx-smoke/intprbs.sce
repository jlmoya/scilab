// intprbs smoke: intprb_prodones (the "Product of Signed Ones" QMC test function),
// macros/intprb_prodones.sci — fully deterministic (no randomness), option codes per its
// own switch: 1=value, 3=variance, 5=name. Matches FINANCE-TOOLBOX-PORTING.md's port-time
// verification note ("prodones exact-integral=1, name + signed-ones eval correct").
// No apifun dependency for this particular function (verified by inspection of its source).
val  = intprb_prodones(1, 2, [0.7 0.3], [], 1);
varr = intprb_prodones(1, 2, [0.7 0.3], [], 3);
nm   = intprb_prodones(1, 2, [0.7 0.3], [], 5);
smoke_ok = (val == -1) & (varr == 1) & (nm == "Product of Signed Ones");
