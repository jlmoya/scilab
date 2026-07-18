import glob
import os

import pytest

from parity.makeflags import parse_make_defs, expand_make_value, makefile_tu_facts

def test_parse_defs_handles_continuations_and_append():
    defs = parse_make_defs("A = one \\\n    two\nB = x\nB += y\n")
    # Assert the SEMANTIC property -- the continuation was joined and both tokens
    # survived -- not the incidental run-length of whitespace between them. The
    # consumer (parse_flag_facts) splits on whitespace, so the exact spacing the
    # join happens to produce is not a behavior worth pinning.
    assert defs["A"].split() == ["one", "two"]
    assert defs["B"].split() == ["x", "y"]

def test_parse_defs_skips_recipes_and_disabled_conditionals():
    # A recipe line is TAB-indented; automake's FALSE conditional becomes a '#' line.
    defs = parse_make_defs("REAL = yes\n\tFAKE = no\n#GONE = no\n")
    assert defs == {"REAL": "yes"}

def test_expand_resolves_nested_refs_and_unknowns():
    defs = {"AM_CFLAGS": "$(SCI_CFLAGS)", "SCI_CFLAGS": "-O2 -fwrapv"}
    assert expand_make_value("$(AM_CFLAGS)", defs) == "-O2 -fwrapv"
    assert expand_make_value("$(NOPE)", defs) == ""      # unknown expands empty, like make

def test_expand_survives_a_definition_cycle():
    assert "loop" not in expand_make_value("$(A)", {"A": "$(B)", "B": "$(A)"})

_MK = """\
CC = gcc -std=gnu23 -arch arm64
CXX = g++ -std=c++17 -arch arm64
F77 = gfortran -arch arm64
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
SCI_CXXFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
SCI_FFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
AM_CXXFLAGS = $(SCI_CXXFLAGS)
AM_FFLAGS = $(SCI_FFLAGS)
libfoo_la_CFLAGS =
libbar_la_CFLAGS = $(AM_CFLAGS) -Iextra
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) $(DEFS)
LTCXXCOMPILE = $(LIBTOOL) --mode=compile $(CXX) $(DEFS)
LTF77COMPILE = $(LIBTOOL) --mode=compile $(F77)

.c.lo:
\t$(LTCOMPILE) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

.cpp.lo:
\t$(LTCXXCOMPILE) $(AM_CXXFLAGS) $(CXXFLAGS) -c -o $@ $<

.f.lo:
\t$(LTF77COMPILE) $(AM_FFLAGS) $(FFLAGS) -c -o $@ $<

src/libfoo_la-drop.lo: src/drop.c
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(libfoo_la_CFLAGS) $(CFLAGS) -c -o $@ src/drop.c

src/libbar_la-keep.lo: src/keep.c
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(libbar_la_CFLAGS) $(CFLAGS) -c -o $@ src/keep.c
"""

def test_tu_facts_default_comes_from_the_suffix_rule():
    facts = makefile_tu_facts(_MK)
    assert facts["defaults"]["c"] == {"opt": "O2", "wrapv": True, "min_macos": "11.0",
                                      "openmp": False, "ndebug": True, "std": "gnu23"}

def test_tu_facts_empty_per_target_cflags_is_the_footgun():
    # The whole recipe is expanded, so -std=gnu23 still arrives via $(CC) even though
    # every SCI_CFLAGS token is gone -- the real shape of a footgunned TU.
    drop = makefile_tu_facts(_MK)["explicit"]["src/drop.c"]
    assert drop["opt"] == "O0" and drop["wrapv"] is False and drop["ndebug"] is False
    assert drop["std"] == "gnu23"
    assert drop["min_macos"] is None

def test_tu_facts_per_target_cflags_that_reincludes_am_cflags_is_NOT_a_footgun():
    # The negative control: localization/spreadsheet shape. A recipe-text matcher
    # that looks for the literal "$(AM_CFLAGS)" gets this wrong -- it is reached
    # only through the per-target variable's own definition.
    keep = makefile_tu_facts(_MK)["explicit"]["src/keep.c"]
    assert keep["opt"] == "O2" and keep["wrapv"] is True

