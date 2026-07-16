# Stage-1e manifest — the 64 baseline module dylibs, classified into batches

The authoritative work-list for the Stage-1e CMake driver (Tasks 5–9). One data
row per module dylib in the parity baseline (`build-parity/baseline-autotools.json`),
classified into the migration batches. Every data row starts with `| libsci`, so the
completeness gate is:

```bash
grep -c '^| libsci' cmake/stage1e-manifest.md    # must print 64
```

## Scope

- **In scope (64 rows):** every `libsci*` dylib key in the baseline except the two
  aggregates (`libscilab.VER.dylib`, `libscilab-cli.VER.dylib`).
- **Excluded (4 keys):** the aggregates, plus `libjavasci2.VER.dylib` (javasci — see
  special cases) and `libxlnt.1.6.1.dylib` (bundled third-party, not a sci module).
- **No hidden rows:** modules whose `libsci<m>.la` is a convenience library folded into
  the aggregates build **no standalone dylib** and are correctly absent from the baseline
  and from this manifest: fileio, mexlib(libmex), parameters, boolean, cacsd, core,
  api_scilab, data_structures, elementary_functions, integer, io, linear_algebra,
  polynomials, time, windows_tools, sparse, output_stream, string, fftw, dynamic_link,
  threads, mpi (MPI=off). Verified: `modules/<m>/.libs/` contains no `.2027.dylib` for any
  of them.

## How each column was measured

Baseline build configuration (from `config.status`): GUI=on, XCOS=on, JAVASCI=on,
UMFPACK=on, TCLTK=off, MPI=off.

| column | source |
|---|---|
| dylib key | `baseline-autotools.json` key, verbatim (`VER` = 2027 on disk) |
| module dir | `modules/<dir>/` that builds the dylib (`libscisundials` is built by `differential_equations`) |
| main/variant | second `pkglib` dylib emitted by the same module dir = variant (`-disable`/`-cli`/`-minimal`/`-java`) |
| class | which aggregate link list in `modules/Makefile.am` names the `.la`: `ENGINE_LIBS`, `DYNAMIC_LOAD` (= `ENGINE_LIBS_DYNAMIC_LOAD`), `GUI_LIBS`, `NO_GUI_LIBS`, or `(none)` — grepped, not guessed |
| languages | main rows: `find modules/<m>/src modules/<m>/sci_gateway -name '*.{c,cpp,cxx,cc,f,F,f90}'` census (file counts in parens); variant rows: the variant's own `*_la_SOURCES` list in the module `Makefile.am`; exemplar rows: the proven values from their migrated `CMakeLists.txt` |
| external deps | `otool -L modules/<m>/.libs/<dylib>` non-`libsci` entries, minus the implicit toolchain (`libSystem`, system `libc++`, `libobjc`) |
| module deps | `otool -L` `libsci*` entries (→ `MODULE_DEPS` / same-dir target links in CMake) |
| symbols | `nm -gU <dylib> \| wc -l` |
| batch | see batch rules below |

**Class footnotes:**
- `NO_GUI_LIBS` is **empty on macOS** (`if IS_MACOSX` in `modules/Makefile.am`); on other
  platforms it holds the `-disable`/`-minimal`/`-cli(preferences)` stubs. On macOS these
  dylibs are still built + installed by their module dirs — they are simply not linked
  into `libscilab-cli`.
- `(none)`: installed dylib that appears in **no** aggregate link list
  (`libscisundials`, `libscihelptools-disable`, `libsciscicos-cli`,
  `libsciscicos_blocks-cli`). sundials is linked directly by
  differential_equations/scicos/xcos; the scicos `-cli` twins are linked by
  `libsciscicos-cli`; helptools-disable is install-only on every platform.

## Batch rules

A dylib belongs to exactly one batch. Batching is per **module dir** (both dylibs of a
variant pair travel together, so one batch task writes the whole `CMakeLists.txt`), at the
highest-complexity batch that applies: **edge (E) > JNI (D) > external-dep (C) >
variant (B) > simple (A)**. The four exemplars are already migrated (Tasks 1–3) and sit
outside the batches. Batch order A→E is also a safe build order: every `MODULE_DEPS`
target of an E-row lives in an earlier batch or in E itself.

