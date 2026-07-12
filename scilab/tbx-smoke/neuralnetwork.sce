// neuralnetwork smoke: ann_PERCEPTRON train + ann_PERCEPTRON_run eval, the toolbox's own
// simplest documented train/eval pair (verbatim from ann_PERCEPTRON.sci's own Examples
// section). No apifun/cross-toolbox dependency -- zero apifun_* calls anywhere in this
// toolbox (confirmed by grep).
//
// P/T is the classic 2-input AND gate (linearly separable), so the Perceptron Convergence
// Theorem guarantees this online update rule (w += e*p'; b += e) converges in finite epochs
// to a weight vector that classifies all 4 training points correctly, regardless of the
// random initial weights -- the fixed seed is only for reproducibility of the exact epoch
// count, not a precondition for correctness. This is a structural/stochastic case (random
// init) with an exactly-known outcome (guaranteed-converged classifier), not a loose-
// tolerance one.
rand("seed", 0);
P = [0 0 1 1; 0 1 0 1];
T = [0 0 0 1];
[w, b] = ann_PERCEPTRON(P, T);
y = ann_PERCEPTRON_run(P, w, b);
smoke_ok = isequal(y, T);
