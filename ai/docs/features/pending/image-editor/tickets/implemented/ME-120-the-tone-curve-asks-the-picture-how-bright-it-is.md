# ME-120 — The tone curve asks the picture how bright it is

| | |
|---|---|
| **Order** | first of the 500 track; ME-118 must not ship before it |
| **Depends on** | ME-172 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Take the white point the tone curve normalises by from the picture instead of
from a literal, so an HDR photograph that barely exceeds SDR is not compressed as though
it had eleven times the range.

**Why now.** `HdrShader.kt:76` reads `const float W = 11.2`, which asserts every HDR
source peaks at 1120 nits. A real HDR frame measured **174** (ME-010), so `hable(1.0) /
hable(11.2)` puts SDR reference white at **148/255** instead of 255 — the "brightness at
−50 %" reported on 2026-08-18, and the second of the two defects found that day. It could
not be fixed then because nothing in the corpus had real HDR pixels to tune against.
**ME-172 put one there**, so it can.

## Where the number comes from, and why measurement is the primary path

**Not from metadata.** The obvious source is mastering-display max luminance or MaxCLL,
and the backlog assumed it. Checked 2026-09-03 with `ffprobe -show_frames`: neither
`hdr_real_pq.avif`, nor `WokaSample.DualVideo.mkv`, nor the tags-only files carry either.
A metadata-first design would fall through to the constant on every file this repository
can test, leaving the defect exactly where it is on the one fixture that can prove it.

So a **still measures its own pixels**, and that is affordable because
`rememberFramePainter` keys its `remember` on the bitmap: once per picture, not per frame.

**Video keeps the constant, and that is a stated limit rather than an oversight.** Its
bitmap changes every frame, so the same measurement would run at the frame rate; a
running maximum across frames would make the curve move while the picture plays, which is
the pumping every player avoids by using static metadata. Video therefore keeps a
constant until a file that carries mastering metadata reaches the corpus.

**A high percentile, not the maximum** — the same argument ME-167 makes for Auto's black
point. One specular pixel should not decide the curve for the whole picture, and a
subsample is both faster and more robust than a full scan of a 100 MP still.

## Steps
1. `HDR_SHADER` gains `uniform float whitePoint;` beside `transfer` and `wideGamut`, and
   `const float W = 11.2` becomes that uniform. **Declaration order is the packing
   order** — `HdrImagePainter.UNIFORM_BYTES` grows from 16 to 20 and the new float is
   written last.
2. `HdrImagePainter` takes `whitePoint: Float` with the present constant as its default,
   so every caller that has nothing to say keeps today's behaviour rather than silently
   acquiring a new one.
3. New `HdrWhitePoint.kt` beside the shader:
   `fun measuredWhitePoint(bitmap, transfer): Float`. It walks a subsample — every *n*th pixel on both axes, *n* chosen so a large
   still costs about the same as a small one — histograms the **signal code**, and puts
   the 99.9th percentile through the same EOTF the shader uses to get the shader's units
   (nits ÷ 100). Clamped to at least 1.0: a picture that never exceeds SDR white needs no
   compression at all, and a white point below 1.0 would *expand* it.

   **Both numbers were measured, not chosen** (2026-09-03, over all 57600 pixels of
   `hdr_real_pq.avif`, peak code 0.620 = 2.94 units):

   | | 99.5th | 99.9th | max |
   |---|---|---|---|
   | code | 0.510 | 0.573 | 0.620 |
   | units | 1.02 | 1.88 | 2.94 |

   The 99.5th percentile this ticket first specified returns **1.02**, which the clamp
   makes 1.0 — every highlight above SDR white would clip, which is the defect the curve
   exists to prevent. MaxCLL, the metadata this stands in for, is the *maximum*; the
   percentile is only there to discard a hot pixel, so it sits just under it.

   And the histogram is over the code, not over light, because 1024 linear buckets across
   0…100 units are 0.098 units apart — near 1.0 that is coarser than the answer.
4. `rememberFramePainter` measures for a still and passes the constant for a video frame.
   The two are already distinguishable there, but the test is `frameColorInfo`'s and was
   inline in it, so it becomes `isMovingFrame(media, drawn)` and both callers use it —
   this file's whole reason to exist is that the branch is written once.
5. The measurement runs **inside the existing `remember`**, which is once per bitmap. It
   must not become a per-frame cost; if a later change makes the painter mint per frame
   for stills too, this is the line that has to move first (ME-168's lesson).

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrShader.kt` (edit, 95 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrImagePainter.kt` (edit, 105 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrWhitePoint.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/ViewerFramePainter.kt` (edit, 61 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerFrame.kt` (edit, the still's call site)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/ambient/AmbientFillLayer.kt` (edit, the ambient fill is always moving)

## Done when
- `jvmTest/unit/`: `measuredWhitePoint` on `hdr_real_pq.avif` lands between the fixture's
  99th percentile and its own peak — **1.88 in shader units, nowhere near 11.2**, and
  never above 2.94;
- `jvmTest/unit/`: it returns 1.0 or more for an ordinary SDR picture, so nothing that
  does not need compressing gets expanded;
- `jvmTest/integration/`: the shader is run twice on SDR reference white — PQ code 0.5081,
  100 nits by ST 2084 — once per divisor. The constant renders it at the **148/255** the
  complaint reported; the measured white point renders it above 200. **Not 255**: 1.0 is
  what `hable(x) / hable(W)` returns at `x = W`, so full scale belongs to the picture's own
  brightest tone, and a third test asserts that tone does reach it;
- `grep "11.2"` finds it only as the documented fallback, never as the divisor;
- confirmed by hand: the picture reported at "brightness −50 %" is the acceptance test,
  and only the author has it.
