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

    return {"ok": not out, "differences": out}


def _main(argv):
    if len(argv) != 3:
        print("usage: python -m parity.diff <baseline.json> <candidate.json>", file=sys.stderr)
        return 2
    with open(argv[1]) as f:
        base = json.load(f)
    with open(argv[2]) as f:
        cand = json.load(f)
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
