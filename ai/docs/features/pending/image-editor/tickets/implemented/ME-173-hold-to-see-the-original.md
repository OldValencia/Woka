# ME-173 — Hold to see the original

| | |
|---|---|
| **Order** | ahead of ME-166; asked for 2026-09-02, after ME-162 was confirmed by hand |
| **Depends on** | ME-101, ME-107 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** A button in the editor's top bar that shows the untouched original while it is
held down, and the current state the moment it is released.

**Why now.** Twelve adjustments shipped with ME-162 and the question a photographer asks
after every one of them is "is this actually better than what I started with". Today the
only answer is Revert, which is destructive and permanent, or Undo pressed as many times
as there were edits. Neither is a comparison.

## What it shows

**The whole original — an empty document**, geometry included, which is what Photos does
and what the author chose on 2026-09-02. A crop is an edit like any other, so while the
button is held the frame returns to the source's own dimensions and the picture is drawn
uncropped, unrotated, unadjusted, without markup.

It is **not** "the file as it was opened". ME-134 replays a stored recipe into history, so
a re-opened edit starts with ops already in the document; the original is the picture as
shot, which is the same thing Revert returns to and is `EditDocument()`.

Nothing is written, no op is committed, and history is not touched — release restores the
document, which was never changed.

## Where it goes, and why not the two other places

**A pill in the top bar's centre, beside whatever is already there.** Opened from the
viewer that is the viewer's own control pill, so the button lands next to the file-info
button; opened from the grid it is `CanvasZoomPill`. The editor composes the previous
centre accessory rather than replacing it, so nothing that was there is taken away and
the button is present on both entry paths — `EditorTopBarUiTest` asserts the viewer's
pill survives, and it must go on surviving.

**Not the canvas**, on press-and-hold: a click there already toggles playback for an
animation (`EditorPlayback`), and a press starts a drag whenever Crop or Markup is open.
The rule would need two exceptions, and an exception on the canvas is what gets found by
hand later.

**Not the tool rail** (considered and rejected 2026-09-02). The rail is derived entirely
from the registry — `tools.forEachIndexed`, dividers from a change of `ToolDomain` — and
a peek button is not a tool by §3.4's definition: no inspector, no params, no stage, not
selectable. Making one would be the same category error that keeps Auto adjust out of the
tool list. The rail is also being kept for things that *edit*: **Auto adjust (ME-167)
belongs there**, and it does write params.

## Steps
1. `Icons.kt`: `val PeekOriginal = Res.drawable.paintbrush_slash` — the SVG is in
   `composeResources/drawable/` already.
2. New `EditorPeekButton.kt` in `feature/editor/shell/`: a pill matching `EditorTokens`,
   icon-only, wrapped in `ViewerTooltip` because an icon with no label cannot say what it
   does. Press with `detectTapGestures(onPress = { onPeek(true); tryAwaitRelease(); onPeek(false) })`, so a release **and a cancel** both restore — a drag off the button must
   not leave the editor stuck showing the original.
3. `EditorScreen.kt`: `var peeking by remember { mutableStateOf(false) }`, passed to
   `EditorTopBarSetup` and into the canvas.
4. `EditorToolScopeImpl.canvasDocumentFor` takes `peeking` and answers `EditDocument()`
   when it is set — one call site, no new branch in `EditorScreen`, and the geometry
   follows because `EditorCanvasHost` measures from `engine.drawnSize(document)`.
5. `EditorCanvasOverlay` takes `peeking` and draws nothing while it is set. A crop frame
   or a markup handle left on screen would point at where the pixels *used* to be.
6. `EditorTopBarSetup.kt`: two new parameters through `rememberUpdatedState`, as every
   other callback in that file already goes, and the centre accessory becomes the
   previous one (or `CanvasZoomPill`) plus the peek pill in a `Row`.

## Files
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorPeekButton.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorTopBarSetup.kt` (edit, 135 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorScreen.kt` (edit, 147 lines — at the mandate, budget a split)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorToolScopeImpl.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorCanvasOverlay.kt` (edit, 46 lines)

## Done when
- `jvmTest/unit/`: `canvasDocumentFor(tool, document, peeking = true)` is empty whatever
  the tool and document are, and unchanged when it is false;
- `jvmTest/ui/`: pressing the pill empties what the canvas host is given and releasing
  restores it; the viewer's centre pill is still composed beside it;
- history is untouched — `canUndo` and the document read the same before and after a
  press;
- confirmed by hand, which is the only way to judge whether the frame jumping to the
  uncropped original reads as information or as a glitch;
- `./gradlew.bat :shared:build` green.
