# ME-122 — Markup text — the fifth shape

| | |
|---|---|
| **Order** | 46 — immediately after ME-114, closing the Annotate domain's model |
| **Depends on** | ME-113 (the shape hierarchy, the draw routine, the fold), ME-114 (the pen and its restyle rule) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Add `MarkupShape.Text` — a string, a size and a colour in source coordinates — drawn,
measured, hit-tested and restyled by the same machinery the other four shapes use.

**Why now.** Markup ships four of its five shapes. Text was split out on 2026-08-18 because it is the
only one with focus, a caret and an IME (§9), and this is the half of it that has none of those: the
model and the raster. It stands to ME-145 as ME-113 stands to ME-114 — the same seam, for the same
reason. A shape that can be built, folded, drawn and saved before anything can type into it.

## The three things that are not obvious

**1. `width` is the type size.** `MarkupShape` requires `color` and `width` of every variant. For a
text shape `width` **is** the font size in source pixels, and the doc comment says so — the
alternative, a fifth field named `size` beside a `width` nothing reads, is a value that exists only to
be ignored by `styled()`, by `restyleSelected` and by every `when` in `MarkupGeometry`.

What that costs is a second control, because the two numbers have nothing like the same range: a
stroke is 1–64 px and type on a 6000 px photograph starts around 64. So the pen gains
`MarkupTextSizeParam` beside `MarkupWidthParam`, and `restyleSelected` routes by the **selected
shape's class**: `Width` restyles a stroke, `Text size` restyles a text shape, and each is a no-op
against the other. Both rows stay visible for every kind, the way Crop's `Portrait` switch stays
visible under a free aspect.

**2. The measurer cannot come from a composition.** `drawMarkup` is a plain `DrawScope` extension
called from three places — the canvas, a preview-choice thumbnail and `renderAtNativeScale` on the
save thread — and only one of them is composing. So the `TextMeasurer` is a file-level `by lazy`
built from `createFontFamilyResolver()` with **`Density(1f)`**, which makes `sp` and `px` the same
number and therefore makes `fontSize = shape.width.sp` exactly `shape.width` source pixels. Measure
first, then draw the `TextLayoutResult`: the `DrawScope.drawText(measurer, …)` overload re-measures
against the *draw scope's* density, so on a HiDPI screen one document would rasterise at twice the
size on the canvas and at one on save.

**3. A blank string is not a shape.** `drawMarkup` draws nothing for it and `hitTest` answers false,
so the invisible-empty-shape failure the backlog names cannot reach the picture even if ME-145 later
lets one into the list.

## Steps

1. `tools/markup/MarkupShape.kt` (edit): `@SerialName("text") data class Text(override val id,
   override val color, override val width, val at: MarkupPoint, val text: String) : MarkupShape`, with
   the doc comment saying `width` is the size in source pixels. Add the branch to `styled()`. Add
   `Text` to `MarkupKind` — **the fifth constant**, which takes the Shape row past
   `ChoiceParamRow`'s `SEGMENTED_LIMIT` of four and turns it into a chip row. That is the row
   renderer working as designed; no icon is needed and none exists.
2. `tools/markup/MarkupText.kt` (new): the lazy `TextMeasurer` above, `textLayoutOf(shape: Text):
   TextLayoutResult?` (null for a blank string), `textBounds(shape: Text): Rect` in source pixels with
   `at` as the top-left, and `DrawScope.drawMarkupText(shape, color)` drawing the measured layout.
   Its own file because `MarkupDraw` is at 117 lines and this is the only part of markup that owns a
   Compose text dependency.
3. `tools/markup/MarkupDraw.kt` (edit): `is Text -> drawMarkupText(shape, color)` in the one `when`.
   Nothing else in that file changes — the routine still draws in source pixels and applies no
   transform of its own, which is what keeps "rasterised at source resolution" true for type too.
