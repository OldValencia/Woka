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

**Status: implemented 2026-11-27**, awaiting the author's check by hand. The prototype was
finished first (step 1); the 2026-11-27 answers below replaced the SDR switch.

## Decided with the author (2026-11-26)

- **Done asks before it saves**, a simpler version of Photoshop's save options. It shows a
  confirmation and the size to save at, so Resize moves into `Done`'s dialog (Q1).
- **SDR moves to `Create a copy`**, because it cannot be undone (Q2).
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

## Decided with the author (2026-11-27)

- **No SDR switch.** A copy is what the screen shows: the pill's colour mode (`Auto`/`HDR`/`SDR`)
  already rules a render, and every encode is 8-bit SDR with the tone map baked in; only a
  cuts-only film copies its packets and stays HDR. `Create a copy` instead shows a note when the
  mode retones the picture (`SDR` on an HDR file, `HDR` on an SDR one): "Colour is set to SDR on
  screen. The copy follows it, so its tone mapping changes too." This replaces Q2's and the
  spec's `Standard colours (SDR)`.
- **Custom may enlarge.** A photo takes 1–16384 px a side. A video takes even sizes from 2, the
  long side up to 4096 and the short up to 2304 (H.264). Out of range shows the error under the
  fields and disables the primary button. `Original` is always allowed.
- **The help sheet's last line** reads "The line above the tabs shows whether you're changing one
  clip or the whole video." — the app has no "Editing" label.
- **`Save frame as photo…` opens the prototype's dialog** (`Current frame · PNG`, the time, `Save`),
  then the progress toast.
- **`Save` and `Keep changes` both show** "Edits kept. The original file is untouched." in the new
  toast; the `Saved <name>` pill for `Done` goes.
- **`Create a copy` and `Save frame` leave the editor open**, as the prototype does; only a
  `RenderOnly` file's `Done` closes before it renders.

## Photo size

Both photo dialogs offer `Original`, `75%`, `50%`, `25%`, and `Custom`. The percentages reuse
the existing Resize presets and apply to both dimensions of the frame after crop and other
geometric edits, before any Resize op. `Original` uses that frame at 100%. Keep the editable
`Width` and `Height` fields visible, with their pixel values filled by the selected preset.

Editing either field selects `Custom`. Selecting `Custom` directly keeps the displayed values
and focuses `Width`. Keep `Keep proportions` visible and checked by default. With it checked,
editing either field recalculates the other from the pre-Resize frame's aspect ratio and rounds
to whole pixels. With it unchecked, the fields are independent. Checking it again keeps the
last edited dimension and recalculates the other. Require positive whole pixel dimensions within
the renderer's limits and show a validation error before saving or exporting an invalid size.

`Done` opens at the saved size, or `Original` when no Resize is applied. Saving `Original`
clears the Resize effect. A percentage saves a proportional Resize op. `Custom` saves the
explicit dimensions, including a pair that changes the aspect ratio, and restores the values
and checkbox when reopened. A legacy proportional Resize outside the named presets opens as
`Custom` with the existing dimensions and the checkbox checked. Saving without touching those
controls preserves the legacy percentage. `Create a copy` starts at `Original` and applies its
size only to the export.

## Video size

In `Create a copy`, `Original` means the actual full-size video frame after geometry, before
export resizing. Always show its width × height. Build up to two smaller presets from the
standard 2160p, 1440p, 1080p, 720p and 480p size tiers. Fit the frame within each tier's bounds,
swapping the bounds for portrait, and preserve its aspect ratio. After rounding to valid
encoder dimensions, keep only sizes that are strictly smaller than `Original` (neither axis
grows and at least one shrinks). Deduplicate by final width × height and offer the two largest
remaining sizes. Show the selected preset's dimensions in the editable fields and use a label such as
`720p` only when the displayed size matches the tier's standard dimensions.

For a standard 1080p source this gives 720p and 480p. For a standard 720p source, `Original`
is 1280 × 720 and the only smaller preset is 480p. A source already at or below the smallest
tier offers `Original` and `Custom`.

`Custom` uses the same pixel fields and `Keep proportions` checkbox as photos, based on the
video frame after geometry. Validate custom dimensions against the video renderer's limits.
The chosen size applies to the export. A video's `Done` opens the save confirmation.

## The spec, measured (`app.js`, `experience.js`, `refine.js`, `editor.css`, `base.css`)

- **Header:** `?` (help) before the panel toggle. After the divider comes `Create a copy…`, a
  quiet button: 32 dp, radius 16, transparent, a 1 dp `line` ring, 13 sp, tip "Make a separate
  file with your edits applied, ready for other apps.". `Done` follows.
- **Compact dialog:** `Save your changes?` and `Create a finished copy` share the `File information`
  frame, up to 460 dp wide and 85% high, with 20 dp horizontal padding, a 14 dp radius and an
  18 sp title. Keep primary and cancel actions at the lower right. Use the editor palette over
  the editor and the viewer palette over the viewer.
- **Other dialogs:** use the existing frame and 180 ms `dialog-in`. Editor primary actions use
  the editor's orange with dark text (answer 6). Option and help cards use `well`.
- **Create a finished copy:** "A new file with your edits. The original stays as it is."
  - **Size:** a compact row of presets above the editable dimensions and proportions checkbox.
  - ~~**Standard colours (SDR)**~~ — dropped 2026-11-27; the retone note instead.
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
  - **A photo:** the size choices above `Save` (primary) and `Cancel`, with the initial selection
    and Resize behavior defined under Photo size.
  - **A film:** the same dialog with no size. Resize only sizes a render.
  - A `RenderOnly` file's `Done` is its render (`DoneChoices.instead`), and the dialog does not
    stand in front of it.

