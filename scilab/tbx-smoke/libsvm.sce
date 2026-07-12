// libsvm smoke: native libsvm_svmtrain/libsvm_svmpredict gateway functions (->
// sci_svmtrain/sci_svmpredict, sci_gateway/c/loader.sce's addinter list) called
// directly -- no macro wraps either name (macros/lib's compiled index lists only 11
// helper macros; the .sci files under macros/help_files_sci/ sharing these names are
// used solely by help-doc generation and are never compiled into macros/lib), so this
// reaches native code unconditionally. Model handles are returned in-memory (a
// tlist); no file round-trip needed (confirmed at the C source level too --
// svmtrain.c's vestigial model_file_name parameter is never fopen'd).
//
// svmpredict's option-string parsing (svmpredict.c) is gated on exactly 4 input args
// and only recognizes '-b'; any other flag hits its "default:" case and bails via
// exit_with_help_predict() without producing valid outputs -- so this smoke calls
// libsvm_svmpredict with NO options string (3 args), matching the toolbox's own
// canonical example exactly, rather than the '-q' used on the train call (svmtrain.c
// does support '-q', confirmed separately).
//
// Ground truth: a hand-built, tightly-clustered, linearly separable 6-point/2-class
// set -- a hard-margin-feasible problem trained with a linear kernel and a large C
// must recover the training labels exactly when predicted back. Neither of libsvm's 2
// own unit tests exercises train/predict (both cover only libsvmread/libsvmwrite file
// I/O), so this is new native-path coverage rather than a lifted golden value; the
// call pattern (train then predict the same matrix back) mirrors the toolbox's own
// canonical example in macros/help_files_sci/libsvm_svmtrain.sci.
label_vector = [-1; -1; -1; 1; 1; 1];
instance_matrix = [-2 -2; -2 -1; -1 -2; 2 2; 2 1; 1 2];
model = libsvm_svmtrain(label_vector, instance_matrix, '-t 0 -c 10 -q');
[predicted_label, accuracy, decision_values] = libsvm_svmpredict(label_vector, instance_matrix, model);
smoke_ok = and(predicted_label == label_vector) & (accuracy(1) == 100);
