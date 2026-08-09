# Plan: Bounding the video pipeline's memory (4K HDR)

**Status: complete.** Every phase below is implemented and measured. What was dropped is recorded
with the reason, and the two questions that outlived this work moved to
`hdr-hardware-decode-plan.md`.

Test file: `Человек-паук Вдали от дома.2019.IMAX.WEB-DL.2160p.HDR.mkv` — 3840×2160, HDR10/PQ, 74 GB.
Machine: 32 GB RAM, 3440×1440 display. That display maximised makes `RenderScale` ask the decoder for
2560×1440, so a frame is **14.7 MB of RGBA**, not the 33 MB a native-resolution frame would be —
`RenderScale` was already doing its job before any of this started.

Goal: a native-memory ceiling that is a fixed multiple of the frame size and does not move with how
long the file has been playing.

---

## Part 0. What the first draft of this document got wrong

Kept deliberately, so nobody re-fixes the wrong thing.

1. **"`makeRaster` allocates 3.68 MB and that is all."** It is not all. `SkiaImage.makeRaster(...)`
   is `SkImages::RasterFromPixmapCopy` — copy #1. `.toComposeImageBitmap()` then does
   **`Bitmap.allocPixels` + `Canvas(bitmap).drawImage(...)`** — copy #2, plus a whole-frame raster
   blit converting RGBA/UNPREMUL to BGRA/PREMUL. Verified in the `ui-graphics-desktop-1.11.1`
   sources: `SkiaImageAsset.skiko.kt` → `Actuals.skikoExcludingWeb.kt:32`. So each frame allocated
   **two** native buffers and ran a 3.7-megapixel per-pixel conversion, not one buffer.

2. **"`Image.makeFromBitmap` in `HdrImagePainter` physically copies 33 MB."** No. `toBitmap()` calls
   `setImmutable()`, and `SkImages::RasterFromBitmap` shares an immutable bitmap's pixelref rather
   than copying it. The symptom was real, the mechanism was different — see Part 2.

3. **"Level 3: FFmpeg decodes into VRAM and we hand Skia a `BackendTexture`."** Not reachable through
   JavaCV. With `pixelFormat = AV_PIX_FMT_RGBA`, `FFmpegFrameGrabber` runs
   `av_hwframe_transfer_data` into system memory and swscales regardless. True zero-copy would mean
   driving `AVHWFramesContext` through JavaCPP and building a Skia `BackendTexture` from a D3D11
   shared handle, bypassing both JavaCV and Compose. Dropped.

4. **"`withFrameNanos` means VSync."** The Compose frame clock is not tied to the display's refresh,
   and the presenter runs on `Dispatchers.Default` rather than the UI thread — moving it there would
   couple decoding to composition. The real source of judder is `delay()`'s millisecond granularity;
   the fix for that is nanosecond parking, not a different tick source. Out of scope.

What the first draft got right: the diagnosis of "native memory the collector cannot see", the ban on
`System.gc()`, and that audio is irrelevant here (`toPcmBytes` is a few KB per packet, and a 500-deep
channel is about 4 MB).

---

## Part 1. Cause one — the frame pipeline allocated two buffers and closed neither

`feature/viewer/video/engine/decode/VideoPlayerFramePacer.kt`, before:

```kotlin
return SkiaImage.makeRaster(info, scratch, frame.imageStride).toComposeImageBitmap()
```

Per **presented** frame, at 2560×1440:

| step | what | cost |
|---|---|---|
| `buffer.get(scratch, …)` | copy into a reused JVM array | 14.7 MB, reused |
| `makeRaster` | native buffer #1 | **+14.7 MB** |
| `toComposeImageBitmap` → `allocPixels` | native buffer #2 | **+14.7 MB** |
| `toComposeImageBitmap` → `Canvas.drawImage` | 3.7 Mpx blit, RGBA→BGRA + premultiply | CPU |

The intermediate `SkiaImage` became garbage immediately and was **never closed**; nor was the
`Canvas`. Both were freed only once the collector reached a Java wrapper of a few dozen bytes.

29.4 MB of native allocation per frame at 24 fps is ~700 MB/s of churn, against a heap growing by
about a kilobyte a second. G1 saw no reason to run.

The same defect existed in a second copy of the code: `VideoPlayerExt.kt`'s `Frame.toComposeBitmap()`
on the seek path.

---

## Part 2. Cause two — `HdrImagePainter` recompiled the shader per frame and pinned pixels

`MediaViewerFrame.kt` does `remember(frameToDraw, color) { HdrImagePainter(...) }`, and `frameToDraw`
is a new object every frame — so the `remember` missed every time, a new painter was built every
frame, and its `private val effect by lazy { RuntimeEffect.makeForShader(HDR_SHADER) }` **parsed and
compiled ~70 lines of SkSL 24 times a second, on the UI thread, inside `onDraw`**.

