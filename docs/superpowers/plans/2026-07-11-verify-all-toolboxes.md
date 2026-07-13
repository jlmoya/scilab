# Verify ALL tbxManager Toolboxes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every toolbox that tbxManager lists (the auto-discovered catalog of `~/Projects/SciLabProjects/*`) shows the `(verified)` tag — legitimately, meaning each one build+loads+runs on this macOS arm64 Scilab 2027 dev build — plus a repeatable harness (`tbxVerify` verb + sweep script) so the set can be re-proven after any engine bump.

**Architecture:** A new `tbxVerify(name)` macro in the core `toolbox_manager` module performs one toolbox's verification in-process (build → arm64 arch gate → load → library-registration delta → optional smoke script). A shell driver (`tbx-verify-all.sh`) runs one fresh headless `scilab-adv-cli` process per toolbox (isolated `-scihome`, hard `gtimeout`) so a hanging or crashing toolbox can't take down the sweep, and emits a TSV report + a paste-ready `cfg.verified` vector. Toolbox fixes happen in each toolbox's own git repo; the verified set stays the curated hard-coded vector in `tbx_cfg.sci` (per the design doc), refreshed from sweep results.

**Tech Stack:** Scilab macros (`.sci`), bash, autotools dev tree at `/Users/josemoya/Projects/CLionProjects/scilab/scilab`, coreutils `gtimeout`, JDK 25.

## Global Constraints

- **JDK 25**: resolve `JAVA_HOME` exactly like `run-with-toolboxes.sh` does: `~/.config/scilab-app/java_home` if non-empty, else inherited `JAVA_HOME`, else `/usr/libexec/java_home -v 25`.
- **NO AI attribution in any commit** (no `Co-Authored-By`, no `Generated with` trailers) — in the scilab repo AND every toolbox repo. This overrides any harness default.
- **Scilab repo commits go directly on `main`**; push BOTH remotes: `git push gitlab main && git push origin main` (gitlab = gitlab.com/jlmoya/scilab, origin = github.com/jlmoya/scilab).
- **Toolbox fixes are committed in that toolbox's own repo** under `~/Projects/SciLabProjects/<name>` (most have jlmoya GitLab+GitHub remotes; push whatever remotes exist).
- **At most ONE Scilab GUI instance open at a time**; kill any stale instance before launching another; leave only the latest good build running if user testing is wanted.
- **Never Rosetta**: a toolbox shipping non-arm64 native libs fails the arch gate and must be rebuilt for arm64, not exempted.
- **No silent delisting**: if a toolbox looks unfixable or redundant, gather the evidence and STOP for a user decision — removing it from the catalog is the user's call.
- Scilab dev tree root (`SCI` at runtime) = `/Users/josemoya/Projects/CLionProjects/scilab/scilab`. Git root = `/Users/josemoya/Projects/CLionProjects/scilab` (one repo; the source tree is the `scilab/` subdir).
- **`macros/*.bin` + `macros/lib` are generated artifacts, git-ignored on purpose** (the module `.start` genlib-self-heals when `lib` is missing). After ANY `.sci` edit in the module, regenerate the lib locally (genlib one-liner) so the runtime sees it — but NEVER `git add` the `.bin`/`lib` files. (Corrected 2026-07-11 during Task 1 review: git history proves none were ever tracked.)

---

## Context primer (read this first)

**What "verified" is:** `modules/toolbox_manager/macros/tbx_cfg.sci` hard-codes `cfg.verified`, a vector of toolbox names. `tbxCatalog()` discovers the list (every dir under `~/Projects/SciLabProjects` containing `loader.sce` or `builder.sce`) and flags each name `verified = or(name == cfg.verified)`. `tbxManager()` (the GUI) shows `(verified)` vs `(build-only)` per row, pre-ticks verified rows on first run, and has a "Select verified" preset button (`tbx_gui_preset.sci`).

**Current state:** catalog = **50** toolboxes; `cfg.verified` = **21** (`sciDatabase parquet xlsx libsvm guibuilder scicv cgal sndfile-toolbox sciSymPy sciTorch sciQuantLib PIMS financial nan quapro json specfun distfun scidoe stixbox lowdisc`); **29 unverified**, which break down (per `~/Projects/SciLabProjects/FINANCE-TOOLBOX-PORTING.md`, the porting campaign tracker) as:

> **Resolved 2026-07-11 (user decision):** a 51st toolbox, **scidb** (Qt4-based "Database Module + FuzzySQL"), was DELETED from `~/Projects/SciLabProjects` as superseded by sciDatabase. Its one unpushed local commit was pushed to the jlmoya GitLab+GitHub mirrors first, so the source survives. Task 11 is therefore already done.

| Class | Names | Intel |
|---|---|---|
| **A. Already ported (tracker ✅), never added to cfg.verified** (13) | apifun, cma-es, dataint, fmincont, FOSSEE-Optimization-toolbox, grocer, intprbs, lsf_toolbox, montesci, nisp, pso-toolbox, sci_gsl, sci-ipopt | Ported+tested during the finance campaign; `cfg.verified` simply was never refreshed. sci_gsl has a documented check (`phyconst(1)==299792458`). sci-ipopt's historical blocker was **at solve time** (MPI-flavored MUMPS), later fixed — so its smoke must actually solve, not just load. |
| **B. Builds+loads, runtime broken (tracker ◑)** (2) | arfit, krisp | arfit: core functions **hang** (mtlb_-heavy); krisp: macros fine (RLHS verified) but native `corr_*` primitives build yet **don't register** at load. |
| **C. Deferred (tracker ⏸)** (2) | regtools, csv-readwrite | regtools: its `.start` auto-installs `guimaker` from ATOMS which fails to build (one parser bug in `nlinregr.sci:221` already fixed). csv-readwrite: **core Scilab already ships** `csvRead`/`csvWrite`/etc.; port was ~80% done (documented fixes: `MALLOC.h`→`sci_malloc.h`, gateway sigs incl. legacy 1-arg form). |
| **D. Never attempted** (12) | anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol (macro-only, 10); accsum (C src+gateway); scimax (src, a Maxima CAS bridge — needs the `maxima` binary) | The 10 macro-only ones are the same class as already-ported quapro/nan/stixbox: expect the standard playbook to suffice. |

