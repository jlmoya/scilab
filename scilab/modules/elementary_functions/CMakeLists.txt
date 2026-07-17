# CMake build for the `elementary_functions` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — elementary_functions ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scielementary_functions-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). THREE .la's: libdummy-
# elementary_functions (5 TUs) + libscielementary_functions-algo (258 TUs:
# SLATEC + LINPACK + EISPACK + C + FORTRAN + CXX) + libscielementary_functions
# (66 gateway TUs).
#
# libdummy's Makefile.am intent was "compile hqror2/comqr3/pade/unsfdcopy/
# icopy at -O0", but its handwritten rules are DEAD in the configured
# build: they target root-level `libdummy_..._la-<f>.lo` names while
# subdir-objects places the real objects at src/**; automake's generated
# rules built all five at the standard -O2 (baseline ground truth:
# unprefixed src/fortran objects, DWARF producer `-g1 -O2 -fwrapv`, and
# DW_AT_APPLE_optimized on the C TU). We compile them at -O2 like the
# baseline did — no override.
# dtensbs.f is DIFFERENT: its `if IS_MACOSX` rule keys on the REAL
# src/fortran/slatec/dtensbs.lo path, so it IS live (producer
# `-g1 -O2 -O0 -fwrapv`) — mirrored below, known to flagfacts_check.
# EXTRA_INCLUDES (libscielementary_functions_la_CPPFLAGS): console,
# threads, string (libdummy's own include pair is a subset of the
# helper base).

# --- per-TU override, verbatim from Makefile.am (`if IS_MACOSX` / "enforce
# -O0 for some file due to macOS crash"): -O0 APPENDED after the global -O2
# (last-wins), the colnew.f class. flagfacts_check.py FILE_EXPECTED_OVERRIDES
# carries dtensbs.f -> opt=O0.
set_source_files_properties(src/fortran/slatec/dtensbs.f PROPERTIES COMPILE_OPTIONS "-O0")

