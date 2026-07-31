#!/usr/bin/env bash
#
# Build and run the B22 defect-(2) signal-ownership probe. See the header of
# b22_signal_ownership.c for what it proves.
#
# Usage:  modules/core/tests/native/run_b22_signal_ownership.sh [build-dir]
#         build-dir defaults to build-cmake at the tree root.
#
# Deliberately NOT a CMake target: it links the CLI aggregate plus the whole
# `-disable`/`-minimal` stub tail (same requirement as the javasci2-cli variant,
# see modules/javasci/CMakeLists.txt), and adding an executable to the build
# graph would put it in front of the parity harness for no gain.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "${here}/../../../.." && pwd)"
build="${1:-${root}/build-cmake}"
mods="${build}/modules"

if [ ! -d "${mods}" ]; then
    echo "no built tree at ${mods} -- build first: cmake --build ${build}" >&2
    exit 2
fi

out="$(mktemp -d)"
trap 'rm -rf "${out}"' EXIT
bin="${out}/b22_signal_ownership"

# libscilab-cli does not fold the stub halves of the GUI modules; in a CLI
# process the EXECUTABLE supplies them, which is what we are standing in for.
stubs=(
    call_scilab/scicall_scilab
    gui/scigui-disable
    graphics/scigraphics-disable
    graphic_export/scigraphic_export-disable
    console/sciconsole-minimal
    action_binding/sciaction_binding-disable
    jvm/scijvm-disable
    xcos/scixcos-disable
    graphic_objects/scigraphic_objects-disable
    scinotes/sciscinotes-disable
    ui_data/sciui_data-disable
    history_browser/scihistory_browser-disable
    commons/scicommons-disable
    preferences/scipreferences-cli
    tclsci/scitclsci-disable
)

link_args=(-L"${mods}" -Wl,-rpath,"${mods}" -lscilab-cli)
for s in "${stubs[@]}"; do
    link_args+=(-L"${mods}/${s%%/*}" -Wl,-rpath,"${mods}/${s%%/*}" "-l${s##*/}")
done

cc -g -O0 -o "${bin}" "${here}/b22_signal_ownership.c" \
    "${link_args[@]}" \
    -Wl,-undefined,dynamic_lookup -Wl,-no_fixup_chains 2>&1 \
    | grep -v 'building for macOS' || true

export SCI="${root}"
rc=0

# Embedded: no arming, so the host's handler must still be there afterwards.
"${bin}" embedded || rc=$?
if [ "${rc}" -ne 0 ]; then
    echo "FAIL: embedded engine seized the host's SIGSEGV handler (B22 defect 2)" >&2
    exit 1
fi

# Standalone: armed, so Scilab must take the handler as it always has.
rc=0
"${bin}" standalone || rc=$?
if [ "${rc}" -ne 1 ]; then
    echo "FAIL: standalone REPL no longer installs its fatal handler (rc=${rc})" >&2
    exit 1
fi

echo "B22 signal-ownership probe OK (embedded keeps host handler, standalone installs its own)"
