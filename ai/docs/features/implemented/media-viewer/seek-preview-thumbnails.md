# Plan: Seek-bar preview thumbnails

**Status: complete.** All six phases shipped and verified by hand against the 2160p HDR test file:
the sweep updates as the pointer moves, the exact frame lands after it settles, and playback shows
no load from any of it.

Three things landed differently from the text below and are marked inline: the load flag went onto
`PlaybackGeneration` rather than a new `PlaybackLoad`; Phase 6 needed four splits rather than two;
and the pill takes a `Painter` rather than an `ImageBitmap`, which is what makes HDR previews carry
the same tone curve as the picture — see Phase 4. The last one was a real bug, found on the first
HDR run and fixed, not a change of mind.

Hovering the seek bar shows the time it would jump to. Add the frame at that time above it, the way
YouTube does: a small still, the chapter title and the timestamp in one pill.

## What it costs — measured, not assumed

Probed against `Человек-паук Вдали от дома.2019.IMAX.WEB-DL.2160p.HDR.mkv` (3840×2160 HEVC, 74 GB,
2h09m) with a throwaway `FFmpegFrameGrabber`, five positions scattered across the film:

| configuration | grabber open | seek + one frame |
|---|---|---|
| full probe, native output | 59 ms | 161–355 ms |
| capped probe (2 MB / 1 s), native output | **4 ms** | 155–271 ms |
| capped probe, downscaled output | 4 ms | 155–270 ms |
| capped probe, downscaled, `skip_frame=nokey` | 3 ms | 117–156 ms |

Opening a second grabber is free once the probe is capped — the streams are already known from the
player's own grabber. One preview costs ~200 ms. That number is the whole design constraint.

## Why the first draft of this plan was wrong

It debounced 120 ms and decoded only once the pointer **settled**. That optimises the wrong moment:
the preview exists to help someone *searching* for a place, and a searching pointer is moving. A
design that shows nothing until the hand stops is a design that shows nothing when it is wanted.

The correction, and it is how YouTube actually behaves: **a coarse grid of frames carries the sweep,
and the exact frame refines it once the pointer settles.** A film gets a fixed budget of grid frames
— a couple of hundred, whatever its length — so sweeping the bar is mostly cache hits, and the
debounce is what buys the precise frame rather than what gates the first one.

---

## The two tiers

**Coarse — carries the sweep. No debounce.** A debounce is a delay, and a moving pointer wants an
answer now. What makes the coarse tier cheap is not a timer but quantisation: the timeline is cut
into cells, a cache hit renders with no latency at all, and a miss queues exactly one decode. The
rate limiter is the decode itself — about five a second — not a clock.

**Exact — refines the answer.** 120 ms after the pointer settles, decode the precise position and
replace the cell frame. That is the only thing the debounce gates, and nothing waits on it.

**The cost cap, which is the part that keeps the app alive.** Both tiers share **one dedicated
single-threaded dispatcher**, so there is never more than one decode in flight, and only while the
pointer is on the bar. Coarse requests conflate — a newer one replaces the pending one rather than
queueing behind it — so a fast sweep skips the cells it flew over instead of decoding every one.
Exact requests go through `mapLatest`, so a pointer that moves again cancels the refine outright.
Leaving the bar cancels the in-flight refine and clears anything pending.

**Prefetch.** When the decoder is idle and the pointer has been moving in one direction, pull the
next cell along. It uses only the slot that would otherwise sit empty, so the sweep starts running
ahead of the cursor at no cost.

**Nothing is decoded before it is asked for.** There is no pass at file-open that fills the grid.
Cells are decoded under the pointer and only under the pointer — see "Not doing".

### Previews always yield to playback

A single dispatcher bounds *how many* decodes run, not *how much* they take. One preview thread still
competes with the playback decoder for cores, so it is held below it three ways:

1. **Thread priority.** The dispatcher gets its own thread factory at `Thread.MIN_PRIORITY`, so the
   OS hands quanta to playback first.
2. **FFmpeg thread count.** The preview grabbers open with `threads=2`, not `auto`. Each preview gets
   slower; it stops taking every core away from 4K HEVC. That is the intended trade.
3. **Back off under load.** The pacer already counts `skipsInARow` and the presenter `droppedInARow`.
   Surface that as a load flag on the player and skip preview decoding while it is raised, plus
   unconditionally while `isReloadingVideo` — a track swap is the moment the decoder can least afford
   company. The pill keeps showing whatever is cached, and the timestamp regardless.

### Preview resolution

Decoded at **240×135**, displayed in a 160×90 dp box. Those two numbers are the same number: at 150%
Windows scaling a 160×90 dp box is 240×135 physical pixels, so the still is pixel-for-pixel with no
waste and no softness.

The temptation to decode something "proper" like 720p is worth pricing, because it is not close:

| | per cell | 300 cells |
|---|---|---|
| 240×135 | 130 KB | 39 MB |
| 1280×720 | 3.7 MB | **1.1 GB** |

— before counting the extra swscale time on every one of them. This is a thumbnail above a
timestamp, not a viewer.

