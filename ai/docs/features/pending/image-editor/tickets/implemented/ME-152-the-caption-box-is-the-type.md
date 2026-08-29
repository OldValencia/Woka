# ME-152 — The caption's box is the type, not the font's line box

| | |
|---|---|
| **Order** | 54 — after ME-148, whose frame it corrects |
| **Depends on** | ME-146 (the plate), ME-148 (the frame and the handles) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make one vertical extent serve the plate, the selection frame, the handles and the caret,
measured from the type rather than from the font's full line box.

**Why now.** Reported by hand 2026-08-29 with a screenshot: "the borders, the background, the text and
the caret are all at different heights". Measured the same day, and the numbers say it is one cause
with four symptoms.

## What was measured

A caption at 64 px, string `sdasdasdfasd`, probed through `textLayoutOf` and by rasterising:

| | |
|---|---|
| layout | `378 × 85`, baseline `68.94`, `lineTop −1.0`, `lineBottom 85.0` |
| ink | rows `21..69` of those 85 — **48 px of glyphs in an 85 px box** |
| air | **21 px above, 16 px below** |
| `textBounds` with a plate | inflated by `width × 0.2` → **110.6 px tall for 48 px of text** |

Rendering the plate, `textBounds` and `frameHandles()` into one image showed all three **coinciding
exactly** — so the model is self-consistent and the box is simply the wrong box. `85 / 64 = 1.33 em` is
the font's line box: the ascent carries the font's internal leading, and the descent is reserved
whether or not anything descends into it.

That single fact is all four symptoms:

- the plate is more than twice the height of the words;
- the text sits `2.5 px` below the box's centre, because the air is `21` above and `16` below — "текст
  чуть ниже в подложке";
- the frame inherits the same box and so looks equally loose;
- the caret spans the line box, because that is what a caret is — so it runs 16 px below the last
  glyph, which reads as "the caret went right to the bottom".

**What was not reproduced:** on the screenshot the dashed frame also looks *shifted down* from the
plate, and in the model they are one rectangle. Either the screenshot is being read a few pixels
wrong, or something in the canvas mapping shifts one of them. **If a mismatch survives this ticket it
is instrumented in the running app, not guessed at** — and it would be its own ticket.

## The decision

**One visual box, shared by all four** (chosen by the author 2026-08-29). The height comes from the
type — the ascender above the baseline and the descender below it — not from the line box, and it does
**not** depend on which letters were typed: a plate that shrank when a word had no descender would
jump as the caption is edited.

## Steps

1. **The `lineHeight` route was measured and is dead.** A probe over the shared measurer at 64 px
   returned the identical `85 px` for the style as shipped, for `lineHeight = 1.0 em`, for
   `0.96 em`, and for `LineHeightStyle(Center, Trim.Both)` — on desktop both properties are ignored
   outright. So the trimming is arithmetic, which is the fallback this ticket named.

2. **`MarkupTextMetrics.kt` (new)** — `markupTextStyle` and the band move here out of
   `MarkupTextLayout.kt`, which the band took to 164 lines. The seam is the one already there:
   measuring the string on that side, the style it is measured in and the box it occupies on this.

   `captionBand(shape)` is the caption's visual extent relative to `at.y`:
   `firstBaseline − width × 0.75` to `lastBaseline + width × 0.25`. **Both constants measured**, by
   rasterising Latin and Cyrillic at 48, 64 and 128 px: ascenders reach `0.749 em` (Cyrillic capitals
   `0.70`), descenders `0.235 em`, and the line box is `1.328 em` at every size. Rounded to a `1.0 em`
   box. Constants rather than the typed string's own ink, so a plate does not jump the moment a `g` is
   typed.

3. **`MarkupText.kt` (edit)** — `textBounds` takes its vertical extent from the band instead of
   `layout.size.height`. Everything downstream follows with no change of its own: the plate, the
   selection frame, the four handles and `hitTest` all read `textBounds` already.

4. **The caret cannot follow, and this is the record of why.** Measured 2026-08-29: a caret open on a
   64 px caption gives a field of **136 dp against 77 dp of glyphs** — the font's line box, which no
   property will shorten. Clipping the field to the band was built and **reverted the same hour**: it
   takes the field out of the semantics tree, and eight UI tests across four files could no longer
   find `markup-text-field` at all. What is left is a caret that stands `0.33 em` above the band's top
   and stops `0.25 em` short of its bottom. With the plate's padding that is about **8 source pixels
   of overshoot at size 64** — visible, and much less than the 37 px of air this ticket removed.

## Files

`tools/markup/MarkupTextMetrics.kt` (new), `tools/markup/MarkupTextLayout.kt` (edit),
`tools/markup/MarkupText.kt` (edit), all under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`.
`MarkupTextField.kt` is **untouched**, per step 4.

## Verification

- `jvmTest/unit/MarkupTextTest.kt` — a caption's measured height is **under `1.05 × fontSize`** and its
  ink starts within `0.1 × fontSize` of the box top; the existing assertions on bounds growing with the
  size and with the string still hold;
- `jvmTest/unit/MarkupTextTest.kt` — a string with a descender (`jpqy`) and one without (`Woka`)
  measure the **same** height, which is what stops the plate jumping as a caption is typed;
- `jvmTest/integration/MarkupTextRenderTest.kt` — the existing "ink inside the measured box, none
  outside" assertion is the regression that matters and must stay green with the tighter box;
- `jvmTest/unit/MarkupTextFrameTest.kt` — the corner-drag ratios are unchanged, because they are
  ratios: a tighter box scales exactly as the loose one did.

## Done when

- a caption's plate hugs the words, with the same gap above and below;
- the selection frame, the four handles and the plate are one rectangle on screen;
- a caption with descenders and one without have plates of the same height;
- ~~the caret is the height of the caption's own line~~ — **not delivered**, and step 4 is why. It
  remains the font's line box and stands about 8 px above the plate at size 64;
- `./gradlew.bat :shared:build` green.

## What changed as this was built

**`at` stopped being the box's top-left, and two tests said so.** The caption's `at` is the layout's
origin — the top of the font's line — and the box now starts at the ascender below it. Both failures
were assertions encoding the old coincidence, not defects:

- `MarkupTextTest` asserted `bounds.top == at.y`; it now asserts the top is below `at.y` by less than
  `0.4 em`, which is the leading that used to be air;
- `MarkupTextFrameTest` asserted that dragging the bottom-right corner leaves `at` where it was. The
  anchor is the **box's** far corner, so `at` moves and the corner is pinned — which is what the user
  grabbed. It now asserts on `textBounds.topLeft`.

**And `MarkupTextFrameUiTest` had to start measuring the canvas scale.** It reached the top-left handle
by assuming it sat on the click that placed the caption, which was true only while `at` was the box's
corner. The scale now comes from the open caret's own field — laid out at `wrap` source pixels wide, so
its measured width over `wrap` is the number — and the handle from `captionBand` at that scale.
