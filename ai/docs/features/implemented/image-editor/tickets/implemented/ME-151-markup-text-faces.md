# ME-151 — Markup text: the four faces

| | |
|---|---|
| **Order** | 51 — immediately after ME-147, which is the row this fills |
| **Depends on** | ME-147 (`ToggleSetParam` and `ParamChipRow`), ME-146 (the plate and the wrap), ME-122 (the shape and its size row) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Give `MarkupShape.Text` a set of faces — bold, italic, underline, strikethrough — reaching
`TextStyle` in the one place a caption is measured, and a chip row that sets them.

**Why now.** Asked for 2026-08-26 as part of ME-146 and left behind twice: once when that ticket was
written, and once more when the schema turned out to be the expensive half (ME-147). That half is now
built and this is what it was built for. A caption today has one face and a size, which is less than a
screenshot annotation needs before it needs anything else.

## The decision this settles

**One `Set<TextFace>`, not four booleans.** The backlog block said four; the row that lights them is a
`ToggleSetParam<TextFace>` whose *value is a set*, so four fields would be mapped to and from one set at
every boundary — the shape, the pen, the restyle and the factory. One field also means one default, one
wire key and one `copy`, and `Set` is what `TextStyle` wants anyway once it is folded into a weight, a
style and a decoration.

**Which makes `TextPen` unnecessary, for now.** The backlog put a `TextPen` value first, because eleven
parameters on `newMarkupShape` is not a factory. With the faces as one field it takes **eight**, and
four of its five branches ignore four of them — no better in kind, much smaller in degree. Introducing a
parameter object here would touch four more files for no behaviour, so it is **not taken**: this is the
record of that, and the next field added to a caption is where it stops being deferrable.

## Steps

1. **`MarkupStyle.kt` (edit)** — beside `MarkupKind`, which is the same kind of thing:

   ```kotlin
   enum class TextFace { Bold, Italic, Underline, Strikethrough }

   internal fun MarkupShape.faced(faces: Set<TextFace>): MarkupShape =
       if (this is MarkupShape.Text) copy(faces = faces) else this
   ```

   `faced` is the third twin of `filled` and `cornered` and exists for the reason those do — `copy` is
   per class on an immutable hierarchy, and a face means nothing to an arrow. The argument for one set
   over four booleans lives here, in full, so the shape's own field can stay short.

2. **`MarkupShape.kt` (edit)** — `Text` gains `val faces: Set<TextFace> = emptySet()`, documented in
   **five lines or fewer**: empty is the wire contract exactly as `wrap = 0f` and `fill = 0L` are, so a
   document written by ME-122, ME-145 or ME-146 decodes to no faces and goes on meaning what it meant.
   The file is at **143 lines** and this is its ceiling — measure with `wc -l` before and after, and if
   it lands over 150 the split is `Freehand`'s and `Text`'s KDoc, not a shorter field.

3. **`MarkupTextLayout.kt` (edit)** — `textLayoutOf` builds its `TextStyle` from the set:
   `fontWeight = FontWeight.Bold` when `Bold in faces`, `fontStyle = FontStyle.Italic` when `Italic`,
   and `textDecoration = TextDecoration.combine(...)` of `Underline` and `LineThrough` — `combine`
   rather than a `when`, because both may be on at once and that is the whole point of the set. The
   `Density(1f)` measurer is untouched, so a face changes the glyphs and not the unit: `width` is still
   a count of source pixels and `wrap` still a source width.

4. **Four glyphs.** `composeResources/drawable/` holds ninety SF Symbols exports and none of this
   family but `textformat.svg`. Add `bold.svg`, `italic.svg`, `underline.svg`, `strikethrough.svg`, and
   in **`core/ui/icons/Icons.kt` (edit)** the four aliases beside the five shape ones —
   `TextBold`, `TextItalic`, `TextUnderline`, `TextStrikethrough`.

   **The exports are the author's**, like every other icon in that folder: they are Apple CoreSVG
   output, and a hand-drawn path carries a different stroke weight — which is exactly the defect the
   one-scale-per-row fix chased on 2026-08-27. **The ticket is green without them**: `ParamChipRow`
   draws a chip with no glyph as its word (ME-147 step 2), so until the four land the row reads
   `Bold` / `Italic` / `Underline` / `Strikethrough` and works. Wiring `iconOf` is one line each when
   they do. They landed the same day, and see below for what they then needed.

