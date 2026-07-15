# Build parity harness (migration Stage 0)

Proves a CMake/Maven build is *behaviorally identical* to the autotools baseline — the safety net
for the make->CMake / Ant->Maven migration (`docs/design/build-cmake-maven-migration.md`).

## Usage
```bash
# Capture the current (candidate) build, then diff against the committed baseline:
./capture.sh .. /tmp/candidate.json candidate
./diff.sh baseline-autotools.json /tmp/candidate.json   # exit 0 = parity, 1 = regression
```

## What it compares
Per dylib: the exported symbol set (addresses stripped) and the link/dependency shape.
Per executable: the `LC_BUILD_VERSION` SDK stamp (must stay `minos 11.0 / sdk 11.0` — the
anti-SIGTRAP fix) and the link shape. Plus: any non-relocatable `/tmp` path, and the normalized
content hash of `etc/classpath.xml`, `machine.h`, `version.h`.

## Scope
Stage 0 fingerprints **dylibs, executables, and generated files** only. **Jars are deliberately
NOT fingerprinted** — Stage 1 (make -> CMake) leaves the Ant-built jars byte-for-byte unchanged, so
there is nothing for a parity check to catch yet; Stage 2 (Ant -> Maven) is what can actually change
jar contents, and that is when jar-parity semantics need to be defined (contained classes, not raw
bytes — a reactor build legitimately changes jar timestamps/manifests). Revisit this file when
Stage 2 starts.

## What it does NOT automate (manual gates — run these too before declaring a module migrated)
1. **Behavior — the `.tst` suite.** There is no compiled test binary; run it inside the built
   interpreter:
   ```bash
   cd .. && LANG=C ./bin/scilab-cli -nb -e "exit(test_run([],[]))"   # or per-module: test_run('statistics')
   ```
   A migrated build must produce the same pass/fail set as autotools.
2. **The GUI-surface checklist** (needs a human at the screen): console, 2-D and 3-D plotting,
   the JavaFX file chooser, the embedded browser, and an xcos simulation run. These exercise the
   off-main-thread graphics path the SDK stamp protects — a green fingerprint does not prove them.

## Refreshing the baseline
Only when the autotools build itself legitimately changes:
`./capture.sh .. baseline-autotools.json autotools` and commit the new baseline in the same change.
