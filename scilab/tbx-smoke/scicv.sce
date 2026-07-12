// scicv smoke: native OpenCV Mat construction/introspection (new_Mat,
// Mat_rows_get, Mat_cols_get, Mat_channels, Mat_empty -- all raw SWIG gateway
// functions with no macro wrapper, sci_gateway/c/scicv_wrap.cxx) on an
// in-memory constant-filled image. No file I/O (imread needs a file, so this
// avoids shipping a fixture), no windows (never imshow). Mirrors the
// toolbox's own tests/unit_tests/Mat.tst check_img() helper.
//
// Ground truth: a Mat filled with a constant scalar must read back that
// exact constant at every pixel/channel -- a no-op/broken native
// constructor would not reproduce the requested dimensions and fill value.
scicv_Init();
rows = 4; cols = 5; channels = 3; val = 100;
img = new_Mat(rows, cols, CV_8UC3, [val val val]);
ok1 = (typeof(img) == "Mat") & ~Mat_empty(img);
ok2 = (Mat_rows_get(img) == rows) & (Mat_cols_get(img) == cols) & (Mat_channels(img) == channels);
data = double(img(:, :));
expected = matrix(ones(1, rows*cols*channels) * val, [rows, cols, channels]);
ok3 = isequal(data, expected);
delete_Mat(img);
smoke_ok = ok1 & ok2 & ok3;
