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

# Where a GENERATED_FILES entry's CMake-written copy lives, for the handful of entries
# NOT at the default build-cmake/generated/<rel> path the loop in fingerprint_build
# assumes. Only version.h needs this: it lands in build-cmake/generated-includes/version.h
# (a header-only directory, no modules/core/includes/ prefix), not build-cmake/generated/
# modules/core/includes/version.h. machine.h sits in that SAME generated-includes/
# directory and is deliberately left OUT of this map -- it already gets its own semantic
# comparison (header_defines, further down) because CMake's machine.h is not
# byte-identical to configure's, unlike version.h, which is (its generating stage proved
# that), so the plain byte-hash mechanism applies directly. etc/classpath.xml has no
# CMake counterpart at all yet (deferred to Stage 2) and is likewise absent. Mapped
# explicitly, one entry at a time, rather than derived by some general rule (e.g.
# "headers go to generated-includes/") -- there is no such rule, only per-file facts, and
# a derived guess would silently mis-route the next file that does not fit the pattern.
#
# GENERALIZED LESSON -- this is the THIRD time this exact class of gap has been found
# (machine.h, before header_defines existed to cover it; the ten RC-c files, before
# generated_cmake existed at all; now version.h): the `generated` dict below ALWAYS
# hashes configure's OWN copy of a file, on both the baseline and candidate side, no
# matter which build produced the fingerprint -- it is structurally blind to anything
# CMake writes. Any new CMake-generated artifact needs an explicit entry HERE (or, if it
# is not byte-identical across generators, its own semantic dimension, machine.h's route)
# or it is completely unguarded: a capture will happily hash a corrupted or stale CMake
# output and never notice, exactly like this file's own history.
_GENERATED_CMAKE_PATH_OVERRIDES = {
    "modules/core/includes/version.h": os.path.join("generated-includes", "version.h"),
}

