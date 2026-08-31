# ME-159 — Markup defaults do not adapt to the picture that was opened

| | |
|---|---|
| **Order** | 70 — a defect in shipped behaviour; ahead of the remaining backlog |
| **Closed** | 2026-08-31, confirmed by hand |
| **Depends on** | ME-113 (the markup engine), ME-122 (text), ME-141/ME-150 (the size rows) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Derive a markup shape's **default** size, and the range its slider offers, from the
dimensions of the picture being edited, instead of shipping one constant for every file.

**Why now.** Reported by hand 2026-08-30. **This is about the first thing the user has to do, not
about what is drawn.** Every markup default is a constant in **source** pixels:

- `MarkupTextSizeParam` — `FloatParam("textSize", …, 12f..512f, default = 64f)` (`MarkupType.kt:25`);
- `MarkupWidthParam` — `FloatParam("width", …, 1f..64f, default = 6f)` (`MarkupPen.kt:72`).

Open a 300 × 400 picture and place a caption: 64 px. Open a 30 000 × 40 000 one and place a caption:
64 px. The number never moves, because nothing in the tool has ever looked at what was opened. So on
every picture that is not the one size the constant was chosen for, **placing a caption is two
actions, not one** — place it, then go and fix its size — and the user pays that step again for the
next shape, and the next.

That is the complaint. Whether 64 px is legible at either extreme is a consequence and not the point:
even a value that happened to read acceptably would still be the wrong value to *start* from, because
the right starting value is a property of the file and the tool does not ask.

There is also no better constant to pick. The two files above are two orders of magnitude apart; any
single number is wrong for one of them by the same factor.

## What the constant reaches, beyond the first placement

`MarkupTool` declares `val TextSize = MarkupTextSizeParam` (`MarkupTool.kt:73`), and the same `range`
is what a corner drag is clamped into — `(width * ratio).coerceIn(MarkupTextSizeParam.range)`
(`MarkupTextFrame.kt:54`). So the range is not only what the slider offers: on a very large picture
the *upper* bound is reached long before the caption looks right, and the extra step the user takes
runs out of travel. Both ends of both ranges are in source pixels.

## The question this ticket has to answer first

**What is the size relative to?** The three candidates are genuinely different and the author should
pick before any code moves:

- **The shorter edge of the source.** A caption at "6 % of the shorter edge" starts at the same
  apparent size on both files above. Simple, and it is a property of the file, which is what makes it
  answer the complaint.
- **The drawn size after the fold** (`EditorEngine.drawnSize`) — which moves when a crop lands, so a
  caption placed before a crop would resize itself when the crop is applied. Almost certainly wrong,
  and worth writing down as rejected.
- **The canvas**, i.e. what is on screen. Wrong for certain: the same document would bake different
  pixels depending on the window size, and `RecipeStillEdits` folds with no canvas at all.

**Whatever is chosen, the wire format is the decision.** `MarkupShape.width` is serialised into the
recipe (ME-113) and files already carry it in absolute pixels. Either the stored number stays absolute
and only the *default* and the *range* are derived from the picture — cheap, and every existing
document keeps meaning what it meant — or the stored number becomes a fraction, which is a schema
change needing the `code-style/model.md` treatment: a new key, a default that reproduces today's
behaviour, and no reuse of the old one. **The first is almost certainly the right answer** and is what
this ticket should be written against unless something argues otherwise.

## Built 2026-08-31 - and the first attempt shipped doing nothing at all

**Read this part first.** The change was made, compiled, unit-tested green and reported as done
- and in the running application every picture still produced a 64 px caption. Found by hand the
same day: a HEIC out of `Pictures/Heic` and a screenshot out of `Pictures/Screenshots` gave the
identical number.

**The cause.** `EditorTool.defaults()` builds a `ParamSet` by **materialising every declared
default into it**, and `markupParams()` / `paramsOf()` fall back to exactly that when the
document has no operation yet. So `textSize` was always already present, always `64`. A derived
default only ever applies where a key is *missing*, and that key is never missing. The first
attempt derived a default and handed it to the row - and the row read the seed instead.

