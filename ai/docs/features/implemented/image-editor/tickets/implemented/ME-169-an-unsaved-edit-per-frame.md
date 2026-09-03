# ME-169 — An unsaved edit per frame

| | |
|---|---|
| **Order** | after ME-167 |
| **Depends on** | ME-158, ME-156, ME-126 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Carry unsaved work across a frame change inside the editor, so walking from frame 1 to
frame 3 and back finds frame 1 as it was left.

**Why now.** Raised by ME-158 on 2026-08-30, which chose the discard dialog instead — deliberately,
because the alternative is a redesign. `EditorHost` keys `engine`, `stored`, `session` and `editScope`
on `remember(target.path, frameIndex)`, so any frame change tears down all four and the work goes with
them. `EditorHostControls.selectFrame` therefore has to ask: `if (isDirty) pendingFrame = next`, and
`DiscardChangesDialog` is shown with `CHANGING_FRAME`. The document is already keyed by frame on disk
(ME-126) and the strip already marks which frames carry an edit (ME-157) — the only thing that is not
per-frame is the *unsaved* half.

## Steps
1. `EditorSession` becomes multi-frame: `class EditorSession(media: MediaItem, initial: Map<Int, EditDocument>)`, holding `histories: Map<Int, EditorHistory>` and a `frame: Int` saying which one
   `document`, `canUndo`, `canRedo`, `undo()`, `redo()`, `preview()` and `commit()` address. Add
   `fun moveToFrame(index: Int)`, which changes the answer and touches no history.
2. **Undo never walks out of the frame being looked at.** `undo()` operates on the current frame's
   history only. A frame with no edits has a history of one entry and `canUndo` false — so the Undo
   button greys out when you step onto an untouched frame, which is the correct and legible answer.
3. `isDirty` becomes `any frame differs from what it opened with`. This is what `EditorExitEffect`
   and the close dialog read (ME-160), and it is now true if *any* frame has work — which is what
   makes the exit prompt honest.
4. In `EditorHost.kt`, drop `frameIndex` from the `remember` keys of `session`, and keep it on
   `engine` and `stored`: the engine draws one frame's pixels and must still be rebuilt, but the
   session must not. Call `session.moveToFrame(frameIndex)` in a `LaunchedEffect(frameIndex)`.
5. `EditorHostControls.selectFrame` loses its `isDirty` guard entirely, and `DiscardChangesDialog`'s
   `CHANGING_FRAME` body is deleted — the dialog itself stays, it is still used for the close path.
6. `Done` writes every frame it touched, in one merge into one `EditPayload`. The payload is already
   frame-keyed (ME-126), so this is a fold over the session's map, not a new format. The `EditScope`
   switch (All Frames / This Frame, ME-156) still decides which slot each frame's document lands in.
7. `EditorSession.kt` is 145 lines and this makes it bigger. **Split first**: the multi-frame holder in
   `EditorSession.kt`, the single-frame history operations it delegates to in `EditorFrameSession.kt`.
   The split is step one of the work, not a cleanup after it.

## Files
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorSession.kt` (edit + split, 145 lines)
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorFrameSession.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHost.kt` (edit, 126 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHostControls.kt` (edit, 104 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/DiscardChangesDialog.kt` (edit, 55 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/ImageSaveJob.kt` (edit — the merge)

## Done when
- `jvmTest/unit/`: edit frame 1, move to 3, move back — frame 1's document is intact and its history
  is walkable; `Undo` on frame 3 with no edits is a no-op and `canUndo` is false; `isDirty` is true
  when any frame differs;
- `jvmTest/integration/`: `Done` on a burst with edits on frames 0 and 2 writes both slots and leaves
  frame 1 unedited;
- no `Color`-less regression on the close path — the exit dialog still appears with work on any frame;
- confirmed by hand on `burst.heic` (5 frames) and `anim.gif` (8 frames);
- `./gradlew.bat :shared:build` green.
