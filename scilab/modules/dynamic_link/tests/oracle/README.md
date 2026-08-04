# `ilib_build` oracle fixtures

Reference material for the autotools-skeleton → CMake migration
(`docs/design/dynamic-link-cmake-migration.md`). These are **not** part of
`test_run("dynamic_link")` — they are inputs to the migration's acceptance gate,
which compares the artifacts the two build paths produce.

## What is here

| file | purpose |
|---|---|
| `gw_c.c` | minimal pure-C gateway |
| `gw_cxx.cpp` | minimal C++ gateway (pulls in libc++ via `std::vector`) |
| `gw_f.f` | Fortran source, used only in the mixed C+Fortran case |
| `capture-oracle.sce` | re-captures the command lines on the current machine |
| `oracle-commands-macos-arm64.txt` | captured 2026-08-04, macOS 26 / arm64 |

Three cases, chosen because they are the three distinct code paths through
`ilib_gen_Make_unix`: C only, C++ only, and C + Fortran mixed. A fourth
combination (C++ + Fortran) adds no new rule — the Fortran rule is independent
of which of C/C++ accompanies it.

## Using it

```
exec("capture-oracle.sce");   // writes oracle-commands-<platform>.NEW.txt
```

It builds in a scratch copy under `TMPDIR`, so this directory stays clean; the
`.NEW` suffix means a re-capture never silently overwrites the reference. Diff
the two, then rename over the reference only if the difference is one you meant
to make. **Expect flag-level differences and
do not treat them as failures**: the captured lines embed the flags Scilab's own
`configure` was run with on the capturing machine — on the reference capture
that includes a stray `-I/opt/homebrew/opt/openssl/include` inherited from the
build host. Invariant 7 in §11 of the design doc explains why the real gate
compares linked artifacts (exports, load commands, `otool -L`) rather than
literal command strings; this file is for understanding *shape*, not for
byte-equality.

The seven invariants the CMake path must preserve are listed in §11 of the
design doc. The two that are easiest to break and hardest to notice:

- the link driver is **always** `g++`, even for a pure-C gateway, because the
  generated wrapper is C++;
- the link is **two steps** — a `-r -keep_private_externs -nostdlib` partial
  link, then `-dynamiclib` on the resulting single object. This is what keeps a
  gateway dylib from exporting its private symbols.

## Traps when re-deriving this by hand

- `ilib_build` **strips a leading `lib`** from the build directory name:
  `ilib_build("libfoo", …)` builds in `TMPDIR/foo`, not `TMPDIR/libfoo`.
- The second argument is a **table** of `[scilab_name, entry_point]` pairs. A
  malformed table surfaces as `ierr=10000` and "no build dir", which reads like
  an `ilib_build` limitation and is not one.