| batch | meaning | rows |
|---|---|---|
| DONE | exemplars migrated in Tasks 1–3 | 4 |
| A | simple leaves — no external dep, no module edge, no variant | 8 |
| B | variant-pair modules (main + stub variant), nothing else | 20 |
| C | external-dependency modules (SYSTEM_LIBS / find_package), no inter-module edge | 20 |
| D | JNI/Java-native — links the JDK (`@rpath/libjli.dylib`), no inter-module edge | 2 |
| E | inter-module-edge modules (link a sibling module dylib → `MODULE_DEPS`) | 10 |

## DONE — exemplars (4 rows)

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libscicoverage.VER.dylib | coverage | main | ENGINE_LIBS | CXX | libxml2, libz, libicucore | — | 158 | DONE (644e9db70e2) |
| libsciinterpolation.VER.dylib | interpolation | main | DYNAMIC_LOAD | C+Fortran+CXX | libgfortran, libquadmath | — | 64 | DONE (644e9db70e2) |
| libsciparallel.VER.dylib | parallel | main | DYNAMIC_LOAD | C | libomp (OpenMP) | — | 3 | DONE (644e9db70e2) |
| libscisound.VER.dylib | sound | main | DYNAMIC_LOAD | C | — | — | 3 | DONE (f2c7b090421) |

## Batch A — simple leaves (8 rows)

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libsciast.VER.dylib | ast | main | ENGINE_LIBS | CXX(93)+C(12) | — | — | 7290 | A |
| libscicall_scilab.VER.dylib | call_scilab | main | ENGINE_LIBS | C(7) | — | — | 21 | A |
| libscicompletion.VER.dylib | completion | main | ENGINE_LIBS | CXX(16)+C(11) | — | — | 107 | A |
| libsciexternal_objects.VER.dylib | external_objects | main | ENGINE_LIBS | CXX(81) | — | — | 126 | A |
| libscifunctions.VER.dylib | functions | main | ENGINE_LIBS | CXX(8) | — | — | 12 | A |
| libscifunctions_manager.VER.dylib | functions_manager | main | ENGINE_LIBS | CXX(5) | — | — | 93 | A |
| libscihistory_manager.VER.dylib | history_manager | main | ENGINE_LIBS | CXX(16)+C(5) | — | — | 140 | A |
| libscirenderer.VER.dylib | renderer | main | GUI_LIBS | CXX(6)+C(1) | — | — | 58 | A |

Note — renderer: an earlier planning pass expected `renderer → jvm`; `otool -L` on the
baseline dylib shows **no** `libsci` edge and no external dep (and
`modules/renderer/Makefile.am` has no `LIBADD` at all). The native half is a leaf; the
Java renderer lives in the jar.

## Batch B — variant pairs (20 rows)

