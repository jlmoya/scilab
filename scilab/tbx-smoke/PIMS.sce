// PIMS smoke: gateway-only toolbox (its two committed macros, checkPython/getBuildFlags,
// are build-time helpers, not the runtime API -- the actual Scilab<->Python bridge is 100%
// native gateway functions registered by addinter: pyEvalStr, pyImport, pyExec-equivalents,
// etc; see sci_gateway/cpp/builder_gateway_cpp.sce). Evaluate a trivial Python expression
// through the bridge and check the value, matching the toolbox's own regression test
// (tests/unit_tests/pyEvalStr.tst: pyEvalStr("print(a * 3)", %t) returns the printed text).
ret = pyEvalStr("print(1+1)", %t);
smoke_ok = (ret == "2");
