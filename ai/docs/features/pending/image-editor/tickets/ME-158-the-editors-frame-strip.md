# ME-158 — The editor's frame strip

| | |
|---|---|
| **Order** | 66 — after ME-157, whose component and mark it mounts |
| **Depends on** | ME-157 (the shared strip and the edited mark), ME-156 (the scope switch and `EditorFrames`) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Put the frame strip down the editor's left edge so a frame set can be walked **without
leaving the editor**, and settle what happens to unsaved work when the frame changes.

**Why now.** After ME-156 the editor can say "this frame only", and the only way to reach a different
frame is to cancel, pick it in the viewer's strip and press `Edit` again. That is three actions to move
one thumbnail, and it is the last thing standing between the switch and a burst being editable frame by
frame.

## Placement, and why it is a strip rather than a carousel

**Vertical, left edge, exactly as in the viewer** (decided 2026-08-30). The axis carries the meaning and
this project already made that call once: the carousel runs horizontally along the bottom and moves
*between* files; the strip runs vertically and moves *within* one. A horizontal row of frames in the
editor would sit above the rail and below the inspector, a third band in a stack that already has two,
in the same axis as both — which is the shape `FrameStrip`'s own doc records as having been tried and
rejected in the viewer.

The editor's left edge is free: the rail and inspector are a centred column at the bottom, and the
canvas fills the rest.

**Shown exactly where the scope switch is shown** — `EditorFrames.allowsPerFrame`, so more than one
frame and not an animation. One rule, one sentence: the strip exists to choose which picture you are
editing, and an animation is one picture that moves (ME-140). A file with a strip therefore always has
the switch above it, and neither ever appears without the other.

## The question this ticket exists to answer

`EditorHost` builds the engine, the stored document and the session with `remember(target)`, so changing
`EditorTarget.frameIndex` rebuilds all three — **discarding whatever was edited on the previous frame**.
Silently. That is the same loss ME-124 closed for the router, arrived at from inside the editor.

**Chosen: switching frames with a dirty document asks first**, through the `DiscardChangesDialog` that
`Cancel` already uses, reworded to name what is being left. Confirm and the editor rebuilds on the new
frame; keep editing and the strip's selection snaps back. Clean documents switch with no dialog at all,
which is the ordinary case.

This is honest and it is small. It is not the best possible answer, and the better one is written down
rather than half-built:

- **Rejected — save on switch.** Elegant on paper: `Done` merges into the payload (ME-126), so each
  frame's work would accumulate. An autosave triggered by a click is a surprise, and there is no undo
  across it.
- **Deferred — hold an unsaved edit per frame.** The real shape is `EditorSession` holding a document
  **and a history** per frame, so undo does not walk out of the frame you are looking at into one you
  are not. That is a redesign of a 145-line state holder at the size mandate, and it is a ticket, not a
  step. It goes to `backlog.md` as part of this change.

## Steps

1. **`shell/EditorFrameStrip.kt` (new)** — the editor's mounting of the shared component: it reads the
   file's frames once (`stillDecoderFor(file).frameSet(file).viewable`, off the composition thread in a
   `LaunchedEffect`, for the reason `rememberFrameStrip` gives — opening a 1296-stream HEIC is
   milliseconds), takes `MediaThumbnails.loadPreview` as `loadPreview`, takes
   `rememberEditedFrames(path)` for the marks, and hands the selection out.

   **It does not reuse `rememberFrameStrip`**: that models the *viewer's* strip — it appends a Live
   Photo's motion sibling, which is a different file the editor has no decoder for and
   `FrameStripModel.editableFrameOf` already exists to keep out. The editor's list is the viewable
   frames and nothing else.

2. **`shell/EditorScreen.kt` (edit)** — mount it at `Alignment.CenterStart` when
   `frames.allowsPerFrame`, with a `frameIndex` and an `onSelectFrame` parameter passed through from
   the host. The canvas host is inset by `FRAME_STRIP_WIDTH` on the start edge in the same condition,
   so the picture is centred in what is left rather than under the strip —
   `MediaViewerPlayer.navStartInset` is the same measurement made in the viewer.