# Key for the macro manifest entry in the "generated" map: one hash over every
# compiled macro's PATH **and CONTENT** (RC-d). It was path-only through RC-c --
# enough to catch a module's macros vanishing (the rc=231 shape), but blind to a
# .bin present at the right path with wrong bytes, which is exactly what
# migrating the macro compiler risks.
#
# RC-d final-review Minor 1: also covers each module's `lib` (e.g.
# modules/core/macros/lib) -- the XML index Scilab actually loads to resolve a
# macro NAME to its .bin path and md5, not a byproduct of building one. Every
# .bin byte could match while a corrupted lib (wrong library-name argument,
# truncated write) left macros unresolvable at runtime, and this manifest would
# not have noticed. Folded into the SAME key (not a new "lib (manifest)" entry)
# and the SAME path\0sha256hex entry shape as the .bin entries -- see the
# fingerprint_build loop below. The key keeps its original ".bin"-only name for
# continuity with the existing baseline entry even though its coverage is now
# broader.
#
# Content hashing is strict rather than flaky because .bin/lib output is
# REPRODUCIBLE FOR A FULL BUILD FROM A PURGED TREE -- measured before RC-d: two
# independent full rebuilds (.bin AND lib deleted first under every
# modules/*/macros/, since genlib is incremental) produced 0 of 3516 .bin files
# differing (81 of 81 lib files likewise, measured for the RC-d final-review
# fix), and both reproduced the pre-existing on-disk state.
#
# RC-d final-review Important 2: that reproducibility claim needs a caveat this
# comment used to omit -- .bin/lib output is NOT a pure function of the sources.
# Every .bin embeds AST node numbers from a process-wide counter that never
# resets (ast.hxx:42, `nodeNumber = globalNodeNumber++`) and gets serialized
# (serializervisitor.hxx:103, under a `saveNodeNumber` flag that DEFAULTS TRUE).
# genlib's own incremental skip (sci_genlib.cpp:266-276) `continue`s BEFORE
# parser.parseFile ever runs for a skipped file, so a skipped file never
# advances the counter either -- which files a build actually reparses, and in
# what order, changes the root node numbers of the ones it does. Measured:
# deleting only who_user.bin and fftshift.bin from an otherwise-full tree and
# rebuilding assigned them root node numbers 448 and 1484, versus 2030 and
# 423875 from a full build of the identical sources. Same sources, different
# bytes -- determinism here is a property of the FULL-PURGED-REBUILD procedure,
# not of the source tree by itself.
#
# Practical consequence: a capture taken after an INCREMENTAL macro rebuild (a
# developer deletes a stray .bin, or touches then reverts a .sci, then runs a
# plain build) can legitimately differ from one taken after a full purged
# rebuild, with no source regression at all. If this manifest ever mismatches,
# do not trust that in isolation -- delete *.bin and lib under every
# modules/*/macros/ and re-run a FULL macro build before concluding anything;
# investigate a mismatch that survives THAT, do not weaken this back to
# presence.
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
    maven_jars = {}
    macro_manifest_entries = []
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
            # modules/assert/macros/assert/assert_checkerror.bin) PLUS each module's
            # `lib` (e.g. modules/core/macros/lib) -- the XML index Scilab actually
            # loads to resolve a macro NAME to its .bin path and md5 (RC-d
            # final-review Minor 1: every .bin byte could match while a corrupted
            # lib left macros unresolvable at runtime, so `lib` needs the same
            # coverage). PATH and CONTENT are both captured for both kinds, folded
            # into the SAME manifest entry (see MACRO_BIN_MANIFEST_KEY above) --
            # catches a module's macros silently vanishing from the build, a .bin
            # present at the right path with the wrong bytes, AND a corrupted lib.
            for fn in files:
                if fn.endswith(".bin") or fn == "lib":
                    p = os.path.join(root, fn)
                    rel = os.path.relpath(p, build_dir).replace(os.sep, "/")
                    # BINARY read for both kinds -- .bin files are serialized ASTs;
                    # `lib` happens to be XML text, but reading it the same way
                    # keeps one code path and avoids an encoding decision. (The
                    # text readers elsewhere in this file pin encoding="utf-8";
                    # that is the wrong tool here and would risk corrupting the
                    # hash for either kind.)
                    with open(p, "rb") as f:
                        macro_manifest_entries.append(
                            rel + "\0" + hashlib.sha256(f.read()).hexdigest())
        elif "/modules/" in posix_root + "/" and posix_root.endswith("/jar"):
            # modules/<m>/jar/*.jar — the Ant-built module jars. Content manifest
            # (fingerprint_jar), NOT byte hash: jars embed timestamps + non-det zip
            # order. Only modules/*/jar (not thirdparty/ or a build-cache jar dir),
            # and never the doc build's output jars (_DOC_OUTPUT_JAR above).
            for fn in files:
                if fn.endswith(".jar") and not _DOC_OUTPUT_JAR.match(fn):
                    rel = os.path.relpath(os.path.join(root, fn), build_dir).replace(os.sep, "/")
                    jars[rel] = fingerprint_jar(os.path.join(root, fn))
        elif "/modules/" in posix_root + "/" and posix_root.endswith("/target"):
            # modules/<m>/target/*.jar — Maven's module jars. TOP LEVEL of target/
            # ONLY: os.walk visits target/classes, target/generated-sources, and
            # target/maven-status as separate root values in their own turn of
            # this loop (posix_root there ends in "/classes",
            # "/generated-sources", or "/maven-status", never "/target"), so
            # this branch never sees them -- the same one-directory-per-visit
            # property the Ant-jar branch above relies on for its own exclusions.
            # (Those three are what actually exists on disk today; a
            # maven-archiver/ subdir, which some Maven jar plugins create, does
            # NOT appear in this tree -- the parent POM sets
            # addMavenDescriptor=false, see modules/commons/pom.xml. Exclusion
            # here is by DIRECTORY, not by an enumerated subdir list, so this
            # parenthetical is informational, not load-bearing: a future subdir
            # this loop has never seen is excluded the same way.)
            #
            # KEYED UNDER modules/<m>/jar/<basename> -- ANT's output path, NOT
            # Maven's. Stage 2-c design doc S2.1: this key is DELIBERATELY
            # SYNTHETIC, the same move as _GENERATED_CMAKE_PATH_OVERRIDES above --
            # Maven really writes here, to modules/<m>/target/, and keeps doing so
            # even after this section exists (the design doc's Decision A changes
            # the BASENAME via a parent-POM <finalName>, never the directory; the
            # directory only flips at the eventual CMake/Ant swap). A future
            # reader must not mistake this key for Maven's real on-disk location
            # -- that location is `root`/fn, one path segment over (target/, not
            # jar/), and is what `fingerprint_jar` below actually opens.
            #
            # Aligning the key to Ant's path is what makes `maven_jars` and `jars`
            # directly comparable dicts (parity/diff.py): a jar the two
            # toolchains name DIFFERENTLY occupies two different keys under this
            # scheme -- an added key on one side, a removed key on the other,
            # i.e. a visible rename -- while a jar they name the SAME way lands
            # on one shared key and is compared entry-by-entry, exactly like
            # `jars` already does. CURRENT STATE (Stage 2-c Decision A, shipped):
            # the parent POM's own
            # <finalName>org.scilab.modules.${project.artifactId}</finalName>
            # (scilab/pom.xml, not this file -- there is no <finalName> "above"
            # here) makes every Maven jar's basename MATCH its Ant counterpart
            # today (e.g. both sides write org.scilab.modules.commons.jar), so a
            # real module lands on one shared key, as designed. A basename that
            # diverges again -- a per-module <finalName> override, a typo'd
            # artifactId, a module POM that forgets to inherit the parent -- is
            # a REGRESSION this key scheme exists to catch (an orphan key on
            # each side), not an expected, tolerated mismatch.
            rel_root = os.path.relpath(root, build_dir).replace(os.sep, "/")
            parts = rel_root.split("/")
            # BOTH checks are required to pin the shape to modules/<m>/target
            # exactly -- len(parts) == 3 alone does not. The outer "/modules/"
            # in posix_root pre-filter above is satisfied by a modules
            # component ANYWHERE in the walked path (including one that is
            # part of build_dir's own absolute path, not just relative to it),
            # so length alone would also match e.g. thirdparty/modules/target/
            # (a stray "modules" component) or, when build_dir's own path
            # contains "/modules/", any two-segment subtree ending in target/
            # such as aaa/bbb/target/. Because this branch REWRITES the path
            # into a synthetic modules/<m>/jar/<basename> key, either
            # mis-capture would be laundered into something indistinguishable
            # from a real module jar -- unlike the Ant `jars` branch above,
            # which keys by the real relpath, so a mis-capture there stays
            # visibly wrong. Demonstrated: thirdparty/modules/target/vendored.jar
            # would otherwise be captured as modules/modules/jar/vendored.jar.
            #
            # LIMITATION, NARROWED BY THE COMPLETENESS CHECK (review Fix 4, then
            # narrowed again at final review): the SAME exact-length-3
            # requirement means a NESTED target/ -- e.g.
            # modules/<m>/sub/target/x.jar, from some future multi-artifact
            # module -- is invisible to THIS WALK (parts there has length 4, so
            # `len(parts) == 3` excludes it before parts[0] is even checked): no
            # key is ever written for it. Two different cases follow from that,
            # and only one is still silent:
            #   - A declared reactor module that is ITSELF nested (e.g. a
            #     hypothetical <module>modules/foo/sub</module>) produces ALL
            #     its jars under a path this walk never visits, so it gets NO
            #     maven_jars key at all -- and that IS caught now:
            #     _missing_reactor_jars (the completeness check,
            #     build-parity/tests/test_acceptance.py) parses the reactor's
            #     own <modules> list and fails loudly, naming the module, when
            #     nothing under its prefix exists.
            #   - An EXTRA nested artifact inside an otherwise-normal module
            #     (e.g. modules/commons/sub/target/extra.jar, alongside the
            #     real modules/commons/target/x.jar that DOES get captured
            #     normally) is still invisible: the module already has a
            #     passing top-level jar, so completeness has nothing to object
            #     to, and nothing else looks for a nested EXTRA. That narrower
            #     case remains the WRONG failure direction for a parity gate
            #     -- silent, not loud -- and is the live limitation this
            #     comment now describes.
            # No Scilab module is nested today (all 24 Ant jars sit at
            # modules/<m>/jar/ directly), so there is no live instance of
            # either case. Do NOT close the remaining gap by broadening the
            # walk (e.g. dropping the length check, or matching on parts[0] ==
            # "modules" alone) -- that is exactly the laundering bug the
            # parts[0] == "modules" check above exists to prevent (a stray
            # "modules" path component anywhere getting rewritten into a
            # plausible-looking synthetic key). If a nested module or a
            # multi-artifact module ever arrives, widen this deliberately and
            # re-derive the synthetic-key scheme for it; don't just relax the
            # guard.
            if len(parts) == 3 and parts[0] == "modules":
                module = parts[1]
                # NO filename filter here, unlike the Ant branch's _DOC_OUTPUT_JAR
                # above -- deliberate, not an oversight. target/ is actually MORE
                # prone to auxiliary jars than jar/ is: -sources.jar, -javadoc.jar,
                # -tests.jar, and original-*.jar (the shade/assembly plugins' own
                # pre-relocation copy) are all standard Maven conventions that a
                # plugin can place right next to the real artifact. Harmless today
                # -- no such plugin is configured in any module POM, and an
                # unexpected extra key fails RED (an "extra in candidate" diff),
                # which is the safe failure direction. Anyone adding
                # maven-source-plugin or maven-javadoc-plugin (routine for
                # `install`/`deploy` executions) would start producing spurious
                # orphan keys here -- that is the trigger to revisit this, not a
                # silent breakage discovered later. Not adding a filter
                # preemptively: a gate that silently DROPS an artifact it was
                # never told to expect is worse than one that over-reports one,
                # and there is no real plugin configuration yet to filter FOR.
                for fn in files:
                    if fn.endswith(".jar"):
                        key = f"modules/{module}/jar/{fn}"
                        # Same precedent as the dylib key collision above: unreachable
                        # today (a given modules/<m>/target is visited once by os.walk,
                        # and a directory cannot hold two files of the same name, so
                        # nothing can yet map two different jars onto one key) -- but
                        # fail loudly rather than silently overwrite if that ever
                        # changes, instead of leaving a stale artifact from one file
                        # masquerading as the other.
                        if key in maven_jars:
                            raise ValueError(
                                f"maven jar key collision: {key} (second file: "
                                f"{os.path.join(root, fn)}). Two jars map to one "
                                f"fingerprint key -- likely a stale artifact in target/; "
                                f"clean the build tree.")
                        maven_jars[key] = fingerprint_jar(os.path.join(root, fn))

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

    manifest = "\n".join(sorted(macro_manifest_entries))
    generated[MACRO_BIN_MANIFEST_KEY] = hashlib.sha256(manifest.encode("utf-8")).hexdigest()

    # RC-c final-review Finding (Critical): CMake's OWN copies of the generated files
    # (build-cmake/generated/, ScilabGeneratedFiles.cmake:19), hashed the SAME way as the
    # block above -- normalize_path(..., roots) then sha256, encoding="utf-8" -- but resolved
    # against CMake's output directory instead of the source tree. This is the half of the
    # gate that was MISSING: the block above resolves every GENERATED_FILES entry against
    # build_dir, which is always the SOURCE TREE (configure's copy) no matter which build
    # produced this fingerprint, so it can never see what CMake wrote -- it just re-hashes
    # configure's output a second time. A candidate captured that way compares
    # configure-vs-configure even when build-cmake/generated/ is silently corrupted (proven:
    # corrupting Version.incl/scilab.pc/etc/logging.properties there still hashed clean).
    # `generated_cmake` plus diff.py's matching block is what actually asserts "CMake wrote
    # what configure wrote".
    #
    # Reuses GENERATED_FILES as the candidate path list rather than a second hardcoded one,
    # resolving each entry against build-cmake/generated/<rel> UNLESS
    # _GENERATED_CMAKE_PATH_OVERRIDES (defined above, by GENERATED_FILES) names a different
    # path for it -- version.h's case, which lives under build-cmake/generated-includes/
    # instead. etc/classpath.xml (no CMake counterpart, deferred to Stage 2) and machine.h
    # (compared by header_defines instead, right below) have no override and no file at the
    # default path, so they fall through the same `os.path.exists` guard as any other
    # missing file and are silently absent here, exactly like a missing file above.
    #
    # Always present in the returned fingerprint, like header_defines below, even when
    # build-cmake/ does not exist at all (an autotools-only capture): an empty dict, never a
    # missing key. That is load-bearing for the diff's transition rule -- it is the only way
    # to tell "captured by an old capture.py, before this fix" (key truly absent) apart from
    # "captured by this tool, found nothing" (key present, empty).
    generated_cmake = {}
    cmake_root = os.path.join(build_dir, "build-cmake")
    for rel in GENERATED_FILES:
        override = _GENERATED_CMAKE_PATH_OVERRIDES.get(rel)
        p = os.path.join(cmake_root, override) if override else os.path.join(cmake_root, "generated", rel)
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8", errors="replace") as f:
                content = normalize_path(f.read(), roots)
            generated_cmake[rel] = hashlib.sha256(content.encode("utf-8", "replace")).hexdigest()

    # The CMake-GENERATED machine.h (RC-a), compared SEMANTICALLY against configure's
    # macro set (the baseline's reference, armed from the source-tree header). Absent
    # until RC-a's generator lands -> section simply empty (the diff's transition rule).
    header_defines = {}
    gen_machine = os.path.join(build_dir, "build-cmake", "generated-includes", "machine.h")
    if os.path.exists(gen_machine):
        with open(gen_machine, "r", errors="replace", encoding="utf-8") as f:
            header_defines["machine.h"] = parse_defines(f.read())

    return {"build_id": build_id, "executables": executables,
            "dylibs": dylibs, "generated": generated, "generated_cmake": generated_cmake,
            "jars": jars,
            "maven_jars": maven_jars,
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
          f"{len(fp['maven_jars'])} maven jars, "
          f"{len(fp['generated'])} generated files, "
          f"{len(fp['generated_cmake'])} generated files (cmake), "
          f"{len(fp['header_defines'])} semantic headers, "
          f"flags[{fp['flags']['source']}], "
          f"{len(fp['tu_flag_facts']['overrides'])} flag-override TUs -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
