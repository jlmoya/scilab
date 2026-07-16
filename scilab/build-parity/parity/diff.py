"""Compare two build fingerprints. `ok` iff behaviorally identical."""
import json
import sys


def _diff_named(kind, base, cand, out):
    """Report added/removed keys in a name->obj mapping."""
    for name in sorted(set(base) - set(cand)):
        out.append(f"{kind} missing in candidate: {name}")
    for name in sorted(set(cand) - set(base)):
        out.append(f"{kind} extra in candidate: {name}")


def diff_fingerprints(base, cand):
    out = []

    # Executables: presence + the SDK stamp (release-blocking) + deps + tmp leak.
    _diff_named("executable", base["executables"], cand["executables"], out)
    for name in sorted(set(base["executables"]) & set(cand["executables"])):
        b, c = base["executables"][name], cand["executables"][name]
        if b["build_version"] != c["build_version"]:
            out.append(f"executable {name}: build_version (minos/sdk) changed "
                       f"{b['build_version']} -> {c['build_version']}")
        if c["tmp_leak"]:
            out.append(f"executable {name}: non-relocatable /tmp path in link")
        # An executable has no LC_ID_DYLIB, so parse_otool_libs records its FIRST
        # linked library under install_name. Compare it (the dylib block does) or a
        # Stage-1 CMake link-order change that reshuffles scilab-bin's first
        # dependency is invisible -- it lands in the one field nobody checked.
        if b["install_name"] != c["install_name"]:
            out.append(f"executable {name}: install_name (first linked library) changed")
        if sorted(b["deps"]) != sorted(c["deps"]):
            out.append(f"executable {name}: link dependencies changed")

    # Dylibs: presence + symbol set + deps + install name + tmp leak.
    _diff_named("dylib", base["dylibs"], cand["dylibs"], out)
    for name in sorted(set(base["dylibs"]) & set(cand["dylibs"])):
        b, c = base["dylibs"][name], cand["dylibs"][name]
        removed = sorted(set(b["symbols"]) - set(c["symbols"]))
        added = sorted(set(c["symbols"]) - set(b["symbols"]))
        if removed:
            out.append(f"dylib {name}: symbols removed: {', '.join(removed)}")
        if added:
            out.append(f"dylib {name}: symbols added: {', '.join(added)}")
        if b["install_name"] != c["install_name"]:
            out.append(f"dylib {name}: install_name changed")
        if sorted(b["deps"]) != sorted(c["deps"]):
            out.append(f"dylib {name}: link dependencies changed")
        if c["tmp_leak"]:
            out.append(f"dylib {name}: non-relocatable /tmp path in link")

    # Generated files: presence + content hash.
    _diff_named("generated file", base["generated"], cand["generated"], out)
    for name in sorted(set(base["generated"]) & set(cand["generated"])):
        if base["generated"][name] != cand["generated"][name]:
            out.append(f"generated file changed: {name}")

    # Compiler-flag facts, per language. The `source` label (autotools vs cmake)
    # is deliberately NOT compared -- flipping it is the migration itself; only
    # the semantic codegen facts matter. .get(): fingerprints captured before
    # the flag manifest existed lack the block entirely -- two old fingerprints
    # diff clean, but a language present on one side only is a difference (a
    # pre-manifest candidate must not silently skip the flag check).
    bflags = base.get("flags") or {}
    cflags = cand.get("flags") or {}
    for lang in ("c", "cxx", "f"):
        b, c = bflags.get(lang), cflags.get(lang)
        if b is None and c is None:
            continue
        if c is None:
            out.append(f"flags {lang}: facts missing in candidate")
        elif b is None:
            out.append(f"flags {lang}: facts extra in candidate")
        elif b != c:
            changed = sorted(k for k in set(b) | set(c) if b.get(k) != c.get(k))
            out.append(f"flags {lang}: " + ", ".join(
                f"{k} {b.get(k)!r} -> {c.get(k)!r}" for k in changed))

    return {"ok": not out, "differences": out}


def _main(argv):
    if len(argv) != 3:
        print("usage: python -m parity.diff <baseline.json> <candidate.json>", file=sys.stderr)
        return 2
    # Exit 2 (not 1) if a file is missing/unreadable/malformed: a broken pipeline
    # must be distinguishable from a genuine parity regression (exit 1) in CI.
    try:
        with open(argv[1]) as f:
            base = json.load(f)
        with open(argv[2]) as f:
            cand = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"error: could not read fingerprint file: {e}", file=sys.stderr)
        return 2
    result = diff_fingerprints(base, cand)
    if result["ok"]:
        print("PARITY OK")
        return 0
    print(f"PARITY FAILED — {len(result['differences'])} difference(s):")
    for d in result["differences"]:
        print(f"  - {d}")
    return 1


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