### Cell size

A fixed step behaves badly at both ends — 10 s over a four-hour film is 1440 cells and 187 MB, while
30 s over a ten-minute clip is twenty frames for the whole bar. So the step is derived and clamped:

```
stepMs = (durationMs / TARGET_CELLS).coerceIn(5_000, 30_000)   // TARGET_CELLS = 300
```

| film | step | cells | cache |
|---|---|---|---|
| 10 min | 5 s | 120 | 16 MB |
| 60 min | 12 s | 300 | 39 MB |
| 2h09m (test file) | 26 s | 300 | 39 MB |
| 4 h | 30 s | 480 | 62 MB |

At 240×135 RGBA a cell is 130 KB. The cache is an LRU capped at 512 entries — enough to hold any
grid this formula can produce, so within one file a cell is decoded at most once.

### Memory only, never disk

Storyboards are **not** persisted. A few MB per film, accumulating for every film ever opened, that
only pays off on a rewatch that mostly never happens, is not a trade worth making — and it takes the
cache-invalidation problem with it.

The cost of that decision, stated so it is not a surprise: the grid is re-decoded each time a file is
opened. In practice it is small. Conflation means a full sweep of the bar in three seconds decodes
about fifteen cells, not three hundred — only the ones the pointer lingered near.

---

## Phase 1: Grid arithmetic ✅

- Add `feature/viewer/video/engine/preview/SeekPreviewGrid.kt`: `stepMs`, `cellOf(positionMs)`,
  `cellMidpointMs(cell)`.
- `stepMs = (durationMs / 300).coerceIn(5_000, 30_000)`, per the table above.
- Decode at the cell **midpoint**, never at the raw pointer position, or sliding inside one cell
  would produce two different stills for the same cell.
- Pure arithmetic, no Compose and no FFmpeg — so the clamping is unit-testable on its own, which is
  where the edge cases live (zero duration, a file shorter than one step).

**Files:** `video/engine/preview/SeekPreviewGrid.kt`, `jvmTest/.../SeekPreviewGridTest.kt`

## Phase 2: The decode source ✅

- Add `feature/viewer/video/engine/preview/SeekPreviewSource.kt`. One class, constructed twice:
  `SeekPreviewSource(file, keyframesOnly = true)` for the coarse tier and `keyframesOnly = false` for
  the exact one. Each owns an `FFmpegFrameGrabber` opened lazily on first request, released on
  `close()`; both are driven from the same single-threaded dispatcher, so two sources still means one
  decode in flight.
- Two sources rather than one because the difference is measured, not cosmetic: `skip_frame=nokey`
  puts a coarse cell at 117–156 ms against 155–271 ms, which on a sweep is seven updates a second
  instead of four. The sweep is the primary scenario. The exact tier cannot use it — it has to land
  on a non-keyframe — hence the pair. Cost is one extra file handle and 4 ms to open it.
- Open the way the audio grabber does — `probesize=2000000`, `analyzeduration=1000000` — plus
  `pixelFormat = AV_PIX_FMT_RGBA`, `audioStream = Int.MAX_VALUE`, `threads=2` (see the load section),
  and `imageWidth`/`imageHeight` at 240 px wide with the stream's aspect applied.
- `suspend fun frameAt(positionMs: Long): ImageBitmap?` — `setTimestamp`, `grabImage`, convert with
  `Bitmap.installPixels` + `setImmutable`, **not** `toComposeImageBitmap` (see
  `video-memory-leak-plan.md` Part 1).
- Previews are not registered with `FrameRetention`: they are retained by the cache, not by the play
  head, and that rule is about frames the presenter published.

**Files:** `video/engine/preview/SeekPreviewSource.kt`

## Phase 3: The controller ✅

- Add `feature/viewer/video/engine/preview/SeekPreviewController.kt` holding the cell cache
  (`LinkedHashMap` LRU, 512 entries), a second 16-entry LRU of exact frames so returning to a spot
  just inspected is instant, the coarse request channel (`Channel.CONFLATED`), the exact request flow
  (`MutableStateFlow` → `debounce(120)` → `mapLatest`), and the idle prefetch.
- Every decode is gated on the player's load flag and on `isReloadingVideo`; when either says no, the
  request is dropped rather than queued — a preview the pointer has moved past is worthless.
- `fun request(positionMs: Long?)` from the hover callback — null means the pointer left, which
  cancels the refine and clears the pending coarse request.
- `fun previewFor(positionMs: Long): ImageBitmap?` returns the exact frame when it still matches the
  asked position, else the cell frame, else null.
- Prefetch: track the sign of the last two requested cells; when the coarse channel is empty and no
  decode is running, request `cell + direction`.
- Add `rememberSeekPreview(file, durationMs)` in the same package returning the controller, with
  `DisposableEffect` closing the source — the grabber must not outlive the viewer, and the cache must
  not outlive the file.

**Files:** `video/engine/preview/SeekPreviewController.kt`

## Phase 4: The pill ✅