5. **`MarkupPen.kt` (edit)** —

   ```kotlin
   internal val MarkupFacesParam = ToggleSetParam(
       key = "faces",
       label = "Style",
       options = TextFace.entries.toList(),
       default = emptySet(),
       iconOf = { face -> when (face) { … } }
   )
   ```

   Labelled `Style` rather than `Faces`: the panel already says `Shape`, `Color`, `Fill`, `Corner` and
   `Text size`, and none of them is a term of art.

6. **`MarkupRows.kt` (new)** — move `MarkupTool.rows()`'s body and its long doc comment out of
   `MarkupTool.kt`, as `internal fun markupRows(params: ParamSet): List<ParamSpec<*>>`. That file is at
   **146 lines** and step 7 adds to it; this is the size mandate paid at the seam that is already
   there — what the tool *is* on one side, which of its rows the pen currently shows on the other. Then
   add `Faces` to the `!typed` hidden set beside `TextSize`, `Fill` and `Corner`: a face does nothing to
   an arrow, which is the rule that file already enforces twice.

7. **`MarkupTool.kt` (edit)** — `val Faces = MarkupFacesParam`; `params` becomes
   `listOf(Kind, Color, Fill, Corner, Width, TextSize, Faces, Shapes)`, the faces after the size because
   they modify it; `rows()` becomes `= markupRows(params)`.

8. **`MarkupRestyle.kt` (edit)** — a `MarkupFacesParam` branch routing through `faced()` and applying to
   a `Text` alone, returning null otherwise — the shape `MarkupFillParam` and `MarkupCornerParam`
   already have, and for the same reason: choosing bold with an arrow selected must cost no history
   entry at all.

9. **`MarkupState.kt` (edit)** — `fun EditDocument.markupFaces(): Set<TextFace>`, the twin of
   `markupFill()`.

10. **The mint path**, one argument each: **`MarkupGesture.kt` (edit)** — `newMarkupShape` takes
    `faces: Set<TextFace>` and its `Text` branch carries it; **`MarkupEdits.kt` (edit)** — `draw` passes
    `document.markupFaces()`; **`MarkupTextEdits.kt` (edit)** — `textEditAt`'s freshly minted
    `MarkupShape.Text` takes the same. **The reopen branch stays untouched**, as ME-146 left it: a
    caption already in the document keeps what it was styled with.

11. **`MarkupTextField.kt` (edit)** — the caret's `TextStyle` gains the same three properties from
    `edit.shape.faces`. The field draws no glyphs, but it measures them: a bold advance is wider than a
    regular one, so without this the caret walks off the string it is sitting on — the same class of
    defect ME-146 fixed when `singleLine` made the caret leave a wrapped line. Extract the mapping used
    in step 3 to `internal fun markupTextStyle(shape: MarkupShape.Text, color: Color): TextStyle` in
    `MarkupTextLayout.kt` and call it from both, or the two spellings drift on the next face added.

## Files

`tools/markup/MarkupRows.kt` (new), `tools/markup/MarkupStyle.kt` (edit),
`tools/markup/MarkupShape.kt` (edit), `tools/markup/MarkupTextLayout.kt` (edit),
`tools/markup/MarkupPen.kt` (edit), `tools/markup/MarkupTool.kt` (edit),
`tools/markup/MarkupRestyle.kt` (edit), `tools/markup/MarkupState.kt` (edit),
`tools/markup/MarkupGesture.kt` (edit), `tools/markup/MarkupEdits.kt` (edit),
`tools/markup/MarkupTextEdits.kt` (edit), `tools/markup/MarkupTextField.kt` (edit) — all under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/` — plus
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt` (edit) and four SVGs under
`shared/src/commonMain/composeResources/drawable/`.

