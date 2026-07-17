# CMake build for the `cacsd` FOLD-IN core module — OBJECT library only
# (scilab_object_module(), cmake/ScilabModule.cmake); built from the top-level
# driver. HYBRID COEXISTENCE: invisible to automake.
#
# FOLD-IN (modules/Makefile.am): on macOS (MAINTAINER_MODE off) automake makes
# this module's .la's noinst convenience libs — cacsd ships NO standalone
# dylib; ALL its objects fold into the libscilab/libscilab-cli aggregates,
# which consume $<TARGET_OBJECTS:scicacsd-obj>. No drop-in, no install_name,
# no per-module dylib parity — the aggregate's parity + the per-TU flag-facts
# gate are the checks.
#
# Sources verbatim from the CONFIGURED Makefile (resolved _la_OBJECTS, cross-
# checked against the baseline .lo set 1:1). TWO .la's: libscicacsd-algo (146
# TUs: CACSD_FORTRAN + SLICOT_FORTRAN + CACSD_C) + libscicacsd (24 gateway
# C/C++ TUs).
#
# EXTRA_INCLUDES (libscicacsd_la_CPPFLAGS): elementary_functions,
# threads, string, console.

# --- per-TU override, verbatim from Makefile.am (`if IS_MACOSX` /
# "enforce -O0 for some files"): sszer.f compiles with -O0 APPENDED after
# the global -O2 (last-wins) — the legacy-Fortran-miscompiled-at-O2 class.
# The baseline object's DWARF producer reads `-g1 -O2 -O0 -fwrapv`; this
# reproduces that shape. parity/flagfacts_check.py knows the exception
# (FILE_EXPECTED_OVERRIDES: sszer.f -> opt=O0).
set_source_files_properties(src/fortran/sszer.f PROPERTIES COMPILE_OPTIONS "-O0")

