# ME-140 — The editing canvas plays what the viewer plays

| | |
|---|---|
| **Order** | **done 2026-08-21** — raised, built and confirmed by hand the same day |
| **Depends on** | ME-103, ME-138, and [animated stills](../../../implemented/animated-stills.md) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** A GIF that animates in the viewer animates in the editor. `Edit` should not be the
one place in the app where a moving picture is a still one.

**Why it happened.** [Animated stills](../../../implemented/animated-stills.md) gave the *viewer* a clock and
ME-138 gave GIF a recipe, so an animation could suddenly be edited — and the canvas it was edited on
was `remember(file, frameIndex) { EditorProxy.load(file, frameIndex) }`, one frame decoded once and
held for the session. Nothing was wrong with it before there was anything to play.

## Three things, and only one of them was visible

Found together, because they are the same seam:

1. **The canvas held one frame.** The bug as reported.
2. **The editor always opened on frame 0.** `EditorTarget(id)` was constructed without a frame at
   both doors, while the viewer had `state.frameIndex` in its hand. Stopping an animation on frame 5
   and pressing `Edit` gave frame 0 — and after this ticket it would have given frame 0 *playing*,
   which is worse.
3. **The viewer under the editor was not frozen**, whatever `FolderScreen`'s comment said. The editor
   mounts over it and leaves it composed, so its clock kept advancing and decoding frames behind an
   opaque panel for as long as the editing lasted — and handed back a different frame from the one
   `Cancel` was supposed to return to.

## Decisions

| Question | Answer | Why |
|---|---|---|
| Does the canvas play by default? | **Yes, and a click pauses it** | The gesture already means that one panel below, on the same file. A second control for the same idea would be a second thing to find |
| Who owns "start or stop"? | **`EditorEngine.togglePlayback()`**, defaulted to nothing | The shell must never name a bitmap or a player (§3.3). A frame clock today, the video editor's player at 5b, and a photograph's engine answers by doing nothing — so the gesture is unconditional and the canvas learns no medium |
| Are frames decoded when due, like the viewer's? | **No — the whole animation is held for the session** | The viewer can decode per frame because `ImageBitmapCache` catches the result. `EditorProxy` is deliberately outside that cache (a 100 MP photograph is 384 MB and would evict everything), so a canvas decoding per frame would re-read the file ten times a second forever |
| What stops that from eating memory? | **A 96 MB budget, refused before anything is decoded** | Estimated from the native size and the frame count *after* the proxy cap, so no allocation happens to find out. Over it, the canvas draws its still frame exactly as it did before — the file is still editable, it just does not move |
| How are the frames read? | **One forward walk, with skia's `priorFrame`** | `SkiaFrameDecoder.frame` refuses to assume an access pattern and rebuilds the chain per call, which is right for a strip walked in both directions and quadratic for loading all of them. A hundred-frame GIF is five thousand decodes that way and a hundred this way |
| Two clocks? | **No — `playFrames` is one loop in `core/data`** | The repeat arithmetic, the wrap and the minimum delay were about to exist twice. The viewer drives `MediaViewerState.frameIndex` with it and the editor a local index; what is left in each is where the number lives |

## Two more from the same by-hand pass

Both are the same complaint as the animation, one level down: **the canvas did not behave like the
viewer on the same file.** Both were found by opening a transparent GIF.

- **The backdrop was black.** `EditorScreen` painted `Color.Black` while §4.2 has always said
  "full-bleed over the viewer's existing backdrop" — which is `Theme.colors.background`. Invisible
  for as long as every picture tested was opaque; a transparent PNG or GIF sits on the app's own
  surface one panel below and on a black plate here. Now on the same surface as everywhere else.
- **Fit was the floor as well as the resting place.** The editor was the one panel where a picture
  could be enlarged and never shrunk. `EditorCanvasGeometry.minZoom` now comes from the viewer's
  `zoomBounds` — reused deliberately, because it takes two sizes and answers a range of percentages
  and names no `SizeMode` — so the canvas zooms out to a tenth of Fit, or to 1:1 where Fit already
  magnifies past 1000 %, exactly as the viewer does. The wheel notch is the viewer's
  `ZOOM_STEP_RATIO` too. `Reset` still returns to Fit and the ceiling settled on 2026-08-18 is
  untouched.

## Files

`core/data/FrameClock.kt` (new — the shared loop) · `feature/viewer/image/SkiaFrameWalk.kt` (new) ·
`feature/editor/engine/image/EditorAnimation.kt` (new) · `.../EditorAnimatedProxy.kt` (new) ·
`feature/editor/shell/EditorCanvasGestures.kt` (new — the size mandate, when the click arrived) ·
`feature/editor/core/EditorEngine.kt` · `.../engine/image/ImageEditorEngine.kt` · `.../EditorProxy.kt` ·
`feature/editor/shell/EditorCanvasHost.kt` · `feature/viewer/shell/frames/FramePlayback.kt` ·
`.../frames/FrameStripState.kt` · `feature/viewer/shell/MediaViewerScreen.kt` ·
`.../MediaViewerPlayer.kt` · `.../MediaViewerPlayerFrames.kt` · `feature/viewer/NativeMediaViewer.kt`
(both halves) · `feature/folder/FolderScreen.kt` · `feature/viewer/image/SkiaFrameDecoder.kt`
(`onCodec` widened to `internal`)

## Done when

- `jvmTest/unit/FrameClockTest.kt`: the loop over virtual time — order, wrap, a set with no timings,
  a single frame, `repeatCount = 0` stopping on the last frame, a numbered count spent one pass at a
  time, a finished animation rewinding when started again, and a duration-less motion entry never
  played.
- `jvmTest/integration/ForwardFramesTest.kt`: **every frame of `anim.gif` and `anim.webp` is pixel
  for pixel what an independent read gives**. This is the one that matters — handing skia a prior
  frame is how a disposal method quietly produces a plausible wrong picture. Plus: the editor loads
  an animation and refuses a photograph, a burst and a page chain.
- `jvmTest/ui/EditorCanvasPlaybackUiTest.kt`: a click on the canvas asks the engine, twice for two
  clicks, and composing it asks nothing.
- `jvmTest/ui/FrameStripUiTest.kt`: the frame handed to the editor is always one the file holds — a
  still frame is itself, the motion entry and an index out of range fall back to the first.
- `jvmTest/unit/EditorCanvasZoomRangeTest.kt` (split from `EditorCanvasGeometryTest` under the size
  mandate): the ceiling at both ends of the fit scale, and the floor — a tenth of Fit, 1:1 for a
  picture the canvas hugely enlarges, never above Fit, never zero.
- `jvmTest/ui/CanvasPillUiTest.kt`: the pill's floor is now below Fit, the canvas actually goes
  there, and `Reset` returns to Fit rather than to the floor.
- By hand: open a GIF, `Edit`, watch it run on the canvas; click to pause; rotate it and watch it
  run rotated; `Cancel` and land back on the frame the viewer was on. A transparent one sits on the
  app's surface, not on black, and zooms out past Fit like it does in the viewer.
- `./gradlew.bat :shared:build` green.
