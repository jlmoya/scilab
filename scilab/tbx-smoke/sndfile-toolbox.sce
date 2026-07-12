// sndfile-toolbox smoke: native sfwrite/sfread gateway functions (registered directly
// via addinter, no macro wrapper exists for either -- macros/lib lists only
// sfgettlbxpath -- see sci_gateway/c/loader.sce's list_functions) on a short generated
// stereo waveform, deliberately longer than libsndfile's BUFFER_FRAMES=8192 multi-buffer
// threshold: this fork's own README documents a real de-interleave bug that ONLY
// manifested above that threshold (now fixed; the toolbox's own tests/unit_tests/
// sndfile.tst round-trip never exercises it at 10 samples -- only
// tests/nonreg_tests/bug_35.tst does, with rand(10000,3)), so this smoke's length
// deliberately mirrors that regression test rather than the shorter primary .tst.
//
// Ground truth: two explicit known-amplitude samples (0.5, -0.3) planted at row 1,
// against small-magnitude filler elsewhere -- exact by construction; round-trip
// tolerance (1e-3) is generous versus this fork's own documented wav-int16 codec error
// (~3e-5).
rand("seed", 0);
n = 9000;
a = 0.2*rand(n, 2) - 0.1;
a(1, 1) = 0.5; a(1, 2) = -0.3;
fname = TMPDIR + "/sndfile_smoke.wav";
if isfile(fname) then mdelete(fname); end
sfwrite(fname, a, 22050, 'wav-int16');
[data, samplerate, fmt] = sfread(fname);
mdelete(fname);
smoke_ok = (size(data, 1) == n) & (size(data, 2) == 2) & (samplerate == 22050) ..
    & (fmt == 'wav-int16') & (max(abs(data(:) - a(:))) < 1e-3);