scilab_object_module(elementary_functions
  SOURCES
    # libdummy-elementary_functions.la (5 TUs)
    src/fortran/eispack/hqror2.f
    src/fortran/comqr3.f
    src/fortran/linpack/pade.f
    src/c/unsfdcopy.c
    src/fortran/linpack/icopy.f
    # libscielementary_functions-algo.la (258 TUs)
    src/fortran/slatec/zwrsk.f
    src/fortran/slatec/d9b0mp.f
    src/fortran/slatec/d9b1mp.f
    src/fortran/slatec/d9knus.f
    src/fortran/slatec/d9lgmc.f
    src/fortran/slatec/dasyik.f
    src/fortran/slatec/dasyjy.f
    src/fortran/slatec/dbdiff.f
    src/fortran/slatec/dbesi0.f
    src/fortran/slatec/dbesi1.f
    src/fortran/slatec/dbesi.f
    src/fortran/slatec/dbesj0.f
    src/fortran/slatec/dbesj1.f
    src/fortran/slatec/dbesj.f
    src/fortran/slatec/dbesk0.f
    src/fortran/slatec/dbesk1.f
    src/fortran/slatec/dbesk.f
    src/fortran/slatec/dbesy0.f
    src/fortran/slatec/dbesy1.f
    src/fortran/slatec/dbesy.f
    src/fortran/slatec/dbkias.f
    src/fortran/slatec/dbkisr.f
    src/fortran/slatec/dbsi0e.f
    src/fortran/slatec/dbsi1e.f
    src/fortran/slatec/dbsk0e.f
    src/fortran/slatec/dbsk1e.f
    src/fortran/slatec/dbskes.f
    src/fortran/slatec/dbskin.f
    src/fortran/slatec/dbsknu.f
    src/fortran/slatec/dbsynu.f
    src/fortran/slatec/dcsevl.f
    src/fortran/slatec/dexint.f
    src/fortran/slatec/dgamlm.f
    src/fortran/slatec/dgamln.f
    src/fortran/slatec/dgamma.f
    src/fortran/slatec/dgamrn.f
    src/fortran/slatec/dhkseq.f
    src/fortran/slatec/djairy.f
    src/fortran/slatec/dpsixn.f
    src/fortran/slatec/dxlegf.f
    src/fortran/slatec/dyairy.f
    src/fortran/slatec/fdump.f
    src/fortran/slatec/gamma.f
    src/fortran/slatec/initds.f
    src/fortran/slatec/j4save.f
    src/fortran/slatec/xercnt.f
    src/fortran/slatec/xermsg.f
    src/fortran/slatec/xerprn.f
    src/fortran/slatec/xersve.f
    src/fortran/slatec/xgetua.f
    src/fortran/slatec/zabs.f
    src/fortran/slatec/zacai.f
    src/fortran/slatec/zacon.f
    src/fortran/slatec/zairy.f
    src/fortran/slatec/zasyi.f
    src/fortran/slatec/zbesh.f
    src/fortran/slatec/zbesi.f
    src/fortran/slatec/zbesj.f
    src/fortran/slatec/zbesk.f
    src/fortran/slatec/zbesy.f
    src/fortran/slatec/zbinu.f
    src/fortran/slatec/zbknu.f
    src/fortran/slatec/zbuni.f
    src/fortran/slatec/zbunk.f
    src/fortran/slatec/zdiv.f
    src/fortran/slatec/zexp.f
    src/fortran/slatec/zkscl.f
    src/fortran/slatec/zlog.f
    src/fortran/slatec/zmlri.f
    src/fortran/slatec/zmlt.f
    src/fortran/slatec/zrati.f
    src/fortran/slatec/zs1s2.f
    src/fortran/slatec/zseri.f
    src/fortran/slatec/zshch.f
    src/fortran/slatec/zsqrt.f
    src/fortran/slatec/zuchk.f
    src/fortran/slatec/zunhj.f
    src/fortran/slatec/zuni1.f
    src/fortran/slatec/zuni2.f
    src/fortran/slatec/zunik.f
    src/fortran/slatec/zunk1.f
    src/fortran/slatec/zunk2.f
    src/fortran/slatec/zuoik.f
    src/fortran/slatec/dlngam.f
    src/fortran/slatec/balanc.f
    src/fortran/slatec/dtensbs.f
    src/fortran/slatec/pchim.f
    src/fortran/linpack/dgesl.f
    src/fortran/linpack/wpade.f
    src/fortran/linpack/util.f
    src/fortran/linpack/dgefa.f
    src/fortran/linpack/dgbfa.f
    src/fortran/linpack/dgeco.f
    src/fortran/linpack/dgedi.f
    src/fortran/linpack/dqrdc.f
    src/fortran/linpack/dqrsl.f
    src/fortran/linpack/dqrsm.f
    src/fortran/linpack/hhdml.f
    src/fortran/linpack/wgeco.f
    src/fortran/linpack/wgefa.f
    src/fortran/linpack/wgesl.f
    src/fortran/linpack/dpofa.f
    src/fortran/eispack/balbak.f
    src/c/finite.c
    src/c/vfinite.c
    src/c/cmp.c
    src/c/vceil.c
    src/c/xerhlt.c
    src/c/vfloor.c
    src/c/vfrexp.c
    src/c/scidcopy.c
    src/c/int2db.c
    src/c/rea2db.c
    src/c/basic_functions.c
    src/c/abs.c
    src/c/atan.c
    src/c/cos.c
    src/c/cosh.c
    src/c/sin.c
    src/c/sinh.c
    src/c/tan.c
    src/c/exp.c
    src/c/expm.c
    src/c/log.c
    src/c/sqrt.c
    src/c/cupro.c
    src/c/sign.c
    src/c/ceil.c
    src/c/eye.c
    src/c/dmsum.c
    src/c/dsearch.c
    src/c/floor.c
    src/c/mprod.c
    src/c/pythag.c
    src/c/matrix_generation.c
    src/c/vmul.c
    src/c/add_vector.c
    src/c/sub_vector.c
    src/c/issymmetric.c
    src/fortran/wasum.f
    src/fortran/dexpm1.f
    src/fortran/wwdiv.f
    src/fortran/wdotcr.f
    src/fortran/iset.f
    src/fortran/franck.f
    src/fortran/dwpowe.f
    src/fortran/rcsort.f
    src/fortran/wddiv.f
    src/fortran/lnblnk.f
    src/fortran/entier.f
    src/fortran/round.f
    src/fortran/cortr.f
    src/fortran/simple.f
    src/fortran/eispack_split.f
    src/fortran/wdrdiv.f
    src/fortran/imcopy.f
    src/fortran/cbal.f
    src/fortran/dsum.f
    src/fortran/urand.f
    src/fortran/intp.f
    src/fortran/watan.f
    src/fortran/wipowe.f
    src/fortran/wacos.f
    src/fortran/wdpow1.f
    src/fortran/dlgama.f
    src/fortran/exch.f
    src/fortran/wsign.f
    src/fortran/kronr.f
    src/fortran/wrscal.f
    src/fortran/dmmul1.f
    src/fortran/dwpow.f
    src/fortran/wmmul.f
    src/fortran/ddif.f
    src/fortran/wdiv.f
    src/fortran/wtan.f
    src/fortran/ddpow.f
    src/fortran/magic.f
    src/fortran/wcerr.f
    src/fortran/ivimp.f
    src/fortran/dadd.f
    src/fortran/isort.f
    src/fortran/hilber.f
    src/fortran/wdpowe.f
    src/fortran/wmprod.f
    src/fortran/wscal.f
    src/fortran/dlblks.f
    src/fortran/kronc.f
    src/fortran/ddrdiv.f
    src/fortran/dipow.f
    src/fortran/wshrsl.f
    src/fortran/wwrdiv.f
    src/fortran/coshin.f
    src/fortran/iwamax.f
    src/fortran/dipowe.f
    src/fortran/cusum.f
    src/fortran/d1mach.f
    src/fortran/wbdiag.f
    src/fortran/ddpow1.f
    src/fortran/vpythag.f
    src/fortran/isova0.f
    src/fortran/wswap.f
    src/fortran/wwpow1.f
    src/fortran/gdcp2i.f
    src/fortran/drdiv.f
    src/fortran/wmsum.f
    src/fortran/corth.f
    src/fortran/mtran.f
    src/fortran/wwpow.f
    src/fortran/dvmul.f
    src/fortran/wclmat.f
    src/fortran/dsort.f
    src/fortran/cerr.f
    src/fortran/rcopy.f
    src/fortran/wdpow.f
    src/fortran/orthes.f
    src/fortran/waxpy.f
    src/fortran/coef.f
    src/fortran/dwdiv.f
    src/fortran/arcosh.f
    src/fortran/wasin.f
    src/fortran/wexpm1.f
    src/fortran/ddpowe.f
    src/fortran/dmprod.f
    src/fortran/wwpowe.f
    src/fortran/wdotci.f
    src/fortran/dmcopy.f
    src/fortran/wexchn.f
    src/fortran/isoval.f
    src/fortran/dwrdiv.f
    src/fortran/wipow.f
    src/fortran/wlog.f
    src/fortran/infinity.f
    src/fortran/dwpow1.f
    src/fortran/wmul.f
    src/fortran/dad.f
    src/fortran/dset.f
    src/fortran/dtild.f
    src/fortran/i1mach.f
    src/fortran/dclmat.f
    src/fortran/ortran.f
    src/fortran/bdiag.f
    src/fortran/wvmul.f
    src/fortran/dmmul.f
    src/fortran/wsqrt.f
    src/fortran/rat.f
    src/fortran/old_pythag.f
    src/fortran/danints.f
    src/cpp/getmode.cpp
    src/cpp/cumsum.cpp
    src/cpp/cumprod.cpp
    src/cpp/diag.cpp
    src/cpp/gsort.cpp
    src/cpp/prod.cpp
    src/cpp/sum.cpp
    src/cpp/max.cpp
    src/cpp/min.cpp
    src/cpp/clean.cpp
    src/cpp/vander.cpp
    # libscielementary_functions.la (66 TUs)
    sci_gateway/c/sci_number_properties.c
    sci_gateway/c/sci_isequalbitwise.c
    sci_gateway/cpp/sci_isequal.cpp
    sci_gateway/cpp/elem_func_gw.cpp
    sci_gateway/cpp/sci_size.cpp
    sci_gateway/cpp/sci_eye.cpp
    sci_gateway/cpp/sci_rand.cpp
    sci_gateway/cpp/sci_prod.cpp
    sci_gateway/cpp/sci_sum.cpp
    sci_gateway/cpp/sci_zeros.cpp
    sci_gateway/cpp/sci_ones.cpp
    sci_gateway/cpp/sci_matrix.cpp
    sci_gateway/cpp/sci_abs.cpp
    sci_gateway/cpp/sci_acos.cpp
    sci_gateway/cpp/sci_acosh.cpp
    sci_gateway/cpp/sci_asin.cpp
    sci_gateway/cpp/sci_asinh.cpp
    sci_gateway/cpp/sci_atan.cpp
    sci_gateway/cpp/sci_atanh.cpp
    sci_gateway/cpp/sci_ceil.cpp
    sci_gateway/cpp/sci_clean.cpp
    sci_gateway/cpp/sci_conj.cpp
    sci_gateway/cpp/sci_cos.cpp
    sci_gateway/cpp/sci_cosh.cpp
    sci_gateway/cpp/sci_sin.cpp
    sci_gateway/cpp/sci_sinh.cpp
    sci_gateway/cpp/sci_tan.cpp
    sci_gateway/cpp/sci_tanh.cpp
    sci_gateway/cpp/sci_cumsum.cpp
    sci_gateway/cpp/sci_cumprod.cpp
    sci_gateway/cpp/sci_diag.cpp
    sci_gateway/cpp/sci_dsearch.cpp
    sci_gateway/cpp/sci_exp.cpp
    sci_gateway/cpp/sci_expm.cpp
    sci_gateway/cpp/sci_floor.cpp
    sci_gateway/cpp/sci_frexp.cpp
    sci_gateway/cpp/sci_gsort.cpp
    sci_gateway/cpp/sci_imag.cpp
    sci_gateway/cpp/sci_imult.cpp
    sci_gateway/cpp/sci_int.cpp
    sci_gateway/cpp/sci_isreal.cpp
    sci_gateway/cpp/sci_kron.cpp
    sci_gateway/cpp/sci_log.cpp
    sci_gateway/cpp/sci_log1p.cpp
    sci_gateway/cpp/sci_max.cpp
    sci_gateway/cpp/sci_rat.cpp
    sci_gateway/cpp/sci_triu.cpp
    sci_gateway/cpp/sci_tril.cpp
    sci_gateway/cpp/sci_real.cpp
    sci_gateway/cpp/sci_round.cpp
    sci_gateway/cpp/sci_sign.cpp
    sci_gateway/cpp/sci_sqrt.cpp
    sci_gateway/cpp/sci_issquare.cpp
    sci_gateway/cpp/sci_linspace.cpp
    sci_gateway/cpp/sci_isvector.cpp
    sci_gateway/cpp/sci_nearfloat.cpp
    sci_gateway/cpp/sci_permute.cpp
    sci_gateway/cpp/sci_bitstring.cpp
    sci_gateway/cpp/sci_base2dec.cpp
    sci_gateway/cpp/sci_dec2base.cpp
    sci_gateway/cpp/sci_vander.cpp
    sci_gateway/cpp/sci_gallery.cpp
    sci_gateway/cpp/sci_isempty.cpp
    sci_gateway/cpp/sci_percent_gallery.cpp
    sci_gateway/cpp/sci_ishermitian.cpp
    sci_gateway/cpp/sci_issymmetric.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes)