3. **`shell/EditorHost.kt` (edit)** — the frame is state here, because it is what the engine and the
   session are keyed on:

   ```kotlin
   var frameIndex by remember(target.path) { mutableStateOf(target.frameIndex) }
   var pendingFrame by remember(target.path) { mutableStateOf<Int?>(null) }
   ```

   …with every `remember(target)` becoming `remember(target.path, frameIndex)`, and
   `onSelectFrame = { next -> if (isDirty) pendingFrame = next else frameIndex = next }`.
   `remember(target.path)` rather than `remember(target)` for the frame itself, so re-entering the same
   file does not reset a frame the user walked to, while a different file does.

   **`editScope` is re-seeded on every frame change** — `engine.frames.storedScope` is a property of the
   frame that was opened, and frame 1 having no override while frame 3 has one is exactly the case this
   gets wrong if the value survives the switch.

4. **`shell/DiscardChangesDialog.kt` (edit)** — a `title` and body parameter pair with the current
   strings as defaults, so the frame-change case can say *"This edit has not been saved. Moving to
   another frame now loses it"* while `Cancel` keeps saying "Closing the editor now loses it". Same
   component, same buttons, one sentence apart.

   While the file is open: its doc names "the overwrite confirmation ME-130 adds beside it", which §5
   deleted on 2026-08-19 — there is no overwrite and no such dialog. Corrected in the same edit, because
   leaving a stale sentence in a file this ticket rewrites is how a stale sentence survives.

5. **`shell/EditorHost.kt` (edit)** — the second dialog site:
   `if (pendingFrame != null) DiscardChangesDialog(onDiscard = { frameIndex = pendingFrame!!;
   pendingFrame = null }, onKeepEditing = { pendingFrame = null }, …)`. The file is at **144 lines**
   before ME-130 and ME-156 have both added to it; **this step is where `shell/EditorActions.kt` is
   created if it does not exist yet** — `leave`, `done` and `revert` as builders taking the session,
   the engine and the callbacks.

6. **`../image-editor.md` §4.2 (edit)** — the layout sketch gains the strip on the left, marked as
   appearing only for multi-frame stills.

7. **`../image-editor.md` §9 (edit)** — a row dated 2026-08-30: *What happens to unsaved work when the
   edited frame changes?* → *the discard dialog, reworded* — with the two alternatives above and the
   reason the good one is deferred.

8. **`backlog.md` (edit)** — a new block: **an unsaved edit per frame**, holding the session-holds-a-
   document-and-a-history-per-frame shape and naming `EditorSession`'s size as the reason it is its own
   ticket.

## Files

`shell/EditorFrameStrip.kt` (new), `shell/EditorActions.kt` (new, if ME-130 did not already create it),
`shell/EditorScreen.kt` (edit), `shell/EditorHost.kt` (edit), `shell/DiscardChangesDialog.kt` (edit)
under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit),
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit).

## Verification

- `jvmTest/ui/EditorFrameStripUiTest.kt` (new), the ticket's real test, under `EditorUnderShell` on a
  copy of `BURST_HEIC`:
  - the strip is present for the burst and absent for `STILL_JPG` and `ANIM_GIF`;
  - with a clean document, clicking frame 3 changes what the canvas draws — asserted through
    `engine.sourceSize` / the surface's bitmap, not through the strip's own selection;
  - with a dirty document, clicking frame 3 raises the dialog and the canvas does **not** change;
    `Keep editing` leaves the selection where it was, `Discard` moves to frame 3 with a clean session;
  - a frame carrying an override shows ME-157's mark inside the editor;
  - opened on frame 3 of a file whose frame 3 carries an override, the scope strip reads
    `This Frame`; walking to frame 1, which carries none, it reads `All Frames`;
- `jvmTest/ui/EditorZoomFidelityUiTest.kt`, `CropOverlayUiTest.kt` — still green with the strip
  present, which is what says step 2's inset moved the canvas rather than shrinking the picture's own
  coordinate space;
- `jvmTest/ui/EditorShellUiTest.kt`, `EditorRevertUiTest.kt`, `EditorKeysUiTest.kt` — unchanged and
  still green.

## Done when

- a burst, a Live Photo still and a multi-page TIFF show the strip in the editor; a photograph and an
  animation show nothing and are laid out exactly as they are today;
- clicking a frame edits that frame — the canvas, the scope strip and `Done`'s destination all follow;
- unsaved work is never lost to a frame change without the dialog;
- the canvas is centred in the space beside the strip, not under it;
- the deferred per-frame-history ticket is written into `backlog.md`;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
