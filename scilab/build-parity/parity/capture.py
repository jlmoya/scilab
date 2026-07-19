"""Walk a built tree and emit a fingerprint (see the shared schema)."""
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

from parity.fingerprint import (parse_nm, parse_otool_libs, parse_build_version,
                                parse_flag_facts, parse_rpaths, normalize_version,
                                normalize_path, normalize_manifest, parse_defines)
from parity.makeflags import makefile_tu_facts, LANG_BY_SUFFIX

# Files config.status substitutes, byte-hashed after root normalization. The three
# original entries plus RC-c's ten. Byte hash (not semantic) is right here: these are
# scalar-substitution templates -- configure_file(@ONLY) reproduces autoconf's @VAR@
# expansion exactly when the values match, which version.h proved. machine.h is the
# exception that needed a semantic dimension, for a reason none of these share.
#
# Version.incl is NOT an AC_CONFIG_FILES entry -- it is written by a conditional shell
# echo at configure.ac:2965 -- so an inventory built from config.status misses it
# entirely, while build.incl.xml:154 stamps every jar's Specification-Version from it.
#
# scilab-lib.properties and scilab-lib-doc.properties are deliberately ABSENT: they and
# etc/classpath.xml carry 115 of the inventory's 142 substitutions, all jar paths from
# AC_JAVA_CHECK_JAR's filesystem search, and are consumed only by the Ant build that
# Stage 2 replaces. (etc/classpath.xml predates RC-c and stays.) RC-c design doc S4.
GENERATED_FILES = [
    "etc/classpath.xml",
    "modules/core/includes/machine.h",
    "modules/core/includes/version.h",
    "build.incl.xml",
    "scilab.pc",
    "scilab.properties",
    "etc/logging.properties",
    "etc/modules.xml",
    "etc/Info.plist",
    "modules/helptools/etc/SciDocConf.xml",
    "modules/atoms/etc/repositories",
    "modules/atoms/tests/unit_tests/repositories.orig",
    "Version.incl",
]

# Key for the macro .bin *manifest* entry in the "generated" map (see
# _macro_bin_manifest_hash): presence of the SET of compiled macro files, not
# their content -- if a Stage-1 CMake bootstrap misses a module, its .bin files
# vanish from this list and the manifest hash changes.
MACRO_BIN_MANIFEST_KEY = "macros/*.bin (manifest)"

# OUTPUT jars of the opt-in help/doc build (CMake `doc` target / `make doc`),
# which land in modules/helptools/jar/ NEXT TO the real module jar: one
# scilab_<locale>_help.jar per ALL_LINGUAS_DOC locale plus scilab_images.jar.
# They are documentation artifacts -- locale-dependent, multi-megabyte, gated
# by the doc target's own acceptance check -- NOT part of the module-jar
# parity contract; capturing them would make parity depend on whether (and
# for which locales) help happened to be built. Excluded by FILENAME, not by
# directory: org.scilab.modules.helptools.jar in that same directory IS a
# real module jar (it is in the committed baseline) and must stay captured.
_DOC_OUTPUT_JAR = re.compile(r"^scilab_(.*_help|images)\.jar$")


def _subprocess_runner(cmd):
    return subprocess.run(cmd, capture_output=True, text=True, check=False).stdout


def _file_reader(path):
    try:
        # encoding="utf-8" is load-bearing, not decorative: without it, Python
        # decodes with the CAPTURING PROCESS's locale-preferred codec (LANG=C or
        # any non-UTF-8-locale machine/container gets ascii/latin-1), which is
        # unrelated to the FILE's actual encoding. UTF-8 is the only codec these
        # files are ever written in.
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


# config.status spells the per-language flags as S["SCI_CFLAGS"]="..." lines.
_SCI_FLAG_VARS = {"c": "SCI_CFLAGS", "cxx": "SCI_CXXFLAGS", "f": "SCI_FFLAGS"}
# compile_commands.json groups by source-file extension (lowercased first).
_CMAKE_EXT_LANG = {".c": "c", ".cc": "cxx", ".cpp": "cxx", ".cxx": "cxx",
                   ".f": "f", ".f90": "f", ".f95": "f"}