Main + stub variant, one `CMakeLists.txt` with two `scilab_module()` calls. All stubs are
single-file `no<module>` sources unless noted.

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libsciaction_binding.VER.dylib | action_binding | main | GUI_LIBS | CXX(5)+C(3) | — | — | 19 | B |
| libsciaction_binding-disable.VER.dylib | action_binding | variant | NO_GUI_LIBS | CXX(1: noaction_binding.cpp) | — | — | 0 | B |
| libscicommons.VER.dylib | commons | main | GUI_LIBS | C(4)+CXX(2) | — | — | 102 | B |
| libscicommons-disable.VER.dylib | commons | variant | NO_GUI_LIBS | C(1: fileutils.c) | — | — | 2 | B |
| libscigraphic_export.VER.dylib | graphic_export | main | GUI_LIBS | C(12)+CXX(7) | — | — | 40 | B |
| libscigraphic_export-disable.VER.dylib | graphic_export | variant | NO_GUI_LIBS | C(1) | — | — | 1 | B |
| libscigraphic_objects.VER.dylib | graphic_objects | main | GUI_LIBS | CXX(43)+C(7) | — | — | 738 | B |
| libscigraphic_objects-disable.VER.dylib | graphic_objects | variant | NO_GUI_LIBS | C(1) | — | — | 21 | B |
| libscigraphics.VER.dylib | graphics | main | GUI_LIBS | C(429)+CXX(12) | — | — | 704 | B |
| libscigraphics-disable.VER.dylib | graphics | variant | NO_GUI_LIBS | C(1) | — | — | 53 | B |
| libscigui.VER.dylib | gui | main | GUI_LIBS | CXX(122)+C(31) | — | — | 515 | B |
| libscigui-disable.VER.dylib | gui | variant | NO_GUI_LIBS | C(1: nogui.c) | — | — | 13 | B |
| libscihistory_browser.VER.dylib | history_browser | main | GUI_LIBS | C(3)+CXX(2) | — | — | 25 | B |
| libscihistory_browser-disable.VER.dylib | history_browser | variant | NO_GUI_LIBS | C(1) | — | — | 7 | B |
| libsciscinotes.VER.dylib | scinotes | main | GUI_LIBS | CXX(5)+C(4) | — | — | 26 | B |
| libsciscinotes-disable.VER.dylib | scinotes | variant | NO_GUI_LIBS | C(1)+CXX(1) | — | — | 1 | B |
| libscitclsci.VER.dylib | tclsci | main | GUI_LIBS | CXX(1: notclsci.cpp — TCLTK=off) | — | — | 11 | B |
| libscitclsci-disable.VER.dylib | tclsci | variant | NO_GUI_LIBS | CXX(1: notclsci.cpp) | — | — | 11 | B |
| libsciui_data.VER.dylib | ui_data | main | GUI_LIBS | CXX(9)+C(4) | — | — | 78 | B |
| libsciui_data-disable.VER.dylib | ui_data | variant | NO_GUI_LIBS | C(1) | — | — | 3 | B |

Note — tclsci: the baseline was configured with TCLTK **off**, so the main dylib is built
from the same `notclsci.cpp` stub as the `-disable` variant (identical export lists,
verified by `nm -gU` diff). The real Tcl sources (`TCLSCI_C_SOURCES`, C) only build under
`if TCLTK`.

