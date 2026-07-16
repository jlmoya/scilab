# CMake build for the `sound` module — migrated onto the shared scilab_module()
# helper (cmake/ScilabModule.cmake); built from the top-level scilab/CMakeLists.txt
# driver, no longer a standalone project. HYBRID COEXISTENCE: this file is
# invisible to automake; the autotools sound is always recoverable with
# `make -C modules/sound`.
#
# LINKING CLASS (modules/Makefile.am): ENGINE_LIBS_DYNAMIC_LOAD — dlopen-only;
# no executable or engine lib records libscisound as a load-command dependency,
# so the drop-in swap is relink-free.
#
# PARITY CONTRACT (arbitrated by build-parity/):
#   file:          libscisound.2027.dylib (+ libscisound.dylib symlink)
#   install_name:  /usr/local/lib/scilab/libscisound.2027.dylib
#   exports:       _doBeep _sci_PlaySound _sci_beep   (exactly these 3)
#   deps:          /usr/lib/libSystem.B.dylib only    (LANG C -> C linker driver;
#                  a C++ link would drag in libc++)
#
# Sources: only the two gateway C files — src/c/DllmainSound.c and src/nosound/
# are Windows-only (the autotools build excludes them too).
# EXTRA_INCLUDES: the autotools compile line carries fileio/includes
# (expandPathVariable.h, used by sci_PlaySound.c) and string/includes
# (os_string.h, used by sci_beep.c); neither is in SCILAB_DEFAULT_INCLUDES.

scilab_module(sound
  GATEWAY_SOURCES
    sci_gateway/c/sci_beep.c
    sci_gateway/c/sci_PlaySound.c
  LANG C
  EXTRA_INCLUDES
    ${SCILAB_SOURCE_DIR}/modules/fileio/includes
    ${SCILAB_SOURCE_DIR}/modules/string/includes
  CLASS DYNAMIC_LOAD
  SYMBOLS 3)
