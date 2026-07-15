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
    r = parse_otool_libs(OTOOL_L_FIXTURE)
    assert r["install_name"] == "/usr/local/lib/scilab/libscistatistics.2027.dylib (compatibility version 2028.0.0, current version 2028.0.0)"
    assert r["deps"] == [
        "/opt/homebrew/opt/gcc/lib/gcc/current/libgfortran.5.dylib (compatibility version 6.0.0, current version 6.0.0)",
        "/opt/homebrew/opt/gcc/lib/gcc/current/libquadmath.0.dylib (compatibility version 1.0.0, current version 1.0.0)",
        "/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1356.0.0)",
        "/usr/lib/libc++.1.dylib (compatibility version 1.0.0, current version 2100.43.0)",
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
        "/a/liba.dylib (compatibility version 1.0.0, current version 1.0.0)",
        "/z/libz.dylib (compatibility version 1.0.0, current version 1.0.0)",
    ]

def test_parse_otool_libs_flags_tmp_path():
    leaky = "x.dylib:\n\t/tmp/build/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
    assert parse_otool_libs(leaky)["tmp_leak"] is True

def test_parse_otool_libs_flags_tmp_in_install_name():
    # A /tmp path in the install name (the FIRST entry), not just a dep, still leaks.
    leaky = "x.dylib:\n\t/tmp/build/libx.dylib (compatibility version 1.0.0, current version 1.0.0)\n"
    assert parse_otool_libs(leaky)["tmp_leak"] is True

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