## Batch C — external-dependency modules (20 rows)

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libsciarnoldi.VER.dylib | arnoldi | main | ENGINE_LIBS | C(9) | libopenblas, libarpack | — | 8 | C |
| libsciconsole.VER.dylib | console | main | GUI_LIBS | C(36)+CXX(18) | libncurses | libsciconsole-minimal (intra-module) | 36 | C |
| libsciconsole-minimal.VER.dylib | console | variant | NO_GUI_LIBS | C(18)+CXX(8) (explicit CLI list) | libncurses | — | 53 | C |
| libscihdf5.VER.dylib | hdf5 | main | ENGINE_LIBS | CXX(65)+C(5) | libhdf5, libhdf5_hl | — | 557 | C |
| libscilocalization.VER.dylib | localization | main | ENGINE_LIBS | C(17)+CXX(7) | Cocoa.framework, CoreFoundation.framework, libintl | — | 33 | C |
| libscimatio.VER.dylib | matio | main | DYNAMIC_LOAD | C(13)+CXX(13) | libmatio, libhdf5, libz | — | 38 | C |
| libscioptimization.VER.dylib | optimization | main | DYNAMIC_LOAD | Fortran(86)+CXX(8)+C(5) | libgfortran, libquadmath | — | 154 | C |
| libscipreferences.VER.dylib | preferences | main | GUI_LIBS | CXX(7)+C(2) | libxml2, libz, libicucore | — | 24 | C |
| libscipreferences-cli.VER.dylib | preferences | variant | NO_GUI_LIBS | CXX(2: nopreferences.cpp, preferences_gw.cpp) | — | — | 12 | C |
| libscirandlib.VER.dylib | randlib | main | DYNAMIC_LOAD | Fortran(15)+C(13)+CXX(2) | libgfortran, libquadmath | — | 58 | C |
| libsciscicos_blocks.VER.dylib | scicos_blocks | main | DYNAMIC_LOAD | C(315)+Fortran(57)+CXX(6) | libgfortran, libquadmath | — | 398 | C |
| libsciscicos_blocks-cli.VER.dylib | scicos_blocks | variant | (none) | C+Fortran+CXX (NON_BLOCK_CLI subset + algo lib) | libgfortran, libquadmath | — | 387 | C |
| libscisignal_processing.VER.dylib | signal_processing | main | DYNAMIC_LOAD | Fortran(58)+CXX(7)+C(6) | libgfortran, libquadmath | — | 110 | C |
| libscislint.VER.dylib | slint | main | ENGINE_LIBS | CXX(74)+C(1) | libxml2, libz, libicucore | — | 676 | C |
| libscispecial_functions.VER.dylib | special_functions | main | ENGINE_LIBS | C(9)+Fortran(9)+CXX(5) | libgfortran, libquadmath | — | 45 | C |
| libscispreadsheet.VER.dylib | spreadsheet | main | DYNAMIC_LOAD | C(24)+CXX(16) | libxlnt (@rpath, bundled), libparquet, libarrow | — | 181 | C |
| libscistatistics.VER.dylib | statistics | main | ENGINE_LIBS | Fortran(65)+C(16)+CXX(8) | libgfortran, libquadmath | — | 114 | C |
| libsciumfpack.VER.dylib | umfpack | main | DYNAMIC_LOAD | C(15) | — at link time; 18 undefined `umfpack_*` syms resolve via `-undefined dynamic_lookup`; suite-sparse headers (`UMFPACK_CFLAGS`) needed at compile | — | 43 | C |
| libsciwebtools.VER.dylib | webtools | main | ENGINE_LIBS | CXX(20)+C(2) | libcurl (@rpath, bundled), libc++ (@rpath — non-system!) | — | 72 | C |
| libscixml.VER.dylib | xml | main | ENGINE_LIBS | CXX(55)+C(1) | libxml2, libz, libicucore | — | 334 | C |

Notes:
- **console** is a variant-pair module promoted to C by its ncurses dependency; the main
  dylib also links its own `-minimal` variant (**intra**-module link, not a Batch-E
  inter-module edge — both targets live in one `CMakeLists.txt`, minimal must be defined
  first).
- **umfpack**: UMFPACK=on — real sources (taucs_scilab.c, common_umfpack.c + 12 gateways),
  but no suite-sparse lib is recorded by `otool`; the symbols resolve at run time via
  `dynamic_lookup`. In C because the migration still needs the suite-sparse include path
  (find_package/pkg-config for headers) and must preserve the no-link behavior.
- **scicos_blocks** feeds Batch E (scicos/xcos link it); C-before-E keeps the build order
  valid. An earlier planning pass expected `localization → io`: no such dylib edge exists
  (io builds no dylib; `otool` shows none, LIBADD is `-lintl` only) — localization is a
  plain external-dep row.

## Batch D — JNI/Java-native (2 rows)

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libscijvm.VER.dylib | jvm | main | GUI_LIBS | C(24)+CXX(2) | @rpath/libjli.dylib (JDK), libxml2, libz, libicucore | — | 42 | D |
| libscijvm-disable.VER.dylib | jvm | variant | NO_GUI_LIBS | C(1: nojvm.c) | — | — | 9 | D |

Note: the other JDK-linking dylibs (external_objects_java, types-java, xcos) carry
inter-module edges and live in Batch E (edge > JNI). jvm is the only edge-free JDK
module; its migration establishes the FindJNI/libjli pattern that E reuses.

## Batch E — inter-module-edge modules (10 rows)

Edges measured by `otool -L` on the baseline dylibs. `MODULE_DEPS` targets must exist
before these link (all are in C/D/E; A→E order is safe).