**The lesson, and it is about the test rather than the code.** The unit test drove `markupRows`,
which is the helper that was changed; it could not have failed. What was missing was a test that
places a caption the way a user does and reads the shape back out of the live document.
`jvmTest/ui/MarkupDefaultSizeUiTest.kt` is that test, and it fails on the inert version with
both sizes reading 64.

### What it actually took

The seed is the thing that had to change, so `EditorTool` gained one hook and one signature:

- `specs(sourceSize)` - every spec, re-defaulted for the picture. The identity for every tool but
  Markup. **Not `rows`**: `rows` answers "what is worth drawing", which is a subset, and seeding
  from it would drop the parameters the panel is not currently showing;
- `defaults(sourceSize)` is built from `specs`, so the seed carries derived values;
- `paramsOf(document, tool, sourceSize)`, `ParamWriter(..., sourceSize)`,
  `markupParams(sourceSize)` and `markupOpWith(shapes, sourceSize)` all carry the picture,
  because each of them is a place the seed is reached. `EditorBottomPanel` passes
  `engine.sourceSize` - the **source**, never `drawnSize`, which moves when a crop lands.

`MarkupShape.width` is still an absolute number of source pixels. Nothing stored changed and
every recipe written before this renders exactly as it did.

### The numbers

Relative to the **shorter edge of the source**, per the section above. The range is a wide
constant - `MARKUP_SIZE_RANGE = 1f..1000f`, replacing type's `12..512` and a stroke's `1..64` -
and only the default is derived, decided by hand against this file's own proposal, which had the
range scaling too.

| shorter edge | 300 | 480 | 1000 | 3000 | 6000 | 30000 |
|---|---|---|---|---|---|---|
| type, 6 % | 18 px | 29 px | 60 px | 180 px | 360 px | 1000 px (clamped) |
| stroke, 0.6 % | 2 px | 3 px | 6 px | 18 px | 36 px | 180 px |

**Rounded to whole pixels**, because the row prints this number: `2.88px` under a control is a
worse start than `3px`, and both old constants were whole.

**The clamp, said out loud.** Past a shorter edge of about 16 700 px the derived default leaves
the ceiling and stops there - 1000 px rather than 1800. Still fifteen times the 64 px it used to
be, and the price of the range being a constant. A source of unknown size falls back to the old
64 / 6, which is what every existing document was written against.

### The stepper had to follow the default, not the range

`stepOf` gave 5 for any span over 200 px, so widening a stroke to `1..1000` took its plus-minus
from 1 px a press to 5 - and a 2 px stroke cannot be nudged at all by 5. That is this ticket's
own "the slider's range is usable on both" failing from the other side. `FloatParam` gained an
optional `step`; markup sets it to a twelfth of the derived default; every other spec keeps the
span rule untouched. At the old constants the numbers are unchanged - a 6 px stroke still steps
by 1, 64 px type still by 5.

### Two identity comparisons, both silent

A re-defaulted spec is a **copy**: equal in key, range and encoding, equal to nothing by `==`.

- `markupLines` built the compact bar with `MarkupTool.Width in rows`, so **both size controls
  vanished off the bar**. Now matched by key, against the rows as they arrived;
- `restyleSelected` dispatched on `when (spec)`, so every size write fell through to `else` and
  **moved the pen instead of the selected shape** - the exact defect `MarkupSizeRowUiTest` was
  written for, reintroduced from a new direction. Now `when (spec.key)`.

Audited the rest: `ParamRows`, `ParamRowCompact` and `ParamReset` dispatch on the *type*
(`is FloatParam`), and `ParamRowRuns` compares objects drawn from one list inside one
composition. Neither is affected.

### Files

