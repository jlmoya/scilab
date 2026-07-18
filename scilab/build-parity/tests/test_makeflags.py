import glob
import os

import pytest

from parity.makeflags import parse_make_defs, expand_make_value, makefile_tu_facts
from parity.capture import capture_tu_flag_facts, _DEFAULT_DEVIATION_LIMIT

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

# am__objects_1 below (referencing both explicit-rule objects) is not incidental:
# makefile_tu_facts only records a --mode=compile explicit rule as live when its
# object is referenced by some live variable definition, exactly like real
# automake output always has (am__objects_N -> ..._OBJECTS). Without it these
# TUs would be silently absent from "explicit", which is the wrong failure mode
# for fixtures that are testing something else entirely (footgun/non-footgun
# fact derivation, below) -- see _MK_DEAD_RULE_COLLISION and
# _MK_SAME_DIR_CONFIG_STATUS_COLLISION further down for fixtures that
# deliberately vary this reference to test the live/dead distinction itself.
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
am__objects_1 = src/libfoo_la-drop.lo src/libbar_la-keep.lo

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


_MK_DEAD_RULE_COLLISION = """\
CC = gcc -std=gnu23
F77 = gfortran
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
SCI_FFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
AM_FFLAGS = $(SCI_FFLAGS)
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) $(DEFS)
LTF77COMPILE = $(LIBTOOL) --mode=compile $(F77)
am__objects_1 = src/c/libfoo_la-live.lo

.c.lo:
\t$(LTCOMPILE) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

.f.lo:
\t$(LTF77COMPILE) $(AM_FFLAGS) $(FFLAGS) -c -o $@ $<

src/c/libfoo_la-live.lo: src/c/live.c
\t$(LIBTOOL) --mode=compile $(CC) $(AM_CFLAGS) -O0 $(CFLAGS) -c -o $@ src/c/live.c

# Hand-written "disable optimisation" block, shaped exactly like the real one in
# modules/elementary_functions/Makefile: a noinst dummy library re-lists a
# SUBDIRECTORY source under a ROOT-level prefixed object name, with no other
# --mode=compile rule of its own (dead.f's real compile is the plain .f.lo:
# suffix default -- this is the ONLY explicit-rule text naming it), and --
# crucially -- that root-level object name is NEVER assigned to any variable
# anywhere in the file (the real elementary_functions Makefile's _OBJECTS list
# uses the plain subdir-objects name instead, never this prefixed one), so it
# is dead under the object-referenced test too, not merely under the directory
# one.
libdummy_foo_la-dead.lo: src/fortran/eispack/dead.f
\t$(LIBTOOL) --tag=F77 --mode=compile $(F77) $(AM_FFLAGS) $(FFLAGS) -O0 -c -o libdummy_foo_la-dead.lo src/fortran/eispack/dead.f
"""

def test_tu_facts_dead_rootlevel_rule_does_not_shadow_a_live_subdir_fact():
    # Regression test for the dead-rule collision (RC-b Task 2 review): reproduces
    # modules/elementary_functions/Makefile's real defect, where a hand-written
    # "Disable optimisation" noinst-library block's root-level-named rule was the
    # ONLY --mode=compile match for hqror2.f/comqr3.f/pade.f/icopy.f/unsfdcopy.c,
    # so it got recorded as those TUs' fact even though it is never requested by
    # any real target's _OBJECTS (the true compile is the plain suffix default).
    # The discriminator is whether the object is actually REFERENCED by some live
    # variable definition -- am__objects_1 above stands in for automake's real
    # am__objects_N -> ..._OBJECTS chain: the live rule's object is listed there;
    # the dead rule's root-level object name is listed nowhere.
    facts = makefile_tu_facts(_MK_DEAD_RULE_COLLISION)["explicit"]
    # The live, referenced object IS recorded, with its true fact.
    assert facts["src/c/live.c"]["opt"] == "O0"
    # The dead, unreferenced, root-level-named rule for a subdirectory source is
    # skipped entirely -- dead.f is simply absent from "explicit", falling
    # through to the (O2) suffix default instead of being poisoned by the
    # orphaned rule.
    assert "src/fortran/eispack/dead.f" not in facts


