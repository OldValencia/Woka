# ME-301 — An HDR film is tone mapped to its own white point

| | |
|---|---|
| **Order** | second of the round: ME-237 → **ME-301** → ME-304 → ME-305 |
| **Depends on** | ME-237 (`FreshFrameGrabber`: the side data read here must be the frame's own) |
| **Feature** | [The shared editor shell](../shell.md) |

**Goal.** Replace the assumed 1120-nit white point of every HDR video surface with the one the
file declares — MaxCLL, else the mastering display's peak — read once, from the first decoded
frame, and checked for plausibility.

**Why now.** The backlog block asked one question first: is there a stable per-file peak, so it
is one number rather than a per-frame measurement? **Probe, 2026-11-05** (`VideoHdrPeakProbe`,
17 PQ files in `D:/Movies`, bundled FFmpeg 8.1.2):

- **Not in the container** of any of them — it is in the HEVC SEI, and the decoder attaches it to
  every `AVFrame` (`AV_FRAME_DATA_CONTENT_LIGHT_LEVEL`, `…_MASTERING_DISPLAY_METADATA`), at the
  start and after a seek. A container that does carry it (MP4 `clli`/`mdcv`, MKV `Colour`) reaches
  the frame the same way: FFmpeg 8 copies stream side data onto every decoded frame — measured on
  `rot90.mp4`'s display matrix. **So the frame is the only place this reads.**
- **Stable:** identical at 0, 5, 15, 25 and 40 min in every file.
- **Truthful on this corpus:** MaxCLL against the frames' own 8-bit peak, a frame every 20 s —
  S02E02 227 / 253, S02E05 273 / 283, Green Book 717 / 679, The Boys 913 / 1010. (Approximate:
  swscale converts BT.2020 with the wrong matrix, so saturated colours overshoot.) MDCV is 1000
  on all 17.

What the author sees: the Avatar episodes (MaxCLL 227–273) were divided by 1120 nits and drawn
dim; they come up to their real brightness. The Boys (~900) barely moves. Every surface — player,
PiP, ambient fill, seek preview, hover reel, the editor's canvas and thumbnail, and the render —
takes the same number, because one that disagrees is a preview that lies (ME-165). That is why
this is one ticket of fifteen files rather than a per-surface split (`tickets.md` §4).

**Files from other people** is why the number is checked rather than trusted — the author's
decision, 2026-11-05. **Not built:** correcting a lying MaxCLL from the pixels. Every file
measured is honest, so the correction could not be verified (`uncertain-heuristics` rule).

## Steps

1. `HdrInfo` gains `val whitePoint: Float? = null` — units of SDR white, **null = not read
   yet** (the codec parameters alone). Add `fun HdrInfo.toColour() = StillColorInfo(transfer,
   isWideGamut, whitePoint)` in the same file.
2. `StillColorInfo` gains `val whitePoint: Float? = null`. KDoc: set for a video frame only; a
   still's white point is measured at its decode (ME-120) and never travels here.
3. New `feature/viewer/video/engine/probe/HdrPeak.kt`:
   - `internal fun plausibleWhitePoint(maxCll: Int, maxFall: Int, masteringNits: Double?): Float`.
     MDCV counts when 100 ≤ nits ≤ 10000. MaxCLL counts when 0 < MaxCLL ≤ 10000 and
     (MaxFALL == 0 or MaxCLL ≥ MaxFALL); then it is `min(MaxCLL, MDCV ?: MaxCLL)`. Else MDCV.
     Else `ASSUMED_WHITE_POINT`. Result `/ 100f`, `coerceAtLeast(1f)` — `HdrWhitePoint`'s rule:
     under SDR white the curve would expand.
   - `internal fun AVFrame.declaredWhitePoint(): Float` — reads both side data types
     (`AVContentLightMetadata`, `AVMasteringDisplayMetadata` with `has_luminance`) and calls the
     above.
   - `internal fun HdrInfo.readFrom(picture: AVFrame?): HdrInfo` — `this` when `whitePoint` is set,
     when `transfer == SDR`, or when `picture` is null; otherwise
     `copy(whitePoint = picture.declaredWhitePoint())`.
4. `FreshFrameGrabber` exposes `val lastPicture: AVFrame?` — the `AVFrame` behind the last picture
   it returned, **in both modes** (RAW: the decoder's `picture`), plus
   `internal val FFmpegFrameGrabber.lastPicture get() = (this as? FreshFrameGrabber)?.lastPicture`
   for callers typed as the base class. KDoc: valid until the next grab — a seek's landed frame is
   a clone whose `opaque` is not an `AVFrame`, which is why the grabber is asked and not the frame.
5. The player fills it. `VideoPlayerExt.kt`: new `fun VideoPlayerState.showColour(hdr: HdrInfo?)`
   setting `currentFrameHdrInfo = hdr` and, when `hdr?.whitePoint != null && hdr != streamHdrInfo`,
   `streamHdrInfo = hdr` — so the stream's colour gains its white point in the same snapshot as
   the first frame drawn with it, and never changes after. `publishFrame` and
   `VideoPlayerPresenter.kt:128` call it instead of assigning `currentFrameHdrInfo`.
   `VideoPlayerDecoder.kt`: inside `if (frame.image != null)`,
   `hdrInfo = hdrInfo.readFrom(grabber.lastPicture)` (149 → 150 lines). `videoSeekTo` publishes
   `hdrInfo?.readFrom(grabber.lastPicture)`, so a paused resume lands at the right brightness
   instead of changing on play.
6. The readers: `ViewerFramePainter` — `frameColorInfo` uses `toColour()`, `rememberFramePainter`
   passes `whitePoint = color.whitePoint ?: ASSUMED_WHITE_POINT`. `SeekPreviewPainter` passes
   `hdr.whitePoint ?: ASSUMED_WHITE_POINT`. `AmbientSampler.publish` uses `toColour()`.
   `HoverExcerptReel` reads `hdr.readFrom(grabber.lastPicture)` after the first span's first grab
   and builds the excerpt's colour from that.
7. `hdrStageFor` takes `StillColorInfo?` instead of `HdrInfo?` and puts
   `color.whitePoint ?: ASSUMED_WHITE_POINT` in the last uniform; its KDoc's "ME-301's probe"
   paragraph is replaced by this ticket's finding. Callers: `RecipeVideoEdits.painter` passes
   `color` straight through (the `HdrInfo(...)` rebuild goes), `VideoEditorEngine` passes
   `streamColour?.toColour()`.
8. The render. `RenderSource.withGrabber` constructs a `FreshFrameGrabber`; `probeSource` reads
   `readStreamHdrInfo()` and, when its transfer is not SDR, grabs one image and applies
   `readFrom(grabber.lastPicture)` — one decoded frame per render, on HDR sources only.
   `VideoRender` passes `probe.hdr.toColour()`.
9. Tests:
   - `jvmTest/unit/HdrPeakTest.kt` — `plausibleWhitePoint`: (717, 168, 1000) → 7.17;
     (0, 0, 1000) → 10; (227, 300, 1000) → 10 (MaxCLL under MaxFALL); (4000, 0, 1000) → 10
     (capped); (0, 0, null) → `ASSUMED_WHITE_POINT`; (0, 0, 50.0) → `ASSUMED_WHITE_POINT`;
     (60, 0, null) → 1.0. `declaredWhitePoint` on an `av_frame_alloc()` frame with
     `av_content_light_metadata_create_side_data` / `av_mastering_display_metadata_create_side_data`
     filled — 717/1000 → 7.17, nothing → `ASSUMED_WHITE_POINT`. `readFrom` leaves an SDR
     `HdrInfo` and a resolved one untouched.
   - `RasterSourceStageTest` follows the signature change; one case asserts the stage's last
     uniform is the colour's white point.
   - By hand: S02E02 next to The Boys in the player — the first brighter than before, the second
     unchanged — and the same S02E02 frame in the editor and in its render.

## Files

`.../viewer/video/engine/probe/HdrInfo.kt` · `.../viewer/video/engine/probe/HdrPeak.kt` (new) ·
`commonMain/.../core/data/StillColorInfo.kt` · `.../viewer/video/engine/decode/FreshFrameGrabber.kt` ·
`.../decode/VideoPlayerExt.kt` · `.../decode/VideoPlayerPresenter.kt` · `.../decode/VideoPlayerDecoder.kt` ·
`.../sync/VideoPlayerSeek.kt` · `.../viewer/shell/ViewerFramePainter.kt` · `.../viewer/video/SeekPreviewPainter.kt` ·
`.../viewer/shell/ambient/AmbientSampler.kt` · `.../video/engine/preview/HoverExcerptReel.kt` ·
`.../editor/engine/video/VideoHdrStage.kt` · `.../video/RecipeVideoEdits.kt` · `.../video/VideoEditorEngine.kt` ·
`.../video/render/RenderSource.kt` · `.../video/render/VideoRender.kt` ·
`jvmTest/.../unit/HdrPeakTest.kt` (new) · `jvmTest/.../unit/RasterSourceStageTest.kt`

## Done when

- `HdrPeakTest` green; `ASSUMED_WHITE_POINT` is read only as the fallback — grep shows no reader
  passing it unconditionally;
- `VideoPlayerDecoder.kt`, `HoverExcerptReel.kt`, `VideoPlayerPresenter.kt` ≤ 150 lines;
- the anti-FQN grep is clean on every edited file; `./gradlew.bat :shared:build` green;
- S02E02 and The Boys looked at by the author.

## Built — 2026-11-05

**Step 5 moved out of the decode loop.** `VideoPlayerDecoder.kt` sat at 149 lines, so the read
went where a picture leaves for the screen instead: `colourOf(hdr, grabber)` in
`FrameDelivery.kt`, called by `deliverPicture`, `JoinStep.send` (with the grabber that decoded
the picture — the lane's after a hand-off) and `videoSeekTo`. Two side-data lookups per picture,
and the player, a lane and a seek all read from the one place that decoded the frame.
**One case the ticket missed:** a freeze re-sends the frame already on screen, and it went out
with the stream's colour before any white point — a brightness jump at every freeze. It now
re-sends `currentFrameHdrInfo`.

Declared white points through the code (`VideoHdrPeakProbe`): Green Book 7.17, Avatar S02E01
7.37, S02E02 / E06 / E07 2.27, S02E05 2.73, The Boys S05E01 9.13 — the same at the start and at
10 min. Unit tests green (`HdrPeakTest`, `RasterSourceStageTest`); **not yet looked at by the
author**.
