from parity.fingerprint import parse_nm

# Real `nm -gU libscistatistics.2027.dylib` output (verified 2026-07-14).
NM_FIXTURE = """\
0000000000006c0c T _CdfBase
000000000000962c T __Z10braycurtisiiiiiPdS_S_
00000000000084bc T __Z10seuclideaniiiiiPdS_S_
00000000000084bc D _someDataSym
"""

def test_parse_nm_strips_addresses_and_sorts():
    syms = parse_nm(NM_FIXTURE)
    # Address dropped; type + name kept; sorted.
    assert syms == [
        "D _someDataSym",
        "T _CdfBase",
        "T __Z10braycurtisiiiiiPdS_S_",
        "T __Z10seuclideaniiiiiPdS_S_",
    ]

def test_parse_nm_ignores_blank_lines():
    assert parse_nm("\n0000000000006c0c T _X\n\n") == ["T _X"]

def test_parse_nm_empty():
    assert parse_nm("") == []

from parity.fingerprint import parse_otool_libs, parse_build_version

# Real, FULL `otool -L libscistatistics.2027.dylib` output (verified 2026-07-14).
OTOOL_L_FIXTURE = """\
modules/statistics/.libs/libscistatistics.2027.dylib:
\t/usr/local/lib/scilab/libscistatistics.2027.dylib (compatibility version 2028.0.0, current version 2028.0.0)
\t/opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)
\t/opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib (compatibility version 1.0.0, current version 1.0.0)
\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1356.0.0)
\t/usr/lib/libc++.1.dylib (compatibility version 1.0.0, current version 2100.43.0)
"""

def test_parse_otool_libs_splits_install_name_from_deps():
    # Expected values are PATH-ONLY: parse_otool_libs strips the trailing
    # "(compatibility version X, current version Y)" so a routine `brew upgrade`
    # bumping a system lib's `current version` doesn't flood every dependent
    # dylib's diff with a false "link dependencies changed" (I3).
    r = parse_otool_libs(OTOOL_L_FIXTURE)
    assert r["install_name"] == "/usr/local/lib/scilab/libscistatistics.2027.dylib"
    assert r["deps"] == [
        "/opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib",
        "/opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib",
        "/usr/lib/libSystem.B.dylib",
        "/usr/lib/libc++.1.dylib",
    ]
    assert r["tmp_leak"] is False

def test_parse_otool_libs_sorts_deps():
    # Deps deliberately in NON-alphabetical encounter order, so this fails if the
    # sorted() in parse_otool_libs is dropped. Task 4 diffs deps lists across builds;
    # non-deterministic order there would be a false parity mismatch.
    out = ("x.dylib:\n"
           "\t/self/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
           "\t/z/libz.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
           "\t/a/liba.dylib (compatibility version 1.0.0, current version 1.0.0)\n")
    assert parse_otool_libs(out)["deps"] == [
        "/a/liba.dylib",
        "/z/libz.dylib",
    ]

def test_parse_otool_libs_flags_tmp_path():
    leaky = "x.dylib:\n\t/tmp/build/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
    assert parse_otool_libs(leaky)["tmp_leak"] is True

def test_parse_otool_libs_flags_tmp_in_install_name():
    # A /tmp path in the install name (the FIRST entry), not just a dep, still leaks.
    leaky = "x.dylib:\n\t/tmp/build/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
    assert parse_otool_libs(leaky)["tmp_leak"] is True

def test_parse_otool_libs_ignores_current_version_only_change():
    # I3: a routine `brew upgrade` bumps a system lib's `current version` with
    # zero relation to Scilab. Same path, only `current version` differs -> the
    # parsed entry must be identical (no false "link dependencies changed").
    before = "x.dylib:\n\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1356.0.0)\n"
    after = "x.dylib:\n\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1360.0.0)\n"
    assert parse_otool_libs(before)["install_name"] == parse_otool_libs(after)["install_name"]
    assert parse_otool_libs(before)["install_name"] == "/usr/lib/libSystem.B.dylib"

def test_parse_otool_libs_still_distinguishes_different_paths():
    # The de-noising must not swallow a REAL change: a different path is still caught.
    a = "x.dylib:\n\t/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1356.0.0)\n"
    b = "x.dylib:\n\t/opt/homebrew/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1356.0.0)\n"
    assert parse_otool_libs(a)["install_name"] != parse_otool_libs(b)["install_name"]

def test_parse_otool_libs_preserves_unrelated_parentheticals():
    # The strip is anchored on the exact otool "(compatibility version X, current
    # version Y)" suffix -- it must NOT touch some other parenthetical, e.g. the
    # synthetic "libc (v)" dep strings test_diff.py uses directly as fixture data.
    out = "x.dylib:\n\t/self/libc (v)\n"
    assert parse_otool_libs(out)["install_name"] == "/self/libc (v)"

