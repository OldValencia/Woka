# ME-232 — Joins without a stall

| | |
|---|---|
| **Order** | first build ticket in `playback/`, after ME-230's measurements (2026-11-04); ME-233 and ME-235 follow |
| **Depends on** | ME-230 (measured), ME-231 (timeline time), ME-216 / ME-217 (speed and freeze exist) |
| **Feature** | [Playback](../playback.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** A cut film plays its kept ranges as one continuous picture — in the editor's canvas, the
viewer and PiP — by handing off to a decoder that has already paid the next range's seek.
**Why now.** Every join today is a seek: **0.2–1.9 s** of frozen picture on a 4K film (ME-230,
`playback.md` "Measured"), which is what the author refused on 2026-09-03. The sound across a join
still re-seeks after this ticket — a short gap, not a frozen picture — and is ME-233's.

## Shape — measured, and the author's, 2026-11-04

- **A pool of decoder lanes, up to four in all, each `threads=4`.** Four at four threads cost
  1.1–1.3 GB, about one and a half of today's single `threads=auto` decoder, and the slowest still
  decodes 4K at 41 fps with all four flat out (`DecoderPoolProbe`).
- **The player's own decoder drops to `threads=4` too**, edit or not: ~0.5 GB less on every 4K film,
  87 vs 91 fps on S02E01 and 48 vs 67 on Green Book, against 24 needed. Author's call.
- **A free lane pre-rolls at once, not at a computed lead.** The lead is the keyframe distance —
  0.3 s on one film, 2.2 s on another — so a timer would be a guess per file. A lane that is free
  takes the earliest upcoming range nobody holds, seeks, and parks holding its first two frames.
  Lanes are opened only for ranges starting within [`LEAD_HORIZON_MS`] of the play head, and closed
  after [`IDLE_CLOSE_MS`] unused, so an uncut film never holds more than the one decoder.
- **The engine owns the list and the hand-off.** Today the join is noticed in Compose, a frame after
  the presenter showed the last kept frame, while the decoder runs on into the cut. The decode loop
  instead stops at the range's end on its own thread and swaps grabbers there — the same move it
  already makes for a video-track reopen. `ClipWalk` and `PlayEditedRanges` stop driving the player
  and only read where it is, which is what both their KDocs promised ME-232 would do.
- **Speed and freeze go with the list.** A range's pace is applied at the hand-off, not by a
  `LaunchedEffect` a frame later; a freeze holds on the decoder thread with `isPlaying` left true, so
  Pause during a hold pauses it — the known defect noted in ME-217 goes with it.

## Steps

1. **Decoder threads.** `VideoPlayerGrabber.kt`: a `const val DECODE_THREADS = "4"` with the ME-230
   numbers in its KDoc; `openVideoGrabber` sets it in place of `"auto"`. Add `openLaneGrabber(startMs)`
   beside it: the same options, `audioStream = Int.MAX_VALUE`, `renderScale.applyTo`, RAW when
   `pictureFilter.active`. Never called before `tracksProbed` completes (`playback.md` §3.2).
2. **Landing, shared.** `sync/VideoPlayerSeek.kt`: extract the backoff loop and `decodeForwardTo`
   from `videoSeekTo` into `internal fun VideoPlayerState.landAt(grabber, filter, targetMs): Frame?`,
   taking the `PictureFilter` as a parameter. `videoSeekTo` calls it with `pictureFilter` and keeps
   its channel drain and `publishFrame`; behaviour unchanged.
3. **A lane.** New `engine/join/DecoderLane.kt`: owns one grabber, its own `PictureFilter` (new
   `PictureFilter.mirrorOf(main)` copies `turns` and `deinterlaceEnabled` and opens a graph at the
   lane's size), the range index it holds, and `parked: List<VideoFrame>` (≤ 2, converted through a
   lane-own `FramePacer.convert`, registered with the player's `FrameRetention`). `preRoll(range)`
   runs on `Dispatchers.IO`: `landAt(range.fromMs)`, convert, grab one more, park. `close()` stops
   and releases. A lane whose `renderScale.decodeWidth` or selected video stream no longer matches
   re-opens and pre-rolls again while parked.
4. **The pool.** New `engine/join/LanePool.kt`: `const val MAX_LANES = 3` (plus the playing
   grabber), `LEAD_HORIZON_MS = 3_000L` (over the 2.2 s worst lead), `IDLE_CLOSE_MS = 10_000L`.
   `plan(ranges, playingIndex, timelineMs)` assigns free lanes to the earliest upcoming ranges in the
   horizon not held, opening lanes up to [MAX_LANES]; `takeParked(index): DecoderLane?`;
   `giveBack(grabber, filter)` makes the outgoing grabber an idle lane; `closeAll()`. One pool per
   `VideoPlayerState`, created lazily, closed in the decode loop's `finally`.
5. **The list on the player.** New `engine/EditRanges.kt` (public API, `VideoPlayerState` stays at
   149 lines through one `internal val edit = EditPlaybackState()` line): `playRanges(ranges:
   List<SourceRange>, owner: Any)`, `releaseRanges(owner)` (the `setClipPace` owner rule, for the
   same reason), `seekInRanges(index, intoMs)` — a source seek for a range, a hold offset for a
   freeze — and the readers `rangeIndex: Int` and `heldMs: Long`, snapshot state written through
   `writeUiState`. An empty list is today's player, untouched.
6. **The hand-off in the loop.** New `engine/join/JoinStep.kt`, called from `startDecoderLoop` in one
   line after a frame is grabbed (`VideoPlayerDecoder.kt` stays ≤ 150): a frame at or past the
   playing range's `toMs` is not delivered; the next range's parked lane, if any, swaps in —
   `grabber`, `pictureFilter` contents (`PictureFilter.adopt(lane.filter)`), its parked frames sent
   with `join = true` and the current generation — and the old grabber goes back to the pool. No
   parked lane: the old path, `videoSeekTo(next.fromMs)`. A freeze range sends its one frame, then
   counts `holdMs` down in 20 ms steps while `isPlaying`, publishing `heldMs` every 40 ms. Past the
   last range: `isPlaying = false` on its last frame. Every hand-off applies `setClipPace(range.speed,
   edit)` and bumps a new `PlaybackGeneration.audioJoin` counter with the next source position.
7. **The presenter at a join.** Split first: move the end-of-stream and pause-parking branches of
   `startPresenterLoop` into `decode/PresenterParking.kt` (the file is 148). Then `VideoFrame` gains
   `val join: Boolean = false`; a join frame re-anchors the clock to `now + one frame period / speed`
   — not `now`, which would show it a frame early — and sets `currentPositionMs` unconditionally,
   since a join may go back by under the 1 s the forward-only rule lets through.
8. **Sound follows.** `audio/VideoPlayerAudioDecoder.kt`: on `audioJoin` change, `audioReseek` to
   its target without waiting on `isVideoAtSeekTarget` (the video did not seek). The gap this leaves
   is ME-233's to close.
9. **The walks read.** `ClipWalk`: `index`, `heldMs`, `holding` read `player.edit`; `onPosition` and
   `tick` go; `seekTo` → `seekInRanges`; `toggle` → `isPlaying` only. `VideoEditorBand` calls
   `playRanges(clips.map { it.toSourceRange() }, walk)` keyed on the list — the mapping
   `RecipeVideoEdits` writes inline today, moved into a `TrimClip.toSourceRange()` extension beside it — drops its
   `withFrameMillis` hold loop and its pace line. `PlayEditedRanges`: `playRanges(ranges, owner)` /
   `releaseRanges(owner)`, mirrors `rangeIndex` and `heldMs` into `EditedClock`; its end rules, hold
   loop, `enter` and `holdOn` go. `ResultClock.seekOnResult` → `seekInRanges`.
10. **Layout.** `CLAUDE.md`: `join/` under `video/engine/` — "decoder lanes, pool, hand-off".

## Files
`viewer/video/engine/decode/VideoPlayerGrabber.kt`, `sync/VideoPlayerSeek.kt`,
`decode/PictureFilter.kt`, `decode/VideoPlayerDecoder.kt`, `decode/VideoPlayerPresenter.kt`,
`decode/PresenterParking.kt` (new), `PlaybackGeneration.kt`, `VideoPlayerState.kt`,
`audio/VideoPlayerAudioDecoder.kt`, `EditRanges.kt` (new), `EditedPlayback.kt`,
`join/DecoderLane.kt` (new), `join/LanePool.kt` (new), `join/JoinStep.kt` (new),
`viewer/video/ResultClock.kt`, `editor/engine/video/ClipWalk.kt`, `editor/engine/video/VideoEditorBand.kt`,
`editor/engine/video/RecipeVideoEdits.kt`,
`CLAUDE.md`. Nineteen, past §4a's eight: the smallest slice the author can see is "the join does not
freeze", and it needs the lane, the loop that swaps to it and both walks that stop seeking — a lane
nothing hands off to shows nothing.

## Done when
- `unit/LanePoolTest` — `plan` opens lanes only inside the horizon, never more than three, prefers
  the earliest upcoming range, gives a returned grabber the next one, closes idle lanes after the
  timeout (fake clock, fake lanes).
- `integration/JoinHandOffTest` — on a generated 4 s clip with a keyframe every 2 s (so a seek pays
  decode-forward): three ranges cut off keyframes, played from the start; the presented frame
  timestamps advance by one frame period across every join (no gap over 2 periods), no frame from
  a cut stretch is presented, and the play ends paused at the last range's end. A freeze range holds
  its frame for its `holdMs` ± 1 period and Pause during it stops the count.
- `EditedClockTest`, `EditedPlaybackUiTest` and the editor's walk tests still pass, rewritten to read
  the engine where they drove the walk.
- **By hand, the author:** S02E01 cut into five ranges in the editor and in the viewer — no frozen
  picture at any join.
- `./gradlew.bat :shared:build` green; the anti-FQN grep clean on every edited file.

## Built — 2026-11-05

Where the build left the steps, read against the tree:

- **One parked picture per lane, not two.** The grabber is left just after it and the next frame
  decodes in 11–25 ms against a 41 ms frame; a second would hold 8–33 MB a lane for nothing.
- **State lives in `EditPlayback.joins`** (`EditJoins`, in `EditRanges.kt`) — `VideoPlayerState`
  is untouched — and so does the `audioJoin` counter, which kept `PlaybackGeneration` at its size.
- **The presenter owns what is on screen**: a join frame (`VideoFrame.joinIndex`) applies the
  range's pace before it is scheduled, then its index, the play head and the audio join once shown.
  **A freeze holds in the presenter**, not the decode loop — the loop runs up to eight frames ahead
  of the screen, so a hold counted there started early. The loop meanwhile sits on a full channel,
  so `holdEnded` makes it rebase its `FramePacer`, which would otherwise skip four frames after
  every freeze.
- Splits for the size mandate: `VideoFrame` moved to `decode/PresenterJoins.kt`, `startPlayback` and
  a new `startVideoGrabber` to `VideoPlayerDecodeStart.kt`, `relocate`/`seekToStart` to
  `join/RangeLocator.kt`, the no-lane fallback `landHere` to `join/LanePool.kt`. `PictureFilter`
  gained `mirror` and `swapGraphWith` (the ticket's `mirrorOf` / `adopt`).
- `unit/LanePoolTest` covers the planner as pure functions (`upcomingStarts`, `continues`) rather
  than through fake lanes. `integration/JoinHandOffTest`: a lane parks on its range's first frame;
  three ranges cut off keyframes play in order with no frame from a cut and no still picture over
  200 ms; a freeze holds 1 s ± 0.2; Pause in it stretches it by the pause. A temporary trace showed
  all six joins in those tests taken from a lane, landing exactly on the range start; removed.

**Not yet by hand.** Not checked: the 4K joins on S02E01 the ticket names, and how a lane opening
for the first time competes with playback on a slower machine.
