"""Derive per-TU compiler-flag facts from the AUTOTOOLS generated Makefiles.

The only place in the harness that reads autotools' *recipes* rather than its
outputs. It exists so the flag gate's expectation is DERIVED from autotools
instead of hand-written -- the hand-written form silently blessed a real
4-TU divergence (RC-b design doc S3.3/S3.4).

Whole-recipe expansion, deliberately: -std=gnu23 and -arch arm64 live in $(CC),
not in $(SCI_CFLAGS), so expanding only the flag variables would derive std=None
for every TU and mismatch CMake everywhere. Unknown variables expand empty, as
make does, so $(LIBTOOL)/$@/$< contribute nothing and parse_flag_facts simply
ignores the residue.
"""
import re
from parity.fingerprint import parse_flag_facts

_DEF = re.compile(r'^([A-Za-z_][A-Za-z0-9_]*)\s*(\+?=)\s*(.*)$')
_REF = re.compile(r'\$\(([A-Za-z_][A-Za-z0-9_]*)\)')
_EXPLICIT_RULE = re.compile(r'^(\S+\.(?:lo|o)):\s+(\S+\.(?:c|cpp|cxx|cc|f|F|f90))\s*$')
_SUFFIX_RULE = re.compile(r'^\.(c|cpp|cxx|cc|f|F|f90)\.(?:lo|o):\s*$')
_MAX_DEPTH = 25

LANG_BY_SUFFIX = {"c": "c", "cpp": "cxx", "cxx": "cxx", "cc": "cxx",
                  "f": "f", "F": "f", "f90": "f"}

def parse_make_defs(text):
    """{VAR: raw unexpanded value}, honoring `\\` continuations and `+=`.

    Skips TAB-indented recipe lines (a recipe is not a definition) and '#' lines
    (config.status renders a FALSE automake conditional by commenting the line out,
    so a '#' line is a definition that is genuinely not in effect)."""
    defs, lines, i = {}, text.splitlines(), 0
    while i < len(lines):
        line = lines[i]
        while line.endswith("\\") and i + 1 < len(lines):
            i += 1
            line = line[:-1] + " " + lines[i].strip()
        if line and not line.startswith(("\t", "#")):
            m = _DEF.match(line)
            if m:
                name, op, val = m.groups()
                defs[name] = (defs.get(name, "") + " " + val) if op == "+=" else val
        i += 1
    return defs

def expand_make_value(value, defs, _depth=0):
    """Recursively expand $(VAR); unknowns expand empty. Depth-capped so a cyclic
    definition returns a partial string instead of recursing forever."""
    if _depth > _MAX_DEPTH:
        return value
    return _REF.sub(lambda m: expand_make_value(defs.get(m.group(1), ""), defs, _depth + 1),
                    value)

def _recipe_after(lines, i):
    body = []
    j = i + 1
    while j < len(lines) and lines[j].startswith("\t"):
        body.append(lines[j].strip())
        j += 1
    return " ".join(body)

def makefile_tu_facts(text):
    """One generated Makefile -> {"defaults": {lang: facts}, "explicit": {relsrc: facts}}.

    "defaults" comes from the file's own suffix rules (.c.lo: etc) -- derived, not
    assumed, so a directory that redefines AM_CFLAGS (modules/dynamic_link/src/scripts)
    reports its real default. "explicit" holds every TU with a per-object rule, which
    is where automake's per-target _CFLAGS override lands.
    """
    defs, lines = parse_make_defs(text), text.splitlines()
    out = {"defaults": {}, "explicit": {}}
    for i, line in enumerate(lines):
        m = _SUFFIX_RULE.match(line)
        if m:
            recipe = _recipe_after(lines, i)
            if "--mode=compile" in recipe:
                out["defaults"][LANG_BY_SUFFIX[m.group(1)]] = \
                    parse_flag_facts(expand_make_value(recipe, defs))
            continue
        m = _EXPLICIT_RULE.match(line)
        if m:
            recipe = _recipe_after(lines, i)
            if "--mode=compile" in recipe:
                out["explicit"][m.group(2)] = \
                    parse_flag_facts(expand_make_value(recipe, defs))
    return out
