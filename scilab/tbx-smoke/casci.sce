// casci smoke: casci's own readme.txt names no single flagship -- it's P. Castagliola's
// (Universite de Nantes) grab-bag probability & statistics library, ~150 bare-named macros,
// zero apifun dependency (confirmed by grep: no file calls apifun_*). Its DESCRIPTION /
// DESCRIPTION-FUNCTIONS files and tests/ directory are stale, vestigial ATOMS
// "toolbox_skeleton" boilerplate literally left over from the scaffold generator (the tests
// exercise c_sum/scilab_sum/fortran_sum, unrelated to any real casci function) so there is no
// shipped ground truth to lift verbatim; picked cp() -- the process-capability index, the
// closest thing to a namesake specialty for an SPC/quality-control researcher's toolbox -- and
// it is self-contained (calls only casci's own standev()).
//
// Ground truth hand-derived from standev.sci's own definition (sample stdev, N-1
// denominator), independent of casci's cp() implementation:
// X=(1:5)', mean=3, sum of squared deviations=10, n-1=4, sd=sqrt(2.5)=1.5811388300841898;
// L=0, U=10 -> Cp=(U-L)/(6*sd)=1.0540925533894598.
X = (1:5)';
Cp = cp(X, 0, 10);
smoke_ok = (abs(Cp - 1.0540925533894598) < 1e-9);
