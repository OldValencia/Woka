# ME-240 — Mute, volume and fades

| | |
|---|---|
| **Order** | first in `audio/`; promoted and built 2026-11-04 — the only audio in 1.0.6 (the author) |
| **Depends on** | ME-215 (This Clip, clip layers) |
| **Feature** | [Audio](../audio.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Mute a film or a clip, change its volume, fade it in and out — heard in the editor and
the viewer, and in a render.

## Shape — `audio.md` §3.2, as written

- **`SoundTool`: a manifest and four specs, no UI code** — Mute (`BoolParam`), Volume 0–200 %
  (detent 100), Fade in and Fade out 0–10 s. The inspector is the schema's. It passed the
  design's acceptance test: nothing in the shell changed for it.
- **A new `ToolDomain.Sound`, appended** so no picture's fold changes (`ToolContractTest` pins it).
  Its stage is a `SoundStage`, which the raster fold drops.
- **All Clips** is the film's: its fades sit at the film's two ends. **This Clip** is the clip's:
  its fades at the clip's. The two **multiply** (`soundGain`), and `SoundMix` — plain data in
  `core` — carries both to the viewer without naming the tool.

## Built

- Player: `EditPlayback` groups the edit's pace and gain apart from the user's; the audio loop plays
  `volume × edit.gain`. The gain has an owner, for the same two-copies reason the pace has.
- Editor: `MixClipSound`; viewer and PiP: `PlayEditedSound` — the gain set on every position the
  player reports (not every UI frame: that kept the window busy and broke `VideoBandUiTest`), and
  nothing at all for an edit whose sound is as recorded.
- Render: sound decoded as interleaved 16-bit and scaled per sample (`RenderSound`); any sound edit
  encodes. `RenderEncoder` split from `FrameRender` for size.

## Verified

`unit/SoundMixTest`; `integration/ClipPaceTest` (the gain's owner); `integration/VideoRenderTimeTest`
— a muted film renders silent where the source is not. `./gradlew.bat :shared:build` green.
**Not yet by hand** — in particular whether a fade sounds smooth: the gain moves once per reported
position (a frame), not per sample, in the preview.
