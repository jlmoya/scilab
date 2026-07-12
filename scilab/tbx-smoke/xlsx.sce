// xlsx smoke: native %xlsxWrite/%xlsxRead gateway functions (sci_gateway/cpp/loader.sce
// registers %xlsxWrite/%xlsxRead/%xlsxMetadata/%xlsxSheet/%isXlsxFile via addinter)
// reached through their thin macros/xlsxWrite.sci and macros/xlsxRead.sci wrappers --
// neither wrapper has a try/catch that could mask a native failure (the only try/catch
// anywhere under macros/ is an unrelated datetime-format-detection helper in
// detectFormatDatetime.sci, which never calls the xlsx gateway at all).
//
// Ground truth: a 2x3 numeric matrix round-tripped through a temp .xlsx file, exactly
// mirroring the toolbox's own tests/unit_tests/xlsx_table_timeseries.tst
// (xlsxWrite(expected, filename); computed = xlsxRead(filename);
// assert_checkequal(computed, expected); with the identical expected = [1 2 3; 4 5 6]).
data = [1 2 3; 4 5 6];
fname = TMPDIR + "/xlsx_smoke.xlsx";
if isfile(fname) then deletefile(fname); end
xlsxWrite(data, fname);
result = xlsxRead(fname);
deletefile(fname);
smoke_ok = isequal(result, data);
