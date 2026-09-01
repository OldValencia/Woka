# ME-144 — Glyphs where a row says a word

| | |
|---|---|
| **Order** | 74 — after ME-142, whose two adjustments it must also draw |
| **Depends on** | ME-147 (`iconOf` and the chip row that draws it), ME-161 (the chooser strip) |
| **Feature** | [Image Editor](../../image-editor.md) |

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

**And it takes the name away, which is the part that was got wrong.** Shipped that way, seen on
screen, refused: *"верни текст. Пусть будет иконка + текст. Потому что выглядит по уебански без
текста"*. So a chip carries **both**, and it is the same answer ME-109 got from the rail — two tools
whose icons had not been drawn were two identical squares nobody could tell apart, and that is how the
rail gained its labels. Adjustment glyphs are a harder set than tool icons: exposure, brilliance and
brightness are three ideas one sun can be drawn for.

The saving therefore does not arrive, and that is the honest outcome: a chip is ~142 dp with the glyph
beside the name rather than ~90 without it, and the strip scrolls exactly as far as before. What the
glyph buys is a strip that can be *scanned* rather than read word by word. The name above the selected
control, designed here as the place the label would move to, was built and then deleted the same day —
with the chips carrying names it was the same word twice.

A tooltip was never the answer either, for the reason ME-161 rejected the compact bar: a name you have
to hover to read is not a way to read a panel. The tooltip stays as the second line of defence.

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

1. **Crop's aspects and Resize's presets keep their words — dropped, not deferred.** The block's own
   rule settles it: *a number stays a number*. `1:1`, `4:3` and `16:9` are read instantly and a
   rectangle drawn to those proportions is not; `2048` and `UHD` say more than any glyph could. Spending
   a dozen exports there to make five ratios less legible is the submenu problem in a different
   costume. What was left of steps 1–3 is nothing, and saying so is worth more than a `iconOf` that
   makes the panel worse.

2. **`tools/crop/CropTool.kt` (edit)** — `Portrait` becomes a two-option `ChoiceParam` with an icon
   each. **The old key keeps loading**: `ParamValue.BoolValue` under key `portrait` must still resolve,
   or every photograph carrying a crop from before this ticket reopens with its orientation lost. That
   is a decode fallback in the param layer, not a rename.

3. **`core/ParamSpec.kt` (edit)** — `FloatParam` gains `icon: DrawableResource? = null`, the field
   `ChoiceParam` already has through `iconOf`. A spec type used by tools that will never want one pays
   a null default, which is what `step` and `unit` already do on the same class.

4. **`shell/ParamStripChip.kt` (edit)** — the glyph is drawn **beside** the label where the spec
   carries one, and a spec with no glyph is unchanged. Null is a working state, not an unfinished one:
   that is what let the art arrive one file at a time instead of all fifteen before any of them.

5. **`shell/ParamRowPreviewChoice.kt` (edit)** — the Filters strip gains the same primary-button drag
   the adjustments chooser has (ME-161). Two scrolling strips one panel apart answering the mouse
   differently was reported by hand as a divergence; the right-click reset it needed was already
   there, on the square that carries a strength bar.

6. **`tools/adjust/AdjustTool.kt` (edit)** — one `icon =` per `FloatParam`, eight after ME-142, plus
   one alias each in `Icons.Static` so swapping the art is a line there rather than a change to the
   tool.

7. **`composeResources/drawable/` (nine new files)** — exported from SF Symbols **by the author**, not
   drawn here: the ~102 existing icons carry `Generator: Apple Native CoreSVG`, and a hand-drawn path
   beside them reads as foreign whatever its shape. Each was checked for the ME-151 defect — an SVG
   carrying `width`/`height` measures itself while one carrying only a `viewBox` measures 100×100 — and
   all nine carry a `viewBox` alone.

## Files

`tools/crop/CropAspect.kt` (edit), `tools/crop/CropTool.kt` (edit), `tools/resize/ResizeTool.kt`
(edit), `tools/adjust/AdjustTool.kt` (edit), `shell/ParamRowChip.kt` (edit),
`shell/ParamStripChip.kt` (edit), `shell/ParamRowPreviewChoice.kt` (edit),
`tools/crop/CropAspect.kt` (edit), `tools/crop/CropState.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`core/ParamSpec.kt` (edit) under `shared/src/commonMain/kotlin/com/woka/feature/editor/`;
new SVGs under `shared/src/commonMain/composeResources/drawable/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and status table).

**Eleven files was past the eight this project aims at**, and dropping the aspect and preset glyphs is
what brought it back inside: what shipped is one orientation pair, eight adjustment glyphs and the
Filters drag.

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

- Crop's aspects and Resize's presets are unchanged, and the ticket says why;
- every adjustment chip carries a glyph **and** its name, and nothing repeats the name above the
  slider;
- the Filters strip drags with the primary button, as the adjustments chooser does;
- `Portrait` is a two-option choice with two glyphs, and a crop saved before this ticket still opens
  with its orientation;
- every new SVG measures 100×100 (`EditorIconsTest`);
- no adjustment slider and no numeric field lost its name — this ticket does not touch them;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
