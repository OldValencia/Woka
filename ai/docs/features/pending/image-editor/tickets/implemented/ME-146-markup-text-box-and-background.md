# ME-146 — Markup text: a box and a background

| | |
|---|---|
| **Order** | 47 — immediately after ME-145, on the shape it left behind |
| **Depends on** | ME-145 (the caret and the live preview), ME-122 (the shape, its measurement and its size row) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Give `MarkupShape.Text` a width it wraps against and a coloured plate behind its glyphs,
and draw both in the one routine that already rasterises it.

**Why now.** A caption is one unwrapped line: type a sentence on a 6000 px photograph and it runs off
the right edge of the picture and into nothing — the glyphs past the edge are measured, laid out, and
then clipped away by the surface. That is not a styling gap, it is text leaving the file. The plate
comes with it because both land on the same two functions (`textLayoutOf`, `drawMarkupText`) and on
the same shape, which is the backlog block's own argument for not separating them.

## What this ticket is not

The backlog block ME-146 covered four things. Two of them are promoted here; the other two are
**back in `backlog.md` as ME-147 and ME-148**, because together the four are ~20 files and three
file splits.

- **ME-147 — the four faces.** Bold, italic, underline, strikethrough are four *independent*
  booleans, so `ChoiceParam`'s chip row does not fit: it picks one of a set. Settled 2026-08-27 —
  they become a **multi-select chip row**, which is a ninth `ParamSpec` (`ToggleSetParam`), a row
  renderer, an extraction of the chip out of `ParamRowChoice`, four SF Symbol glyphs and the fixture
  tool's ninth spec. Seven shell files before markup is touched at all.
- **ME-148 — the frame you can grab.** `handles()` returning four corners, `withHandle` applying a
  **ratio** to `width` and `wrap` together, and the frame drawn on the canvas. It assumes this
  ticket's `wrap` field exists and that `textBounds` already comes from the layout rather than from
  one line — which is exactly what it needs and what this ticket delivers.

## The two decisions this settles

**Where a caption's wrap width comes from: the picture, at the moment the caret is placed.**
`wrap = (sourceWidth - at.x).coerceAtLeast(sourceWidth / 4f)`, in source pixels, stored on the shape
and never set by the user. Taken from where the caption *starts*, so it wraps at the picture's right
edge rather than a quarter-picture past it; floored at a quarter of the width so a caret placed near
the right edge is not a one-character column. Moving a caption afterwards does **not** re-wrap it —
the number is the box's width, and ME-148 is where the box becomes visible and draggable.

**Zero is the wire contract, twice over.** `wrap = 0f` means unconstrained-one-line and `fill = 0L`
means no plate, and both are the field's default. A document written by ME-122 or ME-145 carries
neither key, decodes to both defaults through `ParamSpec.decode`'s null-to-default rule, and goes on
meaning precisely what it meant. No migration, no version field.

## Steps

1. **`MarkupStyle.kt` (new)** — move `styled()` and `enum class MarkupKind` out of `MarkupShape.kt`
   verbatim. `MarkupShape.kt` is at 150 lines and the next step adds two fields to it; this is the
   size mandate paid first, at the seam that was already there — the shape on one side, what the pen
   does to it on the other. No behaviour change.
2. **`MarkupShape.kt` (edit)** — `Text` gains `val wrap: Float = 0f` and `val fill: Long = 0L`, both
   documented as above. `styled()` is untouched: `fill` is not the pen's `color` and must not follow
   it.
3. **`MarkupStyle.kt` (edit)** — add `internal fun MarkupShape.filled(fill: Long): MarkupShape`,
   a `when` that `copy`s the plate onto a `Text` and returns every other shape unchanged. Its twin
   is `styled()` and it exists for the same reason: `copy` is per class on an immutable hierarchy.