Worse, `onDraw` built a `ByteBuffer`, an `Image`, a `Data` and **two** `Shader`s per draw and closed
none of them. The runtime-effect shader holds the image shader, which holds the image, which shares
the bitmap's pixelref — so **every draw left behind a chain pinning a whole frame of pixels**, and
that chain outlived the frame's own wrapper. At 24 fps that is ~350 MB/s of memory that could not be
reclaimed even after the player had moved on.

The test file is HDR10, so this is the branch it actually took. The SDR path (`BitmapPainter`) was
always clean: Compose itself does `Image.makeFromBitmap(bitmap).use { … }` in
`SkiaBackedCanvas.skiko.kt:298`.

---

## Part 3. Phase 1 — one buffer instead of two ✅

`FramePacer.convert` now builds the drawn bitmap directly:

```kotlin
val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
val bitmap = Bitmap()
check(bitmap.installPixels(info, staging, frame.imageStride))
bitmap.setImmutable()
```

- `installPixels` is one native allocation and one memcpy. The second buffer and the canvas blit are
  gone outright.
- `OPAQUE` rather than `UNPREMUL`: swscale writes 255 into every alpha byte, and saying so spares
  Skia an un-premultiply pass at texture upload.
- `setImmutable()` is mandatory — without it `Image.makeFromBitmap` inside `SkiaBackedCanvas` makes a
  defensive copy on every draw.

That `installPixels` copies rather than aliasing the Java array was **verified by test**, not
assumed: the pacer hands it the same staging array shape every frame, and an aliasing implementation
would have made every queued frame show the newest pixels.

Same treatment applied to `VideoPlayerExt.kt: Frame.toComposeBitmap()`.

**Files:** `video/engine/decode/VideoPlayerFramePacer.kt`, `video/engine/decode/VideoPlayerExt.kt`

---

## Part 4. Phase 2 — deterministic release ✅

### The measurement that forced it

After Phases 1 and 3, 60 seconds of playback showed a 4 → 11 GB sawtooth with a ~20 second period.
That is 350 MB/s — exactly `14.7 MB × 24 fps`. The reading was unambiguous: Phase 1 had worked (it
would have been ~700 MB/s otherwise), the shader pinning from Part 2 was gone (memory came back
**completely** on every drop), and one source remained — the pipeline itself. The hypothesis that
minor collections would be frequent enough on their own was wrong; they arrived every 20 seconds.

### The version that was written and reverted

A 12-slot ring closing each bitmap on eviction. Reverted before it ever ran, on this arithmetic: the
ring is advanced by the **decoder**, 8 of its 12 slots are already spoken for by the frame channel,
one by the presenter's local, one by `currentFrame` — leaving about 4 frames, ~165 ms. Drawing reads
`currentFrame` during composition and calls `Image.makeFromBitmap` later, in the draw phase. A GC
pause is harmless (it stops the presenter too), but a 165 ms EDT stall is not exotic: a window
resize, a native dialog, the first frame after a track switch. The cost of being wrong is `_ptr == 0`
inside a native call — a JVM crash, not a leak. Widening the ring to a safe margin (~56 slots ≈ 2 s)
would have put the ceiling at 823 MB, which defeats the point.

### What shipped instead: `FrameRetention`

Close by **draw acknowledgement**, not by ring position. The distinction is what makes it safe: the
mark advances only when something has actually been drawn, so a stalled UI freezes it rather than
racing past it.

- Every produced bitmap gets a monotonic id; `VideoFrame` carries it and the presenter publishes it
  as `VideoPlayerState.currentFrameId` beside `currentFrame`.
- Every site that draws the picture reports what it drew from its `drawWithContent` scope. There are
  exactly two — `MediaViewerFrame` and `ViewerBackdrop` — confirmed by grep, and both read the id in
  the same composition as the bitmap, so the pair can never be reported out of step.
- The decoder closes anything `CLOSE_MARGIN_FRAMES` (16) behind the newest drawn frame. The margin
  covers the only case where two scenes draw different frames at once: the few frames while the
  fullscreen window takes over from the main one, which closes itself right after
  (`MediaViewerScreen.kt:125`).

Two deliberate one-sided failure modes:

- A stalled UI, or a future third draw site that forgets to report, freezes the mark and frees
  nothing. The failure is memory growth, caught by Part 5, never an early free.
- Past `MAX_TRACKED` (64) frames the oldest reference is **dropped rather than closed**, because
  nothing is drawing — a minimised window, a scene not yet composed — and holding strong references
  there would stop the collector doing what it did before this class existed. Dropping hands the
  frame back to the GC path, so the worst case equals the old behaviour and is never worse.

