# ME-144 — Glyphs where a row says a word

| | |
|---|---|
| **Order** | 74 — after ME-142, whose two adjustments it must also draw |
| **Depends on** | ME-147 (`iconOf` and the chip row that draws it), ME-161 (the chooser strip) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Give Crop's aspect chips and Resize's presets their glyphs, put a glyph on every chip of the
adjustments chooser, and answer the one case the mechanism cannot serve — `Portrait`, which is a
`BoolParam` and has no icon slot at all.

**Why now.** The block was written 2026-08-22 predicting "the work is mostly SVGs, not Kotlin; the row
renderer is a small change". Checked 2026-09-01, it came out the other way round: **the renderer
shipped and the candidates did not.**

What exists: `ChoiceParam.iconOf` (`ParamSpecChoice.kt:44`, defaulted to `{ null }`),
`ParamRowChoice.kt:68` passing `spec.iconOf(option)` through, and `ParamRowChip` resolving a painter
per chip and drawing it in place of the label. Two schemas use it — `MarkupPen` for the shape kinds and
`MarkupType` for the four text faces (ME-151).

What does not: `CropTool.Aspect` and `ResizeTool.Preset` / `LongEdge` declare no `iconOf`, so they draw
words. And the drawable folder has grown from ~90 to **102** on its own, already holding
`aspectratio.svg`, `rectangle.svg` and `rectangle_arrowtriangle_2_inward/outward.svg` — so part of the
art this block was going to commission is in the tree.

So the Crop and Resize half is one sitting of Kotlin plus whatever glyphs are genuinely missing.

## The adjustments chooser, asked for by hand 2026-09-01

ME-161 draws the adjustments as a scrolling strip of chips, each `name + value`. With eight after
ME-142 and fifteen after ME-162 the strip is long, and a glyph is the obvious way to shorten a chip.

**What it actually buys, measured rather than hoped.** A named chip is padding 20 + name ~68 + gap 6 +
readout 48 ≈ **142 dp**, so about 2.8 fit in the 392 dp card. With a glyph instead of the name it is
20 + 16 + 6 + 48 ≈ **90 dp**, about 4.4. That is a third more of the list in view — worth having, and
**it does not remove the scrolling**: fifteen chips are ~1350 dp either way. The strip was designed to
scroll and this makes the scrolling shorter, not unnecessary.

**And it takes the name away, which is the part that needs answering.** ME-109 recorded what an
unlabelled glyph costs: two tools whose icons had not been drawn were two identical squares nobody
could tell apart, and that is how the rail gained its labels. Fifteen adjustment glyphs are a harder
set than two tool icons — exposure, brilliance and brightness are three ideas one sun can be drawn
for. So the name has to reappear where there is room for it: **beside the selected parameter's
control**, which today shows a slider, a number and `Reset` and no name at all (`ParamValueControls`).
One `BasicText` and the chooser reads as well as it did with fifteen words in it.

A tooltip is *not* the answer, for the reason ME-161 rejected the compact bar: a name you have to hover
to read is not a way to read a panel. The tooltip stays as the second line of defence.

## The one thing the mechanism cannot do

`CropTool.Portrait` is a `BoolParam`, and `BoolParam` renders as *label plus `IosSwitch`* with **no
icon path anywhere in the spec or the row**. An orientation is the single best glyph candidate in the
editor — a tall rectangle and a wide one — and it is the one control that cannot take one.

Two ways out, and step 4 takes the second:

- add `icon` to `BoolParam` and a glyph branch to `BoolParamRow` — a new capability on a spec type used
  by tools that will never want it;
- **make it a two-option `ChoiceParam`**, which is what it already means. A switch labelled "Portrait"
  is a two-state choice wearing a boolean, and as a choice it inherits `iconOf` free. The cost is a
  key that used to hold `true`/`false` reading an enum, so the document written by an older sitting has
  to keep loading — which is what step 4's migration note is about, and why this is the step to get
  wrong.

## Steps

1. **`tools/crop/CropAspect.kt` (edit)** — `iconOf` on the aspect enum: a square, a wide rectangle and
   a tall one, plus `Original` and `Freeform`. Where a glyph is missing it is exported first (step 5)
   with the attributes stripped — an SVG carrying `width`/`height` measures itself and an SVG carrying
   only a `viewBox` measures 100×100, which is what made three of ME-151's four glyphs render as dots.

