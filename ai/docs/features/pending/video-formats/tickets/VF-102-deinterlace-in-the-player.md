# VF-102 — Deinterlace interlaced video in the player

| | |
|---|---|
| **Order** | 1 of 2 — [VF-106](VF-106-deinterlace-row.md) adds the Auto/Off row on top |
| **Depends on** | nothing (VF-001 landed the media timeline this reads) |
| **Feature** | [Video formats](../video-formats.md) — §7 holds the measurements behind every choice here |

**Goal.** A stream whose decoded frames are flagged interlaced plays through `bwdif` (one picture per
frame, 25i → 25p) in the player; every other stream keeps today's decode path untouched.
**Why now.** 14 of 16 DVD/TV/camcorder samples are interlaced frame by frame (§7); without this every
`vob`/`ts`/`mpg`/`mod`/`evo` combs on motion.

## Design — fixed by the measurements in §7, not open
- **The signal is the frame's flag, never the stream's `field_order`.** `V.VOB`/`lasvegas.vob` state
  `bb` and every frame is TFF; `sample.evo` (film) states `tt` and every frame is progressive;
  `impossible_seek.mpg` states `unknown` and 192/198 frames are interlaced.
- **Detection runs on the ordinary RGBA path.** `FFmpegFrameGrabber.processImage` calls
  `av_frame_copy_props(picture_rgb, picture)` and sets `Frame.opaque = picture_rgb`, so the flags ride on
  every RGBA frame (read from the 1.5.14 bytecode — step 1 confirms it at runtime).
- **The filter is JavaCV's `FFmpegFrameFilter`, but `parity` and `deint` are ours.** Its `push(Frame)`
  rebuilds an `AVFrame` from the buffers (`pushImage`) and drops the flags, so `parity=auto` would read
  BFF and `deint=interlaced` would pass everything. Graph:
  `bwdif=mode=send_frame:parity=<tff|bff>:deint=all,scale=W:H,format=rgba`, parity from the first
  interlaced frame's `AV_FRAME_FLAG_TOP_FIELD_FIRST`. It outputs RGBA `Frame`s, so `FramePacer.convert`
  and `toComposeBitmap` stay as they are. Cost of `deint=all`: the 3 % progressive frames of a mixed
  stream (`impossible_seek.mpg`) are filtered too — accepted.
- **Filtering happens on YUV before scale** (§7, cost: `bwdif` on RGBA costs ~3×), so in
  deinterlace mode the grabber runs `ImageMode.RAW` and the graph does the render-scale.

## Steps
1. **Probe — record in §7, dated.** `jvmTest/.../probe/DeinterlaceProbe.kt` on the `VideoFormatProbe`
   pattern, over `interlaced.ts` plus `WOKA_VIDEO_SAMPLES`: (a) per file, how many RGBA frames carry
   `AV_FRAME_FLAG_INTERLACED` in `(frame.opaque as AVFrame).flags()` against ffprobe's count in §7;
   (b) through `FFmpegFrameFilter` with the graph above, the pulled frame's `timestamp` against the
   pushed one (expect one frame of delay, same values); (c) the comb score of §7 on frame 12. If (a)
   finds no flags, stop and report — the detection design falls.
2. **Fixture** `jvmTest/resources/media/interlaced.ts` — the command in §7 (`i576s.ts`, 447 KB, 1 s,
   720×576 TFF, every frame flagged). Row in the media `README.md`.
3. **Split `VideoPlayerState.kt` (150)** — `updateVolume` becomes an extension in a new
   `engine/VideoPlayerVolume.kt`; import it at its four callers (`PipKeyHandler`, `MediaViewerAmbient`,
   `MediaViewerFullscreenOverlays`, `VideoPlayerEngineTest`).
4. **`decode/Deinterlace.kt` (new)** — `internal class Deinterlace` held as
   `internal val deinterlace = Deinterlace()` on `VideoPlayerState`:
   - `@Volatile var topFieldFirst: Boolean?` — null until an interlaced frame is seen;
     `var isInterlaced by mutableStateOf(false)` for VF-106's row;
   - `fun observe(frame: Frame): Boolean` — true the first time a frame carries the flag (sets both
     fields above);
   - `val active get() = topFieldFirst != null`;
   - `fun open(grabber, outWidth, outHeight)` — builds and starts the filter from the grabber's native
     `imageWidth`/`imageHeight`/`pixelFormat`/`frameRate`/`aspectRatio`; `scale=` takes
     `RenderScale`'s decode size, or the native one when it is 0;
   - `fun process(frame: Frame): Frame?` — push, then pull (null while `bwdif` waits for the next frame);
   - `fun reset()` — stop + start the filter: it holds the neighbouring frames, so every seek and
     restart calls it; `fun drain(): Frame?` — `push(null)` and pull, at EOF; `fun close()`.
