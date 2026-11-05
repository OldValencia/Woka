# ME-216 — Speed per clip

| | |
|---|---|
| **Order** | after ME-215; promoted and built 2026-11-04 |
| **Depends on** | ME-215 (clip keys, the clip bar) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** A clip plays at ¼×, ½×, 1×, 2× or 4×, in the editor, in the viewer and in a render.
**Why now.** `next_steps.md` 1.0.6: *скорость/стоп-кадр*. Folds in ME-234's speed half.

## Shape — the author's choices, 2026-11-04

- **In the clip bar**, beside Delete, Duplicate and Join: `1× ▾`, five speeds. Not a slider in
  Trim's inspector.
- **A field on `TrimClip`**, not an op: it changes the clip's length, and the strip is drawn
  from the list alone (`timeline.md` §3.2). `lengthMs` is the timeline length (source span ÷
  speed); `sourceLengthMs` is the span. Every timeline↔source mapping divides or multiplies by
  it — `ClipTimeline`, `EditedClock`, `StripGeometry.xOf`, the edge drag, the filmstrip slots.
- **Split keeps the speed; Join refuses two speeds** — one clip has one speed.

## Built

- Model: `TrimClip.speed`, `CLIP_SPEEDS`, `withSpeed`; `SourceRange.speed` and `lengthMs` for the
  viewer; chapters on the result divide by it.
- Strip: `ClipSpeedChip` in `ClipBar`; `ClipBadges` (split from `ClipTile`) shows `2×`/`½×`.
- Playback: `VideoPlayerState.setClipSpeed` — 0.25–4×, never persisted, apart from the viewer's
  own 0.5–2× control. The editor's walk sets it per clip; the viewer multiplies the user's speed
  by the range's and gives the user's back when the film is left. `atempoChain` halves twice
  for ¼×, since one `atempo` stops at 0.5.
- Render: any speed other than 1× encodes. `RenderPace` places each frame at `base + offset /
  speed` and drops one that rounds onto a taken slot; sound goes through `atempoChain`.

## Second round — 2026-11-04, by hand

- **4× played at 1× after `Done`, and at 4× after Revert; right only after leaving and coming
  back.** Two effects wrote `playbackSpeed` — the viewer's settings sync and the edit's — and
  whichever ran last won. Now one value of two inputs: `setPlaybackSpeed` sets the user's,
  `setClipPace` the clip's, and the player runs at their product. `integration/ClipPaceTest`
  asserts both orders and the revert.
- **Five speeds were too few** (1.25×, 1.5× wanted). The chip opens a panel: the quick values
  ¼ ½ 1 1.5 2 4×, and a logarithmic `DetentSlider` from ¼× to 4× in steps of 0.05, sticking
  to ¼ ½ ¾ 1 1.25 1.5 2 3 4× (`snapClipSpeed`). Right-click on it is 1×.

## Third round — 2026-11-04, from the author's log

4× → 1× → 4× still played at 1× until the viewer was reopened. `[woka-speed]` logs showed the
viewer's frame composed **twice** over one player for a moment after `Done`: the new copy set
4×, then the old copy's dispose reset it to 1×. The pace now has an owner —
`setClipPace(pace, owner)` / `releaseClipPace(owner)` — and only the last setter can reset it.
`ClipPaceTest` replays the log. The quick speeds are centred in the panel.

## Verified

`unit/ClipSpeedTest` (length, both clocks, split/join, the filter chain);
`integration/VideoRenderTest` — two seconds at 2× render as one, and decode.
`./gradlew.bat :shared:build` green. **Not yet by hand** — and not measured: whether a speed
change at a join clicks (`playback.md` §4's open question).
