# ME-144 — Glyphs where a row says a word

| | |
|---|---|
| **Order** | 71 — independent; nothing waits on it |
| **Depends on** | ME-147 (`iconOf` and the chip row that draws it) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Give Crop's aspect chips and Resize's presets their glyphs, and answer the one case the
mechanism cannot serve — `Portrait`, which is a `BoolParam` and has no icon slot at all.

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

So the ticket is one sitting of Kotlin plus whatever glyphs are genuinely missing, not a drawing job.

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

5. **`composeResources/drawable/` (new files)** — only the glyphs steps 1–4 could not find. Named in
   the family's style, `viewBox` only, no `width`/`height`.

## Files

`tools/crop/CropAspect.kt` (edit), `tools/crop/CropTool.kt` (edit), `tools/resize/ResizeTool.kt`
(edit), `shell/ParamRowChip.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
new SVGs under `shared/src/commonMain/composeResources/drawable/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and status table).

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
- `Portrait` is a two-option choice with two glyphs, and a crop saved before this ticket still opens
  with its orientation;
- every new SVG measures 100×100 (`EditorIconsTest`);
- no adjustment slider and no numeric field lost its name — this ticket does not touch them;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
