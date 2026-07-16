"""Assert the semantic compiler-flag facts of a CMake module's compile lines.
Closes the hybrid blind spot: the tree-wide flag manifest reads config.status
(autotools), so it cannot see a CMake module's own flags; this can."""
import json, os, sys
from parity.fingerprint import parse_flag_facts

# Default expectation: every compiled TU -- C, C++, and Fortran alike -- is
# O2 + fwrapv + min_macos 11.0. Named + module-level (not buried in __main__) so
# it is importable and testable: the CLI gate and Tasks 5-9 share ONE source of
# truth for which suffixes get checked + what is expected of them.
_BASE = {"opt": "O2", "wrapv": True, "min_macos": "11.0"}
# Every compiled-source suffix that actually appears in the Scilab tree (census
# 2026-07-16: .c 1818, .cpp 1435, .cxx 4, .cc 3, .f 848, .F 3, .f90 59). ALL of
# these are MUTUALLY unreachable via endswith (it is exact + case-sensitive:
# "x.cpp".endswith(".c"), "x.cc".endswith(".c"), "x.f90".endswith(".f"),
# "x.F".endswith(".f") are ALL False), so each must be listed explicitly or its
# TUs' flags go unchecked -- a guard that does not guard. The parametrized test
# test_each_required_suffix_is_guarded proves every entry here is live; the CLI
# additionally FAILS on any entry whose suffix is absent here (unchecked_suffixes).
DEFAULT_EXPECTED_BY_SUFFIX = {suffix: _BASE for suffix in
                              (".c", ".cpp", ".cxx", ".cc", ".f", ".F", ".f90")}

def check_flag_facts(compile_commands_path, expected_by_suffix):
    with open(compile_commands_path) as f:
        entries = json.load(f)
    mismatches = []
    for e in entries:
        cmd = e.get("command") or " ".join(e.get("arguments", []))
        for suffix, expected in expected_by_suffix.items():
            if not e["file"].endswith(suffix):
                continue
            facts = parse_flag_facts(cmd)
            for k, want in expected.items():
                if facts.get(k) != want:
                    mismatches.append(f"{e['file']}: flag fact {k}={facts.get(k)!r} (want {want!r})")
    return mismatches

def unchecked_suffixes(compile_commands_path, expected_by_suffix):
    """Compile-DB entries whose file matches NO suffix in the map.

    compile_commands.json holds exactly one entry per COMPILED translation unit,
    so an entry matching no suffix is a compiled source going unchecked -- a
    silent coverage gap. Returns (file, ext) pairs for the CLI to report and fail
    on. Mirrors check_flag_facts' endswith predicate exactly, so "covered here"
    means "actually examined there".
    """
    with open(compile_commands_path) as f:
        entries = json.load(f)
    out = []
    for e in entries:
        if not any(e["file"].endswith(s) for s in expected_by_suffix):
            out.append((e["file"], os.path.splitext(e["file"])[1] or "(none)"))
    return out

if __name__ == "__main__":
    path = sys.argv[1]
    unchecked = unchecked_suffixes(path, DEFAULT_EXPECTED_BY_SUFFIX)
    mismatches = check_flag_facts(path, DEFAULT_EXPECTED_BY_SUFFIX)
    for f, ext in unchecked:
        print(f"unchecked compiled suffix {ext!r} in {f} -- add it to DEFAULT_EXPECTED_BY_SUFFIX")
    for m in mismatches:
        print(m)
    sys.exit(1 if (unchecked or mismatches) else 0)
