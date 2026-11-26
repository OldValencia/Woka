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

**Status: written 2026-11-26, not ready to build.** The questions below are the author's. The
steps are drafted against the tree as read on 2026-11-26 and are re-cut once they are answered.

## Answers so far (the author, 2026-11-26)

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
- **Status:** every question is answered. The steps are re-cut to these answers before building.

## Questions for the author — answer before building

1. **A photo's Resize today is an op, folded and saved by `Done`.** The prototype has no Resize
   in the editor: size is chosen in `Create a copy` (Original / Large 2048 px / Small 1080 px).
   - Does `Done` stop resizing the file itself?
   - What happens to a Resize op that files already carry: do the readers keep folding it?
2. **HDR → SDR on a photo is an in-place bake today.** `Done` re-encodes the source, irreversibly,
   after `BakeToSdrDialog`. The prototype's answer 2 moves Standard colours (SDR) to `Create a
   copy`, which writes a new file. Does the in-place bake go, along with `HdrTool`'s op and its
   dialog?
3. **`Audio only…`** is a M4A export, which is ME-245 (1.1.0, not built). Should 1.0.9 leave the
   button out, or draw it disabled with "1.1.0"?
4. **Save frame as photo:** the app writes a JPEG beside the video (`FramePhoto.kt`), the
   prototype says PNG. Which format?
5. **Revert:** the prototype says "You can undo this action". The app's `RevertDialog` says "it
   cannot be undone", because `engine.revert` writes the file at once. Does the revert become
   undoable, or does the prototype's line change?
6. **Settings switch for the SDR option** (answer 2). Today Settings → Editor draws one row per
   tool, by rule ("No per-tool code, now or ever"). Is it:
   - a new non-tool row, `editorOffersSdr`, in the same panel; or
   - HDR's own tool row, kept with a new meaning?
   Whatever it is, Resize's row goes once Resize is no longer a tool.
7. **The help sheet.** The prototype's video help lists step 2, "Add music or a voice recording",
   which is 1.1.0. Should 1.0.9's sheet drop that step?

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

## Steps (draft — re-cut after the questions)

**Prototype first**
1. `woka/`: apply the answers to questions 1–7, then check headless (`shot.mjs`).

**Toasts**
2. `core/ui/components/Toast.kt` (new): the spec's toast with an optional action and timing.
   `EditorRenderBanner` and `EditorSaveBanner` in `StatusBanners` are checked first; a
   progress toast replaces `EditorRenderBanner` if its states match (name, bar, ✕ → Cancel).
   `UnlinkUndoBanner`'s six seconds and `AccessoryPill` are the existing Undo precedent.
3. Undo toasts on `deleteSelected` (`StripActions.kt`) and the Markup `Remove`
   (`TimelineMenuItems.kt`, the canvas Delete). `Undo` calls `session.undo()`.

**Create a copy**
4. `EditorTopBarSetup`: `?` and `Create a copy…` (`EditorTopBarControls`); `DoneChoices` keeps
   `keeps` and `instead` and loses nothing a `RenderOnly` file needs.
5. `CreateCopyDialog.kt` (new): size tiles, the SDR toggle, actions. It starts `exportAction` with
   the chosen size and colours, through the engine's `RenderVideo` / `RenderPhoto`, which take a
   size and SDR instead of reading Resize's and HDR's ops (question 1, 2).
6. `Save frame as photo…` in the clip and empty-place menus (`TimelineMenuItems`), through
   `SaveFramePhoto` at the play head (question 4).

**Dialogs and help**
7. `DiscardChangesDialog` becomes `Keep your changes?` (three buttons). `RevertDialog` takes the
   prototype's text and buttons (question 5). Both are drawn in the spec's dialog frame.
8. `EditorHelpSheet.kt` (new): the per-kind steps, each card opening its tab or `Create a copy`
   (question 7).
9. Settings: the switch from question 6, read by `CreateCopyDialog`.

**Tests**
10. UI tests:
    - `CreateCopyUiTest`: the button opens the dialog; SDR shows for HDR only, and is hidden by
      the Settings switch; Create copy starts the export at the chosen size.
    - `KeepChangesUiTest`: three buttons, each doing what it says.
    - `RevertDialogUiTest`.
    - `ToastUiTest`: Undo undoes; the timing pauses on hover.
    - `EditorHelpSheetUiTest`: a card opens its tab.

    Replacements for the deleted `EditorRailHdrUiTest` and `ResizeInspectorUiTest` come from
    the questions' answers.

## Files

Draft: `Toast.kt`, `CreateCopyDialog.kt`, `EditorHelpSheet.kt` (new); `EditorTopBarSetup`,
`EditorTopBarControls`, `DoneChoices`, `EditorExportAction`, `EditorHostDialogs`,
`EditorHostControls`, `DiscardChangesDialog`, `RevertDialog`, `BakeToSdrDialog`,
`StatusBanners`, `EditorRenderBanner`, `StripActions`, `TimelineMenuItems`, `VideoEditorOutputs`,
`ImageEditorOutputs`, `ResizeTool`, `HdrTool`, the Settings → Editor panel, the tests. Final list
after the questions.

## Done when

- `Create a copy…` stands beside `Done`, and every export the engines offer is reachable from it
  or from the timeline menus.
- Cancel with edits asks `Keep your changes?`; Revert asks in the prototype's words.
- `?` opens the help sheet; deleting a clip or a text shows an Undo toast; an export shows its
  progress with Cancel.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.
