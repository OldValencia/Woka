# Plan: Rare Image Formats and Frame Sets

> **TL;DR:** `.heic` is listed today but does not open at all — neither skia nor Coil decodes it.
> `.avif` is not even recognised as an image. Animated `.gif`/`.webp` show their first frame and
> then sit still. And HEIC is a **container**: a burst sequence, a Live Photo, a depth map,
> auxiliary images. macOS Preview puts those embedded frames in a strip and lets you walk them.
> This plan builds **one** "frames inside a file" mechanic covering every such type at once, rather
> than a special case bolted on for HEIC. F4 — HDR tone mapping for stills — moves here too.

Status: 🚧 In progress — **Phase 0 done (2026-08-07)**, Phases 1–6 not started. The probe forced a
dependency upgrade (`ffmpeg` → `8.0.1-1.5.13`, `javacv` → `1.5.13`) which is **taken and verified
against the suite**; HEIC and AVIF both decode on it. See
[the Phase 0 verdict](#the-verdict-the-dependency-was-bumped-and-it-holds) and the amendments it
forces on Phases 2b, 3 and 5.

Sits after `viewer-verification-pass.md`. Extends roadmap Phase 4 (The Viewer); nothing here blocks
Phase 5 (Editor), but Phase 1's frame-set model is what an editor would need to know which frame it
is editing, so the two should not be written twice.

---

## 1. What is broken today, precisely

| File | Listed? | Grid | Viewer |
|------|---------|------|--------|
| `.heic` | yes | grey placeholder | nothing — `canDecode` is false |
| `.avif` | **no** — not in `IMAGE_EXTENSIONS` | — | — |
| animated `.gif` | yes | first frame | first frame, frozen |
| animated `.webp` | yes | first frame | first frame, frozen |
| multi-page `.tiff` | **no** — not listed | — | — |
| HDR still (PQ/HLG `.png`, `.jpg`) | yes | as SDR | as SDR — F4 |

**Confirmed by hand, 2026-08-07 (dbad022)** — TC-IMG-05 in `ai/docs/testing/manual/`, the run logged
under `manual/results/`: HEIF, HEIC and AVIF do not open at all, and a PQ PNG opens and renders
without tone mapping. The table above was read off the code; this is the same answer from the app.

Three separate causes, one shape:

1. **No decoder.** `ImageLoader.load` is `Image.makeFromEncoded`, and skia has no HEIF or AVIF.
   `MediaKind.UNDECODABLE_EXTENSIONS` names this honestly and stops there.
2. **No concept of more than one frame.** `MediaThumbnails.loadThumbnail` returns
   `ImageBitmap?` — one bitmap per path. Nothing in the viewer can express "this file has 12
   frames". `Image.makeFromEncoded` silently returns the first.
3. **Colour is video-only.** `HdrInfo` is derived from FFmpeg stream ids inside the player, and
   `MediaViewerFrame` picks `HdrImagePainter` from `video.player.currentFrameHdrInfo`. A still has
   no path to that decision at all.

The unifying idea: a still is not "a bitmap", it is **a frame set with colour metadata**, of which
the overwhelmingly common case has exactly one frame and is SDR.

---

## Phase 0: Capability probe — a gate, not a feature

Nothing below is written until this reports. The bundled decoder set is a fact to be measured, not
assumed — the tone-mapping work already paid for that lesson once, when `avfilter-9.dll` turned out
to ship `tonemap` but neither `zscale` nor `libplacebo`, which closed off the entire filter-graph
approach after it had been designed (see Phase 5).

- Add `jvmTest/.../probe/DecoderCapabilityProbe.kt`. It **reports, it does not assert** — one test
  that prints a table and always passes, in the shape of `SampleMediaTest`'s corpus guard.
- Corpus: `.heic` (burst), `.heic` (Live Photo), `.avif`, animated `.gif`, animated `.webp`,
  multi-page `.tif`, **one PQ still and one HLG still** — the last two exist for Phase 5, which
  cannot settle its HLG question without a file to settle it against. For each, record the outcome
  of
  - `org.jetbrains.skia.Codec.makeFromData(...)` → `frameCount`, `getFrameInfo(i)`;
  - `FFmpegFrameGrabber` → does it open, how many frames does it report, what `pixelFormat`;
  - `avcodec_find_decoder_by_name("hevc" / "av1")` and whether the `mov`/`heif` demuxer is present
    in `org.bytedeco.ffmpeg`.
- **Also record what colour metadata each path exposes** — `colorTrc`/`colorPrimaries` from the
  grabber, and whatever `Image.colorSpace` gives on the skia path. Phase 5 depends on it, and
  finding out there that a still's transfer function is unreachable would be finding out too late.
- Record the result **in this document**, as a table with the exact probe output beside it. Phase 2's
  routing table is written from it.

Two outcomes are acceptable and the plan branches on them:
- **FFmpeg opens HEIF/AVIF** → Phase 2b is a `FfmpegStillDecoder`, no new dependency.
- **It does not** → Phase 2b is deferred to its own plan with a real dependency decision
  (`libheif` JNI, or Coil's AVIF/HEIF decoders), and Phases 1, 3 and 5 still ship on the formats
  skia already handles. **Do not add a native dependency inside this plan without that decision.**

**Files:** `jvmTest/.../probe/DecoderCapabilityProbe.kt`, fixtures in
`jvmTest/resources/media/` (+ `README.md` provenance entries), this document

### ✅ Done — measured 2026-08-07

`com.woka.probe.DecoderCapabilityProbe` reports and always passes; `SkiaCapability`,
`FfmpegCapability`, `ImageIoCapability` and `CapabilityReport` are its parts. Ten fixtures were
authored for it (`tools/frameset_gen.py`, 148 KB, provenance in `resources/media/README.md`): a
5-image HEIC burst, a HEIC + MOV Live Photo pair, an AVIF still, PQ and HLG AVIFs, a PQ PNG, an
8-frame GIF and WebP, a 3-page TIFF. The three stills that already work are in the corpus as
controls — a probe with no known-good row cannot tell a broken format from a broken probe.

`ImageIO` was added to the probe beyond what this phase asked for, because it is the one decoder
that costs nothing to adopt. It earned its place: see TIFF below.

**Exact output on the dependency as it stood — `ffmpeg 6.1.1-1.5.10`, `javacv 1.5.10`.** This is
the "before" table; it is kept because it is what justified the upgrade recorded below.

| File             | skia `Codec`                                         | skia `Image`                                                     | bundled FFmpeg                                                                    | JDK `ImageIO`           | colour metadata                                         |
|------------------|------------------------------------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------|-------------------------|---------------------------------------------------------|
| `burst.heic`     | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | **✗** Exception: avformat_open_input() error -1094995529: Could not open input …. | **✗** no reader         | skia `—` · ffmpeg `—`                                   |
| `livephoto.heic` | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | **✗** Exception: avformat_open_input() error -1094995529: Could not open input …. | **✗** no reader         | skia `—` · ffmpeg `—`                                   |
| `livephoto.mov`  | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | hevc, 23 frames, 320×240, yuv420p                                                 | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `still.avif`     | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, 1 frames, 640×480, yuv420p                                                   | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `hdr_pq.avif`    | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, 1 frames, 640×480, yuv420p10le                                               | **✗** no reader         | skia `—` · ffmpeg `PQ + wide`                           |
| `hdr_hlg.avif`   | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, 1 frames, 640×480, yuv420p10le                                               | **✗** no reader         | skia `—` · ffmpeg `HLG + wide`                          |
| `hdr_pq.png`     | 1 frames, 640×480                                    | ok                                                               | png, 1 frames, 640×480, rgb48be                                                   | png, 1 images, 640×480  | skia `≈sRGB gamma, non-sRGB gamut` · ffmpeg `PQ + wide` |
| `anim.gif`       | 8 frames, 320×240, [120, 120, 120, 120, 120, 120] ms | ok                                                               | gif, 8 frames, 320×240, bgra                                                      | gif, 8 images, 320×240  | skia `sRGB` · ffmpeg `SDR`                              |
| `anim.webp`      | 8 frames, 320×240, [120, 120, 120, 120, 120, 120] ms | ok                                                               | webp, 0 frames, 0×0, bgr24                                                        | **✗** no reader         | skia `sRGB` · ffmpeg `SDR`                              |
| `multipage.tif`  | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | tiff, 1 frames, 320×240, rgb24                                                    | tif, 3 images, 320×240  | skia `—` · ffmpeg `SDR`                                 |
| `still.png`      | 1 frames, 640×480                                    | ok                                                               | png, 1 frames, 640×480, rgb24                                                     | png, 1 images, 640×480  | skia `sRGB` · ffmpeg `SDR`                              |
| `still.jpg`      | 1 frames, 640×480                                    | ok                                                               | mjpeg, 1 frames, 640×480, yuvj420p                                                | JPEG, 1 images, 640×480 | skia `sRGB` · ffmpeg `SDR`                              |
| `still.webp`     | 1 frames, 640×480                                    | ok                                                               | webp, 1 frames, 640×480, yuv420p                                                  | **✗** no reader         | skia `sRGB` · ffmpeg `SDR`                              |

| Kind    | Name                      | Present |
|---------|---------------------------|---------|
| decoder | `hevc`                    | **yes** |
| decoder | `av1`                     | **yes** |
| decoder | `libdav1d`                | **no**  |
| decoder | `libaom-av1`              | **yes** |
| decoder | `webp`                    | **yes** |
| decoder | `gif`                     | **yes** |
| decoder | `tiff`                    | **yes** |
| demuxer | `avif`                    | **no**  |
| demuxer | `heif`                    | **no**  |
| demuxer | `image2`                  | **yes** |
| demuxer | `mov,mp4,m4a,3gp,3g2,mj2` | **yes** |
| demuxer | `gif`                     | **yes** |

### What the numbers say

1. **AVIF already works** on the bundled build, through the `mov` demuxer and the native `av1`
   decoder, and it carries its colour description intact — `hdr_pq.avif` reads `PQ + wide` and
   `hdr_hlg.avif` reads `HLG + wide` straight out of `readHdrInfo`, unchanged. Phase 2b's
   `FfmpegStillDecoder` covers AVIF today with no dependency of any kind.
2. **HEIC does not open, and the reason is not the codec.** `hevc` is present and decodes the Live
   Photo's MOV fine. `avformat_open_input` fails with `-1094995529` — `AVERROR_INVALIDDATA` — and
   the bundled CLI names it exactly: `moov atom not found`. A HEIC still is an item collection in a
   `meta` box with no `moov` at all, and FFmpeg 6.1's `mov` demuxer requires one. Item-based HEIF
   parsing landed in FFmpeg **7.1**.
3. **The fix for that is a version bump, not a new dependency.** Bytedeco publishes
   `org.bytedeco:ffmpeg` up to **8.0.1-1.5.13**. Its `ffprobe.exe`, run against these same fixtures,
   opens `burst.heic` as **five `hevc` streams — one per burst frame** — and `livephoto.heic` as
   one. So the burst's frame set falls out of the demuxer for free; no grid parsing, no `libheif`.
   Same build also reads the AVIF colour tags, and **carries `zscale`**, which 6.1.1 does not —
   see the Phase 5 note below.
4. **Multi-page TIFF is `ImageIO`'s, and nothing else's.** skia has no TIFF codec at all, and
   FFmpeg's `tiff_pipe` demuxer reports one frame for a three-page file on *both* versions. The
   JDK's TIFF plugin reports `3 images` with no dependency added. It reads animated GIF too
   (`8 images`), though skia already does that better — it reports per-frame durations, which
   `ImageIO` does not surface.
5. **skia cannot answer the colour question for a still.** `hdr_pq.png` carries a valid `cICP`
   chunk — FFmpeg reads `PQ + wide` off it — and skia describes its colour space as
   `≈sRGB gamma, non-sRGB gamut`. There is no transfer function on that path: `ColorSpace` exposes
   `isSRGB`, `isGammaLinear`, `isGammaCloseToSRGB` and nothing more. **Phase 5 must take
   `StillColorInfo` from the FFmpeg path**, which also means an HDR still is only tone-mappable
   once it is routed through `FfmpegStillDecoder`.
6. **Animated WebP is skia's alone.** FFmpeg opens it and grabs `0 frames, 0×0`
   (`image data not found`) on both versions; skia reports all 8 with durations. No loss — but the
   routing table must not send `.webp` to FFmpeg on the strength of it handling the *static* case.
7. **A Live Photo is two files.** There is no motion track inside `livephoto.heic` to find; the
   `Motion` frame is the sibling `.mov`, resolved by stem. Phase 3's "branch in `FrameStripState`"
   is therefore a **directory** lookup, not a container one — see the amended Phase 3 note.

### The verdict: the dependency was bumped, and it holds

The plan's Phase 0 branch was binary and reality was not — **AVIF was branch one, HEIC was branch
two, and branch two had a third option the plan did not anticipate.** Bytedeco publishes the same
artifact up to `8.0.1-1.5.13`, and item-based HEIF parsing landed in FFmpeg 7.1. So the HEIC fix
was a version bump, not a new dependency.

**Taken, measured, kept:**

| | |
|---|---|
| `org.bytedeco:ffmpeg-platform` | `6.1.1-1.5.10` → **`8.0.1-1.5.13`** |
| `org.bytedeco:javacv` | `1.5.10` → **`1.5.13`** |

JavaCV moves in step by necessity: the JNI stubs shipped inside the ffmpeg artifact are locked to
the presets version, so the two cannot move apart.

**Evidence it holds.** `:shared:jvmTest --rerun-tasks`, three runs on the new versions: **385
tests, two runs fully green.** One run failed twice in `HomeViewModelTest` with
`IllegalStateException: Dispatchers.Main is used concurrently with setting it`. That is a
**pre-existing race in the test harness**, not a regression: `withCollector` calls `scope.cancel()`
without joining, so `SharingStarted.WhileSubscribed`'s stop-timeout `delay` is still resuming on
`Dispatchers.Main` when `MainDispatcherRule.finished()` runs `resetMain()`. Nothing in either
stack trace touches FFmpeg. A control run on the **old** versions was green, and two further runs
on the new ones were green, so the failure tracks scheduling, not the dependency. **It is worth
fixing on its own terms** — `withCollector` should join its collector — and is deliberately left
alone here rather than folded into a dependency change.

**Exact output on the new versions — `ffmpeg 8.0.1-1.5.13`, `javacv 1.5.13`:**

| File             | skia `Codec`                                         | skia `Image`                                                     | bundled FFmpeg                                           | JDK `ImageIO`           | colour metadata                                         |
|------------------|------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------------------|-------------------------|---------------------------------------------------------|
| `burst.heic`     | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | hevc, **5 video streams**, 1 frames, 320×240, yuvj420p   | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `livephoto.heic` | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | hevc, **1 video streams**, 1 frames, 320×240, yuvj420p   | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `livephoto.mov`  | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | hevc, **1 video streams**, 23 frames, 320×240, yuv420p   | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `still.avif`     | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, **1 video streams**, 1 frames, 640×480, yuv420p     | **✗** no reader         | skia `—` · ffmpeg `SDR`                                 |
| `hdr_pq.avif`    | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, **1 video streams**, 1 frames, 640×480, yuv420p10le | **✗** no reader         | skia `—` · ffmpeg `PQ + wide`                           |
| `hdr_hlg.avif`   | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | av1, **1 video streams**, 1 frames, 640×480, yuv420p10le | **✗** no reader         | skia `—` · ffmpeg `HLG + wide`                          |
| `hdr_pq.png`     | 1 frames, 640×480                                    | ok                                                               | png, **1 video streams**, 1 frames, 640×480, rgb48be     | png, 1 images, 640×480  | skia `≈sRGB gamma, non-sRGB gamut` · ffmpeg `PQ + wide` |
| `anim.gif`       | 8 frames, 320×240, [120, 120, 120, 120, 120, 120] ms | ok                                                               | gif, **1 video streams**, 8 frames, 320×240, bgra        | gif, 8 images, 320×240  | skia `sRGB` · ffmpeg `SDR`                              |
| `anim.webp`      | 8 frames, 320×240, [120, 120, 120, 120, 120, 120] ms | ok                                                               | webp, **1 video streams**, 0 frames, 0×0, bgr24          | **✗** no reader         | skia `sRGB` · ffmpeg `SDR`                              |
| `multipage.tif`  | **✗** IllegalArgumentException: Unsupported format   | **✗** IllegalArgumentException: Failed to Image::makeFromEncoded | tiff, **1 video streams**, 1 frames, 320×240, rgb24      | tif, 3 images, 320×240  | skia `—` · ffmpeg `SDR`                                 |
| `still.png`      | 1 frames, 640×480                                    | ok                                                               | png, **1 video streams**, 1 frames, 640×480, rgb24       | png, 1 images, 640×480  | skia `sRGB` · ffmpeg `SDR`                              |
| `still.jpg`      | 1 frames, 640×480                                    | ok                                                               | mjpeg, **1 video streams**, 1 frames, 640×480, yuvj420p  | JPEG, 1 images, 640×480 | skia `sRGB` · ffmpeg `SDR`                              |
| `still.webp`     | 1 frames, 640×480                                    | ok                                                               | webp, **1 video streams**, 1 frames, 640×480, yuv420p    | **✗** no reader         | skia `sRGB` · ffmpeg `SDR`                              |

The decoder/demuxer table is unchanged between the two versions and is not repeated.

### The finding that decides how Phase 2b is written

**A HEIC burst is five video streams, not one stream of five frames.**

`burst.heic` reads `hevc, **5 video streams**, 1 frames`. HEIF stores each image as its own
stream, and `FFmpegFrameGrabber` decodes exactly one of them — whichever `videoStream` points at.
So the obvious implementation, looping `grabImage()` the way `VideoThumbnailExtractor` does,
reports **one** frame for a five-shot burst and silently throws the frame set away. That is the
same class of bug as `Image.makeFromEncoded` collapsing an animated GIF, arrived at from the
other direction.

The stream count is now a column in the probe so this cannot be rediscovered by accident.
`FfmpegStillDecoder` must **enumerate the container's video streams** and set `videoStream` per
frame — `VideoPlayerVideoTrack.kt` already does exactly that swap for multi-video containers and
is the thing to read first.

Note what this also means: the frame *count* for a HEIC comes from the demuxer without decoding
anything. `MediaThumbnails.frameSet` can answer cheaply; only rendering a chosen frame costs.

### Amendments this phase forces on the phases below

- **Phase 2b:** the routing table is `.heic`/`.heif`/`.avif` → FFmpeg, `.tif`/`.tiff` →
  `ImageIO`, `.gif`/`.webp`/`.png`/`.jpg`/`.bmp` → skia. `ImageIO` was not in the plan and is now
  a fourth decoder in `StillDecoders`. `.webp` must **not** be routed to FFmpeg on the strength of
  it handling the static case — FFmpeg grabs `0 frames` from an animated one on both versions,
  while skia reports all 8 with durations.
- **Phase 2b, second amendment:** `FfmpegStillDecoder` walks video streams, per the finding above.
- **Phase 3:** the Live Photo `Motion` frame comes from a **sibling file**, not a track —
  `livephoto.heic` reports one stream and the motion lives in `livephoto.mov`. `FrameStripState`
  needs a stem-matched directory lookup, the same shape as the external-subtitle scan, which
  already solves "find the files sharing this stem" and should be read before it is written twice.
- **Phase 5:** `StillColorInfo` comes from FFmpeg, never from skia (finding 5). And **fact 1 of
  Phase 5 is now out of date**: the 8.0.1 build carries `zscale`, so the filter-graph route it
  records as closed is open again. That is not a reason to take it — the shader ships and works —
  but "the bundled build ships `tonemap` but not `zscale`" is no longer true, and Phase 5 must not
  be read as if it were.

## Phase 1: The frame-set model

One type for "what is inside an image file", in `commonMain` so the editor can speak it later.

- Add `core/data/ImageFrameSet.kt`:
  - `data class ImageFrame(val index: Int, val kind: FrameKind, val durationMs: Int?)` —
    `durationMs` non-null only for animation.
  - `enum class FrameKind { Primary, Sequence, Auxiliary, Depth, Motion }` — a HEIC burst is
    `Sequence`, a Live Photo's video track is `Motion`, a depth map is `Depth`. The viewer shows
    `Primary` + `Sequence` + `Motion`; `Depth`/`Auxiliary` are listed but not offered as frames.
  - `data class ImageFrameSet(val frames: List<ImageFrame>, val primaryIndex: Int)` with
    `val isSingle: Boolean get() = frames.size <= 1`.
- Add `core/data/StillColorInfo.kt`: `data class StillColorInfo(val transfer: TransferKind, val
  isWideGamut: Boolean)`. **Reuse `TransferKind` from `video/engine/probe/HdrInfo.kt`** rather than
  declaring a second enum — but move `TransferKind` to `core/data/` in this step, because a still is
  not a video concept and `commonMain` cannot depend on `jvmMain`. `HdrInfo` keeps its FFmpeg-id
  mapping and imports the moved enum.
- `MediaThumbnails.loadThumbnail` keeps its signature (one bitmap, the primary frame) — every
  existing caller wants exactly that. The frame set is a **second, separate** call,
  `MediaThumbnails.frameSet(item): ImageFrameSet`, so the grid and the carousel are untouched.

**Files:** `core/data/ImageFrameSet.kt`, `core/data/StillColorInfo.kt`,
`core/data/TransferKind.kt` (moved), `feature/viewer/video/engine/probe/HdrInfo.kt`,
`feature/viewer/shell/MediaThumbnails.kt`

### ✅ Done — 2026-08-07

`ImageFrameSet.kt` (`ImageFrame`, `FrameKind`, `ImageFrameSet` with `isSingle`, `viewable`,
`primary` and a `Single` constant), `StillColorInfo.kt`, and `TransferKind` moved to `core/data`.
`HdrInfo` keeps its FFmpeg-id mapping and imports the moved enum. `:shared:build` green.

Two decisions the plan left open, settled:

- **The tone-map rule is one function, not two properties.** `TransferKind.kt` carries a top-level
  `needsToneMap(transfer, isWideGamut)`; `HdrInfo` and `StillColorInfo` both defer to it. Copying
  the predicate into the new type would have been the same mistake as declaring a second
  `TransferKind`, one level down.
- **`MediaThumbnails.frameSet` is deferred to Phase 2a**, deliberately. Until a decoder exists it
  could only ever return `ImageFrameSet.Single` — which is *true* for a JPEG and a **lie for
  exactly the HEIC, GIF and TIFF files this feature exists for**. A stub that answers "one frame"
  for a five-shot burst is the bug the plan was written to remove, not a step towards removing it.
  `MediaThumbnails.kt` is untouched.

`ImageFrame.index` is documented as decoder-local: skia counts animation frames, `ImageIO` counts
TIFF pages, FFmpeg counts video streams. Phase 2's routing is what gives the number meaning.

## Phase 2: Decoder routing

### 2a — skia's multi-frame path (unconditional)

- Add `feature/viewer/image/SkiaFrameDecoder.kt`: wrap `org.jetbrains.skia.Codec.makeFromData`.
  `frameCount`, per-frame `getFrameInfo(i).duration`, and `readPixels(bitmap, i)` for frame *i*.
  This covers animated `.gif` and `.webp` with no new dependency.
- `ImageLoader.load(file)` stays the single-bitmap entry point and gains
  `ImageLoader.load(file, frameIndex: Int = 0)`. The zero-argument behaviour is unchanged, so the
  viewer, the carousel and `ImageLoaderTest` keep working as they are.
- **The cache key must carry the frame index.** `ImageBitmapCache` is keyed by absolute path today;
  a twelve-frame burst at native resolution would otherwise be twelve collisions on one key. Key
  becomes `"$absolutePath#$frameIndex"` for frame > 0, and the bare path for frame 0 so nothing
  already cached is invalidated. The 256 MB LRU budget (F1) then does the right thing on its own:
  paging a burst evicts the frames behind it.

#### ✅ Done — 2026-08-07

`SkiaFrameDecoder.kt` (`frameSet` counts without decoding; `frame(file, index)` decodes one),
`ImageLoader.load(file, frameIndex = 0)`, `ImageBitmapCache.key(path, frameIndex)`, and
`MediaThumbnails.frameSet(item)` — which is now truthful, since skia answers for every extension
the app currently lists. `:shared:build` green.

`readPixels(bitmap, frame)` is called **without** a prior frame: skia decodes whatever earlier
frames a given one is built on by itself. Passing a prior frame is faster for a forward walk and
wrong for every other access pattern, and the strip is walked both ways.

⚠️ **Not yet exercised by a test.** The suite passes and the zero-frame path is provably unchanged,
but nothing yet decodes frame 3 of `anim.gif` and checks it differs from frame 0. `FrameSetDecodeTest`
and `FrameCacheKeyTest` (Phase 6) are the first things that will actually run this code.

### 2b — HEIF/AVIF, per the Phase 0 verdict

- Add `feature/viewer/image/FfmpegStillDecoder.kt` mirroring `VideoThumbnailExtractor`'s shape:
  `FFmpegFrameGrabber` on the still, `Java2DFrameConverter` to a `BufferedImage`, then
  `Image.makeFromEncoded` on a re-encoded PNG — **not** the raw frame, because the grabber's
  `BufferedImage` is BGR24 and the viewer expects N32Premul, and going through PNG is what the
  poster path already proved works. Guard `stop()`/`release()` with `runCatching` (the F5 shape).
- `MediaKind.kt`: add `avif` and `tif`/`tiff` to `IMAGE_EXTENSIONS`; remove `heic` from
  `UNDECODABLE_EXTENSIONS` **only if Phase 0 says it decodes**. Add `heif` alongside `heic` either
  way — they are the same container and listing one but not the other is the exact inconsistency
  the extension-list comment in that file was written to stop.
- Add `feature/viewer/image/StillDecoders.kt`: the routing function, one `when` on extension,
  returning the decoder to use. It is the only place that knows which decoder handles what.

**Files:** `feature/viewer/image/SkiaFrameDecoder.kt`, `FfmpegStillDecoder.kt`,
`StillDecoders.kt`, `ImageLoader.kt`, `core/data/MediaKind.kt`, `core/ui/image/ImageBitmapCache.kt`

## Phase 3: The frame strip in the viewer

macOS Preview's sidebar, adapted to Woka's chrome: a horizontal strip, because the viewer already
owns the bottom edge with `MediaViewerCarousel` and two vertical lists would fight.

- Add `feature/viewer/shell/frames/FrameStrip.kt` — a `LazyRow` of frame thumbnails, `1.dp` border,
  `8.dp` radius, the selected frame at full opacity and the rest at `0.6f`, matching
  `MediaViewerCarousel`'s treatment so the two read as one family.
- Add `feature/viewer/shell/frames/FrameStripState.kt` — `selectedFrame`, and the frame set for the
  active item, loaded in a `LaunchedEffect(activeItem)`.
- `MediaViewerState` gains `var frameIndex by mutableStateOf(0)`, reset to `primaryIndex` on file
  change in `MediaViewerActiveItemEffects` — the same effect that already resets `sizeMode`.
- `MediaViewerPlayer` composes the strip **only when `!frameSet.isSingle`**, above the carousel and
  below the video controls, so a normal photo is byte-for-byte the tree it is today.
- Keys: `PageUp`/`PageDown` step frames, in `handleMediaViewerKeyEvent`. `←`/`→` keep meaning
  *file*, not frame — a burst is still one item in the folder.
- A Live Photo's `Motion` frame opens the existing video path rather than the still path. That is a
  branch in `FrameStripState`, not a new player.

**Files:** `feature/viewer/shell/frames/FrameStrip.kt`, `FrameStripState.kt`,
`feature/viewer/shell/MediaViewerState.kt`, `MediaViewerPlayer.kt`,
`MediaViewerScreenEffects.kt`, `MediaViewerKeyHandler.kt`

`MediaViewerPlayer.kt` is 146 lines and `MediaViewerKeyHandler.kt` is 149. **Both are over budget
before this phase adds a line.** The split is a step of this phase, not an afterthought: pull the
overlay stack (`PlayPauseOverlay`, `SkipOverlay`, `ZoomHudOverlay`, the three vertical popovers) out
of `MediaViewerPlayer` into `MediaViewerPlayerOverlays.kt`, and the zoom/volume key cases out of
`MediaViewerKeyHandler` into `MediaViewerNavigationKeys.kt`.

## Phase 4: The grid

- Coil 3.0.4 decodes what the platform decodes, so `.heic`/`.avif` in the grid need the same
  decoder as the viewer. Add `core/ui/components/WokaImageDecoder.kt` implementing coil3's
  `Decoder` interface over `StillDecoders`, and register it on the `ImageLoader` coil builds — the
  grid then shows a real cover instead of the grey placeholder.
- `FolderCardBadge` shows the frame count for a set (`"12 FRAMES"`) the way it shows `"n ITEMS"` for
  a folder — it is the same "there is more inside this than one thing" signal.

**Files:** `core/ui/components/WokaImageDecoder.kt`, `core/ui/components/FolderCardOverlays.kt`,
`core/ui/components/FolderCardData.kt`

## Phase 5: HDR for stills

Raised as F4 during the viewer verification pass and deferred because there was nothing to test it
on: the formats that carry HDR stills in practice are HEIC and AVIF, and the app could not open
either. Once Phase 2b lands, it can.

### What is already built, and what it actually does

Tone mapping exists and ships — for **video frames only**. Three facts about it decide this phase,
so they are stated here rather than left to be rediscovered:

1. **It is a render-time GPU shader, and it has to be.** The obvious route — an FFmpeg filter graph
   `zscale=t=linear, tonemap=hable, zscale=t=bt709` — is closed: the bundled build
   (`org.bytedeco:ffmpeg-platform 6.1.1-1.5.10`) ships `tonemap` but **not** `zscale` and **not**
   `libplacebo`, and `tonemap` requires linear-light float input that only those two can produce
   from PQ. `colorspace` does not do the PQ transfer. So `HdrImagePainter` is an SkSL
   `RuntimeEffect` applied at draw time. **This constrains Phase 2b as well:** a `FfmpegStillDecoder`
   cannot be asked to tone map on the way out either — it hands over encoded pixels plus metadata,
   and the shader does the rest.
2. **The shader is PQ-only.** Its pipeline is: PQ EOTF → scale by `10000.0/100.0` → BT.2020→BT.709
   matrix → Hable operator with `W = 11.2` → `1/2.2` gamma. Every constant is hard-coded in the
   shader source rather than passed as a uniform, and all of them were chosen against 4K HDR10
   video — nothing has ever checked them on a photograph.
3. **Selection is already wider than the shader.** `HdrInfo.needsToneMap` is
   `transfer == PQ || transfer == HLG || isWideGamut`, so an HLG stream — and a plain BT.2020 SDR
   one — already reaches the PQ maths today and is mapped with the wrong transfer function. That is
   a pre-existing video defect, and this phase must not widen it to stills without a decision.

### The work

- `StillColorInfo` is filled by the decoder: from whatever `Image.colorSpace` yields on the skia
  path, from `colorTrc`/`colorPrimaries` on the FFmpeg path — the latter reusing `readHdrInfo`
  unchanged. Phase 0 has already recorded which of the two actually carries the information.
- `ViewerMedia.Still` gains `val color: StillColorInfo`, defaulting to SDR, so every existing
  construction site keeps its behaviour.
- `MediaViewerFrame`'s painter choice currently reads `video.player.currentFrameHdrInfo`. Change it
  to ask the `ViewerMedia` for its colour, so `Still` and `Video` answer the same question the same
  way and `HdrImagePainter` is selected for either.
- **Resolve fact 3 before wiring stills in**, one of two ways: add the HLG branch (`arib-std-b67`
  OETF instead of the PQ EOTF) to the shader, or narrow the selection so only `PQ` reaches the
  painter and wide-gamut-SDR takes a plain matrix conversion. Either is a decision that needs a real
  HLG fixture on the bench — Phase 0 collects one.
- If a still turns out to want different tuning from video, promote the operator, `peak-nits` and
  `desat` from shader constants to uniforms rather than forking the shader.

**Files:** `feature/viewer/shell/ViewerMedia.kt`, `MediaViewerFrame.kt`,
`feature/viewer/video/HdrImagePainter.kt`, `feature/viewer/video/engine/probe/HdrInfo.kt`,
`feature/viewer/image/StillDecoders.kt`

## Phase 6: Tests

Placed by the pyramid rule from `viewer-verification-pass.md` — a test moves up a layer only when it
cannot be written on the one below.

- **Unit:** `ImageFrameSetTest` (frame filtering by `FrameKind`, `isSingle`, `primaryIndex` out of
  range), `StillColorInfoTest` (the transfer/gamut → tone-map decision, shared with `HdrInfoTest`).
- **Integration:** `FrameSetDecodeTest` — every Phase 0 fixture reports the frame count the probe
  recorded; `StillDecoderRoutingTest` — each extension reaches the decoder it should;
  `FrameCacheKeyTest` — frame 0 uses the bare path, frames above it do not collide, and a burst
  paged end to end stays inside the `ImageBitmapCache` budget.
- **UI:** `FrameStripUiTest` — a multi-frame file renders the strip and a single-frame one does not;
  clicking a frame changes `state.frameIndex`; `PageDown` steps and stops at the last frame.
- **Manual:** TC-IMG cases for what a HEIC burst and a Live Photo actually look like, and one for an
  HDR still next to the same shot in macOS Preview. Each needs its *Why this is not automated*
  field, as all of them do. **`ai/docs/testing/manual/` no longer exists** — it was deleted in
  `ef874d5` after the verification pass, so this bullet is asking for a directory to come back, not
  for files to be added to one. Decide that before writing them.

**Files:** `jvmTest/.../unit/ImageFrameSetTest.kt`, `StillColorInfoTest.kt`,
`jvmTest/.../integration/FrameSetDecodeTest.kt`, `StillDecoderRoutingTest.kt`,
`FrameCacheKeyTest.kt`, `jvmTest/.../ui/FrameStripUiTest.kt`,
`ai/docs/testing/manual/TC-IMG-*.md`

---

## Out of scope, stated so it is not assumed

- **Writing** any of these formats. The editor saves what it always saved; a HEIC opened and edited
  is exported as PNG/JPEG until an encoder is a separate decision.
- **Dolby Vision Profile 5 stills.** P5 is single-layer with IPT/ICtCp colour and no HDR10 base
  layer, so it needs RPU reshaping — `libplacebo` or a DV decoder, neither of which is bundled. The
  video side made the same call for the same reason.
- **Depth maps and portrait-mode mattes as an editing input.** They are listed by
  `FrameKind.Depth` so the information is not thrown away, and nothing consumes them yet.
- **Animation playback timing** beyond stepping frames. An animated GIF that *plays* at its recorded
  frame durations is a small follow-up on top of Phase 1's `durationMs`, and is deliberately not
  bundled with getting the frames out in the first place.