_MK_SAME_DIR_CONFIG_STATUS_COLLISION = """\
CC = gcc -std=gnu23
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
libscifoo_la_CFLAGS =
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) $(DEFS)

.c.lo:
\t$(LTCOMPILE) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

am__objects_1 = src/nofoo/libscifoo_disable_la-Wrap_Fake.lo
#am__objects_2 = src/nofoo/libscifoo_la-Wrap_Fake.lo
am__objects_2 = sci_gateway/c/libscifoo_la-sci_something.lo

src/nofoo/libscifoo_disable_la-Wrap_Fake.lo: src/nofoo/Wrap_Fake.c
\t$(LIBTOOL) --mode=compile $(CC) $(AM_CFLAGS) $(CFLAGS) -c -o $@ src/nofoo/Wrap_Fake.c

src/nofoo/libscifoo_la-Wrap_Fake.lo: src/nofoo/Wrap_Fake.c
\t$(LIBTOOL) --mode=compile $(CC) $(libscifoo_la_CFLAGS) $(CFLAGS) -c -o $@ src/nofoo/Wrap_Fake.c
"""

def test_tu_facts_same_dir_config_status_excluded_rule_does_not_shadow_the_live_one():
    # Regression test for the Fix-1 review finding (RC-b Task 2 review): the
    # directory-based filter this replaced could not see a SAME-DIRECTORY
    # collision -- unlike the root-level libdummy_ shape above, BOTH candidate
    # objects here sit right beside their source, so "dirname(obj) ==
    # dirname(src)" is True for both and cannot tell them apart. Reproduces
    # modules/history_browser's real CommandHistory_Wrap_Fake.c shape exactly:
    # two --mode=compile rules for one source, one live (listed in am__objects_1,
    # full AM_CFLAGS), one whose own am__objects_2 listing config.status
    # commented out (a FALSE automake conditional branch) and whose recipe uses
    # the EMPTY per-target libscifoo_la_CFLAGS -- the _CFLAGS-replaces-AM_CFLAGS
    # footgun shape. "explicit" is keyed by source path and the dead rule sorts
    # textually LAST, so a filter that cannot distinguish live from dead here
    # records the dead rule's O0/no-wrapv fact as the TU's fact -- in the real
    # Makefile this contradicted both the built object's own DWARF
    # (DW_AT_APPLE_optimized=true) and CMake, which compiles it -O2 -fwrapv.
    facts = makefile_tu_facts(_MK_SAME_DIR_CONFIG_STATUS_COLLISION)["explicit"]
    assert facts["src/nofoo/Wrap_Fake.c"]["opt"] == "O2"
    assert facts["src/nofoo/Wrap_Fake.c"]["wrapv"] is True


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
    derived_per_makefile = []
    for path in makefiles:
        with open(path, encoding="utf-8", errors="replace") as f:
            facts = makefile_tu_facts(f.read())
        derived_per_makefile.append(facts)
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

    # Per-language, not just per-file: a Fortran-only regression leaves the file
    # count untouched (Fortran-bearing Makefiles are a strict subset of the
    # C/C++-bearing ones), so the majority threshold alone cannot see it.
    lang_counts = {}
    for facts in derived_per_makefile:
        for lang in facts["defaults"]:
            lang_counts[lang] = lang_counts.get(lang, 0) + 1
    for lang in ("c", "cxx", "f"):
        assert lang_counts.get(lang, 0) > 0, f"no Makefile yielded a {lang} default"


