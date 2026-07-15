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


_VERSION_TOKEN = re.compile(r"\.\d{4}\.")


def normalize_version(name):
    """Collapse a 4-digit library version token: libsciX.2027.dylib -> libsciX.VER.dylib."""
    return _VERSION_TOKEN.sub(".VER.", name)


def normalize_path(text, roots):
    """Replace absolute-path prefixes (longest first) with placeholders, then version-normalize."""
    for prefix in sorted(roots, key=len, reverse=True):
        text = text.replace(prefix, roots[prefix])
    return normalize_version(text)
