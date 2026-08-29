# ME-148 — Markup text: the frame you can grab

| | |
|---|---|
| **Order** | 52 — after ME-151; the last quarter of the ME-146 block |
| **Depends on** | ME-146 (`wrap`, and `textBounds` measured from the layout), ME-114 (handles, and the drag that moves them) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Give a caption four corner handles that **scale the type**, so its box, its font size and its
line breaks move together as one ratio and one undo step.

**Why now.** `handles()` is `emptyList()` for a caption, so a selected one is marked by a dashed box it
cannot be grabbed by — the only selected shape in the tool whose selection does nothing. Resizing type
is currently the `Text size` slider alone, which is the one control on the canvas nobody reaches for.

## The decision this settles

**A corner scales the type — the iOS Photos reading, settled 2026-08-27.** Not the text-editor reading,
where the frame sets the wrap width and the size is untouched, and not both at once.

Two things follow, and they are the whole ticket:

- **A corner drag is a ratio, not a rectangle.** It multiplies `width` — the font size since ME-122 —
  and `wrap` — the box since ME-146 — by the **same** factor, so the block keeps its proportions and the
  line breaks land where they did. `corner` needs nothing: ME-146 made it a per cent of the type's size
  precisely so it would scale for free.
- **The four corners are the only handles.** A side handle would set the wrap width on its own, which is
  the reading that was not chosen.

**And the `Text size` row becomes the same operation.** This is the half of that decision the backlog
implied and did not spell out: today the slider writes `width` absolutely and leaves `wrap` alone, so
moving it reflows a caption while a corner drag does not — two controls over one thing, disagreeing.
Both go through one ratio, so a caption's proportions are invariant under either and `wrap` stays what
ME-146 defined it as: the width the picture gave it, times whatever scaling has since happened.

## Steps

1. **`MarkupTextFrame.kt` (new)** — the arithmetic, pure and in source pixels, because
   `MarkupGeometry.kt` is at **119 lines** and this is ~45 with its reasoning:

   - `internal fun MarkupShape.Text.frameHandles(): List<MarkupPoint>` — the four corners of
     `textBounds(this)`, walked `topLeft, topRight, bottomRight, bottomLeft` so index `i`'s anchor is
     always `(i + 2) % 4`. **Empty for an empty caption**: `textBounds` is `Rect.Zero` there, and four
     handles in the picture's corner for a shape that draws nothing is a target nobody meant to hit —
     the same `isEmpty` guard `hitTest` already makes.
   - `internal fun MarkupShape.Text.scaled(ratio: Float, anchor: Offset): MarkupShape.Text` —
     `width * ratio` **clamped into `MarkupTextSizeParam.range`**, that clamp read back as the ratio
     actually applied, and then `wrap` and `at` moved by *that* number:
     `at' = anchor + r * (at - anchor)`. Reading the clamp back is not tidiness — without it a caption
     driven past 512 px keeps widening its box while its glyphs stop growing.
   - `internal fun MarkupShape.Text.withFrameHandle(index: Int, x: Float, y: Float): MarkupShape` —
     the anchor is the opposite corner; the ratio is the pointer **projected onto the box's diagonal**,
     `((p − a) · (c − a)) / |c − a|²`, floored just above zero. A projection rather than
     `max(|dx| / |dx₀|, |dy| / |dy₀|)` because the box is not square: the two axes give different
     numbers for the same drag, and picking one makes the corner slide off the pointer along the other.

   The anchor stays exactly put by construction — `textBounds` is `at` plus a measured size plus a plate
   pad, and all three scale by `r`, so the opposite corner maps to itself. That is worth an assertion,
   not a comment; see Verification.

2. **`MarkupGeometry.kt` (edit)** — two branches, one line each:
   `is Text -> frameHandles()` in `handles()`, and `is Text -> withFrameHandle(index, x, y)` in
   `withHandle()`. Their doc comments currently say type is resized from the inspector; both are now the
   opposite and say so. `movedBy` and `hitTest` are untouched — a caption is still grabbed by its filled
   bounds and still moved by translating `at`.

3. **`MarkupCanvas.kt` (edit)** — a caption draws **both**: the dashed frame *and* the four dots. Today
   the box is drawn only when there are no handles, which is the freehand case. The condition becomes
   "a `Freehand` or a `Text`" for the box, and the handles are drawn whenever there are any — so an
   arrow keeps its two dots and no box, a box keeps its four and no box, freehand keeps its outline, and
   a caption gains a frame that says what the corners will do. `points(sourceToCanvas)` needs no change:
   it already routes everything but `Freehand` through `handles()`, which is now the bounds.

4. **`MarkupRestyle.kt` (edit)** — the `sizeParam` branch, for a `Text` only, becomes
   `selected.scaled(params[MarkupTextSizeParam] / selected.width, anchor = selected.at)`. The four
   stroked shapes keep `styled(width = …)` exactly as they are — a stroke has no box to keep in
   proportion. The anchor is the caption's own `at`, so a caption dragged larger by the slider grows
   down and to the right from where it sits, rather than about a corner the slider has no way to name.

## Files

`tools/markup/MarkupTextFrame.kt` (new), `tools/markup/MarkupGeometry.kt` (edit),
`tools/markup/MarkupCanvas.kt` (edit), `tools/markup/MarkupRestyle.kt` (edit)

All under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`. **No pointer file changes at all**:
`markupGestureAt` already prefers the selected shape's handles over every shape's body, and
`MarkupEdits.resize` already calls `withHandle` per event and commits on release — which is what makes
one drag one undo step without anything here saying so.

## Verification

- `jvmTest/unit/MarkupTextFrameTest.kt` (new) — dragging the bottom-right corner to twice its distance
  from the top-left doubles `width` **and** `wrap` and leaves `at` where it was; dragging the top-left
  corner instead leaves the **bottom-right of `textBounds`** where it was, asserted on the rect rather
  than on `at`, since `at` must move for that to hold; a ratio driving `width` past 512 leaves `wrap` at
  exactly `512 / oldWidth` times its old value, not at the unclamped one;
- `jvmTest/unit/MarkupTextFrameTest.kt` — a caption whose string is wider than its `wrap` has the same
  `lineCount` before and after a corner drag, which is the proportion claim stated as something a test
  can fail;
- `jvmTest/unit/MarkupGeometryTest.kt` — `handles()` is four for a caption with text and empty for one
  without; `withHandle` with an out-of-range index returns the shape unchanged, the rule that file
  already keeps for every other kind;
- `jvmTest/unit/MarkupRestyleTest.kt` — the `Text size` row scales `wrap` with `width` and leaves `at`
  alone; the `Width` row still leaves an arrow's geometry untouched;
- `jvmTest/ui/MarkupTextFrameUiTest.kt` (new) — with a caption selected, a drag from one of its corners
  produces exactly **one** further undo step and a larger caption; a press on the corner that does not
  move opens no caret, which is what separates a handle from the second click that reopens an edit.

## Done when

- a selected caption shows a dashed frame with four corner dots;
- dragging a corner scales the type and the box together, keeps the opposite corner still, and breaks
  the lines where it broke them before;
- the drag is one undo step;
- the `Text size` row and a corner drag are the same operation reached two ways — neither reflows a
  caption the other would not;
- a caption at the top of its size range stops growing without its box growing on past it;
- no file in `tools/markup/` exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