**Thirteen files against §4's ~8, knowingly.** Four of them (`MarkupState`, `MarkupGesture`,
`MarkupEdits`, `MarkupTextEdits`) are one argument each — the pen's set reaching the factory — and
`MarkupRows.kt` is a move. The split that would honour the count puts the shape and the drawing in one
half and the control in the other, which leaves a half with nothing on screen to exercise by hand; that
is the worse trade, and the reason it was not taken is recorded here rather than discovered later.

## Verification

- `jvmTest/unit/MarkupShapeTest.kt` — a `Text` encoded without `faces` decodes to the empty set,
  asserted against a **literal JSON string** rather than a round trip, since a round trip of the new
  class cannot show what an old document does; a set of two round-trips;
- `jvmTest/unit/MarkupTextTest.kt` — the same string at the same size measures **wider** bold than
  regular, and identical with `Underline` alone, which is what proves the decoration reaches the layout
  without changing the advance;
- `jvmTest/unit/MarkupRestyleTest.kt` — the style row rewrites the selected caption's faces, leaves its
  `color`, `fill` and `width` alone, and returns null with an arrow selected;
- `jvmTest/integration/MarkupTextRenderTest.kt` — rendered at source resolution, a caption with
  `Strikethrough` has ink on the row through the middle of its glyph box where the same caption without
  it has none;
- `jvmTest/ui/MarkupRowVisibilityUiTest.kt` — `param-faces` is present with the text pen and absent with
  the arrow pen;
- `jvmTest/ui/MarkupTextUiTest.kt` — choosing `Bold` then typing gives a bold caption (the mint path,
  step 10), and choosing `Italic` with that caption selected adds it without dropping `Bold` (the
  restyle path, step 8) in **one** further undo step.

## What the first pass by hand changed (2026-08-29)

One thing came back, and it was in the assets rather than in the Kotlin.

**Three of the four glyphs rendered as dots.** Measured rather than guessed, with a throwaway probe
over `loadSvgPainter` at four densities (written, read, deleted — `grep TEMPORARY` is clean):

| glyph | `intrinsicSize` at density 1.5 | drawn in a 16 dp chip |
|---|---|---|
| `bold` | 22.6 × 27.1 | 2.4 dp |
| `italic` | 19.9 × 27.1 | 2.1 dp |
| `underline` | 22.2 × 39.3 | 2.4 dp |
| `strikethrough` | **150 × 150** | 16 dp |

**An SVG carrying `width`/`height` reports those numbers; one carrying only a `viewBox` reports
`100 × 100`** — the spec's 100 % default, resolved against a 100 px viewport. All ninety icons already
in `composeResources/drawable` omit the attributes; three of these four carried them. `ParamChipRow`
takes the widest intrinsic in the row as its unit, so the unit came from the one file built like the
others and the three built differently were divided by 150. Fixed by stripping `width`/`height`, which
is what makes an export match the family.

**And a consequence worth writing down.** Since *every* icon in the app measures `100 × 100`, the
per-glyph scaling inside `ParamChipRow` never fires: the unit is always 100 and every chip is asked
for 16 × 16. The row looks even because Skia's default `xMidYMid meet` preserves each viewBox's aspect
inside that square, not because of the arithmetic. The code is inert rather than wrong, and was left
alone — simplifying it is a separate change to a file this ticket only inherited.

## Done when

- a caption can be bold, italic, underlined and struck through, in any combination, on the canvas and in
  the saved file;
- the four are set by one chip row shown for the text pen alone;
- choosing a face with a caption selected restyles that caption and sets the pen, in one undo step —
  the rule every other markup row follows;
- the caret sits on the glyphs it is editing at every combination of faces;
- a document written before this ticket opens with its captions unchanged;
- no file in `tools/markup/` exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