**Files:** `video/engine/decode/FrameRetention.kt` (new), `video/engine/VideoPlayerState.kt`,
`video/engine/decode/VideoPlayerFramePacer.kt`, `video/engine/decode/VideoPlayerPresenter.kt`,
`video/engine/decode/VideoPlayerDecoder.kt`, `shell/MediaViewerFrame.kt`, `shell/ViewerBackdrop.kt`

---

## Part 5. Proportional GC pressure — the backstop ✅

Kept on purpose after Part 4, as the path that catches whatever the retention rule refuses to free.

One reason the collector slept: 15 MB of native pixels hide behind a wrapper of a few dozen bytes, so
the heap grew by ~1 KB/s while native memory grew by 350 MB/s.

`FramePacer.convert` therefore **does not reuse** its staging array — a fresh `ByteArray` per frame.
It reads as waste and does the opposite: heap pressure becomes one-for-one with native pressure, so
any GC policy that bounds the heap bounds the frames with it. The memcpy underneath is unchanged;
only the allocation is added, and it is short-lived young-generation garbage.

The ceiling is then set explicitly, in `desktopApp/build.gradle.kts`:

```kotlin
jvmArgs += listOf("-Xmx1536m", "-XX:MaxNewSize=256m")
```

`-XX:MaxNewSize` is the actual dial: 256 MB of staging arrays is roughly one young collection per
second at 24 fps. Before it, the young generation grew adaptively inside an 8 GB heap — a quarter of
this machine's RAM — which is where the 20-second gap came from.

`-Xmx1536m` is not load-bearing for frames. Raise it if a very large still ever exhausts the heap
during an ImageIO decode; the frame ceiling is held by `MaxNewSize`.

**Files:** `video/engine/decode/VideoPlayerFramePacer.kt`, `desktopApp/build.gradle.kts`

---

## Part 6. Phase 3 — `HdrImagePainter`: one shader per process ✅

1. **`RuntimeEffect` hoisted to file level** — `private val HDR_EFFECT by lazy { … }`. One SkSL
   compile per process instead of 24 a second on the UI thread.
2. **`Image`, its shader, `Data` and the effect shader are closed** in a `finally` immediately after
   `drawRect`. Safe: the canvas took its own references while recording the operation, and they
   outlive ours. This is what removed the pixel pinning from Part 2 — the dominant multiplier.
3. The uniform buffer moved into a private `DrawScope.uniforms()`; 16 bytes per draw, left as is.
4. **Not done, and not needed:** stopping the per-frame painter construction. After step 1 the
   painter is a few dozen bytes of young-generation garbage. Caching `Image`/`Shader` inside it would
   only pay off if a frame outlived a single draw, which Part 4 does not arrange.

**Files:** `video/HdrImagePainter.kt`

---

## Part 7. Dropped, with reasons

- **Stop blurring the live frame in `ViewerBackdrop`.** Overstated when first raised. The backdrop is
  drawn inside `mediaViewerBackgroundClip`, which wraps `drawContent()` in a `clipPath` limited to
  the nav pills, the control bar and the carousel — so the blur is bounded by that clip, not a
  full-screen pass. At the measured 10% CPU there is nothing here worth a visible change to how the
  chrome looks.
- **`RenderScale` shared between the main and PiP windows.** Raised from reading the code, never
  reproduced. Opening PiP closes the main viewer immediately (`MediaViewerScreen.kt:125`), so the
  window in which both report a surface size is very narrow. Left alone until something is actually
  observed.

## Part 8. Moved out

- **10-bit PQ truncated to 8 bits.** `VideoPlayerDecodeStart.kt` pins `AV_PIX_FMT_RGBA`, so swscale
  cuts HDR10 to 8 bits *before* `HDR_SHADER` ever sees the signal. A correctness question, not a
  memory one, and it pulls against playback on weak machines — moved to
  `hdr-hardware-decode-plan.md`.
- **Software-only decode.** Deliberate (`"threads", "auto"`, no `hwaccel`, per a comment about
  D3D11VA scaling bugs) and not a memory question either. Same new plan.

---

## Part 9. Result

| | before | after |
|---|---|---|
| native allocation per frame | 29.4 MB, never closed | 14.7 MB, closed 16 frames after it is drawn |
| retained past the wrapper's death | ~350 MB/s (Part 2 shaders) | 0 |
| CPU per frame beyond decoding | 3.7 Mpx blit + an SkSL compile | one memcpy |
| memory graph | 4 → 11 GB sawtooth, ~20 s period | flat |

Measured on the test file: the sawtooth is gone, the line is flat, and the process sits at
2.3–2.4 GB with the CPU at 10%.

Still open, tracked outside this plan: the intermittently crossed-out audio icon. Temporary
`WOKA-AUDIO:` logging is in place at every route that can produce it — `probeTracksAndChapters` and
`reopenAudioGrabber` — and is deliberately left in until the bug is caught once. It is not a memory
issue and does not hold this plan open.