# Real `otool -l scilab-bin | grep -A5 LC_BUILD_VERSION` (verified 2026-07-14).
OTOOL_LV_FIXTURE = """\
      cmd LC_BUILD_VERSION
  cmdsize 32
 platform 1
    minos 11.0
      sdk 11.0
   ntools 1
"""

def test_parse_build_version():
    assert parse_build_version(OTOOL_LV_FIXTURE) == {"minos": "11.0", "sdk": "11.0"}

def test_parse_build_version_absent():
    assert parse_build_version("no build version here") == {"minos": None, "sdk": None}

from parity.fingerprint import parse_flag_facts

# THE fault-injection pair — the exact regression this manifest exists to catch
# (fixed in 516c57573cc): every C file compiled -O0 / no -fwrapv for days while
# the harness sat green, because codegen-only flag drift moves no symbol, link
# edge, or SDK stamp. REGRESSED is the real pre-fix SCI_CFLAGS; CORRECT is the
# real post-fix value (verified against config.status 2026-07-15).
REGRESSED_CFLAGS = ("-DNDEBUG -mmacosx-version-min=11.0 "
                    "-Werror=implicit -Werror=incompatible-pointer-types")
CORRECT_CFLAGS = ("-DNDEBUG -g1 -O2 -fwrapv -mmacosx-version-min=11.0 -fno-stack-protector "
                  "-Wall -Wpedantic -Werror=implicit -Werror=incompatible-pointer-types")

def test_parse_flag_facts_regressed_cflags():
    facts = parse_flag_facts(REGRESSED_CFLAGS)
    assert facts["opt"] == "O0"          # no -O token at all => compiler default, -O0
    assert facts["wrapv"] is False
    assert facts["min_macos"] == "11.0"
    assert facts["ndebug"] is True

def test_parse_flag_facts_correct_cflags():
    facts = parse_flag_facts(CORRECT_CFLAGS)
    assert facts["opt"] == "O2"
    assert facts["wrapv"] is True
    assert facts["min_macos"] == "11.0"
    assert facts["ndebug"] is True
    assert facts["openmp"] is False
    assert facts["std"] is None

def test_parse_flag_facts_last_opt_wins():
    # The per-TU downgrade shape (differential_equations appends -O0 after the
    # global -O2 for colnew.f on macOS): the LAST -O token is the effective one.
    assert parse_flag_facts("-O2 -g -O0")["opt"] == "O0"
    assert parse_flag_facts("-O0 -O2")["opt"] == "O2"

def test_parse_flag_facts_openmp_spellings():
    assert parse_flag_facts("-fopenmp")["openmp"] is True
    # clang spelling: -Xpreprocessor -fopenmp -- the -fopenmp token still appears.
    assert parse_flag_facts("-Xpreprocessor -fopenmp")["openmp"] is True
    assert parse_flag_facts("-O2 -fwrapv")["openmp"] is False

def test_parse_flag_facts_std():
    assert parse_flag_facts("-std=gnu23 -O2")["std"] == "gnu23"
    assert parse_flag_facts("-std=c++17")["std"] == "c++17"

def test_parse_flag_facts_empty():
    assert parse_flag_facts("") == {"opt": "O0", "wrapv": False, "min_macos": None,
                                    "openmp": False, "ndebug": False, "std": None}

def test_parse_flag_facts_ignores_lowercase_output_flag():
    # The cmake path feeds a FULL compile command in; "-o foo.o" (lowercase, the
    # output flag) must not be mistaken for an optimization level.
    assert parse_flag_facts("cc -c foo.c -o foo.o")["opt"] == "O0"

from parity.fingerprint import normalize_version, normalize_path

def test_normalize_version():
    assert normalize_version("libscistatistics.2027.dylib") == "libscistatistics.VER.dylib"
    assert normalize_version("libsciaction_binding-disable.2027.dylib") == "libsciaction_binding-disable.VER.dylib"
    assert normalize_version("libscistatistics.dylib") == "libscistatistics.dylib"

def test_normalize_path_prefixes_and_version():
    # The SHORTER prefix ($HOME) is inserted FIRST on purpose: this only passes if
    # normalize_path sorts by length (longest wins). Naive insertion-order iteration
    # would rewrite $HOME first and never match the $SCI prefix -> test fails. That
    # makes the sort itself testable, not just accidentally right for one ordering.
    roots = {
        "/Users/josemoya": "$HOME",
        "/Users/josemoya/Projects/CLionProjects/scilab/scilab": "$SCI",
    }
    s = "/Users/josemoya/Projects/CLionProjects/scilab/scilab/modules/x/.libs/libx.2027.dylib"
    assert normalize_path(s, roots) == "$SCI/modules/x/.libs/libx.VER.dylib"
    assert normalize_path("/Users/josemoya/other", roots) == "$HOME/other"
