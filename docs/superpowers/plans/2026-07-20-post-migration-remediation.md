# Post-migration remediation — turning the deferred-fixes register into work

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **DO NOT START THIS PLAN YET.** It is gated — see *When this unblocks*. It exists now so the
> findings are not lost, not because the work is ready.

**Goal:** Fix the defects the migration deliberately preserved, each one proven to change exactly
what was intended and nothing else.

**Register:** `docs/design/deferred-fixes-register.md` is the authoritative list. This plan sequences
it. If they disagree, the register is right and this plan is stale — update it.

## The premise, stated plainly

The campaign's binding rule is **reproduce, don't improve**. Its corollary is that *a fix is an
improvement*, so every fix is forbidden during the migration and must be deferred. That is correct
discipline, but it only works if the deferred items are (a) written down and (b) scheduled. The
register does (a). This plan does (b).

## When this unblocks

Not before **all** of:

1. Stage 2 complete — all 24 module jars built by Maven, parity-green.
2. The CMake↔Maven swap done — `cmake/ScilabJava.cmake` invokes Maven, not Ant.
3. RC-e complete — `./configure` deleted.
4. **Autotools and Ant deleted** (migration doc §12 endgame).

Reason: until then, the parity baseline *is* the old build, and every fix here would show up as a
parity failure indistinguishable from a migration bug. Fixing before the endgame means debugging
two changes at once — exactly what the whole staged method exists to prevent.

## The method inverts here — this is the important part

During the migration the harness proves **"the new build reproduces the old build."** After it, that
question is meaningless (there is no old build). The harness's job becomes:

> **"This change altered exactly the artifacts it was supposed to, and nothing else."**

So every task below follows the same shape, and **the re-baseline is part of the task, never a
separate cleanup**:

1. **Predict** — write down, before touching anything, exactly which fingerprint entries should move.
2. **Change** one defect. Only one.
3. **Diff** against the current baseline. The failure list must match the prediction **exactly** —
   no extra entries, no missing ones. A surprise entry means the change did more than intended.
4. **Re-baseline** in the *same commit*, with the diff quoted in the commit message.
5. If prediction and diff disagree, **stop and understand it** before re-baselining. A re-baseline
   that swallows an unexplained diff destroys the only evidence that anything went wrong.

Step 1 is what makes this a gate rather than a rubber stamp. A re-baseline without a prior
prediction is just "accept whatever happened."

---

### Task 1: B10 — the ~30 translation units missing `-fwrapv`

**Do this first.** It is the only item in the register that is a **correctness exposure** rather
than cosmetics: this codebase has a documented class of legacy C/C++/Fortran UB that is benign at
`-O0` and **miscompiled at `-O2`** (the `rand()`-returns-`Inf` bug). Global `-fwrapv` hardening was
applied precisely to contain it; ~30 TUs silently escape it via the `_CFLAGS` footgun, where a
per-target `_CFLAGS` overrides the global set.

- [ ] **Step 1: Enumerate, from the harness, not by grep.** Use RC-b's `tu_flag_facts` to list every
  TU whose fact set lacks `-fwrapv`. Record the exact list and count in the report — the register
  says "~30" and that approximation should become a number.
- [ ] **Step 2: Predict.** These TUs' objects change; their dylibs' *symbols* should not. Write that
  prediction down before changing anything.
- [ ] **Step 3: Fix** the per-target flag composition so the global set is extended, not replaced.
- [ ] **Step 4: Verify** `tu_flag_facts` now shows `-fwrapv` on every TU, and that the only
  fingerprint movement is what Step 2 predicted.
- [ ] **Step 5: Re-baseline + commit**, diff quoted.
- [ ] **Step 6: Re-run the UB sweep** over the newly-covered TUs. The point of the flag is to change
  codegen; confirm nothing regressed and record anything it surfaces.

### Task 2: P1 — the double-encoded vendor string

Every jar manifest carries `"Dassault SystÃ¨mes"`. Ant read UTF-8 as Latin-1; Maven now reproduces
the mangled bytes deliberately.

- [ ] **Step 1: Predict** — exactly 24 jar manifest entries move; no `.class` entry does.
- [ ] **Step 2:** Load the properties as UTF-8 (Maven-side; Ant is gone by now) so the manifests
  carry `Systèmes`.
- [ ] **Step 3:** Confirm the diff is exactly the predicted 24 manifests. **A `.class` entry moving
  here means something else changed** — stop.
- [ ] **Step 4:** Remove the now-obsolete "extract the mangled bytes, never retype" warnings from
  every module POM and from the migration doc. Leaving them would send future readers to reproduce
  a defect that no longer exists.
- [ ] **Step 5: Re-baseline + commit.**

### Task 3: P2 — `Class-Path: ${manifest.class-path}` in 22 of 24 jars

An uninterpolated Ant property literal, shipped in every jar that does not set the property.