4. **`MarkupText.kt` (edit)** — three functions, in this order:
   - `textLayoutOf` measures with `constraints = Constraints(maxWidth = shape.wrap.roundToInt())`
     when `shape.wrap > 0f`, and unconstrained otherwise. At `Density(1f)` a layout pixel *is* a
     source pixel, which is what the file's existing measurer trick already buys — the wrap width
     therefore needs no conversion.
   - `textBounds` returns the layout's box, **outset by the plate's padding when `fill` carries a
     non-zero alpha** and not otherwise, so a caption with a plate is grabbed by its plate and one
     without keeps the box ME-122 gave it.
   - `drawMarkupText` draws `drawRoundRect(Color(shape.fill), …)` over `textBounds(shape)` before
     `drawText`, and skips it entirely when the alpha is zero. Padding `shape.width * PLATE_PAD` and
     corner radius `shape.width * PLATE_RADIUS`, both proportional to the type size so a plate keeps
     its proportions at 12 px and at 512.
   - `fun wrapAt(sourceWidth: Int, x: Float): Float` — the one expression above, spelled once, so
     the two mint sites in step 6 cannot disagree.
5. **`MarkupState.kt` (edit)** — `fun EditDocument.markupFill(): Long`, the twin of `markupColor()`.
6. **The two places a caption is minted**, both reading `wrapAt` and `markupFill()`:
   - `MarkupGesture.kt` (edit): `newMarkupShape` takes `wrap: Float` and `fill: Long` and its `Text`
     branch carries them.
   - `MarkupEdits.kt` (edit): `draw` passes `wrapAt(sourceSize.width, from.x)` and
     `document.markupFill()`.
   - `MarkupTextEdits.kt` (edit): `textEditAt`'s `MarkupShape.Text(...)` gains the same two. The
     *reopen* branch is untouched — a caption already in the document keeps the wrap it was minted
     with, or ME-148's ratio would be overwritten on every second click.
7. **`MarkupRestyle.kt` (new)** — move `restyleSelected` and `sizeParamFor` out of `MarkupPen.kt`
   (136 lines, and step 8 adds to it). Then extend `restyleSelected`: `MarkupFillParam` routes
   through `filled()` rather than `styled()`, and applies only to a `Text` — choosing a plate colour
   with an arrow selected must leave the arrow alone.
8. **`MarkupPen.kt` (edit)** —
   `internal val MarkupFillParam = ColorParam("fill", "Fill", swatches = listOf(0L) + MARKUP_SWATCHES, default = 0L)`.
   `0L` is fully transparent black and reads as **none**, which is the common case and therefore the
   first swatch and the default.
9. **`MarkupTool.kt` (edit)** — `val Fill = MarkupFillParam`; `params` becomes
   `listOf(Kind, Color, Fill, Width, TextSize, Shapes)`; `rows()` hides `Fill` unless the pen is
   `MarkupKind.Text`, extending the rule ME-122 wrote there — a row that changes nothing when used
   reads as broken, and a plate does nothing to an arrow.
10. **`ColorSwatch.kt` (edit)** — `internal fun swatchFill(argb: Long): Brush`: a value with zero
    alpha returns a `Brush.linearGradient` with hard stops at 0.45/0.55, whose default axis runs
    corner to corner and therefore draws the diagonal "none" slash; anything else returns
    `solidFill`. `solidFill` stays for the picker's readout, which never shows none.
    **`ParamRowColorAction.kt` (edit)** — `ColorParamRow` fills its swatches with `swatchFill`. One
    call site; nothing else about the row changes, which is the point of reusing `ColorParam` here
    rather than inventing a nullable colour spec.
11. **`MarkupTextField.kt`** — first move `MarkupTextEdit` into **`MarkupTextEdit.kt` (new)** (the
    file is at 143 lines), then: `singleLine = false`, and the field constrained to
    `Modifier.width((edit.shape.wrap * scale).toDp())` when `wrap > 0f`. The caret has to break where
    the glyphs under it break, and a single-line field measures the string on one line whatever the
    canvas draws.

## Files

