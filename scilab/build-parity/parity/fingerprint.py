"""Pure parsers and normalizers: text from nm/otool -> structured data. No I/O."""

import re


def parse_nm(output):
    """`nm -gU` output -> sorted list of "<type> <symbol>" (address dropped).

    The address (first column) changes every build and is deliberately discarded;
    only the exported *set* and each symbol's linkage kind (T/D/...) are parity-relevant.
    """
    syms = []
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 3:
            syms.append(parts[1] + " " + " ".join(parts[2:]))
    return sorted(syms)


_TMP_MARKERS = ("/tmp", "/private/var/folders")

# otool -L's trailing "(compatibility version X, current version Y)". `current
# version` bumps on a routine `brew upgrade` of a system lib -- zero relation to
# Scilab -- which would otherwise flood every dependent dylib's diff with "link
# dependencies changed". Stripped so deps/install_name compare by PATH only.
# Anchored on the literal "compatibility version ..., current version ...)" text
# so it never touches an unrelated parenthetical (e.g. a synthetic dep "libc (v)").
_OTOOL_VERSION_SUFFIX = re.compile(r" \(compatibility version [^,]+, current version [^)]+\)$")


def _strip_otool_version_suffix(entry):
    return _OTOOL_VERSION_SUFFIX.sub("", entry)


def parse_otool_libs(output):
    """`otool -L` output -> {install_name, deps (sorted, self excluded), tmp_leak}."""
    entries = []
    for line in output.splitlines():
        if line.startswith("\t"):
            entries.append(line.strip())
    tmp_leak = any(any(m in e for m in _TMP_MARKERS) for e in entries)
    entries = [_strip_otool_version_suffix(e) for e in entries]
    install_name = entries[0] if entries else None
    deps = sorted(entries[1:])
    return {"install_name": install_name, "deps": deps, "tmp_leak": tmp_leak}


def parse_build_version(output):
    """`otool -l` LC_BUILD_VERSION block -> {minos, sdk}. First block wins."""
    minos = sdk = None
    in_block = False
    for line in output.splitlines():
        s = line.strip()
        if s.startswith("cmd LC_BUILD_VERSION"):
            in_block = True
        elif in_block and s.startswith("minos "):
            minos = s.split()[1]
        elif in_block and s.startswith("sdk "):
            sdk = s.split()[1]
            break
    return {"minos": minos, "sdk": sdk}


def parse_rpaths(otool_l_output):
    """Ordered LC_RPATH paths from `otool -l <dylib>`. Order is significant (dyld
    searches rpaths in order). Strips the trailing '(offset N)' otool annotation."""
    rpaths, in_rpath = [], False
    for line in otool_l_output.splitlines():
        s = line.strip()
        if s.startswith("cmd LC_RPATH"):
            in_rpath = True
        elif in_rpath and s.startswith("path "):
            rpaths.append(re.sub(r"\s*\(offset \d+\)\s*$", "", s[len("path "):]).strip())
            in_rpath = False
    return rpaths


# -O<level> only: anchored + case-sensitive so "-o foo.o" (the output flag) and
# "-ObjC" never match; the empty suffix is bare "-O" (which gcc/clang treat as -O1).
_OPT_TOKEN = re.compile(r"^-O([0-9a-z]*)$")


def parse_flag_facts(flagstring):
    """Compiler-flag string (or full compile command) -> semantic codegen facts.

    Semantic, NOT a raw string: autotools and CMake spell equivalent flag lines
    differently (order/duplicates/spelling), so a raw-string compare would fail
    parity on the migration itself. Only the facts that change generated code
    are extracted. The LAST -O<x> token wins (a trailing -O0 downgrade overrides
    an earlier -O2); no -O token at all means the compiler default, -O0. The
    -f pairs are likewise last-wins, matching the compiler: "-fwrapv … -fno-wrapv"
    is OFF (reading it as enabled would false-green the append-drift shape).
    """
    facts = {"opt": "O0", "wrapv": False, "min_macos": None,
             "openmp": False, "ndebug": False, "std": None}
    for tok in flagstring.split():
        m = _OPT_TOKEN.match(tok)
        if m:
            facts["opt"] = "O" + (m.group(1) or "1")   # bare -O means -O1
        elif tok == "-fwrapv":
            facts["wrapv"] = True
        elif tok == "-fno-wrapv":
            facts["wrapv"] = False
        elif tok.startswith("-mmacosx-version-min="):
            facts["min_macos"] = tok.split("=", 1)[1]
        elif tok == "-fopenmp" or tok.startswith("-fopenmp="):
            # Token-wise, so the clang "-Xpreprocessor -fopenmp" spelling counts too.
            facts["openmp"] = True
        elif tok == "-fno-openmp":
            facts["openmp"] = False
        elif tok == "-DNDEBUG":
            facts["ndebug"] = True
        elif tok.startswith("-std="):
            facts["std"] = tok.split("=", 1)[1]
    return facts


_VERSION_TOKEN = re.compile(r"\.\d{4}\.")


def normalize_version(name):
    """Collapse a 4-digit library version token: libsciX.2027.dylib -> libsciX.VER.dylib."""
    return _VERSION_TOKEN.sub(".VER.", name)


