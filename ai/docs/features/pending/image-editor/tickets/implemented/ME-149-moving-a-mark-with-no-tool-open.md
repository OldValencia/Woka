# ME-149 — Moving a mark with no tool open

| | |
|---|---|
| **Order** | 53 — after ME-148, whose handles it inherits |
| **Depends on** | ME-114 (the overlay, the selection and the `Delete` slot), ME-109 (`CanvasTool` and the scope) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Let a drawn mark be selected, moved, resized and deleted while **no tool is open at all**, by
mounting the canvas tool that owns it in a select-only mode.

**Why now.** Asked for 2026-08-27. A shape is only touchable while Markup is the open tool, so nudging
an arrow after cropping means going back to Markup, and Markup's panel then covers the bottom of the
picture the arrow is on.

## The reading that was chosen, and the three that were not

**"When no tool is open", not "whatever tool is open."** `EditorScreen.activeToolId` is a `String?` and a
second click on a rail item clears it, so this state exists today and `ActiveToolOverlay` returns
immediately in it — nothing is mounted and nothing claims the pointer. Four things say no to the wider
reading and none of them applies here:

- **`EditorSession` holds one `pending`.** Markup is `Single` and one operation carries the whole shape
  list, so moving a shape *is* a preview of that operation. Drag Crop's frame and nudge an arrow, and
  one preview overwrites the other — a lost edit, not an awkward moment. With nothing open the pending
  is free.
- **`CanvasKeys.onDelete` is a single mutable slot**, handed over by `DisposableEffect` and given back
  on dispose. Two claimants make `Delete` mean whichever registered last, which is composition order
  rather than intent (the defect ME-124 records for PiP). With nothing open the slot is free.
- **Two overlays would contest the pointer.** A Crop handle sits on the frame; an arrow's body may lie
  under it. With nothing open there is no rival.
- **The geometry moves.** Markup lives in source pixels outside the matrix and `sourceToCanvas` is
  rebuilt on every preview, so dragging a shape while Crop reshapes the frame is dragging it inside a
  coordinate system that is itself moving. With nothing open the matrix is still.

## The three things this has to answer

**The pen is off.** `MarkupGesture.Draw` mints a shape today when nothing is selected; here a press on
empty picture may only deselect, and must stay **unconsumed** so the host beneath still pans. A flag
read by the existing recogniser, never a second copy of it.

**Whose overlay is it when nobody is open?** Not "Markup's" — nailing that in is wrong the moment a
second object-carrying `CanvasTool` exists, and ME-143 could well be one. A `CanvasTool` declares
whether it has anything to select in a given document, and the shell mounts **one**: the first such tool
in rail order. One rather than all, because "two overlays contest the pointer" is an objection to the
state, not to the tool that is open — it would come straight back if both were mounted. The rule is
written down in one function so the second tool changes that function and nothing else.

**Resizing is in, restyling is out.** The inspector is not on screen, so `restyleSelected` is out of
reach: no colour, no size row, no plate. Handles are a different question and the answer is yes — they
are the same `MarkupGesture.Resize` path the selection already draws dots for, and suppressing them
would mean drawing four handles that do nothing. So: **select, move, resize, delete.**

## Steps

1. **`core/EditorToolScope.kt` (edit)** — `val selectOnly: Boolean get() = false`. Defaulted in the
   interface, so `FrozenScope` in `MarkupLiveEditTest` and any later fake stay compiling. Documented as
   what it is: the tool is mounted because it owns something on the picture, not because the user opened
   it, and it may therefore only manipulate what is already there.

2. **`core/CanvasTool.kt` (edit)** —

   ```kotlin
   fun selectableIn(document: EditDocument): Boolean = false
   ```

   Defaulted false, which is Crop's answer and every future tool's until it says otherwise. Its doc
   carries the one invariant the shell cannot check: a tool answering true must leave
   `previewsThroughOverlay` false, because with no tool open the canvas is drawn from the whole document
   and a tool whose work was subtracted would show nothing to select.

3. **`shell/EditorToolScopeImpl.kt` (edit)** — `selectOnly` as a constructor parameter, defaulted false,
   overriding the interface's value.

4. **`shell/ActiveToolOverlay.kt` (edit)** — `ActiveToolOverlay` and `rememberToolScope` take
   `selectOnly: Boolean = false` and pass it through; it joins the `remember` key list, since a scope
   built in one mode must not survive into the other. The `DisposableEffect` registering `onDelete` is
   **unchanged and deliberately so**: `Delete` works in select-only mode for the same reason it works in
   the open one, through the same slot, and the slot has no other claimant.

