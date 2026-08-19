# Plan: Animated stills actually animate

An animated GIF or WebP does not move anywhere in Woka. It is decoded, counted and drawn — one frame
at a time. Raised 2026-08-20, while asking what "editing an animated GIF" could possibly mean.

Roadmap: Phase 4 (the viewer). Not part of the media editor, and **ME-138 waits on this** — a format
whose animation the app cannot show is not a format worth teaching the app to edit.

**Scoped 2026-08-20 by the person who asked for it.** The frame strip stays exactly as it is — the
frames are worth seeing and worth stepping through by hand. What is added is a clock: the frames
advance by themselves, at the speed the file declares, looping if the file says to loop, and a click
on the picture pauses and resumes.

## What exists already, and it is most of it

The frame-set work (`features/implemented/media-viewer/`, Phase 4) built everything except the clock:

- `SkiaFrameDecoder.frameSet` reads every frame of a GIF or WebP **and its duration** —
  `ImageFrame.durationMs` is already non-null for animation and null for a burst, which is exactly the
  distinction that decides whether a file plays at all.
- `MediaViewerState.frameIndex` already says which frame is on screen, and the decode effect in
  `MediaViewerScreenEffects` is already keyed on it: moving the number *is* changing the picture.
- `ImageLoader` caches each frame under its own key, so a second pass around the loop is cache hits.
- `mediaTapGestures` already routes a single tap to play/pause — for a video. A still gets nothing.
- `AppliedStillEdits` folds a recipe over whatever frame is decoded (ME-133), so an animation plays
  *edited* the day ME-138 lands, with no further work.

What is missing: the repetition count (skia has it, nothing reads it), a timer, and the two states —
playing or paused — that the timer runs on.

## Scope

**In:** GIF and WebP animation in the viewer; loop as the file declares it; click the picture to
pause and resume; stepping a frame by hand pauses.

**Out:** the grid (a folder of tiles is twenty decode loops — its own decision, Phase 3 below), the
PiP and fullscreen windows, APNG (no decoder), animated AVIF (FFmpeg decodes it, its timing is
untested), speed control and scrubbing, and anything that writes an animation back to disk.

## Phase 1: The file says how often to repeat

- `ImageFrameSet`: add `repeatCount: Int?` — **skia's meaning, carried unchanged**: `-1` is forever,
  `0` is play once, `n` is that many extra passes. Null when the decoder does not answer.
- `SkiaFrameDecoder.frameSet`: fill it from `Codec.repetitionCount`. The other two decoders leave it
  null: `ImageIoStillDecoder` counts TIFF pages, which are not an animation, and `FfmpegStillDecoder`'s
  frame sets are bursts.
- `FrameStripModel`: carry it, and add `isAnimated` — true when a frame declares a duration. A burst
  and a multi-page TIFF must answer false and stay exactly as they are today.

**Files:** `core/data/ImageFrameSet.kt`, `feature/viewer/image/SkiaFrameDecoder.kt`,
`feature/viewer/shell/frames/FrameStripState.kt`

## Phase 2: The clock

- `MediaViewerState`: add `isFramePlaying`, true by default — an animation opens playing, which is
  what every viewer that shows one does.
- `feature/viewer/shell/frames/FramePlayback.kt` (new): a `@Composable` effect keyed on the file, on
  `isFramePlaying` and on the frame set. While playing: `delay` the current frame's own duration,
  then advance `state.frameIndex`, wrapping at the end. A pass that wraps counts against
  `repeatCount`; when the passes run out it stops on the **last** frame rather than the first, which
  is where a non-looping animation is supposed to end.
- A frame with no duration, a duration of zero and a set with one frame all mean "do not run" — the
  effect returns before the first delay, so nothing changes for a photograph.

**Files:** `feature/viewer/shell/frames/FramePlayback.kt` (new),
`feature/viewer/shell/MediaViewerState.kt`, `feature/viewer/shell/MediaViewerPlayer.kt`

## Phase 3: Pause, by clicking the picture

- `mediaTapGestures`: the single tap already toggles a video. Give it the still case — toggle
  `isFramePlaying` when the frame set is animated, and leave a photograph's tap doing nothing at all.
- Stepping a frame in the strip sets `isFramePlaying = false`: reaching for a frame by hand is asking
  the animation to stand still, and an animation that walks away from the frame just chosen would be
  the only surprising thing here.

**Files:** `feature/viewer/shell/MediaViewerTapGestures.kt`,
`feature/viewer/shell/MediaViewerPlayerModifier.kt`, `feature/viewer/shell/MediaViewerPlayer.kt`

## Built 2026-08-20 — phases 1 to 3

All three landed together; the grid (phase 4 below) is untouched and still draws frame 0.

Two things learned on the way, both worth carrying:

- **`ImageFrameSet.isAnimated` is "a frame declares a duration"**, and that one predicate is what
  keeps a burst and a page chain exactly as they were: they have frames and no timings, so the clock
  returns before its first delay and nothing about them changes.
- **A test of a clock has to move the clock.** The first version of `FramePlaybackUiTest` waited on
  real time while the effect's `delay` ran on `runComposeUiTest`'s scheduler, and timed out against a
  player that worked. `waitUntil` and `mainClock.advanceTimeBy` are what drive it; a `Thread.sleep`
  proves nothing here.

## Phase 4: The grid, or not

An animating grid is a decision about the whole folder view, not about GIF: twenty tiles playing at
once is twenty decode loops. Coil can do it — `coil-gif` is one dependency and it is not on the
classpath today — so the question is whether it should, and whether it stops at hover.

Nothing above is blocked by leaving the grid still.

## Verification

- `jvmTest/unit/`: the repetition count maps skia's `-1` to "forever" and `0` to "once"; a set whose
  frames carry no duration is not animated.
- `jvmTest/integration/`: `anim.gif` and `anim.webp` report eight frames **with** durations and a
  repetition count; `burst.heic` and `multipage.tif` report frames **without** either.
- `jvmTest/ui/`: a playing set advances its frame index over virtual time and stops advancing when
  paused; a single frame never advances; a tap toggles; selecting a frame by hand pauses.
- By hand: open a GIF, watch it loop, click to pause, click to resume, step a frame in the strip and
  see it stay put.

## Decisions

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-08-20 | Is editing an animated GIF worth building before this? | **No — ME-138 is deferred until an animation plays** | The recipe would apply to every frame the app draws, and the app draws one. Editing a picture nobody can watch is a feature whose result is invisible until this lands |
| 2026-08-20 | Does animated **WebP** wait too? | **No — its carrier shipped in ME-137 and stays** | A WebP is overwhelmingly a still photograph, so the carrier earns its place on the common case; the animated ones inherit the same limit GIF has, and this feature removes it for both at once |
| 2026-08-20 | Does the frame strip stay? | **Yes, unchanged** | Asked for directly. The frames are the honest content of the file, and being able to stop on one is what a viewer is for; the animation is added *beside* that, not instead of it |
| 2026-08-20 | What pauses it? | **A click on the picture**, and stepping a frame by hand | The tap already means play/pause for a video, so the gesture is learned; a second control for the same idea would be a second thing to find |