Class D macro-only exact list (10): **anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol**.

**The verification bar (this plan's definition, one notch above the old "it loads"):** build succeeds (if needed) → `tbx_arch_check` passes (all-arm64) → `loader.sce` execs clean and registers ≥1 new library → **and** a per-toolbox smoke script proves one representative function returns a correct value. arfit is the cautionary tale: it *loads* fine and *hangs* at runtime. (The 21 already-verified names are grandfathered; backfilling their smokes is out of scope.)

**Key existing machinery** (all in `modules/toolbox_manager/macros/`):
- `tbx_cfg.sci` — paths + the verified vector. `cfg.projects = ~/Projects/SciLabProjects`.
- `tbx_build.sci` — runs `build_macos.sce` (preferred) or `builder.sce`; sets `CPATH`/`LIBRARY_PATH` (gettext + Homebrew gcc runtime — covers the `emutls_w` link error class) and `__USE_DEPRECATED_STACK_FUNCTIONS__=YES`.
- `tbx_arch_check.sci` — refuses non-arm64 native libs.
- `tbxInstall.sci` — resolve → build → arch gate → manifest register → exec loader inline.
- Module bootstrap `etc/toolbox_manager.start` loads `macros/lib`; **after adding a new `.sci` you MUST regenerate the compiled lib** (`genlib`) or the new verb won't exist.

**Headless run pattern:** `bin/scilab-adv-cli -nb -scihome <fresh-dir> -f <script.sce>` (adv-cli = Java-enabled, no desktop; `-nb` = no banner; fresh `-scihome` = no autoload interference). `exit(n)` sets the process exit code. `gtimeout` is at `/opt/homebrew/bin/gtimeout`.

**§ Playbook — the standard 2027/arm64 porting fixes** (accumulated from the 21 done ports; apply on symptom):

| Symptom | Fix |
|---|---|
| `'MALLOC.h' file not found` | `#include "MALLOC.h"` → `#include "sci_malloc.h"` |
| `'stack-c.h' file not found` | Delete that include (gone in 2027). Legacy `Rhs`/`LhsVar`/`CheckRhs` macros still work via `api_scilab.h` + the `__USE_DEPRECATED_STACK_FUNCTIONS__=YES` env (already set by `tbx_build`). |
| Gateway signature errors | Modern form is `int sci_foo(char *fname, void *pvApiCtx)`; the 1-arg legacy `(char *fname)` also links. Match what the errors ask for. |
| Builder syntax error on `....` | Old doubled line-continuation; collapse to `..` |
| Parser error in old macro (e.g. mismatched quote `'…\n");`) | 2027 parser is stricter; fix the literal (cf. regtools `nlinregr.sci:221`). |
| Load error mentioning a stale/incompatible `lib` or `.bin` | `rm macros/lib macros/*.bin` in the toolbox, rerun its builder (genlib recompiles). |
| dylib not found at load | `otool -L` the gateway lib; `install_name_tool -change <abs> @loader_path/<rel>`; then `codesign -f -s - <lib>`. |
| Arch gate failure | Rebuild the native piece for arm64 (Homebrew deps exist for most: gsl, ipopt, …). |
| `scilabVar` param typos (`in_` vs declared name) | Correct to the declared parameter (FOSSEE recipe). |
| Missing native dep | `brew install <dep>`, point the builder's Darwin branch at `/opt/homebrew/opt/<dep>` (sci_gsl/sci-ipopt recipes show the pattern). |

**Report artifact:** `docs/design/toolbox-verification.md` (git-root `docs/design/`, next to the other design docs) — the sweep matrix + per-toolbox notes; regenerated/updated as tasks land.

---

### Task 1: `tbxVerify(name)` — the single-toolbox verifier verb

**Files:**
- Create: `scilab/modules/toolbox_manager/macros/tbxVerify.sci`
- Modify (regenerate): `scilab/modules/toolbox_manager/macros/lib`, `scilab/modules/toolbox_manager/macros/tbxVerify.bin`
- Test: manual control runs via `scilab-adv-cli` (module has no `.tst` infra; the driver task adds the automated loop)

**Interfaces:**
- Consumes: `tbx_cfg()`, `tbx_build(path)`, `tbx_arch_check(path)` (existing macros, signatures above).
- Produces: `R = tbxVerify(name)` where `R` is a struct with fields `name` (string), `built` (%t/%f), `archok` (%t/%f), `loaded` (%t/%f), `delta` (double), `smoke` ("none"|"OK"|"FAIL"), `pass` (%t/%f), `err` (string). Later tasks rely on exactly these fields.
- Smoke contract (used by Tasks 2+): if `<SCI>/tbx-smoke/<name>.sce` exists it is exec'd **after** a successful load, in `tbxVerify`'s scope (so it sees `path`); it must run without error AND set `smoke_ok = %t`, else the toolbox fails.

- [ ] **Step 1: Confirm the missing-verb failure (red)**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./bin/scilab-adv-cli -nb -e 'r = tbxVerify("quapro"); exit(0);' ; echo "rc=$?"
```

Expected: error output containing `Undefined variable: tbxVerify`, non-zero rc.

- [ ] **Step 2: Write the macro**

Create `scilab/modules/toolbox_manager/macros/tbxVerify.sci`:

```scilab
function R = tbxVerify(name)
    // Verify one toolbox against THIS Scilab build:
    //   build (if no loader.sce) -> arm64 arch gate -> load -> must register >=1 library
    //   -> optional deeper smoke: <SCI>/tbx-smoke/<name>.sce (must run clean and set smoke_ok=%t).
    // Meant to run in a throwaway session (see tbx-verify-all.sh): loading pollutes the session.
    cfg = tbx_cfg();
    R = struct("name", name, "built", %f, "archok", %f, "loaded", %f, ..
               "delta", 0, "smoke", "none", "pass", %f, "err", "");
    path = fullfile(cfg.projects, name);
    if ~isdir(path) then path = fullfile(cfg.tbxdir, name); end
    if ~isdir(path) then R.err = "toolbox dir not found"; return; end
    if ~isfile(fullfile(path, "loader.sce")) then
        if ~tbx_build(path) then R.err = "build failed"; return; end
    end
    R.built = %t;
    [archok, bad] = tbx_arch_check(path);
    R.archok = archok;
    if ~archok then R.err = "non-arm64 native lib: " + strcat(bad', ", "); return; end
    nbefore = size(librarieslist(), "*");
    ie = execstr("exec(fullfile(path, ""loader.sce""), -1)", "errcatch");
    if ie <> 0 then R.err = "loader error " + string(ie) + ": " + lasterror(); return; end
    R.loaded = %t;
    R.delta = size(librarieslist(), "*") - nbefore;
    if R.delta < 1 then R.err = "loader registered no new library"; return; end
    smk = fullfile(SCI, "tbx-smoke", name + ".sce");
    if isfile(smk) then
        smoke_ok = %f;
        ie = execstr("exec(smk, -1)", "errcatch");
        if ie <> 0 then R.smoke = "FAIL"; R.err = "smoke error: " + lasterror(); return; end
        if ~smoke_ok then R.smoke = "FAIL"; R.err = "smoke ran but smoke_ok<>%t"; return; end
        R.smoke = "OK";
    end
    R.pass = %t;
endfunction
```

- [ ] **Step 3: Regenerate the module macro lib** (the `.start` loads the compiled lib; without this the verb stays undefined)

```bash
./bin/scilab-adv-cli -nb -e 'genlib("toolbox_managerlib", SCI + "/modules/toolbox_manager/macros", %t); exit(0);'
```

Expected: rc 0; `macros/tbxVerify.bin` now exists and `macros/lib` is newer.

- [ ] **Step 4: Control tests (green)** — a known-good toolbox passes, a bogus name fails cleanly

```bash
./bin/scilab-adv-cli -nb -e 'r = tbxVerify("quapro"); disp(r); exit(1 - bool2s(r.pass));' ; echo "quapro rc=$?"
./bin/scilab-adv-cli -nb -e 'r = tbxVerify("no-such-toolbox"); disp(r.err); exit(bool2s(r.pass));' ; echo "bogus rc=$?"
```

Expected: `quapro rc=0` (struct shows `pass = T`, `delta >= 1`); `bogus rc=0` with `err` = `toolbox dir not found` (and `pass = F` — note the inverted exit expression makes rc 0 mean "correctly failed").

- [ ] **Step 5: Commit** (scilab repo, on main)

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/modules/toolbox_manager/macros/tbxVerify.sci \
        scilab/modules/toolbox_manager/macros/tbxVerify.bin \
        scilab/modules/toolbox_manager/macros/lib
git commit -m "tbxManager: tbxVerify(name) — build+arch+load+smoke verifier for one toolbox"
```

---

### Task 2: `tbx-verify-all.sh` — crash-isolated sweep driver + smoke protocol

**Files:**
- Create: `scilab/tbx-verify-all.sh` (next to `build-macos.sh`)
- Create: `scilab/tbx-verify-one.sce`
- Create: `scilab/tbx-smoke/sci_gsl.sce` (first smoke file; documented expectation)
- Test: 2-name driver run (quapro + bogus name)

**Interfaces:**
- Consumes: `tbxVerify(name)` struct from Task 1; `getenv("TBX_NAME")`/`getenv("TBX_OUT")` inside `tbx-verify-one.sce`.
- Produces: TSV report (default `scilab/tbx-verify-report.tsv`, override via `TBX_REPORT`), schema: `name<TAB>status<TAB>detail` with `status ∈ PASS|FAIL|TIMEOUT|CRASH`; on stdout a summary line `== N PASS / M FAIL of K ==` and a paste-ready space-separated quoted name list of all PASS rows. Per-toolbox timeout via `TBX_TIMEOUT` (default 300s). Args = toolbox names; no args = full catalog sweep.
- Produces: the smoke-file convention `scilab/tbx-smoke/<name>.sce` (contract in Task 1).

- [ ] **Step 1: Write the per-toolbox Scilab entry script**

Create `scilab/tbx-verify-one.sce`:

```scilab
// Runs inside a throwaway scilab-adv-cli (see tbx-verify-all.sh):
// verify $TBX_NAME, append one TSV line to $TBX_OUT, exit 0 on pass / 1 on fail.
name = getenv("TBX_NAME");
out  = getenv("TBX_OUT");
r = tbxVerify(name);
status = "FAIL"; detail = r.err;
if r.pass then
    status = "PASS";
    detail = "delta=" + string(r.delta) + "; smoke=" + r.smoke;
end
fd = mopen(out, "w");
mfprintf(fd, "%s\t%s\t%s\n", name, status, detail);
mclose(fd);
exit(1 - bool2s(r.pass));
```

- [ ] **Step 2: Write the driver**

Create `scilab/tbx-verify-all.sh` (`chmod +x`):

```bash
#!/usr/bin/env bash
# Sweep-verify toolboxes against this dev build: one FRESH scilab-adv-cli per
# toolbox (isolated -scihome + hard timeout) so a hang/crash can't kill the sweep.
# Usage:  ./tbx-verify-all.sh [name...]     (no args = full SciLabProjects catalog)
# Env:    TBX_TIMEOUT (s, default 300) | TBX_REPORT (default ./tbx-verify-report.tsv)
# Failing runs keep their scratch dir (path printed) for debugging; passing runs clean up.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
CFG="$HOME/.config/scilab-app/java_home"
if   [ -s "$CFG" ];           then export JAVA_HOME="$(sed -n '1p' "$CFG")"
elif [ -n "${JAVA_HOME:-}" ]; then :
else export JAVA_HOME="$(/usr/libexec/java_home -v 25)"; fi
PROJECTS="$HOME/Projects/SciLabProjects"
OUT="${TBX_REPORT:-$HERE/tbx-verify-report.tsv}"
TIMEOUT="${TBX_TIMEOUT:-300}"
: > "$OUT"
names=("$@")
if [ ${#names[@]} -eq 0 ]; then
    for d in "$PROJECTS"/*/; do
        d="${d%/}"; n="${d##*/}"
        [ -f "$d/loader.sce" ] || [ -f "$d/builder.sce" ] && names+=("$n")
    done
fi
pass=0; fail=0
for n in "${names[@]}"; do
    sch="$(mktemp -d "${TMPDIR:-/tmp}/tbxverify-$n-XXXXXX")"
    TBX_NAME="$n" TBX_OUT="$sch/result.tsv" gtimeout "$TIMEOUT" \
        "$HERE/bin/scilab-adv-cli" -nb -scihome "$sch" -f "$HERE/tbx-verify-one.sce" \
        > "$sch/log.txt" 2>&1
    rc=$?
    if   [ -s "$sch/result.tsv" ]; then cat "$sch/result.tsv" >> "$OUT"
    elif [ $rc -eq 124 ];          then printf '%s\tTIMEOUT\t%ss; scratch=%s\n' "$n" "$TIMEOUT" "$sch" >> "$OUT"
    else                                printf '%s\tCRASH\trc=%s; scratch=%s\n' "$n" "$rc" "$sch" >> "$OUT"; fi
    tail -1 "$OUT"
    if [ "$(awk -F'\t' 'END{print $2}' "$OUT")" = "PASS" ]; then
        pass=$((pass+1)); rm -rf "$sch"
    else
        fail=$((fail+1))
    fi
done
echo
echo "== $pass PASS / $fail FAIL of ${#names[@]} =="
echo "== PASS names (paste into cfg.verified) =="
awk -F'\t' '$2=="PASS"{printf "\"%s\" ", $1}' "$OUT"; echo
```

- [ ] **Step 3: Write the first smoke file** (documented expectation from the porting tracker)

Create `scilab/tbx-smoke/sci_gsl.sce`:

```scilab
// SCI_GSL smoke: physical constant #1 = speed of light (verified value from the port log).
smoke_ok = (phyconst(1) == 299792458);
```

(If `phyconst`'s name differs on load, check `~/Projects/SciLabProjects/FINANCE-TOOLBOX-PORTING.md` log entry "SCI_GSL ✅" — it records the exact verified calls: `phyconst(1)=299792458`, `Ass_legendre(1,0,0.5)=0.5`.)

- [ ] **Step 4: Test the driver on controls**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
./tbx-verify-all.sh quapro no-such-toolbox ; echo "driver rc=$?"
cat tbx-verify-report.tsv
```

Expected TSV exactly two lines: `quapro	PASS	delta=1; smoke=none` (delta value may differ, ≥1) and `no-such-toolbox	FAIL	toolbox dir not found`; summary `== 1 PASS / 1 FAIL of 2 ==`; PASS list prints `"quapro"`.

- [ ] **Step 5: Commit**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/tbx-verify-all.sh scilab/tbx-verify-one.sce scilab/tbx-smoke/sci_gsl.sce
git commit -m "tbxManager: tbx-verify-all.sh sweep driver + tbx-smoke/ protocol"
```

---

### Task 3: Baseline sweep + report document  ⛳ CHECKPOINT

**Files:**
- Create: `docs/design/toolbox-verification.md`
- Generated (not committed): `scilab/tbx-verify-report.tsv` — add to `.gitignore` if it shows in `git status`

**Interfaces:**
- Consumes: driver + TSV schema from Task 2.
- Produces: the baseline matrix that steers Tasks 4–12; the report doc structure all later tasks append to (`## Matrix` table + `## Per-toolbox notes` sections keyed by toolbox name).

- [ ] **Step 1: Run the full sweep** (50 toolboxes × up to 300 s ⇒ budget up to ~1 h; most finish in seconds)

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
./tbx-verify-all.sh 2>&1 | tee /tmp/tbx-baseline.log
```

Expected: 50 TSV lines. Sanity anchors: all 21 currently-verified names PASS (if one doesn't, STOP — the harness or the tree is broken, fix before proceeding); class-A names mostly PASS; regtools/csv-readwrite FAIL; arfit PASSes (its hang is runtime — its smoke comes in Task 8); krisp PASSes generically (its gap is the missing `corr_*` primitives — Task 6).

- [ ] **Step 2: Write the report doc**

Create `docs/design/toolbox-verification.md` with: an intro paragraph (the verification bar, how to re-run: `cd scilab && ./tbx-verify-all.sh`), a `## Matrix` section holding the TSV rendered as a 3-column markdown table (name / status / detail) sorted FAIL-first, and a `## Per-toolbox notes` section seeded with one `### <name>` stub per non-PASS row stating the observed error verbatim.

- [ ] **Step 3: Commit + report to user**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add docs/design/toolbox-verification.md
git commit -m "docs: toolbox verification baseline matrix (tbx-verify-all sweep)"
```

**CHECKPOINT:** show the user the matrix (counts + the FAIL list with one-line reasons) before starting fixes.

---

> **Baseline results (2026-07-11 sweep, 39 PASS / 11 non-PASS of 50) — scope updates:**
> - **Harness criterion gap found:** gateway-only toolboxes (PIMS, sci-ipopt) register functions via addinter, not macro libraries → delta==0 → false FAIL. Fix (harness v1.1, committed within Task 3): pass = loader clean AND (delta≥1 OR smoke==OK); zero-delta with no smoke file stays FAIL with an explanatory err. Gateway-only toolboxes therefore REQUIRE a smoke file to verify.
> - **Verified-set rot found (3):** parquet (Homebrew arrow bumped past libarrow.2400 → gateway needs rebuild/re-point), PIMS (needs smoke under v1.1 criterion), sciQuantLib (SIGTRAP at load, zero output — dylib-level, diagnose via crash report + otool). These repairs are folded into Task 4.
> - **Class-A failures (3 of 13):** pso-toolbox (loader's add_help_chapter aborts on a missing help dir — guard it), sci_gsl (stale non-arm64 `.so` artifacts in sci_gateway/cpp trip the arch gate — remove/rebuild arm64), sci-ipopt (needs smoke per v1.1). The other 10 class-A already PASS.
> - **Task 9 is nearly free:** all 10 macro-only class-D unknowns PASS the generic bar — the task reduces to authoring 10 smoke files + cfg adds.
> - **arfit PASSes the generic bar as predicted** — do NOT add it to cfg.verified until Task 8's smoke proves the hang is fixed.
> - **krisp arch-gate detail:** stale `libkrisp_c.so`/`libskeleton_c.so` (non-arm64) in sci_gateway/c — Task 6 starts by removing/rebuilding those.
> - **scimax TIMEOUT signature:** builder leaves the CLI at an interactive prompt (REPL echo loop in scratch log) — Task 12 should run the builder with `mode(3)` to find the failing line.

### Task 4: Quick wins + verified-set repairs — smoke + verify the 13 class-A AND repair parquet/PIMS/sciQuantLib, refresh cfg.verified

**Files:**
- Create: `scilab/tbx-smoke/<name>.sce` for: apifun, cma-es, dataint, fmincont, FOSSEE-Optimization-toolbox, grocer, intprbs, lsf_toolbox, montesci, nisp, pso-toolbox, sci-ipopt (sci_gsl's exists from Task 2)
- Modify: `scilab/modules/toolbox_manager/macros/tbx_cfg.sci:13-16` (the `cfg.verified` vector)
- Modify: `docs/design/toolbox-verification.md` (matrix refresh)

**Interfaces:**
- Consumes: Task 2 smoke contract (`smoke_ok = %t`), driver.
- Produces: `cfg.verified` grows by every class-A name that passes; report updated. Later tasks append to the same vector — keep it one name per line region, alphabetical within the additions, so diffs stay reviewable.

- [ ] **Step 1: Write one smoke file per class-A toolbox.** For each name, find one representative call with a checkable result — source it from, in priority order: (1) the FINANCE-TOOLBOX-PORTING.md log entry for that toolbox (several record exact verified calls), (2) the toolbox's `demos/*.sce` or `tests/`, (3) the flagship macro's help/comment header in `~/Projects/SciLabProjects/<name>/macros/`. Each file follows the sci_gsl pattern — compute, then set `smoke_ok` from an exact or tolerance check, e.g. shape:

```scilab
// <name> smoke: <one-line what/why>, source: <demo file or tracker entry>
res = <flagship_function>(<tiny args>);
smoke_ok = (abs(res - <expected>) < 1e-10);   // or an exact/structural check
```

Known anchors: FOSSEE → a 2-variable `linprog` LP from its own macro example (its linprog/quadprog/fmincon were all verified working during the port); **sci-ipopt → must SOLVE a tiny QP from its `demos/`** (the historical failure was MPI at solve time — a load-only check proves nothing); dataint/montesci are GUI-oriented — their smoke must stay non-interactive (call a computational entry point, or if literally everything opens a window, assert the flagship function `exists()==1` and note "GUI-only, load-verified" in the report).

- [ ] **Step 2: Sweep exactly these 13**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab/scilab
./tbx-verify-all.sh apifun cma-es dataint fmincont FOSSEE-Optimization-toolbox grocer \
                    intprbs lsf_toolbox montesci nisp pso-toolbox sci_gsl sci-ipopt
```

Expected: all 13 `PASS` with `smoke=OK`. Any FAIL: apply the § Playbook (these were all working at port time — a failure now is a stale-lib or dylib-path regression, both playbook one-liners); fix in the toolbox's own repo, commit there, re-sweep that name.

- [ ] **Step 3: Add the passing names to `cfg.verified`** in `tbx_cfg.sci` — append to the existing vector, e.g. the block becomes:

```scilab
    cfg.verified = ["sciDatabase" "parquet" "xlsx" "libsvm" "guibuilder" "scicv" ..
                    "cgal" "sndfile-toolbox" "sciSymPy" "sciTorch" "sciQuantLib" ..
                    "PIMS" "financial" "nan" "quapro" "json" "specfun" "distfun" ..
                    "scidoe" "stixbox" "lowdisc" ..
                    "apifun" "cma-es" "dataint" "fmincont" "FOSSEE-Optimization-toolbox" ..
                    "grocer" "intprbs" "lsf_toolbox" "montesci" "nisp" ..
                    "pso-toolbox" "sci_gsl" "sci-ipopt"];
```

Then regenerate the module lib (same genlib one-liner as Task 1 Step 3) and confirm the GUI math headlessly:

```bash
./bin/scilab-adv-cli -nb -e 'C = tbxCatalog(); mprintf("%d/%d verified\n", sum(bool2s(C.verified)), size(C.name,"*")); exit(0);'
```

Expected: `34/50 verified`. (Note: `cfg.verified` had 21 names even though 22 toolboxes were verified-in-practice — FOSSEE-Optimization-toolbox was verified+registered at port time but never added to the vector; this step is what finally records it.)

- [ ] **Step 4: Update the report matrix** (statuses + smoke column for the 13) and **commit both repos' changes**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/tbx-smoke scilab/modules/toolbox_manager/macros/tbx_cfg.sci \
        docs/design/toolbox-verification.md
git commit -m "tbxManager: verify the 13 ported finance-campaign toolboxes (smokes + cfg.verified)"
# (.bin/lib regenerated locally but NEVER committed — they are git-ignored generated artifacts)
```

---

### Task 5: regtools — strip the guimaker auto-install (class C, known fix)

**Files:**
- Modify: `~/Projects/SciLabProjects/regtools/etc/regtools.start` (the auto-install block; exact file may be `etc/<something>.start` — locate with `grep -rn "guimaker\|atomsInstall" ~/Projects/SciLabProjects/regtools/etc/`)
- Create: `scilab/tbx-smoke/regtools.sce`

**Interfaces:**
- Consumes: § Playbook; driver.
- Produces: regtools PASS; `cfg.verified` += `"regtools"`.

- [ ] **Step 1: Reproduce** — `./tbx-verify-all.sh regtools`; expected FAIL with the loader error mentioning guimaker/ATOMS.
- [ ] **Step 2: Fix** — in the `.start`, guard or delete the guimaker auto-install so load never touches ATOMS. Keep the GUI functions degrade-gracefully: wrap their guimaker use so calling `linregr`/`nlinregr` interactively without guimaker raises a clear error instead of load-time failure. Batch functions (`ff2n`, `fullfact`) must work.
- [ ] **Step 3: Smoke** — `scilab/tbx-smoke/regtools.sce`:

```scilab
// regtools smoke: full-factorial design is the batch (non-GUI) core.
d = fullfact([2 3]);
smoke_ok = (size(d, 1) == 6 & size(d, 2) == 2);
```

- [ ] **Step 4: Verify** — `./tbx-verify-all.sh regtools` → `PASS delta>=1; smoke=OK`.
- [ ] **Step 5: Commit** — regtools repo: `git -C ~/Projects/SciLabProjects/regtools add -A && git -C ~/Projects/SciLabProjects/regtools commit -m "macOS/2027: load without guimaker; GUI tools degrade to clear runtime error"` (push its remotes if configured). Scilab repo: add `"regtools"` to `cfg.verified` + regen lib + smoke file + report row; commit `"tbxManager: regtools verified (guimaker decoupled)"`.

---

### Task 6: krisp — make the native `corr_*` primitives register (class B)

**Files:**
- Modify: `~/Projects/SciLabProjects/krisp/loader.sce` and/or its gateway loader (`sci_gateway/loader_gateway.sce` or equivalent — the port log says natives *build* but don't *register*)
- Create: `scilab/tbx-smoke/krisp.sce`

**Interfaces:**
- Consumes: § Playbook (dylib + addinter class); driver.
- Produces: krisp PASS with primitives live; `cfg.verified` += `"krisp"`.

- [ ] **Step 1: Enumerate what must register** — list the gateway's exported primitives: `grep -rn "addinter\|gw_\|getGatewayStruct\|sci_" ~/Projects/SciLabProjects/krisp/sci_gateway/ | head -30`; record the primitive names (the `corr_*` family).
- [ ] **Step 2: Reproduce** — `./bin/scilab-adv-cli -nb -e 'exec(fullfile(getenv("HOME"),"Projects/SciLabProjects/krisp/loader.sce"),-1); mprintf("corr reg: %d\n", exists("<first-corr-primitive>")); exit(0);'` — expected `0` (not registered) and possibly a swallowed `addinter` error; run loader without `-1` to see it.
- [ ] **Step 3: Fix** — typical causes in priority order: `addinter` pointed at a wrong lib name/path (check `loader_gateway.sce` paths vs actual `.dylib` in `sci_gateway/.libs` or `libs/`), dylib deps unresolved (`otool -L` → `@loader_path` rewrite + `codesign -f -s -`), or gateway-name/function-table mismatch in the gateway C (function listed in the XML/struct but not exported). Apply, re-run Step 2 until `exists(...)==1`.
- [ ] **Step 4: Smoke** — `scilab/tbx-smoke/krisp.sce`:

```scilab
// krisp smoke: RLHS (Latin hypercube, verified working at port time) + natives registered.
X = RLHS(8, 2);
ok_lhs = (size(X,1) == 8 & size(X,2) == 2);
smoke_ok = ok_lhs & (exists("<first-corr-primitive>") == 1);
```

(Replace `<first-corr-primitive>` with the actual name recorded in Step 1 — e.g. `corr_gauss` if that's what the gateway exports.)

- [ ] **Step 5: Verify + commit** — `./tbx-verify-all.sh krisp` → PASS. Commit krisp repo (`"macOS/2027: register native corr_* gateway at load"`); scilab repo: cfg.verified += `"krisp"`, regen lib, smoke, report row; commit `"tbxManager: krisp verified (corr_* gateway registration)"`.

---

### Task 7: csv-readwrite — finish the port (class C; delist decision only if it resists)

**Files:**
- Modify: `~/Projects/SciLabProjects/csv-readwrite/src/**` + `sci_gateway/**` (the remaining ~20%: gateway signature fixes; `MALLOC.h`→`sci_malloc.h` class — the port log says these fixes were partially committed locally already)
- Create: `scilab/tbx-smoke/csv-readwrite.sce`

**Interfaces:**
- Consumes: § Playbook; driver.
- Produces: either csv-readwrite PASS + `cfg.verified` += `"csv-readwrite"`, or an evidence bundle for a user delist decision.

- [ ] **Step 1: Reproduce** — `./tbx-verify-all.sh csv-readwrite`; capture the exact build errors from the kept scratch `log.txt`.
- [ ] **Step 2: Fix build** — apply § Playbook to each error (this toolbox's documented classes: `MALLOC.h`, gateway sigs including the legacy 1-arg `(char *fname)` form). Time-box: 90 min.
- [ ] **Step 3: Smoke** — note its function names must not collide with core's `csvRead` (the toolbox's are historically `csv_read`-style; confirm from its macros/gateway):

```scilab
// csv-readwrite smoke: roundtrip a 2x3 matrix through the TOOLBOX's writer/reader.
p = fullfile(TMPDIR, "tbxsmoke.csv");
M = [1 2 3; 4 5 6];
<toolbox_write_fn>(p, M);
N = <toolbox_read_fn>(p);
smoke_ok = and(N == M);
```

(Fill the two function names from the toolbox's own `macros/`/gateway table during Step 2 — record them in the report note.)

- [ ] **Step 4: Verify + commit** as in prior tasks (`cfg.verified` += `"csv-readwrite"`; toolbox-repo commit `"macOS/2027 arm64: finish gateway port"`).
- [ ] **Step 5 (only if Step 2's time-box expires):** STOP. Present to the user: remaining errors, effort estimate, and the delist case (core Scilab ships `csvRead`/`csvWrite`/`csvTextScan` — this toolbox is redundant; verified in the port log). Delisting = moving the dir out of `~/Projects/SciLabProjects` (e.g. to `~/Projects/SciLabProjects/.attic/`) so `tbxCatalog()` stops offering it. Do nothing without the user's answer.

---

### Task 8: arfit — root-cause the runtime hang (class B; fix-or-delist)  ⛳ CHECKPOINT after

**Files:**
- Modify: `~/Projects/SciLabProjects/arfit/macros/*.sci` (suspect: `mtlb_*` emulation loops)
- Create: `scilab/tbx-smoke/arfit.sce`

**Interfaces:**
- Consumes: § Playbook; driver (the smoke + `gtimeout` is what converts "hangs" into a recorded TIMEOUT).
- Produces: arfit PASS + `cfg.verified` += `"arfit"`, or evidence bundle for the user (delist case: VAR fitting is covered by grocer, which is verified in Task 4).

- [ ] **Step 1: Write the smoke FIRST** (it's the failing test) — take the smallest example from arfit's own `demos/` or the `arfit` function header (a 2-D VAR(1) fit on ~100 synthetic points; exact call per its header — signature style `[w, A, C] = arfit(v, pmin, pmax)`, confirm in the file):

```scilab
// arfit smoke: tiny VAR fit; the port log says core fns HANG under 2027 — this pins it.
rand("seed", 0);
v = rand(100, 2);
[w, A, C] = arfit(v, 1, 2);
smoke_ok = (size(A, 1) == 2);
```

- [ ] **Step 2: Reproduce** — `TBX_TIMEOUT=120 ./tbx-verify-all.sh arfit` → expected `TIMEOUT`.
- [ ] **Step 3: Locate the hang** — run the smoke body line-by-line in `scilab-adv-cli` with `mode(3)` (echo) to find the looping macro; inspect for 2027-behavior traps: `mtlb_*` compat shims looping (e.g. old `mtlb_e`/implicit-size loops), `while` conditions comparing empty matrices (2027 `[] == x` semantics), or old `error(...)`-driven retry loops. Fix the offending macro(s). Time-box: 2 h.
- [ ] **Step 4: Verify + commit** (arfit repo `"macOS/2027: fix <macro> hang (…)"`; scilab repo cfg/lib/smoke/report `"tbxManager: arfit verified"`).
- [ ] **Step 5 (if time-box expires):** STOP with evidence (hang site, what was tried) + recommendation (delist to `.attic/`; grocer covers VAR). **CHECKPOINT:** whether Task 7/8 ended in fixes or delist questions, sync with the user here before the long tail.

---

### Task 9: The 10 untouched macro-only toolboxes (class D easy tier)

**Files (per toolbox `<n>` in: anova, casci, condnb, conint, dbldbl, hypt, makematrix, neuralnetwork, number, ortpol):**
- Modify: `~/Projects/SciLabProjects/<n>/**` (playbook fixes as needed)
- Create: `scilab/tbx-smoke/<n>.sce`
- Modify: `scilab/modules/toolbox_manager/macros/tbx_cfg.sci` + regen lib; `docs/design/toolbox-verification.md`

**Interfaces:**
- Consumes: § Playbook; driver; smoke contract.
- Produces: each passing name appended to `cfg.verified`; per-name report notes; toolbox-repo commits.

Run this identical loop for EACH of the 10 names, one at a time (fresh state each; commit each as it lands):

- [ ] **Step 1: Sweep it** — `./tbx-verify-all.sh <n>`. If the Task 3 baseline already had it PASS, skip to Step 3.
- [ ] **Step 2: Fix to green** — read the scratch `log.txt`; apply the matching § Playbook rows (this tier's expected failures are exactly: stale `macros/lib`/`.bin` needing a builder re-run, 2027 parser strictness in old macros, deprecated calls). Re-sweep until built+loaded.
- [ ] **Step 3: Write its smoke** — pick the flagship function from `demos/` or the macro the toolbox is named for (anova→`anova`, conint→a confidence-interval fn, ortpol→an orthogonal-poly evaluator, number→a primality/number-theory fn, dbldbl→a double-double arithmetic op, makematrix→a matrix generator, hypt→a hypothesis test, condnb→a condition-number fn, casci→per its README, neuralnetwork→a tiny train/eval); assert a known value or a structural property (sizes, monotonicity, `p ∈ [0,1]`) with the Task 4 Step 1 template. No GUI calls.
- [ ] **Step 4: Verify** — `./tbx-verify-all.sh <n>` → `PASS … smoke=OK`.
- [ ] **Step 5: Commit** — toolbox repo (if changes: `"macOS/2027: <what>"`); scilab repo: cfg.verified += `"<n>"`, regen lib, add smoke + report row, commit `"tbxManager: <n> verified"`.
- [ ] **Step 6 (any resister):** after a 60-min time-box, record the blocker in the report and move on; collect all resisters for the Task 13 checkpoint rather than stalling the batch.

---

### Task 10: accsum — native C gateway port (class D)

**Files:**
- Modify: `~/Projects/SciLabProjects/accsum/src/**`, `sci_gateway/**`, `builder.sce`/`build_macos.sce`
- Create: `scilab/tbx-smoke/accsum.sce`

**Interfaces:** consumes § Playbook (this is the sciQuantLib/krisp recipe class); produces accsum PASS + cfg entry.

- [ ] **Step 1: Sweep** — `./tbx-verify-all.sh accsum`; collect build errors.
- [ ] **Step 2: Port the gateway** — expected exact classes: `MALLOC.h`→`sci_malloc.h`; delete `stack-c.h` includes; gateway signatures to `(char *fname, void *pvApiCtx)` where demanded; builder Darwin branch (compiler flags, no `-lgomp`); after build, `otool -L` the produced dylib → `@loader_path` rewrites + `codesign -f -s -`. Iterate with Step 1 until built+loaded (arch gate must show arm64).
- [ ] **Step 3: Smoke** — accsum = accurate summation; its classic demo: summing an ill-conditioned series matches the compensated result:

```scilab
// accsum smoke: compensated sum of an ill-conditioned vector (flagship fn per its demos/).
x = [1e16, 1, -1e16];
s = <accsum_flagship_fn>(x);   // exact name from macros/ (e.g. accsum/xsum-style)
smoke_ok = (s == 1);
```

- [ ] **Step 4: Verify + commit** (toolbox repo `"macOS arm64/2027: port C gateway"`; scilab repo `"tbxManager: accsum verified"`).

---

### Task 11: scidb — ✅ RESOLVED (deleted, user decision 2026-07-11)

Investigated: scidb = the legacy Qt4-based "Database Module + FuzzySQL" toolbox (QtCore4/QtSql4 — unbuildable on modern macOS arm64), fully superseded by the verified **sciDatabase** (5 engines, 33 verbs). User chose deletion over porting. Executed: the one unpushed local commit (`75f5bc6`, help-jar guard fix) was pushed to `jlmoya/scidb` on GitLab AND GitHub, then `~/Projects/SciLabProjects/scidb` was deleted. Catalog is now 50; nothing referenced scidb in `cfg.verified`. Remaining for Task 13: record this in the report's delist ledger.

---

### Task 12: scimax — Maxima bridge (class D; external runtime dep)

**Files:**
- Modify: `~/Projects/SciLabProjects/scimax/**` as needed
- Create: `scilab/tbx-smoke/scimax.sce`

**Interfaces:** consumes § Playbook; produces scimax PASS + cfg entry + a report note that it requires the `maxima` binary at runtime.

- [ ] **Step 1: Install the runtime dep** — `brew install maxima`; confirm `which maxima` and `echo "1+1;" | maxima --very-quiet` prints `2`.
- [ ] **Step 2: Sweep + fix** — `./tbx-verify-all.sh scimax`; apply § Playbook to build/load issues (its `src/` is the Scilab↔Maxima pipe layer; watch for hardcoded `/usr/local` maxima paths → resolve via `which maxima` or a cfg point; that hardcode class is the likely fix).
- [ ] **Step 3: Smoke** —

```scilab
// scimax smoke: round-trip a symbolic derivative through Maxima.
r = <scimax_eval_fn>("diff(x^2, x)");   // exact fn name from its macros/ (e.g. maxima("..."))
smoke_ok = (grep(string(r), "2") <> []);   // derivative contains 2*x
```

- [ ] **Step 4: Verify + commit**; report note: `verified; requires Homebrew maxima at runtime (smoke gated on it)`.

---

### Task 13: Final sweep, full cfg refresh, GUI proof, ship  ⛳ CHECKPOINT

**Files:**
- Modify: `scilab/modules/toolbox_manager/macros/tbx_cfg.sci` (+ `.bin`/`lib` regen), `docs/design/toolbox-verification.md`
- Read/verify only: `modules/toolbox_manager/help/en_US/tbxManager.xml` (its "verified-on-macOS set is pre-ticked" wording stays true; edit only if a delist changed semantics)

**Interfaces:** consumes everything above; produces the end state: catalog == verified set (modulo user-sanctioned delists).

- [ ] **Step 1: Full re-sweep** — `./tbx-verify-all.sh` (no args). Acceptance: every cataloged name `PASS` (`smoke=OK` for all names added by this plan). Any user-sanctioned delists from Tasks 7/8/11 must already be out of `~/Projects/SciLabProjects` (so out of the catalog), with the report's note explaining each.
- [ ] **Step 2: Reconcile `cfg.verified`** — set it to exactly the sweep's PASS list (the driver prints the paste-ready vector; keep the original 21 first for diff-friendliness, then the additions). Regen lib. Headless check: `tbxCatalog()` count line (Task 4 Step 3 command) must print `N/N verified` where N = catalog size.
- [ ] **Step 3: GUI proof** — kill any running Scilab instance first (one-instance mandate), then `./run-with-toolboxes.sh`, open `tbxManager()`: every row must show `(verified)`; screenshot for the report. Leave this one instance running for the user.
- [ ] **Step 4: Finalize the report** — final matrix (all PASS), per-toolbox notes for everything touched, the re-run instructions, and the delist ledger (if any).
- [ ] **Step 5: Commit + push both remotes**

```bash
cd /Users/josemoya/Projects/CLionProjects/scilab
git add scilab/modules/toolbox_manager/macros/tbx_cfg.sci \
        scilab/tbx-smoke docs/design/toolbox-verification.md
git commit -m "tbxManager: full catalog verified — cfg.verified == tbxCatalog (see docs/design/toolbox-verification.md)"
git push gitlab main && git push origin main
```

Also push every toolbox repo that gained commits (loop `~/Projects/SciLabProjects/<changed>` → `git push` its configured remotes).

**CHECKPOINT:** report the final matrix + anything delisted to the user.

---

## Execution notes

- **Order:** Tasks 1→4 are strictly sequential (harness before sweeps, baseline before fixes). Tasks 5–12 are independent of each other and can run in any order or in parallel worktree-free (they touch disjoint toolbox repos; the shared files — `tbx_cfg.sci`, the report — are append-only single-line merges; if parallelizing with subagents, serialize just the cfg/report commits).
- **Time-boxes are real:** the plan's per-toolbox boxes (60–120 min) exist because 4 of the 30 have unknown-depth holes; a resister gets a report note + checkpoint escalation, never an indefinite dig.
- **A sweep is the only definition of done** for any toolbox task: no "it worked when I ran it by hand" — the TSV line is the proof.
