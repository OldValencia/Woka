# ME-305 — An HDR10+ film is tone mapped scene by scene

| | |
|---|---|
| **Order** | last of the round: ME-237 → ME-301 → ME-304 → **ME-305** |
| **Depends on** | ME-237 (fresh side data on the RGBA frame), ME-301 (`HdrPeak.kt`, `lastPicture`, `whitePoint`) |
| **Feature** | [The shared editor shell](../shell.md) |

**Goal.** Where a film carries HDR10+, tone map each frame to its own scene's peak, falling back
to ME-301's per-file white point everywhere else.

**Why now.** The author asked for the white point to be found dynamically, 2026-11-05. Measuring
pixels was refused — the curve would move while a shot plays, and the same frame would look
different after a seek, on a frame step and in the render. **Authored per-scene metadata has
none of those defects**: it changes only at a cut, and a frame carries the same value however it
was reached. Probe, 2026-11-05 (`VideoHdrPeakProbe`):

- **HDR10+** (`AV_FRAME_DATA_DYNAMIC_HDR_PLUS`, `AVDynamicHDRPlus.params(0).maxscl`, × 10000 nits)
  — The Boys S05: 10 scene values in 25 s from 4:58 (346 … 689 … **1060** … 407), matching
  ffprobe at 5:14 / 9:58 / 19:59 (617 / 268 / 285). Read off the decoder's frame; on the RGBA
  path it only becomes readable with ME-237. The 1060 scene is **above the file's MaxCLL (913)**,
  so ME-301's single number clips it.
- **Dolby Vision L1 is not used.** Bytedeco maps no `AVDOVIMetadata`; read by raw offset it gave
  one value for three minutes, and ffprobe printed no L1 either. Only the static
  `source_max_pq` (≈ 1000 nits) is there, which ME-301's MDCV already covers.
- Avatar carries no HDR10+, so it keeps ME-301's number — the fallback is the common case.

**Scope by surface.** Per scene: the player, PiP, ambient fill, the editor's canvas and the
render. **Per file (ME-301):** seek preview, hover reel and the editor's thumbnail — a thumbnail
is one picture for a whole span, and keying its fold on the frame under the play head would
re-fold it at every cut for no visible gain.

## Steps

1. `HdrInfo` gains `val scenePeak: Float? = null`; `toColour()` passes
   `whitePoint = scenePeak ?: whitePoint`. KDoc: never part of `streamHdrInfo` —
   `showColour` writes `hdr.copy(scenePeak = null)` there, so the stream's colour stays the
   stable thing ME-205 keyed the fold on.
2. `HdrPeak.kt`: `internal fun AVFrame.scenePeak(): Float?` — the largest of `maxscl[0..2]` of
   window 0, × 100 (units of SDR white); null with no side data, with `num_windows == 0`, or
   outside 0 < x ≤ 100; `coerceAtLeast(1f)`. And
   `internal fun HdrInfo.withSceneOf(picture: AVFrame?): HdrInfo` — `copy(scenePeak = picture?.scenePeak())`
   when `transfer == PQ`, else `this`.
3. The player, at the three places a picture is sent:
   `FrameDelivery.deliverPicture` sends `hdrInfo?.withSceneOf(grabber.lastPicture)`;
   `JoinStep.send` takes the grabber that decoded the picture (the playing one on a
   continuation, `landed.grabber` after a hand-off) and does the same;
   `videoSeekTo` publishes `hdrInfo?.readFrom(...)?.withSceneOf(grabber.lastPicture)`. The
   presenter and `MediaViewerFrame` already carry the per-frame value to the painter.
   Recomposition stays per cut: `currentFrameHdrInfo` is a structural-equality state, and equal
   `HdrInfo`s inside a scene do not invalidate.
4. The editor's canvas. `VideoEditorEngine.Surface` keys its recipe on the frame's colour —
   `playback.state?.currentFrameHdrInfo` through ME-304's mode — instead of the stream's; the
   KDoc on `streamColour` says why the canvas now reads the frame (it changes per cut, not per
   frame) and why `Thumbnail` still reads the stream.
5. The render. `RecipePicture` takes `colour: StillColorInfo?` instead of a recipe already
   carrying its `source`, and in `draw(frame)` rebuilds `recipe.copy(source = hdrStageFor(...))`
   only when `(frame.opaque as? AVFrame)?.scenePeak()` differs from the last frame's — the
   render's grabber is ME-237's, so that side data is the frame's own. `VideoRender` passes the
   moded colour instead of copying the stage in.
6. Tests: `jvmTest/unit/HdrPeakTest.kt` gains `scenePeak` cases on an `av_frame_alloc()` frame
   filled by `av_dynamic_hdr_plus_create_side_data` — maxscl (0.06891, 0.03106, 0.03920) → 6.891;
   no side data → null; `withSceneOf` on HLG → unchanged — and `toColour()` preferring
   `scenePeak`. By hand: The Boys S05E01 from 4:58 — highlights hold through the 1060 scene; the
   same frame in the editor and in its render.

## Files

`.../viewer/video/engine/probe/HdrInfo.kt` · `.../probe/HdrPeak.kt` · `.../decode/VideoPlayerExt.kt` ·
`.../decode/FrameDelivery.kt` · `.../join/JoinStep.kt` · `.../sync/VideoPlayerSeek.kt` ·
`.../editor/engine/video/VideoEditorEngine.kt` · `.../video/render/RecipePicture.kt` ·
`.../video/render/VideoRender.kt` · `jvmTest/.../unit/HdrPeakTest.kt`

## Done when

- `HdrPeakTest` green; Avatar (no HDR10+) draws exactly as after ME-301;
- `JoinStep.kt`, `VideoEditorEngine.kt` ≤ 150 lines; anti-FQN clean;
  `./gradlew.bat :shared:build` green;
- The Boys looked at by the author.

## Built — 2026-11-05

As planned; step 3's three call sites share ME-301's `colourOf`, which applies
`readFrom` and `withSceneOf` together. Through `FreshFrameGrabber`, The Boys from 4:58 reads
3.46, 6.89, 6.84, 4.32, **10.61**, 4.69, 6.18, 5.35, 4.18, 4.07 — the ten scenes the RAW read
saw. `HdrPeakTest` green; **not yet looked at by the author**.
