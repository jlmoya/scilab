# scilab/modules/CMakeLists.txt — the two native AGGREGATES on
# scilab_aggregate() (cmake/ScilabAggregate.cmake): libscilab (GUI) and
# libscilab-cli. Added LAST by the top-level driver so every module target —
# the 64 Stage-1e dylibs and the 21 Task-3 fold-in OBJECT libs — already
# exists.
#
# TRANSCRIPTION SOURCE — modules/Makefile.am on THIS configuration (GUI=yes,
# XCOS=yes, IS_MACOSX=yes -> NO_GUI_LIBS empty, MPI=no, FORCE_LINK=no,
# OTHER_LIBS/EXTERNAL_LIBS undefined -> empty):
#
#   libscilab_la_LIBADD     = $(ENGINE_LIBS) + jvm.la + $(GUI_LIBS)
#                             + $(FLIBS) $(LAPACK_LIBS) $(BLAS_LIBS)
#   libscilab_cli_la_LIBADD = $(ENGINE_LIBS)
#                             + $(FLIBS) $(LAPACK_LIBS) $(BLAS_LIBS)
#
# ENGINE_LIBS_DYNAMIC_LOAD is `if FORCE_LINK` ONLY — never linked in
# production (the DYNAMIC_LOAD modules are dlopen'd; they appear in neither
# baseline dep list).
#
# FOLD vs LINK — the parity-defining classification of every LIBADD member:
# a member .la that automake declares noinst on macOS (the 21 Task-3 core
# modules) FOLDS — its objects enter the aggregate, no dep recorded; a member
# .la that is pkglib (a Stage-1e standalone dylib) is LINKED — recorded as an
# LC_LOAD_DYLIB dep at its install_name. Ground truth: `otool -L` on the
# baseline modules/.libs/libscilab.2027.dylib (59 deps) and
# libscilab-cli.2027.dylib (39 deps).
#
#   ENGINE_LIBS (38 members) ->
#     FOLD (21): fileio mexlib(libmex) parameters boolean cacsd core
#                api_scilab data_structures elementary_functions integer io
#                linear_algebra polynomials time windows_tools sparse
#                output_stream string fftw dynamic_link threads
#     LINK (17): functions call_scilab differential_equations arnoldi
#                localization special_functions completion history_manager
#                hdf5 statistics ast functions_manager xml external_objects
#                slint coverage webtools
#   GUI_LIBS (17 members) + the explicit jvm.la -> ALL pkglib, all LINKED:
#     jvm gui renderer graphics graphic_export console action_binding
#     graphic_objects scinotes ui_data history_browser commons types-java
#     preferences external_objects_java helptools tclsci
#
# TRANSITIVE .la RECORDS — libtool expands each linked .la's dependency_libs
# onto the aggregate link line, so the baseline additionally records deps that
# appear in no LIBADD list; CMake target links do NOT propagate (PRIVATE), so
# each is reproduced explicitly here:
#   libscisundials.2027.dylib      <- differential_equations' dependency_libs
#                                     (both aggregates)
#   libsciconsole-minimal.2027.dylib <- console's dependency_libs (GUI only)
#   + every system/keg lib below (each folded module's dependency_libs -l/-L).

# CMake would append its detected Fortran implicit runtime (emutls_w heapt_w
# gfortran gcc quadmath + the Homebrew gcc -L dirs) to any target containing
# Fortran objects — the aggregates do (cacsd/elementary_functions/sparse/...).
# That must NOT happen here: the baseline records the MINICONDA @rpath
# gfortran/quadmath (the FLIBS accident documented in ScilabAggregate.cmake),
# and the emutls_w/heapt_w archives contributed zero symbols. Directory-scoped
# — only the two aggregate targets live in this scope; scilab_aggregate()
# FATAL_ERRORs if these are not cleared.
set(CMAKE_Fortran_IMPLICIT_LINK_LIBRARIES "")
set(CMAKE_Fortran_IMPLICIT_LINK_DIRECTORIES "")

# The 21 fold-in OBJECT libs, in ENGINE_LIBS order (documentation only — the
# harness compares the dep SET and the exported-symbol set; fold order does
# not change either).
set(_scilab_fold_objects
  scifileio-obj scimexlib-obj sciparameters-obj sciboolean-obj scicacsd-obj
  scicore-obj sciapi_scilab-obj scidata_structures-obj
  scielementary_functions-obj sciinteger-obj sciio-obj scilinear_algebra-obj
  scipolynomials-obj scitime-obj sciwindows_tools-obj scisparse-obj
  scioutput_stream-obj scistring-obj scifftw-obj scidynamic_link-obj
  scithreads-obj)

# ENGINE_LIBS' 17 pkglib members (Makefile.am order) + scisundials right
# after its owner differential_equations (transitive record, see header).
set(_scilab_engine_link
  scifunctions scicall_scilab scidifferential_equations scisundials
  sciarnoldi scilocalization scispecial_functions scicompletion
  scihistory_manager scihdf5 scistatistics sciast scifunctions_manager
  scixml sciexternal_objects scislint scicoverage sciwebtools)

