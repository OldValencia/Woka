# ABANDONED — Playing 4K on machines that cannot

**Status: abandoned. The code was written, shipped, broke playback, and was reverted. Nothing
described below exists in the repository.**

This document is kept for its measurements only. They were expensive to get and they are the reason
not to try this again the same way.

## Why it was abandoned

It was built and reverted within one session. Two failures, and both were the same mistake:

1. **The feature could not be tested where it was written.** Hardware fallback only fires on a
   machine that falls behind for five seconds. The development machine has eight times the headroom
   it needs, so the central path never executed once. Unit tests covered the timing policy; the thing
   the policy triggers was never observed working.
2. **The stall fix that came with it made playback worse.** The background grabber swap
   (`VideoGrabberSwap.kt`) produced stutter, audio desync and a decode-size regression:
   `RenderScale.applyTo` mutates `decodeWidth` at open time, so a preparation that was cancelled or
   mistimed left the policy believing a size was applied that was not, and the picture stayed stuck
   at the wrong decode size. Removing `isReloadingVideo` also stopped the audio loop parking during a
   swap, which is what put sound out of step.

Both are the same lesson: **untestable code in the playback hot path is not worth its upside**, and
the upside here was already measured as negative on any machine that copes.

## What replaced it

`playback-health-notice.md` — measure the displayed frame rate and *tell the user* when the machine
cannot keep up, instead of silently attempting a fix that cannot be verified.

---

## What was measured

`Человек-паук Вдали от дома.2019.IMAX.WEB-DL.2160p.HDR.mkv` — 3840×2160 HEVC Main10 — decoded flat
out, 90 frames from a fixed point, on the development machine (Ryzen-class CPU, RTX 5080 + Radeon
iGPU):

| configuration | fps | per frame |
|---|---|---|
| software, 320px output (isolates the codec) | 198 | **5.0 ms** |
| software, 2560px output (what ships today) | 158 | 6.3 ms |
| software, native 3840px output | 80 | 12.5 ms |
| `hevc_cuvid` (NVDEC), 2560px output | 127 | 7.9 ms |
| `hevc_cuvid` (NVDEC), native output | 71 | 14.1 ms |

Every frame checked whole: mean byte value of the top and bottom halves, compared against a software
reference. No configuration produced the half-frame the old comment warns about.

## Three findings, in order of importance

### 1. Hardware decode has never been on

`FFmpegFrameGrabber` in JavaCV 1.5.13 contains **no reference at all** to `hwaccel`, `hw_device_ctx`,
`av_hwdevice` or `hwframe`. `setVideoOption` puts its pair into an `AVDictionary` handed to
`avcodec_open2` (`FFmpegFrameGrabber.java:1067-1076`), and `hwaccel` is an option of the *ffmpeg CLI
tool*, not an `AVOption` of any decoder — so libavcodec discards it. JavaCV even reports this through
`logRejectedOptions`, which nobody has ever seen because the app sets `AV_LOG_QUIET`.

So `VideoThumbnailExtractor`'s `grabber.setVideoOption("hwaccel", "auto")` does nothing, and the
comment in `openVideoGrabber` about dodging D3D11VA scaling bugs describes something that could not
have happened by that route. It should be deleted rather than worked around.

The real door is one line above the options: `avcodec_find_decoder_by_name(videoCodecName)`
(`FFmpegFrameGrabber.java:1048`). A *named hardware decoder* — `hevc_cuvid`, `hevc_qsv` — goes
through JavaCV's ordinary path and works.

### 2. On a capable machine, hardware decode is a pessimisation

`hevc_cuvid` produced correct frames and was **slower than software**: 127 fps against 158.

The reason is structural, not a tuning problem. NVDEC decodes into GPU memory, and the frame then has
to come back across PCIe (`av_hwframe_transfer_data`) before swscale can convert it — because
everything downstream, Skia included, needs it in system memory. The round trip costs more than a CPU
that decodes a frame in 5 ms saves.

This kills "always use the GPU". It also kills "let the two devices help each other": HEVC is
inter-frame, every frame depends on the ones before it, so a single stream cannot be split across
two decoders in any useful way. One stream, one decoder.

### 3. The codec is not the bottleneck here — the output size is

