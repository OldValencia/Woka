# Manual test cases

The apex of the pyramid in `ai/docs/features/pending/viewer-verification-pass.md`. Everything below
is here because **a machine cannot check it**: visual correctness, how smooth something feels, OS
integration, separate native windows, long runs.

This is a permanent artefact, not a one-off checklist. It is re-run over whatever changed.

## The one rule

> A case belongs here only if it physically cannot be written as an assertion.

Every case therefore carries a mandatory **Why this is not automated** field. That field is what
stops the list growing back into "click through everything". If the answer is anything like "it
would be fiddly to automate", the case does not belong here — it belongs in
`shared/src/jvmTest/.../unit/` or `.../integration/`.

Two examples of the boundary, both real:

- Zoom arithmetic is *not* here. `ZoomScaleTest` covers the formulas; exactly one zoom case survives
  — "the image is not mushy at Actual" — because sharpness is a judgement about pixels on a screen.
- Poster-frame *quality* **is** here (TC-VID-04). The pipeline is asserted end to end by
  `ThumbnailPipelineTest`, but every fixture in the corpus is a few seconds of low-motion footage
  that `isMonochrome` rejects, so "the poster is a frame worth looking at" cannot be asserted on the
  material the suite owns.

## Case format

Fixed. `ID`, `Area`, `Preconditions`, `Steps`, `Expected`, `Why this is not automated`.

```markdown
### TC-XXX-NN — short title

**Area:** what part of the app
**Preconditions:** which files, which settings, which window state
**Steps:**
1. Imperative, numbered, one action each.
2. …
**Expected:** what a pass looks like, precisely enough to disagree with.
**Why this is not automated:** the reason no assertion can replace this.
```

## The id scheme

`TC-<AREA>-<NN>`, two digits, never renumbered. A retired case keeps its id and gains a
`**Retired:** <reason>` line — run logs reference ids, and reusing one would silently rewrite
history.

| Prefix | Area |
|--------|------|
| `TC-IMG` | Still images: what the eye sees |
| `TC-VID` | Video: what is heard and noticed |
| `TC-SUB` | Subtitles: rendering and positioning |
| `TC-PIP` | Picture-in-picture: a separate native window |
| `TC-SYS` | OS integration |
| `TC-PERF` | Long runs and large files |

## Recording a run

One file per run: `results/YYYY-MM-DD-<build>.md`. `<build>` is the short commit the run was made
against, so a result can always be tied back to a tree.

Every case in scope gets exactly one line:

```markdown
# 2026-08-06 — 795b280

Scope: TC-IMG, TC-VID (re-run after the EXIF fix)

| Case | Result | Note |
|------|--------|------|
| TC-IMG-01 | ok | |
| TC-IMG-04 | defect | opens sideways; steps as written, F3 not yet fixed |
| TC-VID-02 | not run | no HDR display available |
```

Three results only: **ok**, **defect**, **not run**. A defect needs steps to reproduce — either
inline or as a pointer to where they were filed. "not run" needs the reason; silently omitting a
case is what makes a run log untrustworthy.

## Fixtures

The committed corpus in `shared/src/jvmTest/resources/media/` is small, synthetic and deliberately
low-motion — it exists to make assertions deterministic, and it is the wrong material for most of
what is here. Cases that need real media say so in their **Preconditions** and name the properties
required (a 4K HDR film, a photo off a phone, a 10-minute clip), never a specific personal file.