- [ ] **Step 1: Predict** — 22 manifests lose the attribute; `gui` and `scirenderer` keep theirs
  unchanged.
- [ ] **Step 2:** Emit `Class-Path` only when the value is non-empty.
- [ ] **Step 3:** Verify `gui` and `scirenderer` are byte-unchanged — they are the two that matter,
  and `gui`'s value wraps at 72 bytes, so confirm the wrapping did not shift.
- [ ] **Step 4: Re-baseline + commit.**

### Task 4: P3 + B2 — `scirenderer`'s dead manifest section, and its Ant-only shape

Its section names `org/scilab/modules/scirenderer/` while its classes are
`org.scilab.forge.scirenderer.*`, so the section is inert.

- [ ] **Step 1: Decide, do not assume.** The per-package section is **load-bearing for other
  modules** — `xcos` reads `getSpecificationVersion()`/`getImplementationVersion()` and stamps them
  into saved `.xcos` diagrams. So the choice is per module: point `scirenderer`'s section at the
  real package, or drop it. Check whether anything reads `scirenderer`'s package metadata before
  choosing; record the evidence either way.
- [ ] **Step 2:** Apply, predict, diff, re-baseline.
- [ ] **Step 3:** Fold `scirenderer` into the normal module shape (B2) or document it as
  deliberately special — the reactor makes this cheap once Ant is gone.

### Task 5: H6 — make the harness able to see attribute *absence*

The structural gap that hid Stage 2-c's Critical 1: `normalize_manifest` strips volatile lines from
both sides, so "attribute absent" and "attribute present with a volatile value" are
indistinguishable. Today that is mitigated only by **convention** — every manifest fragment carries
a frozen `00000000 0000`.

- [ ] **Step 1:** Compare attribute **presence** separately from attribute **value**: normalize the
  value, keep the key.
- [ ] **Step 2:** Fault-inject — removing an attribute entirely must FAIL, while changing only its
  volatile value must PASS. Both seen, not assumed.
- [ ] **Step 3:** With the harness able to see presence, revisit whether the frozen
  `00000000 0000` placeholders should become real per-build timestamps.

### Task 6: The B-series cleanups (batchable)

Lower risk, and several are pure deletion once Ant is gone. Fold into one or two commits, but keep
the predict→diff→re-baseline shape for any that touch build output.

- [ ] **B1** `output_stream` — revive or delete, with its dangling `scilab-lib.properties:170-172`.
- [ ] **B3** `terminal` — bring into the reactor uniformly.
- [ ] **B4** delete the dead `debuglevel` attribute (or turn debug on deliberately).
- [ ] **B5** delete `ivy.xml`.
- [ ] **B6** FlatLaf — adopt it or stop fetching it.
- [ ] **B7** Cobertura → JaCoCo, or drop.
- [ ] **B9** `etc/classpath.xml` — emit relative/`$SCILAB`-rooted paths, not machine-absolute ones.
- [ ] **B11** delete the dead Makefile rules, and **only then** simplify RC-b's live-rule filter —
  the filter exists because of them.

### Task 7: The portability gap (not a defect — a blocker for "done")

Maven resolution currently depends on a machine-local `~/.m2/settings.xml` the repository does not
carry: a wildcard mirror to a Nexus answering 401, with resolution succeeding only via an Azure
DevOps feed excluded from that mirror.

- [ ] **Step 1:** Establish what a fresh clone on an unconfigured machine actually needs.
- [ ] **Step 2:** Make the build work there, or document the required configuration **in the repo**.
- [ ] **Step 3:** Verify by building somewhere without this machine's `settings.xml`. **Unverified
  is unfinished** — this is exactly the class of claim (`curl` ≠ Maven) that has been wrong before.

"Delete autotools" implies the Maven build works for someone who is not us. Until Task 7 passes,
that claim is untested.

---

## Self-Review

**Coverage:** every row of the register maps to a task — P1→2, P2→3, P3→4, B1/B3–B7/B9/B11→6,
B2→4, B10→1, B8 (JUnit 5) is already a named Stage 2 sub-task and is *not* deferred here, H1–H5 are
gate limitations that unblock on their own triggers and are listed in the register rather than
scheduled, H6→5. Task 7 is the portability gap, which is a release blocker rather than a defect.

**Ordering:** Task 1 is first because it is the only correctness exposure. Task 5 (harness sees
absence) is deliberately *after* the manifest fixes — doing it first would fail the existing
manifests and conflate two changes.

**Placeholders:** none, but several tasks deliberately end in "decide, and record the evidence"
rather than a predetermined answer — Task 4 Step 1 and Task 7 Step 2 are genuine decisions that
need facts this plan cannot have yet. Those are stated as decisions, not hidden as steps.

**The one thing that must not be lost:** the predict-before-diff discipline. Without Step 1 of each
task, "re-baseline" degrades into "accept whatever happened," and the harness stops meaning anything
the moment it is no longer comparing against a known-good old build.
