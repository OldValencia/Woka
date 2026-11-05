# ME-237 — Side data stops piling up on the RGBA frame

| | |
|---|---|
| **Order** | first of the ME-301 round, 2026-11-05 — ME-301 and ME-305 read frame side data and need it to be the frame's own |
| **Depends on** | nothing |
| **Feature** | [Playback](../playback.md) |

**Goal.** Clear the side data JavaCV copies onto its RGBA frame before every grab, so a long
film holds one frame's side data instead of every frame's.

**Why now.** Found by ME-301's probe (`jvmTest/probe/VideoHdrPeakProbe.kt`, 2026-11-05). On the
RGBA path `FFmpegFrameGrabber.processImage` calls `av_frame_copy_props(picture_rgb, picture)` on
every frame (JavaCV 1.5.14, line 1236), and in FFmpeg that call **appends** the source's side
data to the destination without clearing what is there. `picture_rgb` lives as long as the
grabber, so it keeps every frame's side data:

| File | Per frame | After 300 frames | ≈ per hour at 24 fps |
|---|---|---|---|
| The Boys S05E01 (DV + HDR10+) | 5 entries, 19.5 KB | 1500 entries, 5.8 MB | **1.7 GB** |
| Avatar S02E01 (DV) | 4 entries, 8.2 KB | 1200 entries, 2.4 MB | 0.7 GB |

Native memory, so the JVM heap never shows it, and `av_frame_get_side_data` walks the growing
list from the front — which is also why it answers the **first** frame's HDR10+ for the whole
film (346 nits at 4:58, 5:14, 9:58 and 19:59, where ffprobe reads 689 / 617 / 268 / 285).
Every grabber that decodes pictures in RGBA leaks this way: the player, its three decoder lanes
(ME-232), the seek preview, the hover reel, the render and the MP4 conversion. A RAW grabber
does not — its `picture` is refilled by the decoder.

## Steps

1. New `feature/viewer/video/engine/decode/FreshFrameGrabber.kt`:
   `internal class FreshFrameGrabber(file: File) : FFmpegFrameGrabber(file)` overriding the
   5-argument `grabFrame(doAudio, doVideo, doProcessing, keyFrames, doData)` — the one every
   other `grab*` overload calls. Before `super.grabFrame(...)` it clears the side data of the
   picture it last returned; after, it remembers `frame.opaque as? AVFrame` when
   `frame.image != null && imageMode != ImageMode.RAW`. Clearing is
   `while (nb_side_data() > 0) av_frame_remove_side_data(frame, side_data(0).type())`.
   KDoc states the JavaCV line, the measurement above, and that the frame a caller holds keeps
   its side data until the **next** grab — which is what ME-301 reads.
2. Replace `FFmpegFrameGrabber(file)` with `FreshFrameGrabber(file)` at the grabbers that
   decode pictures: `VideoPlayerGrabber.kt` `openVideoGrabber` and `openLaneGrabber`,
   `SeekPreviewSource.openGrabber`, `HoverExcerptReel.openGrabber`, `FrameRender.encodeClips`,
   `Mp4Conversion.kt:67`, `VideoThumbnailExtractor.kt:79`. Variables typed
   `FFmpegFrameGrabber` stay so typed; the import of the constructor goes. Probes that only
   read the header (`codedFrameSize`, `FileInfoPanelData`, `FfmpegStreamFacts`,
   `Mp4Conversion.kt:36`, `RenderSource.withGrabber`) and the audio grabbers are untouched.
3. Test `jvmTest/integration/FreshFrameGrabberTest.kt` on `rot90.mp4`, whose display matrix
   FFmpeg 8 hands to **every** decoded frame as side data (ffprobe, 2026-11-05) — so the
   phone-video fixture already reproduces the leak, and phone videos leaked too on every
   RGBA grabber. RGBA mode, all pictures grabbed: `nb_side_data()` on the last equals the
   first's; the same loop on a plain `FFmpegFrameGrabber` grows, which proves the test sees
   the defect.

## Files

`.../viewer/video/engine/decode/FreshFrameGrabber.kt` (new) ·
`.../viewer/video/engine/decode/VideoPlayerGrabber.kt` · `.../viewer/video/engine/preview/SeekPreviewSource.kt` ·
`.../viewer/video/engine/preview/HoverExcerptReel.kt` · `.../editor/engine/video/render/FrameRender.kt` ·
`.../editor/engine/video/render/Mp4Conversion.kt` · `.../viewer/video/engine/VideoThumbnailExtractor.kt` (edit) ·
`jvmTest/kotlin/com/woka/integration/FreshFrameGrabberTest.kt` (new)

## Done when

- the test holds the count flat across 100 frames;
- `VideoHdrPeakProbe`'s side-data run, pointed at `FreshFrameGrabber`, prints the same entry
  count at frame 1 and frame 300;
- the anti-FQN grep is clean on every edited file; `./gradlew.bat :shared:build` green.

## Built — 2026-11-05

As planned. `FreshFrameGrabber` also remembers the picture's `AVFrame` (`lastPicture`) for
ME-301, and `RenderSource.withGrabber` became one too, for ME-301's one-frame read. Measured
through the new class (`VideoHdrPeakProbe`): The Boys S05E01 holds **5 entries at frame 300
against JavaCV's 1500**, and HDR10+ on the RGBA path now changes per scene.