`tools/markup/MarkupStyle.kt` (new), `tools/markup/MarkupRestyle.kt` (new),
`tools/markup/MarkupTextEdit.kt` (new), `tools/markup/MarkupShape.kt` (edit),
`tools/markup/MarkupText.kt` (edit), `tools/markup/MarkupState.kt` (edit),
`tools/markup/MarkupGesture.kt` (edit), `tools/markup/MarkupEdits.kt` (edit),
`tools/markup/MarkupTextEdits.kt` (edit), `tools/markup/MarkupPen.kt` (edit),
`tools/markup/MarkupTool.kt` (edit), `tools/markup/MarkupTextField.kt` (edit),
`shell/ColorSwatch.kt` (edit), `shell/ParamRowColorAction.kt` (edit)

All under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`.

## Verification

- `jvmTest/unit/MarkupTextTest.kt` — a string wider than its `wrap` measures more than one line and
  its bounds are no wider than `wrap`; the same string at `wrap = 0f` measures exactly one line, and
  that line is wider than `wrap` (the ME-122 document, unchanged);
- `jvmTest/unit/MarkupTextTest.kt` — `textBounds` grows by the padding when `fill` is opaque and is
  the glyph box exactly when `fill` is `0L`;
- `jvmTest/unit/MarkupShapeTest.kt` — a `Text` encoded without `wrap` and `fill` decodes to `0f` and
  `0L`, asserted against a literal JSON string rather than a round trip, since a round trip of the
  *new* class cannot show what an *old* document does;
- `jvmTest/unit/MarkupRestyleTest.kt` — the fill row rewrites the selected caption's plate, leaves
  its `color` alone, and returns null with an arrow selected;
- `jvmTest/integration/MarkupTextRenderTest.kt` — rendered at source resolution, a pixel just inside
  the plate's corner radius is the fill colour and one just outside it is the picture;
- `jvmTest/ui/MarkupTextUiTest.kt` — `param-fill` is present with the text pen chosen and absent with
  the arrow pen; clicking `param-fill-swatch-0` leaves the caption with no plate.

## What the first pass by hand changed (2026-08-27)

Exercised on a screenshot at Fit 72 %, and four things came back. Three were built the same
sitting; the fourth was answered and left where it was.

1. **The plate was not centred on its type.** Measured rather than guessed:
   `TextLayoutResult.size.width` reports the **constraint**, not the text — a block wrapped
   at 300 px whose longest line is 266 px still measures 300, so the plate ran 34 px past
   the glyphs and all of it landed on the right. `textBounds` now takes the longest
   `getLineRight`. The vertical gap is *not* a defect: the probe read ascent 103 against
   descent 24 at a 95 px size, so a caption sits lower in its box because that is what the
   font's metrics say, and correcting it would mean trimming leading nobody asked to trim.
2. **`Enter` committed the caption.** It ends a paragraph now, and only a click elsewhere
   commits — which is what ME-145 would have done had a caption had a box to break inside.
   `MarkupTextField` consumes `Esc` alone; `finishCaret` in the test harness is a click.
3. **The corner became a row** — `Corner`, 0–100 %, **a per cent of the type's size** so
   ME-148's ratio scales it for free, clamped to half the shorter side so 100 % is a capsule
   rather than something Skia has to guess at. It is shown only once a fill is chosen: the
   "a row that changes nothing reads as broken" rule, one level below the pen.
4. **Bold / italic / underline / strike are still ME-147**, as split above.

**A note for ME-147.** `newMarkupShape` now takes `wrap`, `fill` and `corner` beside the pen,
and the four faces would make eleven parameters on a factory where four of five branches
ignore most of them. That is the point at which a `TextPen` value replaces them.

## Done when

- a caption longer than the picture is wide wraps at the picture's right edge instead of running off
  it, on the canvas and in the saved file;
- choosing a fill draws a rounded plate behind the glyphs, at the same proportions in the editor and
  at native resolution;
- the first swatch of the `Fill` row is a slashed circle and selecting it removes the plate;
- `Fill` is not shown for the four stroked pens;
- a document written before this ticket opens with its captions unchanged — one line, no plate;
- `Enter` breaks a line and leaves the caret open; a click on the picture commits;
- the `Corner` row appears with a plate and disappears with it, and 0 % squares the corners;
- no file in `tools/markup/` exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
