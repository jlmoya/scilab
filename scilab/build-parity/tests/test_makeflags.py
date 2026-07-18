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
SCI_CFLAGS = -DNDEBUG -O2 -fwrapv -mmacosx-version-min=11.0
AM_CFLAGS = $(SCI_CFLAGS)
libfoo_la_CFLAGS =
libbar_la_CFLAGS = $(AM_CFLAGS) -Iextra

.c.lo:
\t$(LIBTOOL) --mode=compile $(CC) $(DEFS) $(AM_CFLAGS) $(CFLAGS) -c -o $@ $<

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