| dylib key | module dir | main/variant | class | languages | external deps | module deps | symbols | batch |
|---|---|---|---|---|---|---|---|---|
| libscidifferential_equations.VER.dylib | differential_equations | main | ENGINE_LIBS | CXX(42)+Fortran(66)+C(19) | libklu, libamd, libumfpack (suite-sparse), libomp, libgfortran, libquadmath | libscisundials (same-dir target) | 505 | E |
| libscisundials.VER.dylib | differential_equations | second dylib (vendored lib) | (none) | C(179: vendored patched_sundials) | libklu, libamd, libumfpack, libomp | — | 2371 | E |
| libsciexternal_objects_java.VER.dylib | external_objects_java | main | GUI_LIBS | CXX(38) | @rpath/libjli.dylib (JDK), libxml2, libz, libicucore | libscicommons, libscijvm | 318 | E |
| libscihelptools.VER.dylib | helptools | main | GUI_LIBS | CXX(4)+C(1) | — | libscicommons | 31 | E |
| libscihelptools-disable.VER.dylib | helptools | variant | (none) | CXX(1: sci_gateway/nogui/nogui.cpp) | — | — | 3 | E |
| libsciscicos.VER.dylib | scicos | main | DYNAMIC_LOAD | CXX(62)+C(19)+Fortran(8) | libarchive, libklu, libamd, libumfpack, libomp, libgfortran, libquadmath | libscisundials, libsciscicos_blocks | 604 | E |
| libsciscicos-cli.VER.dylib | scicos | variant | (none) | CXX+C+Fortran (same gateway/algo sources as main) | libarchive, libklu, libamd, libumfpack, libomp, libgfortran, libquadmath | libscisundials, libsciscicos_blocks-cli | 604 | E |
| libscitypes-java.VER.dylib | types | sole installed dylib (JNI wrappers; libscitypes.la itself is a convenience lib inside the aggregates) | GUI_LIBS | CXX(4)+C(1) | @rpath/libjli.dylib (JDK), libxml2, libz, libicucore | libscicommons, libscijvm | 79 | E |
| libscixcos.VER.dylib | xcos | main | DYNAMIC_LOAD | CXX(28)+C(2) | libklu, libamd, libumfpack, libomp, libgfortran, libquadmath, libxml2, libz, libicucore, @rpath/libjli.dylib (JDK), libarchive | libsciscicos, libscisundials, libsciscicos_blocks, libscicommons, libscijvm | 231 | E |
| libscixcos-disable.VER.dylib | xcos | variant | NO_GUI_LIBS | C(1: noxcos.c) | — | — | 1 | E |

Notes:
- **scicos main vs `-cli`** are full twins (same `GATEWAY_*` sources + `libsciscicos-algo`
  convenience lib); they differ only in which scicos_blocks twin they link.
- **xcos** `otool` shows two more edges than planned (`libscisundials`,
  `libsciscicos_blocks` in addition to scicos/commons/jvm) — libtool records the
  transitive closure as direct loads; the CMake link must reproduce them (or prove
  transitivity suffices at parity time).

## Special cases recorded (per Task-4 brief Step 2)

- **javasci** builds `libjavasci2.VER.dylib` (in the baseline but **outside this
  manifest's scope** — it is not a `libsci<module>` engine dylib). It links the
  **aggregate `libscilab`**, which is not a module target: in CMake it resolves via
  `-undefined dynamic_lookup`, NOT via `MODULE_DEPS`. It is handled with the aggregates,
  not by Batches A–E.
- Planned edges that do **not** exist as dylib edges in this baseline (their targets
  build no standalone dylib; the references are convenience-`.la`/aggregate-level):
  `fileio → console`, `integer → polynomials`, `localization → io`, `renderer → jvm`.
  None of these modules needs `MODULE_DEPS` in Stage-1e.

## Totals

| batch | DONE | A | B | C | D | E | total |
|---|---|---|---|---|---|---|---|
| rows | 4 | 8 | 20 | 20 | 2 | 10 | 64 |
