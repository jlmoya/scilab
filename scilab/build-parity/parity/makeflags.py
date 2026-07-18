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
        # Belt-and-braces, not load-bearing: _DEF's own `^[A-Za-z_]...` anchor
        # already rejects a tab- or '#'-led line (neither starts with a defs
        # identifier character), so this guard never changes which lines match --
        # it just skips the regex attempt for recipe/disabled-conditional lines.
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

    The compile-marker gate ("is this recipe a libtool compile at all?") is tested
    against the EXPANDED recipe, never the raw text: real automake suffix rules route
    the compile through a wrapper variable ($(LTCOMPILE)/$(LTCXXCOMPILE)/
    $(LTF77COMPILE)), so the literal "--mode=compile" appears only after expansion --
    gating on the raw text derives EMPTY defaults for every real Makefile (verified:
    78/78). Expanded once per recipe and the same string reused for parse_flag_facts,
    so the (potentially recursive) expansion never runs twice for one recipe.
    """
    defs, lines = parse_make_defs(text), text.splitlines()
    # Object paths that appear on the RHS of some LIVE (uncommented) variable
    # definition anywhere in the Makefile -- the discriminator for "is this
    # --mode=compile rule actually requested by the build" (see the long
    # comment at its use below). Built once per Makefile: parse_make_defs
    # already merged \-continuations and += appends and dropped comment/recipe
    # lines, so a flat whitespace-split over every surviving definition's
    # value is enough -- automake's object always appears as a literal token
    # at its innermost am__objects_N definition, so this needs no recursive
    # $(VAR) resolution to find it.
    referenced_objects = set()
    for value in defs.values():
        referenced_objects.update(value.split())

    out = {"defaults": {}, "explicit": {}}
    for i, line in enumerate(lines):
        m = _SUFFIX_RULE.match(line)
        if m:
            expanded = expand_make_value(_recipe_after(lines, i), defs)
            # .c.o:/.cpp.o:/.f.o: match this same regex (it does not distinguish
            # the .lo/.o target) but route through $(COMPILE)/$(CXXCOMPILE)/
            # $(F77COMPILE) -- plain compiler invocations with no libtool
            # indirection -- so they legitimately never carry --mode=compile,
            # expanded or not. That is not special-cased here: the gate below
            # excludes them as a side effect of the same check, and only the
            # .lo variant ever satisfies it. Nothing is lost by that exclusion:
            # both variants are driven by the same $(AM_*FLAGS)/$(*FLAGS)
            # lineage and derive identical facts on this tree (verified).
            if "--mode=compile" in expanded:
                out["defaults"][LANG_BY_SUFFIX[m.group(1)]] = parse_flag_facts(expanded)
            continue
        m = _EXPLICIT_RULE.match(line)
        if m:
            obj, src = m.group(1), m.group(2)
            expanded = expand_make_value(_recipe_after(lines, i), defs)
            # A handful of check_PROGRAMS test-harness TUs tree-wide (4, e.g.
            # modules/functions_manager/src/cpp/test-function.cpp) compile via a
            # bare $(CXX)/$(CC) ... -c explicit rule with no libtool involved at
            # all, so they never carry --mode=compile, expanded or not, and are
            # silently excluded here too. Verified inert: `make check` harnesses
            # are absent from build-cmake/compile_commands.json (CMake does not
            # build them), so there is nothing on the CMake side for these TUs to
            # be compared against -- an absent override, not a missed one.
            #
            # A rule is LIVE iff its object is actually requested by the build --
            # i.e. `obj` is a member of referenced_objects (built above from every
            # live variable definition in the file). This is NOT the same question
            # as "does the object sit beside its source": an earlier version of
            # this filter used exactly that directory heuristic (dirname(obj) ==
            # dirname(src)), which caught the modules/elementary_functions class
            # below but MISSED a same-directory collision that poisoned the
            # capture -- see the two paragraphs after the next for both cases and
            # the tree-wide measurement that replaced one filter with the other.
            #
            # Case 1 -- root-level dead rule (dirname-detectable, kept working):
            # modules/elementary_functions carries a hand-written "Disable
            # optimisation" block (noinst_LTLIBRARIES = libdummy-elementary_
            # functions.la) that appends a root-level-named -O0 rule for
            # hqror2.f, comqr3.f, pade.f, icopy.f and unsfdcopy.c (e.g.
            # libdummy_elementary_functions_la-hqror2.lo: src/fortran/eispack/
            # hqror2.f) -- never listed in any real target's _OBJECTS, so
            # subdir-objects never requests it; it is DEAD (flagfacts_check.py's
            # FILE_EXPECTED_OVERRIDES comment independently confirms the baseline
            # compiled all five at plain -O2). Its object is absent from
            # referenced_objects, same as it was dirname-mismatched before.
            #
            # Case 2 -- same-directory dead rule (the dirname test's blind spot):
            # modules/history_browser's CommandHistory_Wrap_Fake.c has TWO
            # --mode=compile rules, both objects sitting beside the source (so
            # BOTH pass the old dirname test): a live libscihistory_browser_
            # disable_la-...lo rule (listed in am__objects_1, full AM_CFLAGS) and
            # a libscihistory_browser_la-...lo rule whose own am__objects_2
            # listing is commented out by config.status (a FALSE automake
            # conditional branch) and whose recipe uses the empty per-target
            # libscihistory_browser_la_CFLAGS -- the _CFLAGS-replaces-AM_CFLAGS
            # footgun shape. "explicit" is keyed by source path, and the dead
            # rule sorts textually LAST, so the old filter recorded its poisoned
            # O0/no-wrapv fact instead of the live rule's O2/wrapv -- contradicting
            # both the built object's own DWARF (DW_AT_APPLE_optimized=true) and
            # CMake (-O2 -fwrapv). referenced_objects correctly excludes it: its
            # object string appears nowhere except its own dead rule text.
            #
            # Measured tree-wide across all 78 real Makefiles' --mode=compile
            # explicit rules (2857 total): the object-referenced test finds 2810
            # live; the dirname test found 2852 live. EVERY object-referenced-live
            # rule is also dirname-live (overlap 2810) -- the new test is a STRICT
            # SUBSET of the old one, so it replaces rather than ANDs with it. Of
            # the 42 rules the two disagree on: 40 are absent from CMake's build
            # entirely (nothing to gate either way -- verified against build-cmake/
            # compile_commands.json), one (helptools/nogui.cpp) has its two
            # candidate rules agreeing on O2/wrapv (so which one wins is
            # immaterial), and exactly one is the CommandHistory_Wrap_Fake.c
            # poisoning above, which -- once excluded -- correctly falls back to
            # the O2 suffix default, matching both CMake and the DWARF.
            if "--mode=compile" in expanded and obj in referenced_objects:
                out["explicit"][src] = parse_flag_facts(expanded)
    return out
