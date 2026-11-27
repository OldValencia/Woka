# RD-501 — One transport and the GIF

| | |
|---|---|
| **Order** | ninth, after RD-401; RD-502 (carousel badges and Original) follows |
| **Depends on** | RD-101 (theme), RD-201 (the room), RD-204 (the frame strip in both colour sets), RD-301 (the timeline's round play button) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Give the viewer one transport bar for a film, a track and an animation (`#viewer/video`,
`#viewer/track`, `#viewer/gif`): the same left group and seek, per-kind right slots. Give a GIF
in the editor the same bar under the picture and the frame strip, in the room's colours
(`#editor/gif`). Make `,` / `.` step one frame.

**Why now.** Today a GIF has no transport at all. It can be paused only by a click on the picture or
Space. It cannot be stepped, scrubbed, slowed or stopped from looping, and its time is shown nowhere.
In the editor a GIF plays with no strip and no bar, so the one picture to edit against cannot be
held deliberately. The film's bar already has the prototype's slots in the prototype's order
(`MediaViewerTimeSlider`), but not its look: no round play button, no glass, the time label
breathes. `,` / `.` exist nowhere in the app.

## Decided with the author (2026-11-27)

- **The bar's UI is the prototype's for now.** The author has their own ideas for reworking it later.
  That is not RD-501.
- **Loop opens On, every time, for every animation.** It is not remembered. The file's own repeat
  count is not read by the clock: a GIF is a looping picture. On means forever. Off means the clock
  stops on the last frame at the end of the pass it is in. Play from the last frame with Loop off
  starts from the first. (`ImageFrameSet.repeatCount` stays: `bakeGif` writes it into a copy.)
- **Speed is the video's slider** (`PlayerSpeedSlider`, 0.5–2×, detent at 1×) in a popover, but
  the **animation's own** value. It opens at 1× for every file, in the viewer and in the editor,
  and is never carried from one to the other or remembered. Video's `playbackSpeed` is untouched.
  Keeping a GIF's speed as an edit (in its recipe, baked into a copy) is **1.1.0** (`next_steps.md`).
- **Loop On colours:** the viewer's accent on `accentSoft`. In the editor, the bar takes the
  room: Loop On `room.accentText` on `room.accentFill`, the seek's played part `room.accent`, the play
  button the timeline's (`room.well2`, `room.knob` on hover, `room.text`). The prototype drew
  accent-on-`accentEditorSoft` in both, which is an accident of the generic `button.selected`.
  It is corrected first. `accentEditorSoft` is therefore not drawn by this ticket.
- **Time labels stay 15 sp** (`timeLabelStyle`; 13 read as a caption beside the icons). The
  prototype moves from 12 px to 15 px, and the leading label's fixed width from 48 px to 60 px.