def capture_flag_manifest(build_dir, reader=_file_reader):
    """Effective per-language compiler-flag facts: {"source", "c", "cxx", "f"}.

    Closes the harness's codegen blind spot: a dropped -fwrapv or an -O2->-O0
    slip changes no exported symbol, link edge, or SDK stamp, so the binary
    fingerprint stayed green while every C file compiled unoptimized (the
    regression fixed in 516c57573cc). v1 captures the GLOBAL per-language
    flags only -- known limitation: per-TU overrides (e.g. differential_equations
    forcing colnew.f to -O0 on macOS) are invisible under autotools, and under
    CMake -- where the FIRST compile_commands.json entry per language stands in
    for the global flags -- an overridden TU that happens to land first would be
    MISTAKEN for the global fact, not merely missed.

    `reader(path) -> str | None` is injected for unit tests, mirroring the
    `runner` injection of the fingerprint functions.
    """
    text = reader(os.path.join(build_dir, "config.status"))
    if text is not None:
        manifest = {"source": "autotools"}
        for lang, var in _SCI_FLAG_VARS.items():
            # Autoconf splits any value longer than 148 chars across backslash-
            # continuation lines -- `"…first…"\` newline `"…rest…"` -- and the cut
            # lands MID-TOKEN, so the segments are joined by DIRECT concatenation
            # (no space). A first-segment-only read would silently truncate
            # SCI_CFLAGS the moment it crosses the cliff (140 chars today) and
            # could split a token like -fwrapv into "-fwr"|"apv".
            m = re.search(r'S\["%s"\]="([^"]*)"((?:\\\n"[^"]*")*)' % var, text)
            if m:
                value = m.group(1) + "".join(re.findall(r'"([^"]*)"', m.group(2)))
                manifest[lang] = parse_flag_facts(value)
            else:
                manifest[lang] = None
        return manifest

    text = reader(os.path.join(build_dir, "compile_commands.json"))
    if text is not None:
        manifest = {"source": "cmake", "c": None, "cxx": None, "f": None}
        for entry in json.loads(text):
            lang = _CMAKE_EXT_LANG.get(os.path.splitext(entry.get("file", ""))[1].lower())
            if lang is None or manifest[lang] is not None:
                continue   # one representative TU per language (global facts, v1)
            cmd = entry.get("command") or " ".join(entry.get("arguments", []))
            if cmd:
                manifest[lang] = parse_flag_facts(cmd)
        return manifest

    return {"source": "unknown", "c": None, "cxx": None, "f": None}


# Derived per-TU flag expectation (RC-b). Stored as a tree-wide default plus ONLY
# the TUs that deviate -- a few hundred entries instead of ~3600, and it maps
# directly onto how flagfacts_check asks the question ("what is expected of THIS
# file?"). The design's original "~40" estimate undercounted: the known-answer
# validation (Step 5) measures 211 on the real tree -- 146 from
# modules/differential_equations, 16 from spreadsheet's deliberate -std=c++20, 25
# from string's (and a handful of siblings') _CFLAGS-replaces-AM_CFLAGS footgun,
# 13 from mpi's wrapper CC (no -std= token -- see the CAVEAT below), and the rest
# genuine per-TU divergences (the macOS gfortran -O0 workarounds). A couple
# hundred is the expected shape; a count in the THOUSANDS would instead mean an
# empty `defaults` making every TU compare unequal -- investigate, don't
# rebaseline, if that recurs.
#
# The differential_equations 146 is NOT "~145 from the vendored patched_sundials
# subtree", despite an earlier draft of this comment claiming that -- measured:
# only 103 of the 146 are under src/patched_sundials/. The other 43 are the
# module's OWN gateway/manager files (e.g. sci_gateway/cpp/sci_ida.cpp,
# src/c/Ex-daskr.c), which inherit the SAME -fopenmp fact via the shared
# libscidifferential_equations_la_CPPFLAGS block that also covers the vendored
# subtree -- a real deviation, just not a patched_sundials one.
#
# CAVEAT on the mpi 13: genuinely derived from modules/mpi/Makefile (CC =
# $(OPENMPI_CC), which IS blank on this machine, so its recipes really do lose
# the -std=gnu23 token that normally arrives via $(CC) -- not a parser
# artifact), but INERT for flagfacts_check today: build-cmake/compile_commands.json
# carries ZERO modules/mpi entries here (CMake does not build the module on this
# machine either), so the gate never walks these 13 files to check them against
# this expectation. Read "211" as "211 derived facts", not "211 CMake-verified
# deviations" -- 13 of them currently guard nothing.
#
# FROZEN ON PURPOSE: RC-e deletes the generated Makefiles this is derived from, so
# the committed baseline is what lets the autotools-derived truth outlive autotools.
_DEFAULT_DEVIATION_LIMIT = 8