- `MediaSliderTooltip` takes `preview: ImageBitmap?`. When the caller supplies previews at all,
  reserve the 160×90 dp box from the first frame and fade the image in — growing the pill on arrival
  makes it jump under the cursor.
- HIG styling: 8 dp radius, 1 dp border, `ContentScale.Crop`, clipped, scrim while empty.
- Nothing is shown before the first decode lands — not the film's poster, which has no relation to
  the hovered position and would simply lie. Empty scrim, image fades in.
- **The pill takes a `Painter`, not an `ImageBitmap`.** Found on the first HDR run: a preview is
  decoded straight off the stream, so its pixels are PQ-encoded exactly like a played frame, and a
  plain `BitmapPainter` reads them as sRGB — flat, washed-out previews beside a correctly tone-mapped
  picture. `rememberPreviewPainter` picks `HdrImagePainter` from `VideoPlayerState.streamHdrInfo`
  (the stream's own description, stable per track, so no recomposition at frame rate) and
  `BitmapPainter` otherwise. Keeping the choice on the video side is also what stops the shared
  slider from learning what a transfer function is.
- `MediaSlider` takes `tooltipPreview: ((Float) -> ImageBitmap?)? = null` beside `tooltipText` and
  passes the result down. The slider stays generic — it is shared and must not learn what a video is.
- The preview follows **both** hover and drag. The pill already does (`pillX = hoverX ?: dragX`), and
  a real scrub is where a preview is wanted most.
- `MediaViewerTimeSlider` wires the controller into that parameter and calls `request()` from it.

**Files:** `shell/slider/MediaSliderTooltip.kt`, `shell/slider/MediaSlider.kt`,
`video/MediaViewerTimeSlider.kt`

## Phase 5: The playback-load signal ✅

- `FramePacer` exposes `isFallingBehind` — `skipsInARow >= 2`, because one late frame is ordinary
  jitter and a background job gated on that would flicker on and off constantly. The decode loop
  publishes it; the presenter raises the same flag after three consecutive drops and both clear it
  when a frame lands on time.
- The preview controller reads it, and `isReloadingVideo`, before every decode.
- **Landed differently:** it went onto `PlaybackGeneration` rather than a new `PlaybackLoad` class.
  A separate class would have needed a property on `VideoPlayerState`, which was at 148 lines and
  would have had to be split for one field. `PlaybackGeneration` is already the object the playback
  coroutines publish cross-thread state through, is reachable as `player.generation`, and had room.
  The stretch is that it is otherwise all counters, so the field carries a comment saying so.

**Files:** `video/engine/PlaybackGeneration.kt`, `video/engine/decode/VideoPlayerFramePacer.kt`,
`video/engine/decode/VideoPlayerDecoder.kt`, `video/engine/decode/VideoPlayerPresenter.kt`

## Phase 6: The file-size splits this forces ✅

Predicted two, needed four — the preview parameters cost more lines than estimated:

- `MediaSlider.kt` (146): pill block out to `shell/slider/SliderPill.kt`, which also took the
  position callback, since that is the slider's one outward-facing side effect. **148.**
- `MediaViewerTimeSlider.kt` (150): the two identical skip buttons out to
  `video/MediaViewerSkipButton.kt`. **143.**
- **Unplanned:** `MediaSliderTooltip.kt` reached 169 with the frame window inside it, so the window
  went to `shell/slider/SliderPreviewWindow.kt`. **129.**
- **Unplanned:** `MediaSlider.kt` still sat at 153 after the first split, so `preview` and
  `previewAspect` became one `SliderPreview` value — which reads better anyway, and is public
  because `MediaSlider` is.

**Files:** `shell/slider/SliderPill.kt`, `shell/slider/SliderPreviewWindow.kt`,
`video/MediaViewerSkipButton.kt`

---

## Open questions

1. **Cold start on a slow disk.** ~200 ms was measured on NVMe with the file partly in the OS cache.
   On a spinning disk or a network share the first pass over a region will be slower. The design
   degrades correctly — the pill shows text for longer — but if it turns out to be seconds, the
   answer is a coarser grid, not a change to any of the above.
2. **Burst / frame-set files.** This covers video only. Whether a multi-picture still file wants the
   same treatment is a separate decision.

## Not doing

- **Persisting storyboards to disk.** See above — permanent clutter for a rewatch that mostly does
  not happen.
- **Sprite sheets generated ahead of time.** Right for a server that transcodes once and serves a
  million viewers; here it means a full decode pass over a 74 GB file before the first hover.
- **A background pre-pass filling the whole grid.** 300 cells × 200 ms is a minute of decoding
  competing with playback for no benefit — the on-demand grid already makes a second pass over the
  same region instant, and the first pass is exactly where the user is looking.
- **Aligning cells to chapter boundaries.** `SliderSegment` already knows where the cuts are, so a
  cell could be kept from straddling one. It is real complexity for a difference nobody would notice
  at a 12–26 s step.
- **Reusing the playback grabber.** It is the master clock; seeking it to serve a hover stops the
  picture.