4. `tools/markup/MarkupGeometry.kt` (edit), four branches:
   - `hitTest` — inside `textBounds`, **filled rather than outlined**: type is a solid mark and the
     white space between its letters belongs to it, unlike the hollow inside of a box;
   - `handles()` — `emptyList()`, as `Freehand` does, so `MarkupCanvas` draws the dashed selection box
     it already draws for a stroke with no box to pull on;
   - `movedBy` — `copy(at = at.shifted(dx, dy))`;
   - `withHandle` — `this`, unchanged.
5. `tools/markup/MarkupGesture.kt` (edit): `MarkupKind.Text -> Text(id, color, width, at, text = "")`
   in `newMarkupShape`, and `is Text -> this` in `extendedTo` — a text shape is placed, not dragged
   out. The `width` argument is whatever the caller passes; ME-145 is what passes the text size for
   this kind.
6. `tools/markup/MarkupPen.kt` (edit): `MarkupTextSizeParam = FloatParam("textSize", "Text size",
   12f..512f, default = 64f, unit = "px")`, and `restyleSelected` gains the routing — it already
   returns null for any spec that is not one of the two it handles, so the change is: find the
   selected shape, apply `MarkupTextSizeParam` only to a `Text` and `MarkupWidthParam` only to the
   other four.
7. `tools/markup/MarkupTool.kt` (edit): `val TextSize = MarkupTextSizeParam`, added to `params` after
   `Width` and before `Shapes`; the manifest's `summary` becomes "Arrows, boxes, freehand and text".
8. `tools/markup/MarkupState.kt` (edit): `EditDocument.markupTextSize(): Float` beside
   `markupWidth()` — the reader ME-145 needs, added here because this is the ticket that declares the
   parameter.

**Not in this ticket, and deliberately:** creating a text shape on the canvas, the inline field, the
caret, and the keyboard gate that stops `Backspace` deleting the shape being typed into. That is the
new backlog block **ME-145**, and until it lands a text shape can be built, folded, drawn and saved
but not typed. ME-113 shipped in exactly that state, for the same reason.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/markup/MarkupText.kt` (new) ·
`.../tools/markup/MarkupShape.kt` (edit) · `.../tools/markup/MarkupDraw.kt` (edit) ·
`.../tools/markup/MarkupGeometry.kt` (edit) · `.../tools/markup/MarkupGesture.kt` (edit) ·
`.../tools/markup/MarkupPen.kt` (edit) · `.../tools/markup/MarkupTool.kt` (edit) ·
`.../tools/markup/MarkupState.kt` (edit)

## Done when

- `jvmTest/unit/MarkupShapeTest.kt` (edit): a `Text` round-trips through `editorJson` under the
  `"text"` discriminator and comes back equal; `styled(color, width)` changes both and leaves `at` and
  `text` alone.
- `jvmTest/unit/MarkupTextTest.kt` (new): `textBounds` grows with `width` and with the string's
  length; a blank string measures to `Rect.Zero` and `hitTest` answers false anywhere; a point inside
  the bounds hits and one outside by more than the tolerance does not; `movedBy` translates the bounds
  by exactly the delta.
- `jvmTest/unit/MarkupGeometryTest.kt` (edit): `handles()` is empty for a `Text` and `withHandle`
  returns it unchanged.
- `jvmTest/unit/MarkupToolTest.kt` (edit): `params` holds `TextSize`; `stage()` is non-null for a
  document whose only shape is a text shape, and null when that shape's string is blank and it is the
  only one.
- `jvmTest/unit/MarkupRestyleTest.kt` (edit): with a `Text` selected, writing `TextSize` changes its
  `width` and writing `Width` changes nothing; with an `Arrow` selected, the reverse.
- `jvmTest/integration/MarkupTextRenderTest.kt` (new): a document holding one text shape rendered at
  native scale puts ink inside `textBounds` and none more than a pixel outside it — the assertion that
  the measurer's `Density(1f)` really does make the size a source-pixel count. **Re-introduce the bug
  to check the test bites:** with the `DrawScope.drawText(measurer, …)` overload in place of the
  measured layout, it must fail at a density other than 1.
- By hand: **nothing yet** — there is no way to create one. The by-hand pass belongs to ME-145.
- `./gradlew.bat :shared:build` green.
