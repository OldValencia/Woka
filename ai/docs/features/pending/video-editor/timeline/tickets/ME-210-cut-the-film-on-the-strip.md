# ME-210 — Cut the film on the strip

| | |
|---|---|
| **Order** | first in `timeline/` (§1a), built 2026-11-01 in one sitting with ME-231 and ME-291 |
| **Depends on** | ME-206 (the band and the seam) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Trim, split, delete, duplicate and reorder a film on the band, with the canvas playing
what is kept.

**Why now.** The author's brief (§0): *вырезать из центра часть и удалить её либо вставить её в
другое место*. The video editor had been switched off since 2026-10-17 with nothing to cut with.

**Re-cut from five blocks** (`tickets.md` §4): ME-210 (the list and a trim), ME-211 (the
filmstrip), ME-212 (split), ME-213 (select, delete, duplicate) and ME-214 (reorder). Each alone
was a strip that showed something it could not change, or a change nothing showed; together they
are the four gestures of `video-editor.md` §4.3. The ids 211–214 are folded into this one and
not reused.

## What was built

1. `ToolDomain.Time`, **prepended** (`video-editor.md` §5).
2. `commonMain` `editor/tools/trim/`: `TrimClip` (stable id, `fromMs`, `toMs`), `ClipTimeline`
   (ME-231's half, see playback/), `ClipEdits` — `split`, `delete`, `duplicate`, `move`,
   `trimStart`, `trimEnd`, each returning the list unchanged when it has nothing to do, with a
   100 ms floor (`MIN_CLIP_MS`).
3. `jvmMain` `TrimTool` — `Single`, video only, one `ObjectParam` `clips`, `stage()` a `ClipStage`
   that the raster fold drops. Registered in `EditorModule`; Phosphor-style `scissors.svg`.
4. **The seam grew a writer.** `EditorEngineSurface.Timeline(document, edits, modifier)` with a
   new `EditorTimelineScope` (preview / commit / cancel / selection / `editing`), built by
   `EditorBottomPanel` over the session. The band's selection is its own `ToolSelection`, apart
   from the canvas's.
5. The band (`VideoEditorBand`) lays out `ClipStrip` instead of the slider: filmstrip stills from
   `SeekPreviewSource` (`FilmstripCells`), the play head, and with Trim open the outer handles,
   the ✂ on the play head, and a floating ⌫ / ⧉ bar over the selected clip.
6. Gestures (`StripGestures`): at rest press-and-drag scrubs; with Trim open a click selects and
   seeks, a drag carries a clip (one history entry per drop, none if it came back), and an outer
   handle trims with the picture following the edge.
7. `ClipWalk` keeps the player inside the list: a join is a seek, the end of the result pauses,
   play at the end restarts from the first clip. Readout and strip are in timeline time.

## Deviations from the design, and why

- **Split / Delete / Duplicate live on the strip, not in the inspector.** An `ActionParam` would
  need the play head and the selection inside `EditorTool.resolve`, which is handed a frame size
  and a histogram. The inspector shows the tool's footnote only.
- **No join grips** (the mockup's `.grip`): moving a cut is split + trim today.
- **`Delete` key** is not wired: the key slot belongs to the open canvas tool (`CanvasKeys`), and
  sharing it is ME-213's open note.
- **Joins stall** for one seek each. The seamless hand-off is ME-232's, and it replaces
  `ClipWalk.onPosition` alone.

## After the author's first look, 2026-11-02

Four reports, three of them this ticket's, each measured before it was fixed:

- **No picture on open.** A paused-open player decodes and publishes nothing, and a seek asked
  at construction is absorbed by the decoder's generation capture. `VideoPlayerHolder` now seeks
  once the duration is written, until a frame lands. ME-207's claim was wrong.
- **The strip ended a second early.** A keyframes-only grab past the last keyframe returns null;
  `FilmstripCells` borrows the nearest earlier cell.
- **A trimmed end snapped back to 100 %** and showed nothing of what it cut. With Trim open the
  strip lays out the trimmed-off stretches, dimmed, with their film (`trimmedEnds`), so the
  handle stays where it was let go. The edge-drag layout freeze went with it.
- Markup after `Done` not in the player — ME-284, built the same day.

Second round, the same day:

- **Right-click on the strip resets the cut** to the whole film, one history entry, nothing when
  uncut — through `resetOnRightClick`; `awaitFirstDown` ignores a right press (measured).
- **A selected clip has both its own ends.** Handles: the timeline's outer ends always, and both
  edges of the selected clip; not every clip's, two handles 3 px apart at each join being a target
  nobody hits. An edge drag holds its starting layout and shades what it takes.
- **No symbols in UI text**: `Delete` / `Duplicate`, the Trim footnote, and the `Done` caret as the
  app's chevron icon (the author: emoji read as generated).

## Verified

- `jvmTest/unit/ClipTimelineTest`, `unit/ClipEditsTest`, `unit/StripGeometryTest`, `integration/EditorOpenTest`; `ui/ClipStripUiTest` (✂ splits once, a
  split on a join pushes nothing, the bar deletes and the hole closes); `ui/VideoBandUiTest`,
  `ui/EditorShellUiTest` updated (five domains, four dividers).
- **Not yet by hand**: drag-to-reorder, handle trims and the filmstrip have not been looked at on
  screen.