5. **`shell/IdleToolOverlay.kt` (new)** — the rule, alone, because `EditorScreen.kt` is at **139 lines**
   and `ActiveToolOverlay.kt` at **120**:

   ```kotlin
   internal fun idleCanvasTool(tools: List<EditorTool>, document: EditDocument): CanvasTool? =
       tools.filterIsInstance<CanvasTool>().firstOrNull { it.selectableIn(document) }
   ```

   `tools` arrives already filtered by the registry and already in rail order, so "first in rail order"
   costs nothing to say. Its doc is where the "one, not all" argument lives.

6. **`shell/EditorScreen.kt` (edit)** — two lines: the overlay's tool becomes
   `activeTool ?: idleCanvasTool(tools, document)` and it is handed `selectOnly = activeTool == null`.
   `canvasDocumentFor(activeTool, document)`, `resetKey = activeToolId` and `EditorBottomPanel`'s
   `activeTool` all keep taking the **open** tool and nothing else — no inspector, no rail highlight and
   no zoom reset for a tool the user did not open.

7. **`tools/markup/MarkupTool.kt` (edit)** —
   `override fun selectableIn(document: EditDocument) = document.markupShapes().isNotEmpty()`. Empty
   list, no overlay, no pointer taken — so a picture with no marks behaves exactly as it does today.

8. **`tools/markup/MarkupOverlay.kt` (edit)** — pass `scope.selectOnly` into `markupGestures`, as a
   function beside the others for the reason that file already gives. **The text field is not composed
   at all** in this mode: `edit` can only be set by the recogniser, which cannot open one (step 9), so
   the branch is unreachable — assert that by not writing it rather than by trusting it. `captureKeys`
   stays as it is; with no edit it is always false.

9. **`tools/markup/MarkupPointer.kt` (edit)** — `selectOnly: () -> Boolean`, and two guards:

   - a `MarkupGesture.Draw` press in this mode **deselects and returns before `down.consume()`**,
     whether or not something was selected — which folds into the existing "empty picture with something
     selected" branch by dropping its `wasSelected != null` condition when the flag is set. Unconsumed
     is what leaves the press free to pan;
   - the tail that opens a caret — `textEditAt` and everything after it — is skipped. A second click on
     a caption may select it and nothing more.

   The file is at **122 lines** and this is ~15 with its comments. If `wc -l` reports over 150, the split
   is that caret tail into `MarkupCaretPress.kt`, as its own step.

## Files

`core/EditorToolScope.kt` (edit), `core/CanvasTool.kt` (edit) under
`shared/src/commonMain/kotlin/com/woka/feature/editor/`; `shell/IdleToolOverlay.kt` (new),
`shell/ActiveToolOverlay.kt` (edit), `shell/EditorToolScopeImpl.kt` (edit), `shell/EditorScreen.kt`
(edit), `tools/markup/MarkupTool.kt` (edit), `tools/markup/MarkupOverlay.kt` (edit),
`tools/markup/MarkupPointer.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`.

## Verification

- `jvmTest/unit/IdleToolOverlayTest.kt` (new) — `idleCanvasTool` returns Markup for a document holding a
  shape, null for an empty one, null for a list holding only Crop, and the **first** of two tools that
  both claim the document;
- `jvmTest/ui/MarkupIdleUiTest.kt` (new), the ticket's real test — open Markup, draw an arrow, click the
  rail item again to close the tool, and then:
  - the inspector is gone (`editor-inspector` does not exist) while the overlay is present;
  - a click on the arrow selects it — asserted through the overlay's own state, since nothing in the
    document records a selection (§3.1);
  - a drag from the arrow's body moves it, in **one** further undo step;
  - `Delete` removes it, and the overlay then disappears because the document has no shapes left;
  - a click on empty picture **mints nothing** — the shape count is unchanged and `canUndo` has not
    moved — and a click on empty picture with the text pen last chosen opens no caret
    (`markup-text-field` does not exist);
- `jvmTest/ui/MarkupIdleUiTest.kt` — with a caption in the document, a drag on one of its corners
  resizes it (ME-148's handles, reached with no tool open);
- `jvmTest/ui/CropToolSwitchUiTest.kt` — unchanged and still green, which is what says opening a tool
  still takes the canvas back from the idle overlay.

## Done when

- with no tool open, a mark can be selected, moved, resized and deleted, and the picture still pans and
  zooms where there is no mark;
- with no tool open, nothing can be **created** — no shape, no caret;
- no inspector and no rail highlight appear for the idle tool;
- opening any tool hands the canvas back to it, and closing it hands the canvas back;
- a picture with no marks behaves exactly as it does today;
- no file in `tools/markup/` or `shell/` exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