def test_tu_facts_default_covers_cxx_and_f_suffixes():
    # LANG_BY_SUFFIX is otherwise untested beyond "c" -- mutating its cxx/f
    # entries to garbage (e.g. "cpp": "bogus") would leave the rest of this
    # suite green. .cpp.lo: and .f.lo: each route through their own wrapper
    # variable, exactly like .c.lo:, so a broken mapping surfaces here as a
    # KeyError or a wrong "std".
    facts = makefile_tu_facts(_MK)
    assert facts["defaults"]["cxx"] == {"opt": "O2", "wrapv": True, "min_macos": "11.0",
                                        "openmp": False, "ndebug": True, "std": "c++17"}
    # Fortran carries -DNDEBUG (real tree: SCI_FFLAGS) but no -std= token
    # (Fortran has none) -- std stays None, unlike c/cxx.
    assert facts["defaults"]["f"] == {"opt": "O2", "wrapv": True, "min_macos": "11.0",
                                      "openmp": False, "ndebug": True, "std": None}

def test_tu_facts_plain_o_suffix_rule_never_contributes_a_default():
    # .c.o: (like .cpp.o:/.f.o: in the real tree) routes through $(COMPILE) --
    # a plain compiler invocation, never libtool -- so it legitimately never
    # carries --mode=compile, expanded or not, and must not leak into
    # "defaults" even though .c.o: and .c.lo: match the SAME suffix-rule regex
    # (_SUFFIX_RULE does not distinguish the target extension). Real Makefiles
    # carry both rules side by side (verified on modules/core/Makefile).
    # .c.o: is placed AFTER .c.lo: here so a leak would OVERWRITE the correct
    # O0/no-wrapv default with COMPILE's O2/-fwrapv -- a silent regression a
    # same-value fixture could not catch.
    mk = """\
CC = gcc -std=gnu23
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) -O0
COMPILE = $(CC) -O2 -fwrapv

.c.lo:
\t$(LTCOMPILE) -c -o $@ $<

.c.o:
\t$(COMPILE) -c -o $@ $<
"""
    facts = makefile_tu_facts(mk)
    assert facts["defaults"]["c"]["opt"] == "O0"
    assert facts["defaults"]["c"]["wrapv"] is False


HERE = os.path.dirname(__file__)
BUILD_DIR = os.path.abspath(os.path.join(HERE, "..", ".."))   # the scilab/ dev tree
_CORE_MAKEFILE = os.path.join(BUILD_DIR, "modules", "core", "Makefile")


@pytest.mark.skipif(not os.path.exists(_CORE_MAKEFILE),
                    reason="requires the configured autotools tree (generated modules/*/Makefile)")
def test_real_makefiles_yield_non_degenerate_defaults():
    """Regression test for the Critical expand-before-gate fix (RC-b Task 1
    review): gating the compile marker on the RAW recipe text derives an EMPTY
    "defaults" dict for every one of the real modules/*/Makefile files -- 78/78,
    confirmed -- because real automake suffix rules route the compile through a
    wrapper variable ($(LTCOMPILE)/$(LTCXXCOMPILE)/$(LTF77COMPILE)), so the
    literal "--mode=compile" is only present after expansion. The synthetic _MK
    fixture above cannot exercise this: it is a hand-built shape, not automake's
    real indirection. This test reads the ACTUAL generated tree, so a regression
    back to raw-text gating fails it, not just a downstream consumer.
    """
    makefiles = sorted(glob.glob(os.path.join(BUILD_DIR, "modules", "*", "Makefile")))
    assert len(makefiles) >= 40, \
        f"sanity: expected dozens of real generated Makefiles, found {len(makefiles)}"

    non_empty = 0
    core_defaults = None
    for path in makefiles:
        with open(path, encoding="utf-8", errors="replace") as f:
            facts = makefile_tu_facts(f.read())
        if facts["defaults"]:
            non_empty += 1
        if path == _CORE_MAKEFILE:
            core_defaults = facts["defaults"]

    # A solid majority must derive real defaults: the degenerate raw-text-gate
    # bug yields 0/78; the fix yields 69/78 (the other 9 are genuinely
    # source-less modules -- pure macro/Java, e.g. modules/atoms,
    # modules/terminal -- which correctly contribute neither defaults nor
    # explicit TUs, verified). 0.8 sits well below 69/78 with margin to spare
    # while staying far above the degenerate 0.
    assert non_empty >= 0.8 * len(makefiles), (
        f"only {non_empty}/{len(makefiles)} real Makefiles yielded non-empty "
        "defaults -- looks like the --mode=compile gate regressed to raw-text")

    assert core_defaults is not None, "modules/core/Makefile not found among the glob results"
    assert core_defaults["c"]["opt"] == "O2"
    assert core_defaults["c"]["wrapv"] is True
