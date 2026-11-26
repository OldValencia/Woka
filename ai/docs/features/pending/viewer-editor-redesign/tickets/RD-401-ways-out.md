# RD-401 — Ways out

| | |
|---|---|
| **Order** | ninth, after RD-302; RD-501 (the viewer's transport) follows |
| **Depends on** | RD-201 (the header, `DoneChoices`), RD-301 (the timeline menus) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Give the editor the prototype's ways out (`#dialogs/copy`, `#dialogs/keep`,
`#dialogs/revert`, the `?` sheet): a `Create a copy…` button and dialog beside `Done`, the three
dialogs in the prototype's words, a help sheet, and toasts — Undo, and progress with Cancel.

**Why now.** Since RD-201 a film's and a photo's exports cannot be reached. `Render video…`,
`Render photo…` and `Save frame as photo` exist (`VideoEditorOutputs.kt:64`,
`ImageEditorOutputs.kt:32`) but only a `RenderOnly` file reaches them, through `DoneChoices.instead`.
Resize and HDR have no tab (`tabbed = false`) and no other home. The prototype's timeline menus
offer `Save frame as photo…`; the app's do not.

**Status: written 2026-11-26; the author answered the questions the same day.** The steps are cut
to those answers. One point is still open (the size tiles, below). Re-read the files before
building (`tickets.md` §1).

## Decided with the author (2026-11-26)

- **Done asks before it saves**, a simpler version of Photoshop's save options. It shows a
  confirmation and the size to save at, so Resize moves into `Done`'s dialog (Q1).
- **SDR moves to `Create a copy`**, because it cannot be undone (Q2). Whether `Done`'s dialog also
  shows HDR is still open.
- **`Audio only…` is drawn disabled** until ME-245 (Q3).
- **The help sheet has no music step** until 1.1.0 (Q7).
- **`Done`'s dialog shows the size only**; HDR → SDR is offered only in `Create a copy`.
- **A film's `Done` asks for a confirmation too**, with no size: Resize only sizes a render.
- **Save frame as photo writes a PNG** (Q4). A video frame is already lossy, so a JPEG would
  compress it a second time.
- **Revert stays irreversible** (Q5). The prototype's "You can undo this action" is corrected to
  the app's words.
- **No Settings switch for the SDR option** (Q6): the toggle shows only for HDR media and is off by
  default, so hiding it saves nothing. Answer 2's switch is dropped.
- **Open — the size tiles.** Which sizes `Done` and `Create a copy` offer for a photo: the
  prototype's Original / Large 2048 px / Small 1080 px, or Resize's long edges (3840 / 2560 /
  1920 / 1280) and percentages. Asked; step 1 waits on it.

## The spec, measured (`app.js`, `experience.js`, `refine.js`, `editor.css`, `base.css`)

- **Header:** `?` (help) before the panel toggle. After the divider comes `Create a copy…`, a
  quiet button: 32 dp, radius 16, transparent, a 1 dp `line` ring, 13 sp, tip "Make a separate
  file with your edits applied, ready for other apps.". `Done` follows.
- **Dialog:** `min(720 dp, 90 %)` wide, 85 % of the height at most, padding 24 × 28, radius 18,
  `panel` with a 1 dp `line` border; it opens with 180 ms `dialog-in`. Title 21 sp / 600, body
  14 sp / 1.7, `h3` 13 sp. Actions sit right-aligned, gap 8, 22 dp above. Primary: padding 8 × 20,
  radius 18, the editor's orange with dark text (answer 6). Option cards and help options: radius
  12, `well`.
- **Create a finished copy:** "A new file with your edits. The original stays as it is."
  - **Size** is a segmented control of three tiles (min 48 dp, a bold name over a small
    detail): video Original / 720p / Small (854 × 480); photo Original / Large 2048 px / Small
    1080 px.
  - **Standard colours (SDR)** is a toggle, off by default, shown for HDR media only. Tip: "Use
    this when the video looks too bright or washed out in other apps."
  - Actions: `Audio only…` (quiet, video; question 3) and `Create copy` (primary).
- **Keep your changes?** is asked only when there are edits: "You can come back and edit again at
  any time." Buttons: Keep editing (secondary), Discard changes (quiet), Keep changes (primary).
  Keep changes saves and shows the toast "Edits kept. The original file is untouched."
