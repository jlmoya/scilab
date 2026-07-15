"""Pure parsers and normalizers: text from nm/otool -> structured data. No I/O."""


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
