# ME-142 — Two more adjustments the matrix can express

| | |
|---|---|
| **Order** | 73 — after ME-161, which is what makes room for them |
| **Depends on** | ME-111 (the tool and its matrices), ME-161 (the panel) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Add Black Point and a weighted Saturation to Adjust — two more `FloatParam`s and two more
matrices, no engine change and no new stage kind.

**Why now.** Adjust ships six of the fifteen Photos offers, and these are the ones that cost a slider
each. Everything else in the block needs a fourth fold primitive, which is ME-162 and a different
conversation.

## The block said three, and it was wrong about two of them

`backlog.md` listed Vibrance, Black Point and Brilliance as "still one matrix, and therefore a slider
each and nothing more". A `ColorMatrix` is a 4×5 **affine** map on RGBA, and that claim survives only
one of the three:

- **Black Point** — `out = (in − b) / (1 − b)` is a scale and an offset. Affine. It ships here.
- **Vibrance**, as Photos means it, raises saturation *more* for pixels that are already less
  saturated. The multiplier is then a function of the input's own saturation, which makes the map
  quadratic in its inputs and not affine at any weighting. **It cannot ship as written.** What the
  block's own words describe — "saturation weighted away from skin tones" — *is* affine: a saturation
  matrix whose channel weights spare the reds. That ships here, under that name, with the difference
  recorded in the tool rather than left for someone to discover.
- **Brilliance** is region-specific by definition — brighten the dark parts, pull the highlights in,
  per area of the picture. Not affine, not even global. **It moves to ME-162.**

Recording this is most of the ticket's value: the split line in ME-142's block was drawn in the wrong
place, and drawing it here stops ME-162 being sized against a list that is two items short.

## Steps

1. **`tools/adjust/AdjustMatrix.kt` (edit, 123 lines)** — two functions in the file's existing shape,
   each returning `ColorMatrix()` at zero so a untouched panel still composes nothing:

   - `blackPointMatrix(amount)` — a scale of `1 / (1 − b)` on all three channels with an offset of
     `−b · MID / (1 − b)`, `b` mapped from the slider into a range that cannot reach 1 and divide by
     zero. **Offsets are 0..255 here, not 0..1** — the constant `MID` and the comment above it exist
     because writing this against 0.5 looks right in code and is wrong on screen.
   - `vibranceMatrix(amount)` — the saturation matrix with luminance weights biased so reds move least.
     Its KDoc says plainly that this is a weighted linear saturation and **not** per-pixel vibrance,
     names why (affine), and points at ME-162.

   The file is at 123 lines; two functions with the KDoc this file carries will pass 150, so this step
   is also a split — the new pair goes to `AdjustMatrixTone.kt` and `adjustmentMatrix` keeps composing
   them.

2. **`tools/adjust/AdjustTool.kt` (edit, 72 lines)** — two `FloatParam`s on the shared `SPAN`, added to
   `params` in the order they read on the panel: Black Point beside Contrast, Vibrance beside
   Saturation. Its KDoc's "**Six, not eight**" paragraph is rewritten — that sentence names Highlights
   and Shadows as the reason the count stops, and after this it stops for a different reason at a
   different number.

3. **`AdjustMatrix.kt` / `AdjustMatrixTone.kt` (edit)** — `adjustmentMatrix` multiplies the two new
   matrices in. **Order matters and is not free**: Black Point is a level operation and belongs before
   the colour ones, exactly where exposure sits, or a saturation applied first is re-scaled by it.

## Files

`tools/adjust/AdjustMatrix.kt` (edit), `tools/adjust/AdjustMatrixTone.kt` (new, step 1's split),
`tools/adjust/AdjustTool.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — a §9 row and the status table),
`tickets/backlog.md` (edit — ME-162 gains Brilliance and loses the "three matrix ones" claim).

## Verification

- `jvmTest/unit/AdjustMatrixTest.kt` (edit) — the tier this belongs in, no window involved:
  - each new matrix is the identity at 0, which is what keeps an opened-and-untouched panel free,
  - black point at its maximum maps the darkest input to 0 and leaves white at white,
  - vibrance moves a desaturated blue further than a desaturated red at the same setting — the
    assertion that the weighting is actually weighted, and the one that would catch it being pasted
    from `saturationMatrix`,
  - both are monotonic across the span: a slider that reverses direction anywhere is the defect a
    range-mapping bug produces;
- `jvmTest/unit/AdjustOrderTest.kt` (new) — black point composes **before** saturation, asserted on the
  product rather than on the call order, because the call order is what a later edit changes silently;
- `jvmTest/ui/AdjustPanelUiTest.kt` (edit, from ME-161) — the bar draws eight named buttons and still
  wraps rather than scrolls;
- `jvmTest/integration/AdjustReadersTest.kt` — **unchanged and still green**, which is what says a
  reader folding the recipe outside the editor picks the two new adjustments up for free. If it needs
  editing, something was added outside the matrix and the ticket is wrong.

## Done when

- Adjust offers eight adjustments and produces one `RasterStage.Pixels` for all of them;
- an untouched panel still yields no filter at all (`adjustmentMatrix` returns null);
- `AdjustTool`'s KDoc no longer says "six, not eight", and `AdjustMatrixTone`'s says why vibrance here
  is not Photos' vibrance;
- ME-162's block names Brilliance;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