## Steps

**Prototype first**
1. `woka/`:
   - Done in the prototype. Both photo dialogs (`#dialogs/done` and `#dialogs/copy`)
     show `Original`, `75%`, `50%`, `25%` and `Custom` with interactive pixel fields and a working
     `Keep proportions` checkbox. Video `Create a copy` adds the same interactive `Custom`
     controls. A film's `Done` gets a plain confirmation.
   - The video copy mockup derives its presets from the scene's video dimensions, including
     1080p and 720p sources.
   - `File information`, `Save your changes?` and `Create a finished copy` share a compact frame,
     heading, section styling and action spacing. Use this layout as the reference when porting
     the dialogs, with the viewer and editor palettes on their respective surfaces.
   - ~~`Create a copy`'s SDR toggle stays.~~ Replaced 2026-11-27 by the retone note.
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
   - A photo gets the shared size controls. Extend the Resize op to support explicit output
     dimensions with independent horizontal and vertical scaling after geometry. Existing
     percentage ops keep their behavior. `ResizeTool` stays a tool with `tabbed = false` and keeps
     its Settings row.
   - A film gets the confirmation alone.
5. **HDR leaves `Done`.**
   - `bakesToSdr` is no longer asked on `Done` (`EditorHostControls:83`, `ImageEditWrites.kt:54`).
   - `BakeToSdrDialog` is deleted, and `HdrTool` with it: its row leaves Settings → Editor, and a
     stored `hdr` op is skipped as an unknown tool's is.
6. `EditorTopBarSetup`: `?` and `Create a copy…` (`EditorTopBarControls`). `CreateCopyDialog.kt`
   (new) offers:
   - the photo or video size choices, sharing the custom dimensions and proportions controls;
   - the retone note when the colour mode changes the picture;
   - `Audio only…`, disabled;
   - `Create copy`, which runs `exportAction` with `RenderPhoto` / `RenderVideo` at the chosen
     size. `EditorExport` gains the size; the render reads it instead of the document's Resize
     op.
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
    - `SaveChangesUiTest`: a photo's `Done` asks, `Save` writes the chosen size, and reopening
      shows the saved size. Cover the percentage presets, `Original`, an existing percentage,
      and a custom size with linked and independent fields. Check that fields are visible on
      opening, selecting a preset fills them, and typing in either one selects `Custom`.
      A film gets no size.
    - `CreateCopyUiTest`: the retone note shows only when the mode changes the picture,
      `Audio only…` is disabled, and Create copy starts the export with the selected size
      without changing the saved Resize. Cover
      percentage sizing for portrait and landscape photos, a custom size that changes the aspect
      ratio, and invalid dimensions. Cover custom video dimensions with the checkbox on and off.
      For video presets, cover a 720p source (no duplicate 720p option), a 1080p source, a larger
      source, a source at or below 480p, and a portrait or non-16:9 frame. Check displayed and
      exported dimensions agree.
    - `KeepChangesUiTest`: each of the three buttons.
    - `RevertDialogUiTest`.
    - `ToastUiTest`: Undo undoes; the timer pauses on hover.
    - `EditorHelpSheetUiTest`.

    Unit: `HdrBakeTest` goes with `HdrTool`, and `ResizeToolTest` stays. `FramePhoto`
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

## As built (2026-11-27)

- `DiscardChangesDialog.kt` became `KeepChangesDialog.kt`. The dialogs share
  `EditorDialogFrame`, `EditorDialogButton` and `EditorDialogTokens`. The size group is
  `OutputSizeState`, `OutputSizeControls` and `OutputSizeParts`. Its arithmetic is
  `tools/resize/OutputSizes.kt`, and the Resize op's pixels are `tools/resize/ResizeOutput.kt`.
- Resize keeps its percentage and gains `width`, `height` and `keepProportions`. Explicit
  pixels fold through `RasterStage.Framed`. `EditorExport` gains `size`, `progress` and `frame`.
  The copy's size replaces any Resize in the render.
- The Undo toast is `EditorUndoToast`, and `undo()` joins both scopes. The progress and save
  toasts are `EditorRenderBanner` and `EditorSaveBanner` on `core/ui/components/Toast.kt`.
  `StatusBanners` sits 22 dp from the bottom, as `.toast`.
- `CanvasKeys.dialogOpen` makes the editor's keys yield while a dialog is up. `Save frame as
  photo…` uses `FramePhotoDialog`. The help sheet's steps open their tab, the film's first clip,
  or `Create a copy`.

## Done when

- `Done` asks `Save your changes?`: a photo with its size, a film without one.
- The save dialog follows Woka's dialog style in both themes.
- `Create a copy…` stands beside `Done` and makes a new file at the chosen size, coloured as the
  screen shows it.
  The source is untouched, and `Audio only…` is drawn disabled.
- The prototype and app offer matching size controls. Both photo dialogs have percentage
  presets and custom dimensions. Video `Create a copy` has source-dependent presets with no
  duplicates and offers custom dimensions.
- Every export the engines offer is reachable. `Save frame as photo…` writes a PNG.
- Cancel with edits asks `Keep your changes?`; Revert asks in the prototype's frame and says it
  cannot be undone.
- `?` opens the help sheet; deleting a clip or a shape shows an Undo toast; an export shows its
  progress with Cancel.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.