def capture_tu_flag_facts(source_root):
    modules = os.path.join(source_root, "modules")
    per_module, defaults_seen = {}, {}
    for name in sorted(os.listdir(modules)) if os.path.isdir(modules) else []:
        mk = os.path.join(modules, name, "Makefile")
        if not os.path.isfile(mk):
            continue
        with open(mk, errors="replace", encoding="utf-8") as f:
            facts = makefile_tu_facts(f.read())
        per_module[name] = facts
        for lang, d in facts["defaults"].items():
            defaults_seen.setdefault(lang, []).append(json.dumps(d, sort_keys=True))

    # The tree-wide default is the MODAL per-module suffix-rule result, not a
    # hand-picked representative -- picking one module is the same "representative
    # TU" weakness that makes the global `flags` row a non-gate.
    defaults = {}
    for lang, seen in defaults_seen.items():
        modal = max(set(seen), key=seen.count)
        deviants = len(seen) - seen.count(modal)
        if deviants > _DEFAULT_DEVIATION_LIMIT:
            raise RuntimeError(
                f"{lang}: {deviants} modules deviate from the modal default -- "
                "'the tree-wide default' is not a real notion here; investigate "
                "before trusting this capture")
        defaults[lang] = json.loads(modal)

    overrides = {}
    for name, facts in per_module.items():
        for relsrc, tu in facts["explicit"].items():
            lang = LANG_BY_SUFFIX.get(relsrc.rsplit(".", 1)[-1])
            if lang and tu != defaults.get(lang):
                overrides[f"modules/{name}/{relsrc}"] = tu
    return {"defaults": defaults, "overrides": overrides}


def _normalize_entry(entry, roots):
    return normalize_path(entry, roots) if entry else entry


def fingerprint_dylib(path, roots, runner=_subprocess_runner):
    syms = parse_nm(runner(["nm", "-gU", path]))
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    rpaths = parse_rpaths(runner(["otool", "-l", path]))
    return {
        "symbols": syms,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
        # LC_RPATH list: ORDER preserved (dyld searches rpaths in order --
        # deliberately NOT sorted, unlike deps), each entry roots-normalized
        # like every other path field so a build-tree/$HOME rpath (the real
        # tree has /Users/.../xlnt-prefix/lib) stays relocatable.
        "rpaths": [_normalize_entry(r, roots) for r in rpaths],
    }


def fingerprint_jar(path, opener=zipfile.ZipFile):
    """A jar (zip) -> {entry_name: sha256hex(content)}. Reads each entry's CONTENT,
    NOT the zip container's per-entry timestamp or ordering, so two jars with
    identical files but different build times / entry order fingerprint identically.
    META-INF/MANIFEST.MF is normalize_manifest()'d first (strip tool-version lines).
    Directory entries (no content) are skipped. `opener` is injected for tests."""
    out = {}
    with opener(path) as zf:
        for name in sorted(zf.namelist()):
            if name.endswith("/"):
                continue
            data = zf.read(name)
            if name == "META-INF/MANIFEST.MF":
                data = normalize_manifest(data.decode("utf-8", "replace")).encode("utf-8")
            out[name] = hashlib.sha256(data).hexdigest()
    return out


