# Plan: Rare Image Formats and Frame Sets

> **TL;DR:** `.heic` is listed today but does not open at all — neither skia nor Coil decodes it.
> `.avif` is not even recognised as an image. Animated `.gif`/`.webp` show their first frame and
> then sit still. And HEIC is a **container**: a burst sequence, a Live Photo, a depth map,
> auxiliary images. macOS Preview puts those embedded frames in a strip and lets you walk them.
> This plan builds **one** "frames inside a file" mechanic covering every such type at once, rather
> than a special case bolted on for HEIC. F4 — HDR tone mapping for stills — moves here too.

Status: ⏳ Pending — planned, not implemented.

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
- **Manual:** new cases in `ai/docs/testing/manual/` — TC-IMG for what a HEIC burst and a Live Photo
  actually look like, and one for an HDR still next to the same shot in macOS Preview. Each needs
  its *Why this is not automated* field, as all of them do.

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