Decode alone is 5.0 ms. Converting to RGBA at 2560px adds 1.3 ms; at native 3840px it adds 7.4 ms.
Output size, not the codec, is what doubles the frame cost — and `RenderScale` already asks for the
surface size, so the largest win available is one this project made some time ago.

That also sets the scale of the problem honestly: 5 ms of decode against the 41 ms a 24 fps file
allows is **eight times more headroom than needed**. A machine has to be roughly eight times slower
before playback breaks, and that is where hardware decode earns its keep — the PCIe round trip is
roughly constant while software decode scales with the CPU.

---

## The design it led to — reverted, kept for the record

Not a switch, and not a setting the user has to understand. **Software first, hardware only when
software cannot keep up.**

The signal already exists. `FramePacer.isFallingBehind` and the presenter's drop counter feed
`PlaybackGeneration.isBehind`, built to stand seek-bar previews down. Sustained lateness is exactly
the condition under which a hardware decoder stops being a pessimisation, so the same flag can ask
for the swap. The mechanism to perform it exists too: `pendingVideoReopenIndex` re-opens the video
grabber in place, which is how track switching already works.

### ~~Phase 1: Name what is available~~ (не существует в коде)

- Add `video/engine/decode/HardwareDecoders.kt`: for a codec id, the candidate decoder names in
  preference order — `hevc_cuvid`/`h264_cuvid`, then `hevc_qsv`/`h264_qsv` — filtered by
  `avcodec_find_decoder_by_name` returning non-null.
- **Probe off the playback path and time-boxed.** Measured during this work: asking for `hevc_qsv` on
  a machine with no Intel GPU does not fail, it *hangs* — the probe run had to be killed. So the
  check runs once per session on its own thread with a deadline, and a candidate that does not answer
  in time is struck off for good.
- No AMD entry: FFmpeg has no standalone AMD decoder on Windows (AMF is encode-only), and D3D11VA is
  a hwaccel, which JavaCV cannot reach. An AMD-only machine stays on software. Say so plainly rather
  than shipping a setting that silently does nothing.

**Files:** `video/engine/decode/HardwareDecoders.kt`

### ~~Phase 2: Swap when behind, once~~ (не существует в коде)

- `openVideoGrabber` and `reopenVideoGrabber` take a `decoderName`, which they hand to
  `videoCodecName`. **No state on `VideoPlayerState`** — it was at 148 lines, and the decode loop is
  the only thing that needs to remember the choice, so it holds it in a local. One less field on the
  hot object for free.
- The loop asks `requestHardwareSwapIfNeeded`, shaped like the `requestRescaleIfNeeded` beside it:
  sustained lateness *and* a candidate *and* nothing tried yet → request the in-place re-open.
- **Once.** No oscillation: if the hardware decoder is also behind, that is the machine's answer, and
  flipping back and forth would cost a re-open, and its spinner, every swing.
- A re-open that throws falls back to software in the same iteration and abandons hardware for the
  file. Re-entering `reopenVideoGrabber` is safe because its teardown of the old grabber is guarded.

**Files:** `video/engine/decode/HardwareFallback.kt`, `video/engine/decode/VideoPlayerGrabber.kt`,
`video/engine/decode/VideoPlayerDecoder.kt`, `video/engine/track/VideoPlayerVideoTrack.kt`,
`jvmTest/.../HardwareFallbackTest.kt`

### ~~Phase 3: Clean up what was never true~~ (не существует в коде)

- Delete the `hwaccel` option from `VideoThumbnailExtractor` — it has never done anything.
- Replace the `"threads", "auto"` comment in `openVideoGrabber`; keep the setting, drop the claim
  about D3D11VA.
- **Not done:** raising `AV_LOG_QUIET` so FFmpeg's rejected-option warnings are visible. It is what
  hid this, but lifting it globally would print decoder warnings throughout playback of a 74 GB file
  for one diagnostic. Removing the dead option is the actual fix; there is now nothing to warn about.

**Files:** `video/engine/VideoThumbnailExtractor.kt`, `video/engine/decode/VideoPlayerGrabber.kt`

### ~~Phase 4: Re-opening without a stall~~ (не существует в коде)