- **Entering the editor from a GIF carries the frame, play/pause, Loop and the time/frame-number
  choice**, as the prototype's one clock does. Speed is not carried: it starts at 1×. From the grid
  the editor opens playing, Loop On, 1×, time. Leaving the editor returns to the viewer's own state,
  as today (`FramePlaybackEffect`'s `isFrozen`).

## Taken from the prototype as drawn (not asked)

- **Slots** (`transport.js` `TRANSPORT`): film: play, time, −5, seek, +5, duration, subtitles,
  volume, settings, fullscreen. Track: the same without subtitles and fullscreen. Animation: play,
  time, ❙◀, seek, ▶❙, duration, loop, speed. The app's film and track bars already match this order
  and keep their conditions (`SkipButton` from 6 s, subtitles only with tracks, `canFullscreen`).
- **Animation time** reads `m:ss.d` (`clipTime`: `0:01.6`). A click on the leading label switches
  it to `n / N` and back. The trailing label is the length, not clickable. The switch resets to
  time when a file of another kind is shown. It is kept from one animation to the next.
- **Step** (❙◀ ▶❙, `,` `.`) pauses, then moves one frame, clamped at the ends, with no wrap. A drag or
  a click on the seek pauses and shows that frame. Its tooltip names the frame as the label does,
  over that frame's thumbnail.
- **`,` / `.`**: an animation always; a film only while paused, and nothing while it plays. The same
  in the editor: a GIF always, and a film while paused (the prototype's `stepFrame` is global).
  Ignored while a text field has focus, as every shortcut here is.
- **Every animation, not only `.gif`**: the bar shows for whatever `FrameStripModel.isAnimated` /
  `EditorFrames.playsAsAnimation` call an animation (GIF, animated WebP). A burst or a page chain
  keeps its strip and gets no bar, and `PageUp`/`PageDown` keep stepping it.
- **In the editor** (`editor.css` `.gif-media`): the bar sits under the picture, 32 dp from the
  sides and 20 dp from the bottom. It stands on `room.panel` with a 1 dp `room.line` border, no
  glass and no shadow. The picture's area gives up 94 dp at the bottom. The strip stands top-left
  as for a frame set and stops 100 dp above the bottom. There is **no scope line** (ME-138). A click
  on a frame pauses there and changes nothing else.

## The spec, measured (`viewer.css`, `base.css`, `editor.css`)

| Part | Value |
|---|---|
| Bar | 48 dp high, radius 16, `colors.glass`, padding 0 × 12, gap 10, shadow `0 2 18` black 6.7 % |
| Icon buttons | 28 dp, glyph 18 dp |
| Play | 32 dp circle, glyph 15 dp; viewer `surfaceRaised`, `surfaceHover` on hover (`button:hover`), `textPrimary` |
| Leading time | 15 sp tabular, **min width 60 dp** — the seek no longer moves as digits change |
| Loop | off `textSecondary`; on — viewer `accent` on `accentSoft`, editor `room.accentText` on `room.accentFill` |
| Seek | `MediaSlider` as today; editor: `activeColor`/`thumbColor` = `room.accent` |

The bar's outer placement and the carousel under it are unchanged: the carousel is RD-502's.
`PopoverAnchor` offsets are measured from the bar's right edge, so they are re-measured after step 5.

## Steps

1. **Prototype first.** In `viewer.css`, set `.playback-time` to 15 px and `#viewer-time` to
   `min-width: 60px`. Set `#viewer-loop.selected` to `accent` on `--accent-soft`. Under
   `.app-shell.editing .viewer-playback` (`editor.css`), give `.transport-play` `--e-well-2` /
   `--e-knob` / `--e-text`. Give `#viewer-loop.selected` `--e-accent-text` on `--e-accent-fill`,
   and the seek's fill `--e-accent`. In `transport.js`, the speed popover becomes a 0.5–2 range,
   not a select, with its own `gifSpeed` separate from `viewerSpeed`. `gifSpeed` resets to 1 on
   every animation shown and on entering or leaving the editor. `frameClock.loop` resets to
   `true` on every animation shown. Check with `node ai/mockups/tools/shot.mjs` at 1024 × 700, both
   themes, `#viewer/gif`, `#editor/gif`, `#viewer/video`, `#viewer/track`.
2. **The clock takes a speed.** `playFrames` (`core/data/FrameClock.kt`) gains
   `speed: () -> Float = { 1f }`, read before each delay: `delay(delayMs / speed())`. The private
   `delayMs()` becomes public `ImageFrame.frameDelayMs()`, the one rule the labels count with.
3. **`FrameTransport`** (new, `core/ui/components/frames/FrameTransport.kt`, commonMain) is a `@Stable`
   interface: `frames: List<ImageFrame>`, `var frameIndex`, `var isPlaying`, `var loop`,
   `var speed`, `var showFrameNumbers`. Extension functions on it:
   - `playable` returns the frames with a duration.
   - `positionMs()` and `durationMs()` are the sums of `frameDelayMs()`.
   - `step(delta)` pauses and clamps within `playable`.
   - `seekTo(index)` pauses.
   - `togglePlaying()`.
   `repeatCountFor(loop)` returns `-1` or `0`.
4. **One play knob.** `TimelineTransport`'s private `PlayButton` moves to
   `feature/viewer/video/PlayKnob.kt` as `PlayKnob(playing, colors: PlayKnobColors, tag, onToggle)`.
   It gets `viewerPlayKnob()` (surfaceRaised / surfaceHover / textPrimary) and `roomPlayKnob()`
   (well2 / knob / text). The timeline calls it with `roomPlayKnob()` and keeps `editor-timeline-play`.
5. **The film's bar restyled.** `MediaViewerVideoControls` takes the table's geometry and `PlayKnob`
   (`viewer-transport-play`). The fullscreen button moves out into `MediaViewerFullscreenButton.kt`,
   which keeps the file under 150 lines. `TimeLabel` takes `Modifier.widthIn(min = 60.dp)`.
   Re-measure `PopoverAnchor.Subtitles/Volume/Settings` against the new right edge.
6. **The animation's bar.** Add `FrameTransportBar(transport, colors: TransportColors, loadPreview,
   onSpeedHover, modifier)` in `feature/viewer/video/FrameTransportBar.kt`. The slot widgets go in
   `FrameTransportParts.kt`:
   - the leading label (`formatTenths` / `n / N`, click toggles);
   - the step buttons;
   - loop, and the speed button (`EditorIcons.Speed`).
   Add `formatTenths(ms)` (`m:ss.d`) to `MediaViewerTimeLabel.kt`.
   The seek is a `MediaSlider` over `playable`. Its tooltip preview is `loadPreview(frame)` painted
   at that bitmap's aspect. `TransportColors` carries the bar fill, border, shadow, knob, loop and
   slider style, as `viewerTransport()` / `roomTransport()`. Add Phosphor `repeat`, `skip-back` and
   `skip-forward` (regular, as `gauge.svg`) to `drawable/`, as `Icons.Static.Repeat`, `FrameBack`
   and `FrameForward`. Add `FrameSpeedPopover(transport)`, which wraps `PlayerSpeedSlider`.
7. **Viewer wiring.**
   - `MediaViewerState` gains `isFrameLooping = true`, `frameSpeed = 1f`,
     `showFrameNumbers = false` and `isAnimation = false`.
   - The file-change effect (`MediaViewerScreenEffects`) resets looping and speed beside
     `isFramePlaying`.
   - `MediaViewerPlayerFrames` publishes `isAnimation` with `frameCount`, and clears
     `showFrameNumbers` when it is false.
   - `rememberViewerFrameTransport(state, strip)` (in `frames/`) adapts the state to `FrameTransport`.
   - `FramePlaybackEffect` passes `repeatCountFor(state.isFrameLooping)` and `{ state.frameSpeed }`,
     and is keyed on looping. `FrameStripModel.repeatCount` is removed, since nothing reads it.
   - `MediaViewerToolbar` takes `frameTransport: FrameTransport?` and draws `FrameTransportBar`
     where a non-playable item has one.
   - `HoverTarget.Speed` and `PopoverAnchor.FrameSpeed` place `FrameSpeedPopover` in
     `MediaViewerPlayerOverlays`.
8. **`,` / `.`**
   - **Viewer.** `MediaViewerKeyHandler` sends `Key.Comma` / `Key.Period` to a new `onFrameKey(dir)`
     (`mediaViewerPlayerModifier`). An animation calls `transport.step`. A paused film calls
     `stepVideoFrame(player, clock, dir)`. That is a new function in `viewer/video/VideoFrameStep.kt`:
     `stepPicture` from `shownFrameMs`, read on the result timeline (`EditedClock.toTimeline`), then
     `seekOnResult(seekTargetOf(next))`. `VideoPlayerState.frameRate()` and its fallback move from
     `ClipWalk.kt` to `viewer/video/engine/`.
   - **Editor.** `EditorKeys` sends the same keys to `onFrameStep`, wired in `EditorHost` to the new
     `EditorEngine.stepFrame(direction): Boolean = false`. The image engine steps its transport; the
     video engine calls `walk.step(dir, bySecond = false)` only while paused.
9. **The editor's clock.**
   - Add `FrameClockStart(playing = true, loop = true, frameNumbers = false)`, and make it the
     third field of `EditorTarget` (`EditorEntry.kt`).
   - The viewer's `onEdit` becomes `(EditorTarget) -> Unit`: `NativeMediaViewer` in both source
     sets, `MediaViewerHandlers` (which fills it from the state) and `FolderScreen`.
   - `EditorEngineFactory.create(path, frameIndex, clock)` reaches `ImageEditorEngine`.
   - There, `EditorPlayback` becomes the `FrameTransport`, seeded from the frame index and the
     clock, at speed 1×.
   - `rememberEditorFrame` uses `playback.frameIndex` instead of its local `index`, sets
     `playback.frames` when the animation lands, and runs `playFrames` with the loop's count and
     speed.
   - `EditorEngine.frameTransport: FrameTransport? = null`. The image engine answers its playback
     when `frames.playsAsAnimation`.
10. **The editor's band.** `EditorFrameBand(perFrame, engine, file)` keeps the frame set's strip.
    With no `perFrame` and a `frameTransport` that has frames, it draws `EditorFrameStrip` with
    `selectedIndex = frameIndex` and `onSelectFrame = seekTo`, `FrameTransportBar(roomTransport())`
    with the paddings above, and `FrameSpeedPopover` from a local `PopoverHoverState`. In
    `EditorScreen` the call loses its `if`, so the file does not grow. The canvas inset gains a
    bottom of 94 dp for the bar (`canvasInsetFor` returns `PaddingValues`, `EditorCanvasLayer`).

## Files

`ai/mockups/design/woka/{viewer.css, editor.css, transport.js}` (edit);
`core/data/FrameClock.kt`, `core/ui/components/frames/FrameTransport.kt` (new);
`feature/viewer/video/{PlayKnob, FrameTransportBar, FrameTransportParts, FrameSpeedPopover,
VideoFrameStep, MediaViewerFullscreenButton}.kt` (new);
`feature/viewer/video/{MediaViewerVideoControls, MediaViewerTimeSlider, MediaViewerTimeLabel}.kt`,
`feature/viewer/shell/{MediaViewerState, MediaViewerScreenEffects, MediaViewerPlayer,
MediaViewerPlayerFrames, MediaViewerPlayerOverlays, MediaViewerToolbar, MediaViewerKeyHandler,
MediaViewerPlayerModifier, MediaViewerHandlers, PopoverHoverState, ToolbarPopover}.kt`,
`feature/viewer/shell/frames/{FramePlayback, FrameStripState}.kt` + `ViewerFrameTransport.kt` (new),
`feature/viewer/NativeMediaViewer.kt` (common + jvm), `feature/folder/FolderScreen.kt`;
`feature/editor/EditorEntry.kt`, `feature/editor/core/{EditorEngineFactory,
EditorEngineSurface}.kt`, `feature/editor/engine/EditorEngineFactoryImpl.kt`,
`feature/editor/engine/image/{ImageEditorEngine, EditorAnimatedProxy}.kt`,
`feature/editor/engine/video/{TimelineTransport, ClipWalk, VideoEditorEngine}.kt`,
`feature/editor/shell/{EditorFrameBand, EditorScreen, EditorCanvasLayer, EditorKeys, EditorHost}.kt`;
`core/ui/icons/Icons.kt`, `composeResources/drawable/{repeat, skip_back, skip_forward}.svg` (new).
Every file is measured with `wc -l` before it is touched. `EditorHost` (149) and
`MediaViewerScreen` (150) are at the ceiling: a split is its own step when a line is added.

## As built (2026-11-27)

Where the build left the steps, and why:
- **The bar's look lives in `TransportStyle.kt`** (`TransportTokens`, `TransportColors`,
  `viewerTransport()` / `roomTransport()`, `Modifier.transportBar`), shared by the film's bar
  and the animation's. The film's other slots (`SubtitleControl`, `MediaViewerVolumeControl`,
  `PlayerSettingsControl`) took the 28 / 18 dp sizes from it. `PopoverAnchor` re-measured:
  Subtitles 134, Volume 96, Settings 58, and the new `FrameSpeed` 20.
- **The viewer publishes `MediaViewerState.frameTransport`** (not an `isAnimation` flag): `,` /
  `.` step through it (`MediaViewerFrameKey.stepOneFrame`). Both of the viewer's key paths,
  the window-wide hook and the player's own handler, are one call now
  (`viewerKeyedModifier`, in `MediaViewerShortcutEffect.kt`). That was the split
  `MediaViewerScreen` (150) needed for its new import.
- **The editor's `,` / `.` go through `EditorEngine.timelineKey`**, which joins
  `EditorKeys.TIMELINE_KEYS`, and not through a new `stepFrame` / `onFrameStep`. `EditorHost` is
  at 149, and the slot already means "a key the medium's time owns". The image engine steps
  its transport. The film's `TimelineKeys` step while paused and leave the key while playing.
- **Only the canvas runs the editor's clock** (`rememberEditorFrame(runsClock)`). A style tile
  is the same call, and with the index shared each tile's own loop would have advanced it
  again. Tiles now show the canvas's frame. `EditorAnimation.repeatCount` went with
  `FrameStripModel.repeatCount`.
- **Tests:** speed is `unit/FrameClockSpeedTest` and Loop is `ui/FrameLoopUiTest` (both split
  out to keep the files under 150). `integration/VideoFrameStepTest` starts Koin, as
  `VideoSeekTest` does: without it the player never decoded a picture. That was seen in the
  log, not guessed.
- **Prototype:** the speed popover is a 0.5–2 range with the 1× detent, on `gifSpeed`. Loop,
  playing and speed reset for every animation shown, and speed also on entering or leaving
  the editor.

**After the author's first look (2026-11-27):**
- **The speed popover drew its slider over the word Speed.** `PlayerSpeedSlider` emits a header
  and a track as siblings for the ⚙ menu's column, and the popover gave it a `Box`.
- **GIF speed has its own scale** (the author): 0.25–5×, two log halves with 1× at the centre,
  pulled onto 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2 / 3 / 4 / 5× (`FrameSpeedScale`,
  `FrameSpeedSlider`). A drag is applied at once and a right-click returns to 1×. The film's
  0.5–2× slider is unchanged. The prototype maps its range the same way.
- **`,` / `.` on the Russian layout.** AWT names the punctuation keys by what they type, so on
  the Russian layout the `<` / `>` keys arrive as `б` / `ю` with no `Key.Comma` /
  `Key.Period`. `core/system/LayoutFreeKeys.kt` recognises them by that character, from
  Compose's code point or AWT's extended key code. Letters keep their `VK_` codes on every
  layout, so the other shortcuts were never affected.
- `FrameTransportUiTest` starts Koin itself. It had passed only because another test in the
  same run had started it.

## Done when

- `#viewer/gif`, `#editor/gif`, `#viewer/video` and `#viewer/track` match the corrected prototype
  in both themes (shot.mjs computed styles: bar, knob, loop, time width).
- `unit/FrameClockTest` checks that 2× halves and 0.5× doubles the delays. `unit/FrameTransportTest`
  checks `positionMs`/`durationMs` with zero-delay frames (the 100 ms rule), that `step` clamps and
  pauses, that `seekTo` pauses, and `formatTenths`.
- `ui/FramePlaybackUiTest` is rewritten for the new rules. A GIF with a finite repeat count loops
  with Loop On. Loop Off stops on the last frame. Play from there starts at frame 1. Its old
  finite-pass cases go.
- `ui/FrameTransportUiTest` (new) covers the viewer on a GIF fixture: the bar shows, and no bar
  shows for a burst. ❙◀ ▶❙ and `,` `.` pause and move. The label toggles. Loop and speed reset to
  On / 1× on the next animation.
- `ui/EditorGifTransportUiTest` (new) covers the editor:
  - Opened from a paused viewer at frame 5 with Loop Off, it opens paused on 5 with Loop Off and
    speed 1×.
  - A strip click pauses there.
  - There is no scope line, and the document is unchanged.
  - `,` steps.
- `integration/VideoFrameStepTest` (new) checks, on a film fixture, that a paused `.` lands on the
  next picture (`shownFrameMs`) and that a playing `.` does nothing.
- The anti-FQN grep is clean on every edited `.kt`. No `.kt` is over 150 lines.
- `./gradlew.bat :shared:build` is green.
