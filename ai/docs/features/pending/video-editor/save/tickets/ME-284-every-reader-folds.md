# ME-284 — Every reader folds

| | |
|---|---|
| **Order** | after ME-288 and ME-210; built 2026-11-02, the day after the strip |
| **Depends on** | ME-288 (the `uuid` box), ME-210 (the clip list) |
| **Feature** | [Save](../save.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** What `Done` writes into a film is what the viewer, PiP and the grid show.

**Why now.** The author, 2026-11-02: markup placed, `Done` pressed, the markup visible in the
editor and nowhere else — the second time (after 2026-09-04) and the reason `tickets.md` §4
exists. Asked the same day: build it now, rather than hide `Done` for video.

## What was built

1. `core/ui/image/VideoEdits.kt` — `VideoEdits`, `AppliedVideoEdit`, `SourceRange`, and the
   `AppliedVideoEdits` holder: `StillEdits`' seam for a film, so `feature/viewer` reads the
   editor's fold without naming it.
2. `engine/video/RecipeVideoEdits.kt` — folds `EditRecipe.documentIn` at the coded frame size,
   cached on path + length + mtime; installed `createdAtStart` in `EditorModule`.
3. `engine/image/RecipePainter.kt` + `drawThroughRecipe` extracted from `RasterSurface` — the
   viewer's painter draws by the editor canvas's own lines; intrinsic size is the crop's.
4. `MediaViewerFrame` uses the edited painter when there is one (main viewer and PiP both draw
   through it) and runs `PlayEditedRanges` — the editor's `ClipWalk` rules over `SourceRange`.
5. `MediaThumbnails` → `editedPoster` on every video poster, cache hit included.

## Third round, 2026-11-02

- **A trim showed only after leaving the viewer and coming back.** The readers re-read on the
  film's mtime, which they only look at when they recompose, and a paused film and its slider
  do not. A save now moves `AppliedVideoEdits.generation`, and both remember on it.
- **Resize at 25 % kept Crop inside the top-left quarter** (screenshot from the author). The
  canvas measured against the resized frame. The editor's canvas and the viewer now fold without
  Resize (`previewFold`); only the render reads it, which is the author's *render only*.
  `unit/PreviewFoldTest`.

## Not in it

- ~~The slider and readout still speak source time~~ — **done 2026-11-02 as part of ME-285**, after
  the author's 26 s film trimmed to 10 s still read 0:26: `EditedClock` (core) is shared by the walk
  and `MediaViewerTimeSlider` via `ResultClock`. Chapters are left off a cut film; the watch record
  is untouched.
- The ambient fill and the hover excerpts draw the frame as shot.
- A cut that ends before the file's end pauses there; Repeat and the folder playlist do not
  advance from it.

## Verified

`jvmTest/integration/VideoEditsReaderTest` (an unedited film reads as none; cuts and picture work
both reach the reader; cuts alone keep the viewer's painter; the poster is drawn through the
edit). **Not yet by hand.**