def test_capture_shape_and_override_selection(tmp_path):
    mk = """\
CC = gcc -std=gnu23
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
libfoo_la_CFLAGS =
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) $(DEFS)
am__objects_1 = src/libfoo_la-drop.lo src/libfoo_la-plain.lo

.c.lo:
\t$(LTCOMPILE) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

src/libfoo_la-drop.lo: src/drop.c
\t$(LIBTOOL) --mode=compile $(CC) $(libfoo_la_CFLAGS) $(CFLAGS) -c -o $@ src/drop.c

src/libfoo_la-plain.lo: src/plain.c
\t$(LIBTOOL) --mode=compile $(CC) $(AM_CFLAGS) $(CFLAGS) -c -o $@ src/plain.c
"""
    d = tmp_path / "modules" / "m"
    d.mkdir(parents=True)
    (d / "Makefile").write_text(mk)
    got = capture_tu_flag_facts(str(tmp_path))
    # The suffix rule reaches the compiler through $(LTCOMPILE), exactly as real
    # automake output does -- the marker "--mode=compile" is visible only AFTER
    # expansion. A fixture that inlined it would pass against a parser that gates
    # on raw recipe text, which is the bug Task 1's review caught (defaults empty
    # for 78 of 78 real Makefiles). Keep this indirection.
    assert got["defaults"]["c"]["opt"] == "O2"
    # ONLY the deviating TU is recorded; a TU matching the default is not.
    assert "modules/m/src/drop.c" in got["overrides"]
    assert "modules/m/src/plain.c" not in got["overrides"]
    assert got["overrides"]["modules/m/src/drop.c"]["opt"] == "O0"


def _write_default_only_module(tmp_path, name, opt):
    """A minimal module Makefile whose ONLY signal is its .c.lo: suffix-rule
    default -- no explicit rules, so it exercises capture_tu_flag_facts' MODAL
    aggregation across modules in isolation from the override-selection logic
    tested above."""
    mk = f"""\
CC = gcc -std=gnu23
SCI_CFLAGS = -DNDEBUG {opt} -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
LTCOMPILE = $(LIBTOOL) --mode=compile $(CC) $(DEFS)

.c.lo:
\t$(LTCOMPILE) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<
"""
    d = tmp_path / "modules" / name
    d.mkdir(parents=True)
    (d / "Makefile").write_text(mk)


def test_capture_default_is_the_majority_not_merely_a_seen_value(tmp_path):
    # Regression test for the modal-selection line itself, `modal =
    # max(set(seen), key=seen.count)` (RC-b Task 2 review): the ONLY existing
    # exerciser was test_capture_shape_and_override_selection, a SINGLE-module
    # fixture where set(seen) always has exactly one element -- max() and min()
    # of a one-element set return the same thing, so mutating max -> min left
    # the whole suite green. Three modules -- two agreeing, one diverging -- is
    # the minimum shape that can tell them apart: the majority value (-O2, seen
    # twice) must win over the minority value (-O0, seen once); max() picks the
    # 2-vote value, min() would pick the 1-vote value.
    _write_default_only_module(tmp_path, "maj_a", "-O2")
    _write_default_only_module(tmp_path, "maj_b", "-O2")
    _write_default_only_module(tmp_path, "minority", "-O0")

    got = capture_tu_flag_facts(str(tmp_path))
    assert got["defaults"]["c"]["opt"] == "O2"


def test_capture_default_deviation_limit_raises_when_exceeded(tmp_path):
    # Boundary test for _DEFAULT_DEVIATION_LIMIT (RC-b Task 2 review): proves the
    # safety valve actually fires. Every OTHER test in this file stays well under
    # the limit on purpose, so deleting the `if deviants > _DEFAULT_DEVIATION_
    # LIMIT: raise` check entirely is invisible to the rest of the suite -- this
    # is the only test that would notice.
    #
    # Two modules agree on the majority (-O2, count 2); _DEFAULT_DEVIATION_LIMIT
    # + 1 modules each carry their OWN distinct value (-O3, -O4, ...), so no
    # single deviant value can tie or beat the majority's count of 2 and make
    # the outcome depend on Python's set iteration order. deviants ends up at
    # _DEFAULT_DEVIATION_LIMIT + 1 (one over the limit) regardless.
    _write_default_only_module(tmp_path, "maj_a", "-O2")
    _write_default_only_module(tmp_path, "maj_b", "-O2")
    for n in range(_DEFAULT_DEVIATION_LIMIT + 1):
        _write_default_only_module(tmp_path, f"deviant_{n}", f"-O{n + 3}")

    with pytest.raises(RuntimeError, match="deviate from the modal default"):
        capture_tu_flag_facts(str(tmp_path))
