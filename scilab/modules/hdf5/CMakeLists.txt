# CMake build for the `hdf5` module — Batch C (external deps) on the shared
# scilab_module() helper (cmake/ScilabModule.cmake); built from the top-level
# scilab/CMakeLists.txt driver. HYBRID COEXISTENCE: this file is invisible to
# automake; the autotools hdf5 is always recoverable with `make -C modules/hdf5`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscihdf5.2027.dylib (+ symlink)
#   install_name:  /usr/local/lib/scilab/libscihdf5.2027.dylib
#   exports:       exactly 557 symbols
#   deps:          /opt/homebrew/opt/hdf5/lib/libhdf5.320.dylib
#                  /opt/homebrew/opt/hdf5/lib/libhdf5_hl.320.dylib
#                  /usr/lib/libc++.1.dylib  /usr/lib/libSystem.B.dylib
#
# EXTERNAL DEPS: HDF5_LIBS = -L/opt/homebrew/opt/hdf5/lib -lhdf5 -lhdf5_hl
# (configured Makefile). Pinned as ABSOLUTE keg paths (the libomp pattern): the
# recorded dep is each dylib's install_name — the exact /opt/homebrew/opt/hdf5
# paths above. HDF5_CFLAGS (-I/opt/homebrew/opt/hdf5/include) in EXTRA_INCLUDES.
#
# Sources verbatim from Makefile.am: algo = HDF5_SOURCES (5) + HDF5_CXX_SOURCES
# (24, incl. DllmainHdf5.c); gateway = the 36 GATEWAY_CXX_SOURCES.

scilab_module(hdf5
  ALGO_SOURCES
    src/c/h5_fileManagement.c
    src/c/h5_writeDataToFile.c
    src/c/h5_readDataFromFile.c
    src/c/h5_readDataFromFile_v1.c
    src/cpp/listvar_in_hdf5_v1.cpp
    src/cpp/H5Attribute.cpp
    src/cpp/H5AttributesList.cpp
    src/cpp/H5DataConverter.cpp
    src/cpp/H5Dataset.cpp
    src/cpp/H5Dataspace.cpp
    src/cpp/H5File.cpp
    src/cpp/H5Group.cpp
    src/cpp/H5Object.cpp
    src/cpp/H5Type.cpp
    src/cpp/H5DataFactory.cpp
    src/cpp/H5VariableScope.cpp
    src/cpp/H5SoftLink.cpp
    src/cpp/H5ExternalLink.cpp
    src/cpp/H5HardLink.cpp
    src/cpp/H5Link.cpp
    src/cpp/HDF5Scilab.cpp
    src/cpp/H5StringData.cpp
    src/cpp/H5ReferenceData.cpp
    src/cpp/H5CompoundData.cpp
    src/cpp/H5ArrayData.cpp
    src/cpp/H5VlenData.cpp
    src/cpp/H5Options.cpp
    src/cpp/handle_properties.cpp
    src/c/DllmainHdf5.c
  GATEWAY_SOURCES
    sci_gateway/cpp/hdf5_gw.cpp
    sci_gateway/cpp/sci_hdf5_save.cpp
    sci_gateway/cpp/sci_hdf5_load.cpp
    sci_gateway/cpp/sci_hdf5_load_v1.cpp
    sci_gateway/cpp/sci_hdf5_load_v2.cpp
    sci_gateway/cpp/sci_hdf5_load_v3.cpp
    sci_gateway/cpp/sci_hdf5_load_v4.cpp
    sci_gateway/cpp/sci_hdf5_is_file.cpp
    sci_gateway/cpp/sci_hdf5_listvar.cpp
    sci_gateway/cpp/sci_hdf5_listvar_v2.cpp
    sci_gateway/cpp/sci_hdf5_listvar_v3.cpp
    sci_gateway/cpp/sci_hdf5_file_version.cpp
    sci_gateway/cpp/sci_h5dump.cpp
    sci_gateway/cpp/sci_h5open.cpp
    sci_gateway/cpp/sci_h5close.cpp
    sci_gateway/cpp/sci_h5read.cpp
    sci_gateway/cpp/sci_h5ls.cpp
    sci_gateway/cpp/sci_percent_H5Object_p.cpp
    sci_gateway/cpp/sci_percent_H5Object_e.cpp
    sci_gateway/cpp/sci_h5group.cpp
    sci_gateway/cpp/sci_h5rm.cpp
    sci_gateway/cpp/sci_h5dataset.cpp
    sci_gateway/cpp/sci_h5write.cpp
    sci_gateway/cpp/sci_h5attr.cpp
    sci_gateway/cpp/sci_h5ln.cpp
    sci_gateway/cpp/sci_h5readattr.cpp
    sci_gateway/cpp/sci_h5flush.cpp
    sci_gateway/cpp/sci_h5cp.cpp
    sci_gateway/cpp/sci_h5isfoo.cpp
    sci_gateway/cpp/sci_h5mount.cpp
    sci_gateway/cpp/sci_h5umount.cpp
    sci_gateway/cpp/sci_h5mv.cpp
    sci_gateway/cpp/sci_h5get.cpp
    sci_gateway/cpp/sci_h5label.cpp
    sci_gateway/cpp/sci_h5exists.cpp
    sci_gateway/cpp/sci_percent_H5Object_fieldnames.cpp
  LANG C CXX
  SYSTEM_LIBS
    /opt/homebrew/opt/hdf5/lib/libhdf5.dylib
    /opt/homebrew/opt/hdf5/lib/libhdf5_hl.dylib
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/threads/includes
    ${SCILAB_SOURCE_DIR}/modules/jvm/includes
    ${SCILAB_SOURCE_DIR}/modules/commons/src/jni
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
    ${SCILAB_SOURCE_DIR}/modules/console/includes
    ${SCILAB_SOURCE_DIR}/modules/graphic_objects/includes
    ${SCILAB_SOURCE_DIR}/modules/graphics/includes
    ${SCILAB_SOURCE_DIR}/modules/io/includes
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include
    /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/include/darwin
    /opt/homebrew/opt/hdf5/include
  CLASS ENGINE_LIBS
  SYMBOLS 557)
