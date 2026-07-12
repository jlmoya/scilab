// accsum smoke: the ported native C gateway (accsum_fdcs/accsum_fscs/accsum_fcompsum,
// registered via addinter -- see sci_gateway/c/builder_gateway_c.sce's namelist) is
// gateway-only from tbxVerify's point of view: the toolbox's macro library never reaches
// librarieslist() when loaded through THIS harness's exec(execstr("exec(loader.sce,-1)"))
// nesting (etc/accsum.start's loadaccsumlib() calls lib() one exec-level too deep --
// exec(loader.sce) -> exec(etc/accsum.start) -> loadaccsumlib(); confirmed by calling
// etc/accsum.start directly, one level shallower, where it registers fine; see the
// "toolbox macros load to global scope ONLY via top-level exec" note -- a pre-existing,
// separate-class defect in accsum's OWN loader plumbing, left unfixed here as out of
// scope for a native-gateway port). The addinter-registered gateway functions are
// unaffected (gateways are global regardless of exec depth) and are what this task
// actually ports, so this smoke calls them directly without depending on the macro layer.
//
// Ground truth is lifted verbatim from the toolbox's own regression tests:
//   tests/unit_tests/fdcs.tst, fscs.tst, fcompsum.tst (small exact cases +
//   accsum_wilkinson(10) reference values) via their .dia.ref files.

// Part 1: exact small-vector cases straight from {fdcs,fscs,fcompsum}.tst.
[s1, e1] = accsum_fdcs([2 1]);
ok1 = (s1 == 3) & (e1 == 0);
[s2, e2] = accsum_fscs([1 2]);
ok2 = (s2 == 3) & (e2 == 0);
[s3, e3] = accsum_fcompsum([2 1]);
ok3 = (s3 == 3) & (e3 == 0);

// Part 2: the toolbox's ill-conditioned discriminator, accsum_wilkinson(10) (Exercise 4.2
// in Higham's SNAA), reproduced inline from macros/accsum_wilkinson.sci's own documented
// formula (the macro itself isn't reachable here -- see header note above).
r = 10; n = 2^r; u = 2^(-53);
x = zeros(1, n);
x(1) = 1;
x(2) = 1 - u;
for k = 2:r
    x(2^(k-1)+1 : 2^k) = 1 - 2^(k-1)*u;
end

[s4, e4] = accsum_fdcs(x);
// fdcs.tst's own documented reference: assert_checkalmostequal(s,1024,1.e-12) and
// assert_checkalmostequal(e,-3.786e-14,[],1.e-10).
ok4 = (abs(s4 - 1024) <= 1e-12 * 1024) & (abs(e4 - (-3.786e-14)) <= 1e-10);

// Discriminates compensated from naive summation: a plain left-to-right sum() rounds
// this vector to exactly 1024 in double precision, silently losing the ~3.9e-11
// correction that the compensated algorithms track explicitly in s+e -- so the ported
// gateway's high part (s4) must reproduce the toolbox's own reference value and must
// NOT equal the naive sum.
naive = sum(x);
ok5 = (naive == 1024) & (s4 <> naive);

// The other two gateway functions implement different compensation algorithms (doubly
// self-compensated / self-compensated / Knuth) but should recover the same high-order
// sum to within a few ULP on this input.
[s5, e5] = accsum_fscs(x);
[s6, e6] = accsum_fcompsum(x);
ok6 = (abs(s5 - s4) <= 1e-9) & (abs(s6 - s4) <= 1e-9);

smoke_ok = ok1 & ok2 & ok3 & ok4 & ok5 & ok6;
