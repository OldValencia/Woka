# Plan: Animated stills actually animate

An animated GIF or WebP does not move anywhere in Woka. It is decoded, counted and drawn — one frame
at a time. Raised 2026-08-20, while asking what "editing an animated GIF" could possibly mean.

Roadmap: Phase 4 (the viewer). Not part of the media editor, and **ME-138 waits on this** — a format
whose animation the app cannot show is not a format worth teaching the app to edit.

## What exists already, and it is most of it

The frame-set work (`features/implemented/media-viewer/`, Phase 4) built everything except the clock:

- `SkiaFrameDecoder` reads every frame of a GIF or WebP and **its duration** — `ImageFrame.durationMs`
  is already non-null for animation and null for a burst, which is exactly the distinction a player
  needs to know whether to play at all.
- `ImageLoader.load(file, frameIndex)` decodes any one of them, `ImageBitmapCache` keys them apart,
  and `MediaViewerState.frameIndex` already drives which one is on screen.
- `AppliedStillEdits` folds a recipe over whatever frame is decoded (ME-133), so an animation that
  plays through that path plays *edited* with no further work.

What is missing is a timer, a decision about who owns it, and a decode-ahead so the next frame is
ready before it is due.

## Scope

**In:** GIF and WebP animation in the **viewer**, play/pause, and the frame strip staying in step with
the frame on screen. The grid tile: decided below, not assumed.

**Out:** APNG (no decoder), animated AVIF (FFmpeg decodes it; its timing is untested), speed control,
scrubbing, and anything that writes an animation back to disk.

## Phase 1: The clock

- Add a player that owns `frameIndex` for a set whose frames carry `durationMs`: advance on the
  frame's own duration, wrap at the end, and stop when the viewer's item changes.
- Decode-ahead of one frame through `ImageLoader`, so a 40 ms frame is not waiting on a decode.
- **Where it lives is the first decision**, not an implementation detail: `MediaViewerState` already
  holds `frameIndex` and `MediaViewerScreenEffects` already owns the decode, so the timer is likely a
  third effect beside them rather than a new subsystem.

**Files:** `feature/viewer/shell/MediaViewerScreenEffects.kt`, `feature/viewer/shell/MediaViewerState.kt`

## Phase 2: The controls

- Play/pause where the viewer already puts transport controls, shown only for a set that has
  durations — a burst and a multi-page TIFF must not grow a play button.
- The frame strip follows the playing frame without stealing the selection from a user who is
  stepping frames by hand.

**Files:** `feature/viewer/shell/frames/FrameStrip.kt`, the viewer's toolbar

## Phase 3: The grid, or not

An animating grid is a decision about the whole folder view, not about GIF: twenty tiles playing at
once is twenty decode loops. Coil can do it — `coil-gif` is one dependency and it is not on the
classpath today — so the question is whether it should, and whether it stops at hover.

Nothing here is blocked by leaving the grid still.

## Decisions

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-08-20 | Is editing an animated GIF worth building before this? | **No — ME-138 is deferred until an animation plays** | The recipe would apply to every frame the app draws, and the app draws one frame. Editing a picture nobody can watch is a feature whose result is invisible until this lands |
| 2026-08-20 | Does animated **WebP** wait too? | **No — its carrier shipped in ME-137 and stays** | A WebP is overwhelmingly a still photograph, so the carrier earns its place on the common case; the animated ones inherit the same limit GIF has, and this feature removes it for both at once |
