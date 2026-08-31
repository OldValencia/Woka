# ME-164 — The adjustments panel groups what it shows

| | |
|---|---|
| **Order** | 74 — after ME-142, before ME-144 |
| **Depends on** | ME-161 (the chooser), ME-142 (the eight it groups) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Let a spec declare which section it belongs to, and draw a `SegmentedControl` over the
chooser when a run holds more than one — so Light and Colour are two lists rather than one mixed one.

**Why now.** Reported by hand 2026-09-01, at eight adjustments and before fifteen arrive. **The
complaint is not the scrolling** — that was asked and answered directly: *"прокрутка это НЕ проблема.
Проблема в том, что много элементов в одном листе намешаны"*. Exposure sits beside Warmth for no
reason a reader can see, and at fifteen the run would mix three unrelated kinds of adjustment in one
strip.

That distinction decides the shape. A version of this that chased the scroll length would wrap the
chips instead of scrolling them and cost ~88 dp of card; grouping alone keeps the strip exactly as it
is and costs the height of one segmented control — **36 dp, taking the card from ~115 to ~157**.

## Declared, not derived — and why that does not contradict ME-161

ME-161's §9 row says the panel's layout is derived from the schema and never asked of the tool, and
refused a `strips()` hook for it. This does not reopen that. **"Warmth is about colour" cannot be
derived from a range**; it is knowledge the tool has about its own parameters. ME-161 refused a hook
that asked for a *shape*. A section is a *fact*, and facts about parameters are what a schema is.

The schema already carries exactly this: `PreviewChoiceParam.section` (`ParamSpecPreview.kt:30`),
rendered as a heading by `ParamRowPreviewGroup` and used by `FilterTool` for "Undertone" and "Look".
This follows that precedent rather than inventing a second spelling of it.

## Steps

1. **`core/ParamSpec.kt` (edit)** — `FloatParam` gains `section: String? = null`, the field
   `PreviewChoiceParam` already has. A spec type used by tools that will never want one pays a null
   default, as `unit` and `step` already do on the same class.

2. **`tools/adjust/AdjustTool.kt` (edit)** — two sections over the eight: **Light** — Exposure,
   Brightness, Contrast, Black Point — and **Colour** — Saturation, Vibrance, Warmth, Tint. Photos'
   own split, and the third, **Detail**, arrives with ME-162's Sharpness, Definition, Noise Reduction
   and Vignette. The declared order is unchanged, so the ops fold exactly as before.

3. **`shell/ParamStripChooser.kt` (new, step 4's split)** — the section control and the chips it
   filters. `SegmentedControl` with `SegmentedControlDefaults.onScrim`, which exists for this surface
   already.

4. **`shell/ParamRowStrip.kt` (edit, 136 lines)** — the section state beside the selection, and the
   rule that binds them: **changing section moves the selection to that section's first spec**, or the
   control under the strip goes on setting a parameter from a tab nobody is looking at. Adding this to
   the file passes 150, so the chooser moves out — this step is also the split.

5. **The control appears only when there is something to choose between.** One distinct section, or
   none at all, draws no segmented control — so Filter, Markup and every other tool is untouched, and
   a run whose specs declare nothing behaves exactly as it does today.

## Files

`core/ParamSpec.kt` (edit) under `shared/src/commonMain/kotlin/com/woka/feature/editor/`;
`tools/adjust/AdjustTool.kt` (edit), `shell/ParamRowStrip.kt` (edit),
`shell/ParamStripChooser.kt` (new) under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — a §9 row and the status table).

## Verification

- `jvmTest/ui/AdjustSectionUiTest.kt` (new) — the sections are drawn; the strip shows **only** the
  selected section's chips, so `param-chip-warmth` does not exist while Light is open; picking
  `Colour` moves the control to that section's first spec, which is the rule step 4 exists for;
- `jvmTest/ui/AdjustPanelUiTest.kt` (edit) — its assertions about which chips exist now name a
  section, and the one that says a tool with too few sliders draws labelled rows is **unchanged**;
- `jvmTest/ui/AdjustPanelGestureUiTest.kt` — unchanged and still green: dragging and the right-click
  reset are about a chip, not about which chips are shown;
- `jvmTest/unit/AdjustMatrixTest.kt`, `AdjustMatrixToneTest.kt` — unchanged, and this is what says a
  section is a label and not a change to the fold;
- `jvmTest/ui/PreviewChoiceRowUiTest.kt` — unchanged: `PreviewChoiceParam.section` is untouched and
  keeps drawing its heading.

## Done when

- Adjust draws `Light` and `Colour`, and one strip shows one section;
- switching section opens that section's first adjustment;
- a tool whose floats declare no section draws exactly what it drew before;
- the fold is unchanged — the same eight matrices in the same order;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