scilab_object_module(cacsd
  SOURCES
    # libscicacsd-algo.la (146 TUs)
    src/fortran/jacl2.f
    src/fortran/onface.f
    src/fortran/tild.f
    src/fortran/degl2.f
    src/fortran/qvalz.f
    src/fortran/dfrmg.f
    src/fortran/hessl2.f
    src/fortran/rtitr.f
    src/fortran/watfac.f
    src/fortran/sszer.f
    src/fortran/mzdivq.f
    src/fortran/expan.f
    src/fortran/optml2.f
    src/fortran/wdegre.f
    src/fortran/phi.f
    src/fortran/ricd.f
    src/fortran/shrslv.f
    src/fortran/qhesz.f
    src/fortran/rilac.f
    src/fortran/arl2.f
    src/fortran/wesidu.f
    src/fortran/dzdivq.f
    src/fortran/arl2a.f
    src/fortran/lq.f
    src/fortran/outl2.f
    src/fortran/dhetr.f
    src/fortran/domout.f
    src/fortran/rootgp.f
    src/fortran/feq.f
    src/fortran/fout.f
    src/fortran/deg1l2.f
    src/fortran/modul.f
    src/fortran/scapol.f
    src/fortran/giv.f
    src/fortran/storl2.f
    src/fortran/calsca.f
    src/fortran/qitz.f
    src/fortran/front.f
    src/slicot/sb04pd.f
    src/slicot/ib01ad.f
    src/slicot/tb01wd.f
    src/slicot/ib01py.f
    src/slicot/mb01rx.f
    src/slicot/ib01md.f
    src/slicot/sb02ru.f
    src/slicot/sb04nw.f
    src/slicot/sb10dd.f
    src/slicot/mb04id.f
    src/slicot/sb04ny.f
    src/slicot/ib01qd.f
    src/slicot/sb04px.f
    src/slicot/sb04rw.f
    src/slicot/sb04ry.f
    src/slicot/mb01qd.f
    src/slicot/mb01ud.f
    src/slicot/sb02od.f
    src/slicot/mb02ud.f
    src/slicot/sb10pd.f
    src/slicot/sb03od.f
    src/slicot/mb03ud.f
    src/slicot/sb02sd.f
    src/slicot/ib01oy.f
    src/slicot/sb02ms.f
    src/slicot/ab13md.f
    src/slicot/sb03or.f
    src/slicot/mb03oy.f
    src/slicot/ma02fd.f
    src/slicot/sb03ot.f
    src/slicot/mb04oy.f
    src/slicot/sb04mu.f
    src/slicot/sb03mw.f
    src/slicot/sb02ov.f
    src/slicot/sb03ov.f
    src/slicot/sb04mw.f
    src/slicot/sb03my.f
    src/slicot/sb04my.f
    src/slicot/sb04qu.f
    src/slicot/ib01pd.f
    src/slicot/sb03qy.f
    src/slicot/sb03sx.f
    src/slicot/sb04qy.f
    src/slicot/mb01pd.f
    src/slicot/mb02pd.f
    src/slicot/mb01td.f
    src/slicot/sb02nd.f
    src/slicot/sb04nd.f
    src/slicot/sb02rd.f
    src/slicot/sb04rd.f
    src/slicot/ib01cd.f
    src/slicot/ib01px.f
    src/slicot/ma02ad.f
    src/slicot/ab01od.f
    src/slicot/mb01ru.f
    src/slicot/ma02ed.f
    src/slicot/mb04ny.f
    src/slicot/mb01ry.f
    src/slicot/sb04nv.f
    src/slicot/ib01od.f
    src/slicot/sb04nx.f
    src/slicot/sb10fd.f
    src/slicot/select.f
    src/slicot/sb04rv.f
    src/slicot/mb04kd.f
    src/slicot/sb04py.f
    src/slicot/sb04rx.f
    src/slicot/mb03od.f
    src/slicot/mb04od.f
    src/slicot/mb01sd.f
    src/slicot/sb03md.f
    src/slicot/sb04md.f
    src/slicot/sb02qd.f
    src/slicot/sb10rd.f
    src/slicot/sb04qd.f
    src/slicot/ib01my.f
    src/slicot/ib01bd.f
    src/slicot/mb04iy.f
    src/slicot/sb02mr.f
    src/slicot/ab01nd.f
    src/slicot/sb02mt.f
    src/slicot/sb04mr.f
    src/slicot/sb03mv.f
    src/slicot/sb02ou.f
    src/slicot/mb02qy.f
    src/slicot/sb03ou.f
    src/slicot/sb04qr.f
    src/slicot/sb03mx.f
    src/slicot/ib01nd.f
    src/slicot/sb02oy.f
    src/slicot/sb03oy.f
    src/slicot/sb03qx.f
    src/slicot/ib01rd.f
    src/slicot/sb03sy.f
    src/slicot/mb04nd.f
    src/slicot/mb01rd.f
    src/slicot/mb01vd.f
    src/slicot/sb10qd.f
    src/slicot/ereduc.f
    src/slicot/fstair.f
    src/slicot/inva.f
    src/slicot/polmc.f
    src/slicot/ssxmc.f
    src/slicot/ZB03OD.f
    src/slicot/riccpack.f
    src/slicot/Ex-schur.f
    src/fortran/voiddummy.f
    src/c/dmp2pm.c
    # libscicacsd.la (24 TUs)
    sci_gateway/c/gw_cacsd.c
    sci_gateway/c/sci_contr.c
    sci_gateway/c/sci_dhinf.c
    sci_gateway/c/sci_hinf.c
    sci_gateway/c/sci_linmeq.c
    sci_gateway/c/sci_mucomp.c
    sci_gateway/c/sci_rankqr.c
    sci_gateway/c/sci_rrankqr.c
    sci_gateway/c/sci_zrankqr.c
    sci_gateway/c/sci_ricc.c
    sci_gateway/c/sci_sident.c
    sci_gateway/c/sci_sorder.c
    sci_gateway/c/sci_findbd.c
    sci_gateway/cpp/cacsd_gw.cpp
    sci_gateway/cpp/sci_arl2_ius.cpp
    sci_gateway/cpp/sci_ldiv.cpp
    sci_gateway/cpp/sci_tzer.cpp
    sci_gateway/cpp/sci_residu.cpp
    sci_gateway/cpp/sci_ppol.cpp
    sci_gateway/cpp/sci_freq.cpp
    sci_gateway/cpp/sci_ltitr.cpp
    sci_gateway/cpp/sci_rtitr.cpp
    sci_gateway/cpp/sci_ereduc.cpp
    sci_gateway/cpp/sci_fstair.cpp
  LANG C Fortran CXX
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/elementary_functions/includes
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes)