2. **`tools/resize/ResizeTool.kt` (edit)** — `iconOf` on `Preset` and `LongEdge`. A preset that is a
   *number* keeps its word: `2048` says more than any glyph could, and the block's own rule is that a
   number stays a number.

3. **`ParamRowChip.kt` (edit, 133 lines)** — a chip carrying both a glyph and a word where the glyph
   alone is ambiguous. Today `icon` replaces the label; `Original` and `Freeform` are aspect *modes*
   rather than shapes and read badly as pictograms, so the row needs to allow the pair. If this pushes
   the file past 150, the chip's own drawing moves to `ParamRowChipGlyph.kt` and that split is this
   step.

4. **`tools/crop/CropTool.kt` (edit)** — `Portrait` becomes a two-option `ChoiceParam` with an icon
   each. **The old key keeps loading**: `ParamValue.BoolValue` under key `portrait` must still resolve,
   or every photograph carrying a crop from before this ticket reopens with its orientation lost. That
   is a decode fallback in the param layer, not a rename.

5. **`core/ParamSpec.kt` (edit)** — `FloatParam` gains `icon: DrawableResource? = null`, the field
   `ChoiceParam` already has through `iconOf`. A spec type used by tools that will never want one pays
   a null default, which is what `step` and `unit` already do on the same class.

6. **`shell/ParamStripChip.kt` (edit)** — the glyph replaces the label when the spec carries one, and
   the label stays when it does not, so a strip of un-iconed floats is unchanged.

7. **`shell/ParamRowStrip.kt` (edit)** — the selected parameter's **name**, above its control. This is
   not decoration: it is what pays for taking the name off the chips, and without it the strip is
   fifteen glyphs and no words.

8. **`tools/adjust/AdjustTool.kt` (edit)** — one `icon =` per `FloatParam`. Eight after ME-142.

9. **`composeResources/drawable/` (new files)** — the glyphs steps 1–8 could not find, and this is the
   bulk of the ticket: nothing in the ~102 drawables is an exposure, a contrast or a saturation. Named
   in the family's style, `viewBox` only, no `width`/`height`.

## Files

`tools/crop/CropAspect.kt` (edit), `tools/crop/CropTool.kt` (edit), `tools/resize/ResizeTool.kt`
(edit), `tools/adjust/AdjustTool.kt` (edit), `shell/ParamRowChip.kt` (edit),
`shell/ParamStripChip.kt` (edit), `shell/ParamRowStrip.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`core/ParamSpec.kt` (edit) under `shared/src/commonMain/kotlin/com/woka/feature/editor/`;
new SVGs under `shared/src/commonMain/composeResources/drawable/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and status table).

**Eleven files is past the eight this project aims at**, and the seam is between the Crop/Resize half
(steps 1–4) and the adjustments half (steps 5–8). Two sittings under one id, or promote the second
half to its own; the glyphs of step 9 belong to whichever half needs them.

## Verification

- `jvmTest/ui/ParamChipRowUiTest.kt` (edit) — a chip with an icon draws the glyph; a chip with both
  draws both; a chip with neither is unchanged;
- `jvmTest/unit/CropParamsTest.kt` (new) — **the step-4 test**: a `ParamSet` holding
  `portrait` as a boolean decodes to the new choice, `true` landing on the portrait option. Without
  this the migration is a silent data loss on every previously cropped photograph;
- `jvmTest/ui/CropToolSwitchUiTest.kt`, `CropFrameUiTest.kt` — the orientation still flips the frame,
  which is what says the spec change did not change the behaviour;
- `jvmTest/unit/EditorIconsTest.kt` (new) — every icon named by an `iconOf` in the tree resolves, and
  every new SVG measures 100×100 like the other ninety. That second assertion is the ME-151 bug caught
  from the other side;
- `jvmTest/ui/ParamRowUiTest.kt`, `EditorShellUiTest.kt` — unchanged and still green.

## Done when

- Crop's aspect chips and Resize's presets show glyphs where a glyph is clearer, and words where a
  number or a mode is clearer;
- every adjustment chip carries a glyph, and the selected adjustment's **name** is drawn beside its
  control — a strip of unlabelled glyphs is the ME-109 defect restated;
- a chip is about 90 dp rather than 142, so about four are in view rather than three. The strip still
  scrolls at fifteen, and that is the design;
- `Portrait` is a two-option choice with two glyphs, and a crop saved before this ticket still opens
  with its orientation;
- every new SVG measures 100×100 (`EditorIconsTest`);
- no adjustment slider and no numeric field lost its name — this ticket does not touch them;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