5. **`decode/VideoPlayerGrabber.kt`** — `openVideoGrabber`: when `deinterlace.active`, `imageMode =
   RAW`, no `pixelFormat`, no `renderScale.applyTo` (the scale lives in the graph); else as today.
   The decoder loop and `reopenVideoGrabber` call `deinterlace.open(...)` after `start()`.
6. **`decode/VideoPlayerDecoder.kt` (145)** — extract the `frame.image != null` branch (`:201–220`) into
   `VideoPlayerState.deliverFrame(...)` in a new `decode/FrameDelivery.kt` first, then in it: not
   active → `if (deinterlace.observe(frame)) request the re-open` (`pendingVideoReopenIndex`, as
   `requestRescaleIfNeeded` does, no spinner before the first paint); active → `deinterlace.process`,
   and the frame's time is the **pulled** frame's `timestamp - originUs`, not `grabber.mediaTimeUs`.
   On EOF, `drain()` before `handleEndOfStream`. `finally` closes the filter.
7. **`sync/VideoPlayerSeek.kt`** — `videoSeekTo` calls `deinterlace.reset()` after each
   `setTimestamp`/`restart`; `decodeForwardTo` passes frames through `process` when active and compares
   the pulled frame's media time with the target.
8. **Tests** — `integration/DeinterlaceTest.kt` (new): `interlaced.ts` through the player's grabber and
   `Deinterlace` — `observe` true on the first frame, `topFieldFirst == true`, the output frame's comb
   score < 0.6 where the raw frame scores > 1.5 (§7: 1.95 → 0.48); `chapters.mkv` — `observe` never
   true over the whole file. `VideoPlayerEngineTest` stays green.

## Files
`decode/Deinterlace.kt` (new), `decode/FrameDelivery.kt` (new), `engine/VideoPlayerVolume.kt` (new),
`engine/VideoPlayerState.kt`, `decode/VideoPlayerGrabber.kt`, `decode/VideoPlayerDecoder.kt`,
`sync/VideoPlayerSeek.kt`, `track/VideoPlayerVideoTrack.kt`, the four `updateVolume` callers;
`jvmTest/.../probe/DeinterlaceProbe.kt`, `jvmTest/.../integration/DeinterlaceTest.kt` (new),
`jvmTest/resources/media/interlaced.ts` + `README.md`. Eleven files: three are the size split and the
behaviour cannot be shown with less (tickets §4a).

## Out of scope
Posters, hover reel and seek preview stay undeinterlaced (a comb at thumbnail size is invisible).
50p per-field output — the author chose per-frame on 2026-11-01; a later ticket if wanted.

## Done when
- §7 holds the probe result, dated;
- `interlaced.ts` and a real `.vob` play without combing; seeking lands on a clean picture; a
  progressive `.mp4` takes the old path (no `FFmpegFrameFilter` created — a log line in the probe);
- `./gradlew.bat :shared:build` green (`DirectoryWatchTest`, `FolderSelectionTest` are known flakes);
- anti-FQN clean on every touched `.kt`; none over 150 lines.

## Result — 2026-11-01
**Built**, one pass with VF-106. Probe result in the feature doc §7.

- **Changed from the design above, on measurement:** not `FFmpegFrameFilter` — on the grabber's RAW
  planar frames its `pushImage` crashes the JVM, and it drops the flags anyway. Our own
  `decode/DeinterlaceGraph.kt` (libavfilter: `buffer → bwdif → [scale] → format=rgba → buffersink`)
  takes the decoder's `AVFrame` itself, so `parity=auto:deint=interlaced` work per frame and no
  parity is read by us. Pictures are timed in the sink's time base (`bwdif` halves the source's).
- `decode/Deinterlace.kt` — detection from the RGBA frame's flags, the graph's lifetime, `reset` on
  every seek and restart, `drain` at EOF; `decode/FrameDelivery.kt` — the per-picture branch out of
  the decode loop; `engine/VideoPlayerVolume.kt` — the size split of `VideoPlayerState`.
- **Tests:** `integration/DeinterlaceTest` (graph: comb 2.48 → < half, one frame behind; player:
  found interlaced and re-opened behind the graph, a seek lands a picture, Off, a progressive file);
  `support/CombScore.kt` shared with the probe.

Done-when:
- [x] §7 holds the probe result, dated
- [x] `interlaced.ts` plays through the graph and seeks (test); a real `.vob` — **not looked at on
  screen**, for the author
- [x] progressive files take the old path — `chapters.mkv`: no flag, no graph (test)
- [x] `:shared:build`; anti-FQN clean; no touched `.kt` over 150 lines