Reported after the first real run, and not caused by any of this work: entering fullscreen, and
dragging the window from a 3440×1440 display at 100% scaling to a 4096×2160 one at 225%, each showed
a 250–500 ms spinner. It is the pre-existing decode-size policy (§6) — `RenderScale` asks the decoder
for frames the size of the surface *in pixels* and re-opens the grabber when that moves by more than
25%. A 1100×720 window wants 1068 px of decode, fullscreen at 3440×1440 wants 2560, so the threshold
is passed by a mile; a DPI change multiplies the pixel size by 2.25 and does the same. Zoom does not,
because zoom is a `graphicsLayer` transform and leaves the surface size alone — which is exactly what
the report observed.

The re-open is legitimate. Being made to watch it is not. Two fixes:

- **Capped probe on re-open.** Opening this file costs 59 ms with a full probe and **4 ms** with
  `probesize=2000000` / `analyzeduration=1000000`. A re-open already knows the container, so it has
  no reason to pay for discovery — the audio grabber has capped its own probe all along. Falls back
  to the full probe if `start()` fails: the cap is an optimisation, not a contract.
- **Background preparation with a seamless hand-over** (`VideoGrabberSwap.kt`). The replacement is
  opened and positioned *while the current grabber keeps playing*, and only then traded in. The
  hand-over is keyed on the decoder's position rather than the play head's: the decoder runs ahead of
  the picture by whatever the frame channel holds, so trading when the picture arrives would leave
  old frames queued past the point the new grabber starts at, and the picture would jump backwards.
  Trading at the decoder's own position makes the seam exact — the old grabber stops at the target
  and the new one begins there, so the channel is never drained and no frame is lost.

The spinner now appears **only** when the background preparation fails and the in-line re-open is
used instead. A deliberate track switch changes over within about 1.2 s rather than freezing for half
of one, and audio no longer parks through it, because nothing raises `isReloadingVideo` on the happy
path.

Cost: two grabbers are alive for roughly a second per swap. That is the trade for never stalling, and
it is bounded — one preparation at a time, and one invalidated by a seek is cancelled and released
off the decode loop.

**Files:** `video/engine/decode/VideoGrabberSwap.kt` (new),
`video/engine/decode/VideoPlayerGrabber.kt`, `video/engine/decode/VideoPlayerDecoder.kt`,
`video/engine/decode/VideoPlayerRenderScale.kt`, `video/engine/decode/HardwareFallback.kt`,
`video/engine/track/VideoPlayerVideoTrack.kt`

---

## Coverage, stated plainly

| machine | path | verified |
|---|---|---|
| NVIDIA | `hevc_cuvid` / `h264_cuvid` | decoder yes, swap no |
| Intel | `hevc_qsv` / `h264_qsv` | no — no Intel hardware here |
| AMD | none — stays on software | n/a |
| no GPU | software | yes, it is the default and unchanged |

The AMD gap is structural, not an omission, and it matters less than it looks: hardware decode is
only wanted by a machine that cannot manage software, and the machines that fit that description are
mostly weak Intel parts, which QSV covers. A Ryzen APU has cores enough; an AMD chip old enough to
struggle would struggle at everything.

Since two of the four rows cannot be tested here, the design is built to **degrade safely** rather
than to work everywhere: no candidate counts until it has opened and produced a frame, the attempt is
time-boxed because an absent decoder can hang rather than throw, a failure is remembered for the
session, and software sits underneath all of it.

---

## Bit depth — deliberately not doing this

`AV_PIX_FMT_RGBA` truncates 10-bit PQ to 8 bits before `HDR_SHADER` expands it again, which is the
textbook recipe for banding on gradients. The fix is `RGBA64LE`/F16 and it doubles the bytes per
frame and the swscale time.

Two reasons it is not in this plan. The banding has never actually been observed — it is predicted
from the pixel format, not seen. And the cost lands hardest on exactly the machines this plan exists
to serve. Revisit if banding is ever seen on a real gradient; the change itself is small
(`pixelFormat`, plus a matching `ColorType` in `FramePacer.convert`).

## Out of scope

**Zero-copy GPU rendering** — decoding into VRAM and handing Skia a `BackendTexture`, so the frame
never crosses PCIe at all. This is the only thing that would make hardware decode a win on a fast
machine too, and it is unreachable from JavaCV: any `pixelFormat` forces the transfer to system
memory. It would mean driving `AVHWFramesContext` through JavaCPP and building a Skia backend texture
from a D3D11 shared handle, bypassing both JavaCV and Compose. Recorded so it is not rediscovered as
a good idea.