**Core** `core/EditorTool.kt` (`specs`, `defaults(sourceSize)`, `rows(params, sourceSize)`) ·
`core/ParamSpec.kt` (`FloatParam.step`).
**Markup** `tools/markup/MarkupSizing.kt` (new - the fractions, the clamp, the step) ·
`MarkupType.kt`, `MarkupPen.kt` (ranges) · `MarkupTool.kt` (`specs`) · `MarkupRows.kt`,
`MarkupRestyle.kt` (match by key) · `MarkupState.kt`, `MarkupEdits.kt`, `MarkupTextEdits.kt`
(the seed and the mint sites take `sourceSize`).
**Shell** `shell/ParamValues.kt`, `ParamWriter.kt`, `ParamRows.kt`, `EditorBottomPanel.kt`,
`ParamRowScalars.kt`.
**Other tools** `tools/filter/FilterTool.kt` (signature only).
**Tests** `jvmTest/ui/MarkupDefaultSizeUiTest.kt` (new) · `jvmTest/unit/MarkupSizingTest.kt`
(new) · `support/MarkupUi.kt` (`openMarkupTool` takes a source size) · edited expectations in
`MarkupTextFrameTest.kt`, `MarkupSelectionReadbackUiTest.kt`, `MarkupSliderDragUiTest.kt`,
`IdleToolOverlayTest.kt`.

## Verification

- `jvmTest/ui/MarkupDefaultSizeUiTest.kt` (**new, and the one that matters**) - a caption placed
  through the real editor on a 640 x 480 source is 29 px and on a 4000 x 3000 source is 180 px;
  a stroke is 18 px on the large one; and two pictures do not produce the same caption size.
  Nothing between the pen and the shape is stubbed - the assertions read
  `markupShapes().single().width` out of the live document after a real click, which is exactly
  what the first attempt's tests did not do;
- `jvmTest/unit/MarkupSizingTest.kt` (new) - the derived defaults scale with the shorter edge;
  every one lands inside the offered range; one that would leave it is clamped; an unknown source
  keeps the old constants; a derived spec keeps the declared key and range; and **the compact bar
  still carries the size row after it is re-defaulted**, which is the first trap, pinned;
- `jvmTest/ui/MarkupSizeRowUiTest.kt` - untouched and green, which is what says the row still
  writes to the selected shape rather than to the pen. That is the second trap;
- `jvmTest/ui/MarkupSelectionReadbackUiTest.kt`, `MarkupSliderDragUiTest.kt` - **expectations
  updated, not behaviour**: the fixture engine is 640 x 480, so its stroke now starts at 3 px
  rather than 6, and `penValue` had to start asking `paramsOf` *with* the picture, since asking
  without one answers from the declared constant and no longer matches what the screen shows;
- `jvmTest/unit/MarkupTextFrameTest.kt` - the corner-drag clamp reads back at the new ceiling
  (512 -> 1000, and the wrap that scales with it);
- `jvmTest/unit/IdleToolOverlayTest.kt` - a fixture call site, one argument;
- every edited file is inside 150 lines (`wc -l`), the anti-FQN scan is clean, and
  `./gradlew.bat :shared:build` is green.

## Done when

- placing a caption or a stroke on a small and on a large picture is **one action on both**: the
  default that arrives needs no correction to be usable - **built**, and asserted through the
  real editor by `MarkupDefaultSizeUiTest`. Still wants a look by hand, on the two folders the
  defect was reported from;
- the slider's range is usable on both, including at the top end where the corner drag clamps -
  **done**: one constant `1..1000` for both rows, and a stepper that follows the derived default
  so a 2 px stroke is still nudgeable;
- a recipe written before this ticket renders unchanged - **done**: nothing stored moved,
  `MarkupShape.width` is still absolute source pixels;
- the answer to "relative to what" is written down with its rejected alternatives - **done**,
  above: the shorter edge of the source, with `drawnSize` and the canvas rejected in writing;
- no file touched exceeds 150 lines (`wc -l`) - **done**, `MarkupTool.kt` is exactly 150;
- `./gradlew.bat :shared:build` green - **done**.

**Confirmed by hand 2026-08-31**, on the two folders the defect was reported from. That
confirmation is the one that counts here: the first attempt was green on every automated check
and did nothing at all in the application, which is why `MarkupDefaultSizeUiTest` now drives the
real editor rather than the helper that was changed.
