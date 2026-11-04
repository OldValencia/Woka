# ME-217 — Freeze-frame

| | |
|---|---|
| **Order** | after ME-216; promoted and built 2026-11-04 |
| **Depends on** | ME-216 (timeline length ≠ source span), ME-215 (clip bar, looks) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Hold one frame of the film for a while — in the editor, the viewer and a render.
**Why now.** `next_steps.md` 1.0.6: *скорость/стоп-кадр*. Folds in ME-234's freeze half.

## Shape — the author's choices, 2026-11-04

- **`Freeze` in the clip bar** inserts a freeze of the frame under the play head, 2 s long;
  inside a clip it splits it first and sits between the halves, both of which keep the clip's
  look and speed, as does the freeze.
- **Its length is its edge**: either edge of a freeze drags its hold, 0.1–30 s.
- **The model is the design doc's**: a clip whose range is a point plus a hold —
  `TrimClip.holdMs`, `isFreeze`. `lengthMs` is the hold; `atTimeline` answers its one frame for
  the whole of it. It joins nothing, takes no speed and is not frozen again.

## Built

- Editor: `ClipWalk` holds — the player parked on the frame, the hold advanced by a frame-clock
  `tick` in the band, then on to the next clip. Play/pause and Space go through `walk.toggle`,
  so they pause a hold too. The strip places the play head by timeline fraction (`xOf`).
- Viewer: `SourceRange.holdMs`; `EditedClock.heldMs/holding/startOf`; `PlayEditedRanges` holds
  the same way, and a seek on the slider lands inside a hold. Chapters draw a freeze as plain
  track; the hover reel grabs its one frame.
- Render: a freeze always encodes. `recordFreeze` draws the frame through the clip's look onto
  every slot of the hold, with silence under it so the sound after stays level.

## Verified

`unit/FreezeFrameTest` (insertion, the held frame, the drag bounds, joins, the viewer's clock);
`integration/VideoRenderTimeTest` — 1 s of film and a 1 s freeze render as 2 s and decode.
`./gradlew.bat :shared:build` green. **Not yet by hand.** Known: in the viewer, Pause during a
hold does not pause it — the player is already paused there, so the button reads Play.