def _fingerprint_exe(path, roots, runner):
    load_cmds = runner(["otool", "-l", path])   # one stream feeds build_version AND rpaths
    bv = parse_build_version(load_cmds)
    libs = parse_otool_libs(runner(["otool", "-L", path]))
    return {
        "build_version": bv,
        "install_name": _normalize_entry(libs["install_name"], roots),
        "deps": sorted(_normalize_entry(d, roots) for d in libs["deps"]),
        "tmp_leak": libs["tmp_leak"],
        # Same LC_RPATH treatment as fingerprint_dylib (order-significant,
        # roots-normalized) -- executables are the rpath-load-bearing case
        # (scilab-bin resolves @rpath JDK libs).
        "rpaths": [_normalize_entry(r, roots) for r in parse_rpaths(load_cmds)],
    }


def fingerprint_build(build_dir, roots, runner=_subprocess_runner, build_id="build"):
    dylibs = {}
    jars = {}
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
        elif "/modules/" in posix_root + "/" and posix_root.endswith("/jar"):
            # modules/<m>/jar/*.jar — the Ant-built module jars. Content manifest
            # (fingerprint_jar), NOT byte hash: jars embed timestamps + non-det zip
            # order. Only modules/*/jar (not thirdparty/ or a build-cache jar dir),
            # and never the doc build's output jars (_DOC_OUTPUT_JAR above).
            for fn in files:
                if fn.endswith(".jar") and not _DOC_OUTPUT_JAR.match(fn):
                    rel = os.path.relpath(os.path.join(root, fn), build_dir).replace(os.sep, "/")
                    jars[rel] = fingerprint_jar(os.path.join(root, fn))

    executables = {}
    for name in ("scilab-bin", "scilab-cli-bin"):
        p = os.path.join(build_dir, ".libs", name)
        if os.path.exists(p):
            executables[name] = _fingerprint_exe(p, roots, runner)

    generated = {}
    for rel in GENERATED_FILES:
        p = os.path.join(build_dir, rel)
        if os.path.exists(p):
            # encoding="utf-8": see _file_reader's comment above -- two of these
            # (scilab.properties, etc/Info.plist) embed non-ASCII bytes ("Dassault
            # Systèmes", "©"), so an implicit locale-dependent decode here would
            # hash differently on a non-UTF-8-locale machine than the committed
            # baseline, a false PARITY FAILED against an unmodified tree.
            with open(p, "r", encoding="utf-8", errors="replace") as f:
                content = normalize_path(f.read(), roots)
            generated[rel] = hashlib.sha256(content.encode("utf-8", "replace")).hexdigest()

    manifest = "\n".join(sorted(macro_bins))
    generated[MACRO_BIN_MANIFEST_KEY] = hashlib.sha256(manifest.encode("utf-8")).hexdigest()

    # The CMake-GENERATED machine.h (RC-a), compared SEMANTICALLY against configure's
    # macro set (the baseline's reference, armed from the source-tree header). Absent
    # until RC-a's generator lands -> section simply empty (the diff's transition rule).
    header_defines = {}
    gen_machine = os.path.join(build_dir, "build-cmake", "generated-includes", "machine.h")
    if os.path.exists(gen_machine):
        with open(gen_machine, "r", errors="replace", encoding="utf-8") as f:
            header_defines["machine.h"] = parse_defines(f.read())

    return {"build_id": build_id, "executables": executables,
            "dylibs": dylibs, "generated": generated, "jars": jars,
            "header_defines": header_defines,
            "flags": capture_flag_manifest(build_dir),
            "tu_flag_facts": capture_tu_flag_facts(build_dir)}


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
          f"{len(fp['jars'])} jars, "
          f"{len(fp['generated'])} generated files, "
          f"{len(fp['header_defines'])} semantic headers, "
          f"flags[{fp['flags']['source']}], "
          f"{len(fp['tu_flag_facts']['overrides'])} flag-override TUs -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
