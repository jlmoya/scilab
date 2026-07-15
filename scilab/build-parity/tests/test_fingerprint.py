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