- **Revert to original?**: "All edits to the video, including cuts, will be reset." (or "the
  photo"), plus the second line (question 5). Two full-width buttons: Revert to original
  (primary), Cancel (quiet).
- **Help sheet** (`?`): numbered option cards, each opening its tool or dialog. Video: "A short
  edit, step by step" — keep the moments you want, then keep the edit or make a finished copy.
  Photo: "A quick photo edit" — try a style, fine-tune light and colour, frame the picture,
  compare and finish.
- **Toast:** bottom 22, centred, gap 16, padding 10 × 18, radius 22, `tip` with `tipText`, 13 sp.
  - **Undo toast:** a quiet `Undo`; it hides at 7 s, or 4.5 s without Undo. Hover pauses it, and it
    hides 3 s after the pointer leaves. Uses: "Clip removed. The remaining clips close the gap.",
    "Text removed."
  - **Progress toast:** "<label>…", a 120 × 4 bar (radius 2, `accent`) and `Cancel`.
    - Cancelled: "Cancelled. Nothing was saved."
    - Done: "Saved next to the original." with `Show in folder`, hiding at 6 s.

- **Save your changes?** (new; drawn in the prototype first): the dialog `Done` opens.
  - **A photo:** the size tiles over `Save` (primary) and `Cancel`. The size is Resize's op, so it
    stays in the edit and can be changed again. The tiles open on what the document holds
    (Original when it holds none).
  - **A film:** the same dialog with no size. Resize only sizes a render.
  - A `RenderOnly` file's `Done` is its render (`DoneChoices.instead`), and the dialog does not
    stand in front of it.

## Steps

**Prototype first**
1. `woka/`:
   - `#dialogs/done`: `Save your changes?` with size tiles for a photo, without them for a film.
   - `Create a copy`'s SDR toggle stays.
   - `Audio only…` is drawn disabled, with the tip "Coming in 1.1.0".
   - Revert's body reads "This cannot be undone."
   - The help sheet loses its music step, and `Save frame as photo…` writes a PNG.

   Check headless (`shot.mjs`).

**Toasts**
2. `core/ui/components/Toast.kt` (new): the spec's toast with an optional action and its timing.
   - The progress toast takes `EditorRenderBanner`'s place (name, bar, ✕ → `Cancel`), driven by
     `EditorRenderStatus` as now.
   - On finish it reads "Saved next to the original." with `Show in folder`.
   - `UnlinkUndoBanner` is the existing Undo precedent.
3. Undo toasts on `deleteSelected` (`StripActions.kt`) and on a Markup shape's removal (the menu's
   `Remove`, the canvas Delete). `Undo` calls `session.undo()`.

**Done and Create a copy**
4. `SaveChangesDialog.kt` (new), opened by `Done` (`EditorHostControls.done`):
   - A photo gets the size tiles, written through Resize's op on `Save` (`ResizeTool` stays a tool
     with `tabbed = false`; its Settings row stays).
   - A film gets the confirmation alone.
5. **HDR leaves `Done`.**
   - `bakesToSdr` is no longer asked on `Done` (`EditorHostControls:83`, `ImageEditWrites.kt:54`).
   - `BakeToSdrDialog` is deleted, and `HdrTool` with it: its row leaves Settings → Editor, and a
     stored `hdr` op is skipped as an unknown tool's is.
6. `EditorTopBarSetup`: `?` and `Create a copy…` (`EditorTopBarControls`). `CreateCopyDialog.kt`
   (new) offers:
   - the size tiles;
   - Standard colours (SDR) for HDR media;
   - `Audio only…`, disabled;
   - `Create copy`, which runs `exportAction` with `RenderPhoto` / `RenderVideo` at the chosen
     size and colours. `EditorExport` gains the size and an SDR flag; the render reads them
     instead of the document's Resize op.
7. `Save frame as photo…` in the clip and empty-place menus (`TimelineMenuItems`), through
   `SaveFramePhoto` at the play head; `FramePhoto.kt` writes a PNG.

**Dialogs and help**
8. `DiscardChangesDialog` becomes `Keep your changes?`, with three buttons, asked only with edits.
   `RevertDialog` gets the spec's frame, buttons and "This cannot be undone." Both are drawn in
   the spec's dialog frame (`EditorDialogFrame.kt`, new, shared by the four dialogs).
9. `EditorHelpSheet.kt` (new): the per-kind steps without music, each card opening its tab or
   `Create a copy`.

**Tests**
10. UI tests:
    - `SaveChangesUiTest`: a photo's `Done` asks, `Save` writes the chosen size and saves; a film
      gets no size.
    - `CreateCopyUiTest`: SDR shows for HDR media only, `Audio only…` is disabled, and Create copy
      starts the export with the size and SDR.
    - `KeepChangesUiTest`: each of the three buttons.
    - `RevertDialogUiTest`.
    - `ToastUiTest`: Undo undoes; the timer pauses on hover.
    - `EditorHelpSheetUiTest`.

    Unit: `HdrBakeTest` is replaced by a copy's SDR, and `ResizeToolTest` stays. `FramePhoto`
    writes a PNG.

## Files

New: `Toast.kt`, `SaveChangesDialog.kt`, `CreateCopyDialog.kt`, `EditorDialogFrame.kt`,
`EditorHelpSheet.kt`, the tests. Edited: `woka/` (step 1), `EditorTopBarSetup`,
`EditorTopBarControls`, `DoneChoices`, `EditorExportAction`, `EditorExport`, `EditorHostDialogs`,
`EditorHostControls`, `DiscardChangesDialog`, `RevertDialog`, `ImageEditWrites`, `StatusBanners`,
`EditorRenderBanner`, `StripActions`, `TimelineMenuItems`, `FramePhoto`, `VideoEditorOutputs`,
`ImageEditorOutputs`, the render's size reading, the tool registry's Koin list. Deleted:
`BakeToSdrDialog.kt`, `tools/hdr/`. The author waived the size limit for a block; each touched
`.kt` stays ≤ 150 lines.

## Done when

- `Done` asks `Save your changes?`: a photo with its size, a film without one.
- `Create a copy…` stands beside `Done` and makes a new file at the chosen size, SDR for HDR media.
  The source is untouched, and `Audio only…` is drawn disabled.
- Every export the engines offer is reachable. `Save frame as photo…` writes a PNG.
- Cancel with edits asks `Keep your changes?`; Revert asks in the prototype's frame and says it
  cannot be undone.
- `?` opens the help sheet; deleting a clip or a shape shows an Undo toast; an export shows its
  progress with Cancel.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.