def normalize_path(text, roots):
    """Replace absolute-path prefixes (longest first) with placeholders, then version-normalize."""
    for prefix in sorted(roots, key=len, reverse=True):
        text = text.replace(prefix, roots[prefix])
    return normalize_version(text)


# MANIFEST.MF lines Ant/jar stamp with build-environment specifics (tool + JDK
# versions/vendor). Identical across two runs on the same machine, but they would
# make a jar's content hash Ant/JDK-version-dependent, defeating the point of
# comparing bytecode. Stripped before hashing so the manifest compares by its
# STABLE attributes only (Manifest-Version, Main-Class, Class-Path, package attrs).
#
# Implementation-Version is Scilab's Built-Date in disguise: build.incl.xml:157
# stamps it with "${DSTAMP} ${TSTAMP}" (build date + minute, e.g. "20260717 1645"),
# so every cross-minute rebuild changes it in all 23 sectioned module jars. Matched
# by FORM (8-digit date + 4-digit time) so a real semantic version in that
# attribute still compares as a stable fact.
#
# The two-build reproducibility probe (plan Task 3) claimed this list complete,
# but both probe builds ran within one minute — the Stage-1f-b cross-day jar
# rebuild (2026-07-17) is what exposed the DSTAMP/TSTAMP gap.
_MANIFEST_VOLATILE = re.compile(
    r"^(Ant-Version|Created-By|Built-By|Built-Date|Build-Jdk(-Spec)?|"
    r"Bnd-LastModified|Archiver-Version):"
    r"|^Implementation-Version: [0-9]{8} [0-9]{4}$", re.IGNORECASE)


def _join_manifest_continuations(lines):
    """Un-wrap JAR-spec continuation lines: a line beginning with a single leading
    space is a continuation of the previous line, with that one leading space
    stripped and the remainder appended directly (no separator). Reconstructs the
    logical value regardless of where -- or whether -- the writer chose to break it.

    A line that starts with a space but has no predecessor (a malformed manifest
    opening on a continuation) is kept as-is rather than raising, since this
    function's only job is reconstruction, not validation.
    """
    joined = []
    for line in lines:
        if line.startswith(" ") and joined:
            joined[-1] += line[1:]
        else:
            joined.append(line)
    return joined


def normalize_manifest(text):
    """Drop build-environment-volatile lines from a jar's META-INF/MANIFEST.MF so
    its content hash reflects only stable attributes. Preserves the order of
    surviving lines.

    Joins continuation lines FIRST, before filtering. A manifest's meaning is
    {attribute: value}; where the writer chose to break a long value across
    lines is a serialization artifact of the 72-byte-per-line limit -- the same
    class of thing as zip entry ordering (which fingerprint_jar already
    normalizes away) or the volatile lines filtered below. Every real consumer
    unwraps: java.util.jar.Manifest reconstitutes the logical value when a jar
    is read, so the break position is invisible to the JVM classloader that
    resolves Class-Path. It is also NOT a POM-controllable fact: Ant's
    org.apache.tools.ant.taskdefs.Manifest breaks at 70 bytes
    (MAX_LINE_LENGTH - 2, reserving room for the trailing CRLF) while Maven's
    archiver stack breaks at the full 72 -- two different writer classes,
    neither of which is java.util.jar.Manifest, and no manifest content changes
    either one. See "Accepted divergences" in docs/design/deferred-fixes-register.md.

    This is a CORRECTION, not a loosened check: joining is applied to BOTH
    sides of every comparison, so a changed, added, or removed attribute still
    differs after joining -- only two different break positions of the SAME
    value stop differing. Must run before the volatile-line filter: the
    Implementation-Version volatile pattern is anchored end-to-end
    (^Implementation-Version: [0-9]{8} [0-9]{4}$), so a wrapped DSTAMP/TSTAMP
    value would fail to match piecemeal and leak through as a spuriously
    "stable" line, flipping cross-minute rebuilds red for no product reason --
    filtering after joining is what makes that pattern reliable again.
    """
    joined = _join_manifest_continuations(text.splitlines())
    return "\n".join(l for l in joined if not _MANIFEST_VOLATILE.match(l))


# A generated C config header -> its {macro: value} #define SET. autoconf and CMake
# spell the SAME configuration differently (comment style, `#define X 1` vs
# `/* #undef X */`, ordering), exactly like they spell compiler flags differently —
# so machine.h is compared SEMANTICALLY by this set, never byte-for-byte. Key is the
# bare identifier (so a function-like C2F(name) keys as "C2F"); value is the rest of
# the line, whitespace-collapsed. #undef / commented-out macros are simply ABSENT
# from the map, which is exactly how "this feature is off" must compare.
_DEFINE_RE = re.compile(r"^\s*#\s*define\s+([A-Za-z_][A-Za-z_0-9]*)\s*(.*?)\s*$")


def parse_defines(header_text):
    """Generated C header text -> {macro: value}; bare `#define X` -> value ""."""
    out = {}
    for line in header_text.splitlines():
        m = _DEFINE_RE.match(line)
        if m:
            out[m.group(1)] = " ".join(m.group(2).split())
    return out