# jvm first ("Force the libscijvm to be linked first" — Makefile.am), then
# GUI_LIBS order; sciconsole-minimal follows sciconsole whose .la
# dependency_libs carried it into the baseline record.
set(_scilab_gui_link
  scijvm scigui scirenderer scigraphics scigraphic_export sciconsole
  sciconsole-minimal sciaction_binding scigraphic_objects sciscinotes
  sciui_data scihistory_browser scicommons scitypes-java scipreferences
  sciexternal_objects_java scihelptools scitclsci)

# MACHINE-CONFIG-SPECIFIC (the webtools precedent, same ledger debt): the
# baseline's -lcurl/-lgfortran/-lquadmath resolved against miniconda's lib dir
# (its -L rides in via webtools' .la), recording @rpath install_names that
# resolve at run time through the gcc LC_RPATH. Reproduced with the SAME
# autotools spelling — raw -L + plain names (the webtools/jvm SYSTEM_LIBS
# pattern) — NOT absolute file paths: CMake auto-appends the directory of any
# @rpath-install_name'd file it links by path to the build rpath, which would
# grow LC_RPATH beyond the baseline's two entries (the harness checks rpaths
# in order; scilab_aggregate() also sets SKIP_BUILD_RPATH as a hard guard).
set(_miniconda_lib /Users/josemoya/miniconda3/lib)

# The system/keg libs BOTH aggregates record (18 entries + implicit
# libSystem + FRAMEWORKS Cocoa = the baseline's non-module dep set minus the
# GUI-only pair below). Plain names resolve in the SDK sysroot; absolute
# paths pin keg/miniconda files (the recorded dep is that file's
# install_name — unversioned symlinks resolve to the versioned IDs the
# baseline shows). Origin of each record: the named folded module's .la
# dependency_libs (or FLIBS/BLAS_LIBS where noted).
set(_scilab_engine_system_libs
  /opt/homebrew/opt/libarchive/lib/libarchive.dylib         # fileio
  xslt                                                      # -> /usr/lib/libxslt.1
  /opt/homebrew/opt/suite-sparse/lib/libklu.dylib           # sundials/umfpack chain
  /opt/homebrew/opt/suite-sparse/lib/libamd.dylib
  /opt/homebrew/opt/suite-sparse/lib/libumfpack.dylib
  /opt/homebrew/opt/libomp/lib/libomp.dylib                 # OpenMP (coverage et al.)
  /opt/homebrew/opt/arpack/lib/libarpack.dylib              # arnoldi chain
  /opt/homebrew/opt/gettext/lib/libintl.dylib               # localization chain
  /opt/homebrew/opt/pcre2/lib/libpcre2-32.dylib             # string
  /opt/homebrew/opt/hdf5/lib/libhdf5.dylib                  # hdf5/fileio
  /opt/homebrew/opt/hdf5/lib/libhdf5_hl.dylib
  /opt/homebrew/opt/fftw/lib/libfftw3.dylib                 # fftw
  -L${_miniconda_lib}
  curl                                                      # webtools chain -> @rpath/libcurl.4
  xml2 z icucore                                            # core/io -> /usr/lib
  gfortran                                                  # FLIBS -> @rpath/libgfortran.5
  quadmath                                                  # FLIBS -> @rpath/libquadmath.0
  /opt/homebrew/opt/openblas/lib/libopenblas.dylib)         # BLAS_LIBS

# libscilab — the GUI aggregate: ENGINE + jvm + GUI_LIBS, plus the GUI-only
# system records (curses <- console chain; jli <- jvm's
# -L$(JAVA_HOME)/lib -ljli, recorded at its @rpath/libjli.dylib install_name;
# SCILAB_JAVA_HOME is the toolchain's (ScilabToolchain.cmake) config.status-derived JDK).
# Baseline: 59 deps, 3543 exported symbols.
scilab_aggregate(libscilab
  FOLD_OBJECTS ${_scilab_fold_objects}
  LINK_MODULES ${_scilab_engine_link} ${_scilab_gui_link}
  SYSTEM_LIBS ${_scilab_engine_system_libs}
              curses
              -L${SCILAB_JAVA_HOME}/lib jli
  FRAMEWORKS Cocoa)

# libscilab-cli — ENGINE only (IS_MACOSX makes NO_GUI_LIBS empty; no jvm, no
# GUI_LIBS, no curses/jli). Baseline: 39 deps, the same 3543 symbols (both
# aggregates fold the identical 21 modules).
scilab_aggregate(libscilab-cli
  FOLD_OBJECTS ${_scilab_fold_objects}
  LINK_MODULES ${_scilab_engine_link}
  SYSTEM_LIBS ${_scilab_engine_system_libs}
  FRAMEWORKS Cocoa)
