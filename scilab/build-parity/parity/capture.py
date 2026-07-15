"""Walk a built tree and emit a fingerprint (see the shared schema)."""
import hashlib
import json
import os
import subprocess
import sys

from parity.fingerprint import (parse_nm, parse_otool_libs, parse_build_version,
                                normalize_version, normalize_path)

GENERATED_FILES = [
    "etc/classpath.xml",
    "modules/core/includes/machine.h",
    "modules/core/includes/version.h",
]

# Key for the macro .bin *manifest* entry in the "generated" map (see
# _macro_bin_manifest_hash): presence of the SET of compiled macro files, not
# their content -- if a Stage-1 CMake bootstrap misses a module, its .bin files
# vanish from this list and the manifest hash changes.
MACRO_BIN_MANIFEST_KEY = "macros/*.bin (manifest)"


def _subprocess_runner(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=False).stdout


def _normalize_entry(entry, roots):
    return normalize_path(entry, roots) if entry else entry


def fingerprint_dylib(path, roots, runner=_subprocess_runner):
    syms = parse_nm(runner(["nm", "-gU", path]))
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    return {
        "symbols": syms,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
    }


def _fingerprint_exe(path, roots, runner):
    bv = parse_build_version(runner(["otool", "-l", path]))
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    return {
        "build_version": bv,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
    }


def fingerprint_build(build_dir, roots, runner=_subprocess_runner, build_id="build"):
    dylibs = {}
    macro_bins = []
    for root, _dirs, files in os.walk(build_dir):
        posix_root = root.replace(os.sep, "/")
        if posix_root.endswith("/.libs"):
            for fn in files:
                path = os.path.join(root, fn)
                # Real files only (skip the bare-name symlinks and non-dylibs). Skip
                # by symlink-ness, NOT by "does the name carry a 4-digit version
                # token" -- that proxy silently dropped real built libs with
                # non-Scilab version schemes (e.g. libxlnt.1.6.1.dylib, vendored,
                # no 4-digit token).
                if fn.endswith(".dylib") and not os.path.islink(path):
                    key = normalize_version(fn)
                    if key in dylibs:
                        raise ValueError(
                            f"dylib key collision after version-normalization: {key} "
                            f"(second file: {path}). Two libraries map to one "
                            f"fingerprint key -- likely a stale artifact in .libs/; clean the build tree.")
                    dylibs[key] = fingerprint_dylib(path, roots, runner)
        elif "/macros/" in posix_root + "/":
            # Compiled macro .bin files (any depth under a macros/ dir, e.g.
            # modules/assert/macros/assert/assert_checkerror.bin). Only the SET of
            # paths is captured (see MACRO_BIN_MANIFEST_KEY below) -- cheap, and
            # enough to catch a module's macros silently vanishing from the build.
            for fn in files:
                if fn.endswith(".bin"):
                    rel = os.path.relpath(os.path.join(root, fn), build_dir)
                    macro_bins.append(rel.replace(os.sep, "/"))

    executables = {}
    for name in ("scilab-bin", "scilab-cli-bin"):
        p = os.path.join(build_dir, ".libs", name)
        if os.path.exists(p):
            executables[name] = _fingerprint_exe(p, roots, runner)

    generated = {}
    for rel in GENERATED_FILES:
        p = os.path.join(build_dir, rel)
        if os.path.exists(p):
            with open(p, "r", errors="replace") as f:
                content = normalize_path(f.read(), roots)
            generated[rel] = hashlib.sha256(content.encode("utf-8", "replace")).hexdigest()

    manifest = "\n".join(sorted(macro_bins))
    generated[MACRO_BIN_MANIFEST_KEY] = hashlib.sha256(manifest.encode("utf-8")).hexdigest()

    return {"build_id": build_id, "executables": executables,
            "dylibs": dylibs, "generated": generated}


def _default_roots(build_dir):
    return {os.path.abspath(build_dir): "$SCI", os.path.expanduser("~"): "$HOME"}


def _main(argv):
    if len(argv) < 3:
        print("usage: python -m parity.capture <build-dir> <out.json> [build_id]", file=sys.stderr)
        return 2
    build_dir, out = argv[1], argv[2]
    build_id = argv[3] if len(argv) > 3 else "build"
    fp = fingerprint_build(build_dir, _default_roots(build_dir), build_id=build_id)
    if not fp["dylibs"] and not fp["executables"]:
        # An empty tree is never a real Scilab build: either build_dir is wrong
        # (e.g. a relative path resolved against the wrong cwd) or the .libs walk
        # found nothing. Fail loudly instead of "succeeding" with an empty capture.
        print(f"error: captured 0 dylibs and 0 executables from build dir '{build_dir}' -- "
              f"the build dir is wrong or the .libs walk found nothing; refusing to write {out}",
              file=sys.stderr)
        return 2
    with open(out, "w") as f:
        json.dump(fp, f, indent=2, sort_keys=True)
    print(f"captured {len(fp['dylibs'])} dylibs, {len(fp['executables'])} executables, "
          f"{len(fp['generated'])} generated files -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
