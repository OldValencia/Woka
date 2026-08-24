# ME-114 — Markup interaction

| | |
|---|---|
| **Order** | 45 — immediately after ME-113, which it makes reachable |
| **Depends on** | ME-113 (the model, the hit test and the draw routine), ME-109 (the overlay pattern) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Draw, select, move, resize and delete the four shapes on the canvas — the overlay ME-113
deliberately left out.

**Why now.** ME-113 ships a tool that renders a shape list it has no way to create. This is the other
half, and it is the second `CanvasTool` in the app, so it is also the test of whether the overlay
pattern Crop settled is a pattern or a one-off.

## The one thing that is not obvious

**`Delete` has to reach the tool, and the editor's keyboard is a single mutable slot.**

`GlobalShortcutState.onPreviewKey` holds one handler. `EditorKeyEffect` installs the editor's in
`EditorHost` and restores the previous one on dispose, and `EditorKeys` documents at length why a
second claimant is how the viewer's arrow keys died. So the overlay must **not** register its own —
and it cannot, anyway: the active tool lives inside `EditorScreen`, two composables below the handler.

The bridge is one `@Stable` holder, built in `EditorHost` and passed down, mirroring `ToolSelection`
exactly: a slot the active canvas tool writes while it is open and clears on dispose, and the key
handler reads. It is a slot rather than a lookup because "which tool is open" is `EditorScreen`'s
state and the handler must not learn it.

**Everything else needs no new API.** ME-113 made the tool `Single` with one operation carrying the
list, so adding, moving, resizing and deleting are all `scope.preview(op with a different list)`
followed by one `scope.commit()` — the mechanism Crop already uses. There is no session change in this
ticket, and if one appears the ME-113 decision was wrong.

## Steps

1. `tools/markup/MarkupState.kt` (new), the twin of `CropState`: `EditDocument.markupOp()`,
   `EditDocument.markupShapes(): List<MarkupShape>` (pending wins over committed, because a drag in
   flight is what the user is looking at), and `EditDocument.markupOpWith(shapes)` carrying every other
   parameter through — a drag must not reset the pen the inspector just set.
2. `tools/markup/MarkupCanvas.kt` (new): draws the shape list with `drawMarkup`, then the selected
   shape's `handles()` as filled circles, mapped through `scope.sourceToCanvas`. Handles are drawn at a
   fixed **screen** size, so they do not grow with the picture.
3. `tools/markup/MarkupOverlay.kt` (new): `awaitEachGesture`, following `CropOverlay` line for line on
   the three things it got wrong by hand —
   - the scope is read through `rememberUpdatedState`, or every event of a drag measures against the
     document as it was when the gesture started;
   - the press is left **unconsumed** when it lands on nothing of ours, so the canvas underneath can
     still be panned;
   - `Ctrl` is always pan, even over a shape.

   Then: a press on the selected shape's handle resizes (`withHandle`) — for an arrow that is its
   endpoint, which is the "bend" the backlog asked for; a press on a shape (`hitTest`, topmost first)
   selects and moves it (`movedBy`); a press on empty picture starts a new shape of `Kind` with the
   pen's `Color` and `Width`, extended per event, committed on release. A press on empty picture with
   something selected **deselects and draws nothing** — a stray click must not mint a zero-sized shape.
4. Same file: freehand accumulates points per event rather than replacing an endpoint, and drops
   points closer together than a source pixel — a 6000 px photograph at 60 fps otherwise stores a
   thousand points for one stroke, and every one of them goes into the file.
5. `core/CanvasTool.kt` (edit): `fun onDelete(scope: EditorToolScope): Boolean = false`, defaulted so
   Crop says nothing. Markup returns true when a shape was selected and removes it, previewing the
   shorter list and committing — one undo step, like any other gesture.
6. `shell/EditorToolScopeImpl.kt` (edit): a `@Stable class CanvasKeys { var onDelete: (() -> Boolean)? }`
   beside `ToolSelection`, and `ActiveToolOverlay` registers the active tool's handler in a
   `DisposableEffect(canvasTool, scope)` that clears it on dispose.
7. `shell/EditorHost.kt` (edit) builds the `CanvasKeys`, passes it to `EditorScreen` and to
   `EditorKeyEffect`; `shell/EditorScreen.kt` (edit) hands it to `ActiveToolOverlay`;
   `shell/EditorKeys.kt` (edit) adds `Key.Delete` and `Key.Backspace`, **consumed either way** — the
   rule that file already applies to `Ctrl+Z` with nothing to undo.
8. `tools/markup/MarkupTool.kt` (edit): `EditorTool` becomes `CanvasTool`, `Overlay(scope) =
   MarkupOverlay(scope)`, and `previewsThroughOverlay` stays **false** — the default, and the case its
   doc comment was written for: a stroke has to be visible on the canvas as it is drawn, so the fold
   must keep applying it.

**Nine files, one over the guideline.** If it does not fit one sitting, steps 5–7 are the separable
half: without them a shape is removed with `Ctrl+Z` instead of `Delete`, and nothing else changes.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/markup/MarkupState.kt` (new) ·
`.../tools/markup/MarkupCanvas.kt` (new) · `.../tools/markup/MarkupOverlay.kt` (new) ·
`.../tools/markup/MarkupTool.kt` (edit) · `.../shell/EditorToolScopeImpl.kt` (edit) ·
`.../shell/EditorHost.kt` (edit) · `.../shell/EditorScreen.kt` (edit) ·
`.../shell/EditorKeys.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/CanvasTool.kt` (edit)

## Done when

- `jvmTest/ui/MarkupOverlayUiTest.kt`: a drag across the overlay adds **one** shape and **one** undo
  step, and the shape's endpoints are the drag's, in source coordinates — asserted through a fixture
  engine whose `previewMatrix` is not the identity, because a flipped preview is where Crop's overlay
  landed on the far side of the picture.
- Same file: a second drag over an existing shape **moves** it rather than adding a third, the document
  still holding one markup operation; a drag starting on empty picture with a selection deselects and
  adds nothing.
- `jvmTest/ui/`: `Delete` with a shape selected removes it and records one undo step; `Delete` with
  nothing selected is consumed and changes the document not at all.
- **A regression test that bites.** As in ME-109, verify by re-introducing the bug: with the scope
  captured directly instead of through `rememberUpdatedState`, the move test must fail. A UI test that
  passes with that bug present is the failure mode this project has already hit once.
- By hand: draw an arrow, a box, an ellipse and a scribble; move one, drag a handle, delete one; crop
  the picture and confirm the markup is **clipped, not moved**; press Done, reopen, and find the shapes
  where they were left.
- `./gradlew.bat :shared:build` green.
